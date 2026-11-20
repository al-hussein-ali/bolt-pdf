package com.bolt.pdf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bolt.pdf.PdfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.pdf_core.PageRotation
import kotlin.math.roundToInt


@Composable
internal fun TextEditorWorkspace(
    page: EditorPageItem,
    pageNumber: Int,
    totalPages: Int,
    activeTextBoxId: String?,
    viewModel: PdfViewModel,
    onSelectTextBox: (String?) -> Unit,
    onAddTextBox: (EditorTextBox) -> Unit,
    onUpdateTextBox: (EditorTextBox, Boolean) -> Unit,
    onDeleteTextBox: (String) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val activeBox = page.textEdits.firstOrNull { it.id == activeTextBoxId }
    var showFontMenu by remember { mutableStateOf(false) }
    var isPlacingNewBox by remember(page.id) { mutableStateOf(false) }
    var isMovingActiveBox by remember(activeTextBoxId) { mutableStateOf(false) }
    var canvasZoom by remember(page.id) { mutableFloatStateOf(1f) }

    Column(modifier = modifier.fillMaxSize()) {
        if (isPlacingNewBox || isMovingActiveBox || activeBox == null) {
            Surface(
                color = if (isPlacingNewBox || isMovingActiveBox) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPlacingNewBox || isMovingActiveBox) {
                        strings.tapToPlaceHint
                    } else {
                        strings.tapTextToEditHint
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlacingNewBox || isMovingActiveBox) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Scrollable + pinch-zoomable page canvas area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            val hPaddingPx = with(density) { 32.dp.roundToPx() }
            val basePageWidthPx = (constraints.maxWidth - hPaddingPx).coerceAtLeast(1)
            val aspectRatio = page.effectiveAspectRatio()
            val pageWidthPx = (basePageWidthPx * canvasZoom).roundToInt().coerceAtLeast(1)
            val pageHeightPx = (pageWidthPx / aspectRatio).roundToInt().coerceAtLeast(1)
            val pageWidthDp = with(density) { pageWidthPx.toDp() }
            val pageHeightDp = with(density) { pageHeightPx.toDp() }
            val ptsToPx = pageHeightPx.toFloat() / page.effectiveHeightPts().coerceAtLeast(1f)
            val hScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        canvasZoom = (canvasZoom * zoomChange).coerceIn(1f, 3f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .horizontalScroll(hScrollState, enabled = canvasZoom > 1.02f)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
            Surface(
                shadowElevation = 4.dp,
                color = Color.White,
                modifier = Modifier
                    .width(pageWidthDp)
                    .height(pageHeightDp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(page.id, page.textEdits, isPlacingNewBox, isMovingActiveBox, activeBox?.id, pageWidthPx, pageHeightPx) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / pageWidthPx.toFloat()).coerceIn(0f, 1f)
                                val ny = (offset.y / pageHeightPx.toFloat()).coerceIn(0f, 1f)

                                if (isPlacingNewBox) {
                                    isPlacingNewBox = false
                                    val boxW = 0.65f
                                    val leftPos = (nx - boxW * 0.25f).coerceIn(0.02f, 1f - boxW - 0.02f)
                                    val topPos = (ny - 0.02f).coerceIn(0.02f, 0.88f)
                                    val newBox = EditorTextBox(
                                        text = "",
                                        left = leftPos,
                                        top = topPos,
                                        right = (leftPos + boxW).coerceAtMost(0.98f),
                                        bottom = (topPos + 0.08f).coerceAtMost(0.96f),
                                        coverBackground = false,
                                        fontSizePts = 16f,
                                        colorArgb = 0xFF111111L,
                                        isBold = false,
                                    )
                                    onAddTextBox(newBox)
                                    return@detectTapGestures
                                }

                                if (isMovingActiveBox && activeBox != null) {
                                    isMovingActiveBox = false
                                    val w = activeBox.right - activeBox.left
                                    val h = activeBox.bottom - activeBox.top
                                    val newLeft = (nx - w * 0.5f).coerceIn(0f, (1f - w).coerceAtLeast(0f))
                                    val newTop = (ny - h * 0.5f).coerceIn(0f, (1f - h).coerceAtLeast(0f))
                                    onUpdateTextBox(
                                        activeBox.copy(
                                            left = newLeft,
                                            top = newTop,
                                            right = (newLeft + w).coerceAtMost(1f),
                                            bottom = (newTop + h).coerceAtMost(1f)
                                        ),
                                        true
                                    )
                                    return@detectTapGestures
                                }

                                val hitExisting = page.textEdits.lastOrNull { box ->
                                    nx in (box.left - 0.02f)..(box.right + 0.02f) &&
                                        ny in (box.top - 0.02f)..(box.bottom + 0.05f)
                                }
                                if (hitExisting != null) {
                                    onSelectTextBox(hitExisting.id)
                                } else {
                                    onSelectTextBox(null)
                                }
                            }
                        }
                ) {
                    EditorPagePreviewBitmap(
                        page = page,
                        targetWidthPx = pageWidthPx.coerceAtMost(1440),
                        targetHeightPx = pageHeightPx.coerceAtMost(2048),
                        viewModel = viewModel,
                        context = context,
                        modifier = Modifier.fillMaxSize()
                    )

                    for (box in page.textEdits) {
                        val isActive = box.id == activeTextBoxId
                        val bx = (box.left * pageWidthPx).roundToInt()
                        val by = (box.top * pageHeightPx).roundToInt()
                        val bwPx = ((box.right - box.left) * pageWidthPx).coerceAtLeast(64f)
                        val bwDp = with(density) { bwPx.toDp() }
                        val fontSp = with(density) { (box.fontSizePts * ptsToPx).toSp() }

                        val focusRequester = remember(box.id) { FocusRequester() }

                        LaunchedEffect(isActive) {
                            if (isActive) {
                                delay(80)
                                runCatching { scrollState.animateScrollTo((by - 120).coerceAtLeast(0)) }
                                runCatching { focusRequester.requestFocus() }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset { IntOffset(bx, by) }
                                .width(bwDp)
                                .wrapContentHeight()
                        ) {
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .pointerInput(box.id, pageWidthPx, pageHeightPx) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val dx = dragAmount.x / pageWidthPx.toFloat()
                                                val dy = dragAmount.y / pageHeightPx.toFloat()
                                                val w = box.right - box.left
                                                val h = box.bottom - box.top
                                                val newLeft = (box.left + dx).coerceIn(0f, (1f - w).coerceAtLeast(0f))
                                                val newTop = (box.top + dy).coerceIn(0f, (1f - h).coerceAtLeast(0f))
                                                onUpdateTextBox(
                                                    box.copy(
                                                        left = newLeft,
                                                        top = newTop,
                                                        right = (newLeft + w).coerceAtMost(1f),
                                                        bottom = (newTop + h).coerceAtMost(1f)
                                                    ),
                                                    false
                                                )
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenWith,
                                            contentDescription = strings.moveTextBox,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = strings.moveTextBox,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .background(
                                        color = if (box.coverBackground) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        width = if (isActive) 1.75.dp else 0.75.dp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else Color(0x661976D2),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                val textStyle = TextStyle(
                                    color = Color(box.colorArgb),
                                    fontSize = fontSp,
                                    fontWeight = if (box.isBold) FontWeight.Bold else FontWeight.Normal,
                                    lineHeight = fontSp * 1.25f,
                                    textDirection = TextDirection.Content
                                )
                                if (isActive) {
                                    BasicTextField(
                                        value = box.text,
                                        onValueChange = { newText ->
                                            onUpdateTextBox(box.copy(text = newText), false)
                                        },
                                        textStyle = textStyle,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        decorationBox = { innerTextField ->
                                            if (box.text.isEmpty()) {
                                                Text(
                                                    text = strings.typeTextHint,
                                                    style = textStyle.copy(color = Color.Gray)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                } else {
                                    Text(
                                        text = box.text.ifEmpty { strings.typeTextHint },
                                        style = textStyle.copy(
                                            color = if (box.text.isEmpty()) Color.Gray else Color(box.colorArgb)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectTextBox(box.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        // Bottom bar: Two-row clean formatting toolbar when active, otherwise page navigation + Add Text
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (activeBox != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Font size, Bold, White fill, Delete, Done
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            onUpdateTextBox(
                                                activeBox.copy(fontSizePts = (activeBox.fontSizePts - 2f).coerceAtLeast(8f)),
                                                true
                                            )
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Box {
                                        Text(
                                            text = "${activeBox.fontSizePts.roundToInt()}pt",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clickable { showFontMenu = true }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                        DropdownMenu(
                                            expanded = showFontMenu,
                                            onDismissRequest = { showFontMenu = false }
                                        ) {
                                            FONT_SIZE_PRESETS.forEach { preset ->
                                                DropdownMenuItem(
                                                    text = { Text("${preset.roundToInt()} pt") },
                                                    onClick = {
                                                        showFontMenu = false
                                                        onUpdateTextBox(activeBox.copy(fontSizePts = preset), true)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdateTextBox(
                                                activeBox.copy(fontSizePts = (activeBox.fontSizePts + 2f).coerceAtMost(72f)),
                                                true
                                            )
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            FilterChip(
                                selected = activeBox.isBold,
                                onClick = { onUpdateTextBox(activeBox.copy(isBold = !activeBox.isBold), true) },
                                label = {
                                    Icon(
                                        Icons.Default.FormatBold,
                                        contentDescription = strings.bold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )

                            FilterChip(
                                selected = activeBox.coverBackground,
                                onClick = { onUpdateTextBox(activeBox.copy(coverBackground = !activeBox.coverBackground), true) },
                                label = {
                                    Icon(
                                        Icons.Default.FormatColorFill,
                                        contentDescription = strings.coverBackground,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )

                            FilterChip(
                                selected = isMovingActiveBox,
                                onClick = { isMovingActiveBox = !isMovingActiveBox },
                                label = {
                                    Icon(
                                        Icons.Default.OpenWith,
                                        contentDescription = strings.moveTextBox,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onDeleteTextBox(activeBox.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = strings.delete,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            FilledTonalButton(
                                onClick = { onSelectTextBox(null) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.done, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Row 2: Clean, well-spaced color swatches
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EDITOR_TEXT_COLORS.forEach { argb ->
                            val selected = activeBox.colorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable { onUpdateTextBox(activeBox.copy(colorArgb = argb), true) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrevPage, enabled = pageNumber > 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = strings.previous)
                        }
                        Text(
                            text = strings.pageOfTotal(pageNumber, totalPages),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onNextPage, enabled = pageNumber < totalPages) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = strings.next)
                        }
                        if (canvasZoom > 1.02f) {
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = true,
                                onClick = { canvasZoom = 1f },
                                label = { Text("${String.format(java.util.Locale.US, "%.1f", canvasZoom)}x") }
                            )
                        }
                    }

                    Button(
                        onClick = { isPlacingNewBox = !isPlacingNewBox },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlacingNewBox) strings.cancel else strings.addText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
