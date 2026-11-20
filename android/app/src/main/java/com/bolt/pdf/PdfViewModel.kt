package com.bolt.pdf

import android.Manifest
import android.app.Application
import android.app.LocaleManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bolt.pdf.ui.EditorPageItem
import com.bolt.pdf.ui.EditorPageKind
import com.bolt.pdf.ui.ImageToPdfEntry
import com.bolt.pdf.ui.MarkdownDocumentState
import com.bolt.pdf.ui.MergePdfEntry
import com.bolt.pdf.ui.PageRangeSpec
import com.bolt.pdf.ui.PdfEditorSession
import com.bolt.pdf.ui.SYSTEM_LANGUAGE_TAG
import com.bolt.pdf.ui.SplitTarget
import com.bolt.pdf.ui.createLocalizedContext
import com.bolt.pdf.ui.renderTextBoxOverlayPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.pdf_core.DocumentOpenResult
import uniffi.pdf_core.HighlightRecord
import uniffi.pdf_core.NormalizedRect
import uniffi.pdf_core.PageDimension
import uniffi.pdf_core.PageRotation
import uniffi.pdf_core.PageTextContent
import uniffi.pdf_core.PdfEngine
import uniffi.pdf_core.PdfException
import uniffi.pdf_core.ReadingProgress
import uniffi.pdf_core.SearchMatch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class OrientationMode {
    PORTRAIT,
    LANDSCAPE,
    REVERSE_LANDSCAPE
}

enum class PageLayoutMode {
    VERTICAL_SCROLL,
    HORIZONTAL_PAGER
}

enum class HomeSortOption(val label: String) {
    RECENTLY_OPENED("Newest first"),
    OLDEST_FIRST("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_OR_PAGES("Most pages / Largest"),
    SMALLEST_OR_FEWEST("Fewest pages / Smallest")
}

enum class HomeDateFilter(val label: String) {
    ALL("All time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year")
}

enum class HomeProgressFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    IN_PROGRESS("In progress"),
    COMPLETED("Finished")
}

enum class HomeSizeFilter(val label: String) {
    ALL("All sizes"),
    SMALL("Under 1 MB"),
    MEDIUM("1 – 10 MB"),
    LARGE("Over 10 MB")
}

data class DevicePdfItem(
    val uri: Uri,
    val displayName: String,
    val folderName: String,
    val sizeBytes: Long,
    val dateModifiedSec: Long,
)

data class ReaderUiState(
    val progress: ReadingProgress,
    val dimensions: List<PageDimension>,
    val renderGeneration: Long = 0L,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val activeMatchIndex: Int = -1,
    val isSearching: Boolean = false,
    val highlights: List<HighlightRecord> = emptyList(),
    val pageLayoutMode: PageLayoutMode = PageLayoutMode.VERTICAL_SCROLL,
)

data class DriveSyncConflict(
    val fileHash: String,
    val displayName: String,
    val localProgress: ReadingProgress,
    val localIsFavorite: Boolean,
    val remoteItem: DriveManifestItem
)

enum class ConflictResolutionChoice {
    KEEP_LOCAL,
    USE_DRIVE
}

