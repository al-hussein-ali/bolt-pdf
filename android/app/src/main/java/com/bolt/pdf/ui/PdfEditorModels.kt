package com.bolt.pdf.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import uniffi.pdf_core.PageDimension
import uniffi.pdf_core.PageTextContent
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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

data class DetectedTextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val estimatedFontSizePts: Float,
)

/**
 * Groups character boxes from PDFium into clean editable horizontal text segments.
 * Splits across large horizontal gaps so multi-column layouts or table cells can be edited individually.
 */
fun detectEditableTextLines(
    content: PageTextContent,
    pageHeightPts: Float,
): List<DetectedTextLine> {
    if (content.chars.isEmpty()) return emptyList()

    data class MutableLine(
        val chars: MutableList<uniffi.pdf_core.PageTextChar>,
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        var sumHeight: Float,
    ) {
        fun centerY(): Float = (top + bottom) * 0.5f
        fun avgHeight(): Float = (sumHeight / chars.size.coerceAtLeast(1)).coerceAtLeast(0.008f)
    }

    val lines = mutableListOf<MutableLine>()
    for (ch in content.chars) {
        if (ch.text == "\r" || ch.text == "\n") continue
        val r = ch.rect
        val chW = (r.right - r.left).coerceAtLeast(0f)
        val chH = (r.bottom - r.top).coerceAtLeast(0f)
        if (chH <= 0.0005f) continue
        val cy = (r.top + r.bottom) * 0.5f

        val last = lines.lastOrNull()
        val sameLine = if (last != null) {
            val refH = max(last.avgHeight(), chH)
            val yClose = abs(cy - last.centerY()) <= refH * 0.65f
            val gapX = max(0f, max(r.left - last.right, last.left - r.right))
            val xClose = gapX <= refH * 2.6f
            yClose && xClose
        } else {
            false
        }

        if (sameLine && last != null) {
            last.chars.add(ch)
            if (chW > 0f) {
                last.left = min(last.left, r.left)
                last.right = max(last.right, r.right)
            }
            last.top = min(last.top, r.top)
            last.bottom = max(last.bottom, r.bottom)
            last.sumHeight += chH
        } else {
            lines.add(
                MutableLine(
                    chars = mutableListOf(ch),
                    left = r.left,
                    top = r.top,
                    right = r.right,
                    bottom = r.bottom,
                    sumHeight = chH
                )
            )
        }
    }

    return lines.mapNotNull { line ->
        val rawText = buildString {
            line.chars.forEach { append(it.text) }
        }.trim()
        if (rawText.isEmpty()) return@mapNotNull null

        val normHeight = (line.bottom - line.top).coerceAtLeast(0.01f)
        val fontPts = (normHeight * pageHeightPts * 0.82f).coerceIn(8f, 48f)
        DetectedTextLine(
            text = rawText,
            left = line.left.coerceIn(0f, 1f),
            top = line.top.coerceIn(0f, 1f),
            right = line.right.coerceIn(0f, 1f),
            bottom = line.bottom.coerceIn(0f, 1f),
            estimatedFontSizePts = fontPts,
        )
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
