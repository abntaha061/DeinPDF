package com.example.ui.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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

    // Start background file preparation
    LaunchedEffect(pdfUri) {
        withContext(Dispatchers.IO) {
            try {
                isPreparingFile = true
                val destinationFile = File(context.cacheDir, "temp_viewer.pdf")
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                    destinationFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                tempFilePath = destinationFile.absolutePath
                copyError = null
            } catch (e: Exception) {
                copyError = e.localizedMessage
                e.printStackTrace()
            } finally {
                isPreparingFile = false
            }
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    val fileName = pdfUri.lastPathSegment ?: "ملف PDF"
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "❌ حدث خطأ أثناء تحميل الملف:\n$copyError",
                                color = Color.Red,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Text("العودة للقائمة الرئيسية")
                            }
                        }
                    }
                }
                tempFilePath != null -> {
                    AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                // Enable standard JavaScript and file loading
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                
                                // Enable security bypasses to load file:// PDFs inside assets/viewer.html
                                @Suppress("DEPRECATION")
                                settings.allowFileAccessFromFileURLs = true
                                @Suppress("DEPRECATION")
                                settings.allowUniversalAccessFromFileURLs = true
                                
                                // Support Pinch-to-zoom and disable custom buttons
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                
                                // Fit screens seamlessly
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                
                                setBackgroundColor(0xFF0B0F19.toInt())
                                
                                webChromeClient = android.webkit.WebChromeClient()
                                webViewClient = object : android.webkit.WebViewClient() {
                                    private fun handlePdfUrl(url: String?): Boolean {
                                        if (url == null) return false
                                        
                                        // 1. Audio Link Handling (translate_tts or .mp3)
                                        if (url.contains("translate_tts", ignoreCase = true) || url.endsWith(".mp3", ignoreCase = true)) {
                                            Toast.makeText(ctx, "جاري تشغيل الصوت...", Toast.LENGTH_SHORT).show()
                                            try {
                                                MediaPlayer().apply {
                                                    setAudioAttributes(
                                                        AudioAttributes.Builder()
                                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                                            .build()
                                                    )
                                                    setDataSource(url)
                                                    setOnPreparedListener { mp ->
                                                        mp.start()
                                                    }
                                                    setOnErrorListener { mp, _, _ ->
                                                        Toast.makeText(ctx, "فشل في تشغيل الصوت", Toast.LENGTH_SHORT).show()
                                                        mp.release()
                                                        true
                                                    }
                                                    setOnCompletionListener { mp ->
                                                        mp.release()
                                                    }
                                                    prepareAsync()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(ctx, "خطأ في تشغيل الصوت: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                            return true
                                        }
                                        
                                        // 2. Web/Dictionary Link Handling
                                        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                ctx.startActivity(intent)
                                                Toast.makeText(ctx, "جاري فتح الرابط في المتصفح...", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(ctx, "لا يمكن فتح الرابط: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                            return true
                                        }
                                        
                                        return false
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: android.webkit.WebView?,
                                        request: android.webkit.WebResourceRequest?
                                    ): Boolean {
                                        return handlePdfUrl(request?.url?.toString())
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(
                                        view: android.webkit.WebView?,
                                        url: String?
                                    ): Boolean {
                                        return handlePdfUrl(url)
                                    }
                                }
                                
                                // Load HTML passing temp file path as the 'file' query parameter
                                val loadUrl = "file:///android_asset/pdfjs/viewer.html?file=file://$tempFilePath"
                                loadUrl(loadUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
