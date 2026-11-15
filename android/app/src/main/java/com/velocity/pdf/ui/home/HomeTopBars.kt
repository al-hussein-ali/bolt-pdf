package com.velocity.pdf.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velocity.pdf.HomeDateFilter
import com.velocity.pdf.HomeProgressFilter
import com.velocity.pdf.HomeSizeFilter
import com.velocity.pdf.HomeSortOption
import com.velocity.pdf.R
import com.velocity.pdf.ui.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSelectionTopBar(
    selectedTab: Int,
    currentTabTotal: Int,
    selectedCount: Int,
    allSelectedInTab: Boolean,
    allSelectedAreFavorite: Boolean,
    driveAccountEmail: String?,
    isSyncingDrive: Boolean,
    canUploadSelection: Boolean,
    onClearSelection: () -> Unit,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleFavoritesBatch: () -> Unit,
    onUploadSelectionToDrive: () -> Unit,
    onDeleteSelection: () -> Unit,
) {
    val strings = LocalAppStrings.current
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(imageVector = Icons.Default.Close, contentDescription = strings.close)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = currentTabTotal > 0) { onToggleSelectAll(!allSelectedInTab) }
                    .padding(end = 8.dp)
            ) {
                Checkbox(
                    checked = allSelectedInTab,
                    onCheckedChange = onToggleSelectAll,
                    enabled = currentTabTotal > 0
                )
                Text(
                    text = if (selectedCount == 0) strings.selectAll else strings.selectedCount(selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            IconButton(enabled = selectedCount > 0, onClick = onToggleFavoritesBatch) {
                Icon(
                    imageVector = if (allSelectedAreFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (allSelectedAreFavorite) strings.removeFromFavorites else strings.addToFavorites,
                    tint = if (selectedCount > 0 && allSelectedAreFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                )
            }
            if ((selectedTab == 0 || selectedTab == 2) && driveAccountEmail != null) {
                IconButton(enabled = canUploadSelection, onClick = onUploadSelectionToDrive) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = strings.uploadToDrive)
                }
            }
            IconButton(enabled = selectedCount > 0, onClick = onDeleteSelection) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = strings.delete,
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeMainTopBar(
    selectedTab: Int,
    currentTabTotal: Int,
    hasRecentFiles: Boolean,
    driveAccountEmail: String?,
    isSyncingDrive: Boolean,
    onEnterSelectionMode: () -> Unit,
    onRefreshDevicePdfs: () -> Unit,
    onSyncWithDrive: () -> Unit,
    onUploadAllRecentsToDrive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var driveMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_bolt_pdf_logo),
                    contentDescription = strings.appName,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = strings.appName, fontWeight = FontWeight.Bold)
            }
        },
        actions = {
            if (currentTabTotal > 0) {
                IconButton(onClick = onEnterSelectionMode) {
                    Icon(imageVector = Icons.Default.Checklist, contentDescription = strings.selectFiles)
                }
            }
            if (selectedTab == 2) {
                IconButton(onClick = onRefreshDevicePdfs) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh device PDFs")
                }
            }
            if (selectedTab == 0 && driveAccountEmail != null) {
                Box {
                    IconButton(enabled = !isSyncingDrive, onClick = { driveMenuExpanded = true }) {
                        if (isSyncingDrive) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = strings.driveSync,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = driveMenuExpanded,
                        onDismissRequest = { driveMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.syncFromDriveMenu) },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                            onClick = {
                                driveMenuExpanded = false
                                onSyncWithDrive()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.uploadToDriveMenu) },
                            enabled = hasRecentFiles,
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                            onClick = {
                                driveMenuExpanded = false
                                onUploadAllRecentsToDrive()
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = strings.settings)
            }
        }
    )
}

@Composable
internal fun HomeSearchAndSortRow(
    selectedTab: Int,
    searchQuery: String,
    sortOption: HomeSortOption,
    onSearchQueryChange: (String) -> Unit,
    onSelectSortOption: (HomeSortOption) -> Unit,
) {
    val strings = LocalAppStrings.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    when (selectedTab) {
                        0 -> strings.searchRecentHint
                        1 -> strings.searchFavoritesHint
                        else -> strings.searchDeviceHint
                    }
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.weight(1f)
        )

        Box {
            FilledTonalIconButton(
                onClick = { sortMenuExpanded = true },
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort PDFs")
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                HomeSortOption.entries.forEach { option ->
                    val isSelected = option == sortOption
                    val label = when (option) {
                        HomeSortOption.RECENTLY_OPENED ->
                            if (selectedTab != 2) strings.sortRecentlyOpened else strings.sortNewestModified
                        HomeSortOption.OLDEST_FIRST ->
                            if (selectedTab != 2) strings.sortOldestOpened else strings.sortOldestModified
                        HomeSortOption.NAME_ASC -> strings.sortNameAsc
                        HomeSortOption.NAME_DESC -> strings.sortNameDesc
                        HomeSortOption.SIZE_OR_PAGES ->
                            if (selectedTab != 2) strings.sortMostPages else strings.sortLargestSize
                        HomeSortOption.SMALLEST_OR_FEWEST ->
                            if (selectedTab != 2) strings.sortFewestPages else strings.sortSmallestSize
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onSelectSortOption(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeFilterChipsRow(
    selectedTab: Int,
    dateFilter: HomeDateFilter,
    progressFilter: HomeProgressFilter,
    sizeFilter: HomeSizeFilter,
    onSelectDateFilter: (HomeDateFilter) -> Unit,
    onSelectProgressFilter: (HomeProgressFilter) -> Unit,
    onSelectSizeFilter: (HomeSizeFilter) -> Unit,
    onClearFilters: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val hasActiveFilters = dateFilter != HomeDateFilter.ALL ||
        (selectedTab != 2 && progressFilter != HomeProgressFilter.ALL) ||
        (selectedTab == 2 && sizeFilter != HomeSizeFilter.ALL)

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterDropdownChip(
                label = if (dateFilter == HomeDateFilter.ALL) strings.filterDate else localizedDateLabel(dateFilter, strings),
                isActive = dateFilter != HomeDateFilter.ALL,
                leadingIcon = Icons.Default.DateRange,
                options = HomeDateFilter.entries,
                selectedOption = dateFilter,
                optionLabel = { localizedDateLabel(it, strings) },
                onSelect = onSelectDateFilter
            )
        }

        item {
            if (selectedTab != 2) {
                FilterDropdownChip(
                    label = if (progressFilter == HomeProgressFilter.ALL) strings.filterReadingStatus else localizedProgressLabel(progressFilter, strings),
                    isActive = progressFilter != HomeProgressFilter.ALL,
                    leadingIcon = null,
                    options = HomeProgressFilter.entries,
                    selectedOption = progressFilter,
                    optionLabel = { localizedProgressLabel(it, strings) },
                    onSelect = onSelectProgressFilter
                )
            } else {
                FilterDropdownChip(
                    label = if (sizeFilter == HomeSizeFilter.ALL) strings.filterFileSize else localizedSizeLabel(sizeFilter, strings),
                    isActive = sizeFilter != HomeSizeFilter.ALL,
                    leadingIcon = null,
                    options = HomeSizeFilter.entries,
                    selectedOption = sizeFilter,
                    optionLabel = { localizedSizeLabel(it, strings) },
                    onSelect = onSelectSizeFilter
                )
            }
        }

        if (hasActiveFilters) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(strings.clear) }
                )
            }
        }
    }
}
