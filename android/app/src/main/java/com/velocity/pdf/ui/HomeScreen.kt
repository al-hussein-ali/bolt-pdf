package com.velocity.pdf.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WarningAmber
import com.velocity.pdf.ConflictResolutionChoice
import com.velocity.pdf.DriveSyncConflict
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import com.velocity.pdf.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velocity.pdf.DevicePdfItem
import com.velocity.pdf.HomeDateFilter
import com.velocity.pdf.HomeProgressFilter
import com.velocity.pdf.HomeSizeFilter
import com.velocity.pdf.HomeSortOption
import uniffi.pdf_core.ReadingProgress
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    recentFiles: List<ReadingProgress>,
    isLoadingRecents: Boolean,
    devicePdfs: List<DevicePdfItem>,
    isLoadingDevicePdfs: Boolean,
    hasStoragePermission: Boolean,
    sortOption: HomeSortOption,
    dateFilter: HomeDateFilter,
    progressFilter: HomeProgressFilter,
    sizeFilter: HomeSizeFilter,
    favoriteHashes: Set<String>,
    favoriteUris: Set<String>,
    isOpeningDocument: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onSelectSortOption: (HomeSortOption) -> Unit,
    onSelectDateFilter: (HomeDateFilter) -> Unit,
    onSelectProgressFilter: (HomeProgressFilter) -> Unit,
    onSelectSizeFilter: (HomeSizeFilter) -> Unit,
    onClearFilters: () -> Unit,
    onRefreshDevicePdfs: () -> Unit,
    onPickFile: () -> Unit,
    onOpenRecent: (ReadingProgress) -> Unit,
    onOpenDevicePdf: (DevicePdfItem) -> Unit,
    onToggleFavoriteRecent: (ReadingProgress) -> Unit,
    onToggleFavoriteDevicePdf: (DevicePdfItem) -> Unit,
    onRenameRecent: (ReadingProgress, String) -> Unit,
    onShareRecent: (ReadingProgress) -> Unit,
    onShareDevicePdf: (DevicePdfItem) -> Unit,
    onMergeRecent: (ReadingProgress) -> Unit,
    onSplitRecent: (ReadingProgress) -> Unit,
    onMergeDevicePdf: (DevicePdfItem) -> Unit,
    onSplitDevicePdf: (DevicePdfItem) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onSetFavoritesBatch: (List<ReadingProgress>, List<DevicePdfItem>, Boolean) -> Unit,
    onRemoveRecentFiles: (List<String>) -> Unit,
    onDeleteDevicePdfs: (List<DevicePdfItem>) -> Unit,
    driveAccountEmail: String?,
    isSyncingDrive: Boolean,
    driveSyncConflicts: List<DriveSyncConflict>,
    onConnectGoogleAccount: () -> Unit,
    onDisconnectGoogleAccount: () -> Unit,
    onUploadToDrive: (List<ReadingProgress>) -> Unit,
    onUploadDevicePdfsToDrive: (List<DevicePdfItem>) -> Unit,
    onSyncWithDrive: () -> Unit,
    onResolveDriveConflicts: (Map<String, ConflictResolutionChoice>) -> Unit,
    onDismissDriveConflicts: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val hapticFeedback = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var driveMenuExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ReadingProgress?>(null) }
    var renameText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedRecentHashes by remember { mutableStateOf(emptySet<String>()) }
    var selectedDeviceUris by remember { mutableStateOf(emptySet<String>()) }
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }

    fun clearSelection() {
        isSelectionMode = false
        selectedRecentHashes = emptySet()
        selectedDeviceUris = emptySet()
    }

    BackHandler(enabled = isSelectionMode) {
        clearSelection()
    }
    val recentGridState = remember(sortOption, dateFilter, progressFilter) { LazyGridState() }
    val favoriteGridState = remember(sortOption, dateFilter, progressFilter) { LazyGridState() }
    val deviceGridState = remember(sortOption, dateFilter, sizeFilter) { LazyGridState() }
    val context = LocalContext.current

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onRefreshDevicePdfs()
        }
    }

    val requestStorageAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val sortedRecents = remember(recentFiles, searchQuery, sortOption, dateFilter, progressFilter) {
        val q = searchQuery.trim()
        val filtered = recentFiles.filter { item ->
            val matchesQuery = q.isEmpty() || item.displayName.contains(q, ignoreCase = true)
            val matchesDate = matchesDateFilter(item.lastOpenedUnix, dateFilter)
            val matchesProgress = matchesProgressFilter(item, progressFilter)
            matchesQuery && matchesDate && matchesProgress
        }
        sortRecentItems(filtered, sortOption)
    }

    val sortedFavoriteRecents = remember(
        recentFiles,
        favoriteHashes,
        favoriteUris,
        searchQuery,
        sortOption,
        dateFilter,
        progressFilter
    ) {
        val q = searchQuery.trim()
        val filtered = recentFiles.filter { item ->
            val isFav = item.fileHash in favoriteHashes || item.fileUri in favoriteUris
            val matchesQuery = q.isEmpty() || item.displayName.contains(q, ignoreCase = true)
            val matchesDate = matchesDateFilter(item.lastOpenedUnix, dateFilter)
            val matchesProgress = matchesProgressFilter(item, progressFilter)
            isFav && matchesQuery && matchesDate && matchesProgress
        }
        sortRecentItems(filtered, sortOption)
    }

    val sortedFavoriteDeviceOnly = remember(
        devicePdfs,
        recentFiles,
        favoriteUris,
        searchQuery,
        sortOption,
        dateFilter,
        progressFilter
    ) {
        if (progressFilter != HomeProgressFilter.ALL && progressFilter != HomeProgressFilter.UNREAD) {
            emptyList()
        } else {
            val recentUris = recentFiles.mapTo(HashSet()) { it.fileUri }
            val q = searchQuery.trim()
            val filtered = devicePdfs.filter { item ->
                val uriStr = item.uri.toString()
                val isFavOnly = uriStr in favoriteUris && uriStr !in recentUris
                val matchesQuery = q.isEmpty() ||
                    item.displayName.contains(q, ignoreCase = true) ||
                    item.folderName.contains(q, ignoreCase = true)
                val matchesDate = matchesDateFilter(item.dateModifiedSec, dateFilter)
                isFavOnly && matchesQuery && matchesDate
            }
            sortDeviceItems(filtered, sortOption)
        }
    }

    val sortedDevicePdfs = remember(devicePdfs, searchQuery, sortOption, dateFilter, sizeFilter) {
        val q = searchQuery.trim()
        val filtered = devicePdfs.filter { item ->
            val matchesQuery = q.isEmpty() ||
                item.displayName.contains(q, ignoreCase = true) ||
                item.folderName.contains(q, ignoreCase = true)
            val matchesDate = matchesDateFilter(item.dateModifiedSec, dateFilter)
            val matchesSize = matchesSizeFilter(item.sizeBytes, sizeFilter)
            matchesQuery && matchesDate && matchesSize
        }
        sortDeviceItems(filtered, sortOption)
    }

    val currentTabRecents = when (selectedTab) {
        0 -> sortedRecents
        1 -> sortedFavoriteRecents
        else -> emptyList()
    }
    val currentTabDevices = when (selectedTab) {
        1 -> sortedFavoriteDeviceOnly
        2 -> sortedDevicePdfs
        else -> emptyList()
    }
    val currentTabTotal = currentTabRecents.size + currentTabDevices.size
    val selectedRecentsList = currentTabRecents.filter { it.fileHash in selectedRecentHashes }
    val selectedDevicesList = currentTabDevices.filter { it.uri.toString() in selectedDeviceUris }
    val selectedCount = selectedRecentsList.size + selectedDevicesList.size
    val allSelectedInTab = currentTabTotal > 0 && selectedCount == currentTabTotal
    val allSelectedAreFavorite = selectedCount > 0 &&
        selectedRecentsList.all { it.fileHash in favoriteHashes || it.fileUri in favoriteUris } &&
        selectedDevicesList.all { it.uri.toString() in favoriteUris }

    fun toggleSelectAllInCurrentTab(selectAll: Boolean) {
        if (selectAll) {
            selectedRecentHashes = currentTabRecents.map { it.fileHash }.toSet()
            selectedDeviceUris = currentTabDevices.map { it.uri.toString() }.toSet()
        } else {
            selectedRecentHashes = emptySet()
            selectedDeviceUris = emptySet()
        }
    }

    fun toggleRecentSelection(item: ReadingProgress) {
        selectedRecentHashes = if (item.fileHash in selectedRecentHashes) {
            selectedRecentHashes - item.fileHash
        } else {
            selectedRecentHashes + item.fileHash
        }
    }

    fun toggleDeviceSelection(item: DevicePdfItem) {
        val key = item.uri.toString()
        selectedDeviceUris = if (key in selectedDeviceUris) {
            selectedDeviceUris - key
        } else {
            selectedDeviceUris + key
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { DismissibleSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                if (isSelectionMode) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        navigationIcon = {
                            IconButton(onClick = { clearSelection() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.close
                                )
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = currentTabTotal > 0) {
                                        toggleSelectAllInCurrentTab(!allSelectedInTab)
                                    }
                                    .padding(end = 8.dp)
                            ) {
                                Checkbox(
                                    checked = allSelectedInTab,
                                    onCheckedChange = { checked -> toggleSelectAllInCurrentTab(checked) },
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
                            IconButton(
                                enabled = selectedCount > 0,
                                onClick = {
                                    onSetFavoritesBatch(
                                        selectedRecentsList,
                                        selectedDevicesList,
                                        !allSelectedAreFavorite
                                    )
                                    clearSelection()
                                }
                            ) {
                                Icon(
                                    imageVector = if (allSelectedAreFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = if (allSelectedAreFavorite) strings.removeFromFavorites else strings.addToFavorites,
                                    tint = if (selectedCount > 0 && allSelectedAreFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if ((selectedTab == 0 || selectedTab == 2) && driveAccountEmail != null) {
                                val canUploadSelection = !isSyncingDrive &&
                                    if (selectedTab == 0) selectedRecentsList.isNotEmpty() else selectedDevicesList.isNotEmpty()
                                IconButton(
                                    enabled = canUploadSelection,
                                    onClick = {
                                        if (selectedTab == 0) {
                                            onUploadToDrive(selectedRecentsList)
                                        } else {
                                            onUploadDevicePdfsToDrive(selectedDevicesList)
                                        }
                                        clearSelection()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = strings.uploadToDrive
                                    )
                                }
                            }
                            IconButton(
                                enabled = selectedCount > 0,
                                onClick = {
                                    when (selectedTab) {
                                        0 -> {
                                            onRemoveRecentFiles(selectedRecentsList.map { it.fileHash })
                                            clearSelection()
                                        }
                                        1 -> {
                                            onSetFavoritesBatch(selectedRecentsList, selectedDevicesList, false)
                                            clearSelection()
                                        }
                                        else -> {
                                            showPermanentDeleteDialog = true
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = strings.delete,
                                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                } else {
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
                                Text(
                                    text = strings.appName,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            if (currentTabTotal > 0) {
                                IconButton(onClick = { isSelectionMode = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = strings.selectFiles
                                    )
                                }
                            }
                            if (selectedTab == 2) {
                                IconButton(onClick = onRefreshDevicePdfs) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh device PDFs"
                                    )
                                }
                            }
                            if (selectedTab == 0 && driveAccountEmail != null) {
                                Box {
                                    IconButton(
                                        enabled = !isSyncingDrive,
                                        onClick = { driveMenuExpanded = true }
                                    ) {
                                        if (isSyncingDrive) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
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
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.CloudSync,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                driveMenuExpanded = false
                                                onSyncWithDrive()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(strings.uploadToDriveMenu) },
                                            enabled = recentFiles.isNotEmpty(),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.CloudUpload,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                driveMenuExpanded = false
                                                onUploadToDrive(recentFiles)
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = strings.settings
                                )
                            }
                        }
                    )
                }

                RtlSafeTabRow(
                    titles = listOf(strings.tabRecent, strings.tabFavorites, strings.tabOnDevice),
                    selectedTab = selectedTab,
                    onSelectTab = { index ->
                        clearSelection()
                        selectedTab = index
                        if (index == 2 && devicePdfs.isEmpty()) {
                            onRefreshDevicePdfs()
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickFile,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(strings.openPdf, fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Unified Search + Sort Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
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
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort PDFs"
                            )
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

                // Filter Chips Row (Date + Reading status / File size)
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

                if (selectedTab == 0) {
                    when {
                        isLoadingRecents && recentFiles.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        sortedRecents.isEmpty() -> {
                            EmptyStateBox(
                                icon = Icons.Default.Description,
                                title = if (recentFiles.isEmpty()) strings.noRecentPdfs else strings.noMatchingPdfs,
                                subtitle = if (recentFiles.isEmpty()) strings.noRecentSubtitle else strings.tryDifferentSearch
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
                                state = recentGridState,
                                columns = GridCells.Adaptive(minSize = 320.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 92.dp
                                ),
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
                                        onClick = {
                                            if (isSelectionMode) {
                                                toggleRecentSelection(item)
                                            } else {
                                                onOpenRecent(item)
                                            }
                                        },
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedRecentHashes = setOf(item.fileHash)
                                            } else {
                                                toggleRecentSelection(item)
                                            }
                                        },
                                        onToggleFavorite = { onToggleFavoriteRecent(item) },
                                        onRename = {
                                            renameTarget = item
                                            renameText = item.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
                                        },
                                        onShare = { onShareRecent(item) },
                                        onMerge = { onMergeRecent(item) },
                                        onSplit = { onSplitRecent(item) },
                                        onDelete = { onRemoveRecent(item.fileHash) }
                                    )
                                }
                            }
                        }
                    }
                } else if (selectedTab == 1) {
                    val totalFavorites = sortedFavoriteRecents.size + sortedFavoriteDeviceOnly.size
                    if (totalFavorites == 0) {
                        val hasAnyFavorites = favoriteHashes.isNotEmpty() || favoriteUris.isNotEmpty()
                        EmptyStateBox(
                            icon = Icons.Default.StarBorder,
                            title = if (hasAnyFavorites) strings.noMatchingFavorites else strings.noFavoritePdfs,
                            subtitle = if (hasAnyFavorites) strings.tryAdjustFilters else strings.noFavoritesSubtitle
                        )
                    } else {
                        Text(
                            text = strings.favoritesCount(totalFavorites),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                        LazyVerticalGrid(
                            state = favoriteGridState,
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 92.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(sortedFavoriteRecents, key = { "fav_recent_${it.fileHash}" }) { item ->
                                RecentFileCard(
                                    item = item,
                                    isFavorite = true,
                                    selectionMode = isSelectionMode,
                                    isSelected = item.fileHash in selectedRecentHashes,
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleRecentSelection(item)
                                        } else {
                                            onOpenRecent(item)
                                        }
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedRecentHashes = setOf(item.fileHash)
                                        } else {
                                            toggleRecentSelection(item)
                                        }
                                    },
                                    onToggleFavorite = { onToggleFavoriteRecent(item) },
                                    onRename = {
                                        renameTarget = item
                                        renameText = item.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
                                    },
                                    onShare = { onShareRecent(item) },
                                    onMerge = { onMergeRecent(item) },
                                    onSplit = { onSplitRecent(item) },
                                    onDelete = { onRemoveRecent(item.fileHash) }
                                )
                            }
                            items(sortedFavoriteDeviceOnly, key = { "fav_dev_${it.uri}" }) { item ->
                                DevicePdfCard(
                                    item = item,
                                    isFavorite = true,
                                    selectionMode = isSelectionMode,
                                    isSelected = item.uri.toString() in selectedDeviceUris,
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleDeviceSelection(item)
                                        } else {
                                            onOpenDevicePdf(item)
                                        }
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedDeviceUris = setOf(item.uri.toString())
                                        } else {
                                            toggleDeviceSelection(item)
                                        }
                                    },
                                    onToggleFavorite = { onToggleFavoriteDevicePdf(item) },
                                    onShare = { onShareDevicePdf(item) },
                                    onMerge = { onMergeDevicePdf(item) },
                                    onSplit = { onSplitDevicePdf(item) }
                                )
                            }
                        }
                    }
                } else {
                    when {
                        isLoadingDevicePdfs && devicePdfs.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
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
                                        text = if (!hasStoragePermission) {
                                            "Storage access needed"
                                        } else if (devicePdfs.isEmpty()) {
                                            "No PDFs found on device"
                                        } else {
                                            "No matching PDFs"
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
                                        Button(
                                            onClick = requestStorageAccess,
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
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
                                            onClick = requestStorageAccess,
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
                                state = deviceGridState,
                                columns = GridCells.Adaptive(minSize = 320.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 92.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = sortedDevicePdfs,
                                    key = { it.uri.toString() }
                                ) { item ->
                                    val isFav = item.uri.toString() in favoriteUris
                                    DevicePdfCard(
                                        item = item,
                                        isFavorite = isFav,
                                        selectionMode = isSelectionMode,
                                        isSelected = item.uri.toString() in selectedDeviceUris,
                                        onClick = {
                                            if (isSelectionMode) {
                                                toggleDeviceSelection(item)
                                            } else {
                                                onOpenDevicePdf(item)
                                            }
                                        },
                                        onLongClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedDeviceUris = setOf(item.uri.toString())
                                            } else {
                                                toggleDeviceSelection(item)
                                            }
                                        },
                                        onToggleFavorite = { onToggleFavoriteDevicePdf(item) },
                                        onShare = { onShareDevicePdf(item) },
                                        onMerge = { onMergeDevicePdf(item) },
                                        onSplit = { onSplitDevicePdf(item) },
                                        onUploadToDrive = if (driveAccountEmail != null && !isSyncingDrive) {
                                            { onUploadDevicePdfsToDrive(listOf(item)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isOpeningDocument) {
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
                            Text(strings.openingPdf, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        if (driveSyncConflicts.isNotEmpty()) {
            DriveSyncConflictDialog(
                conflicts = driveSyncConflicts,
                onResolve = onResolveDriveConflicts,
                onDismiss = onDismissDriveConflicts
            )
        }

        val targetToRename = renameTarget
        if (targetToRename != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(strings.renamePdf) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text(strings.fileName) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = renameText.isNotBlank(),
                        onClick = {
                            onRenameRecent(targetToRename, renameText)
                            renameTarget = null
                        }
                    ) {
                        Text(strings.rename)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(strings.cancel)
                    }
                }
            )
        }

        if (showPermanentDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showPermanentDeleteDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(strings.deletePermanentlyTitle) },
                text = { Text(strings.deletePermanentlyMessage(selectedDevicesList.size)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermanentDeleteDialog = false
                            onDeleteDevicePdfs(selectedDevicesList)
                            clearSelection()
                        }
                    ) {
                        Text(strings.deletePermanentlyConfirm, color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermanentDeleteDialog = false }) {
                        Text(strings.cancel)
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyStateBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun RecentFileCard(
    item: ReadingProgress,
    isFavorite: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
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
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
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
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onMerge()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.splitPdf) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onSplit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.rename) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.share) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = strings.delete,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DevicePdfCard(
    item: DevicePdfItem,
    isFavorite: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onUploadToDrive: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    val sizeText = remember(item.sizeBytes) { formatBytes(item.sizeBytes) }
    val dateFormatter = remember(strings) {
        DateFormat.getDateInstance(DateFormat.MEDIUM)
    }
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
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
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
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onMerge()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.splitPdf) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onSplit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.share) },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    if (onUploadToDrive != null) {
                        DropdownMenuItem(
                            text = { Text(strings.uploadToDrive) },
                            leadingIcon = {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onUploadToDrive()
                            }
                        )
                    }
                }
            }
            }
        }
    }
}

