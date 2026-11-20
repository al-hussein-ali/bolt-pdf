package com.bolt.pdf.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bolt.pdf.ConflictResolutionChoice
import com.bolt.pdf.DevicePdfItem
import com.bolt.pdf.DriveSyncConflict
import com.bolt.pdf.HomeDateFilter
import com.bolt.pdf.HomeProgressFilter
import com.bolt.pdf.HomeSizeFilter
import com.bolt.pdf.HomeSortOption
import com.bolt.pdf.ui.home.DevicePdfsTabContent
import com.bolt.pdf.ui.home.DriveSyncConflictDialog
import com.bolt.pdf.ui.home.FavoritesTabContent
import com.bolt.pdf.ui.home.HomeFilterChipsRow
import com.bolt.pdf.ui.home.HomeMainTopBar
import com.bolt.pdf.ui.home.HomeSearchAndSortRow
import com.bolt.pdf.ui.home.HomeSelectionTopBar
import com.bolt.pdf.ui.home.OpeningDocumentOverlay
import com.bolt.pdf.ui.home.PermanentDeleteDialog
import com.bolt.pdf.ui.home.RecentTabContent
import com.bolt.pdf.ui.home.RenamePdfDialog
import com.bolt.pdf.ui.home.RtlSafeTabRow
import com.bolt.pdf.ui.home.matchesDateFilter
import com.bolt.pdf.ui.home.matchesProgressFilter
import com.bolt.pdf.ui.home.matchesSizeFilter
import com.bolt.pdf.ui.home.sortDeviceItems
import com.bolt.pdf.ui.home.sortRecentItems
import uniffi.pdf_core.ReadingProgress

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
    onEditRecent: (ReadingProgress) -> Unit,
    onEditDevicePdf: (DevicePdfItem) -> Unit,
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
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ReadingProgress?>(null) }
    var renameText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedRecentHashes by remember { mutableStateOf(emptySet<String>()) }
    var selectedDeviceUris by remember { mutableStateOf(emptySet<String>()) }
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }
    var singleDeleteDeviceTarget by remember { mutableStateOf<DevicePdfItem?>(null) }

    fun clearSelection() {
        isSelectionMode = false
        selectedRecentHashes = emptySet()
        selectedDeviceUris = emptySet()
    }

    BackHandler(enabled = isSelectionMode) { clearSelection() }

    val recentGridState = remember(sortOption, dateFilter, progressFilter) { LazyGridState() }
    val favoriteGridState = remember(sortOption, dateFilter, progressFilter) { LazyGridState() }
    val deviceGridState = remember(sortOption, dateFilter, sizeFilter) { LazyGridState() }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRefreshDevicePdfs()
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
        sortRecentItems(
            recentFiles.filter { item ->
                (q.isEmpty() || item.displayName.contains(q, ignoreCase = true)) &&
                    matchesDateFilter(item.lastOpenedUnix, dateFilter) &&
                    matchesProgressFilter(item, progressFilter)
            },
            sortOption
        )
    }

    val sortedFavoriteRecents = remember(recentFiles, favoriteHashes, favoriteUris, searchQuery, sortOption, dateFilter, progressFilter) {
        val q = searchQuery.trim()
        sortRecentItems(
            recentFiles.filter { item ->
                (item.fileHash in favoriteHashes || item.fileUri in favoriteUris) &&
                    (q.isEmpty() || item.displayName.contains(q, ignoreCase = true)) &&
                    matchesDateFilter(item.lastOpenedUnix, dateFilter) &&
                    matchesProgressFilter(item, progressFilter)
            },
            sortOption
        )
    }

    val sortedFavoriteDeviceOnly = remember(devicePdfs, recentFiles, favoriteUris, searchQuery, sortOption, dateFilter, progressFilter) {
        if (progressFilter != HomeProgressFilter.ALL && progressFilter != HomeProgressFilter.UNREAD) {
            emptyList()
        } else {
            val recentUris = recentFiles.mapTo(HashSet()) { it.fileUri }
            val q = searchQuery.trim()
            sortDeviceItems(
                devicePdfs.filter { item ->
                    val uriStr = item.uri.toString()
                    uriStr in favoriteUris && uriStr !in recentUris &&
                        (q.isEmpty() || item.displayName.contains(q, ignoreCase = true) || item.folderName.contains(q, ignoreCase = true)) &&
                        matchesDateFilter(item.dateModifiedSec, dateFilter)
                },
                sortOption
            )
        }
    }

    val sortedDevicePdfs = remember(devicePdfs, searchQuery, sortOption, dateFilter, sizeFilter) {
        val q = searchQuery.trim()
        sortDeviceItems(
            devicePdfs.filter { item ->
                (q.isEmpty() || item.displayName.contains(q, ignoreCase = true) || item.folderName.contains(q, ignoreCase = true)) &&
                    matchesDateFilter(item.dateModifiedSec, dateFilter) &&
                    matchesSizeFilter(item.sizeBytes, sizeFilter)
            },
            sortOption
        )
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

    fun startRename(item: ReadingProgress) {
        renameTarget = item
        renameText = item.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { DismissibleSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                if (isSelectionMode) {
                    HomeSelectionTopBar(
                        selectedTab = selectedTab,
                        currentTabTotal = currentTabTotal,
                        selectedCount = selectedCount,
                        allSelectedInTab = allSelectedInTab,
                        allSelectedAreFavorite = allSelectedAreFavorite,
                        driveAccountEmail = driveAccountEmail,
                        isSyncingDrive = isSyncingDrive,
                        canUploadSelection = !isSyncingDrive &&
                            if (selectedTab == 0) selectedRecentsList.isNotEmpty() else selectedDevicesList.isNotEmpty(),
                        onClearSelection = ::clearSelection,
                        onToggleSelectAll = { selectAll ->
                            if (selectAll) {
                                selectedRecentHashes = currentTabRecents.map { it.fileHash }.toSet()
                                selectedDeviceUris = currentTabDevices.map { it.uri.toString() }.toSet()
                            } else {
                                selectedRecentHashes = emptySet()
                                selectedDeviceUris = emptySet()
                            }
                        },
                        onToggleFavoritesBatch = {
                            onSetFavoritesBatch(selectedRecentsList, selectedDevicesList, !allSelectedAreFavorite)
                            clearSelection()
                        },
                        onUploadSelectionToDrive = {
                            if (selectedTab == 0) onUploadToDrive(selectedRecentsList)
                            else onUploadDevicePdfsToDrive(selectedDevicesList)
                            clearSelection()
                        },
                        onDeleteSelection = {
                            when (selectedTab) {
                                0 -> {
                                    onRemoveRecentFiles(selectedRecentsList.map { it.fileHash })
                                    clearSelection()
                                }
                                1 -> {
                                    onSetFavoritesBatch(selectedRecentsList, selectedDevicesList, false)
                                    clearSelection()
                                }
                                else -> showPermanentDeleteDialog = true
                            }
                        }
                    )
                } else {
                    HomeMainTopBar(
                        selectedTab = selectedTab,
                        currentTabTotal = currentTabTotal,
                        hasRecentFiles = recentFiles.isNotEmpty(),
                        driveAccountEmail = driveAccountEmail,
                        isSyncingDrive = isSyncingDrive,
                        onEnterSelectionMode = { isSelectionMode = true },
                        onRefreshDevicePdfs = onRefreshDevicePdfs,
                        onSyncWithDrive = onSyncWithDrive,
                        onUploadAllRecentsToDrive = { onUploadToDrive(recentFiles) },
                        onOpenSettings = onOpenSettings
                    )
                }

                RtlSafeTabRow(
                    titles = listOf(strings.tabRecent, strings.tabFavorites, strings.tabOnDevice),
                    selectedTab = selectedTab,
                    onSelectTab = { index ->
                        clearSelection()
                        selectedTab = index
                        if (index == 2 && devicePdfs.isEmpty()) onRefreshDevicePdfs()
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
                HomeSearchAndSortRow(
                    selectedTab = selectedTab,
                    searchQuery = searchQuery,
                    sortOption = sortOption,
                    onSearchQueryChange = { searchQuery = it },
                    onSelectSortOption = onSelectSortOption
                )

                HomeFilterChipsRow(
                    selectedTab = selectedTab,
                    dateFilter = dateFilter,
                    progressFilter = progressFilter,
                    sizeFilter = sizeFilter,
                    onSelectDateFilter = onSelectDateFilter,
                    onSelectProgressFilter = onSelectProgressFilter,
                    onSelectSizeFilter = onSelectSizeFilter,
                    onClearFilters = onClearFilters
                )

                when (selectedTab) {
                    0 -> RecentTabContent(
                        isLoadingRecents = isLoadingRecents,
                        allRecentCount = recentFiles.size,
                        sortedRecents = sortedRecents,
                        searchQuery = searchQuery,
                        progressFilter = progressFilter,
                        onSelectProgressFilter = onSelectProgressFilter,
                        gridState = recentGridState,
                        favoriteHashes = favoriteHashes,
                        favoriteUris = favoriteUris,
                        isSelectionMode = isSelectionMode,
                        selectedRecentHashes = selectedRecentHashes,
                        onCardClick = { item ->
                            if (isSelectionMode) toggleRecentSelection(item) else onOpenRecent(item)
                        },
                        onCardLongClick = { item ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedRecentHashes = setOf(item.fileHash)
                            } else {
                                toggleRecentSelection(item)
                            }
                        },
                        onToggleFavorite = onToggleFavoriteRecent,
                        onRename = ::startRename,
                        onShare = onShareRecent,
                        onEdit = onEditRecent,
                        onMerge = onMergeRecent,
                        onSplit = onSplitRecent,
                        onDelete = { onRemoveRecent(it.fileHash) }
                    )

                    1 -> FavoritesTabContent(
                        sortedFavoriteRecents = sortedFavoriteRecents,
                        sortedFavoriteDeviceOnly = sortedFavoriteDeviceOnly,
                        hasAnyFavorites = favoriteHashes.isNotEmpty() || favoriteUris.isNotEmpty(),
                        gridState = favoriteGridState,
                        isSelectionMode = isSelectionMode,
                        selectedRecentHashes = selectedRecentHashes,
                        selectedDeviceUris = selectedDeviceUris,
                        onRecentClick = { item ->
                            if (isSelectionMode) toggleRecentSelection(item) else onOpenRecent(item)
                        },
                        onRecentLongClick = { item ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedRecentHashes = setOf(item.fileHash)
                            } else {
                                toggleRecentSelection(item)
                            }
                        },
                        onDeviceClick = { item ->
                            if (isSelectionMode) toggleDeviceSelection(item) else onOpenDevicePdf(item)
                        },
                        onDeviceLongClick = { item ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedDeviceUris = setOf(item.uri.toString())
                            } else {
                                toggleDeviceSelection(item)
                            }
                        },
                        onToggleFavoriteRecent = onToggleFavoriteRecent,
                        onToggleFavoriteDevice = onToggleFavoriteDevicePdf,
                        onRenameRecent = ::startRename,
                        onShareRecent = onShareRecent,
                        onEditRecent = onEditRecent,
                        onMergeRecent = onMergeRecent,
                        onSplitRecent = onSplitRecent,
                        onDeleteRecent = { onRemoveRecent(it.fileHash) },
                        onShareDevice = onShareDevicePdf,
                        onEditDevice = onEditDevicePdf,
                        onMergeDevice = onMergeDevicePdf,
                        onSplitDevice = onSplitDevicePdf,
                        onDeleteDevice = { singleDeleteDeviceTarget = it }
                    )

                    else -> DevicePdfsTabContent(
                        isLoadingDevicePdfs = isLoadingDevicePdfs,
                        allDeviceCount = devicePdfs.size,
                        sortedDevicePdfs = sortedDevicePdfs,
                        hasStoragePermission = hasStoragePermission,
                        gridState = deviceGridState,
                        favoriteUris = favoriteUris,
                        isSelectionMode = isSelectionMode,
                        selectedDeviceUris = selectedDeviceUris,
                        driveAccountEmail = driveAccountEmail,
                        isSyncingDrive = isSyncingDrive,
                        onRequestStorageAccess = requestStorageAccess,
                        onCardClick = { item ->
                            if (isSelectionMode) toggleDeviceSelection(item) else onOpenDevicePdf(item)
                        },
                        onCardLongClick = { item ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedDeviceUris = setOf(item.uri.toString())
                            } else {
                                toggleDeviceSelection(item)
                            }
                        },
                        onToggleFavorite = onToggleFavoriteDevicePdf,
                        onShare = onShareDevicePdf,
                        onEdit = onEditDevicePdf,
                        onMerge = onMergeDevicePdf,
                        onSplit = onSplitDevicePdf,
                        onDelete = { singleDeleteDeviceTarget = it },
                        onUploadToDrive = { onUploadDevicePdfsToDrive(listOf(it)) }
                    )
                }
            }

            if (isOpeningDocument) {
                OpeningDocumentOverlay(strings.openingPdf)
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
            RenamePdfDialog(
                renameText = renameText,
                onTextChange = { renameText = it },
                onConfirm = {
                    onRenameRecent(targetToRename, renameText)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null }
            )
        }

        if (showPermanentDeleteDialog) {
            PermanentDeleteDialog(
                count = selectedDevicesList.size,
                onConfirm = {
                    showPermanentDeleteDialog = false
                    onDeleteDevicePdfs(selectedDevicesList)
                    clearSelection()
                },
                onDismiss = { showPermanentDeleteDialog = false }
            )
        }

        singleDeleteDeviceTarget?.let { target ->
            PermanentDeleteDialog(
                count = 1,
                onConfirm = {
                    singleDeleteDeviceTarget = null
                    onDeleteDevicePdfs(listOf(target))
                },
                onDismiss = { singleDeleteDeviceTarget = null }
            )
        }
    }
}
