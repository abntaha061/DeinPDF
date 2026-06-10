package com.example.ui.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun getCleanFileName(context: Context, uri: Uri): String {
    var result = "ملف PDF"
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == "ملف PDF" || result.startsWith("document")) {
        result = uri.lastPathSegment?.substringAfterLast("/") ?: "ملف PDF"
    }
    return result.replace("document:", "").replace(".pdf", "", ignoreCase = true)
}

@Composable
fun ReaderScreen(
    pdfUri: Uri,
    pdfId: Long,
    onBack: () -> Unit,
    onNavigateToVocabulary: () -> Unit
) {
    val context = LocalContext.current
    var tempFilePath by remember { mutableStateOf<String?>(null) }
    var copyError by remember { mutableStateOf<String?>(null) }
    var isPreparingFile by remember { mutableStateOf(true) }

    LaunchedEffect(pdfUri) {
        withContext(Dispatchers.IO) {
            try {
                isPreparingFile = true
                val destinationFile = File(context.cacheDir, "temp_viewer.pdf")
                if (destinationFile.exists()) destinationFile.delete()
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFilePath = destinationFile.absolutePath
                copyError = null
            } catch (e: Exception) {
                copyError = e.localizedMessage
            } finally {
                isPreparingFile = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
    ) {
        when {
            isPreparingFile -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF2196F3))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "جاري تحميل وتجهيز الملف...",
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            copyError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "❌ حدث خطأ أثناء تحميل الملف:\n$copyError",
                        color = Color.Red,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            tempFilePath != null -> {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true

                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = true

                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false

                            settings.useWideViewPort = false
                            settings.loadWithOverviewMode = false

                            setBackgroundColor(0xFF0B0F19.toInt())

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun getPdfBase64(): String {
                                    return try {
                                        val file = File(tempFilePath ?: return "")
                                        if (file.exists()) {
                                            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                                        } else ""
                                    } catch (e: Exception) { "" }
                                }

                                @JavascriptInterface
                                fun onLinkIntercepted(url: String) {
                                    (ctx as? Activity)?.runOnUiThread {
                                        if (url.contains("translate_tts", ignoreCase = true) || url.endsWith(".mp3", ignoreCase = true)) {
                                            Toast.makeText(ctx, "🎧 جاري النطق بالألمانية...", Toast.LENGTH_SHORT).show()
                                            try {
                                                MediaPlayer().apply {
                                                    setAudioAttributes(
                                                        AudioAttributes.Builder()
                                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                                            .build()
                                                    )
                                                    setDataSource(url)
                                                    setOnPreparedListener { mp -> mp.start() }
                                                    setOnErrorListener { mp, _, _ ->
                                                        Toast.makeText(ctx, "فشل النطق", Toast.LENGTH_SHORT).show()
                                                        mp.release()
                                                        true
                                                    }
                                                    setOnCompletionListener { mp -> mp.release() }
                                                    prepareAsync()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        } else if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                ctx.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(ctx, "لا يمكن فتح القاموس", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }, "AndroidPdfBridge")

                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()

                            loadUrl("file:///android_asset/pdfjs/viewer.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
