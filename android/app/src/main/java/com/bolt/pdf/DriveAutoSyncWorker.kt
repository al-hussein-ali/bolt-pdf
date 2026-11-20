package com.bolt.pdf

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.pdf_core.PageRotation
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class DriveAutoSyncSettings(
    val autoUploadEnabled: Boolean = false,
    val uploadHour: Int = 23,
    val uploadMinute: Int = 0,
    val wifiOnly: Boolean = true,
    val keepOldVersions: Boolean = true,
    val autoSyncEnabled: Boolean = false,
)

object DriveAutoSyncPrefs {
    private const val PREFS_NAME = "velocity_pdf_prefs"
    private const val KEY_AUTO_UPLOAD = "drive_auto_upload_enabled"
    private const val KEY_UPLOAD_HOUR = "drive_auto_upload_hour"
    private const val KEY_UPLOAD_MINUTE = "drive_auto_upload_minute"
    private const val KEY_WIFI_ONLY = "drive_auto_upload_wifi_only"
    private const val KEY_KEEP_OLD = "drive_auto_upload_keep_old_versions"
    private const val KEY_AUTO_SYNC = "drive_auto_sync_enabled"

    fun load(context: Context): DriveAutoSyncSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DriveAutoSyncSettings(
            autoUploadEnabled = prefs.getBoolean(KEY_AUTO_UPLOAD, false),
            uploadHour = prefs.getInt(KEY_UPLOAD_HOUR, 23).coerceIn(0, 23),
            uploadMinute = prefs.getInt(KEY_UPLOAD_MINUTE, 0).coerceIn(0, 59),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
            keepOldVersions = prefs.getBoolean(KEY_KEEP_OLD, true),
            autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, false),
        )
    }

    fun save(context: Context, settings: DriveAutoSyncSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_UPLOAD, settings.autoUploadEnabled)
            .putInt(KEY_UPLOAD_HOUR, settings.uploadHour.coerceIn(0, 23))
            .putInt(KEY_UPLOAD_MINUTE, settings.uploadMinute.coerceIn(0, 59))
            .putBoolean(KEY_WIFI_ONLY, settings.wifiOnly)
            .putBoolean(KEY_KEEP_OLD, settings.keepOldVersions)
            .putBoolean(KEY_AUTO_SYNC, settings.autoSyncEnabled)
            .apply()
    }
}

object DriveAutoSyncScheduler {
    private const val WORK_NAME = "bolt_drive_auto_sync"

    fun scheduleOrCancel(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val settings = DriveAutoSyncPrefs.load(appContext)
        val isSignedIn = GoogleDriveSyncManager(appContext).getLastSignedInEmail() != null

        if (!isSignedIn || (!settings.autoUploadEnabled && !settings.autoSyncEnabled)) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val initialDelayMs = computeInitialDelayMs(settings.uploadHour, settings.uploadMinute)
        val networkType = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<DriveAutoSyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun computeInitialDelayMs(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }
}

private data class RecentDbRow(
    val fileHash: String,
    val fileUri: String,
    val fallbackCachePath: String?,
    val displayName: String,
    val lastPageIndex: Int,
    val pageOffsetFraction: Float,
    val zoomScale: Float,
    val panOffsetXFraction: Float,
    val manualRotationOrdinal: Int,
    val darkModeEnabled: Boolean,
    val lastOpenedUnix: Long,
    val totalPages: Int,
)

class DriveAutoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = DriveAutoSyncPrefs.load(applicationContext)
        if (!settings.autoUploadEnabled && !settings.autoSyncEnabled) {
            return@withContext Result.success()
        }

        val syncManager = GoogleDriveSyncManager(applicationContext)
        if (syncManager.getLastSignedInEmail() == null) {
            return@withContext Result.success()
        }
        if (!syncManager.isOnline()) {
            return@withContext Result.retry()
        }

        val dbFile = File(applicationContext.filesDir, "velocity_pdf.db")
        if (!dbFile.exists()) {
            return@withContext Result.success()
        }