private fun localizedDateLabel(filter: HomeDateFilter, strings: AppStrings): String = when (filter) {
    HomeDateFilter.ALL -> strings.filterAllTime
    HomeDateFilter.TODAY -> strings.filterToday
    HomeDateFilter.LAST_7_DAYS -> strings.filterLast7Days
    HomeDateFilter.LAST_30_DAYS -> strings.filterLast30Days
    HomeDateFilter.THIS_YEAR -> strings.filterThisYear
}

private fun localizedProgressLabel(filter: HomeProgressFilter, strings: AppStrings): String = when (filter) {
    HomeProgressFilter.ALL -> strings.filterAll
    HomeProgressFilter.UNREAD -> strings.filterUnread
    HomeProgressFilter.IN_PROGRESS -> strings.filterInProgress
    HomeProgressFilter.COMPLETED -> strings.filterFinished
}

private fun localizedSizeLabel(filter: HomeSizeFilter, strings: AppStrings): String = when (filter) {
    HomeSizeFilter.ALL -> strings.filterAllSizes
    HomeSizeFilter.SMALL -> strings.filterUnder1Mb
    HomeSizeFilter.MEDIUM -> strings.filter1To10Mb
    HomeSizeFilter.LARGE -> strings.filterOver10Mb
}

private fun sortRecentItems(
    items: List<ReadingProgress>,
    sortOption: HomeSortOption,
): List<ReadingProgress> {
    return when (sortOption) {
        HomeSortOption.RECENTLY_OPENED -> items.sortedByDescending { it.lastOpenedUnix }
        HomeSortOption.OLDEST_FIRST -> items.sortedBy { it.lastOpenedUnix }
        HomeSortOption.NAME_ASC -> items.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        HomeSortOption.NAME_DESC -> items.sortedByDescending { it.displayName.lowercase(Locale.getDefault()) }
        HomeSortOption.SIZE_OR_PAGES -> items.sortedByDescending { it.totalPages }
        HomeSortOption.SMALLEST_OR_FEWEST -> items.sortedBy { it.totalPages }
    }
}

