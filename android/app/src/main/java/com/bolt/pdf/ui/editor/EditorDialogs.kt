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


@Composable
internal fun SwapPagesDialog(
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

private val editorPreviewCache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun calculateSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
    var sample = 1
    if (rawH > reqH || rawW > reqW) {
        val halfH = rawH / 2
        val halfW = rawW / 2
        while ((halfH / sample) >= reqH && (halfW / sample) >= reqW) {
            sample *= 2
        }
    }
    return sample.coerceAtLeast(1)
}

@Composable
internal fun EditorPagePreviewBitmap(
    page: EditorPageItem,
    targetWidthPx: Int,
    targetHeightPx: Int,
    viewModel: PdfViewModel,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val cacheKey = "${page.id}_${page.rotationDegrees}_${targetWidthPx}x${targetHeightPx}"
    var bitmap by remember(cacheKey) {
        mutableStateOf<Bitmap?>(editorPreviewCache.get(cacheKey)?.takeUnless { it.isRecycled })
    }

    DisposableEffect(cacheKey) {
        if (bitmap != null) {
            return@DisposableEffect onDispose { }
        }
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
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(kind.uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, bounds)
                        }
                        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetWidthPx, targetHeightPx)
                        context.contentResolver.openInputStream(kind.uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    }.getOrNull()
                }
                is EditorPageKind.Blank,
                is EditorPageKind.ExternalPdf -> null
            }
            if (rendered != null && !rendered.isRecycled) {
                editorPreviewCache.put(cacheKey, rendered)
            }
            withContext(Dispatchers.Main) {
                if (active) {
                    bitmap = rendered
                }
            }
        }
        onDispose {
            active = false
            job.cancel()
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
internal fun SaveEditedPdfDialog(
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
