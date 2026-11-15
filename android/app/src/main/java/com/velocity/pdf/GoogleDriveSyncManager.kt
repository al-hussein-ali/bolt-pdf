package com.velocity.pdf

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import uniffi.pdf_core.PageRotation
import uniffi.pdf_core.ReadingProgress
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DriveManifestItem(
    val fileHash: String,
    val displayName: String,
    val lastPageIndex: Int,
    val totalPages: Int,
    val pageOffsetFraction: Float,
    val zoomScale: Float,
    val panOffsetXFraction: Float,
    val manualRotation: String,
    val darkModeEnabled: Boolean,
    val lastOpenedUnix: Long,
    val isFavorite: Boolean,
    val inRecent: Boolean = true,
    val driveFileId: String,
    val fileSizeBytes: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fileHash", fileHash)
        put("displayName", displayName)
        put("lastPageIndex", lastPageIndex)
        put("totalPages", totalPages)
        put("pageOffsetFraction", pageOffsetFraction.toDouble())
        put("zoomScale", zoomScale.toDouble())
        put("panOffsetXFraction", panOffsetXFraction.toDouble())
        put("manualRotation", manualRotation)
        put("darkModeEnabled", darkModeEnabled)
        put("lastOpenedUnix", lastOpenedUnix)
        put("isFavorite", isFavorite)
        put("inRecent", inRecent)
        put("driveFileId", driveFileId)
        put("fileSizeBytes", fileSizeBytes)
    }

    companion object {
        fun fromJson(obj: JSONObject): DriveManifestItem = DriveManifestItem(
            fileHash = obj.optString("fileHash", ""),
            displayName = obj.optString("displayName", "Document.pdf"),
            lastPageIndex = obj.optInt("lastPageIndex", 0),
            totalPages = obj.optInt("totalPages", 0),
            pageOffsetFraction = obj.optDouble("pageOffsetFraction", 0.0).toFloat(),
            zoomScale = obj.optDouble("zoomScale", 1.0).toFloat(),
            panOffsetXFraction = obj.optDouble("panOffsetXFraction", 0.0).toFloat(),
            manualRotation = obj.optString("manualRotation", PageRotation.DEG0.name),
            darkModeEnabled = obj.optBoolean("darkModeEnabled", false),
            lastOpenedUnix = obj.optLong("lastOpenedUnix", 0L),
            isFavorite = obj.optBoolean("isFavorite", false),
            inRecent = obj.optBoolean("inRecent", true),
            driveFileId = obj.optString("driveFileId", ""),
            fileSizeBytes = obj.optLong("fileSizeBytes", 0L)
        )

        fun fromReadingProgress(
            progress: ReadingProgress,
            isFavorite: Boolean,
            inRecent: Boolean = true,
            driveFileId: String,
            fileSizeBytes: Long
        ): DriveManifestItem = DriveManifestItem(
            fileHash = progress.fileHash,
            displayName = progress.displayName,
            lastPageIndex = progress.lastPageIndex.toInt(),
            totalPages = progress.totalPages.toInt(),
            pageOffsetFraction = progress.pageOffsetFraction,
            zoomScale = progress.zoomScale,
            panOffsetXFraction = progress.panOffsetXFraction,
            manualRotation = progress.manualRotation.name,
            darkModeEnabled = progress.darkModeEnabled,
            lastOpenedUnix = progress.lastOpenedUnix,
            isFavorite = isFavorite,
            inRecent = inRecent,
            driveFileId = driveFileId,
            fileSizeBytes = fileSizeBytes
        )
    }
}

class DriveAuthResolutionException(val pendingIntent: PendingIntent) :
    Exception("Google Drive authorization requires user consent")

