package com.example.ui.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // جلب ألوان الثيم الديناميكية
    val bgColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBgColor = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val window = (context as? Activity)?.window
    LaunchedEffect(Unit) {
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            val controller = WindowInsetsControllerCompat(it, it.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                val controller = WindowInsetsControllerCompat(it, it.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

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
            .background(bgColor) // استخدام اللون الديناميكي بدلاً من الأسود الثابت
    ) {
        when {
            isPreparingFile -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "جاري تحميل وتجهيز الملف...",
                            color = onBgColor,
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
                        color = MaterialTheme.colorScheme.error,
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
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true

                            // جعل خلفية متصفح الـ PDF تتغير حسب الوضع الفاتح/الداكن
                            setBackgroundColor(bgColor.toArgb())

                            addJavascriptInterface(object {
                                
                                @JavascriptInterface
                                fun getPdfBase64(): String {
                                    // قمنا بتعطيل الـ Base64 لأنه يسبب انهيار للكتب الكبيرة (OutOfMemory)
                                    return "" 
                                }

                                @JavascriptInterface
                                fun getPdfUrl(): String {
                                    // هذه الدالة الجديدة السريعة والآمنة جداً لجلب مسار الملف مباشرة
                                    return "file://" + (tempFilePath ?: "")
                                }

                                @JavascriptInterface
                                fun onTotalPages(total: Int) {
                                    (ctx as? Activity)?.runOnUiThread {
                                        totalPages = total
                                    }
                                }

                                @JavascriptInterface
                                fun onPageChanged(page: Int) {
                                    (ctx as? Activity)?.runOnUiThread {
                                        currentPage = page
                                    }
                                }

                                @JavascriptInterface
                                fun onLinkIntercepted(url: String) {
                                    (ctx as? Activity)?.runOnUiThread {
                                        if (url.contains("translate_tts", ignoreCase = true) || url.endsWith(".mp3", ignoreCase = true)) {
                                            // تم حذف Toast "جاري النطق..."
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
                                            // تم حذف Toast "جاري فتح Arabdict..."
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                ctx.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(ctx, "عذراً، لا يمكن فتح الرابط", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }, "AndroidPdfBridge")

                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            webViewRef = this
                            loadUrl("file:///android_asset/pdfjs/viewer.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Bottom Bar
                if (totalPages > 1) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(surfaceColor.copy(alpha = 0.95f)) // لون شريط سفلي متجاوب
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$currentPage / $totalPages",
                                color = onSurfaceColor,
                                fontSize = 14.sp
                            )
                        }

                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { },
                            onValueChangeFinished = { },
                            valueRange = 1f..totalPages.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor,
                                inactiveTrackColor = onSurfaceColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