@OptIn(FlowPreview::class)
class PdfViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MAX_CACHE_BYTES: Long = 150L * 1024L * 1024L // 150 MB
        private const val MAX_BASE_EDGE_PX: Int = 2560
        private const val MAX_TILE_EDGE_PX: Int = 4096
    }

    private val appContext = application.applicationContext
    private val engine: PdfEngine by lazy {
        NativeZeroCopy
        val dbPath = File(appContext.filesDir, "velocity_pdf.db").absolutePath
        val pdfiumDir = appContext.applicationInfo.nativeLibraryDir
        PdfEngine(dbPath, pdfiumDir)
    }

    private val prefs = appContext.getSharedPreferences("velocity_pdf_prefs", Context.MODE_PRIVATE)
    private val initialSystemDark = (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val _recentFiles = MutableStateFlow<List<ReadingProgress>>(emptyList())
    val recentFiles: StateFlow<List<ReadingProgress>> = _recentFiles.asStateFlow()

    private val _isLoadingRecents = MutableStateFlow(true)
    val isLoadingRecents: StateFlow<Boolean> = _isLoadingRecents.asStateFlow()

    private val _isOpeningDocument = MutableStateFlow(false)
    val isOpeningDocument: StateFlow<Boolean> = _isOpeningDocument.asStateFlow()

    private val _isSharingDocument = MutableStateFlow(false)
    val isSharingDocument: StateFlow<Boolean> = _isSharingDocument.asStateFlow()

    private inline fun <reified T : Enum<T>> loadEnumPref(key: String, default: T): T {
        val raw = prefs.getString(key, default.name) ?: default.name
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    private fun loadSavedLanguageTag(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = appContext.getSystemService(LocaleManager::class.java)
            val appLocales = localeManager?.applicationLocales
            if (appLocales != null && !appLocales.isEmpty) {
                return appLocales[0].toLanguageTag()
            }
        }
        val raw = prefs.getString("app_language", null) ?: return SYSTEM_LANGUAGE_TAG
        return when (raw) {
            "ENGLISH" -> "en"
            "ARABIC" -> "ar"
            else -> raw
        }
    }

    private fun localizedContext(): Context =
        createLocalizedContext(appContext, _appLanguageTag.value)

    private fun str(@StringRes resId: Int, vararg args: Any): String {
        val ctx = localizedContext()
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }

    private fun unknownErrorText(): String = str(R.string.msg_unknown_error)

    private fun saveEnumPref(key: String, value: Enum<*>) {
        prefs.edit().putString(key, value.name).apply()
    }

    private val _sortOption = MutableStateFlow(loadEnumPref("home_sort_option", HomeSortOption.RECENTLY_OPENED))
    val sortOption: StateFlow<HomeSortOption> = _sortOption.asStateFlow()

    private val _dateFilter = MutableStateFlow(loadEnumPref("home_date_filter", HomeDateFilter.ALL))
    val dateFilter: StateFlow<HomeDateFilter> = _dateFilter.asStateFlow()

    private val _progressFilter = MutableStateFlow(loadEnumPref("home_progress_filter", HomeProgressFilter.ALL))
    val progressFilter: StateFlow<HomeProgressFilter> = _progressFilter.asStateFlow()

    private val _sizeFilter = MutableStateFlow(loadEnumPref("home_size_filter", HomeSizeFilter.ALL))
    val sizeFilter: StateFlow<HomeSizeFilter> = _sizeFilter.asStateFlow()

    private val _favoriteHashes = MutableStateFlow(
        prefs.getStringSet("favorite_hashes", emptySet())?.toSet() ?: emptySet()
    )
    val favoriteHashes: StateFlow<Set<String>> = _favoriteHashes.asStateFlow()

    private val _favoriteUris = MutableStateFlow(
        prefs.getStringSet("favorite_uris", emptySet())?.toSet() ?: emptySet()
    )
    val favoriteUris: StateFlow<Set<String>> = _favoriteUris.asStateFlow()

    private val _devicePdfs = MutableStateFlow<List<DevicePdfItem>>(emptyList())
    val devicePdfs: StateFlow<List<DevicePdfItem>> = _devicePdfs.asStateFlow()

    private val _isLoadingDevicePdfs = MutableStateFlow(false)
    val isLoadingDevicePdfs: StateFlow<Boolean> = _isLoadingDevicePdfs.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(checkStoragePermission())
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _readerState = MutableStateFlow<ReaderUiState?>(null)
    val readerState: StateFlow<ReaderUiState?> = _readerState.asStateFlow()

    private val _globalDarkMode = MutableStateFlow(
        if (prefs.contains("global_dark_mode")) prefs.getBoolean("global_dark_mode", false) else initialSystemDark
    )
    val globalDarkMode: StateFlow<Boolean> = _globalDarkMode.asStateFlow()

    private val _appLanguageTag = MutableStateFlow(loadSavedLanguageTag())
    val appLanguageTag: StateFlow<String> = _appLanguageTag.asStateFlow()

    private val _markdownState = MutableStateFlow<MarkdownDocumentState?>(null)
    val markdownState: StateFlow<MarkdownDocumentState?> = _markdownState.asStateFlow()

    private val _editorSession = MutableStateFlow<PdfEditorSession?>(null)
    val editorSession: StateFlow<PdfEditorSession?> = _editorSession.asStateFlow()

    private val _isToolProcessing = MutableStateFlow(false)
    val isToolProcessing: StateFlow<Boolean> = _isToolProcessing.asStateFlow()

    private val _orientationMode = MutableStateFlow(OrientationMode.PORTRAIT)
    val orientationMode: StateFlow<OrientationMode> = _orientationMode.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    val driveSyncManager = GoogleDriveSyncManager(appContext)
    private val _driveAccountEmail = MutableStateFlow(driveSyncManager.getLastSignedInEmail())
    val driveAccountEmail: StateFlow<String?> = _driveAccountEmail.asStateFlow()

    private val _isSyncingDrive = MutableStateFlow(false)
    val isSyncingDrive: StateFlow<Boolean> = _isSyncingDrive.asStateFlow()

    private val _driveSyncConflicts = MutableStateFlow<List<DriveSyncConflict>>(emptyList())
    val driveSyncConflicts: StateFlow<List<DriveSyncConflict>> = _driveSyncConflicts.asStateFlow()

    private val _driveAuthPendingIntent = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 2)
    val driveAuthPendingIntent: SharedFlow<PendingIntent> = _driveAuthPendingIntent.asSharedFlow()

    private var pendingDriveRetryAction: (() -> Unit)? = null

    private val pendingProgressToSave = MutableStateFlow<ReadingProgress?>(null)
    private var searchJob: Job? = null
    private var refreshRecentsJob: Job? = null
    private var refreshDevicePdfsJob: Job? = null

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun setSortOption(option: HomeSortOption) {
        _sortOption.value = option
        saveEnumPref("home_sort_option", option)
    }

    fun setDateFilter(filter: HomeDateFilter) {
        _dateFilter.value = filter
        saveEnumPref("home_date_filter", filter)
    }

    fun setProgressFilter(filter: HomeProgressFilter) {
        _progressFilter.value = filter
        saveEnumPref("home_progress_filter", filter)
    }

    fun setSizeFilter(filter: HomeSizeFilter) {
        _sizeFilter.value = filter
        saveEnumPref("home_size_filter", filter)
    }

    fun clearFilters() {
        setDateFilter(HomeDateFilter.ALL)
        setProgressFilter(HomeProgressFilter.ALL)
        setSizeFilter(HomeSizeFilter.ALL)
    }

    fun toggleFavorite(progress: ReadingProgress) {
        val currentHashes = _favoriteHashes.value
        val currentUris = _favoriteUris.value
        val isFav = progress.fileHash in currentHashes || progress.fileUri in currentUris
        val updatedHashes = if (isFav) currentHashes - progress.fileHash else currentHashes + progress.fileHash
        val updatedUris = if (isFav) currentUris - progress.fileUri else currentUris + progress.fileUri
        _favoriteHashes.value = updatedHashes
        _favoriteUris.value = updatedUris
        prefs.edit()
            .putStringSet("favorite_hashes", updatedHashes)
            .putStringSet("favorite_uris", updatedUris)
            .apply()
    }

    fun toggleDevicePdfFavorite(item: DevicePdfItem) {
        val uriStr = item.uri.toString()
        val matchingRecent = _recentFiles.value.firstOrNull { it.fileUri == uriStr }
        if (matchingRecent != null) {
            toggleFavorite(matchingRecent)
            return
        }
        val currentUris = _favoriteUris.value
        val updatedUris = if (uriStr in currentUris) currentUris - uriStr else currentUris + uriStr
        _favoriteUris.value = updatedUris
        prefs.edit().putStringSet("favorite_uris", updatedUris).apply()
    }

    fun setFavoritesBatch(
        recentItems: List<ReadingProgress>,
        deviceItems: List<DevicePdfItem>,
        makeFavorite: Boolean,
    ) {
        if (recentItems.isEmpty() && deviceItems.isEmpty()) return
        val recentHashes = recentItems.map { it.fileHash }.toSet()
        val allUris = (recentItems.map { it.fileUri } + deviceItems.map { it.uri.toString() }).toSet()
        val updatedHashes = if (makeFavorite) _favoriteHashes.value + recentHashes else _favoriteHashes.value - recentHashes
        val updatedUris = if (makeFavorite) _favoriteUris.value + allUris else _favoriteUris.value - allUris
        _favoriteHashes.value = updatedHashes
        _favoriteUris.value = updatedUris
        prefs.edit()
            .putStringSet("favorite_hashes", updatedHashes)
            .putStringSet("favorite_uris", updatedUris)
            .apply()
        val count = recentItems.size + deviceItems.size
        _snackbarMessages.tryEmit(
            if (makeFavorite) {
                str(R.string.msg_added_to_favorites, count)
            } else {
                str(R.string.msg_removed_from_favorites, count)
            }
        )
    }

    fun removeRecentFiles(fileHashes: List<String>) {
        if (fileHashes.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = File(appContext.filesDir, "recent_cache")
            fileHashes.forEach { fileHash ->
                runCatching { engine.removeRecentFile(fileHash) }
                val cached = File(cacheDir, "$fileHash.pdf")
                if (cached.exists()) cached.delete()
            }
            refreshRecentFiles()
            _snackbarMessages.tryEmit(
                str(R.string.msg_removed_from_recent, fileHashes.size)
            )
        }
    }

    fun deleteDevicePdfs(items: List<DevicePdfItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            val deletedUris = mutableSetOf<String>()
            items.forEach { item ->
                val deleted = runCatching {
                    val uri = item.uri
                    if (uri.scheme == "file" && uri.path != null) {
                        File(uri.path!!).delete()
                    } else {
                        appContext.contentResolver.delete(uri, null, null) > 0
                    }
                }.getOrDefault(false)
                if (deleted) {
                    deletedCount++
                    deletedUris += item.uri.toString()
                }
            }

            if (deletedUris.isNotEmpty()) {
                _devicePdfs.update { list -> list.filterNot { it.uri.toString() in deletedUris } }
                val updatedFavUris = _favoriteUris.value - deletedUris
                if (updatedFavUris != _favoriteUris.value) {
                    _favoriteUris.value = updatedFavUris
                    prefs.edit().putStringSet("favorite_uris", updatedFavUris).apply()
                }
                val matchingHashes = _recentFiles.value
                    .filter { it.fileUri in deletedUris }
                    .map { it.fileHash }
                if (matchingHashes.isNotEmpty()) {
                    val cacheDir = File(appContext.filesDir, "recent_cache")
                    matchingHashes.forEach { hash ->
                        runCatching { engine.removeRecentFile(hash) }
                        File(cacheDir, "$hash.pdf").takeIf { it.exists() }?.delete()
                    }
                    refreshRecentFiles()
                }
            }

            _snackbarMessages.tryEmit(
                when {
                    deletedCount == items.size -> str(R.string.msg_deleted_from_device, deletedCount)
                    deletedCount > 0 -> str(R.string.msg_deleted_partial, deletedCount, items.size)
                    else -> str(R.string.msg_unable_to_delete)
                }
            )
        }
    }

    fun refreshDevicePdfs(onlyIfNeeded: Boolean = false) {
        val granted = checkStoragePermission()
        val permissionChanged = granted != _hasStoragePermission.value
        _hasStoragePermission.value = granted
        if (onlyIfNeeded && _devicePdfs.value.isNotEmpty() && !permissionChanged) {
            return
        }
        refreshDevicePdfsJob?.cancel()
        refreshDevicePdfsJob = viewModelScope.launch(Dispatchers.IO) {
            if (_devicePdfs.value.isEmpty()) {
                _isLoadingDevicePdfs.value = true
            }
            try {
                val results = linkedMapOf<String, DevicePdfItem>()

                // 1. Fast MediaStore query (works for Downloads/Documents even without All Files Access)
                runCatching {
                    val collection = MediaStore.Files.getContentUri("external")
                    val projection = arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.DATE_MODIFIED,
                        MediaStore.Files.FileColumns.DATA
                    )
                    val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                        "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ? OR " +
                        "LOWER(${MediaStore.Files.FileColumns.DATA}) LIKE ?"
                    val selectionArgs = arrayOf("application/pdf", "%.pdf", "%.pdf")
                    appContext.contentResolver.query(
                        collection,
                        projection,
                        selection,
                        selectionArgs,
                        "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                        val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                        val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                        while (cursor.moveToNext()) {
                            val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                            val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                            val name = (if (nameCol >= 0) cursor.getString(nameCol) else null)
                                ?.takeIf { it.isNotBlank() }
                                ?: path?.let { File(it).name }?.takeIf { it.isNotBlank() }
                                ?: continue
                            val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                            val dateMod = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                            val folder = path?.let { File(it).parentFile?.name } ?: "Device"
                            val contentUri = ContentUris.withAppendedId(collection, id)
                            val key = path?.lowercase() ?: "${name.lowercase()}_$size"
                            results.putIfAbsent(
                                key,
                                DevicePdfItem(
                                    uri = contentUri,
                                    displayName = name,
                                    folderName = folder,
                                    sizeBytes = size,
                                    dateModifiedSec = dateMod
                                )
                            )
                        }
                    }
                }

                // 2. Full storage scan (internal + removable SD card) when storage permission is granted
                if (granted) {
                    val roots = linkedSetOf<File>()
                    val primaryRoot = Environment.getExternalStorageDirectory()
                    if (primaryRoot != null) {
                        roots.add(primaryRoot)
                    }
                    appContext.getExternalFilesDirs(null).forEach { appDir ->
                        val path = appDir?.absolutePath ?: return@forEach
                        val androidIdx = path.indexOf("/Android/")
                        if (androidIdx > 0) {
                            roots.add(File(path.substring(0, androidIdx)))
                        }
                    }
                    for (root in roots) {
                        if (root.exists() && root.canRead()) {
                            scanPdfDirectory(root, results)
                        }
                    }
                }

                _devicePdfs.value = results.values.toList()
            } finally {
                _isLoadingDevicePdfs.value = false
            }
        }
    }

    private fun scanPdfDirectory(dir: File, out: MutableMap<String, DevicePdfItem>) {
        val children = dir.listFiles() ?: return
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (name.equals("Android", ignoreCase = true)) continue
                scanPdfDirectory(file, out)
            } else if (name.endsWith(".pdf", ignoreCase = true) && file.length() > 0L) {
                val key = file.absolutePath.lowercase()
                if (!out.containsKey(key)) {
                    out[key] = DevicePdfItem(
                        uri = Uri.fromFile(file),
                        displayName = name,
                        folderName = file.parentFile?.name ?: "Storage",
                        sizeBytes = file.length(),
                        dateModifiedSec = file.lastModified() / 1000L
                    )
                }
            }
        }
    }

    private val pageTextCache = object : LinkedHashMap<String, PageTextContent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageTextContent>?): Boolean {
            return size > 16
        }
    }

    private val bitmapPool = ArrayDeque<Bitmap>()
    private val bitmapPoolLock = Any()

    private fun acquireBitmap(width: Int, height: Int): Bitmap {
        synchronized(bitmapPoolLock) {
            val idx = bitmapPool.indexOfFirst { !it.isRecycled && it.isMutable && it.width == width && it.height == height }
            if (idx >= 0) {
                return bitmapPool.removeAt(idx)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun releaseBitmapToPool(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled || !bitmap.isMutable) return
        synchronized(bitmapPoolLock) {
            if (bitmapPool.size >= 6) {
                bitmapPool.removeFirst().recycle()
            }
            bitmapPool.addLast(bitmap)
        }
    }

    init {
        refreshRecentFiles()
        viewModelScope.launch(Dispatchers.IO) {
            pendingProgressToSave
                .filterNotNull()
                .debounce(300L)
                .collect { progress ->
                    runCatching { engine.saveProgress(progress) }
                }
        }
    }

    fun refreshRecentFiles(force: Boolean = false) {
        if (!force && refreshRecentsJob?.isActive == true) {
            return
        }
        refreshRecentsJob?.cancel()
        refreshRecentsJob = viewModelScope.launch(Dispatchers.IO) {
            if (_recentFiles.value.isEmpty()) {
                _isLoadingRecents.value = true
            }
            try {
                var result: Result<List<ReadingProgress>> = Result.failure(IllegalStateException("Not run"))
                repeat(3) { attempt ->
                    result = runCatching { engine.getRecentFiles(50u) }
                    if (result.isSuccess) return@repeat
                    if (attempt < 2) delay(150L)
                }
                result.onSuccess { _recentFiles.value = it }
            } finally {
                _isLoadingRecents.value = false
            }
        }
    }

    fun toggleGlobalDarkMode() {
        val activeReader = _readerState.value
        if (activeReader != null) {
            val next = !activeReader.progress.darkModeEnabled
            _readerState.update { state ->
                state?.let {
                    val updated = it.progress.copy(darkModeEnabled = next)
                    queueProgressSave(updated)
                    it.copy(progress = updated, renderGeneration = it.renderGeneration + 1)
                }
            }
        } else {
            val next = !_globalDarkMode.value
            _globalDarkMode.value = next
            prefs.edit().putBoolean("global_dark_mode", next).apply()
        }
    }

    fun togglePageLayoutMode() {
        _readerState.update { state ->
            state?.let {
                val next = if (it.pageLayoutMode == PageLayoutMode.VERTICAL_SCROLL) {
                    PageLayoutMode.HORIZONTAL_PAGER
                } else {
                    PageLayoutMode.VERTICAL_SCROLL
                }
                it.copy(pageLayoutMode = next)
            }
        }
    }

    fun cycleOrientationMode() {
        _orientationMode.update { mode ->
            when (mode) {
                OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
                OrientationMode.LANDSCAPE, OrientationMode.REVERSE_LANDSCAPE -> OrientationMode.PORTRAIT
            }
        }
    }

    fun setOrientationMode(mode: OrientationMode) {
        _orientationMode.value = mode
    }

    fun openFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOpeningDocument.value = true
            try {
                openUriInternal(uri, fallbackCachePath = null, preferredDisplayName = null)
            } finally {
                _isOpeningDocument.value = false
            }
        }
    }

    fun openRecent(item: ReadingProgress) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOpeningDocument.value = true
            try {
                val uri = Uri.parse(item.fileUri)
                val opened = openUriInternal(
                    uri = uri,
                    fallbackCachePath = item.fallbackCachePath,
                    preferredDisplayName = item.displayName
                )
                if (!opened && item.fallbackCachePath != null) {
                    openFallbackFile(item.fallbackCachePath!!, item.fileUri, item.displayName)
                }
            } finally {
                _isOpeningDocument.value = false
            }
        }
    }

    private suspend fun openUriInternal(
        uri: Uri,
        fallbackCachePath: String?,
        preferredDisplayName: String? = null
    ): Boolean {
        val resolver = appContext.contentResolver
        val displayName = preferredDisplayName?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(uri)
            ?: "Document.pdf"

        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                try {
                    val result = engine.openFromPath(path, uri.toString(), displayName)
                    applyOpenResult(result)
                    return true
                } catch (e: PdfException.PasswordRequired) {
                    handlePdfException(e)
                    return false
                } catch (_: Exception) {
                    // Fall through to FileDescriptor / stream copy fallback
                }
            }
        }

        var needsFallbackCopy = false
        if (uri.scheme == "content") {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                needsFallbackCopy = true
            } catch (_: Exception) {
                needsFallbackCopy = true
            }
        } else {
            needsFallbackCopy = true
        }

        return try {
            val pfd = resolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val rawFd = pfd.detachFd()
                val result = engine.openFromFd(rawFd, uri.toString(), displayName, fallbackCachePath)
                applyOpenResult(result)
                if (needsFallbackCopy && result.progress.fallbackCachePath == null) {
                    launchBackgroundCacheCopy(uri, result.progress)
                }
                true
            } else {
                copyStreamAndOpen(uri, displayName)
            }
        } catch (e: PdfException.PasswordRequired) {
            handlePdfException(e)
            false
        } catch (_: SecurityException) {
            if (fallbackCachePath != null && File(fallbackCachePath).exists()) {
                openFallbackFile(fallbackCachePath, uri.toString(), displayName)
            } else {
                copyStreamAndOpen(uri, displayName)
            }
        } catch (_: Exception) {
            if (fallbackCachePath != null && File(fallbackCachePath).exists()) {
                openFallbackFile(fallbackCachePath, uri.toString(), displayName)
            } else {
                copyStreamAndOpen(uri, displayName)
            }
        }
    }

    private fun openFallbackFile(path: String, fileUri: String, displayName: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) {
                _snackbarMessages.tryEmit(str(R.string.msg_cached_pdf_unavailable))
                return false
            }
            val result = engine.openFromPath(path, fileUri, displayName)
            applyOpenResult(result)
            true
        } catch (e: PdfException) {
            handlePdfException(e)
            false
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(
                str(R.string.msg_unable_to_open_cached_pdf, e.message ?: unknownErrorText())
            )
            false
        }
    }

    private fun copyStreamAndOpen(uri: Uri, displayName: String): Boolean {
        return try {
            val cacheDir = File(appContext.filesDir, "recent_cache").apply { mkdirs() }
            val tempFile = File.createTempFile("import_", ".pdf", cacheDir)
            val copied = appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false

            if (!copied) {
                tempFile.delete()
                _snackbarMessages.tryEmit(str(R.string.msg_unable_to_read_pdf_stream))
                return false
            }

            val result = engine.openFromPath(tempFile.absolutePath, uri.toString(), displayName)
            val finalFile = File(cacheDir, "${result.progress.fileHash}.pdf")
            if (finalFile.exists()) {
                tempFile.delete()
            } else {
                tempFile.renameTo(finalFile)
            }
            enforceCacheLimit(cacheDir, keepFile = finalFile)

            val updatedProgress = result.progress.copy(fallbackCachePath = finalFile.absolutePath)
            engine.saveProgress(updatedProgress)
            applyOpenResult(result.copy(progress = updatedProgress))
            true
        } catch (e: PdfException) {
            handlePdfException(e)
            false
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(
                str(R.string.msg_failed_to_open_pdf, e.message ?: unknownErrorText())
            )
            false
        }
    }

    private fun launchBackgroundCacheCopy(uri: Uri, progress: ReadingProgress) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // Skip background caching for very large files (> 35 MB) to preserve flash I/O & responsiveness
                val fileSize = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else -1L
                } ?: -1L
                if (fileSize > 35L * 1024L * 1024L) {
                    return@runCatching
                }

                val cacheDir = File(appContext.filesDir, "recent_cache").apply { mkdirs() }
                val targetFile = File(cacheDir, "${progress.fileHash}.pdf")
                if (!targetFile.exists()) {
                    val tempFile = File(cacheDir, "${progress.fileHash}.tmp")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@runCatching
                    tempFile.renameTo(targetFile)
                }
                enforceCacheLimit(cacheDir, keepFile = targetFile)

                val current = _readerState.value?.progress
                val updated = (if (current?.fileHash == progress.fileHash) current else progress)
                    .copy(fallbackCachePath = targetFile.absolutePath)
                engine.saveProgress(updated)
                _readerState.update { state ->
                    if (state?.progress?.fileHash == updated.fileHash) {
                        state.copy(progress = state.progress.copy(fallbackCachePath = targetFile.absolutePath))
                    } else {
                        state
                    }
                }
                refreshRecentFiles()
            }
        }
    }

    private fun enforceCacheLimit(cacheDir: File, keepFile: File) {
        val files = cacheDir.listFiles()?.toList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return

        val evictCandidates = files
            .filter { it.absolutePath != keepFile.absolutePath }
            .sortedBy { it.lastModified() }

        for (file in evictCandidates) {
            if (total <= MAX_CACHE_BYTES) break
            val size = file.length()
            if (file.delete()) {
                total -= size
            }
        }
    }

    private fun applyOpenResult(result: DocumentOpenResult) {
        pageTextCache.clear()
        val highlights = runCatching {
            engine.getHighlightsForFile(result.progress.fileHash)
        }.getOrDefault(emptyList())
        _readerState.value = ReaderUiState(
            progress = result.progress,
            dimensions = result.dimensions,
            renderGeneration = 0L,
            highlights = highlights,
        )
        refreshRecentFiles()
    }

    private fun handlePdfException(e: PdfException) {
        val errorText = when (e) {
            is PdfException.PasswordRequired -> str(R.string.msg_password_protected_unsupported)
            is PdfException.InvalidSource -> str(R.string.msg_invalid_pdf_source)
            else -> str(R.string.msg_could_not_open_pdf, e.message ?: unknownErrorText())
        }
        _snackbarMessages.tryEmit(errorText)
    }

    fun closeDocument() {
        flushProgress()
        searchJob?.cancel()
        engine.cancelActiveSearch()
        pageTextCache.clear()
        _orientationMode.value = OrientationMode.PORTRAIT
        viewModelScope.launch(Dispatchers.IO) {
            engine.closeDocument()
            refreshRecentFiles()
        }
        _readerState.value = null
    }

    suspend fun getPageTextContent(pageIndex: Int): PageTextContent? {
        val state = _readerState.value ?: return null
        return getPageTextContent(pageIndex, state.progress.manualRotation)
    }

    suspend fun getPageTextContent(pageIndex: Int, rotation: PageRotation): PageTextContent? = withContext(Dispatchers.Default) {
        val hash = _readerState.value?.progress?.fileHash
            ?: _editorSession.value?.fileHash
            ?: return@withContext null
        val key = "${hash}_${pageIndex}_${rotation.name}"
        pageTextCache[key]?.let { return@withContext it }
        runCatching {
            engine.getPageTextContent(pageIndex.toUInt(), rotation)
        }.onSuccess { content ->
            synchronized(pageTextCache) {
                pageTextCache[key] = content
            }
        }.getOrNull()
    }

    fun addHighlight(
        pageIndex: Int,
        startChar: Int,
        endChar: Int,
        rects: List<NormalizedRect>,
        selectedText: String,
        colorArgb: UInt = 0x66FFEB3Bu
    ) {
        val state = _readerState.value ?: return
        if (rects.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine.addHighlight(
                    fileHash = state.progress.fileHash,
                    pageIndex = pageIndex.toUInt(),
                    startChar = startChar.toUInt(),
                    endChar = endChar.toUInt(),
                    rects = rects,
                    colorArgb = colorArgb,
                    selectedText = selectedText
                )
            }.onSuccess { record ->
                _readerState.update { cur ->
                    cur?.copy(highlights = cur.highlights + record)
                }
            }.onFailure { err ->
                _snackbarMessages.tryEmit(
                    str(R.string.msg_failed_to_save_highlight, err.message ?: unknownErrorText())
                )
            }
        }
    }

    fun removeHighlight(highlightId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine.removeHighlight(highlightId)
            }.onSuccess {
                _readerState.update { cur ->
                    cur?.copy(highlights = cur.highlights.filterNot { it.id == highlightId })
                }
            }
        }
    }

    fun recolorHighlight(highlight: HighlightRecord, newColorArgb: UInt) {
        val state = _readerState.value ?: return
        if (highlight.colorArgb == newColorArgb) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine.removeHighlight(highlight.id)
                engine.addHighlight(
                    fileHash = state.progress.fileHash,
                    pageIndex = highlight.pageIndex,
                    startChar = highlight.startChar,
                    endChar = highlight.endChar,
                    rects = highlight.rects,
                    colorArgb = newColorArgb,
                    selectedText = highlight.selectedText
                )
            }.onSuccess { updated ->
                _readerState.update { cur ->
                    cur?.copy(highlights = cur.highlights.filterNot { it.id == highlight.id } + updated)
                }
            }
        }
    }

    fun unhighlightRange(pageIndex: Int, selStartChar: Int, selEndChar: Int) {
        val state = _readerState.value ?: return
        val overlapping = state.highlights.filter { hl ->
            hl.pageIndex.toInt() == pageIndex &&
                hl.startChar.toInt() <= selEndChar &&
                hl.endChar.toInt() >= selStartChar
        }
        if (overlapping.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val needsSplit = overlapping.any { hl ->
                hl.startChar.toInt() < selStartChar || hl.endChar.toInt() > selEndChar
            }
            val pageText = if (needsSplit) getPageTextContent(pageIndex) else null

            val removedIds = mutableSetOf<Long>()
            val addedRecords = mutableListOf<HighlightRecord>()

            for (hl in overlapping) {
                val hlStart = hl.startChar.toInt()
                val hlEnd = hl.endChar.toInt()
                runCatching { engine.removeHighlight(hl.id) }.onSuccess {
                    removedIds += hl.id
                }

                if (pageText != null) {
                    if (hlStart < selStartChar) {
                        buildTrimmedSubHighlight(
                            fileHash = state.progress.fileHash,
                            pageIndex = pageIndex,
                            pageText = pageText,
                            fromChar = hlStart,
                            toChar = selStartChar - 1,
                            colorArgb = hl.colorArgb
                        )?.let { addedRecords += it }
                    }
                    if (hlEnd > selEndChar) {
                        buildTrimmedSubHighlight(
                            fileHash = state.progress.fileHash,
                            pageIndex = pageIndex,
                            pageText = pageText,
                            fromChar = selEndChar + 1,
                            toChar = hlEnd,
                            colorArgb = hl.colorArgb
                        )?.let { addedRecords += it }
                    }
                }
            }

            _readerState.update { cur ->
                cur?.copy(
                    highlights = cur.highlights.filterNot { it.id in removedIds } + addedRecords
                )
            }
        }
    }

    private fun buildTrimmedSubHighlight(
        fileHash: String,
        pageIndex: Int,
        pageText: PageTextContent,
        fromChar: Int,
        toChar: Int,
        colorArgb: UInt
    ): HighlightRecord? {
        val slice = pageText.chars.filter { ch ->
            val idx = ch.charIndex.toInt()
            idx in fromChar..toChar
        }
        val firstNonWs = slice.indexOfFirst { ch -> ch.text.any { !it.isWhitespace() } }
        val lastNonWs = slice.indexOfLast { ch -> ch.text.any { !it.isWhitespace() } }
        if (firstNonWs < 0 || lastNonWs < firstNonWs) return null

        val trimmed = slice.subList(firstNonWs, lastNonWs + 1)
        val validRects = trimmed.map { it.rect }.filter { it.right > it.left && it.bottom > it.top }
        if (validRects.isEmpty()) return null

        val mergedRects = mergeHighlightLineRects(validRects)
        val text = trimmed.joinToString("") { it.text }
        return runCatching {
            engine.addHighlight(
                fileHash = fileHash,
                pageIndex = pageIndex.toUInt(),
                startChar = trimmed.first().charIndex,
                endChar = trimmed.last().charIndex,
                rects = mergedRects,
                colorArgb = colorArgb,
                selectedText = text
            )
        }.getOrNull()
    }

    private fun mergeHighlightLineRects(rects: List<NormalizedRect>): List<NormalizedRect> {
        if (rects.isEmpty()) return emptyList()
        val merged = mutableListOf<NormalizedRect>()
        var cur = rects[0]
        for (i in 1 until rects.size) {
            val next = rects[i]
            val curMidY = (cur.top + cur.bottom) / 2f
            val nextMidY = (next.top + next.bottom) / 2f
            val lineHeight = (cur.bottom - cur.top).coerceAtLeast(0.005f)
            if (abs(curMidY - nextMidY) < lineHeight * 0.6f) {
                cur = NormalizedRect(
                    left = min(cur.left, next.left),
                    top = min(cur.top, next.top),
                    right = max(cur.right, next.right),
                    bottom = max(cur.bottom, next.bottom)
                )
            } else {
                merged.add(cur)
                cur = next
            }
        }
        merged.add(cur)
        return merged
    }

    fun removeRecentFile(fileHash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { engine.removeRecentFile(fileHash) }
            val cached = File(File(appContext.filesDir, "recent_cache"), "$fileHash.pdf")
            if (cached.exists()) {
                cached.delete()
            }
            refreshRecentFiles()
        }
    }

    fun renameRecentFile(item: ReadingProgress, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val finalName = if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed else "$trimmed.pdf"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine.saveProgress(item.copy(displayName = finalName))
            }.onFailure { e ->
                _snackbarMessages.tryEmit(
                    str(R.string.msg_unable_to_rename_pdf, e.message ?: unknownErrorText())
                )
            }
            refreshRecentFiles()
        }
    }

    fun updateReadingPosition(
        pageIndex: Int,
        offsetFraction: Float,
        zoomScale: Float,
        panOffsetXFraction: Float
    ) {
        _readerState.update { state ->
            state ?: return@update null
            val total = state.progress.totalPages.toInt().coerceAtLeast(1)
            val clampedPage = pageIndex.coerceIn(0, total - 1).toUInt()
            val clampedOffset = offsetFraction.coerceIn(0f, 1f)
            val clampedZoom = zoomScale.coerceIn(1f, 5f)
            val clampedPan = panOffsetXFraction.coerceIn(-1f, 1f)
            val nowUnix = System.currentTimeMillis() / 1000L

            val updated = state.progress.copy(
                lastPageIndex = clampedPage,
                pageOffsetFraction = clampedOffset,
                zoomScale = clampedZoom,
                panOffsetXFraction = clampedPan,
                lastOpenedUnix = nowUnix
            )
            queueProgressSave(updated)
            state.copy(progress = updated)
        }
    }

    fun rotatePage90() {
        val state = _readerState.value ?: return
        val nextRotation = when (state.progress.manualRotation) {
            PageRotation.DEG0 -> PageRotation.DEG90
            PageRotation.DEG90 -> PageRotation.DEG180
            PageRotation.DEG180 -> PageRotation.DEG270
            PageRotation.DEG270 -> PageRotation.DEG0
        }

        // Rotating by 90° always swaps width and height of every page; compute in memory (< 0.1ms)
        // instead of re-reading all pages from PDFium.
        val newDims = state.dimensions.map { dim ->
            val w = dim.heightPts
            val h = dim.widthPts
            dim.copy(
                widthPts = w,
                heightPts = h,
                aspectRatio = w / h.coerceAtLeast(1f)
            )
        }
        val updatedProgress = state.progress.copy(
            manualRotation = nextRotation,
            lastOpenedUnix = System.currentTimeMillis() / 1000L
        )
        queueProgressSave(updatedProgress)
        _readerState.update { current ->
            current?.copy(
                progress = updatedProgress,
                dimensions = newDims,
                renderGeneration = current.renderGeneration + 1
            )
        }
        val activeQuery = _readerState.value?.searchQuery.orEmpty()
        if (activeQuery.isNotBlank()) {
            viewModelScope.launch(Dispatchers.Default) {
                launchSearch(activeQuery, nextRotation)
            }
        }
    }

    fun setSearchVisible(visible: Boolean) {
        if (!visible) {
            engine.cancelActiveSearch()
            searchJob?.cancel()
            _readerState.update {
                it?.copy(
                    searchVisible = false,
                    searchQuery = "",
                    searchMatches = emptyList(),
                    activeMatchIndex = -1,
                    isSearching = false
                )
            }
        } else {
            _readerState.update { it?.copy(searchVisible = true) }
        }
    }

    fun onSearchQueryChanged(query: String) {
        engine.cancelActiveSearch()
        searchJob?.cancel()

        val state = _readerState.value ?: return
        if (query.isBlank()) {
            _readerState.update {
                it?.copy(
                    searchQuery = query,
                    searchMatches = emptyList(),
                    activeMatchIndex = -1,
                    isSearching = false
                )
            }
            return
        }

        _readerState.update { it?.copy(searchQuery = query, isSearching = true) }
        val rotation = state.progress.manualRotation
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(250L)
            launchSearch(query, rotation)
        }
    }

    private suspend fun launchSearch(query: String, rotation: PageRotation) {
        withContext(Dispatchers.Default) {
            try {
                val matches = engine.searchDocument(query, rotation)
                _readerState.update { current ->
                    if (current == null || current.searchQuery != query) return@update current
                    current.copy(
                        searchMatches = matches,
                        activeMatchIndex = if (matches.isNotEmpty()) 0 else -1,
                        isSearching = false
                    )
                }
            } catch (_: PdfException.SearchCancelled) {
                // Expected when superseded by a newer keystroke
            } catch (e: Exception) {
                _readerState.update { it?.copy(isSearching = false) }
                _snackbarMessages.tryEmit(str(R.string.msg_search_error, e.message ?: unknownErrorText()))
            }
        }
    }

    fun nextSearchMatch(): SearchMatch? {
        val state = _readerState.value ?: return null
        if (state.searchMatches.isEmpty()) return null
        val next = (state.activeMatchIndex + 1) % state.searchMatches.size
        _readerState.update { it?.copy(activeMatchIndex = next) }
        return state.searchMatches[next]
    }

    fun previousSearchMatch(): SearchMatch? {
        val state = _readerState.value ?: return null
        if (state.searchMatches.isEmpty()) return null
        val prev = if (state.activeMatchIndex <= 0) {
            state.searchMatches.lastIndex
        } else {
            state.activeMatchIndex - 1
        }
        _readerState.update { it?.copy(activeMatchIndex = prev) }
        return state.searchMatches[prev]
    }

    suspend fun renderBasePageBitmap(
        pageIndex: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
        rotation: PageRotation,
        darkMode: Boolean
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (targetWidthPx <= 0 || targetHeightPx <= 0) return@withContext null
        val maxDim = max(targetWidthPx, targetHeightPx)
        val scale = if (maxDim > MAX_BASE_EDGE_PX) {
            MAX_BASE_EDGE_PX.toFloat() / maxDim.toFloat()
        } else {
            1f
        }
        val w = (targetWidthPx * scale).roundToInt().coerceAtLeast(1)
        val h = (targetHeightPx * scale).roundToInt().coerceAtLeast(1)

        val bitmap = acquireBitmap(w, h)
        val ok = NativeZeroCopy.renderPageDirect(
            enginePtr = engine.rawEnginePtr(),
            pageIndex = pageIndex,
            bitmap = bitmap,
            manualRotationOrdinal = rotation.ordinal,
            darkMode = darkMode
        )
        if (ok) {
            bitmap
        } else {
            releaseBitmapToPool(bitmap)
            null
        }
    }

    fun prefetchAdjacentPages(
        currentPage: Int,
        targetWidthPx: Int,
        dimensions: List<PageDimension>,
        rotation: PageRotation,
    ) {
        if (targetWidthPx <= 0 || dimensions.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            for (offset in listOf(1, -1)) {
                val idx = currentPage + offset
                if (idx !in dimensions.indices) continue
                val aspect = dimensions[idx].aspectRatio.coerceAtLeast(0.1f)
                val targetH = (targetWidthPx / aspect).roundToInt().coerceAtLeast(1)
                val bmp = renderBasePageBitmap(idx, targetWidthPx, targetH, rotation, darkMode = false)
                releaseBitmapToPool(bmp)
            }
        }
    }

    suspend fun renderZoomTileBitmap(
        pageIndex: Int,
        fullScaledWidthPx: Int,
        fullScaledHeightPx: Int,
        visibleXPx: Int,
        visibleYPx: Int,
        tileWidthPx: Int,
        tileHeightPx: Int,
        rotation: PageRotation,
        darkMode: Boolean
    ): Bitmap? = withContext(Dispatchers.Default) {
        val safeW = tileWidthPx.coerceIn(1, MAX_TILE_EDGE_PX)
        val safeH = tileHeightPx.coerceIn(1, MAX_TILE_EDGE_PX)
        val bitmap = acquireBitmap(safeW, safeH)
        val ok = NativeZeroCopy.renderZoomTileDirect(
            enginePtr = engine.rawEnginePtr(),
            pageIndex = pageIndex,
            fullScaledWidthPx = fullScaledWidthPx,
            fullScaledHeightPx = fullScaledHeightPx,
            visibleXPx = visibleXPx,
            visibleYPx = visibleYPx,
            bitmap = bitmap,
            manualRotationOrdinal = rotation.ordinal,
            darkMode = darkMode
        )
        if (ok) {
            bitmap
        } else {
            releaseBitmapToPool(bitmap)
            null
        }
    }

    private fun queueProgressSave(progress: ReadingProgress) {
        pendingProgressToSave.value = progress
    }

    fun flushProgress() {
        val current = _readerState.value?.progress ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { engine.saveProgress(current) }
        }
    }

    fun prepareSharePdf(onReady: (Uri, String) -> Unit) {
        val state = _readerState.value ?: return
        if (_isSharingDocument.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSharingDocument.value = true
            try {
                val (uri, sanitized) = exportCurrentEnginePdfToShareFile(
                    displayName = state.progress.displayName,
                    rotation = state.progress.manualRotation
                )
                withContext(Dispatchers.Main) {
                    onReady(uri, sanitized)
                }
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_unable_to_prepare_share, e.message ?: unknownErrorText())
                )
            } finally {
                _isSharingDocument.value = false
            }
        }
    }

    fun prepareShareRecentPdf(item: ReadingProgress, onReady: (Uri, String) -> Unit) {
        if (_isSharingDocument.value || _isOpeningDocument.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSharingDocument.value = true
            var openedInEngine = false
            try {
                val uri = Uri.parse(item.fileUri)
                if (uri.scheme == "file" && uri.path != null && File(uri.path!!).exists()) {
                    engine.openFromPath(uri.path!!, item.fileUri, item.displayName)
                    openedInEngine = true
                } else {
                    val pfd = runCatching { appContext.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
                    if (pfd != null) {
                        engine.openFromFd(pfd.detachFd(), item.fileUri, item.displayName, item.fallbackCachePath)
                        openedInEngine = true
                    } else if (item.fallbackCachePath != null && File(item.fallbackCachePath!!).exists()) {
                        engine.openFromPath(item.fallbackCachePath!!, item.fileUri, item.displayName)
                        openedInEngine = true
                    }
                }
                if (!openedInEngine) {
                    _snackbarMessages.tryEmit(
                        str(R.string.msg_pdf_not_accessible_for_sharing)
                    )
                    return@launch
                }
                val (shareUri, sanitized) = exportCurrentEnginePdfToShareFile(
                    displayName = item.displayName,
                    rotation = item.manualRotation
                )
                withContext(Dispatchers.Main) {
                    onReady(shareUri, sanitized)
                }
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_unable_to_prepare_share, e.message ?: unknownErrorText())
                )
            } finally {
                if (openedInEngine && _readerState.value == null) {
                    runCatching { engine.closeDocument() }
                }
                _isSharingDocument.value = false
            }
        }
    }

    private fun exportCurrentEnginePdfToShareFile(
        displayName: String,
        rotation: uniffi.pdf_core.PageRotation
    ): Pair<Uri, String> {
        val shareDir = File(appContext.cacheDir, "shared_pdfs").apply { mkdirs() }
        shareDir.listFiles()?.forEach { old -> old.delete() }

        val rawName = displayName.trim().ifEmpty { "Document.pdf" }
        val sanitized = rawName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
        val outFile = File(shareDir, sanitized)

        engine.exportPdf(outFile.absolutePath, rotation)
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            outFile
        )
        return uri to sanitized
    }

    fun setAppLanguageTag(languageTag: String) {
        _appLanguageTag.value = languageTag
        prefs.edit().putString("app_language", languageTag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = appContext.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = if (languageTag.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(languageTag)
            }
        }
    }

    fun syncLanguageFromSystemIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = appContext.getSystemService(LocaleManager::class.java)
            val appLocales = localeManager?.applicationLocales
            val systemTag = if (appLocales != null && !appLocales.isEmpty) {
                appLocales[0].toLanguageTag()
            } else {
                SYSTEM_LANGUAGE_TAG
            }
            if (!systemTag.equals(_appLanguageTag.value, ignoreCase = true)) {
                _appLanguageTag.value = systemTag
                prefs.edit().putString("app_language", systemTag).apply()
            }
        }
    }

    fun setGlobalDarkMode(enabled: Boolean) {
        _globalDarkMode.value = enabled
        prefs.edit().putBoolean("global_dark_mode", enabled).apply()
    }

    fun openMarkdownFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = queryDisplayName(uri) ?: "Notes.md"
                val text = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                if (text != null) {
                    _markdownState.value = MarkdownDocumentState(displayName = name, content = text)
                } else {
                    _snackbarMessages.tryEmit(str(R.string.msg_unable_to_read_markdown))
                }
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_unable_to_open_markdown, e.message ?: unknownErrorText())
                )
            }
        }
    }

    fun closeMarkdown() {
        _markdownState.value = null
    }

    fun resolveDisplayNameForUri(uri: Uri, fallback: String = "Document.pdf"): String {
        return queryDisplayName(uri) ?: fallback
    }

    suspend fun resolvePdfPageCount(uri: Uri?, fallbackPath: String?): Int? = withContext(Dispatchers.IO) {
        val (file, isTemp) = resolveToReadableTempFile(uri, fallbackPath, "count", ".pdf") ?: return@withContext null
        try {
            runCatching { engine.getPdfPageCount(file.absolutePath).toInt() }.getOrNull()
        } finally {
            if (isTemp) file.delete()
        }
    }

    fun mergePdfsToUri(
        items: List<MergePdfEntry>,
        outputUri: Uri,
        onComplete: (Boolean) -> Unit,
    ) {
        if (items.size < 2) return
        runPdfToolToUri(
            outputUri = outputUri,
            tempPrefix = "merged",
            successMessage = str(R.string.msg_merged_pdf_saved),
            errorPrefix = str(R.string.msg_merge_failed),
            onComplete = onComplete
        ) { _, outFile, tempFiles ->
            val inputPaths = items.mapIndexed { idx, entry ->
                val resolved = resolveToReadableTempFile(entry.uri, entry.fallbackPath, "merge_$idx", ".pdf")
                    ?: throw IllegalStateException(str(R.string.msg_unable_to_read_file, entry.displayName))
                if (resolved.second) tempFiles.add(resolved.first)
                resolved.first.absolutePath
            }
            engine.mergePdfs(inputPaths, outFile.absolutePath)
        }
    }

    fun splitPdfRangesToUri(
        target: SplitTarget,
        ranges: List<PageRangeSpec>,
        outputUri: Uri,
        onComplete: (ok: Boolean, isZip: Boolean) -> Unit,
    ) {
        if (ranges.isEmpty()) return
        val isZip = ranges.size > 1
        val successMsg = if (isZip) {
            str(R.string.msg_split_zip_saved, ranges.size)
        } else {
            str(R.string.msg_split_pdf_saved)
        }
        runPdfToolToUri(
            outputUri = outputUri,
            tempPrefix = if (isZip) "split_zip" else "split",
            successMessage = successMsg,
            errorPrefix = str(R.string.msg_split_failed),
            onComplete = { ok -> onComplete(ok, isZip) }
        ) { tmpDir, outFile, tempFiles ->
            val resolved = resolveToReadableTempFile(target.uri, target.fallbackPath, "split_src", ".pdf")
                ?: throw IllegalStateException(str(R.string.msg_unable_to_read_file, target.displayName))
            if (resolved.second) tempFiles.add(resolved.first)
            val srcPath = resolved.first.absolutePath

            if (!isZip) {
                val r = ranges.first()
                val indices = (r.fromOneBased - 1 until r.toOneBased).map { it.toUInt() }
                engine.splitPdf(srcPath, indices, outFile.absolutePath)
            } else {
                val baseName = target.displayName
                    .removeSuffix(".pdf")
                    .removeSuffix(".PDF")
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .ifBlank { "Document" }
                val usedNames = mutableSetOf<String>()
                ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zipOut ->
                    ranges.forEachIndexed { idx, r ->
                        val baseEntry = "${baseName}_${r.fromOneBased}-${r.toOneBased}"
                        var entryName = "$baseEntry.pdf"
                        var dup = 2
                        while (!usedNames.add(entryName)) {
                            entryName = "${baseEntry}_$dup.pdf"
                            dup++
                        }
                        val rangeFile = File(tmpDir, "split_part_${idx}_${System.currentTimeMillis()}.pdf")
                        try {
                            val indices = (r.fromOneBased - 1 until r.toOneBased).map { it.toUInt() }
                            engine.splitPdf(srcPath, indices, rangeFile.absolutePath)
                            zipOut.putNextEntry(ZipEntry(entryName))
                            rangeFile.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        } finally {
                            rangeFile.delete()
                        }
                    }
                }
            }
        }
    }

    fun imagesToPdfToUri(
        items: List<ImageToPdfEntry>,
        outputUri: Uri,
        onComplete: (Boolean) -> Unit,
    ) {
        if (items.isEmpty()) return
        runPdfToolToUri(
            outputUri = outputUri,
            tempPrefix = "images",
            successMessage = str(R.string.msg_pdf_created_from_images, items.size),
            errorPrefix = str(R.string.msg_image_to_pdf_failed),
            onComplete = onComplete
        ) { _, outFile, tempFiles ->
            val inputPaths = items.mapIndexed { idx, entry ->
                val resolved = resolveToReadableTempFile(entry.uri, null, "img_$idx", ".img")
                    ?: throw IllegalStateException(str(R.string.msg_unable_to_read_file, entry.displayName))
                if (resolved.second) tempFiles.add(resolved.first)
                resolved.first.absolutePath
            }
            engine.imagesToPdf(inputPaths, outFile.absolutePath)
        }
    }

    fun openPdfForEdit(
        uri: Uri,
        displayName: String,
        fallbackPath: String? = null,
        initialPageIndex: Int = 0,
        fromReader: Boolean = false,
    ) {
        val activeReader = _readerState.value
        if (fromReader && activeReader != null) {
            val fbPath = fallbackPath ?: activeReader.progress.fallbackCachePath
            _editorSession.value = PdfEditorSession(
                sourceUri = uri,
                displayName = displayName,
                fallbackPath = fbPath,
                fileHash = activeReader.progress.fileHash,
                canReplaceOriginal = canOverwriteSource(uri, fbPath),
                initialPageIndex = initialPageIndex.coerceIn(0, (activeReader.dimensions.size - 1).coerceAtLeast(0)),
                returnToReader = true,
                dimensions = activeReader.dimensions
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isOpeningDocument.value = true
            try {
                val resolved = resolveToReadableTempFile(uri, fallbackPath, "edit_open", ".pdf")
                if (resolved == null) {
                    _snackbarMessages.tryEmit(str(R.string.msg_unable_to_read_file, displayName))
                    return@launch
                }
                val (file, _) = resolved
                val result = engine.openFromPath(file.absolutePath, uri.toString(), displayName)
                val dims = engine.getPageDimensions(PageRotation.DEG0)
                _editorSession.value = PdfEditorSession(
                    sourceUri = uri,
                    displayName = displayName,
                    fallbackPath = fallbackPath ?: file.absolutePath,
                    fileHash = result.progress.fileHash,
                    canReplaceOriginal = canOverwriteSource(uri, fallbackPath),
                    initialPageIndex = initialPageIndex.coerceIn(0, (dims.size - 1).coerceAtLeast(0)),
                    returnToReader = false,
                    dimensions = dims
                )
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(str(R.string.msg_could_not_open_pdf, e.message ?: unknownErrorText()))
            } finally {
                _isOpeningDocument.value = false
            }
        }
    }

    fun closePdfEditor() {
        val session = _editorSession.value ?: return
        _editorSession.value = null
        if (!session.returnToReader) {
            viewModelScope.launch(Dispatchers.IO) {
                engine.closeDocument()
            }
        }
    }

    private fun resolveWritableFile(uri: Uri): File? {
        if (uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            if (f.exists() && f.canWrite()) return f
        }
        if (uri.scheme == "content") {
            val path = runCatching {
                appContext.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Files.FileColumns.DATA),
                    null,
                    null,
                    null
                )?.use { c ->
                    val col = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    if (col >= 0 && c.moveToFirst()) c.getString(col) else null
                }
            }.getOrNull()
            if (!path.isNullOrBlank()) {
                val f = File(path)
                if (f.exists() && f.canWrite()) return f
            }
        }
        return null
    }

    private fun canOverwriteSource(uri: Uri, fallbackPath: String?): Boolean {
        if (resolveWritableFile(uri) != null) return true
        if (uri.scheme == "content") {
            val writable = runCatching {
                appContext.contentResolver.openFileDescriptor(uri, "rw")?.use { true } ?: false
            }.getOrDefault(false)
            if (writable) return true
        }
        if (!fallbackPath.isNullOrBlank()) {
            val f = File(fallbackPath)
            if (f.exists() && f.canWrite()) return true
        }
        return false
    }

    private fun buildNativeEditSpecs(
        pages: List<EditorPageItem>,
        tmpDir: File,
        tempFiles: MutableList<File>,
    ): List<uniffi.pdf_core.EditPageSpec> {
        val externalPathCache = mutableMapOf<Uri, String>()
        return pages.mapIndexed { pageIdx, item ->
            val source = when (val kind = item.kind) {
                is EditorPageKind.Original -> uniffi.pdf_core.EditPageSource.Original(
                    pageIndex = kind.pageIndex.toUInt()
                )
                is EditorPageKind.Blank -> uniffi.pdf_core.EditPageSource.Blank(
                    widthPts = kind.widthPts,
                    heightPts = kind.heightPts
                )
                is EditorPageKind.Image -> {
                    val resolved = resolveToReadableTempFile(kind.uri, null, "edit_img_$pageIdx", ".img")
                        ?: throw IllegalStateException(str(R.string.msg_unable_to_read_file, kind.displayName))
                    if (resolved.second) tempFiles.add(resolved.first)
                    uniffi.pdf_core.EditPageSource.Image(path = resolved.first.absolutePath)
                }
                is EditorPageKind.ExternalPdf -> {
                    val path = externalPathCache.getOrPut(kind.uri) {
                        val resolved = resolveToReadableTempFile(kind.uri, null, "edit_ext_$pageIdx", ".pdf")
                            ?: throw IllegalStateException(str(R.string.msg_unable_to_read_file, kind.displayName))
                        if (resolved.second) tempFiles.add(resolved.first)
                        resolved.first.absolutePath
                    }
                    uniffi.pdf_core.EditPageSource.ExternalPdf(
                        path = path,
                        pageIndex = kind.pageIndex.toUInt()
                    )
                }
            }

            val effW = item.effectiveWidthPts()
            val effH = item.effectiveHeightPts()
            val textOps = item.textEdits.mapIndexedNotNull { boxIdx, box ->
                val hasCover = box.coverBackground &&
                    box.coverLeft != null && box.coverTop != null &&
                    box.coverRight != null && box.coverBottom != null
                if (box.text.isEmpty() && !hasCover) return@mapIndexedNotNull null

                val needsBitmapRender = box.text.isNotEmpty() && (
                    box.text.any { it.code > 127 } ||
                        box.text.contains('\n') ||
                        item.rotationDegrees % 360 != 0
                    )
                var overlayPath: String? = null
                if (needsBitmapRender) {
                    val pngFile = File(tmpDir, "edit_txt_${pageIdx}_${boxIdx}_${System.currentTimeMillis()}.png")
                    if (renderTextBoxOverlayPng(box, effW, effH, pngFile)) {
                        tempFiles.add(pngFile)
                        overlayPath = pngFile.absolutePath
                    }
                }

                uniffi.pdf_core.PdfTextEditOp(
                    coverLeft = if (box.coverBackground) (box.coverLeft ?: box.left) else null,
                    coverTop = if (box.coverBackground) (box.coverTop ?: box.top) else null,
                    coverRight = if (box.coverBackground) (box.coverRight ?: box.right) else null,
                    coverBottom = if (box.coverBackground) (box.coverBottom ?: box.bottom) else null,
                    coverColorArgb = 0xFFFFFFFFu,
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom,
                    text = box.text,
                    fontSizePts = box.fontSizePts,
                    colorArgb = box.colorArgb.toUInt(),
                    isBold = box.isBold,
                    overlayImagePath = overlayPath
                )
            }

            uniffi.pdf_core.EditPageSpec(
                source = source,
                rotationDegrees = item.rotationDegrees,
                textEdits = textOps
            )
        }
    }

    fun saveEditedPdfAsNewFile(
        session: PdfEditorSession,
        pages: List<EditorPageItem>,
        outputUri: Uri,
        onComplete: (Boolean) -> Unit,
    ) {
        if (pages.isEmpty()) return
        runPdfToolToUri(
            outputUri = outputUri,
            tempPrefix = "edited",
            successMessage = str(R.string.msg_pdf_edits_saved),
            errorPrefix = str(R.string.msg_pdf_edit_failed, "").removeSuffix(": "),
            onComplete = { ok ->
                if (ok) {
                    refreshDevicePdfs(onlyIfNeeded = false)
                    closePdfEditor()
                }
                onComplete(ok)
            }
        ) { tmpDir, outFile, tempFiles ->
            val resolvedSrc = resolveToReadableTempFile(session.sourceUri, session.fallbackPath, "edit_src", ".pdf")
                ?: throw IllegalStateException(str(R.string.msg_unable_to_read_file, session.displayName))
            if (resolvedSrc.second) tempFiles.add(resolvedSrc.first)
            val specs = buildNativeEditSpecs(pages, tmpDir, tempFiles)
            engine.applyPdfEdits(resolvedSrc.first.absolutePath, specs, outFile.absolutePath)
        }
    }

    fun replaceEditedPdf(
        session: PdfEditorSession,
        pages: List<EditorPageItem>,
        newFileName: String,
        onComplete: (Boolean) -> Unit,
    ) {
        if (pages.isEmpty() || _isToolProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isToolProcessing.value = true
            val tempFiles = mutableListOf<File>()
            var outFile: File? = null
            var ok = false
            var finalUri = session.sourceUri
            var finalName = session.displayName
            var finalFallback = session.fallbackPath
            try {
                val tmpDir = File(appContext.cacheDir, "tool_tmp").apply { mkdirs() }
                outFile = File(tmpDir, "edited_replace_${System.currentTimeMillis()}.pdf")
                val resolvedSrc = resolveToReadableTempFile(session.sourceUri, session.fallbackPath, "edit_src", ".pdf")
                    ?: throw IllegalStateException(str(R.string.msg_unable_to_read_file, session.displayName))
                if (resolvedSrc.second) tempFiles.add(resolvedSrc.first)

                val specs = buildNativeEditSpecs(pages, tmpDir, tempFiles)
                engine.applyPdfEdits(resolvedSrc.first.absolutePath, specs, outFile.absolutePath)

                val writableFile = resolveWritableFile(session.sourceUri)
                if (writableFile != null) {
                    outFile.inputStream().use { input ->
                        writableFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!newFileName.equals(writableFile.name, ignoreCase = false)) {
                        val renamed = File(writableFile.parentFile, newFileName)
                        if (!renamed.exists() && writableFile.renameTo(renamed)) {
                            finalUri = Uri.fromFile(renamed)
                            finalName = renamed.name
                            finalFallback = renamed.absolutePath
                        }
                    }
                } else {
                    val stream = runCatching {
                        appContext.contentResolver.openOutputStream(session.sourceUri, "wt")
                    }.getOrNull() ?: appContext.contentResolver.openOutputStream(session.sourceUri)
                    if (stream != null) {
                        stream.use { output ->
                            outFile.inputStream().use { input -> input.copyTo(output) }
                        }
                    } else if (!session.fallbackPath.isNullOrBlank()) {
                        val fb = File(session.fallbackPath)
                        if (fb.exists() && fb.canWrite()) {
                            outFile.inputStream().use { input ->
                                fb.outputStream().use { output -> input.copyTo(output) }
                            }
                        } else {
                            throw IllegalStateException(str(R.string.msg_unable_to_write_destination))
                        }
                    } else {
                        throw IllegalStateException(str(R.string.msg_unable_to_write_destination))
                    }
                }

                // Also refresh cached copy if one exists
                val cachedPath = session.fallbackPath
                if (!cachedPath.isNullOrBlank()) {
                    val cachedFile = File(cachedPath)
                    if (cachedFile.exists() && cachedFile.canWrite() && cachedFile.absolutePath != writableFile?.absolutePath) {
                        runCatching {
                            outFile.inputStream().use { input ->
                                cachedFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }

                ok = true
                _snackbarMessages.tryEmit(str(R.string.msg_pdf_edits_replaced))
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(str(R.string.msg_pdf_edit_failed, e.message ?: unknownErrorText()))
            } finally {
                tempFiles.forEach { it.delete() }
                outFile?.delete()
                _isToolProcessing.value = false
            }

            withContext(Dispatchers.Main) {
                if (ok) {
                    _editorSession.value = null
                    refreshRecentFiles()
                    refreshDevicePdfs(onlyIfNeeded = false)
                    if (session.returnToReader) {
                        openFromUri(finalUri)
                    } else {
                        viewModelScope.launch(Dispatchers.IO) { engine.closeDocument() }
                    }
                }
                onComplete(ok)
            }
        }
    }

    private fun runPdfToolToUri(
        outputUri: Uri,
        tempPrefix: String,
        successMessage: String,
        errorPrefix: String,
        onComplete: (Boolean) -> Unit,
        block: (tmpDir: File, outFile: File, tempFilesToDelete: MutableList<File>) -> Unit,
    ) {
        if (_isToolProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isToolProcessing.value = true
            val tempFiles = mutableListOf<File>()
            var outFile: File? = null
            var ok = false
            try {
                val tmpDir = File(appContext.cacheDir, "tool_tmp").apply { mkdirs() }
                outFile = File(tmpDir, "${tempPrefix}_${System.currentTimeMillis()}.pdf")
                block(tmpDir, outFile, tempFiles)
                appContext.contentResolver.openOutputStream(outputUri)?.use { outStream ->
                    outFile.inputStream().use { inStream -> inStream.copyTo(outStream) }
                } ?: throw IllegalStateException(str(R.string.msg_unable_to_write_destination))
                ok = true
                _snackbarMessages.tryEmit(successMessage)
            } catch (e: Exception) {
                runCatching { appContext.contentResolver.delete(outputUri, null, null) }
                _snackbarMessages.tryEmit("$errorPrefix: ${e.message ?: unknownErrorText()}")
            } finally {
                tempFiles.forEach { it.delete() }
                outFile?.delete()
                _isToolProcessing.value = false
                withContext(Dispatchers.Main) { onComplete(ok) }
            }
        }
    }

    private fun resolveToReadableTempFile(
        uri: Uri?,
        fallbackPath: String?,
        prefix: String,
        ext: String,
    ): Pair<File, Boolean>? {
        if (uri != null && uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            if (f.exists() && f.canRead()) return f to false
        }
        if (uri != null) {
            val copied = runCatching {
                val tmpDir = File(appContext.cacheDir, "tool_tmp").apply { mkdirs() }
                val tmpFile = File(tmpDir, "${prefix}_${System.nanoTime()}$ext")
                appContext.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(tmpFile).use { outStream -> inStream.copyTo(outStream) }
                } ?: return@runCatching null
                tmpFile
            }.getOrNull()
            if (copied != null && copied.exists()) return copied to true
        }
        if (fallbackPath != null) {
            val f = File(fallbackPath)
            if (f.exists() && f.canRead()) return f to false
        }
        return null
    }

    fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) {
                                val name = cursor.getString(idx)?.trim()
                                if (!name.isNullOrEmpty() && !name.all { it.isDigit() }) {
                                    return name
                                }
                            }
                        }
                    }
            }
        }
        val segment = uri.lastPathSegment?.substringAfterLast('/')?.trim()
        return segment?.takeIf { it.isNotEmpty() && !it.all { ch -> ch.isDigit() } }
    }

    fun onGoogleSignInResult(data: Intent?) {
        runCatching {
            val account = driveSyncManager.parseSignInResult(data)
            _driveAccountEmail.value = account?.email
            if (account?.email != null) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_google_connected, account.email ?: "")
                )
            }
        }.onFailure { err ->
            _snackbarMessages.tryEmit(
                str(R.string.msg_google_sign_in_failed, err.message ?: unknownErrorText())
            )
        }
    }

    fun signOutGoogleDrive() {
        viewModelScope.launch {
            driveSyncManager.signOut()
            _driveAccountEmail.value = null
            _driveSyncConflicts.value = emptyList()
            _snackbarMessages.tryEmit(
                str(R.string.msg_google_disconnected)
            )
        }
    }

    fun retryPendingDriveAction() {
        val action = pendingDriveRetryAction
        pendingDriveRetryAction = null
        action?.invoke()
    }

    fun dismissDriveConflicts() {
        _driveSyncConflicts.value = emptyList()
    }

    private val _driveStoredFiles = MutableStateFlow<List<DriveStoredFile>>(emptyList())
    val driveStoredFiles: StateFlow<List<DriveStoredFile>> = _driveStoredFiles.asStateFlow()

    private val _isLoadingDriveFiles = MutableStateFlow(false)
    val isLoadingDriveFiles: StateFlow<Boolean> = _isLoadingDriveFiles.asStateFlow()

    fun loadDriveStoredFiles() {
        if (!requireOnlineAndAccount()) return
        viewModelScope.launch {
            _isLoadingDriveFiles.value = true
            try {
                val token = driveSyncManager.fetchAccessToken()
                _driveStoredFiles.value = driveSyncManager.listAppDataPdfFiles(token)
            } catch (e: DriveAuthResolutionException) {
                pendingDriveRetryAction = { loadDriveStoredFiles() }
                _driveAuthPendingIntent.tryEmit(e.pendingIntent)
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.drive_delete_failed, e.message ?: unknownErrorText())
                )
            } finally {
                _isLoadingDriveFiles.value = false
            }
        }
    }

    fun deleteDriveStoredFile(file: DriveStoredFile) {
        if (!requireOnlineAndAccount()) return
        viewModelScope.launch {
            _isLoadingDriveFiles.value = true
            try {
                val token = driveSyncManager.fetchAccessToken()
                driveSyncManager.deleteAppDataFile(token, file.id)
                val (manifestId, existingItems) = driveSyncManager.loadManifest(token)
                val filtered = existingItems.filterNot { item ->
                    item.driveFileId == file.id ||
                        (file.fileHash != null && item.fileHash == file.fileHash)
                }
                if (manifestId != null && filtered.size != existingItems.size) {
                    driveSyncManager.saveManifest(token, manifestId, filtered)
                }
                _driveStoredFiles.value = _driveStoredFiles.value.filterNot { it.id == file.id }
                _snackbarMessages.tryEmit(str(R.string.drive_file_deleted, file.name))
            } catch (e: DriveAuthResolutionException) {
                pendingDriveRetryAction = { deleteDriveStoredFile(file) }
                _driveAuthPendingIntent.tryEmit(e.pendingIntent)
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.drive_delete_failed, e.message ?: unknownErrorText())
                )
            } finally {
                _isLoadingDriveFiles.value = false
            }
        }
    }

    private fun requireOnlineAndAccount(): Boolean {
        if (_driveAccountEmail.value == null) {
            _snackbarMessages.tryEmit(
                str(R.string.msg_connect_google_first)
            )
            return false
        }
        if (!driveSyncManager.isOnline()) {
            _snackbarMessages.tryEmit(
                str(R.string.msg_no_internet_drive)
            )
            return false
        }
        return true
    }

    private data class UploadCandidate(
        val progress: ReadingProgress,
        val file: File,
        val isTemp: Boolean,
        val sizeBytes: Long,
        val existingDriveId: String?,
        val inRecent: Boolean
    )

    fun uploadRecentToDrive(items: List<ReadingProgress> = _recentFiles.value) {
        if (!requireOnlineAndAccount()) return
        if (items.isEmpty()) {
            _snackbarMessages.tryEmit(
                str(R.string.msg_no_recent_to_upload)
            )
            return
        }
        if (_isSyncingDrive.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingDrive.value = true
            try {
                val token = driveSyncManager.fetchAccessToken()
                val candidates = mutableListOf<UploadCandidate>()
                for (item in items) {
                    val resolved = resolveToReadableTempFile(
                        uri = runCatching { Uri.parse(item.fileUri) }.getOrNull(),
                        fallbackPath = item.fallbackCachePath,
                        prefix = "drive_up",
                        ext = ".pdf"
                    ) ?: continue
                    val (file, isTemp) = resolved
                    val existingDriveId = driveSyncManager.findAppDataFileByHash(token, item.fileHash)
                    candidates.add(
                        UploadCandidate(
                            progress = item,
                            file = file,
                            isTemp = isTemp,
                            sizeBytes = file.length(),
                            existingDriveId = existingDriveId,
                            inRecent = true
                        )
                    )
                }
                uploadCandidatesInternal(token, candidates)
            } catch (authEx: DriveAuthResolutionException) {
                pendingDriveRetryAction = { uploadRecentToDrive(items) }
                _driveAuthPendingIntent.tryEmit(authEx.pendingIntent)
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_upload_failed, e.message ?: unknownErrorText())
                )
            } finally {
                _isSyncingDrive.value = false
            }
        }
    }

    fun uploadDevicePdfsToDrive(items: List<DevicePdfItem>) {
        if (!requireOnlineAndAccount()) return
        if (items.isEmpty()) {
            _snackbarMessages.tryEmit(
                str(R.string.msg_no_files_selected_to_upload)
            )
            return
        }
        if (_isSyncingDrive.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingDrive.value = true
            try {
                val token = driveSyncManager.fetchAccessToken()
                val recentByHash = _recentFiles.value.associateBy { it.fileHash }
                val candidates = mutableListOf<UploadCandidate>()
                for (dev in items) {
                    val hash = driveSyncManager.computePdfHashForUri(dev.uri) ?: continue
                    val resolved = resolveToReadableTempFile(
                        uri = dev.uri,
                        fallbackPath = null,
                        prefix = "drive_dev",
                        ext = ".pdf"
                    ) ?: continue
                    val (file, isTemp) = resolved
                    val existingRecent = recentByHash[hash]
                    val pageCount = if (existingRecent == null) {
                        resolvePdfPageCount(dev.uri, null) ?: 0
                    } else {
                        existingRecent.totalPages.toInt()
                    }
                    val progress = existingRecent ?: ReadingProgress(
                        fileHash = hash,
                        fileUri = dev.uri.toString(),
                        fallbackCachePath = null,
                        displayName = dev.displayName,
                        lastPageIndex = 0u,
                        pageOffsetFraction = 0f,
                        zoomScale = 1f,
                        panOffsetXFraction = 0f,
                        manualRotation = PageRotation.DEG0,
                        darkModeEnabled = _globalDarkMode.value,
                        lastOpenedUnix = dev.dateModifiedSec.takeIf { it > 0L }
                            ?: (System.currentTimeMillis() / 1000L),
                        totalPages = pageCount.coerceAtLeast(0).toUInt()
                    )
                    val existingDriveId = driveSyncManager.findAppDataFileByHash(token, hash)
                    candidates.add(
                        UploadCandidate(
                            progress = progress,
                            file = file,
                            isTemp = isTemp,
                            sizeBytes = file.length(),
                            existingDriveId = existingDriveId,
                            inRecent = existingRecent != null
                        )
                    )
                }
                uploadCandidatesInternal(token, candidates)
            } catch (authEx: DriveAuthResolutionException) {
                pendingDriveRetryAction = { uploadDevicePdfsToDrive(items) }
                _driveAuthPendingIntent.tryEmit(authEx.pendingIntent)
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_upload_failed, e.message ?: unknownErrorText())
                )
            } finally {
                _isSyncingDrive.value = false
            }
        }
    }

    private suspend fun uploadCandidatesInternal(
        token: String,
        candidates: List<UploadCandidate>
    ) {
        try {
            if (candidates.isEmpty()) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_no_readable_pdfs_to_upload)
                )
                return
            }

            val bytesNeeded = candidates
                .filter { it.existingDriveId == null }
                .sumOf { it.sizeBytes }

            if (bytesNeeded > 0L) {
                val remainingBytes = driveSyncManager.getRemainingStorageBytes(token)
                if (remainingBytes != null && bytesNeeded > remainingBytes) {
                    _snackbarMessages.tryEmit(
                        str(
                            R.string.msg_not_enough_drive_space,
                            formatStorageBytes(bytesNeeded),
                            formatStorageBytes(remainingBytes)
                        )
                    )
                    return
                }
            }

            val (manifestId, existingItems) = driveSyncManager.loadManifest(token)
            val manifestMap = existingItems.associateBy { it.fileHash }.toMutableMap()
            val favHashes = _favoriteHashes.value
            val favUris = _favoriteUris.value

            var uploadedCount = 0
            var skippedCount = 0
            val conflicts = mutableListOf<DriveSyncConflict>()

            for (candidate in candidates) {
                val item = candidate.progress
                val alreadyInManifest = manifestMap[item.fileHash]
                val driveFileId = candidate.existingDriveId
                    ?: driveSyncManager.ensurePdfUploaded(
                        accessToken = token,
                        fileHash = item.fileHash,
                        displayName = item.displayName,
                        localFile = candidate.file
                    )
                val localIsFav = item.fileHash in favHashes || item.fileUri in favUris

                if (candidate.inRecent &&
                    alreadyInManifest != null &&
                    (alreadyInManifest.lastPageIndex != item.lastPageIndex.toInt() ||
                        alreadyInManifest.isFavorite != localIsFav) &&
                    alreadyInManifest.lastOpenedUnix > item.lastOpenedUnix
                ) {
                    conflicts.add(
                        DriveSyncConflict(
                            fileHash = item.fileHash,
                            displayName = item.displayName,
                            localProgress = item,
                            localIsFavorite = localIsFav,
                            remoteItem = alreadyInManifest
                        )
                    )
                } else {
                    if (alreadyInManifest == null) uploadedCount++ else skippedCount++
                    val effectiveInRecent = candidate.inRecent || (alreadyInManifest?.inRecent == true)
                    manifestMap[item.fileHash] = DriveManifestItem.fromReadingProgress(
                        progress = if (!candidate.inRecent && alreadyInManifest != null) {
                            item.copy(
                                lastPageIndex = alreadyInManifest.lastPageIndex.coerceAtLeast(0).toUInt(),
                                totalPages = alreadyInManifest.totalPages.coerceAtLeast(0).toUInt()
                            )
                        } else item,
                        isFavorite = localIsFav || (alreadyInManifest?.isFavorite == true),
                        inRecent = effectiveInRecent,
                        driveFileId = driveFileId,
                        fileSizeBytes = candidate.sizeBytes
                    )
                }
            }

            driveSyncManager.saveManifest(token, manifestId, manifestMap.values.toList())
            if (conflicts.isNotEmpty()) {
                _driveSyncConflicts.value = conflicts
            }
            val summary = if (uploadedCount > 0) {
                str(R.string.msg_uploaded_to_drive, uploadedCount)
            } else {
                str(R.string.msg_synced_metadata_drive, skippedCount)
            }
            _snackbarMessages.tryEmit(summary)
        } finally {
            candidates.forEach { if (it.isTemp) it.file.delete() }
        }
    }

    private fun formatStorageBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun syncWithGoogleDrive() {
        if (!requireOnlineAndAccount()) return
        if (_isSyncingDrive.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingDrive.value = true
            try {
                val token = driveSyncManager.fetchAccessToken()
                val (_, remoteItems) = driveSyncManager.loadManifest(token)
                if (remoteItems.isEmpty()) {
                    _snackbarMessages.tryEmit(
                        str(R.string.msg_no_synced_files_drive)
                    )
                    return@launch
                }

                val localRecentsByHash = _recentFiles.value.associateBy { it.fileHash }
                val favHashes = _favoriteHashes.value.toMutableSet()
                val favUris = _favoriteUris.value
                val deviceList = _devicePdfs.value

                var downloadedCount = 0
                var linkedLocalCount = 0
                val conflicts = mutableListOf<DriveSyncConflict>()

                for (remote in remoteItems) {
                    val existingRecent = localRecentsByHash[remote.fileHash]
                    if (existingRecent != null) {
                        var currentRecent = existingRecent
                        // If the file was deleted from the device, re-download it to Downloads/Bolt PDF so it's reachable on device too
                        val stillOnDevice = findMatchingDevicePdf(remote, deviceList) != null ||
                            isLocalFileReadable(existingRecent.fileUri, existingRecent.fallbackCachePath)
                        if (!stillOnDevice && remote.driveFileId.isNotBlank()) {
                            val (newUri, newFallback) = driveSyncManager.downloadPdfToDevice(
                                accessToken = token,
                                driveFileId = remote.driveFileId,
                                displayName = remote.displayName,
                                fileHash = remote.fileHash
                            )
                            currentRecent = existingRecent.copy(
                                fileUri = newUri.toString(),
                                fallbackCachePath = newFallback
                            )
                            runCatching { engine.saveProgress(currentRecent) }
                            downloadedCount++
                        }

                        val localIsFav = currentRecent.fileHash in favHashes || currentRecent.fileUri in favUris
                        val pageDiffers = currentRecent.lastPageIndex.toInt() != remote.lastPageIndex
                        val favDiffers = localIsFav != remote.isFavorite
                        if (pageDiffers || favDiffers) {
                            conflicts.add(
                                DriveSyncConflict(
                                    fileHash = remote.fileHash,
                                    displayName = currentRecent.displayName.ifBlank { remote.displayName },
                                    localProgress = currentRecent,
                                    localIsFavorite = localIsFav,
                                    remoteItem = remote
                                )
                            )
                        }
                        continue
                    }

                    // Not in Recent yet: check if file already exists on this device to avoid downloading & duplicating
                    val localDeviceMatch = findMatchingDevicePdf(remote, deviceList)
                    val targetUri: String
                    val fallbackPath: String?
                    if (localDeviceMatch != null) {
                        targetUri = localDeviceMatch.uri.toString()
                        fallbackPath = null
                        if (remote.inRecent || remote.isFavorite) {
                            linkedLocalCount++
                        }
                    } else {
                        if (remote.driveFileId.isBlank()) continue
                        val (downloadedUri, downloadedFallback) = driveSyncManager.downloadPdfToDevice(
                            accessToken = token,
                            driveFileId = remote.driveFileId,
                            displayName = remote.displayName,
                            fileHash = remote.fileHash
                        )
                        targetUri = downloadedUri.toString()
                        fallbackPath = downloadedFallback
                        downloadedCount++
                    }

                    // If it was uploaded from On Device without ever being opened or favorited,
                    // keep it in On Device only — do not insert it into Recent or Favorites.
                    if (!remote.inRecent && !remote.isFavorite) {
                        continue
                    }

                    val rotation = runCatching {
                        PageRotation.valueOf(remote.manualRotation)
                    }.getOrDefault(PageRotation.DEG0)

                    val resolvedTotalPages = if (remote.totalPages > 0) {
                        remote.totalPages
                    } else {
                        resolvePdfPageCount(Uri.parse(targetUri), fallbackPath) ?: 0
                    }

                    val restoredProgress = ReadingProgress(
                        fileHash = remote.fileHash,
                        fileUri = targetUri,
                        fallbackCachePath = fallbackPath,
                        displayName = remote.displayName,
                        lastPageIndex = remote.lastPageIndex.coerceAtLeast(0).toUInt(),
                        pageOffsetFraction = remote.pageOffsetFraction,
                        zoomScale = remote.zoomScale.coerceAtLeast(1f),
                        panOffsetXFraction = remote.panOffsetXFraction,
                        manualRotation = rotation,
                        darkModeEnabled = remote.darkModeEnabled,
                        lastOpenedUnix = remote.lastOpenedUnix.takeIf { it > 0L }
                            ?: (System.currentTimeMillis() / 1000L),
                        totalPages = resolvedTotalPages.coerceAtLeast(0).toUInt()
                    )
                    runCatching { engine.saveProgress(restoredProgress) }

                    if (remote.isFavorite) {
                        favHashes.add(remote.fileHash)
                    }
                }

                if (favHashes != _favoriteHashes.value) {
                    _favoriteHashes.value = favHashes
                    prefs.edit().putStringSet("favorite_hashes", favHashes).apply()
                }
                refreshRecentFiles()
                if (downloadedCount > 0) {
                    refreshDevicePdfs(onlyIfNeeded = false)
                }

                if (conflicts.isNotEmpty()) {
                    _driveSyncConflicts.value = conflicts
                }

                val summary = when {
                    downloadedCount > 0 || linkedLocalCount > 0 -> str(
                        R.string.msg_synced_summary,
                        downloadedCount,
                        linkedLocalCount
                    )
                    conflicts.isNotEmpty() -> str(R.string.msg_sync_conflicts_found, conflicts.size)
                    else -> str(R.string.msg_already_up_to_date)
                }
                _snackbarMessages.tryEmit(summary)
            } catch (authEx: DriveAuthResolutionException) {
                pendingDriveRetryAction = { syncWithGoogleDrive() }
                _driveAuthPendingIntent.tryEmit(authEx.pendingIntent)
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_sync_failed, e.message ?: unknownErrorText())
                )
            } finally {
                _isSyncingDrive.value = false
            }
        }
    }

    fun resolveDriveConflicts(resolutions: Map<String, ConflictResolutionChoice>) {
        val currentConflicts = _driveSyncConflicts.value
        if (currentConflicts.isEmpty()) return
        if (!requireOnlineAndAccount()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingDrive.value = true
            try {
                val favHashes = _favoriteHashes.value.toMutableSet()
                var needDriveUpdate = false
                val localWinsMap = mutableMapOf<String, DriveSyncConflict>()

                for (conflict in currentConflicts) {
                    val choice = resolutions[conflict.fileHash] ?: ConflictResolutionChoice.KEEP_LOCAL
                    if (choice == ConflictResolutionChoice.KEEP_LOCAL) {
                        needDriveUpdate = true
                        localWinsMap[conflict.fileHash] = conflict
                    } else {
                        val remote = conflict.remoteItem
                        val rotation = runCatching {
                            PageRotation.valueOf(remote.manualRotation)
                        }.getOrDefault(PageRotation.DEG0)
                        val updatedLocal = conflict.localProgress.copy(
                            lastPageIndex = remote.lastPageIndex.coerceAtLeast(0).toUInt(),
                            pageOffsetFraction = remote.pageOffsetFraction,
                            zoomScale = remote.zoomScale.coerceAtLeast(1f),
                            panOffsetXFraction = remote.panOffsetXFraction,
                            manualRotation = rotation,
                            darkModeEnabled = remote.darkModeEnabled,
                            lastOpenedUnix = remote.lastOpenedUnix.takeIf { it > 0L }
                                ?: conflict.localProgress.lastOpenedUnix,
                            totalPages = if (remote.totalPages > 0) {
                                remote.totalPages.toUInt()
                            } else {
                                conflict.localProgress.totalPages
                            }
                        )
                        runCatching { engine.saveProgress(updatedLocal) }
                        if (remote.isFavorite) {
                            favHashes.add(conflict.fileHash)
                        } else {
                            favHashes.remove(conflict.fileHash)
                        }
                    }
                }

                if (favHashes != _favoriteHashes.value) {
                    _favoriteHashes.value = favHashes
                    prefs.edit().putStringSet("favorite_hashes", favHashes).apply()
                }

                if (needDriveUpdate) {
                    val token = driveSyncManager.fetchAccessToken()
                    val (manifestId, existingItems) = driveSyncManager.loadManifest(token)
                    val manifestMap = existingItems.associateBy { it.fileHash }.toMutableMap()
                    for ((hash, conflict) in localWinsMap) {
                        manifestMap[hash] = DriveManifestItem.fromReadingProgress(
                            progress = conflict.localProgress,
                            isFavorite = conflict.localIsFavorite,
                            driveFileId = conflict.remoteItem.driveFileId,
                            fileSizeBytes = conflict.remoteItem.fileSizeBytes
                        )
                    }
                    driveSyncManager.saveManifest(token, manifestId, manifestMap.values.toList())
                }

                _driveSyncConflicts.value = emptyList()
                refreshRecentFiles()
                _snackbarMessages.tryEmit(
                    str(R.string.msg_sync_conflicts_resolved)
                )
            } catch (authEx: DriveAuthResolutionException) {
                pendingDriveRetryAction = { resolveDriveConflicts(resolutions) }
                _driveAuthPendingIntent.tryEmit(authEx.pendingIntent)
            } catch (e: Exception) {
                _snackbarMessages.tryEmit(
                    str(R.string.msg_failed_to_resolve_conflicts, e.message ?: unknownErrorText())
                )
            } finally {
                _isSyncingDrive.value = false
            }
        }
    }

    private fun isLocalFileReadable(fileUri: String, fallbackPath: String?): Boolean {
        val uri = runCatching { Uri.parse(fileUri) }.getOrNull()
        if (uri != null) {
            if (uri.scheme == "file" && uri.path != null) {
                val f = File(uri.path!!)
                if (f.exists() && f.canRead() && f.length() > 0L) return true
            } else {
                val ok = runCatching {
                    appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                }.getOrDefault(false)
                if (ok) return true
            }
        }
        if (fallbackPath != null) {
            val f = File(fallbackPath)
            if (f.exists() && f.canRead() && f.length() > 0L) return true
        }
        return false
    }

    private fun findMatchingDevicePdf(
        remote: DriveManifestItem,
        deviceList: List<DevicePdfItem>
    ): DevicePdfItem? {
        val candidates = if (remote.fileSizeBytes > 0L) {
            deviceList.filter { it.sizeBytes == remote.fileSizeBytes }
        } else {
            deviceList
        }
        for (candidate in candidates) {
            val hash = driveSyncManager.computePdfHashForUri(candidate.uri)
            if (hash == remote.fileHash) return candidate
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        engine.cancelActiveSearch()
    }
}

