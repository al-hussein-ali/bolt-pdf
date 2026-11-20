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


@Composable
internal fun FastScrollBar(
    currentPage: Int,
    totalPages: Int,
    viewportHeightPx: Int,
    onScrubToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val displayFraction = if (isDragging) {
        dragFraction
    } else {
        (currentPage.toFloat() / max(1, totalPages - 1)).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val thumbHeightDp = 48.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
    val trackHeightPx = (viewportHeightPx - with(density) { 100.dp.toPx() } - thumbHeightPx).coerceAtLeast(1f)
    val thumbOffsetYPx = (displayFraction * trackHeightPx).roundToInt()

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.Top
    ) {
        if (isDragging) {
            val previewPage = (displayFraction * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1) + 1
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetYPx) }
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "Page $previewPage / $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetYPx) }
                    .width(36.dp)
                    .height(thumbHeightDp + 16.dp)
                    .pointerInput(totalPages, trackHeightPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragFraction = (currentPage.toFloat() / max(1, totalPages - 1)).coerceIn(0f, 1f)
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val nextFrac = (dragFraction + dragAmount / trackHeightPx).coerceIn(0f, 1f)
                                dragFraction = nextFrac
                                val target = (nextFrac * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
                                onScrubToPage(target)
                            }
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .width(if (isDragging) 10.dp else 6.dp)
                        .height(thumbHeightDp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
internal fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.jumpToPage) },
        text = {
            Column {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        pageInput = digits
                        digits.toIntOrNull()?.let { num ->
                            sliderValue = num.coerceIn(1, totalPages).toFloat()
                        }
                    },
                    label = { Text(strings.pageRangeInputLabel(totalPages)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val target = pageInput.toIntOrNull() ?: currentPage
                            onJumpToPage(target.coerceIn(1, totalPages))
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { v ->
                            sliderValue = v
                            pageInput = v.roundToInt().toString()
                        },
                        valueRange = 1f..totalPages.toFloat()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = pageInput.toIntOrNull() ?: sliderValue.roundToInt()
                onJumpToPage(target.coerceIn(1, totalPages))
            }) {
                Text(strings.go)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}


@Composable
internal fun SearchBarRow(
    query: String,
    matchCount: Int,
    activeIndex: Int,
    isSearching: Boolean,
    isCompact: Boolean,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(strings.searchInDocument) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    val displayIdx = if (matchCount > 0 && activeIndex >= 0) activeIndex + 1 else 0
                    Text(
                        text = "$displayIdx/$matchCount",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val btnModifier = if (isCompact) Modifier.size(36.dp) else Modifier.size(44.dp)
                IconButton(onClick = onPrevious, enabled = matchCount > 0, modifier = btnModifier) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = onNext, enabled = matchCount > 0, modifier = btnModifier) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = onClose, modifier = btnModifier) {
                    Icon(Icons.Default.Close, contentDescription = "Close search")
                }
            }
        }
    }
}

internal suspend fun scrollToMatch(
    listState: androidx.compose.foundation.lazy.LazyListState,
    dimensions: List<PageDimension>,
    match: SearchMatch,
    pageWidthPx: Int,
) {
    val pageIndex = match.pageIndex.toInt().coerceIn(0, (dimensions.size - 1).coerceAtLeast(0))
    val viewportHeightPx = listState.layoutInfo.viewportSize.height.coerceAtLeast(1)
    val dim = dimensions.getOrNull(pageIndex)
    val pageHeightPx = if (dim != null) {
        pageWidthPx.toFloat() / dim.aspectRatio.coerceAtLeast(0.1f)
    } else {
        viewportHeightPx.toFloat()
    }
    val topFrac = match.rects.firstOrNull()?.top ?: 0f
    val pixelOffset = (topFrac * pageHeightPx - viewportHeightPx * 0.3f).toInt().coerceAtLeast(0)
    listState.scrollToItem(pageIndex, pixelOffset)
}