private fun sortDeviceItems(
    items: List<DevicePdfItem>,
    sortOption: HomeSortOption,
): List<DevicePdfItem> {
    return when (sortOption) {
        HomeSortOption.RECENTLY_OPENED -> items.sortedByDescending { it.dateModifiedSec }
        HomeSortOption.OLDEST_FIRST -> items.sortedBy { it.dateModifiedSec }
        HomeSortOption.NAME_ASC -> items.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        HomeSortOption.NAME_DESC -> items.sortedByDescending { it.displayName.lowercase(Locale.getDefault()) }
        HomeSortOption.SIZE_OR_PAGES -> items.sortedByDescending { it.sizeBytes }
        HomeSortOption.SMALLEST_OR_FEWEST -> items.sortedBy { it.sizeBytes }
    }
}

private fun matchesDateFilter(timestampSec: Long, filter: HomeDateFilter): Boolean {
    if (filter == HomeDateFilter.ALL) return true
    if (timestampSec <= 0L) return false
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfTodaySec = cal.timeInMillis / 1000L

    val cutoffSec = when (filter) {
        HomeDateFilter.ALL -> 0L
        HomeDateFilter.TODAY -> startOfTodaySec
        HomeDateFilter.LAST_7_DAYS -> startOfTodaySec - 6L * 86_400L
        HomeDateFilter.LAST_30_DAYS -> startOfTodaySec - 29L * 86_400L
        HomeDateFilter.THIS_YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.timeInMillis / 1000L
        }
    }
    return timestampSec >= cutoffSec
}

