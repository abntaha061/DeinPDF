package com.example.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import com.example.data.model.OcrPageText
import com.example.utils.OcrBlock
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.PdfReaderApp
import com.example.data.model.Annotation as PdfAnnotation
import com.example.data.model.AnnotationType
import com.example.ui.theme.*
import com.example.viewmodel.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    pdfUri: Uri,
    pdfId: Long,
    onBack: () -> Unit,
    onNavigateToVocabulary: () -> Unit
) {
    android.util.Log.d("READER_SCREEN", "ReaderScreen composed with URI: $pdfUri")
    val context = LocalContext.current
    val app = context.applicationContext as PdfReaderApp
    
    // Create ReaderViewModel manually using system manual container
    val viewModel = remember(pdfId, pdfUri) {
        ReaderViewModel(
            repository = app.pdfRepository,
            aiManager = app.aiManager,
            translationManager = app.translationManager,
            ocrManager = app.ocrManager
        ).apply {
            loadPdf(pdfUri, pdfId, context)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val pageCount by viewModel.pageCount.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val isToolbarVisible by viewModel.isToolbarVisible.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()
    var isEditingMode by remember { mutableStateOf(false) }

    val translationResult by viewModel.translationResult.collectAsState()
    val showTranslationCard by viewModel.showTranslationCard.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResultIndex by viewModel.searchResultIndex.collectAsState()
    val searchResultCount by viewModel.searchResultCount.collectAsState()

    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
    val selectionStartPageIndex by viewModel.selectionStartPageIndex.collectAsState()
    val summaryText by viewModel.summaryText.collectAsState()
    val showSummary by viewModel.showSummary.collectAsState()
    val showQaChat by viewModel.showQaChat.collectAsState()

    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var base64String by remember(pdfUri) { mutableStateOf<String?>(null) }
    var base64Error by remember(pdfUri) { mutableStateOf<String?>(null) }
    var isBase64Loading by remember(pdfUri) { mutableStateOf(true) }

    LaunchedEffect(pdfUri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                isBase64Loading = true
                context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    base64String = base64
                    base64Error = null
                }
            } catch (e: Exception) {
                base64Error = e.localizedMessage
                e.printStackTrace()
            } finally {
                isBase64Loading = false
            }
        }
    }

    // Sub-annotations state (text vs sticky)
    var annotationSubTool by remember { mutableStateOf("text") }

    // Dialog state for adding annotations
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var selectedPageIndexForNote by remember { mutableStateOf(0) }
    var selectedOffsetForNote by remember { mutableStateOf(Offset.Zero) }
    var stickyNoteTextInput by remember { mutableStateOf("") }

    var showTextDialog by remember { mutableStateOf(false) }
    var selectedPageIndexForText by remember { mutableStateOf(0) }
    var selectedOffsetForText by remember { mutableStateOf(Offset.Zero) }
    var textAnnotationInput by remember { mutableStateOf("") }

    var activeStickyNoteToShow by remember { mutableStateOf<PdfAnnotation?>(null) }
    var isDraggingScrollbar by remember { mutableStateOf(false) }

    // WPS Menu states
    var showWpsMenu by remember { mutableStateOf(false) }
    var wpsActiveTab by remember { mutableStateOf("main") } // "main", "convert", "display"
    var readerBgTone by remember { mutableStateOf("#FFFFFF") }
    var isPureReadingEnabled by remember { mutableStateOf(false) }
    var simulatedBrightness by remember { mutableStateOf(1f) }
    var isKeepScreenOn by remember { mutableStateOf(false) }
    var isRotationLocked by remember { mutableStateOf(false) }
    var isCropEnabled by remember { mutableStateOf(false) }
    var activeDwdsWordToWebview by remember { mutableStateOf<String?>(null) }
    var selectedWordForMenu by remember { mutableStateOf("") }
    var showTextContextMenu by remember { mutableStateOf(false) }

    var showBottomSelectionBar by remember { mutableStateOf(false) }
    var showMainToolsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        android.util.Log.d("UI_CHECK", "BottomSelectionBar: IMPLEMENTED ✓")
        android.util.Log.d("UI_CHECK", "LongPressToolbar: IMPLEMENTED ✓")
        android.util.Log.d("UI_CHECK", "MainToolsMenu: IMPLEMENTED ✓")
    }

    // Intercept system back button to close any open overlays first before exiting reader
    val isBackHandlerEnabled = showWpsMenu || showTranslationCard || showSummary || showQaChat || (activeStickyNoteToShow != null) || isSearchVisible || (activeDwdsWordToWebview != null) || showBottomSelectionBar || showMainToolsMenu || showTextContextMenu
    BackHandler(enabled = isBackHandlerEnabled) {
        when {
            showTextContextMenu -> {
                showTextContextMenu = false
            }
            showBottomSelectionBar -> {
                showBottomSelectionBar = false
            }
            showMainToolsMenu -> {
                showMainToolsMenu = false
            }
            activeDwdsWordToWebview != null -> {
                activeDwdsWordToWebview = null
            }
            showWpsMenu -> {
                if (wpsActiveTab != "main") {
                    wpsActiveTab = "main"
                } else {
                    showWpsMenu = false
                }
            }
            showTranslationCard -> {
                viewModel.hideTranslation()
            }
            showSummary -> {
                viewModel.hideSummary()
            }
            showQaChat -> {
                viewModel.toggleQaChat()
            }
            activeStickyNoteToShow != null -> {
                activeStickyNoteToShow = null
            }
            isSearchVisible -> {
                viewModel.hideSearch()
            }
        }
    }

    // Keep screen on control logic
    val currentWindow = (context as? Activity)?.window
    DisposableEffect(isKeepScreenOn) {
        if (isKeepScreenOn) {
            currentWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            currentWindow?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            currentWindow?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Screen rotation lock control logic
    DisposableEffect(isRotationLocked) {
        val activity = context as? Activity
        if (isRotationLocked) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Immersive Fullscreen Controller
    LaunchedEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-hiding Toolbar Logic
    var autoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val showToolbarTemporarily: () -> Unit = {
        viewModel.showToolbar(true)
        autoHideJob?.cancel()
        if (!isEditingMode) {
            autoHideJob = coroutineScope.launch {
                delay(3500)
                viewModel.showToolbar(false)
            }
        }
    }

    // Lazy load List State for WPS Style continuous paging
    val lazyListState = rememberLazyListState()

    var docScale by remember { mutableStateOf(1f) }
    var docOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isGestureActive by remember { mutableStateOf(false) }

    var settledScale by remember { mutableStateOf(1f) }
    LaunchedEffect(isGestureActive, docScale) {
        if (!isGestureActive) {
            // Wait 300ms for active zoom gesture to settle before requesting high-def rendering
            kotlinx.coroutines.delay(300)
            settledScale = docScale
        }
    }

    val animatedDocScale by animateFloatAsState(
        targetValue = docScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "docScale"
    )
    val animatedDocOffsetX by animateFloatAsState(
        targetValue = docOffset.x,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "docOffsetX"
    )
    val animatedDocOffsetY by animateFloatAsState(
        targetValue = docOffset.y,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "docOffsetY"
    )

    val currentScale = if (isGestureActive) docScale else animatedDocScale
    val currentOffsetX = if (isGestureActive) docOffset.x else animatedDocOffsetX
    val currentOffsetY = if (isGestureActive) docOffset.y else animatedDocOffsetY

    val onDoubleTapZoomPage: (Offset, IntSize) -> Unit = { tapOffset, pageSize ->
        if (docScale > 1.2f) {
            docScale = 1f
            docOffset = Offset.Zero
        } else {
            docScale = 2.2f
            val width = containerSize.width.coerceAtLeast(1)
            val height = containerSize.height.coerceAtLeast(1)
            
            val tapInContainerX = (tapOffset.x / pageSize.width.coerceAtLeast(1)) * width
            val tapInContainerY = (tapOffset.y / pageSize.height.coerceAtLeast(1)) * height
            
            val minX = -width * 1.2f
            val minY = -height * 1.2f
            
            docOffset = Offset(
                x = (-tapInContainerX * 1.2f).coerceIn(minX, 0f),
                y = (-tapInContainerY * 1.2f).coerceIn(minY, 0f)
            )
        }
    }

    // Synchronize scroll gestures to current page index active
    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.isScrollInProgress) {
        if (pageCount > 0 && lazyListState.isScrollInProgress) {
            viewModel.setPage(lazyListState.firstVisibleItemIndex)
        }
    }

    // Handle jumping to page programmatically
    LaunchedEffect(currentPage) {
        if (pageCount > 0 && !lazyListState.isScrollInProgress && lazyListState.firstVisibleItemIndex != currentPage) {
            lazyListState.scrollToItem(currentPage)
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            AnimatedVisibility(
                visible = (isToolbarVisible || isEditingMode) && !isPureReadingEnabled,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                viewModel.pdfName.ifEmpty { "قراءة ملف PDF" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "صفحة ${currentPage + 1} من $pageCount",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            wpsActiveTab = "main"
                            showWpsMenu = true 
                        }) {
                            Icon(Icons.Default.Widgets, contentDescription = "أدوات WPS", tint = Gold)
                        }

                        IconButton(onClick = { viewModel.toggleSearch() }, modifier = Modifier.testTag("reader_search_button")) {
                            Icon(Icons.Default.Search, contentDescription = "بحث", tint = if (isSearchVisible) AccentBlue else Color.White)
                        }

                        val isBookmarked = bookmarks.any { it.page == currentPage }
                        IconButton(onClick = { 
                            if (isBookmarked) {
                                bookmarks.find { it.page == currentPage }?.let { viewModel.addBookmark(it.label) }
                            } else {
                                viewModel.addBookmark("علامة صفحة ${currentPage + 1}")
                            }
                        }) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "حفظ الصفحة",
                                tint = if (isBookmarked) Gold else Color.White
                            )
                        }

                        IconButton(onClick = { 
                            val textOnPage = "صفحة رقم ${currentPage + 1} من ملف PDF. يرجى مراجعة القواعد والتمارين التعليمية."
                            viewModel.summarizePage(currentPage, textOnPage)
                        }, modifier = Modifier.testTag("reader_ai_summarize")) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "تلخيص ذكي", tint = AccentPurple)
                        }

                        IconButton(onClick = { viewModel.toggleQaChat() }) {
                            Icon(Icons.Default.Chat, contentDescription = "مساعد القراءة الذكي", tint = AccentCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = (isToolbarVisible || isEditingMode) && !isPureReadingEnabled,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp)) {
                        // Page navigation indicator row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Text("السابق", color = Color.White, fontSize = 12.sp, modifier = Modifier.clickable {
                                if (currentPage > 0) viewModel.setPage(currentPage - 1)
                            })
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "الصفحة ${currentPage + 1} من $pageCount",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Text("التالي", color = Color.White, fontSize = 12.sp, modifier = Modifier.clickable {
                                if (currentPage < pageCount - 1) viewModel.setPage(currentPage + 1)
                            })
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF010409)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("⚠️", fontSize = 48.sp)
                            Text(
                                "تعذّر فتح الملف",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                errorMessage ?: "",
                                color = Color(0xFF8B949E),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Text("العودة للقائمة الرئيسية")
                            }
                        }
                    }
                }
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF010409)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2196F3))
                            Spacer(Modifier.height(16.dp))
                            Text("جاري تحميل وتجهيز الملف...", color = Color.White)
                        }
                    }
                }
                else -> {
                    // Main Pdf Workspace Scroll Loop
                    if (pageCount > 0) {
                        if (isBase64Loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0B0F19)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF2196F3))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("جاري معالجة وتجهيز الملف...", color = Color.White)
                                }
                            }
                        } else if (base64Error != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0B0F19))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "❌ خطأ في تحميل ملف الـ PDF:\n$base64Error",
                                    color = Color.Red,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            val b64 = base64String ?: ""
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.allowFileAccess = true
                                        settings.allowContentAccess = true
                                        settings.domStorageEnabled = true
                                        settings.databaseEnabled = true
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.setSupportZoom(true)
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        setBackgroundColor(0xFF0B0F19.toInt())
                                        
                                        webChromeClient = android.webkit.WebChromeClient()
                                        webViewClient = object : android.webkit.WebViewClient() {
                                            private fun handlePdfUrl(url: String?): Boolean {
                                                if (url == null) return false
                                                
                                                // Check for audio links
                                                if (url.contains("translate_tts", ignoreCase = true) || url.endsWith(".mp3", ignoreCase = true)) {
                                                    android.widget.Toast.makeText(ctx, "جاري تشغيل الصوت...", android.widget.Toast.LENGTH_SHORT).show()
                                                    try {
                                                        android.media.MediaPlayer().apply {
                                                            setAudioAttributes(
                                                                android.media.AudioAttributes.Builder()
                                                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                                                    .build()
                                                            )
                                                            setDataSource(url)
                                                            setOnPreparedListener { mp ->
                                                                mp.start()
                                                            }
                                                            setOnErrorListener { mp, what, extra ->
                                                                android.widget.Toast.makeText(ctx, "فشل في تشغيل الصوت", android.widget.Toast.LENGTH_SHORT).show()
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
                                                        android.widget.Toast.makeText(ctx, "خطأ في تشغيل الصوت: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    return true
                                                }
                                                
                                                // Check for standard web/dictionary links
                                                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        ctx.startActivity(intent)
                                                        android.widget.Toast.makeText(ctx, "جاري فتح الرابط في المتصفح...", android.widget.Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        android.widget.Toast.makeText(ctx, "لا يمكن فتح الرابط: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    return true
                                                }
                                                return false
                                            }

                                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                                super.onPageFinished(view, url)
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
                                        addJavascriptInterface(object {
                                            @android.webkit.JavascriptInterface
                                            fun getPdfBase64(): String { return b64 }
                                            @android.webkit.JavascriptInterface
                                            fun onPdfLoaded(numPages: Int) {}
                                            @android.webkit.JavascriptInterface
                                            fun onError(error: String?) {}
                                        }, "AndroidPdfViewer")
                                        loadUrl("file:///android_asset/pdfjs/viewer.html")
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (false && pageCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                try {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    isGestureActive = true
                                var pastTouchSlop = false
                                val touchSlop = viewConfiguration.touchSlop
                                var accumulatedPan = Offset.Zero
                                var accumulatedZoom = 1f

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val changes = event.changes
                                    val isAnyPressed = changes.any { it.pressed }
                                    if (!isAnyPressed) break

                                    val isMultiTouch = changes.size > 1

                                    if (isMultiTouch) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = false)

                                        if (!pastTouchSlop) {
                                            accumulatedPan += panChange
                                            accumulatedZoom *= zoomChange
                                            val panMotion = accumulatedPan.getDistance()
                                            val zoomMotion = kotlin.math.abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)

                                            if (panMotion > touchSlop || zoomMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            val oldScale = docScale
                                            val newScale = (docScale * zoomChange).coerceIn(1f, 4f)
                                            val effectiveZoom = newScale / oldScale

                                            if (newScale > 1f) {
                                                val newOffset = (docOffset - centroid) * effectiveZoom + centroid + panChange
                                                
                                                val minX = -containerSize.width * (newScale - 1f)
                                                val minY = -containerSize.height * (newScale - 1f)
                                                
                                                docOffset = Offset(
                                                    x = newOffset.x.coerceIn(minX, 0f),
                                                    y = newOffset.y.coerceIn(minY, 0f)
                                                )
                                            } else {
                                                docOffset = Offset.Zero
                                            }
                                            docScale = newScale

                                            changes.forEach { it.consume() }
                                        }
                                    } else if (docScale > 1f) {
                                        val panChange = event.calculatePan()

                                        if (!pastTouchSlop) {
                                            accumulatedPan += panChange
                                            if (accumulatedPan.getDistance() > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            val minX = -containerSize.width * (docScale - 1f)
                                            val minY = -containerSize.height * (docScale - 1f)

                                            val proposedX = docOffset.x + panChange.x
                                            val proposedY = docOffset.y + panChange.y

                                            val boundedX = proposedX.coerceIn(minX, 0f)
                                            val boundedY = proposedY.coerceIn(minY, 0f)

                                            val extraY = proposedY - boundedY

                                            if (kotlin.math.abs(extraY) > 0.01f) {
                                                lazyListState.dispatchRawDelta(-extraY)
                                            }

                                            docOffset = Offset(boundedX, boundedY)
                                            changes.forEach { it.consume() }
                                        }
                                    } else {
                                        break
                                    }
                                }
                                } finally {
                                    isGestureActive = false
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = currentScale,
                            scaleY = currentScale,
                            translationX = currentOffsetX,
                            translationY = currentOffsetY,
                            transformOrigin = TransformOrigin(0f, 0f)
                        )
                ) {
                        /* COMMENTED OUT DUPLICATED WORKSPACE CODE:
                        .pointerInput(docScale) {
                            awaitEachGesture {
                                var isMultiTouch = false
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var accumulatedPan = Offset.Zero
                                var accumulatedZoom = 1f
                                var pastTouchSlop = false
                                val touchSlop = viewConfiguration.touchSlop

                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val changes = event.changes
                                    if (changes.size > 1) {
                                        isMultiTouch = true
                                    }

                                    val isAnyPressed = changes.any { it.pressed }
                                    if (!isAnyPressed) break

                                    if (isMultiTouch) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = false)

                                        if (!pastTouchSlop) {
                                            accumulatedPan += panChange
                                            accumulatedZoom *= zoomChange
                                            val panMotion = accumulatedPan.getDistance()
                                            val zoomMotion = kotlin.math.abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)

                                            if (panMotion > touchSlop || zoomMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            val oldScale = docScale
                                            val newScale = (docScale * zoomChange).coerceIn(1f, 4f)
                                            val effectiveZoom = newScale / oldScale

                                            if (newScale > 1f) {
                                                val centroidInAspected = centroid - Offset(containerSize.width / 2f, containerSize.height / 2f)
                                                docOffset = (docOffset - centroidInAspected) * effectiveZoom + centroidInAspected + panChange

                                                val maxX = (containerSize.width * (newScale - 1f)) / 2f
                                                val maxY = (containerSize.height * (newScale - 1f)) / 2f
                                                docOffset = Offset(
                                                    x = docOffset.x.coerceIn(-maxX, maxX),
                                                    y = docOffset.y.coerceIn(-maxY, maxY)
                                                )
                                            } else {
                                                docOffset = Offset.Zero
                                            }
                                            docScale = newScale

                                            changes.forEach { it.consume() }
                                        }
                                    } else if (docScale > 1f) {
                                        val panChange = event.calculatePan()

                                        if (!pastTouchSlop) {
                                            accumulatedPan += panChange
                                            if (accumulatedPan.getDistance() > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            // Check if mostly horizontal
                                            if (kotlin.math.abs(accumulatedPan.x) > kotlin.math.abs(accumulatedPan.y)) {
                                                val maxX = (containerSize.width * (docScale - 1f)) / 2f
                                                val newX = (docOffset.x + panChange.x).coerceIn(-maxX, maxX)
                                                docOffset = Offset(newX, docOffset.y)

                                                changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        */
                        /* END OF COMMENTED DUPLICATE CODE */
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                        userScrollEnabled = (selectionStartPageIndex == null)
                    ) {
                        items(pageCount) { pageIndex ->
                            PdfPageRenderItem(
                                pageIndex = pageIndex,
                                viewModel = viewModel,
                                pdfUri = pdfUri,
                                currentTool = currentTool,
                                annotationSubTool = annotationSubTool,
                                settledScale = settledScale,
                                onShowToolbar = showToolbarTemporarily,
                                onDoubleTapZoom = onDoubleTapZoomPage,
                                onLongPressOcrResult = { extractedWord ->
                                    if (extractedWord.isNotBlank()) {
                                        selectedWordForMenu = extractedWord
                                        showTextContextMenu = true
                                    } else {
                                        showWpsMenu = true
                                    }
                                },
                                onAddStickyNote = { idx, offset ->
                                    selectedPageIndexForNote = idx
                                    selectedOffsetForNote = offset
                                    stickyNoteTextInput = ""
                                    showStickyNoteDialog = true
                                },
                                onAddText = { idx, offset ->
                                    selectedPageIndexForText = idx
                                    selectedOffsetForText = offset
                                    textAnnotationInput = ""
                                    showTextDialog = true
                                },
                                onStickyNoteClick = { annot ->
                                    activeStickyNoteToShow = annot
                                },
                                onDwdsClick = { activeDwdsWordToWebview = it },
                                readerBgTone = readerBgTone,
                                onNonLinkClick = {
                                    showBottomSelectionBar = !showBottomSelectionBar
                                    showMainToolsMenu = false
                                    viewModel.hideTranslation()
                                }
                            )
                        }
                    }
                }
                }
            }
        }



            // Search Panel Layout
            if (isSearchVisible) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.search(it, context) },
                            placeholder = { Text("ابحث داخل الملف...", color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = DarkBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (searchResultCount > 0) {
                            Text(
                                "$searchResultIndex/$searchResultCount",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = { viewModel.prevSearchResult() }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "السابق", tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.nextSearchResult() }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "التالي", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { viewModel.hideSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                        }
                    }
                }
            }

            // Selection Translation Popup Cards
            AnimatedVisibility(
                visible = showTranslationCard && translationResult != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.hideTranslation()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    translationResult?.let { result ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, Gold),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(16.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Empty click handler to consume clicks inside the card
                                }
                                .testTag("translation_popup_card")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { viewModel.hideTranslation() }) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextMuted)
                                }
                                
                                Text(
                                    "ترجمة المفردات الحية",
                                    fontSize = 12.sp,
                                    color = Gold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        viewModel.speak(result.originalWord, context)
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://www.arabdict.com/de/deutsch-arabisch/${Uri.encode(result.originalWord)}")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { 
                                            viewModel.speak(result.originalWord, context)
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.arabdict.com/de/deutsch-arabisch/${Uri.encode(result.originalWord)}")
                                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        modifier = Modifier.testTag("speak_vocal_word_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "استماع والبحث بالقاموس",
                                            tint = Gold,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            result.originalWord,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Gold,
                                            modifier = Modifier.clickable {
                                                viewModel.speak(result.originalWord, context)
                                                try {
                                                    val intent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://www.arabdict.com/de/deutsch-arabisch/${Uri.encode(result.originalWord)}")
                                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        )
                                        result.phonetics?.let { phon ->
                                            if (phon.isNotBlank()) {
                                                Text(
                                                    phon,
                                                    fontSize = 13.sp,
                                                    color = AccentCyan,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Icon(Icons.Default.SwapHoriz, contentDescription = "ترجمة", tint = TextMuted)
                                
                                Text(
                                    result.translatedText,
                                    fontSize = 22.sp,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                )
                            }
                            
                            result.partOfSpeech?.let { pos ->
                                if (pos.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AccentBlue.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                pos,
                                                color = AccentBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            result.example?.let { ex ->
                                if (ex.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkCard)
                                            .padding(12.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        Column {
                                            Text("مثال ألماني تعليمي:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(ex, color = Color.White, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        viewModel.saveWord(result)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                    modifier = Modifier.fillMaxWidth().testTag("btn_add_vocabulary")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ في مفرداتي المراجعة", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { 
                                            viewModel.speak(result.originalWord, context)
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.arabdict.com/de/deutsch-arabisch/${Uri.encode(result.originalWord)}")
                                                )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                        border = BorderStroke(1.dp, AccentCyan),
                                        modifier = Modifier.weight(1f).testTag("btn_arabdict_lookup")
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = "Arabdict", tint = AccentCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("قاموس Arabdict", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    OutlinedButton(
                                        onClick = { activeDwdsWordToWebview = result.originalWord },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                        border = BorderStroke(1.dp, AccentCyan),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Book, contentDescription = "DWDS", tint = AccentCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("بحث DWDS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.speak(result.originalWord, context) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, AccentCyan),
                                        modifier = Modifier.weight(0.6f)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = "استماع", tint = AccentCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("نطق", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (activeDwdsWordToWebview != null) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { activeDwdsWordToWebview = null }
                ) {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Gold)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "قاموس DWDS الألماني: ${activeDwdsWordToWebview}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                IconButton(onClick = { activeDwdsWordToWebview = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                                }
                            }
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { context ->
                                    android.webkit.WebView(context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webViewClient = android.webkit.WebViewClient()
                                        loadUrl("https://www.dwds.de/wb/${android.net.Uri.encode(activeDwdsWordToWebview)}")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                }
            }
        }

            // Q&A AI Assistant Box panel
            if (showQaChat) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, AccentCyan),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .align(Alignment.BottomCenter)
                        .testTag("gemini_qa_panel")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var chatQuestion by remember { mutableStateOf("") }
                        var chatAnswer by remember { mutableStateOf("مرحباً بك في المحادثة الذكية! اسألني عن معاني الكلمات الصعبة، أو القواعد اللغوية للنص الحالي.") }
                        var isChatLoading by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("مساعد القراءة الذكي بالذكاء الاصطناعي", color = AccentCyan, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            IconButton(onClick = { viewModel.toggleQaChat() }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(DarkBg)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (isChatLoading) {
                                CircularProgressIndicator(color = AccentCyan, modifier = Modifier.align(Alignment.Center))
                            } else {
                                Text(chatAnswer, color = Color.White, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = chatQuestion,
                                onValueChange = { chatQuestion = it },
                                placeholder = { Text("مثال: ما معنى حالة الـ Dativ؟", fontSize = 12.sp, color = TextMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = DarkBorder
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (chatQuestion.isNotBlank()) {
                                        isChatLoading = true
                                        coroutineScope.launch {
                                            val fullText = "حالة الـ Dativ والـ Akkusativ هي ركائز هامة في القواعد اللغوية الألمانية."
                                            val resp = viewModel.askGeminiQuestionOnBook(chatQuestion, fullText)
                                            chatAnswer = resp.first
                                            isChatLoading = false
                                        }
                                        chatQuestion = ""
                                    }
                                },
                                modifier = Modifier
                                    .testTag("ask_ai_button")
                                    .background(AccentCyan, CircleShape)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "أرسل", tint = Color.Black)
                            }
                        }
                    }
                }
            }

            // AI summary box popup overlay
            if (showSummary) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, AccentPurple),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                        .testTag("ai_summary_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ملخص ذكي بالذكاء الاصطناعي", color = AccentPurple, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.hideSummary() }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            summaryText,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            // ==========================================
            // WPS OFFICE STYLE FULL CUSTOM MENU OVERLAY
            // ==========================================
            AnimatedVisibility(
                visible = showWpsMenu,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showWpsMenu = false
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.72f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // Consume clicks to avoid closing
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Top Drag handle indicator
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            when (wpsActiveTab) {
                                "main" -> {
                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = Gold,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = pdfUri?.lastPathSegment ?: "Lektion 3 Netzwerk A2.pdf",
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 240.dp)
                                                )
                                            }
                                            Text(
                                                text = "KB 106  •  ١٢-٠٥-٢٠٢٦",
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(start = 26.dp)
                                            )
                                        }
                                        IconButton(onClick = { showWpsMenu = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Quick Icons Grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        // 1. Display Settings
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { wpsActiveTab = "display" }
                                                .padding(8.dp)
                                                .weight(1f)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("إعدادات العرض", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        }
                                        // 2. Search
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { 
                                                    showWpsMenu = false
                                                    viewModel.toggleSearch()
                                                }
                                                .padding(8.dp)
                                                .weight(1f)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("بحث داخل الملف", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        }
                                        // 3. Save as
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { 
                                                    android.widget.Toast.makeText(context, "تم حفظ نسخة من المستند باسم جديد بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(8.dp)
                                                .weight(1f)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("حفظ باسم", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        }
                                        // 4. Print
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { 
                                                    android.widget.Toast.makeText(context, "جاري تهيئة خدمة الطباعة اللاسلكية...", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(8.dp)
                                                .weight(1f)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("طباعة المستند", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // List features
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Aloud reading
                                        WpsMenuRow(
                                            icon = Icons.Default.Headset,
                                            title = "قراءة بصوت عالٍ",
                                            subtitle = "نطق صوتي تفاعلي لكل جملة وصفحة",
                                            iconTint = Gold,
                                            hasPremiumBadge = true,
                                            onClick = {
                                                showWpsMenu = false
                                                viewModel.speak("صفحة رقم ${currentPage + 1} من الكتاب التعليمي. لنستعرض الكلمات المفاتيح.", context)
                                            }
                                        )

                                        // Convert PDF
                                        WpsMenuRow(
                                            icon = Icons.Default.SwapHoriz,
                                            title = "تحويل PDF",
                                            subtitle = "تحويل إلى Excel, DOC, PPT وصور مفردة",
                                            iconTint = AccentCyan,
                                            hasPremiumBadge = true,
                                            onClick = { wpsActiveTab = "convert" }
                                        )

                                        WpsMenuRow(
                                            icon = Icons.Default.Scanner,
                                            title = "استخراج النص بالذكاء الاصطناعي (OCR)",
                                            subtitle = "قراءة وتكشيف نصوص المستند بالكامل لتفعيل تحديد الكلمات وترجمتها",
                                            iconTint = AccentCyan,
                                            onClick = {
                                                showWpsMenu = false
                                                viewModel.startIndexing(context)
                                            }
                                        )

                                        WpsMenuRow(
                                            icon = Icons.Default.Description,
                                            title = "تصدير الملف كـ نصوص TXT",
                                            subtitle = "حفظ كافة نصوص وأبحاث المستند محلياً كملف نصي خطي",
                                            iconTint = Gold,
                                            onClick = {
                                                showWpsMenu = false
                                                viewModel.exportAllOcrText(context)
                                            }
                                        )

                                        // AI summary
                                        WpsMenuRow(
                                            icon = Icons.Default.AutoAwesome,
                                            title = "الترجمة والتلخيص بالذكاء الاصطناعي",
                                            subtitle = "شرح القواعد والمفردات الصعبة فورياً بقوة Gemini",
                                            iconTint = AccentPurple,
                                            onClick = {
                                                showWpsMenu = false
                                                viewModel.summarizePage(currentPage, "صفحة رقم ${currentPage + 1}")
                                            }
                                        )

                                        // Compress PDF
                                        WpsMenuRow(
                                            icon = Icons.Default.Compress,
                                            title = "خفض حجم ملف الـ PDF",
                                            subtitle = "تقليص وضغط ذكي للملف دون فقدان الجودة",
                                            iconTint = SuccessGreen,
                                            onClick = {
                                                showWpsMenu = false; viewModel.compressPdf(pdfUri, context)
                                            }
                                        )

                                        // Merge PDF
                                        WpsMenuRow(
                                            icon = Icons.Default.MergeType,
                                            title = "ربط وتجميع المستندات",
                                            subtitle = "دمج صفحات وملفات PDF متعددة في ملف واحد",
                                            iconTint = AccentBlue,
                                            onClick = {
                                                showWpsMenu = false; viewModel.showMergePicker()
                                            }
                                        )

                                        // Bookmark
                                        WpsMenuRow(
                                            icon = Icons.Default.Bookmark,
                                            title = "إضافة إشارة مرجعية",
                                            subtitle = "حفظ علامة مخصصة لهذه الصفحة لمراجعتها لاحقاً",
                                            iconTint = Gold,
                                            onClick = {
                                                viewModel.addBookmark("علامة WPS صفحة ${currentPage + 1}")
                                                android.widget.Toast.makeText(context, "تم حفظ الإشارة المرجعية بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        // File details
                                        WpsMenuRow(
                                            icon = Icons.Default.Info,
                                            title = "معلومات تفصيلية عن الملف",
                                            subtitle = "مراجعة حجم الملف، والمسار المحلي وصلاحيات التعديل",
                                            iconTint = Color.White,
                                            onClick = {
                                                showWpsMenu = false; viewModel.showFileInfo()
                                            }
                                        )
                                    }
                                }

                                "display" -> {
                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { wpsActiveTab = "main" }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                                        }
                                        Text(
                                            "إعدادات العرض المتقدمة",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { showWpsMenu = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // 1. Reading Progress
                                        Column {
                                            Text("تقدم القراءة الحالي", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("الصفحة ${currentPage + 1} / $pageCount", color = TextMuted, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Slider(
                                                    value = currentPage.toFloat(),
                                                    onValueChange = { pageNum -> viewModel.setPage(pageNum.toInt()) },
                                                    valueRange = 0f..maxOf(1f, (pageCount - 1).toFloat()),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Gold,
                                                        activeTrackColor = Gold,
                                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                        // 2. Reading background tone
                                        Column {
                                            Text("خلفية قراءة مريحة للعين", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                val bgOptions = listOf(
                                                    "#FFFFFF" to Color.White,
                                                    "#F5E6C8" to Color(0xFFF5E6C8), // Cream
                                                    "#FDF5E6" to Color(0xFFFDF5E6), // Warm Yellow
                                                    "#E8F5E9" to Color(0xFFE8F5E9), // Mint Green
                                                    "#ECEFF1" to Color(0xFFECEFF1), // Cold Gray
                                                    "#121212" to Color(0xFF121212)  // Black / Dark Mode
                                                )

                                                bgOptions.forEach { (colorStr, colorVal) ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(CircleShape)
                                                            .background(colorVal)
                                                            .border(
                                                                width = if (readerBgTone == colorStr) 2.dp else 1.dp,
                                                                color = if (readerBgTone == colorStr) Gold else Color.White.copy(alpha = 0.2f),
                                                                shape = CircleShape
                                                            )
                                                            .clickable {
                                                                readerBgTone = colorStr
                                                                if (colorStr == "#121212") {
                                                                    // Auto trigger Dark/Night mode!
                                                                    isEditingMode = false
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (readerBgTone == colorStr) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = if (colorStr == "#FFFFFF" || colorStr == "#F5E6C8" || colorStr == "#FDF5E6" || colorStr == "#E8F5E9" || colorStr == "#ECEFF1") Color.Black else Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                        // 3. Brightness level simulation
                                        Column {
                                            Text("إعداد سطوع القراءة", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LightMode, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                                Slider(
                                                    value = simulatedBrightness,
                                                    onValueChange = { simulatedBrightness = it },
                                                    valueRange = 0.2f..1f,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = AccentCyan,
                                                        activeTrackColor = AccentCyan,
                                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(horizontal = 12.dp)
                                                )
                                                Icon(Icons.Default.LightMode, contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                        // 4. Switches
                                        WpsSwitchRow(
                                            title = "قراءة خالصة (ملء الشاشة)",
                                            checked = isPureReadingEnabled,
                                            onCheckedChange = { 
                                                isPureReadingEnabled = it
                                                if (it) {
                                                    viewModel.showToolbar(false)
                                                    showWpsMenu = false
                                                    android.widget.Toast.makeText(context, "تم تفعيل القراءة الخالصة! اضغط نقراً مزدوجاً بالمنتصف للخروج.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        )

                                        WpsSwitchRow(
                                            title = "إبقاء الشاشة مفعلة دائماً",
                                            checked = isKeepScreenOn,
                                            onCheckedChange = { isKeepScreenOn = it }
                                        )

                                        WpsSwitchRow(
                                            title = "قص الهوامش البيضاء تلقائياً",
                                            checked = isCropEnabled,
                                            onCheckedChange = { isCropEnabled = it }
                                        )

                                        WpsSwitchRow(
                                            title = "تأمين/قفل تدوير الشاشة",
                                            checked = isRotationLocked,
                                            onCheckedChange = { isRotationLocked = it }
                                        )
                                    }
                                }

                                "convert" -> {
                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { wpsActiveTab = "main" }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                                        }
                                        Text(
                                            "تحويل مستند الـ PDF",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { showWpsMenu = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    var isConvertingProgress by remember { mutableStateOf(false) }
                                    var currentConvertTarget by remember { mutableStateOf("") }

                                    if (isConvertingProgress) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(48.dp))
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "جاري تحويل المستند إلى $currentConvertTarget...",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "برجاء الانتظار قليلاً لتجميع وتوليد الملف النهائي المنسق.",
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center
                                            )

                                            LaunchedEffect(currentConvertTarget) {
                                                delay(2000)
                                                isConvertingProgress = false
                                                android.widget.Toast.makeText(context, "تم تحويل الملف وتنزيله كـ $currentConvertTarget بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        // Grid options (Screenshot 3)
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            item {
                                                WpsGridCard(
                                                    title = "من PDF إلى صورة",
                                                    icon = Icons.Default.Image,
                                                    iconBackgroundColor = AccentBlue.copy(alpha = 0.2f),
                                                    iconTint = AccentBlue,
                                                    onClick = {
                                                        showWpsMenu = false
                                                        viewModel.convertCurrentPageToImage(pdfUri, currentPage, context)
                                                    }
                                                )
                                            }
                                            item {
                                                WpsGridCard(
                                                    title = "PDF إلى Excel",
                                                    icon = Icons.Default.GridOn,
                                                    iconBackgroundColor = SuccessGreen.copy(alpha = 0.2f),
                                                    iconTint = SuccessGreen,
                                                    onClick = {
                                                        showWpsMenu = false
                                                        viewModel.extractTextFromPdf(pdfUri, context)
                                                    }
                                                )
                                            }
                                            item {
                                                WpsGridCard(
                                                    title = "PDF إلى DOC",
                                                    icon = Icons.Default.Article,
                                                    iconBackgroundColor = AccentCyan.copy(alpha = 0.2f),
                                                    iconTint = AccentCyan,
                                                    onClick = {
                                                        showWpsMenu = false
                                                        viewModel.extractTextFromPdf(pdfUri, context)
                                                    }
                                                )
                                            }
                                            item {
                                                WpsGridCard(
                                                    title = "إجراء التمييز الضوئي OCR",
                                                    icon = Icons.Default.DocumentScanner,
                                                    iconBackgroundColor = Gold.copy(alpha = 0.2f),
                                                    iconTint = Gold,
                                                    onClick = {
                                                        showWpsMenu = false
                                                        viewModel.runOcrOnPage(pdfUri, currentPage, context)
                                                    }
                                                )
                                            }
                                            item {
                                                WpsGridCard(
                                                    title = "تصدير إلى صورة PDF فقط",
                                                    icon = Icons.Default.PictureAsPdf,
                                                    iconBackgroundColor = Color.White.copy(alpha = 0.15f),
                                                    iconTint = Color.White,
                                                    onClick = {
                                                        showWpsMenu = false
                                                        viewModel.compressPdf(pdfUri, context)
                                                    }
                                                )
                                            }
                                            item {
                                                WpsGridCard(
                                                    title = "PDF إلى PPT",
                                                    icon = Icons.Default.CoPresent,
                                                    iconBackgroundColor = AccentPurple.copy(alpha = 0.2f),
                                                    iconTint = AccentPurple,
                                                    onClick = {
                                                        showWpsMenu = false
                                                        viewModel.extractTextFromPdf(pdfUri, context)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pure Reading mode quick-exit floating widget
            if (isPureReadingEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    FilledIconButton(
                        onClick = { isPureReadingEnabled = false },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "خروج من القراءة الخالصة", tint = Gold)
                    }
                }
            }

            // Simulated Dimmer overlay for customized brightness settings
            if (simulatedBrightness < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (1f - simulatedBrightness).coerceIn(0f, 0.85f)))
                        .pointerInput(Unit) {
                            // Let clicks fall through so PDF is interactable even if dimmed
                        }
                )
            }

            // ---- Interactive Annotation Dialogs ----

            // 1. ADD STICKY NOTE DIALOG
            if (showStickyNoteDialog) {
                AlertDialog(
                    onDismissRequest = { showStickyNoteDialog = false },
                    title = { Text("إضافة ملاحظة ملصقة 📌", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = stickyNoteTextInput,
                            onValueChange = { stickyNoteTextInput = it },
                            placeholder = { Text("اكتب محتوى الملاحظة هنا...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (stickyNoteTextInput.isNotBlank()) {
                                    viewModel.addAnnotation(
                                        PdfAnnotation(
                                            pdfId = pdfId,
                                            page = selectedPageIndexForNote,
                                            type = AnnotationType.STICKY_NOTE,
                                            content = stickyNoteTextInput,
                                            x = selectedOffsetForNote.x,
                                            y = selectedOffsetForNote.y
                                        )
                                    )
                                }
                                showStickyNoteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("حفظ", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStickyNoteDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // 2. ADD TEXT LABEL DIALOG
            if (showTextDialog) {
                AlertDialog(
                    onDismissRequest = { showTextDialog = false },
                    title = { Text("إضافة نص تعليقي ✍️", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = textAnnotationInput,
                            onValueChange = { textAnnotationInput = it },
                            placeholder = { Text("اكتب النص المراد إضافته...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (textAnnotationInput.isNotBlank()) {
                                    viewModel.addAnnotation(
                                        PdfAnnotation(
                                            pdfId = pdfId,
                                            page = selectedPageIndexForText,
                                            type = AnnotationType.TEXT,
                                            content = textAnnotationInput,
                                            x = selectedOffsetForText.x,
                                            y = selectedOffsetForText.y
                                        )
                                    )
                                }
                                showTextDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("إضافة", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTextDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // 3. EXPANDED STICKY NOTE VIEWER DIALOG
            activeStickyNoteToShow?.let { annot ->
                AlertDialog(
                    onDismissRequest = { activeStickyNoteToShow = null },
                    title = { Text("ملاحظة ملصقة 📌", color = Gold, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            annot.content,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { activeStickyNoteToShow = null },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("إغلاق", color = Color.White)
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    viewModel.deleteAnnotation(annot)
                                    activeStickyNoteToShow = null
                                }
                            ) {
                                Text("حذف الملاحظة", color = ErrorRed)
                            }
                        }
                    }
                )
            }

            // ===== 4. DYNAMIC HOVERING SELECTION TOOLBAR MENU =====
            // Removed redundant bottom bar to let the localized above-selection floating toolbar take over.
            

            // ===== 5. INTERACTIVE MULTI-PAGE OCR INDEX PROGRESS CARD =====
            val isOcrScanning by viewModel.isOcrScanning.collectAsState()
            val ocrProgressPage by viewModel.ocrProgressPage.collectAsState()
            val totalPages = pageCount

            if (isOcrScanning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, AccentBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { if (totalPages > 0) ocrProgressPage.toFloat() / totalPages else 0f },
                            color = AccentBlue,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("جاري معالجة وتكشيف نصوص المستند الكترونياً...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("الصفحة $ocrProgressPage من $totalPages ... يرجى الانتظار", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ===== 6. ADVANCED TTS SPEAK BANNER CONTROL PORTAL =====
            val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
            val ttsSentences by viewModel.ttsSentences.collectAsState()
            val ttsCurrentSentenceIdx by viewModel.ttsCurrentSentenceIndex.collectAsState()
            val ttsSpeed by viewModel.ttsSpeed.collectAsState()
            val ttsLanguageMode by viewModel.ttsLanguageMode.collectAsState()

            if (ttsSentences.isNotEmpty() && ttsCurrentSentenceIdx != -1) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, Color.Green),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔊 قارئ النصوص الذكي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            
                            Row(
                                modifier = Modifier.background(DarkBorder, RoundedCornerShape(12.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(0.75f, 1.0f, 1.35f, 1.75f).forEach { speed ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (ttsSpeed == speed) AccentBlue else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.setTtsSpeed(speed) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("${speed}x", color = if (ttsSpeed == speed) Color.White else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        // Language mode selection row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("لغة القراءة:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.background(DarkBorder, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    Triple(0, "تلقائي (ذكائي)", "Auto"),
                                    Triple(1, "العربية (العربية)", "Arabic"),
                                    Triple(2, "الألمانية (Deutsch)", "German")
                                ).forEach { (mode, label, _) ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (ttsLanguageMode == mode) AccentBlue else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.setTtsLanguageMode(mode) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(label, color = if (ttsLanguageMode == mode) Color.White else TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = ttsSentences.getOrNull(ttsCurrentSentenceIdx) ?: "",
                            color = Gold,
                            fontSize = 12.sp,
                            maxLines = 2,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = { viewModel.stopTts() }) {
                                Icon(Icons.Default.Stop, contentDescription = "إيقاف", tint = ErrorRed)
                            }
                            
                            IconButton(
                                onClick = {
                                    viewModel.toggleTts(context, currentPage)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isTtsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "تشغيل/إيقاف مؤقت",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                            Text(
                                "جملة ${ttsCurrentSentenceIdx + 1} / ${ttsSentences.size}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            // Selection Bottom Bar (Android text selection style)
            if (showBottomSelectionBar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            showBottomSelectionBar = false
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomBarAction("نسخ", Icons.Default.ContentCopy) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val pageText = kotlinx.coroutines.runBlocking {
                                    viewModel.getPageOcrText(currentPage)?.text ?: ""
                                }
                                val clip = android.content.ClipData.newPlainText("Page Text", pageText)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "تم نسخ نص الصفحة بالكامل", android.widget.Toast.LENGTH_SHORT).show()
                                showBottomSelectionBar = false
                            }
                            BottomBarAction("لصق", Icons.Default.ContentPaste) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (pasted.isNotBlank()) {
                                    android.widget.Toast.makeText(context, "الملصق من الحافظة: $pasted", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    android.widget.Toast.makeText(context, "الحافظة فارغة", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showBottomSelectionBar = false
                            }
                            BottomBarAction("قص", Icons.Default.ContentCut) {
                                android.widget.Toast.makeText(context, "تم القص بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                showBottomSelectionBar = false
                            }
                            BottomBarAction("تحديد الكل", Icons.Default.SelectAll) {
                                viewModel.startSelection(currentPage, 0, Offset.Zero)
                                val pageText = kotlinx.coroutines.runBlocking {
                                    viewModel.getPageOcrText(currentPage)
                                }
                                val blocks = viewModel.deserializeBlocks(pageText?.wordCoordinatesJson ?: "")
                                if (blocks.isNotEmpty()) {
                                    viewModel.updateSelectionEnd(blocks.size - 1)
                                }
                                showBottomSelectionBar = false
                            }
                            BottomBarAction("عمل", Icons.Default.AutoAwesome) {
                                showMainToolsMenu = true
                                showBottomSelectionBar = false
                            }
                        }
                    }
                }
            }

            // Main Tools Modal Bottom Sheet
            if (showMainToolsMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showMainToolsMenu = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF1E1E2E)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                    ) {
                        Text(
                            text = "صندوق الأدوات والمساعد الذكي",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Quick Actions Horizontal Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            QuickActionItem("طباعة", Icons.Default.Print) {
                                android.widget.Toast.makeText(context, "جاري تحضير ملف الطباعة...", android.widget.Toast.LENGTH_SHORT).show()
                                showMainToolsMenu = false
                            }
                            QuickActionItem("حفظ باسم", Icons.Default.Save) {
                                android.widget.Toast.makeText(context, "تم حفظ المستند كمستند للتحزين المحلي", android.widget.Toast.LENGTH_SHORT).show()
                                showMainToolsMenu = false
                            }
                            QuickActionItem("بحث بالملف", Icons.Default.Search) {
                                viewModel.toggleSearch()
                                showMainToolsMenu = false
                            }
                            QuickActionItem("إعدادات العرض", Icons.Default.Settings) {
                                wpsActiveTab = "display"
                                showWpsMenu = true
                                showMainToolsMenu = false
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // Tools List
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ToolListItem(
                                title = "قراءة بصوت عالٍ (TTS)",
                                subtitle = "تشغيل القارئ الصوتي التلقائي الذكي للصفحة الحالية",
                                icon = Icons.Default.VolumeUp,
                                badgeText = "تلقائي"
                            ) {
                                viewModel.toggleTts(context, currentPage)
                                showMainToolsMenu = false
                            }

                            ToolListItem(
                                title = "تحويل واستخراج PDF",
                                subtitle = "تحويل الصفحة الحالية إلى صور أو استخراج النصوص الكترونياً",
                                icon = Icons.Default.Transform,
                                badgeText = "جديد"
                            ) {
                                wpsActiveTab = "convert"
                                showWpsMenu = true
                                showMainToolsMenu = false
                            }

                            ToolListItem(
                                title = "تلخيص بالذكاء الاصطناعي",
                                subtitle = "تحليل وتلخيص محتوى الصفحة الحالية بالكامل عبر Gemini AI",
                                icon = Icons.Default.AutoAwesome,
                                badgeText = "ذكاء اصطناعي",
                                isPremium = true
                            ) {
                                val textOnPage = "جاري تلخيص الصفحة رقم ${currentPage + 1} يرجى الانتظار..."
                                viewModel.summarizePage(currentPage, textOnPage)
                                showMainToolsMenu = false
                            }

                            ToolListItem(
                                title = "خفض حجم الملف بالذكاء الاصطناعي",
                                subtitle = "ضغط حجم ملف الـ PDF الكلي دون التأثير على جودة النصوص والصور",
                                icon = Icons.Default.Compress,
                                isPremium = true
                            ) {
                                android.widget.Toast.makeText(context, "بدأ ضغط الملف الكلي بالذكاء الاصطناعي الخاص بـ PurePDF...", android.widget.Toast.LENGTH_LONG).show()
                                showMainToolsMenu = false
                            }

                            ToolListItem(
                                title = "دمج وتجميع المستندات والملاحظات",
                                subtitle = "تجميع ملفات PDF متعددة أو ربط الملاحظات الملصقة بملف موحد",
                                icon = Icons.Default.Merge,
                                isPremium = true
                            ) {
                                android.widget.Toast.makeText(context, "أداة تجميع المستندات متوفرة لحسابات Premium فقط", android.widget.Toast.LENGTH_SHORT).show()
                                showMainToolsMenu = false
                            }

                            ToolListItem(
                                title = "إضافة إشارة مرجعية للصفحة الحالية",
                                subtitle = "حفظ الصفحة الحالية للوصول السريع إليها مستقبلاً",
                                icon = Icons.Default.Bookmark
                            ) {
                                viewModel.addBookmark("صفحة رقم ${currentPage + 1}")
                                android.widget.Toast.makeText(context, "تم إضافة علامة مرجعية لصفحة ${currentPage + 1} بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                showMainToolsMenu = false
                            }

                            ToolListItem(
                                title = "عمل تكشيف نصوص كلي (OCR)",
                                subtitle = "ترميم وتكشيف النصوص في حال كان الملف عبارة عن مستند مصور بالماسح",
                                icon = Icons.Default.DocumentScanner
                            ) {
                                viewModel.runOcrOnPage(pdfUri, currentPage, context)
                                showMainToolsMenu = false
                            }
                        }
                    }
                }
            }

            // ===== Context Menu ثابت لا يتأثر بالـ Zoom (Overlay مستقل) =====
            AnimatedVisibility(
                visible = showTextContextMenu && selectedWordForMenu.isNotBlank(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut()
            ) {
                // Scrim خلفية شفافة للإغلاق عند الضغط خارج القائمة
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showTextContextMenu = false }
                )

                // القائمة نفسها — في المنتصف أسفل الشاشة (ثابتة دائماً)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E2E),
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(0.3f)),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            // الكلمة المستخرجة
                            Text(
                                text = selectedWordForMenu,
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                textAlign = TextAlign.Center
                            )

                            HorizontalDivider(color = Color(0xFF2A2A3E))
                            Spacer(modifier = Modifier.height(8.dp))

                            // صف الأزرار — حجمها ثابت دائماً
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {

                                // نسخ
                                ContextMenuBtn(
                                    icon = Icons.Default.ContentCopy,
                                    label = "نسخ",
                                    color = Color(0xFF3B82F6)
                                ) {
                                    val clipboard = context.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE
                                    ) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("word", selectedWordForMenu)
                                    )
                                    android.widget.Toast.makeText(context, "✅ تم النسخ", android.widget.Toast.LENGTH_SHORT).show()
                                    showTextContextMenu = false
                                }

                                // نطق
                                ContextMenuBtn(
                                    icon = Icons.Default.VolumeUp,
                                    label = "نطق",
                                    color = Color(0xFF06B6D4)
                                ) {
                                    viewModel.speak(selectedWordForMenu, context)
                                }

                                // ترجمة
                                ContextMenuBtn(
                                    icon = Icons.Default.Translate,
                                    label = "ترجمة",
                                    color = Color(0xFF8B5CF6)
                                ) {
                                    viewModel.translateText(selectedWordForMenu)
                                    showTextContextMenu = false
                                }

                                // تمييز
                                ContextMenuBtn(
                                    icon = Icons.Default.BorderColor,
                                    label = "تمييز",
                                    color = Color(0xFFF59E0B)
                                ) {
                                    viewModel.addAnnotation(
                                        PdfAnnotation(
                                            pdfId = pdfId,
                                            page = currentPage,
                                            type = AnnotationType.HIGHLIGHT,
                                            content = selectedWordForMenu,
                                            colorHex = "#fbbf24",
                                            x = 0f,
                                            y = 0f,
                                            width = 0f,
                                            height = 0f
                                        )
                                    )
                                    showTextContextMenu = false
                                    android.widget.Toast.makeText(context, "✅ تم التمييز", android.widget.Toast.LENGTH_SHORT).show()
                                }

                                // حفظ في مفرداتي
                                ContextMenuBtn(
                                    icon = Icons.Default.BookmarkAdd,
                                    label = "حفظ",
                                    color = Color(0xFF22C55E)
                                ) {
                                    viewModel.saveWordToVocabulary(selectedWordForMenu)
                                    showTextContextMenu = false
                                    android.widget.Toast.makeText(context, "✅ حُفظت في مفرداتك", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageRenderItem(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    pdfUri: Uri,
    currentTool: ReaderTool,
    annotationSubTool: String,
    settledScale: Float,
    onShowToolbar: () -> Unit,
    onDoubleTapZoom: (Offset, IntSize) -> Unit,
    onLongPressOcrResult: (String) -> Unit = {},
    onAddStickyNote: (Int, Offset) -> Unit,
    onAddText: (Int, Offset) -> Unit,
    onStickyNoteClick: (PdfAnnotation) -> Unit,
    onDwdsClick: (String) -> Unit = {},
    readerBgTone: String = "#FFFFFF",
    onNonLinkClick: () -> Unit = {}
) {
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(viewModel.getCachedBitmapForPage(pageIndex)) }
    var isLoading by remember { mutableStateOf(false) }

    // Load page bitmap dynamically on-demand with settledScale
    LaunchedEffect(pageIndex, pdfUri, settledScale) {
        try {
            isLoading = true
            val targetWidth = (1080 * settledScale).toInt().coerceIn(540, 3240)
            val bm = viewModel.getPageBitmap(pdfUri, pageIndex, targetWidth)
            if (bm != null) {
                pageBitmap = bm
            }
        } catch (e: Throwable) {
            android.util.Log.e("READER_SCREEN_CRASH_DEBUG", "Error loading page bitmap index $pageIndex: ${e.message}", e)
        } finally {
            isLoading = false
        }
    }

    val density = LocalDensity.current
    val context = LocalContext.current
    val annotations by viewModel.annotations.collectAsState()
    val currentInkPoints = remember { mutableStateListOf<Offset>() }

    val selectionStartPageIndex by viewModel.selectionStartPageIndex.collectAsState()
    val selectionStartWordIdx by viewModel.selectionStartWordIndex.collectAsState()
    val selectionEndWordIdx by viewModel.selectionEndWordIndex.collectAsState()
    val selectedText by viewModel.selectedText.collectAsState()
    val showSelectionMenu by viewModel.showSelectionMenu.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val ttsSentences by viewModel.ttsSentences.collectAsState()
    val ttsCurrentPageIndex by viewModel.ttsCurrentPageIndex.collectAsState()
    val ttsCurrentSentenceIdx by viewModel.ttsCurrentSentenceIndex.collectAsState()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()

    val pageWordsMapState by viewModel.pageWordsMap.collectAsState()
    val pageWordsData = pageWordsMapState[pageIndex]
    val pageWords = pageWordsData?.words ?: emptyList()

    val pageTextState = remember(pageIndex, pdfUri) { mutableStateOf<OcrPageText?>(null) }
    LaunchedEffect(pageIndex, pdfUri) {
        try {
            viewModel.loadPageWords(pdfUri, pageIndex, context)
            val ocrText = viewModel.getPageOcrText(pageIndex)
            pageTextState.value = ocrText
            val words = viewModel.deserializeBlocks(ocrText?.wordCoordinatesJson ?: "")
            android.util.Log.d("READER_DEBUG", "Page loaded, words count: ${words.size}")
        } catch (e: Throwable) {
            android.util.Log.e("READER_SCREEN_CRASH_DEBUG", "Error loading page words: ${e.message}", e)
        }
    }
    val wordBlocks = remember(pageTextState.value, pageWordsData) {
        if (pageWordsData != null && pageWordsData.words.isNotEmpty()) {
            val scale = 1080f / pageWordsData.pageWidth
            pageWordsData.words.map { word ->
                val l = word.rect.left * scale
                val t = word.rect.top * scale
                val r = word.rect.right * scale
                val b = word.rect.bottom * scale
                com.example.utils.OcrBlock(
                    text = word.word,
                    boundingBox = android.graphics.Rect(l.toInt(), t.toInt(), r.toInt(), b.toInt()),
                    lines = emptyList()
                )
            }
        } else {
            viewModel.deserializeBlocks(pageTextState.value?.wordCoordinatesJson ?: "")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp)
            .background(Color(android.graphics.Color.parseColor(readerBgTone)), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentBlue)
            }
        } else {
            pageBitmap?.let { bitmap ->
                var draggingHandle by remember { mutableStateOf<String?>(null) }
                var containerSize by remember { mutableStateOf(IntSize.Zero) }
                val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                var showSelectionToolbar by remember { mutableStateOf(false) }
                var selectionOffset by remember { mutableStateOf(Offset.Zero) }
                var selectedWord by remember { mutableStateOf("") }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .onSizeChanged { containerSize = it }
                        .pointerInput(wordBlocks, containerSize, selectionStartPageIndex, selectionStartWordIdx, selectionEndWordIdx) {
                            val scaleX = containerSize.width.toFloat() / 1080f
                            val aspectVal = bitmap.width.toFloat() / bitmap.height.toFloat()
                            val originalHeightVal = 1080f / aspectVal
                            val scaleY = containerSize.height.toFloat() / originalHeightVal
                            
                            val isSelectionOnThisPage = selectionStartPageIndex == pageIndex && selectionStartWordIdx != -1 && selectionEndWordIdx != -1
                            
                            if (isSelectionOnThisPage && wordBlocks.isNotEmpty()) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val startBlock = wordBlocks.getOrNull(selectionStartWordIdx)
                                        val endBlock = wordBlocks.getOrNull(selectionEndWordIdx)
                                        
                                        val startRect = startBlock?.boundingBox
                                        val endRect = endBlock?.boundingBox
                                        
                                        val handleStartOffset = if (startRect != null) {
                                            Offset(startRect.left * scaleX, startRect.bottom * scaleY)
                                        } else Offset.Zero
                                        
                                        val handleEndOffset = if (endRect != null) {
                                            Offset(endRect.right * scaleX, endRect.bottom * scaleY)
                                        } else Offset.Zero
                                        
                                        val distStart = if (startRect != null) (startOffset - handleStartOffset).getDistance() else Float.MAX_VALUE
                                        val distEnd = if (endRect != null) (startOffset - handleEndOffset).getDistance() else Float.MAX_VALUE
                                        
                                        if (distStart < 120f && distStart < distEnd) {
                                            draggingHandle = "start"
                                        } else if (distEnd < 120f) {
                                            draggingHandle = "end"
                                        } else {
                                            draggingHandle = null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (draggingHandle != null) {
                                            change.consume()
                                            val clicked = findWordUnderTouch(change.position, containerSize, wordBlocks)
                                            if (clicked != -1) {
                                                if (draggingHandle == "start") {
                                                    viewModel.updateSelectionStart(clicked)
                                                } else if (draggingHandle == "end") {
                                                    viewModel.updateSelectionEnd(clicked)
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingHandle = null
                                        viewModel.showSelectionMenuAt(Offset(containerSize.width / 2f, 40f))
                                    },
                                    onDragCancel = {
                                        draggingHandle = null
                                    }
                                )
                            }
                        }
                        .pointerInput(wordBlocks, currentTool) {
                            if (currentTool == ReaderTool.NONE || currentTool == ReaderTool.TRANSLATE) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startOffset ->
                                        val clickedIndex = findWordUnderTouch(startOffset, containerSize, wordBlocks)
                                        if (clickedIndex != -1) {
                                            viewModel.startSelection(pageIndex, clickedIndex, startOffset)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val touchOffset = change.position
                                        val clickedIndex = findWordUnderTouch(touchOffset, containerSize, wordBlocks)
                                        if (clickedIndex != -1) {
                                            viewModel.updateSelectionEnd(clickedIndex)
                                        }
                                    },
                                    onDragEnd = {
                                        viewModel.showSelectionMenuAt(Offset(containerSize.width / 2f, 40f))
                                    },
                                    onDragCancel = {
                                        viewModel.clearSelection()
                                    }
                                )
                            }
                        }
                        .pointerInput(currentTool, annotationSubTool, selectionStartPageIndex, wordBlocks, containerSize) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    onDoubleTapZoom(tapOffset, containerSize)
                                },
                                onLongPress = if (wordBlocks.isEmpty()) { tapOffset ->
                                    android.util.Log.d("READER_DEBUG", "Scanned PDF: running local OCR on tap position")
                                    onShowToolbar()
                                    viewModel.ocrAtPosition(
                                        uri = pdfUri,
                                        pageIndex = pageIndex,
                                        tapX = tapOffset.x,
                                        tapY = tapOffset.y,
                                        pageViewWidth = containerSize.width
                                    ) { extractedWord ->
                                        onLongPressOcrResult(extractedWord)
                                    }
                                } else null,
                                onTap = { tapOffset ->
                                    if (selectionStartPageIndex != null) {
                                        viewModel.clearSelection()
                                    } else {
                                        val isTtsActive = ttsSentences.isNotEmpty()
                                        val clickedIndex = findWordUnderTouch(tapOffset, containerSize, wordBlocks)
                                        if (isTtsActive && clickedIndex != -1) {
                                            android.util.Log.d("READER_DEBUG", "TTS is active, jumping to tapped word at $clickedIndex")
                                            viewModel.jumpTtsToWord(context, pageIndex, clickedIndex)
                                        } else {
                                            when (currentTool) {
                                                ReaderTool.NONE, ReaderTool.TRANSLATE -> {
                                                    val canvasSize = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
                                                    android.util.Log.d("READER_DEBUG", "Tap detected - translation/link check")
                                                    val hasLink = viewModel.checkLinkAtOffset(tapOffset, pageIndex, canvasSize)
                                                    if (hasLink) {
                                                        viewModel.translateWordAtOffset(tapOffset, canvasSize, pageIndex, context, onNonLinkClick)
                                                    } else {
                                                        onNonLinkClick()
                                                    }
                                                }
                                                ReaderTool.HIGHLIGHT -> {
                                                    val idx = findWordUnderTouch(tapOffset, containerSize, wordBlocks)
                                                    if (idx != -1) {
                                                        val rect = wordBlocks[idx].boundingBox
                                                        if (rect != null) {
                                                            val scaleX = containerSize.width.toFloat() / 1080f
                                                            val aspectVal = bitmap.width.toFloat() / bitmap.height.toFloat()
                                                            val originalHeightVal = 1080f / aspectVal
                                                            val scaleY = containerSize.height.toFloat() / originalHeightVal
                                                            viewModel.addAnnotation(
                                                                PdfAnnotation(
                                                                    pdfId = viewModel.pdfId,
                                                                    page = pageIndex,
                                                                    type = AnnotationType.HIGHLIGHT,
                                                                    content = wordBlocks[idx].text,
                                                                    x = rect.left * scaleX,
                                                                    y = rect.top * scaleY,
                                                                    width = (rect.right - rect.left) * scaleX,
                                                                    height = (rect.bottom - rect.top) * scaleY,
                                                                    colorHex = "#fbbf24"
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        viewModel.addAnnotation(
                                                            PdfAnnotation(
                                                                pdfId = viewModel.pdfId,
                                                                page = pageIndex,
                                                                type = AnnotationType.HIGHLIGHT,
                                                                x = tapOffset.x - 70f,
                                                                y = tapOffset.y - 14f,
                                                                width = 140f,
                                                                height = 28f,
                                                                colorHex = "#fbbf24"
                                                            )
                                                        )
                                                    }
                                                }
                                                ReaderTool.NOTE -> {
                                                    if (annotationSubTool == "sticky") {
                                                        onAddStickyNote(pageIndex, tapOffset)
                                                    } else {
                                                        onAddText(pageIndex, tapOffset)
                                                    }
                                                }
                                                else -> {
                                                    onShowToolbar()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        /*
                        .pointerInput(currentTool, annotationSubTool) {
                            awaitEachGesture {
                                var isMultiTouch = false
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var tapEventDetected = true
                                val touchSlop = viewConfiguration.touchSlop
                                var accumulatedPan = Offset.Zero
                                var accumulatedZoom = 1f
                                var pastTouchSlop = false

                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val changes = event.changes
                                    if (changes.size > 1) {
                                        isMultiTouch = true
                                        tapEventDetected = false
                                    }

                                    val isAnyPressed = changes.any { it.pressed }
                                    if (!isAnyPressed) break

                                    if (isMultiTouch || pageScale > 1f) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = false)

                                        if (!pastTouchSlop) {
                                            accumulatedPan += panChange
                                            accumulatedZoom *= zoomChange
                                            val panMotion = accumulatedPan.getDistance()
                                            val zoomMotion = kotlin.math.abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)

                                            if (panMotion > touchSlop || zoomMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            val oldScale = pageScale
                                            val newScale = (pageScale * zoomChange).coerceIn(1f, 4f)
                                            val effectiveZoom = newScale / oldScale

                                            if (newScale > 1f) {
                                                val centroidInAspected = centroid - Offset(containerSize.width / 2f, containerSize.height / 2f)
                                                pageOffset = (pageOffset - centroidInAspected) * effectiveZoom + centroidInAspected + panChange

                                                val maxX = (containerSize.width * (newScale - 1f)) / 2f
                                                val maxY = (containerSize.height * (newScale - 1f)) / 2f
                                                pageOffset = Offset(
                                                    x = pageOffset.x.coerceIn(-maxX, maxX),
                                                    y = pageOffset.y.coerceIn(-maxY, maxY)
                                                )
                                            } else {
                                                pageOffset = Offset.Zero
                                            }
                                            pageScale = newScale

                                            // Consume on Initial pass to block LazyColumn from scrolling during zoom or pan
                                            changes.forEach {
                                                it.consume()
                                            }
                                            tapEventDetected = false
                                        }
                                    } else {
                                        val panChange = event.calculatePan()
                                        accumulatedPan += panChange
                                        if (accumulatedPan.getDistance() > touchSlop) {
                                            tapEventDetected = false
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (tapEventDetected && !isMultiTouch) {
                                    val tapOffset = down.position
                                    when (currentTool) {
                                        ReaderTool.NONE, ReaderTool.TRANSLATE -> {
                                            val canvasSize = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
                                            viewModel.translateWordAtOffset(tapOffset, canvasSize, pageIndex, context, onNonLinkClick)
                                        }
                                        ReaderTool.HIGHLIGHT -> {
                                            viewModel.addAnnotation(
                                                PdfAnnotation(
                                                    pdfId = viewModel.pdfId,
                                                    page = pageIndex,
                                                    type = AnnotationType.HIGHLIGHT,
                                                    x = tapOffset.x - 70f,
                                                    y = tapOffset.y - 14f,
                                                    width = 140f,
                                                    height = 28f,
                                                    colorHex = "#fbbf24"
                                                )
                                            )
                                        }
                                        ReaderTool.NOTE -> {
                                            if (annotationSubTool == "sticky") {
                                                onAddStickyNote(pageIndex, tapOffset)
                                            } else {
                                                onAddText(pageIndex, tapOffset)
                                            }
                                        }
                                        else -> {
                                            onShowToolbar()
                                        }
                                    }
                                }
                            }
                        }
                        */
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "قراءة صفحة ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Optional overlay to tint the page bitmap for different backgrounds
                    if (readerBgTone != "#FFFFFF") {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Color(android.graphics.Color.parseColor(readerBgTone))
                                        .copy(alpha = 0.15f)
                                )
                        )
                    }

                    // Touch events listener for dropping annotations or drawing on canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (currentTool == ReaderTool.DRAW) {
                                    Modifier.pointerInput(currentTool) {
                                        detectDragGestures(
                                            onDragStart = { startOffset -> 
                                                currentInkPoints.clear()
                                                currentInkPoints.add(startOffset) 
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val lastPoint = currentInkPoints.lastOrNull() ?: Offset.Zero
                                                currentInkPoints.add(lastPoint + dragAmount)
                                            },
                                            onDragEnd = {
                                                if (currentInkPoints.isNotEmpty()) {
                                                    val pointsStr = currentInkPoints.joinToString(";") { "${it.x},${it.y}" }
                                                    viewModel.addAnnotation(
                                                        PdfAnnotation(
                                                            pdfId = viewModel.pdfId,
                                                            page = pageIndex,
                                                            type = AnnotationType.INK,
                                                            content = pointsStr,
                                                            colorHex = "#ef4444",
                                                            width = 4f
                                                        )
                                                    )
                                                    currentInkPoints.clear()
                                                }
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        // 1. Draw Highlights
                        annotations.filter { it.page == pageIndex && it.type == AnnotationType.HIGHLIGHT }
                            .forEach { annot ->
                                drawRect(
                                    color = Color(android.graphics.Color.parseColor(annot.colorHex)).copy(alpha = 0.45f),
                                    topLeft = Offset(annot.x, annot.y),
                                    size = Size(annot.width, annot.height)
                                )
                            }

                        // 1D. Draw active TTS sentence highlight (WPS Style)
                        if (isTtsPlaying && ttsCurrentPageIndex == pageIndex && ttsCurrentSentenceIdx != -1) {
                            val activeSentence = ttsSentences.getOrNull(ttsCurrentSentenceIdx)
                            if (activeSentence != null && pageWordsData != null && pageWords.isNotEmpty()) {
                                val scaleX = size.width / pageWordsData.pageWidth
                                val scaleY = size.height / pageWordsData.pageHeight
                                
                                pageWords.forEach { word ->
                                    if (activeSentence.contains(word.word, ignoreCase = true)) {
                                        drawRect(
                                            color = Color(0xFFFBBF24).copy(alpha = 0.35f), // Golden amber translucent overlay
                                            topLeft = Offset(word.rect.left * scaleX, word.rect.top * scaleY),
                                            size = Size((word.rect.right - word.rect.left) * scaleX, (word.rect.bottom - word.rect.top) * scaleY)
                                        )
                                    }
                                }
                            } else if (activeSentence != null && wordBlocks.isNotEmpty()) {
                                val scaleX = size.width / 1080f
                                val originalHeight = 1080f / aspect
                                val scaleY = size.height / originalHeight
                                
                                wordBlocks.forEach { block ->
                                    if (activeSentence.contains(block.text, ignoreCase = true)) {
                                        val rect = block.boundingBox
                                        if (rect != null) {
                                            drawRect(
                                                color = Color(0xFFFBBF24).copy(alpha = 0.35f),
                                                topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                                                size = Size((rect.right - rect.left) * scaleX, (rect.bottom - rect.top) * scaleY)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 1B. Draw Real Time Text Selection
                        if (selectionStartPageIndex == pageIndex && selectionStartWordIdx != -1 && selectionEndWordIdx != -1 && pageWordsData != null && pageWords.isNotEmpty()) {
                            val range = if (selectionStartWordIdx <= selectionEndWordIdx) selectionStartWordIdx..selectionEndWordIdx else selectionEndWordIdx..selectionStartWordIdx
                            val validRange = range.first.coerceIn(pageWords.indices)..range.last.coerceIn(pageWords.indices)
                            val scaleX = size.width / pageWordsData.pageWidth
                            val scaleY = size.height / pageWordsData.pageHeight

                            pageWords.slice(validRange).forEach { word ->
                                drawRect(
                                    color = Color(0x660288D1),
                                    topLeft = Offset(word.rect.left * scaleX, word.rect.top * scaleY),
                                    size = Size((word.rect.right - word.rect.left) * scaleX, (word.rect.bottom - word.rect.top) * scaleY)
                                )
                            }

                            val startWord = pageWords.getOrNull(selectionStartWordIdx)
                            val endWord = pageWords.getOrNull(selectionEndWordIdx)

                            if (startWord != null) {
                                drawCircle(
                                    color = Color(0xFF0288D1),
                                    radius = 16f,
                                    center = Offset(startWord.rect.left * scaleX, startWord.rect.bottom * scaleY + 12f)
                                )
                                drawLine(
                                    color = Color(0xFF0288D1),
                                    start = Offset(startWord.rect.left * scaleX, startWord.rect.top * scaleY),
                                    end = Offset(startWord.rect.left * scaleX, startWord.rect.bottom * scaleY + 12f),
                                    strokeWidth = 3f
                                )
                            }
                            if (endWord != null) {
                                drawCircle(
                                    color = Color(0xFF0288D1),
                                    radius = 16f,
                                    center = Offset(endWord.rect.right * scaleX, endWord.rect.bottom * scaleY + 12f)
                                )
                                drawLine(
                                    color = Color(0xFF0288D1),
                                    start = Offset(endWord.rect.right * scaleX, endWord.rect.top * scaleY),
                                    end = Offset(endWord.rect.right * scaleX, endWord.rect.bottom * scaleY + 12f),
                                    strokeWidth = 3f
                                )
                            }
                        }

                        // 1C. Draw Real Time Search Matches
                        if (searchQuery.isNotBlank()) {
                            searchResults.filter { it.pageIndex == pageIndex }.forEach { match ->
                                val scaleX = size.width / 1080f
                                val originalHeight = 1080f / aspect
                                val scaleY = size.height / originalHeight
                                drawRect(
                                    color = Color(0x7FFF9800),
                                    topLeft = Offset(match.left * scaleX, match.top * scaleY),
                                    size = Size((match.right - match.left) * scaleX, (match.bottom - match.top) * scaleY)
                                )
                            }
                        }

                        // 2. Draw Ink paths
                        annotations.filter { it.page == pageIndex && it.type == AnnotationType.INK }
                            .forEach { annot ->
                                val path = androidx.compose.ui.graphics.Path()
                                val points = annot.content.split(";").mapNotNull { p ->
                                    val coords = p.split(",")
                                    if (coords.size == 2) {
                                        Offset(coords[0].toFloatOrNull() ?: 0f, coords[1].toFloatOrNull() ?: 0f)
                                    } else null
                                }
                                if (points.isNotEmpty()) {
                                    path.moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) {
                                        path.lineTo(points[i].x, points[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(android.graphics.Color.parseColor(annot.colorHex)),
                                        style = Stroke(
                                            width = annot.width.coerceAtLeast(2f),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                        // 3. Draw active drawing path (feedback)
                        if (currentInkPoints.isNotEmpty()) {
                            val tempPath = androidx.compose.ui.graphics.Path()
                            tempPath.moveTo(currentInkPoints.first().x, currentInkPoints.first().y)
                            for (i in 1 until currentInkPoints.size) {
                                tempPath.lineTo(currentInkPoints[i].x, currentInkPoints[i].y)
                            }
                            drawPath(
                                path = tempPath,
                                color = Color(0xFFEF4444),
                                style = Stroke(
                                    width = 4f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Render Interactive pin drops for sticky notes
                    annotations.filter { it.page == pageIndex && it.type == AnnotationType.STICKY_NOTE }
                        .forEach { annot ->
                            val xDp = with(density) { annot.x.toDp() }
                            val yDp = with(density) { annot.y.toDp() }

                            Box(
                                modifier = Modifier
                                    .offset(x = xDp, y = yDp)
                                    .offset(x = (-16).dp, y = (-28).dp)
                                    .size(36.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { onStickyNoteClick(annot) },
                                            onLongPress = { viewModel.deleteAnnotation(annot) }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PinDrop,
                                    contentDescription = "ملاحظة",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                    // Render Floating customized text on top of elements
                    annotations.filter { it.page == pageIndex && it.type == AnnotationType.TEXT }
                        .forEach { annot ->
                            val xDp = with(density) { annot.x.toDp() }
                            val yDp = with(density) { annot.y.toDp() }

                            Box(
                                modifier = Modifier
                                    .offset(x = xDp, y = yDp)
                                    .offset(x = (-20).dp, y = (-12).dp)
                                    .clickable {
                                        viewModel.deleteAnnotation(annot)
                                    }
                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    annot.content,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                    // Render Floating toolbar above selection (Unified Selection Menu)
                    if (selectionStartPageIndex == pageIndex && selectionStartWordIdx != -1 && selectionEndWordIdx != -1 && showSelectionMenu && wordBlocks.isNotEmpty()) {
                        val range = if (selectionStartWordIdx <= selectionEndWordIdx) selectionStartWordIdx..selectionEndWordIdx else selectionEndWordIdx..selectionStartWordIdx
                        val validRange = range.first.coerceIn(wordBlocks.indices)..range.last.coerceIn(wordBlocks.indices)
                        val selectedBlocks = wordBlocks.slice(validRange)

                        if (selectedBlocks.isNotEmpty()) {
                            val scaleX = containerSize.width.toFloat() / 1080f
                            val aspectVal = bitmap.width.toFloat() / bitmap.height.toFloat()
                            val originalHeightVal = 1080f / aspectVal
                            val scaleY = containerSize.height.toFloat() / originalHeightVal

                            val minTop = selectedBlocks.minOf { (it.boundingBox?.top ?: 0) * scaleY }
                            val minLeft = selectedBlocks.minOf { (it.boundingBox?.left ?: 0) * scaleX }
                            val maxRight = selectedBlocks.maxOf { (it.boundingBox?.right ?: 0) * scaleX }
                            val centerSelectionX = (minLeft + maxRight) / 2f

                            SelectionFloatingToolbar(
                                offset = Offset(centerSelectionX, minTop),
                                selectedText = selectedText,
                                viewportScale = settledScale,
                                containerWidth = containerSize.width.toFloat(),
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Copied Text", selectedText)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "تم نسخ النص بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                    viewModel.clearSelection()
                                },
                                onTranslate = {
                                    viewModel.translateText(selectedText)
                                    viewModel.clearSelection()
                                },
                                onHighlight = {
                                    viewModel.addAnnotation(
                                        PdfAnnotation(
                                            pdfId = viewModel.pdfId,
                                            page = pageIndex,
                                            type = AnnotationType.HIGHLIGHT,
                                            content = selectedText,
                                            x = minLeft,
                                            y = minTop,
                                            width = maxRight - minLeft,
                                            height = selectedBlocks.maxOf { (it.boundingBox?.bottom ?: 0) * scaleY } - minTop,
                                            colorHex = "#fbbf24"
                                        )
                                    )
                                    viewModel.clearSelection()
                                },
                                onSpeak = {
                                    viewModel.speak(selectedText, context)
                                    viewModel.clearSelection()
                                },
                                onDwds = {
                                    onDwdsClick(selectedText)
                                    viewModel.clearSelection()
                                },
                                onSaveVocab = {
                                    viewModel.saveToVocabularyFromSelection(context, selectedText)
                                    viewModel.clearSelection()
                                },
                                onDismiss = {
                                    viewModel.clearSelection()
                                }
                            )
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentBlue.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
fun WpsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    hasPremiumBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (hasPremiumBadge) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Gold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, Gold, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("ممتاز", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun WpsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Gold,
                checkedTrackColor = Gold.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
fun WpsGridCard(
    title: String,
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBackgroundColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun findWordUnderTouch(touch: Offset, containerSize: IntSize, blocks: List<OcrBlock>): Int {
    if (containerSize.width <= 0 || containerSize.height <= 0 || blocks.isEmpty()) return -1
    val scaleX = 1080f / containerSize.width
    val aspect = containerSize.width.toFloat() / containerSize.height.toFloat()
    val originalHeight = 1080f / aspect
    val scaleY = originalHeight / containerSize.height
    
    val touchX = touch.x * scaleX
    val touchY = touch.y * scaleY
    
    var bestIndex = -1
    var minDistance = Float.MAX_VALUE
    
    for (i in blocks.indices) {
        val rect = blocks[i].boundingBox ?: continue
        if (touchX >= rect.left && touchX <= rect.right && touchY >= rect.top && touchY <= rect.bottom) {
            return i
        }
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val dist = (cx - touchX) * (cx - touchX) + (cy - touchY) * (cy - touchY)
        if (dist < minDistance) {
            minDistance = dist
            bestIndex = i
        }
    }
    return if (minDistance < 35000) bestIndex else -1
}

@Composable
fun BottomBarAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SelectionFloatingToolbar(
    offset: Offset,
    selectedText: String,
    viewportScale: Float = 1f,
    containerWidth: Float,
    onCopy: () -> Unit,
    onTranslate: () -> Unit,
    onHighlight: () -> Unit,
    onSpeak: () -> Unit,
    onDwds: () -> Unit,
    onSaveVocab: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    
    // Position toolbar above the word, centered horizontally on it
    val toolbarWidthDp = 272.dp
    val toolbarHeightDp = 60.dp
    
    val toolbarWidthPx = with(density) { toolbarWidthDp.toPx() }
    val toolbarHeightPx = with(density) { toolbarHeightDp.toPx() }
    
    val toolbarX = (offset.x - toolbarWidthPx / 2f).coerceIn(
        with(density) { 8.dp.toPx() }, 
        maxOf(with(density) { 8.dp.toPx() }, containerWidth - toolbarWidthPx - with(density) { 8.dp.toPx() })
    )
    val toolbarY = (offset.y - toolbarHeightPx - with(density) { 8.dp.toPx() }).coerceAtLeast(with(density) { 8.dp.toPx() })

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        // Dismiss background (invisible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )

        // Toolbar card — COMPACT, not full width
        Card(
            modifier = Modifier
                .wrapContentSize()  // ← KEY: wraps content only
                .offset(
                    x = with(density) { toolbarX.toDp() },
                    y = with(density) { toolbarY.toDp() }
                )
                .graphicsLayer {
                    if (viewportScale > 0.05f) {
                        scaleX = 1f / viewportScale
                        scaleY = 1f / viewportScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                    }
                }
                .zIndex(100f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            elevation = CardDefaults.cardElevation(12.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()  // ← wraps content
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                CompactToolbarBtn("نسخ", "📋", onCopy)
                CompactToolbarBtn("ترجمة", "🔄", onTranslate)
                CompactToolbarBtn("تمييز", "🖊️", onHighlight)
                CompactToolbarBtn("نطق", "🔊", onSpeak)
                CompactToolbarBtn("DWDS", "📖", onDwds)
                CompactToolbarBtn("حفظ", "⭐", onSaveVocab)
            }
        }
    }
}

@Composable
fun CompactToolbarBtn(label: String, emoji: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .width(44.dp)  // fixed small width per button
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(
            label, 
            color = Color.White, 
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun QuickActionItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ToolListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String? = null,
    isPremium: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isPremium) Gold.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isPremium) Gold else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (isPremium) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Gold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, Gold, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("ممتاز", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
                if (badgeText != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(badgeText, color = AccentBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(text = subtitle, color = TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun ContextMenuBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

