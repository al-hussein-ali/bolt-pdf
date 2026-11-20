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

private val HIGHLIGHT_COLOR_OPTIONS = listOf(
    0x66FFEB3BL, // Yellow
    0x664CAF50L, // Green
    0x662196F3L, // Blue
    0x66E91E63L, // Pink
    0x66FF9800L, // Orange
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal data class SelectionHandleBounds(
    val startX: Float,
    val startTop: Float,
    val startBottom: Float,
    val endX: Float,
    val endTop: Float,
    val endBottom: Float,
)

private data class ZoomTileOverlay(
    val pageIndex: Int,
    val visibleXPx: Int,
    val visibleYPx: Int,
    val tileWidthPx: Int,
    val tileHeightPx: Int,
    val fullScaledWidthPx: Int,
    val fullScaledHeightPx: Int,
    val scaleAtRender: Float,
    val bitmap: Bitmap,
)

private data class TileTriggerSnapshot(
    val zoomScale: Float,
    val panX: Float,
    val panY: Float,
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Int,
    val renderGen: Long,
    val fitFullWidth: Boolean,
    val topPaddingPx: Int,
)

internal data class ActiveTextSelection(
    val pageIndex: Int,
    val startListIdx: Int,
    val endListIdx: Int,
    val startCharIndex: Int,
    val endCharIndex: Int,
    val rects: List<NormalizedRect>,
    val handles: SelectionHandleBounds,
    val selectedText: String,
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    orientationMode: OrientationMode,
    isOpeningDocument: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onEditPdf: (currentPageIndex: Int) -> Unit,
) {
    val progress = state.progress
    val favoriteHashes by viewModel.favoriteHashes.collectAsState()
    val favoriteUris by viewModel.favoriteUris.collectAsState()
    val isFavorite = progress.fileHash in favoriteHashes || progress.fileUri in favoriteUris
    val isSharingDocument by viewModel.isSharingDocument.collectAsState()
    val totalPages = state.dimensions.size.coerceAtLeast(1)
    val initialPage = progress.lastPageIndex.toInt().coerceIn(0, totalPages - 1)
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage,
        initialFirstVisibleItemScrollOffset = 0
    )
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { totalPages }
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val strings = LocalAppStrings.current
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isCompactPhone = screenWidthDp < 400
    val isWideScreen = screenWidthDp >= 720
    val readableMaxPx = with(density) { 840.dp.roundToPx() }
    val screenWidthPx = with(density) { screenWidthDp.dp.roundToPx() }
    val estimatedPageWidthPx = if (isWideScreen) {
        min(screenWidthPx, readableMaxPx)
    } else {
        screenWidthPx
    }

    var isBarsVisible by remember(progress.fileHash) { mutableStateOf(true) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var detectedDeviceOrientation by remember { mutableStateOf(orientationMode) }
    var showOrientationPrompt by remember { mutableStateOf(false) }
    val latestOrientationMode by rememberUpdatedState(orientationMode)

    LaunchedEffect(showOrientationPrompt, detectedDeviceOrientation, orientationMode) {
        if (detectedDeviceOrientation == orientationMode) {
            showOrientationPrompt = false
        } else if (showOrientationPrompt) {
            kotlinx.coroutines.delay(5000L)
            showOrientationPrompt = false
        }
    }

    // Hide battery/time status bar when reading full-screen; swipe from edge peeks it transiently.
    LaunchedEffect(isBarsVisible) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isBarsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = context.findActivity()?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN) return
                val detected = when {
                    angle in 240..300 -> OrientationMode.LANDSCAPE
                    angle in 60..120 -> OrientationMode.REVERSE_LANDSCAPE
                    angle in 0..30 || angle in 330..359 -> OrientationMode.PORTRAIT
                    else -> return
                }
                if (detectedDeviceOrientation != detected) {
                    detectedDeviceOrientation = detected
                    showOrientationPrompt = (detected != latestOrientationMode)
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose {
            listener.disable()
        }
    }

    var zoomScale by remember(progress.fileHash) {
        mutableFloatStateOf(progress.zoomScale.coerceIn(1f, 5f))
    }
    var panOffsetX by remember(progress.fileHash) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(progress.fileHash) { mutableFloatStateOf(0f) }
    var activeZoomTiles by remember(progress.fileHash) { mutableStateOf<Map<Int, ZoomTileOverlay>>(emptyMap()) }
    var initialRestored by remember(progress.fileHash) { mutableStateOf(false) }
    var fitFullWidthOnTablet by remember(progress.fileHash) { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    var activeSelection by remember(progress.fileHash) { mutableStateOf<ActiveTextSelection?>(null) }
    var selectedExistingHighlight by remember(progress.fileHash) { mutableStateOf<HighlightRecord?>(null) }
    var selectedHighlightColorArgb by remember { mutableLongStateOf(HIGHLIGHT_COLOR_OPTIONS.first()) }
    var isSelectionMode by remember(progress.fileHash) { mutableStateOf(false) }
    var activePageText by remember(progress.fileHash) { mutableStateOf<PageTextContent?>(null) }
    var pendingSelStartNorm by remember(progress.fileHash) { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingSelEndNorm by remember(progress.fileHash) { mutableStateOf<Pair<Float, Float>?>(null) }
    var dragAnchorRange by remember(progress.fileHash) { mutableStateOf<Pair<Int, Int>?>(null) }
    var isDraggingSelectionHandle by remember(progress.fileHash) { mutableStateOf(false) }
    var isDraggingNewSelection by remember(progress.fileHash) { mutableStateOf(false) }

    val blockScrollForNewDragSelection = (isSelectionMode && activeSelection == null) || isDraggingNewSelection
    val latestDraggingHandle by rememberUpdatedState(isDraggingSelectionHandle)
    val latestBlockScrollForNewDragSelection by rememberUpdatedState(blockScrollForNewDragSelection)

    val matchesByPage = remember(state.searchMatches) {
        state.searchMatches.groupBy { it.pageIndex.toInt() }
    }
    val highlightsByPage = remember(state.highlights) {
        state.highlights.groupBy { it.pageIndex.toInt() }
    }
    val activeMatch = state.searchMatches.getOrNull(state.activeMatchIndex)

    val currentPageIndex = if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
        pagerState.currentPage
    } else {
        lazyListState.firstVisibleItemIndex
    }

    // Keep LazyColumn and HorizontalPager synchronized when switching layout mode
    LaunchedEffect(state.pageLayoutMode) {
        val target = progress.lastPageIndex.toInt().coerceIn(0, totalPages - 1)
        if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
            pagerState.scrollToPage(target)
        } else {
            lazyListState.scrollToItem(target, 0)
        }
    }

    // Auto-scroll to active search match when search results arrive or activeMatch changes
    LaunchedEffect(state.searchMatches, state.activeMatchIndex) {
        val match = state.searchMatches.getOrNull(state.activeMatchIndex) ?: return@LaunchedEffect
        val pageIdx = match.pageIndex.toInt().coerceIn(0, totalPages - 1)
        if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
            pagerState.animateScrollToPage(pageIdx)
        } else {
            val targetW = if (fitFullWidthOnTablet) screenWidthPx else estimatedPageWidthPx
            scrollToMatch(lazyListState, state.dimensions, match, targetW)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (progress.darkModeEnabled) Color(0xFF33363F) else Color(0xFF1A1C22))
        ) {
            val viewportWidthPx = constraints.maxWidth.coerceAtLeast(1)
            val viewportHeightPx = constraints.maxHeight.coerceAtLeast(1)
            val pageWidthPx = if (isWideScreen && !fitFullWidthOnTablet) {
                min(viewportWidthPx, readableMaxPx)
            } else {
                viewportWidthPx
            }
            val pageWidthDp = with(density) { pageWidthPx.toDp() }
            val pageLeftOffsetPx = ((viewportWidthPx - pageWidthPx) / 2f).coerceAtLeast(0f)
            val pageSpacingDp = 1.5.dp
            val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val topContentPadding by animateDpAsState(
                targetValue = if (isBarsVisible) statusBarTopDp + 48.dp + pageSpacingDp else pageSpacingDp,
                label = "topContentPadding"
            )

            // Clear any stale zoom tile immediately when visual geometry, dark mode, or rotation changes
            LaunchedEffect(progress.darkModeEnabled, progress.manualRotation, pageWidthPx, state.pageLayoutMode) {
                activeZoomTiles = emptyMap()
            }

            // Restore initial scroll fraction and preserve position across rotations
            LaunchedEffect(pageWidthPx, progress.manualRotation, progress.fileHash) {
                val targetIndex = progress.lastPageIndex.toInt().coerceIn(0, totalPages - 1)
                val dim = state.dimensions.getOrNull(targetIndex)
                val pageHeightPx = if (dim != null) {
                    (pageWidthPx.toFloat() / dim.aspectRatio.coerceAtLeast(0.1f)).roundToInt()
                } else {
                    viewportHeightPx
                }
                val targetOffset = (progress.pageOffsetFraction * pageHeightPx.toFloat())
                    .roundToInt()
                    .coerceAtLeast(0)
                if (state.pageLayoutMode == PageLayoutMode.VERTICAL_SCROLL) {
                    lazyListState.scrollToItem(targetIndex, targetOffset)
                } else {
                    pagerState.scrollToPage(targetIndex)
                }

                if (!initialRestored) {
                    initialRestored = true
                    val maxPanX = viewportWidthPx * (zoomScale - 1f)
                    panOffsetX = (-progress.panOffsetXFraction.coerceIn(0f, 1f) * maxPanX)
                        .coerceIn(-maxPanX, 0f)
                }
            }

            // Persist reading position continuously (debounced inside ViewModel)
            LaunchedEffect(pageWidthPx, viewportWidthPx, state.dimensions, state.pageLayoutMode) {
                snapshotFlow {
                    val idx = if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                        pagerState.currentPage
                    } else {
                        lazyListState.firstVisibleItemIndex
                    }
                    val offset = if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                        0
                    } else {
                        lazyListState.firstVisibleItemScrollOffset
                    }
                    Triple(
                        idx,
                        offset,
                        Triple(zoomScale, panOffsetX, progress.manualRotation)
                    )
                }.collect { (firstIdx, firstOffset, zoomTuple) ->
                    val dim = state.dimensions.getOrNull(firstIdx)
                    val pageHeightPx = if (dim != null) {
                        (pageWidthPx.toFloat() / dim.aspectRatio.coerceAtLeast(0.1f)).coerceAtLeast(1f)
                    } else {
                        1f
                    }
                    val offsetFraction = (firstOffset.toFloat() / pageHeightPx).coerceIn(0f, 1f)
                    val maxPanX = (viewportWidthPx * (zoomTuple.first - 1f)).coerceAtLeast(1f)
                    val panXFraction = if (zoomTuple.first > 1.01f) {
                        (-zoomTuple.second / maxPanX).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    viewModel.updateReadingPosition(
                        pageIndex = firstIdx,
                        offsetFraction = offsetFraction,
                        zoomScale = zoomTuple.first,
                        panOffsetXFraction = panXFraction
                    )
                }
            }

            // High-DPI zoom tile trigger debounced at 120ms when zoomScale > 1.25x
            LaunchedEffect(
                viewportWidthPx,
                viewportHeightPx,
                pageWidthPx,
                state.dimensions,
                state.pageLayoutMode,
                progress.darkModeEnabled,
                progress.manualRotation
            ) {
                snapshotFlow {
                    TileTriggerSnapshot(
                        zoomScale = zoomScale,
                        panX = panOffsetX,
                        panY = panOffsetY,
                        firstVisibleIndex = if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                            pagerState.currentPage
                        } else {
                            lazyListState.firstVisibleItemIndex
                        },
                        firstVisibleOffset = if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                            0
                        } else {
                            lazyListState.firstVisibleItemScrollOffset
                        },
                        renderGen = state.renderGeneration,
                        fitFullWidth = fitFullWidthOnTablet,
                        topPaddingPx = with(density) { topContentPadding.roundToPx() }
                    )
                }
                    .debounce(120L)
                    .collectLatest { snap ->
                        if (snap.zoomScale <= 1.25f) {
                            activeZoomTiles = emptyMap()
                            return@collectLatest
                        }

                        data class VisibleTarget(
                            val pageIdx: Int,
                            val pageScreenTop: Float,
                            val basePageW: Float,
                            val basePageH: Float,
                            val leftOffset: Float,
                        )

                        val targets = mutableListOf<VisibleTarget>()
                        if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                            val pageIdx = snap.firstVisibleIndex
                            val dim = state.dimensions.getOrNull(pageIdx) ?: return@collectLatest
                            val fitRatio = min(
                                viewportWidthPx.toFloat() / max(1f, dim.widthPts),
                                viewportHeightPx.toFloat() / max(1f, dim.heightPts)
                            )
                            val basePageW = dim.widthPts * fitRatio
                            val basePageH = dim.heightPts * fitRatio
                            val leftOffset = ((viewportWidthPx - basePageW) / 2f).coerceAtLeast(0f)
                            val bottomPadPx = with(density) { pageSpacingDp.toPx() }
                            val contentH = (viewportHeightPx - snap.topPaddingPx - bottomPadPx).coerceAtLeast(1f)
                            val topOffset = snap.topPaddingPx + ((contentH - basePageH) / 2f).coerceAtLeast(0f)
                            targets += VisibleTarget(
                                pageIdx = pageIdx,
                                pageScreenTop = topOffset * snap.zoomScale + snap.panY,
                                basePageW = basePageW,
                                basePageH = basePageH,
                                leftOffset = leftOffset
                            )
                        } else {
                            val beforePaddingPx = lazyListState.layoutInfo.beforeContentPadding
                            for (item in lazyListState.layoutInfo.visibleItemsInfo) {
                                val dim = state.dimensions.getOrNull(item.index) ?: continue
                                val basePageW = pageWidthPx.toFloat()
                                val basePageH = basePageW / dim.aspectRatio.coerceAtLeast(0.1f)
                                val itemTopInListPx = (item.offset + beforePaddingPx).toFloat()
                                targets += VisibleTarget(
                                    pageIdx = item.index,
                                    pageScreenTop = itemTopInListPx * snap.zoomScale + snap.panY,
                                    basePageW = basePageW,
                                    basePageH = basePageH,
                                    leftOffset = pageLeftOffsetPx
                                )
                            }
                        }

                        if (targets.isEmpty()) return@collectLatest
                        val rendered = mutableMapOf<Int, ZoomTileOverlay>()
                        for (t in targets) {
                            val fullW = (t.basePageW * snap.zoomScale).roundToInt().coerceAtLeast(1)
                            val fullH = (t.basePageH * snap.zoomScale).roundToInt().coerceAtLeast(1)
                            val pageScreenLeft = t.leftOffset * snap.zoomScale + snap.panX

                            val clipLeft = max(0f, pageScreenLeft)
                            val clipTop = max(0f, t.pageScreenTop)
                            val clipRight = min(viewportWidthPx.toFloat(), pageScreenLeft + fullW)
                            val clipBottom = min(viewportHeightPx.toFloat(), t.pageScreenTop + fullH)

                            val tileW = (clipRight - clipLeft).roundToInt()
                            val tileH = (clipBottom - clipTop).roundToInt()
                            if (tileW <= 1 || tileH <= 1) continue

                            val visX = (clipLeft - pageScreenLeft).roundToInt().coerceAtLeast(0)
                            val visY = (clipTop - t.pageScreenTop).roundToInt().coerceAtLeast(0)

                            val bmp = viewModel.renderZoomTileBitmap(
                                pageIndex = t.pageIdx,
                                fullScaledWidthPx = fullW,
                                fullScaledHeightPx = fullH,
                                visibleXPx = visX,
                                visibleYPx = visY,
                                tileWidthPx = tileW,
                                tileHeightPx = tileH,
                                rotation = progress.manualRotation,
                                darkMode = progress.darkModeEnabled
                            )
                            if (bmp != null) {
                                rendered[t.pageIdx] = ZoomTileOverlay(
                                    pageIndex = t.pageIdx,
                                    visibleXPx = visX,
                                    visibleYPx = visY,
                                    tileWidthPx = bmp.width,
                                    tileHeightPx = bmp.height,
                                    fullScaledWidthPx = fullW,
                                    fullScaledHeightPx = fullH,
                                    scaleAtRender = snap.zoomScale,
                                    bitmap = bmp
                                )
                            }
                        }
                        activeZoomTiles = rendered
                    }
            }

            // Non-conflicting pinch-to-zoom + zoomed-pan gesture container:
            // Leaves 1-finger scrolling & long-press text selection untouched when zoomScale <= 1.02f.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(viewportWidthPx, viewportHeightPx, state.pageLayoutMode) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var isPinching = false
                            var isPanning = false
                            var panAccum = Offset.Zero
                            val touchSlop = viewConfiguration.touchSlop
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) {
                                    isPinching = true
                                }
                                val anyConsumed = event.changes.any { it.isConsumed }
                                if (!isPinching && !isPanning && !anyConsumed && zoomScale > 1.02f && !latestDraggingHandle && !latestBlockScrollForNewDragSelection) {
                                    panAccum += event.calculatePan()
                                    if (panAccum.getDistance() > touchSlop) {
                                        isPanning = true
                                    }
                                }
                                val shouldHandlePan = isPinching || (isPanning && !anyConsumed && zoomScale > 1.02f && !latestDraggingHandle && !latestBlockScrollForNewDragSelection)
                                if (shouldHandlePan) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)

                                    if (zoomChange != 1f || (panChange != Offset.Zero && (isPinching || zoomScale > 1.02f))) {
                                        val oldScale = zoomScale
                                        val newScale = (oldScale * zoomChange).coerceIn(1.0f, 5.0f)
                                        val scaleRatio = newScale / oldScale

                                        val maxPanX = (viewportWidthPx * (newScale - 1f)).coerceAtLeast(0f)
                                        val maxPanY = (viewportHeightPx * (newScale - 1f)).coerceAtLeast(0f)

                                        val nextPanX = ((panOffsetX - centroid.x) * scaleRatio + centroid.x + panChange.x)
                                            .coerceIn(-maxPanX, 0f)
                                        val rawPanY = (panOffsetY - centroid.y) * scaleRatio + centroid.y + panChange.y
                                        val nextPanY = rawPanY.coerceIn(-maxPanY, 0f)

                                        val overflowY = rawPanY - nextPanY
                                        if (newScale > 1.02f && abs(overflowY) > 0.5f && state.pageLayoutMode == PageLayoutMode.VERTICAL_SCROLL) {
                                            coroutineScope.launch {
                                                lazyListState.scrollBy(-overflowY / newScale)
                                            }
                                        }

                                        zoomScale = newScale
                                        panOffsetX = nextPanX
                                        panOffsetY = nextPanY

                                        if (newScale <= 1.02f) {
                                            zoomScale = 1.0f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        }

                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) {
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                val startSelectionAt: (Int, Float, Float) -> Unit = { pageIdx, normX, normY ->
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedExistingHighlight = null
                    pendingSelStartNorm = normX to normY
                    pendingSelEndNorm = normX to normY
                    dragAnchorRange = null

                    val cached = activePageText?.takeIf { it.pageIndex.toInt() == pageIdx }
                    if (cached != null && cached.chars.isNotEmpty()) {
                        val startIdx = findNearestCharIndex(cached, normX, normY)
                        if (startIdx >= 0) {
                            val wordRange = expandToWord(cached, startIdx)
                            dragAnchorRange = wordRange
                            activeSelection = buildSelection(cached, pageIdx, wordRange.first, wordRange.second)
                        }
                    } else {
                        coroutineScope.launch {
                            val content = viewModel.getPageTextContent(pageIdx)
                            if (content != null && content.chars.isNotEmpty()) {
                                activePageText = content
                                val sNorm = pendingSelStartNorm ?: (normX to normY)
                                val eNorm = pendingSelEndNorm ?: sNorm
                                val startIdx = findNearestCharIndex(content, sNorm.first, sNorm.second)
                                val endIdx = findNearestCharIndex(content, eNorm.first, eNorm.second)
                                if (startIdx >= 0 && endIdx >= 0) {
                                    val wordRange = expandToWord(content, startIdx)
                                    dragAnchorRange = wordRange
                                    activeSelection = if (startIdx == endIdx) {
                                        buildSelection(content, pageIdx, wordRange.first, wordRange.second)
                                    } else {
                                        val lo = min(wordRange.first, endIdx)
                                        val hi = max(wordRange.second, endIdx)
                                        buildSelection(content, pageIdx, lo, hi)
                                    }
                                }
                            } else {
                                snackbarHostState.showSnackbar(strings.noSelectableText)
                            }
                        }
                    }
                }

                val dragSelectionTo: (Int, Float, Float) -> Unit = { pageIdx, normX, normY ->
                    pendingSelEndNorm = normX to normY
                    val content = activePageText?.takeIf { it.pageIndex.toInt() == pageIdx }
                    val sNorm = pendingSelStartNorm
                    if (content != null && content.chars.isNotEmpty() && sNorm != null) {
                        val anchorRange = dragAnchorRange ?: run {
                            val s = findNearestCharIndex(content, sNorm.first, sNorm.second)
                            if (s >= 0) expandToWord(content, s).also { dragAnchorRange = it } else null
                        }
                        val endIdx = findNearestCharIndex(content, normX, normY)
                        if (anchorRange != null && endIdx >= 0) {
                            val lo = min(anchorRange.first, endIdx)
                            val hi = max(anchorRange.second, endIdx)
                            activeSelection = buildSelection(content, pageIdx, lo, hi)
                        }
                    }
                }

                val dragStartHandleTo: (Int, Float, Float) -> Boolean = { pageIdx, normX, normY ->
                    val cur = activeSelection?.takeIf { it.pageIndex == pageIdx }
                    val content = activePageText?.takeIf { it.pageIndex.toInt() == pageIdx }
                    if (cur != null && content != null && content.chars.isNotEmpty()) {
                        val newStart = findNearestCharIndex(content, normX, normY)
                        if (newStart >= 0) {
                            if (newStart > cur.endListIdx) {
                                activeSelection = buildSelection(content, pageIdx, cur.endListIdx, newStart)
                                false
                            } else {
                                activeSelection = buildSelection(content, pageIdx, newStart, cur.endListIdx)
                                true
                            }
                        } else {
                            true
                        }
                    } else {
                        true
                    }
                }

                val dragEndHandleTo: (Int, Float, Float) -> Boolean = { pageIdx, normX, normY ->
                    val cur = activeSelection?.takeIf { it.pageIndex == pageIdx }
                    val content = activePageText?.takeIf { it.pageIndex.toInt() == pageIdx }
                    if (cur != null && content != null && content.chars.isNotEmpty()) {
                        val newEnd = findNearestCharIndex(content, normX, normY)
                        if (newEnd >= 0) {
                            if (newEnd < cur.startListIdx) {
                                activeSelection = buildSelection(content, pageIdx, newEnd, cur.startListIdx)
                                true
                            } else {
                                activeSelection = buildSelection(content, pageIdx, cur.startListIdx, newEnd)
                                false
                            }
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                if (state.pageLayoutMode == PageLayoutMode.VERTICAL_SCROLL) {
                    LazyColumn(
                        state = lazyListState,
                        userScrollEnabled = zoomScale <= 1.02f && !isDraggingSelectionHandle && !blockScrollForNewDragSelection,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                                translationY = panOffsetY
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                        contentPadding = PaddingValues(top = topContentPadding, bottom = pageSpacingDp),
                        verticalArrangement = Arrangement.spacedBy(pageSpacingDp)
                    ) {
                        itemsIndexed(
                            items = state.dimensions,
                            key = { index, _ -> "${progress.fileHash}_$index" }
                        ) { index, _ ->
                            val dim = state.dimensions[index]
                            val pageHeightPx = (pageWidthPx.toFloat() / dim.aspectRatio.coerceAtLeast(0.1f))
                                .roundToInt()
                                .coerceAtLeast(1)
                            val pageHeightDp = with(density) { pageHeightPx.toDp() }

                            PdfPageItem(
                                pageIndex = index,
                                pageWidthPx = pageWidthPx,
                                pageWidthDp = pageWidthDp,
                                pageHeightPx = pageHeightPx,
                                pageHeightDp = pageHeightDp,
                                renderGeneration = state.renderGeneration,
                                rotation = progress.manualRotation,
                                darkMode = progress.darkModeEnabled,
                                matches = matchesByPage[index].orEmpty(),
                                activeMatchIndex = activeMatch?.matchIndex?.toInt(),
                                highlights = highlightsByPage[index].orEmpty(),
                                selectionRects = if (activeSelection?.pageIndex == index) activeSelection?.rects.orEmpty() else emptyList(),
                                selectionHandles = if (activeSelection?.pageIndex == index) activeSelection?.handles else null,
                                isDirectDragSelection = blockScrollForNewDragSelection,
                                zoomScale = zoomScale,
                                zoomTile = activeZoomTiles[index],
                                viewModel = viewModel,
                                onTapPage = { normX, normY ->
                                    if (activeSelection != null || selectedExistingHighlight != null) {
                                        activeSelection = null
                                        selectedExistingHighlight = null
                                    } else {
                                        val hitHighlight = highlightsByPage[index].orEmpty().firstOrNull { h ->
                                            h.rects.any { r ->
                                                normX in (r.left - 0.015f)..(r.right + 0.015f) &&
                                                    normY in (r.top - 0.015f)..(r.bottom + 0.015f)
                                            }
                                        }
                                        if (hitHighlight != null) {
                                            selectedExistingHighlight = hitHighlight
                                        } else {
                                            isBarsVisible = !isBarsVisible
                                        }
                                    }
                                },
                                onDoubleTapPage = { tapOffset ->
                                    val targetScale = if (zoomScale > 1.25f) 1.0f else 2.5f
                                    if (targetScale == 1.0f) {
                                        zoomScale = 1.0f
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    } else {
                                        val maxX = viewportWidthPx * (targetScale - 1f)
                                        val maxY = viewportHeightPx * (targetScale - 1f)
                                        zoomScale = targetScale
                                        panOffsetX = (tapOffset.x - tapOffset.x * targetScale).coerceIn(-maxX, 0f)
                                        panOffsetY = (tapOffset.y - tapOffset.y * targetScale).coerceIn(-maxY, 0f)
                                    }
                                },
                                onStartSelection = { normX, normY -> startSelectionAt(index, normX, normY) },
                                onDragSelection = { normX, normY -> dragSelectionTo(index, normX, normY) },
                                onDragStartHandle = { normX, normY -> dragStartHandleTo(index, normX, normY) },
                                onDragEndHandle = { normX, normY -> dragEndHandleTo(index, normX, normY) },
                                onHandleDragStateChanged = { dragging -> isDraggingSelectionHandle = dragging },
                                onNewDragSelectionStateChanged = { dragging -> isDraggingNewSelection = dragging }
                            )
                        }
                    }

                    // Fast-scroll draggable scrollbar thumb on the right edge
                    if (totalPages > 1 && zoomScale <= 1.02f) {
                        FastScrollBar(
                            currentPage = currentPageIndex,
                            totalPages = totalPages,
                            viewportHeightPx = viewportHeightPx,
                            onScrubToPage = { targetPage ->
                                coroutineScope.launch {
                                    lazyListState.scrollToItem(targetPage.coerceIn(0, totalPages - 1), 0)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(top = if (isBarsVisible) 72.dp else 16.dp, bottom = 16.dp)
                        )
                    }
                } else {
                    // HORIZONTAL PAGER MODE (Horizontal Page-by-Page Display)
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(top = topContentPadding, bottom = pageSpacingDp),
                        userScrollEnabled = zoomScale <= 1.02f && !isDraggingSelectionHandle && !blockScrollForNewDragSelection,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                                translationY = panOffsetY
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) { index ->
                        val dim = state.dimensions[index]
                        val fitScale = min(
                            viewportWidthPx.toFloat() / max(1f, dim.widthPts),
                            viewportHeightPx.toFloat() / max(1f, dim.heightPts)
                        )
                        val pWidthPx = (dim.widthPts * fitScale).roundToInt().coerceAtLeast(1)
                        val pHeightPx = (dim.heightPts * fitScale).roundToInt().coerceAtLeast(1)
                        val pWidthDp = with(density) { pWidthPx.toDp() }
                        val pHeightDp = with(density) { pHeightPx.toDp() }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PdfPageItem(
                                pageIndex = index,
                                pageWidthPx = pWidthPx,
                                pageWidthDp = pWidthDp,
                                pageHeightPx = pHeightPx,
                                pageHeightDp = pHeightDp,
                                renderGeneration = state.renderGeneration,
                                rotation = progress.manualRotation,
                                darkMode = progress.darkModeEnabled,
                                matches = matchesByPage[index].orEmpty(),
                                activeMatchIndex = activeMatch?.matchIndex?.toInt(),
                                highlights = highlightsByPage[index].orEmpty(),
                                selectionRects = if (activeSelection?.pageIndex == index) activeSelection?.rects.orEmpty() else emptyList(),
                                selectionHandles = if (activeSelection?.pageIndex == index) activeSelection?.handles else null,
                                isDirectDragSelection = blockScrollForNewDragSelection,
                                zoomScale = zoomScale,
                                zoomTile = activeZoomTiles[index],
                                viewModel = viewModel,
                                onTapPage = { normX, normY ->
                                    if (activeSelection != null || selectedExistingHighlight != null) {
                                        activeSelection = null
                                        selectedExistingHighlight = null
                                    } else {
                                        val hitHighlight = highlightsByPage[index].orEmpty().firstOrNull { h ->
                                            h.rects.any { r ->
                                                normX in (r.left - 0.015f)..(r.right + 0.015f) &&
                                                    normY in (r.top - 0.015f)..(r.bottom + 0.015f)
                                            }
                                        }
                                        if (hitHighlight != null) {
                                            selectedExistingHighlight = hitHighlight
                                        } else {
                                            isBarsVisible = !isBarsVisible
                                        }
                                    }
                                },
                                onDoubleTapPage = { tapOffset ->
                                    val targetScale = if (zoomScale > 1.25f) 1.0f else 2.5f
                                    if (targetScale == 1.0f) {
                                        zoomScale = 1.0f
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    } else {
                                        val maxX = viewportWidthPx * (targetScale - 1f)
                                        val maxY = viewportHeightPx * (targetScale - 1f)
                                        zoomScale = targetScale
                                        panOffsetX = (tapOffset.x - tapOffset.x * targetScale).coerceIn(-maxX, 0f)
                                        panOffsetY = (tapOffset.y - tapOffset.y * targetScale).coerceIn(-maxY, 0f)
                                    }
                                },
                                onStartSelection = { normX, normY -> startSelectionAt(index, normX, normY) },
                                onDragSelection = { normX, normY -> dragSelectionTo(index, normX, normY) },
                                onDragStartHandle = { normX, normY -> dragStartHandleTo(index, normX, normY) },
                                onDragEndHandle = { normX, normY -> dragEndHandleTo(index, normX, normY) },
                                onHandleDragStateChanged = { dragging -> isDraggingSelectionHandle = dragging },
                                onNewDragSelectionStateChanged = { dragging -> isDraggingNewSelection = dragging }
                            )
                        }
                    }
                }
            }

            // Collapsible Top App Bar + Search Bar (toggled on single tap, unaffected by scroll)
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            val currentPage = (currentPageIndex + 1).coerceIn(1, totalPages)
                            Text(
                                text = "Page $currentPage / $totalPages",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showJumpDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.setSearchVisible(!state.searchVisible) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(progress) }) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                        tint = if (isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleGlobalDarkMode() }) {
                                    Icon(
                                        imageVector = if (progress.darkModeEnabled) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Toggle night mode"
                                    )
                                }
                                Box {
                                    IconButton(onClick = { overflowExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = strings.options)
                                    }
                                    DropdownMenu(
                                        expanded = overflowExpanded,
                                        onDismissRequest = { overflowExpanded = false },
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isSelectionMode) {
                                                        strings.exitTextSelection
                                                    } else {
                                                        strings.selectText
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.BorderColor, contentDescription = null)
                                            },
                                            onClick = {
                                                overflowExpanded = false
                                                isSelectionMode = !isSelectionMode
                                                if (!isSelectionMode) {
                                                    activeSelection = null
                                                } else {
                                                    isBarsVisible = false
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (state.pageLayoutMode == PageLayoutMode.VERTICAL_SCROLL) {
                                                        strings.horizontalPages
                                                    } else {
                                                        strings.verticalScroll
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (state.pageLayoutMode == PageLayoutMode.VERTICAL_SCROLL) {
                                                        Icons.Default.SwapHoriz
                                                    } else {
                                                        Icons.Default.SwapVert
                                                    },
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                overflowExpanded = false
                                                viewModel.togglePageLayoutMode()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(strings.rotate90) },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                                            },
                                            onClick = {
                                                overflowExpanded = false
                                                viewModel.rotatePage90()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (orientationMode == OrientationMode.PORTRAIT) {
                                                        strings.switchToHorizontalScreen
                                                    } else {
                                                        strings.switchToVerticalScreen
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.ScreenRotation, contentDescription = null)
                                            },
                                            onClick = {
                                                overflowExpanded = false
                                                showOrientationPrompt = false
                                                viewModel.cycleOrientationMode()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(strings.editPdf) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                            },
                                            onClick = {
                                                overflowExpanded = false
                                                onEditPdf(currentPageIndex)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(strings.jumpToPage) },
                                            leadingIcon = {
                                                Icon(Icons.Default.FindInPage, contentDescription = null)
                                            },
                                            onClick = {
                                                overflowExpanded = false
                                                showJumpDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(strings.sharePdf) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Share, contentDescription = null)
                                            },
                                            onClick = {
                                                overflowExpanded = false
                                                viewModel.prepareSharePdf { shareUri, fileName ->
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                                                        clipData = ClipData.newRawUri(fileName, shareUri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    val chooser = Intent.createChooser(sendIntent, strings.sharePdf).apply {
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    try {
                                                        context.startActivity(chooser)
                                                    } catch (_: Exception) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(strings.noShareApp)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        if (isWideScreen) {
                                            DropdownMenuItem(
                                                text = { Text(if (fitFullWidthOnTablet) strings.readableWidth else strings.fitFullWidth) },
                                                leadingIcon = { Icon(Icons.Default.FitScreen, contentDescription = null) },
                                                onClick = {
                                                    overflowExpanded = false
                                                    fitFullWidthOnTablet = !fitFullWidthOnTablet
                                                }
                                            )
                                        }
                                    }
                                }
                        }

                        AnimatedVisibility(visible = state.searchVisible) {
                            SearchBarRow(
                                query = state.searchQuery,
                                matchCount = state.searchMatches.size,
                                activeIndex = state.activeMatchIndex,
                                isSearching = state.isSearching,
                                isCompact = isCompactPhone,
                                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                onPrevious = {
                                    val match = viewModel.previousSearchMatch()
                                    if (match != null) {
                                        val pageIdx = match.pageIndex.toInt().coerceIn(0, totalPages - 1)
                                        coroutineScope.launch {
                                            if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                                                pagerState.animateScrollToPage(pageIdx)
                                            } else {
                                                val targetW = if (fitFullWidthOnTablet) screenWidthPx else estimatedPageWidthPx
                                                scrollToMatch(lazyListState, state.dimensions, match, targetW)
                                            }
                                        }
                                    }
                                },
                                onNext = {
                                    val match = viewModel.nextSearchMatch()
                                    if (match != null) {
                                        val pageIdx = match.pageIndex.toInt().coerceIn(0, totalPages - 1)
                                        coroutineScope.launch {
                                            if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                                                pagerState.animateScrollToPage(pageIdx)
                                            } else {
                                                val targetW = if (fitFullWidthOnTablet) screenWidthPx else estimatedPageWidthPx
                                                scrollToMatch(lazyListState, state.dimensions, match, targetW)
                                            }
                                        }
                                    }
                                },
                                onClose = { viewModel.setSearchVisible(false) }
                            )
                        }
                    }
                }
            }

            // Floating "Selection mode" pill when direct-drag text selection is active
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = if (isBarsVisible) 76.dp else 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable {
                        isSelectionMode = false
                        activeSelection = null
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BorderColor,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Drag text to select · Done",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit selection mode",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bottom-right floating orientation prompt (auto-hides after 5s) when physical device orientation changes
            AnimatedVisibility(
                visible = showOrientationPrompt &&
                    detectedDeviceOrientation != orientationMode &&
                    activeSelection == null &&
                    selectedExistingHighlight == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 20.dp, bottom = 24.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        showOrientationPrompt = false
                        viewModel.setOrientationMode(detectedDeviceOrientation)
                    },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Rotate screen to match device"
                    )
                }
            }

            // Floating Text Selection Toolbar with live selected-text preview, color picker & unhighlight
            val sel = activeSelection
            if (sel != null) {
                val overlappingHighlights = highlightsByPage[sel.pageIndex].orEmpty().filter { hl ->
                    val hStart = hl.startChar.toInt()
                    val hEnd = hl.endChar.toInt()
                    hStart <= sel.endCharIndex && hEnd >= sel.startCharIndex
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .widthIn(max = 460.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HIGHLIGHT_COLOR_OPTIONS.forEach { colorArgb ->
                                val isSelected = colorArgb == selectedHighlightColorArgb
                                val swatchColor = Color(colorArgb.toInt()).copy(alpha = 0.92f)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { selectedHighlightColorArgb = colorArgb },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected highlight color",
                                            tint = Color.Black.copy(alpha = 0.8f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = {
                                clipboardManager.setText(AnnotatedString(sel.selectedText))
                                activeSelection = null
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(strings.copiedToClipboard)
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.copy)
                            }
                            TextButton(onClick = {
                                viewModel.addHighlight(
                                    pageIndex = sel.pageIndex,
                                    startChar = sel.startCharIndex,
                                    endChar = sel.endCharIndex,
                                    rects = sel.rects,
                                    selectedText = sel.selectedText,
                                    colorArgb = selectedHighlightColorArgb.toUInt()
                                )
                                activeSelection = null
                            }) {
                                Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.highlight)
                            }
                            if (overlappingHighlights.isNotEmpty()) {
                                TextButton(onClick = {
                                    viewModel.unhighlightRange(
                                        pageIndex = sel.pageIndex,
                                        selStartChar = sel.startCharIndex,
                                        selEndChar = sel.endCharIndex
                                    )
                                    activeSelection = null
                                }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.removeHighlight)
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { activeSelection = null }) {
                                Icon(Icons.Default.Close, contentDescription = strings.close)
                            }
                        }
                    }
                }
            }

            // Floating Unhighlight & Recolor Toolbar when user taps an existing highlight
            val existingHl = selectedExistingHighlight
            if (existingHl != null && sel == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .widthIn(max = 460.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HIGHLIGHT_COLOR_OPTIONS.forEach { colorArgb ->
                                val isCurrent = colorArgb.toUInt() == existingHl.colorArgb
                                val swatchColor = Color(colorArgb.toInt()).copy(alpha = 0.92f)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isCurrent) 2.5.dp else 1.dp,
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedHighlightColorArgb = colorArgb
                                            viewModel.recolorHighlight(existingHl, colorArgb.toUInt())
                                            selectedExistingHighlight = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Current highlight color",
                                            tint = Color.Black.copy(alpha = 0.8f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (existingHl.selectedText.isNotBlank()) {
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(existingHl.selectedText))
                                    selectedExistingHighlight = null
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(strings.copiedToClipboard)
                                    }
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.copy)
                                }
                            }
                            TextButton(onClick = {
                                viewModel.removeHighlight(existingHl.id)
                                selectedExistingHighlight = null
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.removeHighlight)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectedExistingHighlight = null }) {
                                Icon(Icons.Default.Close, contentDescription = strings.dismiss)
                            }
                        }
                    }
                }
            }

            if (isOpeningDocument || isSharingDocument) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(
                                text = if (isSharingDocument) "Preparing PDF to share..." else "Opening PDF...",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            currentPage = (currentPageIndex + 1).coerceIn(1, totalPages),
            totalPages = totalPages,
            onDismiss = { showJumpDialog = false },
            onJumpToPage = { pageOneBased ->
                showJumpDialog = false
                val targetZero = (pageOneBased - 1).coerceIn(0, totalPages - 1)
                coroutineScope.launch {
                    if (state.pageLayoutMode == PageLayoutMode.HORIZONTAL_PAGER) {
                        pagerState.scrollToPage(targetZero)
                    } else {
                        lazyListState.scrollToItem(targetZero, 0)
                    }
                }
            }
        )
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    pageWidthPx: Int,
    pageWidthDp: Dp,
    pageHeightPx: Int,
    pageHeightDp: Dp,
    renderGeneration: Long,
    rotation: uniffi.pdf_core.PageRotation,
    darkMode: Boolean,
    matches: List<SearchMatch>,
    activeMatchIndex: Int?,
    highlights: List<HighlightRecord>,
    selectionRects: List<NormalizedRect>,
    selectionHandles: SelectionHandleBounds?,
    isDirectDragSelection: Boolean,
    zoomScale: Float,
    zoomTile: ZoomTileOverlay?,
    viewModel: PdfViewModel,
    onTapPage: (normX: Float, normY: Float) -> Unit,
    onDoubleTapPage: (Offset) -> Unit,
    onStartSelection: (normX: Float, normY: Float) -> Unit,
    onDragSelection: (normX: Float, normY: Float) -> Unit,
    onDragStartHandle: (normX: Float, normY: Float) -> Boolean,
    onDragEndHandle: (normX: Float, normY: Float) -> Boolean,
    onHandleDragStateChanged: (Boolean) -> Unit,
    onNewDragSelectionStateChanged: (Boolean) -> Unit,
) {
    var baseBitmap by remember(pageIndex, pageWidthPx, pageHeightPx, renderGeneration, rotation, darkMode) {
        mutableStateOf<Bitmap?>(null)
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestSelectionHandles by rememberUpdatedState(selectionHandles)

    // Cancel pending render immediately when item leaves viewport (OOM safeguard)
    DisposableEffect(pageIndex, pageWidthPx, pageHeightPx, renderGeneration, rotation, darkMode) {
        val job = scope.launch {
            val bmp = viewModel.renderBasePageBitmap(
                pageIndex = pageIndex,
                targetWidthPx = pageWidthPx,
                targetHeightPx = pageHeightPx,
                rotation = rotation,
                darkMode = darkMode
            )
            baseBitmap = bmp
        }
        onDispose {
            job.cancel()
            val old = baseBitmap
            baseBitmap = null
            old?.recycle()
        }
    }

    Box(
        modifier = Modifier
            .width(pageWidthDp)
            .height(pageHeightDp)
            .clipToBounds()
            .background(if (darkMode) Color(0xFF141414) else Color.White)
            .pointerInput(pageIndex, pageWidthPx, pageHeightPx) {
                detectTapGestures(
                    onTap = { offset ->
                        val nx = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val ny = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onTapPage(nx, ny)
                    },
                    onDoubleTap = { offset ->
                        onDoubleTapPage(offset)
                    }
                )
            }
            .pointerInput(pageIndex, pageWidthPx, pageHeightPx, isDirectDragSelection) {
                if (isDirectDragSelection) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onNewDragSelectionStateChanged(true)
                            val nx = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val ny = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onStartSelection(nx, ny)
                        },
                        onDragEnd = { onNewDragSelectionStateChanged(false) },
                        onDragCancel = { onNewDragSelectionStateChanged(false) },
                        onDrag = { change, _ ->
                            change.consume()
                            val nx = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val ny = (change.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onDragSelection(nx, ny)
                        }
                    )
                } else {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val nx = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val ny = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onStartSelection(nx, ny)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val nx = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val ny = (change.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onDragSelection(nx, ny)
                        }
                    )
                }
            }
    ) {
        baseBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Sharp high-DPI cropped viewport tile drawn seamlessly over base bitmap
        if (zoomTile != null && !zoomTile.bitmap.isRecycled) {
            val scaleXRatio = pageWidthPx.toFloat() / zoomTile.fullScaledWidthPx.coerceAtLeast(1)
            val scaleYRatio = pageHeightPx.toFloat() / zoomTile.fullScaledHeightPx.coerceAtLeast(1)
            val offsetX = (zoomTile.visibleXPx * scaleXRatio).roundToInt()
            val offsetY = (zoomTile.visibleYPx * scaleYRatio).roundToInt()
            val widthDp = with(density) { (zoomTile.tileWidthPx * scaleXRatio).toDp() }
            val heightDp = with(density) { (zoomTile.tileHeightPx * scaleYRatio).toDp() }

            Image(
                bitmap = zoomTile.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset(offsetX, offsetY) }
                    .size(width = widthDp, height = heightDp)
            )
        }

        // Permanent user highlights + Search match highlights + Active text selection + Handles
        if (highlights.isNotEmpty() || matches.isNotEmpty() || selectionRects.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 2.dp.toPx()

                for (hl in highlights) {
                    val color = Color(hl.colorArgb.toInt())
                    for (rect in hl.rects) {
                        drawRect(
                            color = color,
                            topLeft = Offset(rect.left * size.width, rect.top * size.height),
                            size = Size(
                                (rect.right - rect.left).coerceAtLeast(0f) * size.width,
                                (rect.bottom - rect.top).coerceAtLeast(0f) * size.height
                            )
                        )
                    }
                }

                for (match in matches) {
                    val isActive = match.matchIndex.toInt() == activeMatchIndex
                    val fillColor = if (isActive) Color(0x88FF9800) else Color(0x55FFEB3B)
                    for (rect in match.rects) {
                        val left = rect.left * size.width
                        val top = rect.top * size.height
                        val w = (rect.right - rect.left).coerceAtLeast(0f) * size.width
                        val h = (rect.bottom - rect.top).coerceAtLeast(0f) * size.height
                        drawRect(
                            color = fillColor,
                            topLeft = Offset(left, top),
                            size = Size(w, h)
                        )
                        if (isActive) {
                            drawRect(
                                color = Color(0xFFFF5722),
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Stroke(width = strokePx)
                            )
                        }
                    }
                }

                if (selectionRects.isNotEmpty()) {
                    val handleColor = Color(0xFF1976D2)
                    val invScale = 1f / zoomScale.coerceAtLeast(1f)
                    val handleRadius = 4.5.dp.toPx() * invScale
                    val barWidth = 1.75.dp.toPx() * invScale

                    for (rect in selectionRects) {
                        drawRect(
                            color = Color(0x552196F3),
                            topLeft = Offset(rect.left * size.width, rect.top * size.height),
                            size = Size(
                                (rect.right - rect.left).coerceAtLeast(0f) * size.width,
                                (rect.bottom - rect.top).coerceAtLeast(0f) * size.height
                            )
                        )
                    }

                    val handles = selectionHandles ?: SelectionHandleBounds(
                        startX = selectionRects.first().left,
                        startTop = selectionRects.first().top,
                        startBottom = selectionRects.first().bottom,
                        endX = selectionRects.last().right,
                        endTop = selectionRects.last().top,
                        endBottom = selectionRects.last().bottom
                    )

                    // Start cursor handle
                    val sx = handles.startX * size.width
                    val syTop = handles.startTop * size.height
                    val syBot = handles.startBottom * size.height
                    drawLine(
                        color = handleColor,
                        start = Offset(sx, syTop),
                        end = Offset(sx, syBot),
                        strokeWidth = barWidth
                    )
                    drawCircle(
                        color = handleColor,
                        radius = handleRadius,
                        center = Offset(sx, syTop - handleRadius)
                    )

                    // End cursor handle
                    val ex = handles.endX * size.width
                    val eyTop = handles.endTop * size.height
                    val eyBot = handles.endBottom * size.height
                    drawLine(
                        color = handleColor,
                        start = Offset(ex, eyTop),
                        end = Offset(ex, eyBot),
                        strokeWidth = barWidth
                    )
                    drawCircle(
                        color = handleColor,
                        radius = handleRadius,
                        center = Offset(ex, eyBot + handleRadius)
                    )
                }
            }
        }

        // Dedicated topmost touch layer for dragging selection handles without cancelling selection
        if (selectionHandles != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageIndex, pageWidthPx, pageHeightPx, zoomScale) {
                        val invScale = 1f / zoomScale.coerceAtLeast(1f)
                        val handleRadiusPx = 4.5.dp.toPx() * invScale
                        val hitRadiusPx = 28.dp.toPx() * invScale
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val handles = latestSelectionHandles ?: return@awaitEachGesture
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)

                            val sx = handles.startX * w
                            val syLine = (handles.startTop + handles.startBottom) * 0.5f * h
                            val sCircle = Offset(sx, handles.startTop * h - handleRadiusPx)

                            val ex = handles.endX * w
                            val eyLine = (handles.endTop + handles.endBottom) * 0.5f * h
                            val eCircle = Offset(ex, handles.endBottom * h + handleRadiusPx)

                            val distStart = min(
                                hypot(down.position.x - sCircle.x, down.position.y - sCircle.y),
                                hypot(down.position.x - sx, down.position.y - syLine)
                            )
                            val distEnd = min(
                                hypot(down.position.x - eCircle.x, down.position.y - eCircle.y),
                                hypot(down.position.x - ex, down.position.y - eyLine)
                            )

                            if (min(distStart, distEnd) > hitRadiusPx) {
                                return@awaitEachGesture
                            }

                            down.consume()
                            onHandleDragStateChanged(true)
                            try {
                                var draggingStart = distStart <= distEnd
                                val anchorLineX = if (draggingStart) sx else ex
                                val anchorLineY = if (draggingStart) syLine else eyLine
                                val grabOffsetX = anchorLineX - down.position.x
                                val grabOffsetY = anchorLineY - down.position.y

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    if (change.positionChanged()) {
                                        change.consume()
                                        val nx = ((change.position.x + grabOffsetX) / w).coerceIn(0f, 1f)
                                        val ny = ((change.position.y + grabOffsetY) / h).coerceIn(0f, 1f)
                                        draggingStart = if (draggingStart) {
                                            onDragStartHandle(nx, ny)
                                        } else {
                                            onDragEndHandle(nx, ny)
                                        }
                                    }
                                }
                            } finally {
                                onHandleDragStateChanged(false)
                            }
                        }
                    }
            )
        }
    }
}

