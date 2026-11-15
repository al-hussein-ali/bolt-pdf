package com.velocity.pdf

import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.velocity.pdf.ui.AppLanguage
import com.velocity.pdf.ui.HomeScreen
import com.velocity.pdf.ui.ImageToPdfEntry
import com.velocity.pdf.ui.ImagesToPdfScreen
import com.velocity.pdf.ui.LocalAppStrings
import com.velocity.pdf.ui.MarkdownReaderScreen
import com.velocity.pdf.ui.MergePdfEntry
import com.velocity.pdf.ui.MergePdfsScreen
import com.velocity.pdf.ui.ReaderScreen
import com.velocity.pdf.ui.SettingsScreen
import com.velocity.pdf.ui.SplitPdfDialog
import com.velocity.pdf.ui.SplitTarget
import com.velocity.pdf.ui.VelocityPdfTheme
import com.velocity.pdf.ui.stringsFor
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private enum class HomeSubScreen {
    HOME,
    SETTINGS,
    MERGE,
    IMAGES_TO_PDF
}

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val readerState by viewModel.readerState.collectAsState()
            val markdownState by viewModel.markdownState.collectAsState()
            val recentFiles by viewModel.recentFiles.collectAsState()
            val isLoadingRecents by viewModel.isLoadingRecents.collectAsState()
            val devicePdfs by viewModel.devicePdfs.collectAsState()
            val isLoadingDevicePdfs by viewModel.isLoadingDevicePdfs.collectAsState()
            val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
            val sortOption by viewModel.sortOption.collectAsState()
            val dateFilter by viewModel.dateFilter.collectAsState()
            val progressFilter by viewModel.progressFilter.collectAsState()
            val sizeFilter by viewModel.sizeFilter.collectAsState()
            val favoriteHashes by viewModel.favoriteHashes.collectAsState()
            val favoriteUris by viewModel.favoriteUris.collectAsState()
            val isOpeningDocument by viewModel.isOpeningDocument.collectAsState()
            val isToolProcessing by viewModel.isToolProcessing.collectAsState()
            val globalDarkMode by viewModel.globalDarkMode.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val orientationMode by viewModel.orientationMode.collectAsState()
            val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
            val isSyncingDrive by viewModel.isSyncingDrive.collectAsState()
            val driveSyncConflicts by viewModel.driveSyncConflicts.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()

            var subScreen by remember { mutableStateOf(HomeSubScreen.HOME) }
            var mergeEntries by remember { mutableStateOf<List<MergePdfEntry>>(emptyList()) }
            var imageEntries by remember { mutableStateOf<List<ImageToPdfEntry>>(emptyList()) }
            var splitTarget by remember { mutableStateOf<SplitTarget?>(null) }

            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.openFromUri(uri)
                }
            }

            val markdownPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.openMarkdownFromUri(uri)
                }
            }

            val googleSignInLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                viewModel.onGoogleSignInResult(result.data)
            }

            val driveAuthLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    viewModel.retryPendingDriveAction()
                }
            }

            LaunchedEffect(orientationMode, readerState == null) {
                requestedOrientation = if (readerState == null) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    when (orientationMode) {
                        OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        OrientationMode.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.snackbarMessages.collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            LaunchedEffect(Unit) {
                viewModel.driveAuthPendingIntent.collect { pendingIntent ->
                    driveAuthLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            viewModel.refreshRecentFiles()
                            viewModel.refreshDevicePdfs(onlyIfNeeded = true)
                        }
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.flushProgress()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val darkTheme = readerState?.progress?.darkModeEnabled ?: globalDarkMode
            val strings = stringsFor(appLanguage)
            val layoutDirection = if (appLanguage == AppLanguage.ARABIC) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            val shareUriAction: (Uri, String) -> Unit = { shareUri, fileName ->
                val resolvedUri = if (shareUri.scheme == "file" && shareUri.path != null) {
                    FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        File(shareUri.path!!)
                    )
                } else {
                    shareUri
                }
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, resolvedUri)
                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                    clipData = ClipData.newRawUri(fileName, resolvedUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, strings.sharePdf).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { startActivity(chooser) }
            }

            CompositionLocalProvider(
                LocalAppStrings provides strings,
                LocalLayoutDirection provides layoutDirection
            ) {
                VelocityPdfTheme(darkTheme = darkTheme) {
                    val activeReader = readerState
                    val activeMarkdown = markdownState
                    when {
                        activeReader != null -> {
                            BackHandler {
                                viewModel.closeDocument()
                            }
                            ReaderScreen(
                                state = activeReader,
                                orientationMode = orientationMode,
                                isOpeningDocument = isOpeningDocument,
                                snackbarHostState = snackbarHostState,
                                viewModel = viewModel,
                                onBack = { viewModel.closeDocument() }
                            )
                        }

                        activeMarkdown != null -> {
                            BackHandler {
                                viewModel.closeMarkdown()
                            }
                            MarkdownReaderScreen(
                                document = activeMarkdown,
                                onBack = { viewModel.closeMarkdown() }
                            )
                        }

                        subScreen == HomeSubScreen.SETTINGS -> {
                            BackHandler {
                                subScreen = HomeSubScreen.HOME
                            }
                            SettingsScreen(
                                darkMode = globalDarkMode,
                                language = appLanguage,
                                driveAccountEmail = driveAccountEmail,
                                onBack = { subScreen = HomeSubScreen.HOME },
                                onToggleDarkMode = { enabled -> viewModel.setGlobalDarkMode(enabled) },
                                onSelectLanguage = { lang -> viewModel.setAppLanguage(lang) },
                                onConnectGoogleAccount = {
                                    googleSignInLauncher.launch(viewModel.driveSyncManager.getSignInIntent())
                                },
                                onDisconnectGoogleAccount = { viewModel.signOutGoogleDrive() },
                                onOpenMergePdfs = {
                                    mergeEntries = emptyList()
                                    subScreen = HomeSubScreen.MERGE
                                },
                                onOpenImagesToPdf = {
                                    imageEntries = emptyList()
                                    subScreen = HomeSubScreen.IMAGES_TO_PDF
                                },
                                onPickMarkdown = {
                                    markdownPickerLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                                }
                            )
                        }

                        subScreen == HomeSubScreen.MERGE -> {
                            BackHandler(enabled = !isToolProcessing) {
                                subScreen = HomeSubScreen.HOME
                            }
                            MergePdfsScreen(
                                items = mergeEntries,
                                isProcessing = isToolProcessing,
                                onBack = { subScreen = HomeSubScreen.HOME },
                                onAddPdfs = { uris ->
                                    val added = uris.map { uri ->
                                        MergePdfEntry(
                                            id = UUID.randomUUID().toString(),
                                            displayName = viewModel.resolveDisplayNameForUri(uri),
                                            uri = uri,
                                            fallbackPath = null,
                                            pageCount = null
                                        )
                                    }
                                    mergeEntries = mergeEntries + added
                                    added.forEach { entry ->
                                        scope.launch {
                                            val count = viewModel.resolvePdfPageCount(entry.uri, entry.fallbackPath)
                                            if (count != null) {
                                                mergeEntries = mergeEntries.map {
                                                    if (it.id == entry.id) it.copy(pageCount = count) else it
                                                }
                                            }
                                        }
                                    }
                                },
                                onMoveItem = { from, to ->
                                    mergeEntries = mergeEntries.moved(from, to)
                                },
                                onRemoveItem = { index ->
                                    mergeEntries = mergeEntries.removedAt(index)
                                },
                                onMergeToUri = { outUri ->
                                    viewModel.mergePdfsToUri(mergeEntries, outUri) { ok ->
                                        if (ok) {
                                            subScreen = HomeSubScreen.HOME
                                            viewModel.openFromUri(outUri)
                                        }
                                    }
                                }
                            )
                        }

                        subScreen == HomeSubScreen.IMAGES_TO_PDF -> {
                            BackHandler(enabled = !isToolProcessing) {
                                subScreen = HomeSubScreen.HOME
                            }
                            ImagesToPdfScreen(
                                items = imageEntries,
                                isProcessing = isToolProcessing,
                                onBack = { subScreen = HomeSubScreen.HOME },
                                onAddImages = { uris ->
                                    val added = uris.map { uri ->
                                        ImageToPdfEntry(
                                            id = UUID.randomUUID().toString(),
                                            displayName = viewModel.resolveDisplayNameForUri(uri, "Image"),
                                            uri = uri
                                        )
                                    }
                                    imageEntries = imageEntries + added
                                },
                                onMoveItem = { from, to ->
                                    imageEntries = imageEntries.moved(from, to)
                                },
                                onRemoveItem = { index ->
                                    imageEntries = imageEntries.removedAt(index)
                                },
                                onCreatePdfToUri = { outUri ->
                                    viewModel.imagesToPdfToUri(imageEntries, outUri) { ok ->
                                        if (ok) {
                                            subScreen = HomeSubScreen.HOME
                                            viewModel.openFromUri(outUri)
                                        }
                                    }
                                }
                            )
                        }

                        else -> {
                            HomeScreen(
                                recentFiles = recentFiles,
                                isLoadingRecents = isLoadingRecents,
                                devicePdfs = devicePdfs,
                                isLoadingDevicePdfs = isLoadingDevicePdfs,
                                hasStoragePermission = hasStoragePermission,
                                sortOption = sortOption,
                                dateFilter = dateFilter,
                                progressFilter = progressFilter,
                                sizeFilter = sizeFilter,
                                favoriteHashes = favoriteHashes,
                                favoriteUris = favoriteUris,
                                isOpeningDocument = isOpeningDocument,
                                snackbarHostState = snackbarHostState,
                                onOpenSettings = { subScreen = HomeSubScreen.SETTINGS },
                                onSelectSortOption = { option -> viewModel.setSortOption(option) },
                                onSelectDateFilter = { filter -> viewModel.setDateFilter(filter) },
                                onSelectProgressFilter = { filter -> viewModel.setProgressFilter(filter) },
                                onSelectSizeFilter = { filter -> viewModel.setSizeFilter(filter) },
                                onClearFilters = { viewModel.clearFilters() },
                                onRefreshDevicePdfs = { viewModel.refreshDevicePdfs() },
                                onPickFile = {
                                    filePickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                onOpenRecent = { item -> viewModel.openRecent(item) },
                                onOpenDevicePdf = { item -> viewModel.openFromUri(item.uri) },
                                onToggleFavoriteRecent = { item -> viewModel.toggleFavorite(item) },
                                onToggleFavoriteDevicePdf = { item -> viewModel.toggleDevicePdfFavorite(item) },
                                onRenameRecent = { item, newName -> viewModel.renameRecentFile(item, newName) },
                                onShareRecent = { item ->
                                    viewModel.prepareShareRecentPdf(item, shareUriAction)
                                },
                                onShareDevicePdf = { item ->
                                    shareUriAction(item.uri, item.displayName)
                                },
                                onMergeRecent = { item ->
                                    mergeEntries = listOf(
                                        MergePdfEntry(
                                            id = UUID.randomUUID().toString(),
                                            displayName = item.displayName,
                                            uri = Uri.parse(item.fileUri),
                                            fallbackPath = item.fallbackCachePath,
                                            pageCount = item.totalPages.toInt()
                                        )
                                    )
                                    subScreen = HomeSubScreen.MERGE
                                },
                                onSplitRecent = { item ->
                                    splitTarget = SplitTarget(
                                        displayName = item.displayName,
                                        uri = Uri.parse(item.fileUri),
                                        fallbackPath = item.fallbackCachePath,
                                        totalPages = item.totalPages.toInt()
                                    )
                                },
                                onMergeDevicePdf = { item ->
                                    val entryId = UUID.randomUUID().toString()
                                    mergeEntries = listOf(
                                        MergePdfEntry(
                                            id = entryId,
                                            displayName = item.displayName,
                                            uri = item.uri,
                                            fallbackPath = null,
                                            pageCount = null
                                        )
                                    )
                                    subScreen = HomeSubScreen.MERGE
                                    scope.launch {
                                        val count = viewModel.resolvePdfPageCount(item.uri, null)
                                        if (count != null) {
                                            mergeEntries = mergeEntries.map {
                                                if (it.id == entryId) it.copy(pageCount = count) else it
                                            }
                                        }
                                    }
                                },
                                onSplitDevicePdf = { item ->
                                    scope.launch {
                                        val count = viewModel.resolvePdfPageCount(item.uri, null)
                                        if (count != null && count > 0) {
                                            splitTarget = SplitTarget(
                                                displayName = item.displayName,
                                                uri = item.uri,
                                                fallbackPath = null,
                                                totalPages = count
                                            )
                                        } else {
                                            snackbarHostState.showSnackbar(stringsFor(appLanguage).unableToReadPageCount)
                                        }
                                    }
                                },
                                onRemoveRecent = { hash -> viewModel.removeRecentFile(hash) },
                                onSetFavoritesBatch = { recents, devices, makeFav ->
                                    viewModel.setFavoritesBatch(recents, devices, makeFav)
                                },
                                onRemoveRecentFiles = { hashes -> viewModel.removeRecentFiles(hashes) },
                                onDeleteDevicePdfs = { items -> viewModel.deleteDevicePdfs(items) },
                                driveAccountEmail = driveAccountEmail,
                                isSyncingDrive = isSyncingDrive,
                                driveSyncConflicts = driveSyncConflicts,
                                onConnectGoogleAccount = {
                                    googleSignInLauncher.launch(viewModel.driveSyncManager.getSignInIntent())
                                },
                                onDisconnectGoogleAccount = { viewModel.signOutGoogleDrive() },
                                onUploadToDrive = { items -> viewModel.uploadRecentToDrive(items) },
                                onUploadDevicePdfsToDrive = { items -> viewModel.uploadDevicePdfsToDrive(items) },
                                onSyncWithDrive = { viewModel.syncWithGoogleDrive() },
                                onResolveDriveConflicts = { choices -> viewModel.resolveDriveConflicts(choices) },
                                onDismissDriveConflicts = { viewModel.dismissDriveConflicts() }
                            )

                            val activeSplit = splitTarget
                            if (activeSplit != null) {
                                SplitPdfDialog(
                                    target = activeSplit,
                                    onDismiss = { splitTarget = null },
                                    onConfirmSplit = { ranges, outUri ->
                                        splitTarget = null
                                        viewModel.splitPdfRangesToUri(activeSplit, ranges, outUri) { ok, isZip ->
                                            if (ok && !isZip) {
                                                viewModel.openFromUri(outUri)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data ?: intent.clipData?.getItemAt(0)?.uri
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
                ?: intent.clipData?.getItemAt(0)?.uri
            else -> null
        }
        if (uri != null) {
            val mime = intent.type ?: contentResolver.getType(uri)
            val name = viewModel.resolveDisplayNameForUri(uri, "")
            val isMarkdown = mime?.equals("text/markdown", ignoreCase = true) == true ||
                name.endsWith(".md", ignoreCase = true) ||
                name.endsWith(".markdown", ignoreCase = true)
            if (isMarkdown) {
                viewModel.openMarkdownFromUri(uri)
            } else {
                viewModel.openFromUri(uri)
            }
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply {
        val item = removeAt(from)
        add(to, item)
    }
}

private fun <T> List<T>.removedAt(index: Int): List<T> {
    if (index !in indices) return this
    return toMutableList().apply { removeAt(index) }
}
