package com.bolt.pdf.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bolt.pdf.DevicePdfItem
import com.bolt.pdf.ui.LocalAppStrings
import uniffi.pdf_core.ReadingProgress
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
internal fun EmptyStateBox(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecentFileCard(
    item: ReadingProgress,
    isFavorite: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    val totalPages = item.totalPages.toInt().coerceAtLeast(1)
    val currentPage = (item.lastPageIndex.toInt() + 1).coerceIn(1, totalPages)
    val progress = (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
    val pct = (progress * 100f).roundToInt()
    val dateFormatter = remember(strings) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    val formattedDate = remember(item.lastOpenedUnix, dateFormatter) {
        if (item.lastOpenedUnix <= 0L) "" else dateFormatter.format(Date(item.lastOpenedUnix * 1000L))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Page $currentPage / $totalPages ($pct%)" +
                        if (formattedDate.isNotEmpty()) " • $formattedDate" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) strings.removeFromFavorites else strings.addToFavorites,
                        tint = if (isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = strings.options,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.mergeWith) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null) },
                            onClick = { menuExpanded = false; onMerge() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.editPdf) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.splitPdf) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
                            onClick = { menuExpanded = false; onSplit() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.rename) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.share) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DevicePdfCard(
    item: DevicePdfItem,
    isFavorite: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onUploadToDrive: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    val sizeText = remember(item.sizeBytes) { formatBytes(item.sizeBytes) }
    val dateFormatter = remember(strings) { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val dateText = remember(item.dateModifiedSec, dateFormatter) {
        if (item.dateModifiedSec <= 0L) "" else dateFormatter.format(Date(item.dateModifiedSec * 1000L))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.folderName)
                        if (sizeText.isNotEmpty()) append(" • $sizeText")
                        if (dateText.isNotEmpty()) append(" • $dateText")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) strings.removeFromFavorites else strings.addToFavorites,
                        tint = if (isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = strings.options,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.mergeWith) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null) },
                            onClick = { menuExpanded = false; onMerge() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.editPdf) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.splitPdf) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
                            onClick = { menuExpanded = false; onSplit() }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.share) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        if (onUploadToDrive != null) {
                            DropdownMenuItem(
                                text = { Text(strings.uploadToDrive) },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                onClick = { menuExpanded = false; onUploadToDrive() }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RtlSafeTabRow(
    titles: List<String>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onSelectTab(index) },
                    modifier = Modifier.weight(1f),
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val count = titles.size.coerceAtLeast(1)
            val tabWidth = maxWidth / count
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedTab.coerceIn(0, count - 1),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "tab_indicator_offset"
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .offset(x = indicatorOffset)
                    .align(Alignment.BottomStart),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .width((tabWidth * 0.55f).coerceAtLeast(36.dp))
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
