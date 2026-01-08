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

sealed class DeleteResult {
    data class Success(val deletedCount: Int) : DeleteResult()
    data class RequiresPermission(val intentSender: IntentSender) : DeleteResult()
    data class Error(val message: String) : DeleteResult()
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
                // Для Android 9 и ниже нужно явно создать папку и указать путь в DATA
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
                // Для Android 9 и ниже
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
                // На API 26-28 это удалит файл, если есть разрешение WRITE_EXTERNAL_STORAGE
                // На API 29+ это удалит файл, если он принадлежит приложению
                val rows = contentResolver.delete(uri, null, null)
                if (rows > 0) {
                    deletedDirectlyCount++
                } else {
                    // Файл есть, но удалить не вышло.
                    // На старых API это может значить ошибку доступа, добавим в список ошибок
                    if (isFileExists(uri)) {
                        filesRequiringPermission.add(uri)
                    } else {
                        deletedDirectlyCount++
                    }
                }
            } catch (_: SecurityException) {
                // На API 29+ (Android 10+) это означает "нужны права"
                filesRequiringPermission.add(uri)
            } catch (e: Exception) {
                Log.e("MediaManager", "Error pre-deleting $uri", e)
            }
        }

        if (filesRequiringPermission.isEmpty()) {
            return@withContext DeleteResult.Success(deletedCount = uriList.size)
        }

        // 3. Если остались файлы — запрашиваем права
        return@withContext requestBatchDeletePermission(filesRequiringPermission)
    }

    private fun requestBatchDeletePermission(uris: List<Uri>): DeleteResult {
        // API 30+ (Android 11+): Красивый групповой диалог
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                DeleteResult.RequiresPermission(pi.intentSender)
            } catch (_: Exception) {
                DeleteResult.Error("Failed to request permission")
            }
        }

        // API 29 (Android 10): RecoverableSecurityException
        // API 26-28 (Android 8-9): Здесь нет системного диалога на удаление чужих файлов через IntentSender.
        // Обычно там просто нужен пермишен WRITE_EXTERNAL_STORAGE в манифесте.
        else {
            val uri = uris.first()
            return try {
                contentResolver.delete(uri, null, null)
                DeleteResult.Error("Unexpected state")
            } catch (securityException: SecurityException) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val recoverable = securityException as? RecoverableSecurityException
                    if (recoverable != null) {
                        return DeleteResult.RequiresPermission(recoverable.userAction.actionIntent.intentSender)
                    }
                }

                // Для Android 8-9 (или если на 10-ке ошибка не Recoverable)
                DeleteResult.Error("Permission denied. Check WRITE_EXTERNAL_STORAGE.")
            } catch (e: Exception) {
                DeleteResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun isFileExists(uri: Uri): Boolean {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}