        return@withContext try {
            val token = syncManager.fetchAccessToken()
            val (manifestId, remoteItems) = syncManager.loadManifest(token)
            val manifestMap = remoteItems.associateBy { it.fileHash }.toMutableMap()
            var manifestChanged = false

            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            db.use { database ->
                val localRows = readRecentRows(database)
                val localByHash = localRows.associateBy { it.fileHash }.toMutableMap()

                // 1. Always upload/push BEFORE fetching so newer local changes are never overwritten
                if (settings.autoUploadEnabled && localRows.isNotEmpty()) {
                    for (row in localRows) {
                        val (resolvedFile, isTemp) = resolveLocalFile(row) ?: continue
                        try {
                            val existingRemote = manifestMap[row.fileHash]
                            val driveId = existingRemote?.driveFileId
                                ?: syncManager.findAppDataFileByHash(token, row.fileHash)
                                ?: run {
                                    val quota = runCatching { syncManager.getRemainingStorageBytes(token) }.getOrNull()
                                    if (quota != null && quota < resolvedFile.length()) {
                                        return@run null
                                    }
                                    syncManager.ensurePdfUploaded(
                                        accessToken = token,
                                        fileHash = row.fileHash,
                                        displayName = row.displayName,
                                        localFile = resolvedFile
                                    )
                                } ?: continue

                            if (!settings.keepOldVersions) {
                                val olderSameName = manifestMap.values.filter { old ->
                                    old.displayName == row.displayName &&
                                        old.fileHash != row.fileHash &&
                                        old.lastOpenedUnix < row.lastOpenedUnix
                                }
                                for (old in olderSameName) {
                                    old.driveFileId?.let { oldId ->
                                        runCatching { syncManager.deleteAppDataFile(token, oldId) }
                                    }
                                    manifestMap.remove(old.fileHash)
                                    manifestChanged = true
                                }
                            }

                            if (existingRemote == null || row.lastOpenedUnix >= existingRemote.lastOpenedUnix) {
                                manifestMap[row.fileHash] = DriveManifestItem(
                                    fileHash = row.fileHash,
                                    displayName = row.displayName,
                                    lastPageIndex = row.lastPageIndex,
                                    totalPages = row.totalPages,
                                    pageOffsetFraction = row.pageOffsetFraction,
                                    zoomScale = row.zoomScale,
                                    panOffsetXFraction = row.panOffsetXFraction,
                                    manualRotation = PageRotation.entries
                                        .getOrElse(row.manualRotationOrdinal) { PageRotation.DEG0 }
                                        .name,
                                    darkModeEnabled = row.darkModeEnabled,
                                    lastOpenedUnix = row.lastOpenedUnix,
                                    isFavorite = existingRemote?.isFavorite ?: false,
                                    inRecent = true,
                                    driveFileId = driveId,
                                    fileSizeBytes = resolvedFile.length()
                                )
                                manifestChanged = true
                            }
                        } finally {
                            if (isTemp) {
                                runCatching { resolvedFile.delete() }
                            }
                        }
                    }
                    if (manifestChanged) {
                        syncManager.saveManifest(token, manifestId, manifestMap.values.toList())
                    }
                }

                // 2. Auto-sync (fetch from Drive) after upload completes
                if (settings.autoSyncEnabled) {
                    for (remote in manifestMap.values) {
                        val local = localByHash[remote.fileHash]
                        if (local == null) {
                            val driveId = remote.driveFileId.takeIf { it.isNotBlank() }
                                ?: syncManager.findAppDataFileByHash(token, remote.fileHash)
                                ?: continue
                            val (newUri, newFallback) = runCatching {
                                syncManager.downloadPdfToDevice(
                                    accessToken = token,
                                    driveFileId = driveId,
                                    displayName = remote.displayName,
                                    fileHash = remote.fileHash
                                )
                            }.getOrNull() ?: continue

                            val rotationOrdinal = runCatching {
                                PageRotation.valueOf(remote.manualRotation).ordinal
                            }.getOrDefault(0)
                            upsertRecentRow(
                                database,
                                RecentDbRow(
                                    fileHash = remote.fileHash,
                                    fileUri = newUri.toString(),
                                    fallbackCachePath = newFallback,
                                    displayName = remote.displayName,
                                    lastPageIndex = remote.lastPageIndex,
                                    pageOffsetFraction = remote.pageOffsetFraction,
                                    zoomScale = remote.zoomScale,
                                    panOffsetXFraction = 0.5f,
                                    manualRotationOrdinal = rotationOrdinal,
                                    darkModeEnabled = remote.darkModeEnabled,
                                    lastOpenedUnix = remote.lastOpenedUnix,
                                    totalPages = remote.totalPages
                                )
                            )
                        } else if (remote.lastOpenedUnix > local.lastOpenedUnix) {
                            // Only update local progress if remote is strictly newer
                            val rotationOrdinal = runCatching {
                                PageRotation.valueOf(remote.manualRotation).ordinal
                            }.getOrDefault(local.manualRotationOrdinal)
                            upsertRecentRow(
                                database,
                                local.copy(
                                    lastPageIndex = remote.lastPageIndex,
                                    pageOffsetFraction = remote.pageOffsetFraction,
                                    zoomScale = remote.zoomScale,
                                    manualRotationOrdinal = rotationOrdinal,
                                    darkModeEnabled = remote.darkModeEnabled,
                                    lastOpenedUnix = remote.lastOpenedUnix,
                                    totalPages = remote.totalPages.coerceAtLeast(local.totalPages)
                                )
                            )
                        }
                    }
                }
            }
            Result.success()
        } catch (_: DriveAuthResolutionException) {
            // Cannot prompt UI from background worker
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun resolveLocalFile(row: RecentDbRow): Pair<File, Boolean>? {
        val fb = row.fallbackCachePath?.let(::File)
        if (fb != null && fb.exists() && fb.length() > 0L) {
            return fb to false
        }
        val uri = runCatching { Uri.parse(row.fileUri) }.getOrNull() ?: return null
        if (uri.scheme == "file") {
            val f = uri.path?.let(::File)
            if (f != null && f.exists() && f.length() > 0L) return f to false
        }
        val tmp = File(applicationContext.cacheDir, "autosync_${row.fileHash}.pdf")
        val copied = runCatching {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }.getOrDefault(false)
        return if (copied && tmp.exists() && tmp.length() > 0L) tmp to true else {
            runCatching { tmp.delete() }
            null
        }
    }

    private fun readRecentRows(db: SQLiteDatabase): List<RecentDbRow> {
        val out = mutableListOf<RecentDbRow>()
        db.rawQuery(
            """
            SELECT file_hash, file_uri, fallback_cache_path, display_name,
                   last_page_index, page_offset_fraction, zoom_scale,
                   pan_offset_x_fraction, manual_rotation, dark_mode_enabled,
                   last_opened_unix, total_pages
            FROM recent_files
            ORDER BY last_opened_unix DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    RecentDbRow(
                        fileHash = c.getString(0),
                        fileUri = c.getString(1),
                        fallbackCachePath = c.getString(2),
                        displayName = c.getString(3),
                        lastPageIndex = c.getInt(4),
                        pageOffsetFraction = c.getFloat(5),
                        zoomScale = c.getFloat(6),
                        panOffsetXFraction = c.getFloat(7),
                        manualRotationOrdinal = c.getInt(8),
                        darkModeEnabled = c.getInt(9) != 0,
                        lastOpenedUnix = c.getLong(10),
                        totalPages = c.getInt(11),
                    )
                )
            }
        }
        return out
    }

    private fun upsertRecentRow(db: SQLiteDatabase, row: RecentDbRow) {
        val cv = ContentValues().apply {
            put("file_hash", row.fileHash)
            put("file_uri", row.fileUri)
            put("fallback_cache_path", row.fallbackCachePath)
            put("display_name", row.displayName)
            put("last_page_index", row.lastPageIndex)
            put("page_offset_fraction", row.pageOffsetFraction)
            put("zoom_scale", row.zoomScale)
            put("pan_offset_x_fraction", row.panOffsetXFraction)
            put("manual_rotation", row.manualRotationOrdinal)
            put("dark_mode_enabled", if (row.darkModeEnabled) 1 else 0)
            put("last_opened_unix", row.lastOpenedUnix)
            put("total_pages", row.totalPages)
        }
        db.insertWithOnConflict("recent_files", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
