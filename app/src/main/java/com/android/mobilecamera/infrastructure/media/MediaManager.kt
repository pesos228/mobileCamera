package com.android.mobilecamera.infrastructure.media

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "MediaManager"

sealed class DeleteResult {
    data class Success(val deletedCount: Int) : DeleteResult()
    data class RequiresPermission(val intentSender: IntentSender) : DeleteResult()
    object Error : DeleteResult()
}

class MediaManager(context: Context) {

    private val contentResolver = context.contentResolver

    fun createPhotoOutputOptions(): ImageCapture.OutputFileOptions {
        val fileName = generateFileName("IMG")
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MobileCamera")
            }
            else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "MobileCamera")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, "$fileName.jpg")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
        }
        return ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
    }

    fun createVideoOutputOptions(): MediaStoreOutputOptions {
        val fileName = generateFileName("VID")
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MobileCamera")
            }
            else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val appDir = File(moviesDir, "MobileCamera")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, "$fileName.mp4")
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
        }
        return MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()
    }

    private fun generateFileName(prefix: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        return "${prefix}_$timestamp"
    }


    suspend fun deleteMultipleFiles(uris: List<String>): DeleteResult = withContext(Dispatchers.IO) {
        val uriList = uris.map { it.toUri() }

        val existingUris = uriList.filter { isFileExists(it) }

        if (existingUris.isEmpty()) {
            return@withContext DeleteResult.Success(deletedCount = uriList.size)
        }

        val filesRequiringPermission = mutableListOf<Uri>()
        var deletedDirectlyCount = 0

        for (uri in existingUris) {
            try {
                val rows = contentResolver.delete(uri, null, null)
                if (rows > 0) {
                    deletedDirectlyCount++
                } else {
                    if (isFileExists(uri)) {
                        filesRequiringPermission.add(uri)
                    } else {
                        deletedDirectlyCount++
                    }
                }
            } catch (_: SecurityException) {
                filesRequiringPermission.add(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-deleting $uri", e)
            }
        }
        if (filesRequiringPermission.isEmpty()) {
            return@withContext DeleteResult.Success(deletedCount = uriList.size)
        }

        return@withContext requestBatchDeletePermission(filesRequiringPermission)
    }

    private fun requestBatchDeletePermission(uris: List<Uri>): DeleteResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                DeleteResult.RequiresPermission(pi.intentSender)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create delete request (Android 11+)", e)
                DeleteResult.Error
            }
        }
        else {
            val uri = uris.first()
            return try {
                contentResolver.delete(uri, null, null)
                Log.e(TAG, "Unexpected state: file deleted without permission request on retry")
                DeleteResult.Error
            } catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val recoverable = securityException as? RecoverableSecurityException
                    if (recoverable != null) {
                        return DeleteResult.RequiresPermission(recoverable.userAction.actionIntent.intentSender)
                    }
                }
                Log.e(TAG, "Permission denied permanently or legacy storage issue", securityException)
                DeleteResult.Error
            } catch (e: Exception) {
                Log.e(TAG, "Unknown error during delete retry", e)
                DeleteResult.Error
            }
        }
    }

    private fun isFileExists(uri: Uri): Boolean {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check file existence: $uri", e)
            false
        }
    }
}