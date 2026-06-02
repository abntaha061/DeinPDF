package com.example.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AppBiometricManager(private val context: Context) {

    // ===== Check availability =====
    fun isBiometricAvailable(): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    fun getBiometricStatus(): String {
        return try {
            val manager = BiometricManager.from(context)
            when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> "متاح"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "لا يوجد جهاز بصمة"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "الجهاز غير متاح"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "لم تُسجَّل بصمة"
                else -> "غير متاح"
            }
        } catch (e: Exception) {
            "غير متاح"
        }
    }

    // ===== Show biometric prompt =====
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "قفل التطبيق",
        subtitle: String = "أثبت هويتك للمتابعة",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    activity.runOnUiThread { onSuccess() }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    activity.runOnUiThread { onFailure(errString.toString()) }
                }
                override fun onAuthenticationFailed() {
                    activity.runOnUiThread { onFailure("فشل التحقق من الهوية") }
                }
            }

            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                builder.setNegativeButtonText("إلغاء")
            }

            val promptInfo = builder.build()

            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        } catch (e: Exception) {
            onFailure(e.message ?: "فشل تشغيل مستشعر البصمة")
        }
    }
}