private fun matchesProgressFilter(item: ReadingProgress, filter: HomeProgressFilter): Boolean {
    if (filter == HomeProgressFilter.ALL) return true
    val totalPages = item.totalPages.toInt().coerceAtLeast(1)
    val currentPage = (item.lastPageIndex.toInt() + 1).coerceIn(1, totalPages)
    return when (filter) {
        HomeProgressFilter.ALL -> true
        HomeProgressFilter.UNREAD -> item.lastPageIndex == 0u && totalPages > 1
        HomeProgressFilter.IN_PROGRESS -> currentPage in 2 until totalPages
        HomeProgressFilter.COMPLETED -> currentPage >= totalPages
    }
}

private fun matchesSizeFilter(sizeBytes: Long, filter: HomeSizeFilter): Boolean {
    if (filter == HomeSizeFilter.ALL) return true
    val oneMb = 1024L * 1024L
    val tenMb = 10L * oneMb
    return when (filter) {
        HomeSizeFilter.ALL -> true
        HomeSizeFilter.SMALL -> sizeBytes in 1L until oneMb
        HomeSizeFilter.MEDIUM -> sizeBytes in oneMb..tenMb
        HomeSizeFilter.LARGE -> sizeBytes > tenMb
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

@Composable
private fun <T> FilterDropdownChip(
    label: String,
    isActive: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = isActive,
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = { Text(label) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
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
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun RtlSafeTabRow(
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

@Composable
private fun DriveSyncConflictDialog(
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
