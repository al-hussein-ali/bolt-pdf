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

private enum class EditorTab {
    EDIT_TEXT,
    ORGANIZE_PAGES
}

private val EDITOR_TEXT_COLORS = listOf(
    0xFF111111L, // Black
    0xFF4B5563L, // Slate Gray
    0xFF1D4ED8L, // Blue
    0xFFDC2626L, // Red
    0xFF15803DL, // Green
    0xFFD97706L, // Amber
    0xFF7C3AEDL, // Purple
)

private val FONT_SIZE_PRESETS = listOf(10f, 12f, 14f, 16f, 18f, 22f, 28f, 36f)

private fun rotationFromDegrees(deg: Int): PageRotation = when (((deg % 360) + 360) % 360) {
    90 -> PageRotation.DEG90
    180 -> PageRotation.DEG180
    270 -> PageRotation.DEG270
    else -> PageRotation.DEG0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    session: PdfEditorSession,
    viewModel: PdfViewModel,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onSaveAsNewFile: (pages: List<EditorPageItem>, fileName: String, outputUri: Uri) -> Unit,
    onReplaceCurrentFile: (pages: List<EditorPageItem>, newFileName: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pages by remember(session.fileHash, session.sourceUri) {
        mutableStateOf(
            session.dimensions.mapIndexed { idx, dim ->
                EditorPageItem(
                    kind = EditorPageKind.Original(
                        pageIndex = idx,
                        widthPts = dim.widthPts.coerceAtLeast(72f),
                        heightPts = dim.heightPts.coerceAtLeast(72f)
                    )
                )
            }
        )
    }
    var undoStack by remember(session.fileHash, session.sourceUri) {
        mutableStateOf<List<List<EditorPageItem>>>(emptyList())
    }
    var redoStack by remember(session.fileHash, session.sourceUri) {
        mutableStateOf<List<List<EditorPageItem>>>(emptyList())
    }
    var activeTab by remember(session.fileHash, session.sourceUri) {
        mutableStateOf(EditorTab.EDIT_TEXT)
    }
    var currentPageIdx by remember(session.fileHash, session.sourceUri) {
        mutableIntStateOf(session.initialPageIndex.coerceIn(0, (session.dimensions.size - 1).coerceAtLeast(0)))
    }
    var selectedOrganizerIdx by remember(session.fileHash, session.sourceUri) {
        mutableIntStateOf(session.initialPageIndex.coerceIn(0, (session.dimensions.size - 1).coerceAtLeast(0)))
    }
    var activeTextBoxId by remember(session.fileHash, session.sourceUri) {
        mutableStateOf<String?>(null)
    }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var swapSourceIdx by remember { mutableStateOf<Int?>(null) }
    var pendingNewFileName by remember { mutableStateOf<String?>(null) }
    var pendingNewFilePages by remember { mutableStateOf<List<EditorPageItem>?>(null) }

    val hasUnsavedChanges = undoStack.isNotEmpty()

    fun commitPagesChange(newPages: List<EditorPageItem>) {
        undoStack = (undoStack + listOf(pages)).takeLast(30)
        redoStack = emptyList()
        pages = newPages
        if (currentPageIdx >= newPages.size) {
            currentPageIdx = (newPages.size - 1).coerceAtLeast(0)
        }
        if (selectedOrganizerIdx >= newPages.size) {
            selectedOrganizerIdx = (newPages.size - 1).coerceAtLeast(0)
        }
    }

    fun updateCurrentPage(transform: (EditorPageItem) -> EditorPageItem, pushUndo: Boolean = true) {
        val idx = currentPageIdx
        if (idx !in pages.indices) return
        val updated = pages.toMutableList()
        updated[idx] = transform(updated[idx])
        if (pushUndo) {
            commitPagesChange(updated)
        } else {
            pages = updated
        }
    }

    fun cleanupEmptyAndDeselect() {
        val id = activeTextBoxId ?: return
        val currentPage = pages.getOrNull(currentPageIdx)
        val box = currentPage?.textEdits?.firstOrNull { it.id == id }
        if (box != null && box.text.isBlank()) {
            // Drop empty placeholder without polluting undo history
            val cleaned = pages.toMutableList()
            cleaned[currentPageIdx] = currentPage.copy(
                textEdits = currentPage.textEdits.filterNot { it.id == id }
            )
            pages = cleaned
        }
        activeTextBoxId = null
    }

    fun performUndo() {
        val prev = undoStack.lastOrNull() ?: return
        redoStack = (redoStack + listOf(pages)).takeLast(30)
        undoStack = undoStack.dropLast(1)
        pages = prev
        activeTextBoxId = null
        currentPageIdx = currentPageIdx.coerceIn(0, (prev.size - 1).coerceAtLeast(0))
        selectedOrganizerIdx = selectedOrganizerIdx.coerceIn(0, (prev.size - 1).coerceAtLeast(0))
    }

    fun performRedo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = (undoStack + listOf(pages)).takeLast(30)
        redoStack = redoStack.dropLast(1)
        pages = next
        activeTextBoxId = null
        currentPageIdx = currentPageIdx.coerceIn(0, (next.size - 1).coerceAtLeast(0))
        selectedOrganizerIdx = selectedOrganizerIdx.coerceIn(0, (next.size - 1).coerceAtLeast(0))
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val name = pendingNewFileName
        val pagesToSave = pendingNewFilePages
        pendingNewFileName = null
        pendingNewFilePages = null
        if (uri != null && name != null && pagesToSave != null) {
            onSaveAsNewFile(pagesToSave, name, uri)
        }
    }

    val insertImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val newItems = uris.map { uri ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                }
                val wPts = if (bounds.outWidth > 0) (bounds.outWidth * 72f / 150f).coerceAtLeast(72f) else 595f
                val hPts = if (bounds.outHeight > 0) (bounds.outHeight * 72f / 150f).coerceAtLeast(72f) else 842f
                val name = viewModel.queryDisplayName(uri) ?: "Image"
                EditorPageItem(
                    kind = EditorPageKind.Image(
                        uri = uri,
                        displayName = name,
                        widthPts = wPts,
                        heightPts = hPts
                    )
                )
            }
            withContext(Dispatchers.Main) {
                val newIdx = pages.size
                commitPagesChange(pages + newItems)
                selectedOrganizerIdx = newIdx
            }
        }
    }

    val handleBack: () -> Unit = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = !isSaving) { handleBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { DismissibleSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = handleBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = session.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.pagesCount(pages.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { performUndo() },
                        enabled = !isSaving && undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undo)
                    }
                    IconButton(
                        onClick = { performRedo() },
                        enabled = !isSaving && redoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redo)
                    }
                    Button(
                        onClick = {
                            cleanupEmptyAndDeselect()
                            showSaveDialog = true
                        },
                        enabled = !isSaving && pages.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.save, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = activeTab == EditorTab.EDIT_TEXT,
                        onClick = { activeTab = EditorTab.EDIT_TEXT },
                        text = { Text(strings.editTextTab, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = activeTab == EditorTab.ORGANIZE_PAGES,
                        onClick = {
                            cleanupEmptyAndDeselect()
                            selectedOrganizerIdx = currentPageIdx.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                            activeTab = EditorTab.ORGANIZE_PAGES
                        },
                        text = { Text(strings.organizePagesTab, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                when (activeTab) {
                    EditorTab.EDIT_TEXT -> {
                        val currentPage = pages.getOrNull(currentPageIdx)
                        if (currentPage != null) {
                            TextEditorWorkspace(
                                page = currentPage,
                                pageNumber = currentPageIdx + 1,
                                totalPages = pages.size,
                                activeTextBoxId = activeTextBoxId,
                                viewModel = viewModel,
                                onSelectTextBox = { id ->
                                    if (id == null) {
                                        cleanupEmptyAndDeselect()
                                    } else {
                                        if (activeTextBoxId != id) {
                                            cleanupEmptyAndDeselect()
                                            // Snapshot before editing an existing box
                                            undoStack = (undoStack + listOf(pages)).takeLast(30)
                                            redoStack = emptyList()
                                            activeTextBoxId = id
                                        }
                                    }
                                },
                                onAddTextBox = { newBox ->
                                    cleanupEmptyAndDeselect()
                                    updateCurrentPage(
                                        transform = { p -> p.copy(textEdits = p.textEdits + newBox) },
                                        pushUndo = true
                                    )
                                    activeTextBoxId = newBox.id
                                },
                                onUpdateTextBox = { updatedBox, pushUndo ->
                                    updateCurrentPage(
                                        transform = { p ->
                                            p.copy(
                                                textEdits = p.textEdits.map {
                                                    if (it.id == updatedBox.id) updatedBox else it
                                                }
                                            )
                                        },
                                        pushUndo = pushUndo
                                    )
                                },
                                onDeleteTextBox = { boxId ->
                                    updateCurrentPage(
                                        transform = { p ->
                                            p.copy(textEdits = p.textEdits.filterNot { it.id == boxId })
                                        },
                                        pushUndo = true
                                    )
                                    if (activeTextBoxId == boxId) activeTextBoxId = null
                                },
                                onPrevPage = {
                                    if (currentPageIdx > 0) {
                                        cleanupEmptyAndDeselect()
                                        currentPageIdx -= 1
                                    }
                                },
                                onNextPage = {
                                    if (currentPageIdx < pages.lastIndex) {
                                        cleanupEmptyAndDeselect()
                                        currentPageIdx += 1
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    EditorTab.ORGANIZE_PAGES -> {
                        PageOrganizerWorkspace(
                            pages = pages,
                            selectedIndex = selectedOrganizerIdx.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                            viewModel = viewModel,
                            onSelectPage = { idx ->
                                selectedOrganizerIdx = idx
                            },
                            onOpenPageInTextEditor = { idx ->
                                currentPageIdx = idx
                                activeTextBoxId = null
                                activeTab = EditorTab.EDIT_TEXT
                            },
                            onMovePage = { from, to ->
                                if (from in pages.indices && to in pages.indices && from != to) {
                                    val mutable = pages.toMutableList()
                                    val item = mutable.removeAt(from)
                                    mutable.add(to, item)
                                    commitPagesChange(mutable)
                                    selectedOrganizerIdx = to
                                }
                            },
                            onRotatePage = { idx ->
                                if (idx in pages.indices) {
                                    val mutable = pages.toMutableList()
                                    val item = mutable[idx]
                                    mutable[idx] = item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
                                    commitPagesChange(mutable)
                                }
                            },
                            onSwapPageRequest = { idx ->
                                if (idx in pages.indices) {
                                    swapSourceIdx = idx
                                }
                            },
                            onDuplicatePage = { idx ->
                                if (idx in pages.indices) {
                                    val mutable = pages.toMutableList()
                                    val copy = mutable[idx].copy(
                                        id = java.util.UUID.randomUUID().toString(),
                                        textEdits = mutable[idx].textEdits.map {
                                            it.copy(id = java.util.UUID.randomUUID().toString())
                                        }
                                    )
                                    mutable.add(idx + 1, copy)
                                    commitPagesChange(mutable)
                                    selectedOrganizerIdx = idx + 1
                                }
                            },
                            onDeletePage = { idx ->
                                if (pages.size <= 1) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(strings.cannotDeleteLastPage)
                                    }
                                } else if (idx in pages.indices) {
                                    val mutable = pages.toMutableList()
                                    mutable.removeAt(idx)
                                    commitPagesChange(mutable)
                                    selectedOrganizerIdx = idx.coerceAtMost(mutable.lastIndex)
                                }
                            },
                            onAddBlankPage = {
                                val refPage = pages.getOrNull(selectedOrganizerIdx) ?: pages.lastOrNull()
                                val w = refPage?.effectiveWidthPts() ?: 595f
                                val h = refPage?.effectiveHeightPts() ?: 842f
                                val newIdx = pages.size
                                commitPagesChange(
                                    pages + EditorPageItem(kind = EditorPageKind.Blank(w, h))
                                )
                                selectedOrganizerIdx = newIdx
                            },
                            onInsertImages = {
                                insertImagesLauncher.launch(arrayOf("image/*"))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(strings.savingPdf, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    swapSourceIdx?.let { sourceIdx ->
        if (sourceIdx in pages.indices) {
            SwapPagesDialog(
                sourcePageNumber = sourceIdx + 1,
                totalPages = pages.size,
                onDismiss = { swapSourceIdx = null },
                onConfirmSwap = { targetPageNumber ->
                    val targetIdx = targetPageNumber - 1
                    if (targetIdx in pages.indices && targetIdx != sourceIdx) {
                        val mutable = pages.toMutableList()
                        val temp = mutable[sourceIdx]
                        mutable[sourceIdx] = mutable[targetIdx]
                        mutable[targetIdx] = temp
                        commitPagesChange(mutable)
                        selectedOrganizerIdx = targetIdx
                    }
                    swapSourceIdx = null
                }
            )
        }
    }

    if (showSaveDialog) {
        SaveEditedPdfDialog(
            currentDisplayName = session.displayName,
            canReplaceOriginal = session.canReplaceOriginal,
            onDismiss = { showSaveDialog = false },
            onConfirmSaveAsNew = { chosenName ->
                showSaveDialog = false
                val finalName = if (chosenName.endsWith(".pdf", ignoreCase = true)) chosenName else "$chosenName.pdf"
                pendingNewFileName = finalName
                pendingNewFilePages = pages
                createDocumentLauncher.launch(finalName)
            },
            onConfirmReplace = { chosenName ->
                showSaveDialog = false
                val finalName = if (chosenName.endsWith(".pdf", ignoreCase = true)) chosenName else "$chosenName.pdf"
                onReplaceCurrentFile(pages, finalName)
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(strings.discardChangesTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.discardChangesMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    }
                ) {
                    Text(strings.discard, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextEditorWorkspace(
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

    Column(modifier = modifier.fillMaxSize()) {
        if (activeBox == null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.tapTextToEditHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Scrollable page canvas area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .verticalScroll(scrollState)
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val maxCanvasWidthPx = constraints.maxWidth.coerceAtLeast(1)
            val aspectRatio = page.effectiveAspectRatio()
            val pageWidthPx = maxCanvasWidthPx
            val pageHeightPx = (pageWidthPx / aspectRatio).roundToInt().coerceAtLeast(1)
            val pageWidthDp = with(density) { pageWidthPx.toDp() }
            val pageHeightDp = with(density) { pageHeightPx.toDp() }
            val ptsToPx = pageHeightPx.toFloat() / page.effectiveHeightPts().coerceAtLeast(1f)

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
                        .pointerInput(page.id, page.textEdits, pageWidthPx, pageHeightPx) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / pageWidthPx.toFloat()).coerceIn(0f, 1f)
                                val ny = (offset.y / pageHeightPx.toFloat()).coerceIn(0f, 1f)

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
                        val bringIntoViewRequester = remember(box.id) { BringIntoViewRequester() }

                        LaunchedEffect(isActive) {
                            if (isActive) {
                                runCatching { focusRequester.requestFocus() }
                                delay(120)
                                runCatching { bringIntoViewRequester.bringIntoView() }
                            }
                        }

                        LaunchedEffect(isActive, box.text, box.top) {
                            if (isActive) {
                                runCatching { bringIntoViewRequester.bringIntoView() }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset { IntOffset(bx, by) }
                                .width(bwDp)
                                .wrapContentHeight()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .background(
                                    color = if (box.coverBackground) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = if (isActive) 1.75.dp else 0.75.dp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else Color(0x661976D2),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(box.id, pageWidthPx, pageHeightPx) {
                                    detectDragGestures(
                                        onDragStart = { onSelectTextBox(box.id) },
                                        onDrag = { change, dragAmount ->
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
                                    )
                                }
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
                    }

                    Button(
                        onClick = {
                            val stagger = (page.textEdits.size % 5) * 0.11f
                            val topPos = (0.14f + stagger).coerceAtMost(0.75f)
                            val newBox = EditorTextBox(
                                text = "",
                                left = 0.10f,
                                top = topPos,
                                right = 0.86f,
                                bottom = (topPos + 0.08f).coerceAtMost(0.95f),
                                coverBackground = false,
                                fontSizePts = 16f,
                                colorArgb = 0xFF111111L,
                                isBold = false,
                            )
                            onAddTextBox(newBox)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.addText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageOrganizerWorkspace(
    pages: List<EditorPageItem>,
    selectedIndex: Int,
    viewModel: PdfViewModel,
    onSelectPage: (Int) -> Unit,
    onOpenPageInTextEditor: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onRotatePage: (Int) -> Unit,
    onSwapPageRequest: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onAddBlankPage: () -> Unit,
    onInsertImages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val safeSelected = selectedIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    Column(modifier = modifier.fillMaxSize()) {
        // Subtle hint bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = strings.selectPageHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                val isSelected = index == safeSelected
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = { onSelectPage(index) },
                            onDoubleClick = { onOpenPageInTextEditor(index) }
                        )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.74f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            EditorPagePreviewBitmap(
                                page = item,
                                targetWidthPx = 320,
                                targetHeightPx = 420,
                                viewModel = viewModel,
                                context = context,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Page number pill
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            // Status badge (Edited / Blank / Image)
                            val badgeText = when {
                                item.textEdits.isNotEmpty() -> strings.editedBadge
                                item.kind is EditorPageKind.Blank -> strings.blankPageLabel
                                item.kind is EditorPageKind.Image -> strings.imagePageLabel
                                else -> null
                            }
                            if (badgeText != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Contextual Page Toolbar + Add Page Actions
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pages.isNotEmpty()) {
                    // Selected-page action strip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Selected page badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = strings.pageLabel(safeSelected + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(horizontal = 2.dp)
                            )

                            IconButton(
                                onClick = { onMovePage(safeSelected, safeSelected - 1) },
                                enabled = safeSelected > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.moveLeft, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { onMovePage(safeSelected, safeSelected + 1) },
                                enabled = safeSelected < pages.lastIndex,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.moveRight, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { onRotatePage(safeSelected) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = strings.rotatePage, modifier = Modifier.size(18.dp))
                            }

                            // Swap button showing selected page number
                            FilledTonalButton(
                                onClick = { onSwapPageRequest(safeSelected) },
                                enabled = pages.size > 1,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${strings.swapPages} #${safeSelected + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(
                                onClick = { onDuplicatePage(safeSelected) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = strings.duplicatePage, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { onOpenPageInTextEditor(safeSelected) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = strings.addText, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { onDeletePage(safeSelected) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = strings.delete,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom add actions: Blank page & Add images only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddBlankPage,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.addBlankPage, fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = onInsertImages,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.insertImages, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapPagesDialog(
    sourcePageNumber: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirmSwap: (targetPageNumber: Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    var targetInput by remember(sourcePageNumber) { mutableStateOf("") }
    val parsedTarget = targetInput.trim().toIntOrNull()
    val isValidRange = parsedTarget != null && parsedTarget in 1..totalPages
    val isDifferent = parsedTarget != sourcePageNumber
    val canSwap = isValidRange && isDifferent

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.swapPagesTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.currentPageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = strings.pageLabel(sourcePageNumber),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { input ->
                        if (input.length <= 5 && input.all { it.isDigit() }) {
                            targetInput = input
                        }
                    },
                    label = { Text(strings.swapWithPageLabel(totalPages)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = targetInput.isNotEmpty() && !canSwap,
                    supportingText = {
                        when {
                            targetInput.isEmpty() -> Unit
                            !isValidRange -> Text(strings.invalidPageNumber(totalPages))
                            !isDifferent -> Text(strings.samePageSwapHint)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsedTarget?.let(onConfirmSwap) },
                enabled = canSwap
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.swapPages)
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
private fun EditorPagePreviewBitmap(
    page: EditorPageItem,
    targetWidthPx: Int,
    targetHeightPx: Int,
    viewModel: PdfViewModel,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    var bitmap by remember(page.id, page.kind, page.rotationDegrees, targetWidthPx, targetHeightPx) {
        mutableStateOf<Bitmap?>(null)
    }

    DisposableEffect(page.id, page.kind, page.rotationDegrees, targetWidthPx, targetHeightPx) {
        var active = true
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val rendered: Bitmap? = when (val kind = page.kind) {
                is EditorPageKind.Original -> {
                    viewModel.renderBasePageBitmap(
                        pageIndex = kind.pageIndex,
                        targetWidthPx = targetWidthPx,
                        targetHeightPx = targetHeightPx,
                        rotation = rotationFromDegrees(page.rotationDegrees),
                        darkMode = false
                    )
                }
                is EditorPageKind.Image -> {
                    runCatching {
                        context.contentResolver.openInputStream(kind.uri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeStream(stream, null, opts)
                        }
                    }.getOrNull()
                }
                is EditorPageKind.Blank,
                is EditorPageKind.ExternalPdf -> null
            }
            withContext(Dispatchers.Main) {
                if (active) {
                    bitmap = rendered
                } else {
                    rendered?.recycle()
                }
            }
        }
        onDispose {
            active = false
            job.cancel()
            val old = bitmap
            bitmap = null
            old?.recycle()
        }
    }

    when (val kind = page.kind) {
        is EditorPageKind.Blank -> {
            Box(
                modifier = modifier.background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.blankPageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray
                )
            }
        }
        is EditorPageKind.ExternalPdf -> {
            Box(
                modifier = modifier.background(Color(0xFFF5F7FA)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = kind.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.pageLabel(kind.pageIndex + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is EditorPageKind.Image -> {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = kind.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = modifier.graphicsLayer {
                        rotationZ = page.rotationDegrees.toFloat()
                    }
                )
            } else {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        is EditorPageKind.Original -> {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = modifier
                )
            } else {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SaveEditedPdfDialog(
    currentDisplayName: String,
    canReplaceOriginal: Boolean,
    onDismiss: () -> Unit,
    onConfirmSaveAsNew: (fileName: String) -> Unit,
    onConfirmReplace: (fileName: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val baseName = remember(currentDisplayName) {
        currentDisplayName.removeSuffix(".pdf").removeSuffix(".PDF")
    }
    var replaceMode by remember(canReplaceOriginal) { mutableStateOf(false) }
    var fileNameInput by remember(baseName) { mutableStateOf(baseName) }
    val trimmed = fileNameInput.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.savePdfDialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!replaceMode) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (!replaceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceMode = false }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !replaceMode, onClick = { replaceMode = false })
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.saveAsNewFile,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.saveAsNewFileSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (replaceMode) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (replaceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canReplaceOriginal) { replaceMode = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = replaceMode,
                            onClick = { if (canReplaceOriginal) replaceMode = true },
                            enabled = canReplaceOriginal
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.replaceOriginalFile,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canReplaceOriginal) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                }
                            )
                            Text(
                                text = if (canReplaceOriginal) {
                                    strings.replaceOriginalFileSubtitle
                                } else {
                                    strings.replaceNotAvailableSubtitle
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text(strings.fileName) },
                    supportingText = {
                        Text(
                            if (trimmed.isEmpty()) strings.outputNameRequired else strings.keepOrChangeNameHint
                        )
                    },
                    isError = trimmed.isEmpty(),
                    singleLine = true,
                    suffix = { Text(".pdf") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (replaceMode) {
                        onConfirmReplace(trimmed)
                    } else {
                        onConfirmSaveAsNew(trimmed)
                    }
                },
                enabled = trimmed.isNotEmpty()
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
