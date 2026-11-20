package com.bolt.pdf.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.view.OrientationEventListener
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolt.pdf.OrientationMode
import com.bolt.pdf.PageLayoutMode
import com.bolt.pdf.PdfViewModel
import com.bolt.pdf.ReaderUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import uniffi.pdf_core.HighlightRecord
import uniffi.pdf_core.NormalizedRect
import uniffi.pdf_core.PageDimension
import uniffi.pdf_core.PageTextContent
import uniffi.pdf_core.SearchMatch
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


internal data class VisualChar(
    val text: String,
    val rect: NormalizedRect,
    val charIndex: Int,
    val origListIdx: Int,
    val lineIdx: Int,
    val lineIsRtl: Boolean,
    val isDropCap: Boolean = false
)

internal class VisualPageText(
    val chars: List<VisualChar>
)

private val visualPageCache = WeakHashMap<PageTextContent, VisualPageText>()

internal fun getVisualPageText(content: PageTextContent): VisualPageText {
    visualPageCache[content]?.let { return it }

    data class RawItem(
        val text: String,
        val rect: NormalizedRect,
        val charIndex: Int,
        val origIdx: Int,
        val midY: Float,
        val midX: Float,
        val height: Float
    )

    val validItems = content.chars.mapIndexedNotNull { idx, ch ->
        val r = ch.rect
        if (r.right > r.left && r.bottom > r.top) {
            RawItem(
                text = ch.text,
                rect = r,
                charIndex = ch.charIndex.toInt(),
                origIdx = idx,
                midY = (r.top + r.bottom) * 0.5f,
                midX = (r.left + r.right) * 0.5f,
                height = (r.bottom - r.top).coerceAtLeast(0.005f)
            )
        } else null
    }

    if (validItems.isEmpty()) {
        return VisualPageText(emptyList()).also { visualPageCache[content] = it }
    }

    val sortedHeights = validItems.map { it.height }.sorted()
    val medianH = sortedHeights[sortedHeights.size / 2].coerceAtLeast(0.005f)
    val dropCapThreshold = medianH * 1.65f

    // Cluster normal-height characters into lines first so tall drop-caps never merge multiple lines
    val normalItems = validItems.filter { it.height <= dropCapThreshold }.sortedBy { it.midY }
    val dropCapItems = validItems.filter { it.height > dropCapThreshold }

    val lines = mutableListOf<MutableList<RawItem>>()
    for (item in normalItems) {
        val targetLine = lines.lastOrNull()?.takeIf { line ->
            val lineMidY = line.map { it.midY }.average().toFloat()
            abs(item.midY - lineMidY) < medianH * 0.58f
        }
        if (targetLine != null) {
            targetLine.add(item)
        } else {
            lines.add(mutableListOf(item))
        }
    }

    // Attach any tall drop-caps to the line whose top edge is closest to the drop-cap's top edge
    for (dc in dropCapItems) {
        if (lines.isEmpty()) {
            lines.add(mutableListOf(dc))
        } else {
            val bestLine = lines.minByOrNull { line ->
                val lineTop = line.minOf { it.rect.top }
                abs(dc.rect.top - lineTop)
            }
            bestLine?.add(dc)
        }
    }

    val visualChars = ArrayList<VisualChar>(validItems.size)
    lines.forEachIndexed { lineIdx, rawLineItems ->
        val lineIsRtl = isPredominantlyRtl(rawLineItems.map { it.text })
        val ordered = if (lineIsRtl) {
            rawLineItems.sortedByDescending { it.midX }
        } else {
            rawLineItems.sortedBy { it.midX }
        }
        // Deduplicate faux-bold / double-drawn glyphs at overlapping coordinates
        val deduped = ArrayList<RawItem>(ordered.size)
        val xTolBase = medianH * 0.45f
        val yTol = medianH * 0.45f
        for (item in ordered) {
            val itemW = (item.rect.right - item.rect.left).coerceAtLeast(0.001f)
            val xTol = max(itemW, xTolBase) * 0.7f
            val isDuplicate = deduped.any { prev ->
                prev.text == item.text &&
                    abs(prev.midX - item.midX) < xTol &&
                    abs(prev.midY - item.midY) < yTol
            }
            if (!isDuplicate) {
                deduped.add(item)
            }
        }
        // Ensure combining diacritics (tashkeel) always follow their base letter, never precede it
        val normalizedOrder = ArrayList<RawItem>(deduped.size)
        val pendingMarks = mutableListOf<RawItem>()
        for (item in deduped) {
            val isMark = item.text.isNotEmpty() && item.text.all { ch ->
                val t = Character.getType(ch)
                t == Character.NON_SPACING_MARK.toInt() ||
                    t == Character.COMBINING_SPACING_MARK.toInt() ||
                    t == Character.ENCLOSING_MARK.toInt()
            }
            if (isMark) {
                if (normalizedOrder.isNotEmpty()) {
                    // Attach after the last base letter if close, otherwise hold until next base letter
                    val prev = normalizedOrder.last()
                    if (abs(prev.midX - item.midX) < medianH * 0.8f) {
                        normalizedOrder.add(item)
                    } else {
                        pendingMarks.add(item)
                    }
                } else {
                    pendingMarks.add(item)
                }
            } else {
                normalizedOrder.add(item)
                if (pendingMarks.isNotEmpty()) {
                    normalizedOrder.addAll(pendingMarks)
                    pendingMarks.clear()
                }
            }
        }
        if (pendingMarks.isNotEmpty()) {
            normalizedOrder.addAll(pendingMarks)
        }
        for (item in normalizedOrder) {
            visualChars.add(
                VisualChar(
                    text = item.text,
                    rect = item.rect,
                    charIndex = item.charIndex,
                    origListIdx = item.origIdx,
                    lineIdx = lineIdx,
                    lineIsRtl = lineIsRtl,
                    isDropCap = item.height > dropCapThreshold
                )
            )
        }
    }

    return VisualPageText(visualChars).also { visualPageCache[content] = it }
}