class GoogleDriveSyncManager(private val context: Context) {

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val MANIFEST_FILE_NAME = "bolt_pdf_sync_manifest.json"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val DRIVE_ABOUT_URL = "https://www.googleapis.com/drive/v3/about"
        private const val HASH_CHUNK_BYTES = 65_536
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun buildSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
    }

    fun getSignInIntent(): Intent {
        return GoogleSignIn.getClient(context, buildSignInOptions()).signInIntent
    }

    fun getLastSignedInEmail(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return account.email
    }

    fun parseSignInResult(data: Intent?): GoogleSignInAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return task.getResult(ApiException::class.java)
    }

    suspend fun signOut() {
        runCatching {
            GoogleSignIn.getClient(context, buildSignInOptions()).signOut().awaitResult()
        }
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun fetchAccessToken(): String = withContext(Dispatchers.IO) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context)
            .authorize(request)
            .awaitResult()
        if (result.hasResolution()) {
            val pending = result.pendingIntent
                ?: throw IllegalStateException("Authorization required without PendingIntent")
            throw DriveAuthResolutionException(pending)
        }
        result.accessToken ?: throw IllegalStateException("No access token returned")
    }

    /**
     * Loads the sync manifest from Google Drive's private appDataFolder.
     * Returns Pair(manifestFileIdOrNull, itemsList).
     */
    suspend fun loadManifest(accessToken: String): Pair<String?, List<DriveManifestItem>> =
        withContext(Dispatchers.IO) {
            val fileId = findAppDataFileId(accessToken, MANIFEST_FILE_NAME)
                ?: return@withContext null to emptyList()

            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Drive manifest read failed (${response.code})")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext fileId to emptyList()
                val root = JSONObject(body)
                val arr = root.optJSONArray("items") ?: JSONArray()
                val items = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val parsed = DriveManifestItem.fromJson(obj)
                        if (parsed.fileHash.isNotBlank()) add(parsed)
                    }
                }
                fileId to items
            }
        }

    /**
     * Creates or updates the sync manifest in Google Drive's private appDataFolder.
     */
    suspend fun saveManifest(
        accessToken: String,
        existingManifestFileId: String?,
        items: List<DriveManifestItem>
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", 1)
            put("updatedAtUnix", System.currentTimeMillis() / 1000L)
            put("items", JSONArray().apply {
                items.forEach { put(it.toJson()) }
            })
        }
        val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
        val jsonBody = jsonBytes.toRequestBody("application/json; charset=utf-8".toMediaType())

        if (existingManifestFileId != null) {
            val request = Request.Builder()
                .url("$DRIVE_UPLOAD_URL/$existingManifestFileId?uploadType=media")
                .header("Authorization", "Bearer $accessToken")
                .patch(jsonBody)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Drive manifest update failed (${response.code})")
                }
                return@withContext existingManifestFileId
            }
        } else {
            val metadata = JSONObject().apply {
                put("name", MANIFEST_FILE_NAME)
                put("parents", JSONArray().apply { put("appDataFolder") })
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata)
                .addPart(jsonBody)
                .build()

            val request = Request.Builder()
                .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Drive manifest create failed (${response.code})")
                }
                val respJson = JSONObject(response.body?.string().orEmpty())
                return@withContext respJson.getString("id")
            }
        }
    }

    /**
     * Checks if a PDF with [fileHash] already exists in appDataFolder; if not, uploads [localFile].
     * Returns the Drive file ID.
     */
    suspend fun ensurePdfUploaded(
        accessToken: String,
        fileHash: String,
        displayName: String,
        localFile: File
    ): String = withContext(Dispatchers.IO) {
        val existingId = findAppDataFileByHash(accessToken, fileHash)
        if (existingId != null) return@withContext existingId

        val safeName = displayName.ifBlank { "$fileHash.pdf" }
        val metadata = JSONObject().apply {
            put("name", safeName)
            put("parents", JSONArray().apply { put("appDataFolder") })
            put("appProperties", JSONObject().apply {
                put("fileHash", fileHash)
            })
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val fileBody = localFile.asRequestBody("application/pdf".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata)
            .addPart(fileBody)
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(multipart)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive upload failed (${response.code})")
            }
            val respJson = JSONObject(response.body?.string().orEmpty())
            respJson.getString("id")
        }
    }

    /**
     * Returns remaining Google Drive storage in bytes, or null if quota is unlimited/unavailable.
     */
    suspend fun getRemainingStorageBytes(accessToken: String): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$DRIVE_ABOUT_URL?fields=storageQuota")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive quota check failed (${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val quota = root.optJSONObject("storageQuota") ?: return@withContext null
            val limit = quota.optString("limit", "").toLongOrNull() ?: return@withContext null
            if (limit <= 0L) return@withContext null
            val usage = quota.optString("usage", "").toLongOrNull() ?: 0L
            (limit - usage).coerceAtLeast(0L)
        }
    }

    suspend fun downloadPdfToDevice(
        accessToken: String,
        driveFileId: String,
        displayName: String,
        fileHash: String
    ): Pair<Uri, String?> = withContext(Dispatchers.IO) {
        val baseName = displayName.trim().ifBlank { "Document_$fileHash" }
        val safeName = if (baseName.endsWith(".pdf", ignoreCase = true)) baseName else "$baseName.pdf"
        val sanitized = safeName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, sanitized)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Bolt PDF")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create file in Downloads/Bolt PDF")
            try {
                val request = Request.Builder()
                    .url("$DRIVE_FILES_URL/$driveFileId?alt=media")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Drive download failed (${response.code})")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty Drive response")
                    resolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Unable to open output stream for $sanitized")
                }
                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                return@withContext uri to null
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Bolt PDF"
            ).apply { mkdirs() }
            var destFile = File(downloadsDir, sanitized)
            if (destFile.exists()) {
                val stem = sanitized.removeSuffix(".pdf").removeSuffix(".PDF")
                destFile = File(downloadsDir, "${stem}_${fileHash.take(6)}.pdf")
            }
            downloadPdf(accessToken, driveFileId, destFile)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("application/pdf"),
                null
            )
            return@withContext Uri.fromFile(destFile) to destFile.absolutePath
        }
    }

    suspend fun downloadPdf(
        accessToken: String,
        driveFileId: String,
        destFile: File
    ) = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$driveFileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive download failed (${response.code})")
            }
            val body = response.body ?: throw IllegalStateException("Empty Drive response")
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (destFile.exists()) destFile.delete()
        if (!tempFile.renameTo(destFile)) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun findAppDataFileId(accessToken: String, name: String): String? {
        val escaped = name.replace("'", "\\'")
        val url = Uri.parse(DRIVE_FILES_URL).buildUpon()
            .appendQueryParameter("spaces", "appDataFolder")
            .appendQueryParameter("q", "name = '$escaped' and trashed = false")
            .appendQueryParameter("fields", "files(id,name)")
            .appendQueryParameter("pageSize", "1")
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive query failed (${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val files = root.optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }
    }

    fun findAppDataFileByHash(accessToken: String, fileHash: String): String? {
        val escaped = fileHash.replace("'", "\\'")
        val url = Uri.parse(DRIVE_FILES_URL).buildUpon()
            .appendQueryParameter("spaces", "appDataFolder")
            .appendQueryParameter(
                "q",
                "appProperties has { key='fileHash' and value='$escaped' } and trashed = false"
            )
            .appendQueryParameter("fields", "files(id)")
            .appendQueryParameter("pageSize", "1")
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string().orEmpty())
            val files = root.optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Computes the exact same `< 1ms` fingerprint as Rust `compute_file_hash`:
     * SHA-256(len_u64_le + head_64KiB + tail_64KiB).
     */
    fun computePdfHashForUri(uri: Uri): String? {
        return runCatching {
            if (uri.scheme == "file" && uri.path != null) {
                return computePdfHashForFile(File(uri.path!!))
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                computePdfHashFromPfd(pfd)
            }
        }.getOrNull()
    }

    fun computePdfHashForFile(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                computePdfHashFromPfd(pfd)
            }
        }.getOrNull()
    }

    private fun computePdfHashFromPfd(pfd: ParcelFileDescriptor): String? {
        val size = pfd.statSize
        if (size <= 0L) return null
        FileInputStream(pfd.fileDescriptor).use { fis ->
            val channel = fis.channel
            val headLen = minOf(size, HASH_CHUNK_BYTES.toLong()).toInt()
            val headBuf = ByteBuffer.allocate(headLen)
            channel.position(0L)
            while (headBuf.hasRemaining()) {
                if (channel.read(headBuf) <= 0) break
            }

            val tailStart = (size - HASH_CHUNK_BYTES.toLong()).coerceAtLeast(0L)
            val tailLen = (size - tailStart).toInt()
            val tailBuf = ByteBuffer.allocate(tailLen)
            channel.position(tailStart)
            while (tailBuf.hasRemaining()) {
                if (channel.read(tailBuf) <= 0) break
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val lenBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(size).array()
            digest.update(lenBytes)
            digest.update(headBuf.array(), 0, headBuf.position())
            digest.update(tailBuf.array(), 0, tailBuf.position())
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exc ->
        if (cont.isActive) cont.resumeWithException(exc)
    }
}
