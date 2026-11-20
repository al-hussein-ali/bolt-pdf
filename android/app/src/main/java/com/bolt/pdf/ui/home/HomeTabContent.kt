package com.bolt.pdf.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bolt.pdf.DevicePdfItem
import com.bolt.pdf.HomeProgressFilter
import com.bolt.pdf.ui.LocalAppStrings
import uniffi.pdf_core.ReadingProgress

@Composable
internal fun RecentTabContent(
    isLoadingRecents: Boolean,
    allRecentCount: Int,
    sortedRecents: List<ReadingProgress>,
    searchQuery: String,
    progressFilter: HomeProgressFilter,
    onSelectProgressFilter: (HomeProgressFilter) -> Unit,
    gridState: LazyGridState,
    favoriteHashes: Set<String>,
    favoriteUris: Set<String>,
    isSelectionMode: Boolean,
    selectedRecentHashes: Set<String>,
    onCardClick: (ReadingProgress) -> Unit,
    onCardLongClick: (ReadingProgress) -> Unit,
    onToggleFavorite: (ReadingProgress) -> Unit,
    onRename: (ReadingProgress) -> Unit,
    onShare: (ReadingProgress) -> Unit,
    onEdit: (ReadingProgress) -> Unit,
    onMerge: (ReadingProgress) -> Unit,
    onSplit: (ReadingProgress) -> Unit,
    onDelete: (ReadingProgress) -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSelectionMode && allRecentCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeProgressFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        HomeProgressFilter.ALL -> strings.filterAll
                        HomeProgressFilter.UNREAD -> strings.filterUnread
                        HomeProgressFilter.IN_PROGRESS -> strings.filterInProgress
                        HomeProgressFilter.COMPLETED -> strings.filterFinished
                    }
                    FilterChip(
                        selected = progressFilter == filter,
                        onClick = { onSelectProgressFilter(filter) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
        when {
        isLoadingRecents && allRecentCount == 0 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        sortedRecents.isEmpty() -> {
            EmptyStateBox(
                icon = Icons.Default.Description,
                title = if (allRecentCount == 0) strings.noRecentPdfs else strings.noMatchingPdfs,
                subtitle = if (allRecentCount == 0) strings.noRecentSubtitle else strings.tryDifferentSearch
            )
        }
        else -> {
            Text(
                text = if (searchQuery.isBlank()) {
                    strings.recentDocumentsCount(sortedRecents.size)
                } else {
                    strings.resultsCount(sortedRecents.size)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedRecents, key = { it.fileHash }) { item ->
                    val isFav = item.fileHash in favoriteHashes || item.fileUri in favoriteUris
                    RecentFileCard(
                        item = item,
                        isFavorite = isFav,
                        selectionMode = isSelectionMode,
                        isSelected = item.fileHash in selectedRecentHashes,
                        onClick = { onCardClick(item) },
                        onLongClick = { onCardLongClick(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                        onRename = { onRename(item) },
                        onShare = { onShare(item) },
                        onEdit = { onEdit(item) },
                        onMerge = { onMerge(item) },
                        onSplit = { onSplit(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
    }
}

@Composable
internal fun FavoritesTabContent(
    sortedFavoriteRecents: List<ReadingProgress>,
    sortedFavoriteDeviceOnly: List<DevicePdfItem>,
    hasAnyFavorites: Boolean,
    gridState: LazyGridState,
    isSelectionMode: Boolean,
    selectedRecentHashes: Set<String>,
    selectedDeviceUris: Set<String>,
    onRecentClick: (ReadingProgress) -> Unit,
    onRecentLongClick: (ReadingProgress) -> Unit,
    onDeviceClick: (DevicePdfItem) -> Unit,
    onDeviceLongClick: (DevicePdfItem) -> Unit,
    onToggleFavoriteRecent: (ReadingProgress) -> Unit,
    onToggleFavoriteDevice: (DevicePdfItem) -> Unit,
    onRenameRecent: (ReadingProgress) -> Unit,
    onShareRecent: (ReadingProgress) -> Unit,
    onEditRecent: (ReadingProgress) -> Unit,
    onMergeRecent: (ReadingProgress) -> Unit,
    onSplitRecent: (ReadingProgress) -> Unit,
    onDeleteRecent: (ReadingProgress) -> Unit,
    onShareDevice: (DevicePdfItem) -> Unit,
    onEditDevice: (DevicePdfItem) -> Unit,
    onMergeDevice: (DevicePdfItem) -> Unit,
    onSplitDevice: (DevicePdfItem) -> Unit,
    onDeleteDevice: (DevicePdfItem) -> Unit,
) {
    val strings = LocalAppStrings.current
    val totalFavorites = sortedFavoriteRecents.size + sortedFavoriteDeviceOnly.size
    if (totalFavorites == 0) {
        EmptyStateBox(
            icon = Icons.Default.StarBorder,
            title = if (hasAnyFavorites) strings.noMatchingFavorites else strings.noFavoritePdfs,
            subtitle = if (hasAnyFavorites) strings.tryAdjustFilters else strings.noFavoritesSubtitle
        )
        return
    }

    Text(
        text = strings.favoritesCount(totalFavorites),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sortedFavoriteRecents, key = { "fav_recent_${it.fileHash}" }) { item ->
            RecentFileCard(
                item = item,
                isFavorite = true,
                selectionMode = isSelectionMode,
                isSelected = item.fileHash in selectedRecentHashes,
                onClick = { onRecentClick(item) },
                onLongClick = { onRecentLongClick(item) },
                onToggleFavorite = { onToggleFavoriteRecent(item) },
                onRename = { onRenameRecent(item) },
                onShare = { onShareRecent(item) },
                onEdit = { onEditRecent(item) },
                onMerge = { onMergeRecent(item) },
                onSplit = { onSplitRecent(item) },
                onDelete = { onDeleteRecent(item) }
            )
        }
        items(sortedFavoriteDeviceOnly, key = { "fav_dev_${it.uri}" }) { item ->
            DevicePdfCard(
                item = item,
                isFavorite = true,
                selectionMode = isSelectionMode,
                isSelected = item.uri.toString() in selectedDeviceUris,
                onClick = { onDeviceClick(item) },
                onLongClick = { onDeviceLongClick(item) },
                onToggleFavorite = { onToggleFavoriteDevice(item) },
                onShare = { onShareDevice(item) },
                onEdit = { onEditDevice(item) },
                onMerge = { onMergeDevice(item) },
                onSplit = { onSplitDevice(item) },
                onDelete = { onDeleteDevice(item) }
            )
        }
    }
}

@Composable
internal fun DevicePdfsTabContent(
    isLoadingDevicePdfs: Boolean,
    allDeviceCount: Int,
    sortedDevicePdfs: List<DevicePdfItem>,
    hasStoragePermission: Boolean,
    gridState: LazyGridState,
    favoriteUris: Set<String>,
    isSelectionMode: Boolean,
    selectedDeviceUris: Set<String>,
    driveAccountEmail: String?,
    isSyncingDrive: Boolean,
    onRequestStorageAccess: () -> Unit,
    onCardClick: (DevicePdfItem) -> Unit,
    onCardLongClick: (DevicePdfItem) -> Unit,
    onToggleFavorite: (DevicePdfItem) -> Unit,
    onShare: (DevicePdfItem) -> Unit,
    onEdit: (DevicePdfItem) -> Unit,
    onMerge: (DevicePdfItem) -> Unit,
    onSplit: (DevicePdfItem) -> Unit,
    onDelete: (DevicePdfItem) -> Unit,
    onUploadToDrive: (DevicePdfItem) -> Unit,
) {
    when {
        isLoadingDevicePdfs && allDeviceCount == 0 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        sortedDevicePdfs.isEmpty() -> {
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
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when {
                            !hasStoragePermission -> "Storage access needed"
                            allDeviceCount == 0 -> "No PDFs found on device"
                            else -> "No matching PDFs"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (!hasStoragePermission) {
                            "Grant storage access to list all PDF files stored on your device."
                        } else {
                            "Try refreshing or picking a file with \"Open PDF\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (!hasStoragePermission) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRequestStorageAccess, shape = RoundedCornerShape(14.dp)) {
                            Text("Grant Storage Access")
                        }
                    }
                }
            }
        }
        else -> {
            if (!hasStoragePermission) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Showing shared PDFs. Allow storage access to see all folders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onRequestStorageAccess,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Allow", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text(
                text = "Device PDFs (${sortedDevicePdfs.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = sortedDevicePdfs, key = { it.uri.toString() }) { item ->
                    DevicePdfCard(
                        item = item,
                        isFavorite = item.uri.toString() in favoriteUris,
                        selectionMode = isSelectionMode,
                        isSelected = item.uri.toString() in selectedDeviceUris,
                        onClick = { onCardClick(item) },
                        onLongClick = { onCardLongClick(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                        onShare = { onShare(item) },
                        onEdit = { onEdit(item) },
                        onMerge = { onMerge(item) },
                        onSplit = { onSplit(item) },
                        onDelete = { onDelete(item) },
                        onUploadToDrive = if (driveAccountEmail != null && !isSyncingDrive) {
                            { onUploadToDrive(item) }
                        } else null
                    )
                }
            }
        }
    }
}
