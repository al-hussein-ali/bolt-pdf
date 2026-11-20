package com.bolt.pdf.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import uniffi.pdf_core.PageDimension
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class PdfEditorSession(
    val sourceUri: Uri,
    val displayName: String,
    val fallbackPath: String?,
    val fileHash: String,
    val canReplaceOriginal: Boolean,
    val initialPageIndex: Int,
    val returnToReader: Boolean,
    val dimensions: List<PageDimension>,
)

sealed interface EditorPageKind {
    data class Original(
        val pageIndex: Int,
        val widthPts: Float,
        val heightPts: Float,
    ) : EditorPageKind

    data class Blank(
        val widthPts: Float = 595f,
        val heightPts: Float = 842f,
    ) : EditorPageKind

    data class Image(
        val uri: Uri,
        val displayName: String,
        val widthPts: Float,
        val heightPts: Float,
    ) : EditorPageKind

    data class ExternalPdf(
        val uri: Uri,
        val pageIndex: Int,
        val displayName: String,
        val widthPts: Float = 595f,
        val heightPts: Float = 842f,
    ) : EditorPageKind
}

data class EditorTextBox(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val coverLeft: Float? = null,
    val coverTop: Float? = null,
    val coverRight: Float? = null,
    val coverBottom: Float? = null,
    val coverBackground: Boolean = false,
    val fontSizePts: Float = 14f,
    val colorArgb: Long = 0xFF111111L,
    val isBold: Boolean = false,
)

data class EditorPageItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: EditorPageKind,
    val rotationDegrees: Int = 0,
    val textEdits: List<EditorTextBox> = emptyList(),
) {
    fun effectiveWidthPts(): Float {
        val (w, h) = rawDimensionsPts()
        return if (rotationDegrees % 180 == 90) h else w
    }

    fun effectiveHeightPts(): Float {
        val (w, h) = rawDimensionsPts()
        return if (rotationDegrees % 180 == 90) w else h
    }

    fun effectiveAspectRatio(): Float =
        (effectiveWidthPts() / effectiveHeightPts().coerceAtLeast(1f)).coerceIn(0.2f, 5f)

    private fun rawDimensionsPts(): Pair<Float, Float> = when (kind) {
        is EditorPageKind.Original -> kind.widthPts to kind.heightPts
        is EditorPageKind.Blank -> kind.widthPts to kind.heightPts
        is EditorPageKind.Image -> kind.widthPts to kind.heightPts
        is EditorPageKind.ExternalPdf -> kind.widthPts to kind.heightPts
    }
}

/**
 * Renders an EditorTextBox to a crisp high-DPI transparent PNG so Arabic, RTL, emojis,
 * and multi-line wrapped text render identically to the live Compose editor preview.
 */
fun renderTextBoxOverlayPng(
    box: EditorTextBox,
    pageWidthPts: Float,
    pageHeightPts: Float,
    outFile: File,
): Boolean {
    val boxWidthPts = ((box.right - box.left) * pageWidthPts).coerceAtLeast(16f)
    val boxHeightPts = ((box.bottom - box.top) * pageHeightPts).coerceAtLeast(12f)
    // 300 DPI rasterization (300 / 72 = 4.1667 px per PDF point), clamped to safe bitmap bounds
    val scale = (300f / 72f).coerceAtMost(2048f / max(boxWidthPts, boxHeightPts))
    val widthPx = (boxWidthPts * scale).roundToInt().coerceIn(8, 2048)
    val heightPx = (boxHeightPts * scale).roundToInt().coerceIn(8, 2048)

    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
        color = box.colorArgb.toInt()
        textSize = box.fontSizePts * scale
        typeface = if (box.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    val staticLayout = StaticLayout.Builder
        .obtain(box.text, 0, box.text.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setIncludePad(false)
        .build()

    val finalHeightPx = max(heightPx, staticLayout.height).coerceIn(8, 2048)
    val bitmap = Bitmap.createBitmap(widthPx, finalHeightPx, Bitmap.Config.ARGB_8888)
    return try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        staticLayout.draw(canvas)
        outFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        true
    } catch (_: Exception) {
        false
    } finally {
        bitmap.recycle()
    }
}
