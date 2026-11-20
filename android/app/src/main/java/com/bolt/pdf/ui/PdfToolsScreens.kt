package com.bolt.pdf.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID

data class MergePdfEntry(
    val id: String,
    val displayName: String,
    val uri: Uri?,
    val fallbackPath: String?,
    val pageCount: Int?,
)

data class ImageToPdfEntry(
    val id: String,
    val displayName: String,
    val uri: Uri,
)

data class SplitTarget(
    val displayName: String,
    val uri: Uri?,
    val fallbackPath: String?,
    val totalPages: Int,
)

data class PageRangeSpec(
    val fromOneBased: Int,
    val toOneBased: Int,
)

private data class SplitRangeDraft(
    val id: String,
    val fromText: String,
    val toText: String,
)

@Composable
fun MergePdfsScreen(
    items: List<MergePdfEntry>,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onAddPdfs: (List<Uri>) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onMergeToUri: (Uri) -> Unit,
) {
    val strings = LocalAppStrings.current
    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddPdfs(uris)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onMergeToUri(uri)
        }
    }

    OrderableToolScaffold(
        title = strings.mergePdfs,
        backContentDescription = strings.back,
        processingLabel = strings.processing,
        isProcessing = isProcessing,
        addButtonLabel = strings.addPdfs,
        primaryButtonLabel = strings.mergeAndSave,
        primaryButtonIcon = Icons.AutoMirrored.Filled.MergeType,
        primaryEnabled = items.size >= 2,
        onBack = onBack,
        onAddClick = { addLauncher.launch(arrayOf("application/pdf")) },
        onPrimaryClick = {
            val defaultName = items.firstOrNull()?.displayName
                ?.removeSuffix(".pdf")?.removeSuffix(".PDF")
                ?.let { "${it}_merged.pdf" } ?: "Merged.pdf"
            saveLauncher.launch(defaultName)
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (items.size < 2) strings.needAtLeastTwoPdfs else strings.mergeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    OrderableFileCard(
                        orderNumber = index + 1,
                        title = item.displayName,
                        subtitle = item.pageCount?.let { strings.pagesCount(it) } ?: "PDF",
                        isPdf = true,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.lastIndex,
                        onMoveUp = { onMoveItem(index, index - 1) },
                        onMoveDown = { onMoveItem(index, index + 1) },
                        onRemove = { onRemoveItem(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun ImagesToPdfScreen(
    items: List<ImageToPdfEntry>,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onAddImages: (List<Uri>) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onCreatePdfToUri: (Uri) -> Unit,
) {
    val strings = LocalAppStrings.current
    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddImages(uris)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onCreatePdfToUri(uri)
        }
    }

    OrderableToolScaffold(
        title = strings.imagesToPdf,
        backContentDescription = strings.back,
        processingLabel = strings.processing,
        isProcessing = isProcessing,
        addButtonLabel = strings.addImages,
        primaryButtonLabel = strings.createPdf,
        primaryButtonIcon = Icons.Default.PictureAsPdf,
        primaryEnabled = items.isNotEmpty(),
        onBack = onBack,
        onAddClick = { addLauncher.launch(arrayOf("image/*")) },
        onPrimaryClick = { saveLauncher.launch("Images.pdf") }
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.noImagesSelected,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.imagesToPdfEmptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    OrderableFileCard(
                        orderNumber = index + 1,
                        title = item.displayName,
                        subtitle = strings.pageLabel(index + 1),
                        isPdf = false,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.lastIndex,
                        onMoveUp = { onMoveItem(index, index - 1) },
                        onMoveDown = { onMoveItem(index, index + 1) },
                        onRemove = { onRemoveItem(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderableToolScaffold(
    title: String,
    backContentDescription: String,
    processingLabel: String,
    isProcessing: Boolean,
    addButtonLabel: String,
    primaryButtonLabel: String,
    primaryButtonIcon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryEnabled: Boolean,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onPrimaryClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backContentDescription)
                    }
                },
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddClick,
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(addButtonLabel)
                    }
                    Button(
                        onClick = onPrimaryClick,
                        enabled = primaryEnabled && !isProcessing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(primaryButtonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(primaryButtonLabel)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content()
            if (isProcessing) {
                ProcessingOverlay(label = processingLabel)
            }
        }
    }
}

@Composable
fun SplitPdfDialog(
    target: SplitTarget,
    onDismiss: () -> Unit,
    onConfirmSplit: (ranges: List<PageRangeSpec>, outputUri: Uri) -> Unit,
) {
    val strings = LocalAppStrings.current
    val total = target.totalPages.coerceAtLeast(1)
    val baseName = remember(target.displayName) {
        target.displayName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "Document" }
    }
    val initialTo = minOf(10, total)
    var ranges by remember(target) {
        mutableStateOf(
            listOf(
                SplitRangeDraft(
                    id = UUID.randomUUID().toString(),
                    fromText = "1",
                    toText = initialTo.toString()
                )
            )
        )
    }
    var outputNameEdited by remember(target) { mutableStateOf(false) }
    var outputName by remember(target) {
        mutableStateOf("${baseName}_1-$initialTo")
    }

    val parsedRanges = remember(ranges, total) {
        ranges.map { draft ->
            val from = draft.fromText.toIntOrNull()
            val to = draft.toText.toIntOrNull()
            if (from != null && to != null && from in 1..total && to in from..total) {
                PageRangeSpec(from, to)
            } else {
                null
            }
        }
    }
    val validRanges = remember(parsedRanges) {
        if (parsedRanges.all { it != null }) parsedRanges.filterNotNull() else null
    }
    val isMultiRange = ranges.size > 1
    val trimmedOutputName = outputName.trim()
    val isNameValid = trimmedOutputName.isNotEmpty()
    val lastValidTo = parsedRanges.lastOrNull()?.toOneBased
    val canAddRange = lastValidTo != null && lastValidTo < total

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val specs = validRanges
        if (uri != null && !specs.isNullOrEmpty()) {
            onConfirmSplit(specs, uri)
        }
    }
    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val specs = validRanges
        if (uri != null && !specs.isNullOrEmpty()) {
            onConfirmSplit(specs, uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = strings.splitPdf,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${target.displayName} (${strings.pagesCount(total)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                ranges.forEachIndexed { index, draft ->
                    val spec = parsedRanges[index]
                    val isRowError = spec == null
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isRowError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.rangeLabel(index + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (spec != null) {
                                    Text(
                                        text = strings.pagesCount(spec.toOneBased - spec.fromOneBased + 1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (ranges.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val updated = ranges.filterNot { it.id == draft.id }
                                            ranges = updated
                                            if (!outputNameEdited) {
                                                outputName = if (updated.size > 1) {
                                                    "${baseName}_splitted"
                                                } else {
                                                    val only = updated.first()
                                                    val f = only.fromText.toIntOrNull() ?: 1
                                                    val t = only.toText.toIntOrNull() ?: total
                                                    "${baseName}_$f-$t"
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.delete,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = draft.fromText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }
                                        ranges = ranges.map {
                                            if (it.id == draft.id) it.copy(fromText = digits) else it
                                        }
                                        if (!outputNameEdited && ranges.size == 1) {
                                            outputName = "${baseName}_${digits.ifEmpty { "1" }}-${draft.toText.ifEmpty { "1" }}"
                                        }
                                    },
                                    singleLine = true,
                                    label = { Text(strings.fromPage) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = draft.toText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }
                                        ranges = ranges.map {
                                            if (it.id == draft.id) it.copy(toText = digits) else it
                                        }
                                        if (!outputNameEdited && ranges.size == 1) {
                                            outputName = "${baseName}_${draft.fromText.ifEmpty { "1" }}-${digits.ifEmpty { "1" }}"
                                        }
                                    },
                                    singleLine = true,
                                    label = { Text(strings.toPage) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val nextStart = ((lastValidTo ?: total) + 1).coerceAtMost(total)
                        val updated = ranges + SplitRangeDraft(
                            id = UUID.randomUUID().toString(),
                            fromText = nextStart.toString(),
                            toText = total.toString()
                        )
                        ranges = updated
                        if (!outputNameEdited) {
                            outputName = "${baseName}_splitted"
                        }
                    },
                    enabled = canAddRange,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.addRange)
                }

                OutlinedTextField(
                    value = outputName,
                    onValueChange = {
                        outputNameEdited = true
                        outputName = it
                    },
                    singleLine = true,
                    isError = !isNameValid,
                    label = { Text(strings.outputFolderName) },
                    supportingText = if (!isNameValid) {
                        { Text(strings.outputNameRequired) }
                    } else if (isMultiRange) {
                        { Text(strings.splitZipHint(ranges.size)) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (validRanges == null) {
                    Text(
                        text = strings.invalidPageRange,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !validRanges.isNullOrEmpty() && isNameValid,
                onClick = {
                    val sanitized = trimmedOutputName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (isMultiRange) {
                        val zipName = if (sanitized.endsWith(".zip", ignoreCase = true)) sanitized else "$sanitized.zip"
                        saveZipLauncher.launch(zipName)
                    } else {
                        val pdfName = if (sanitized.endsWith(".pdf", ignoreCase = true)) sanitized else "$sanitized.pdf"
                        savePdfLauncher.launch(pdfName)
                    }
                }
            ) {
                Text(strings.extractAndSave)
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
private fun OrderableFileCard(
    orderNumber: Int,
    title: String,
    subtitle: String,
    isPdf: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = orderNumber.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = strings.moveUp)
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = strings.moveDown)
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = strings.remove,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ProcessingOverlay(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

fun parsePageRanges(input: String, totalPages: Int): List<Int>? {
    if (totalPages <= 0) return null
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val result = mutableListOf<Int>()
    val parts = trimmed.split(',')
    for (part in parts) {
        val token = part.trim()
        if (token.isEmpty()) continue
        if ('-' in token) {
            val bounds = token.split('-')
            if (bounds.size != 2) return null
            val start = bounds[0].trim().toIntOrNull() ?: return null
            val end = bounds[1].trim().toIntOrNull() ?: return null
            if (start < 1 || end < 1 || start > totalPages || end > totalPages) return null
            if (start <= end) {
                for (p in start..end) result.add(p - 1)
            } else {
                for (p in start downTo end) result.add(p - 1)
            }
        } else {
            val page = token.toIntOrNull() ?: return null
            if (page < 1 || page > totalPages) return null
            result.add(page - 1)
        }
    }
    return result.takeIf { it.isNotEmpty() }
}
