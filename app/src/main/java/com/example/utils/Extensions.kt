package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ===== Context extensions =====
fun Context.showToast(message: String, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Context.getFileName(uri: Uri): String {
    return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: uri.lastPathSegment ?: "unknown.pdf"
}

fun Context.getFileSize(uri: Uri): Long {
    return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
    } ?: 0L
}

// ===== String extensions =====
fun String.removeExtension(): String = if (contains('.')) substringBeforeLast('.') else this

fun String.isPdf(): Boolean = lowercase().endsWith(".pdf")

fun String.isArabic(): Boolean = any { it in '\u0600'..'\u06FF' }

fun String.isGerman(): Boolean = contains(Regex("[äöüÄÖÜß]"))

fun String.truncate(maxLen: Int): String =
    if (length <= maxLen) this else "${take(maxLen)}..."

// ===== Long (time) extensions =====
fun Long.toReadableDate(locale: Locale = Locale.forLanguageTag("ar")): String {
    val fmt = SimpleDateFormat("dd MMM yyyy", locale)
    return fmt.format(Date(this))
}

fun Long.toReadableDateTime(locale: Locale = Locale.forLanguageTag("ar")): String {
    val fmt = SimpleDateFormat("dd MMM yyyy - hh:mm a", locale)
    return fmt.format(Date(this))
}

fun Long.toReadableDuration(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}س ${minutes % 60}د"
        minutes > 0 -> "${minutes}د"
        else -> "${seconds}ث"
    }
}

fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "منذ ${minutes}د"
        hours < 24 -> "منذ ${hours}س"
        days < 7 -> "منذ ${days} أيام"
        else -> toReadableDate()
    }
}

// ===== Float extensions =====
fun Float.toPercent(): String = "${(this * 100).toInt()}%"

fun Float.roundTo(decimals: Int): Float {
    var multiplier = 1f
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

// ===== Color extensions =====
fun Color.toHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

fun String.toComposeColor(): Color {
    return try { Color(android.graphics.Color.parseColor(this)) }
    catch (e: Exception) { Color(0xFF3B82F6) }
}

// ===== File extensions =====
fun File.formatSize(): String {
    val bytes = length()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    }
}

// ===== Bitmap extensions =====
fun Bitmap.toFile(file: File, quality: Int = 90): Boolean {
    return try {
        file.outputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        true
    } catch (e: Exception) { false }
}

// ===== List extensions =====
fun <T> List<T>.safeGet(index: Int): T? = if (index in indices) this[index] else null

fun <T> MutableList<T>.addIfNotExists(item: T) {
    if (!contains(item)) add(item)
}