internal fun findNearestCharIndex(content: PageTextContent, normX: Float, normY: Float): Int {
    val vChars = getVisualPageText(content).chars
    if (vChars.isEmpty()) return -1
    // Exact containment hit check first so character selection never skips to a neighbor
    for (i in vChars.indices) {
        val r = vChars[i].rect
        if (normX in r.left..r.right && normY in r.top..r.bottom) {
            return i
        }
    }
    var bestIdx = -1
    var bestDist = Float.MAX_VALUE
    for (i in vChars.indices) {
        val r = vChars[i].rect
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        val dy = abs(cy - normY)
        val dx = abs(cx - normX)
        val dist = dy * 4f + dx
        if (dist < bestDist) {
            bestDist = dist
            bestIdx = i
        }
    }
    return if (bestIdx >= 0) bestIdx else 0
}

internal fun expandToWord(content: PageTextContent, index: Int): Pair<Int, Int> {
    val vChars = getVisualPageText(content).chars
    if (vChars.isEmpty()) return 0 to 0
    var start = index.coerceIn(0, vChars.lastIndex)
    var end = start
    val lineIdx = vChars[start].lineIdx
    while (start > 0 &&
        vChars[start - 1].lineIdx == lineIdx &&
        vChars[start - 1].text.none { it.isWhitespace() }
    ) {
        start--
    }
    while (end < vChars.lastIndex &&
        vChars[end + 1].lineIdx == lineIdx &&
        vChars[end + 1].text.none { it.isWhitespace() }
    ) {
        end++
    }
    return start to end
}

internal fun buildSelection(
    content: PageTextContent,
    pageIndex: Int,
    startListIdx: Int,
    endListIdx: Int
): ActiveTextSelection {
    val vChars = getVisualPageText(content).chars
    if (vChars.isEmpty()) {
        return ActiveTextSelection(
            pageIndex = pageIndex,
            startListIdx = 0,
            endListIdx = 0,
            startCharIndex = 0,
            endCharIndex = 0,
            rects = emptyList(),
            handles = SelectionHandleBounds(0f, 0f, 0f, 0f, 0f, 0f),
            selectedText = ""
        )
    }

    val lo = min(startListIdx, endListIdx).coerceIn(0, vChars.lastIndex)
    val hi = max(startListIdx, endListIdx).coerceIn(0, vChars.lastIndex)
    val selected = vChars.subList(lo, hi + 1)

    val byLine = selected.groupBy { it.lineIdx }
    val mergedRects = byLine.values.map { lineChars ->
        val normalOnLine = lineChars.filter { !it.isDropCap }.ifEmpty { lineChars }
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        for (ch in lineChars) {
            left = min(left, ch.rect.left)
            right = max(right, ch.rect.right)
        }
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (ch in normalOnLine) {
            top = min(top, ch.rect.top)
            bottom = max(bottom, ch.rect.bottom)
        }
        NormalizedRect(left = left, top = top, right = right, bottom = bottom)
    }

    val text = byLine.values.joinToString("\n") { lineChars ->
        buildLogicalLineText(lineChars)
    }

    val firstChar = selected.first()
    val lastChar = selected.last()
    val firstLineRect = mergedRects.firstOrNull()
    val lastLineRect = mergedRects.lastOrNull()

    val handles = if (firstLineRect != null && lastLineRect != null) {
        SelectionHandleBounds(
            startX = if (firstChar.lineIsRtl) firstLineRect.right else firstLineRect.left,
            startTop = firstLineRect.top,
            startBottom = firstLineRect.bottom,
            endX = if (lastChar.lineIsRtl) lastLineRect.left else lastLineRect.right,
            endTop = lastLineRect.top,
            endBottom = lastLineRect.bottom
        )
    } else {
        SelectionHandleBounds(0f, 0f, 0f, 0f, 0f, 0f)
    }

    val minOrigCharIdx = selected.minOf { it.charIndex }
    val maxOrigCharIdx = selected.maxOf { it.charIndex }

    return ActiveTextSelection(
        pageIndex = pageIndex,
        startListIdx = lo,
        endListIdx = hi,
        startCharIndex = minOrigCharIdx,
        endCharIndex = maxOrigCharIdx,
        rects = mergedRects,
        handles = handles,
        selectedText = text
    )
}

