package com.example.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatus(
    val name: String,
    val displayName: String,
    val isGranted: Boolean,
    val isRequired: Boolean,
    val settingsAction: String
)

class PermissionManager(private val context: Context) {

    // ===== Check storage permission =====
    fun hasStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    // ===== Check write permission =====
    fun hasWritePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> true // Scoped storage
            else -> ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ===== Check notification permission =====
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ===== Get all permissions status =====
    fun getAllPermissionsStatus(): List<PermissionStatus> {
        return listOf(
            PermissionStatus(
                name = "storage",
                displayName = "الوصول للملفات",
                isGranted = hasStoragePermission(),
                isRequired = true,
                settingsAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ),
            PermissionStatus(
                name = "write",
                displayName = "كتابة الملفات",
                isGranted = hasWritePermission(),
                isRequired = false,
                settingsAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ),
            PermissionStatus(
                name = "notification",
                displayName = "الإشعارات (TTS)",
                isGranted = hasNotificationPermission(),
                isRequired = false,
                settingsAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )
        )
    }

    // ===== Build intent to open app settings =====
    fun buildAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ===== Build intent for all files access (Android 11+) =====
    fun buildAllFilesAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } else {
            buildAppSettingsIntent()
        }
    }

    // ===== Required permissions list by SDK =====
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
}
