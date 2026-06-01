package com.example.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScannedPdf(
    val name: String,
    val path: String,
    val uri: Uri,
    val size: Long,
    val dateModified: Long
)

class FileManager(private val context: Context) {

    // ===== Scan device for all PDF files =====
    suspend fun scanAllPdfs(): List<ScannedPdf> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScannedPdf>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val path = cursor.getString(dataCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Files.getContentUri("external"),
                        id.toString()
                    )
                    results.add(ScannedPdf(name, path, uri, size, date * 1000))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: scan filesystem directly
            results.addAll(scanFilesystem())
        }
        results
    }

    // ===== Fallback: scan common directories =====
    private fun scanFilesystem(): List<ScannedPdf> {
        val results = mutableListOf<ScannedPdf>()
        val dirsToScan = listOf(
            Environment.getExternalStorageDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents"),
            context.getExternalFilesDir(null)
        ).filterNotNull()

        dirsToScan.forEach { dir ->
            if (dir.exists()) {
                scanDir(dir, results, depth = 0)
            }
        }
        return results
    }

    private fun scanDir(dir: File, results: MutableList<ScannedPdf>, depth: Int) {
        if (depth > 5) return // max recursion depth
        try {
            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory -> scanDir(file, results, depth + 1)
                    file.name.lowercase().endsWith(".pdf") -> {
                        results.add(
                            ScannedPdf(
                                name = file.name,
                                path = file.absolutePath,
                                uri = Uri.fromFile(file),
                                size = file.length(),
                                dateModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Permission denied for some directories — skip silently
        }
    }

    // ===== Get file info =====
    fun getFileInfo(uri: Uri): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.columnNames.forEachIndexed { i, name ->
                        runCatching { info[name] = cursor.getString(i) ?: "" }
                    }
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return info
    }

    // ===== Check if URI is accessible =====
    fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) { false }
    }

    // ===== Format file size =====
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
        else -> "${"%.2f".format(bytes / 1024.0 / 1024.0 / 1024.0)} GB"
    }

    // ===== Get MIME type =====
    fun getMimeType(uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/pdf"
    }

    // ===== App output directory =====
    fun getAppOutputDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "PDFReader"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