internal fun isStrongLtrChar(text: String): Boolean {
    for (ch in text) {
        val dir = Character.getDirectionality(ch)
        if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT ||
            dir == Character.DIRECTIONALITY_EUROPEAN_NUMBER ||
            dir == Character.DIRECTIONALITY_ARABIC_NUMBER
        ) {
            return true
        }
    }
    return false
}

internal fun buildLogicalLineText(lineChars: List<VisualChar>): String {
    if (lineChars.isEmpty()) return ""
    val isRtl = lineChars.first().lineIsRtl
    val widths = lineChars
        .map { (it.rect.right - it.rect.left) }
        .filter { it > 0.001f }
        .sorted()
    val medianWidth = if (widths.isNotEmpty()) widths[widths.size / 2] else 0.01f
    val spaceThreshold = (medianWidth * 0.30f).coerceAtLeast(0.002f)

    // Reconstruct tokens in visual reading order, inserting spaces at word gaps,
    // and keeping embedded LTR runs (Latin/numbers) left-to-right on RTL lines.
    val rawTokens = ArrayList<Pair<String, Boolean>>(lineChars.size * 2)
    for (i in lineChars.indices) {
        val cur = lineChars[i]
        if (i > 0) {
            val prev = lineChars[i - 1]
            val gap = if (isRtl) {
                prev.rect.left - cur.rect.right
            } else {
                cur.rect.left - prev.rect.right
            }
            val hasSpace = prev.text.any { it.isWhitespace() } || cur.text.any { it.isWhitespace() }
            if (!hasSpace && gap > spaceThreshold) {
                rawTokens.add(" " to false)
            }
        }
        rawTokens.add(cur.text to isStrongLtrChar(cur.text))
    }

    if (!isRtl) {
        return rawTokens.joinToString("") { it.first }
    }

    // On RTL lines, reverse contiguous strong-LTR runs (allowing internal spaces/punctuation between LTR words)
    val result = StringBuilder(rawTokens.size)
    var idx = 0
    while (idx < rawTokens.size) {
        if (rawTokens[idx].second) {
            val runStart = idx
            var runEnd = idx
            var j = idx + 1
            while (j < rawTokens.size) {
                if (rawTokens[j].second) {
                    runEnd = j
                    j++
                } else if (rawTokens[j].first == " " || rawTokens[j].first in listOf(".", ",", "-", "_", "/", ":")) {
                    // Include separator only if followed by another LTR token
                    if (j + 1 < rawTokens.size && rawTokens[j + 1].second) {
                        j++
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            for (k in runEnd downTo runStart) {
                result.append(rawTokens[k].first)
            }
            idx = runEnd + 1
        } else {
            result.append(rawTokens[idx].first)
            idx++
        }
    }
    return result.toString()
}

internal fun mergeLineRects(rects: List<NormalizedRect>): List<NormalizedRect> {
    if (rects.isEmpty()) return emptyList()
    val merged = mutableListOf<NormalizedRect>()
    var cur = rects[0]
    for (i in 1 until rects.size) {
        val next = rects[i]
        val curMidY = (cur.top + cur.bottom) / 2f
        val nextMidY = (next.top + next.bottom) / 2f
        val lineHeight = (cur.bottom - cur.top).coerceAtLeast(0.005f)
        if (abs(curMidY - nextMidY) < lineHeight * 0.6f) {
            cur = NormalizedRect(
                left = min(cur.left, next.left),
                top = min(cur.top, next.top),
                right = max(cur.right, next.right),
                bottom = max(cur.bottom, next.bottom)
            )
        } else {
            merged.add(cur)
            cur = next
        }
    }
    merged.add(cur)
    return merged
}

internal fun hasStrongDirection(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true
        }
    }
    return false
}

internal fun isPredominantlyRtl(texts: List<String>): Boolean {
    var rtl = 0
    var ltr = 0
    for (text in texts) {
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> rtl++
                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> ltr++
            }
        }
    }
    return rtl > ltr
}
