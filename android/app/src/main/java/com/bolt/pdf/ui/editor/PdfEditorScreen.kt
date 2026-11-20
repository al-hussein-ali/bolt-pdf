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

internal enum class EditorTab {
    EDIT_TEXT,
    ORGANIZE_PAGES
}

internal val EDITOR_TEXT_COLORS = listOf(
    0xFF111111L, // Black
    0xFF4B5563L, // Slate Gray
    0xFF1D4ED8L, // Blue
    0xFFDC2626L, // Red
    0xFF15803DL, // Green
    0xFFD97706L, // Amber
    0xFF7C3AEDL, // Purple
)

internal val FONT_SIZE_PRESETS = listOf(10f, 12f, 14f, 16f, 18f, 22f, 28f, 36f)

internal fun rotationFromDegrees(deg: Int): PageRotation = when (((deg % 360) + 360) % 360) {
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
        contentWindowInsets = WindowInsets.systemBars,
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
