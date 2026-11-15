package com.velocity.pdf.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velocity.pdf.ConflictResolutionChoice
import com.velocity.pdf.DriveSyncConflict
import com.velocity.pdf.ui.LocalAppStrings

@Composable
internal fun OpeningDocumentOverlay(label: String) {
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

@Composable
internal fun RenamePdfDialog(
    renameText: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(strings.renamePdf) },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = onTextChange,
                singleLine = true,
                label = { Text(strings.fileName) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = renameText.isNotBlank(), onClick = onConfirm) {
                Text(strings.rename)
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
internal fun PermanentDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(strings.deletePermanentlyTitle) },
        text = { Text(strings.deletePermanentlyMessage(count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.deletePermanentlyConfirm, color = MaterialTheme.colorScheme.error)
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
internal fun DriveSyncConflictDialog(
    conflicts: List<DriveSyncConflict>,
    onResolve: (Map<String, ConflictResolutionChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val choices = remember(conflicts) {
        mutableStateOf(
            conflicts.associate { c ->
                val defaultChoice = if (c.localProgress.lastOpenedUnix >= c.remoteItem.lastOpenedUnix) {
                    ConflictResolutionChoice.KEEP_LOCAL
                } else {
                    ConflictResolutionChoice.USE_DRIVE
                }
                c.fileHash to defaultChoice
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = strings.syncConflictTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.syncConflictSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (conflicts.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                choices.value = conflicts.associate { it.fileHash to ConflictResolutionChoice.KEEP_LOCAL }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(strings.keepAllDevice, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                conflicts.forEach { conflict ->
                    val currentChoice = choices.value[conflict.fileHash] ?: ConflictResolutionChoice.KEEP_LOCAL
                    val localPage = conflict.localProgress.lastPageIndex.toInt() + 1
                    val localTotal = conflict.localProgress.totalPages.toInt().coerceAtLeast(localPage)
                    val remotePage = conflict.remoteItem.lastPageIndex + 1
                    val remoteTotal = conflict.remoteItem.totalPages.coerceAtLeast(remotePage)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = conflict.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            ConflictOptionRow(
                                selected = currentChoice == ConflictResolutionChoice.KEEP_LOCAL,
                                title = strings.thisDevice,
                                subtitle = strings.pageOfTotal(localPage, localTotal) +
                                    if (conflict.localIsFavorite) "  •  ★ ${strings.favoritedLabel}" else "",
                                onClick = {
                                    choices.value = choices.value + (conflict.fileHash to ConflictResolutionChoice.KEEP_LOCAL)
                                }
                            )

                            ConflictOptionRow(
                                selected = currentChoice == ConflictResolutionChoice.USE_DRIVE,
                                title = strings.googleDrive,
                                subtitle = strings.pageOfTotal(remotePage, remoteTotal) +
                                    if (conflict.remoteItem.isFavorite) "  •  ★ ${strings.favoritedLabel}" else "",
                                onClick = {
                                    choices.value = choices.value + (conflict.fileHash to ConflictResolutionChoice.USE_DRIVE)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onResolve(choices.value) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(strings.apply)
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
private fun ConflictOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
