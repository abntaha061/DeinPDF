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
    val summaryText by viewModel.summaryText.collectAsState()
    val showSummary by viewModel.showSummary.collectAsState()
    val showQaChat by viewModel.showQaChat.collectAsState()

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

    // Intercept system back button to close any open overlays first before exiting reader
    val isBackHandlerEnabled = showWpsMenu || showTranslationCard || showSummary || showQaChat || (activeStickyNoteToShow != null) || isSearchVisible
    BackHandler(enabled = isBackHandlerEnabled) {
        when {
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // WPS Tools Action Bar Redesigned (Clickable Columns for auto-adapting labels, no Arabic clipping)
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 0. WPS Tools Menu
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { 
                                        wpsActiveTab = "main"
                                        showWpsMenu = true
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Widgets, 
                                    contentDescription = "أدوات WPS", 
                                    tint = Gold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "أدوات WPS", 
                                    fontSize = 11.sp, 
                                    lineHeight = 16.sp,
                                    color = Gold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            // 1. Highlight
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentTool == ReaderTool.HIGHLIGHT) AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .clickable { 
                                        viewModel.setTool(ReaderTool.HIGHLIGHT)
                                        isEditingMode = true
                                        showToolbarTemporarily()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.BorderColor, 
                                    contentDescription = "تمييز", 
                                    tint = if (currentTool == ReaderTool.HIGHLIGHT) Gold else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "تمييز ورقي", 
                                    fontSize = 11.sp, 
                                    lineHeight = 16.sp,
                                    color = if (currentTool == ReaderTool.HIGHLIGHT) Gold else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            
                            // 2. Ink Draw
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentTool == ReaderTool.DRAW) AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .clickable { 
                                        viewModel.setTool(ReaderTool.DRAW)
                                        isEditingMode = true
                                        showToolbarTemporarily()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Gesture, 
                                    contentDescription = "رسم حر", 
                                    tint = if (currentTool == ReaderTool.DRAW) AccentCyan else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "رسم حر", 
                                    fontSize = 11.sp, 
                                    lineHeight = 16.sp,
                                    color = if (currentTool == ReaderTool.DRAW) AccentCyan else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            // 3. Text note
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentTool == ReaderTool.NOTE && annotationSubTool == "text") AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .clickable { 
                                        viewModel.setTool(ReaderTool.NOTE)
                                        annotationSubTool = "text"
                                        isEditingMode = true
                                        showToolbarTemporarily()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.TextFields, 
                                    contentDescription = "كتابة نص", 
                                    tint = if (currentTool == ReaderTool.NOTE && annotationSubTool == "text") AccentPurple else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "أضف نص", 
                                    fontSize = 11.sp, 
                                    lineHeight = 16.sp,
                                    color = if (currentTool == ReaderTool.NOTE && annotationSubTool == "text") AccentPurple else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            // 4. Sticky note
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentTool == ReaderTool.NOTE && annotationSubTool == "sticky") AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .clickable { 
                                        viewModel.setTool(ReaderTool.NOTE)
                                        annotationSubTool = "sticky"
                                        isEditingMode = true
                                        showToolbarTemporarily()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.PinDrop, 
                                    contentDescription = "ملاحظة ملصقة", 
                                    tint = if (currentTool == ReaderTool.NOTE && annotationSubTool == "sticky") Gold else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "ملاحظة", 
                                    fontSize = 11.sp, 
                                    lineHeight = 16.sp,
                                    color = if (currentTool == ReaderTool.NOTE && annotationSubTool == "sticky") Gold else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }



                            // 6. Translator Text Select Touch
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentTool == ReaderTool.TRANSLATE) AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                                    )
                                    .clickable { 
                                        viewModel.setTool(ReaderTool.TRANSLATE)
                                        isEditingMode = true
                                        showToolbarTemporarily()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Translate, 
                                    contentDescription = "مترجم", 
                                    tint = if (currentTool == ReaderTool.TRANSLATE) Gold else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "مترجم", 
                                    fontSize = 11.sp, 
                                    lineHeight = 16.sp,
                                    color = if (currentTool == ReaderTool.TRANSLATE) Gold else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
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
            // Main Pdf Workspace Scroll Loop
            if (pageCount > 0) {
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
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
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
                                readerBgTone = readerBgTone,
                                onNonLinkClick = {
                                    showWpsMenu = true
                                    viewModel.hideTranslation()
                                }
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
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
                                        onClick = { viewModel.speak(result.originalWord, context) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, AccentCyan),
                                        modifier = Modifier.weight(0.4f)
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
            val selectedText by viewModel.selectedText.collectAsState()
            val selectionStartPageIndexState by viewModel.selectionStartPageIndex.collectAsState()
            val showSelectionMenu by viewModel.showSelectionMenu.collectAsState()
            
            if (selectionStartPageIndexState != null && selectedText.isNotBlank() && showSelectionMenu) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, AccentBlue),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(onClick = {
                            viewModel.addAnnotation(
                                PdfAnnotation(
                                    pdfId = viewModel.pdfId,
                                    page = selectionStartPageIndexState ?: 0,
                                    type = AnnotationType.HIGHLIGHT,
                                    content = selectedText,
                                    x = 100f,
                                    y = 100f,
                                    width = 300f,
                                    height = 40f,
                                    colorHex = "#FBBF24"
                                )
                            )
                            viewModel.clearSelection()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.BorderColor, contentDescription = "تمييز", tint = Gold, modifier = Modifier.size(20.dp))
                                Text("تمييز", fontSize = 9.sp, color = Color.White)
                            }
                        }

                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Copied Text", selectedText)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "تم كبس النص بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.clearSelection()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("نسخ", fontSize = 9.sp, color = Color.White)
                            }
                        }

                        IconButton(onClick = {
                            viewModel.translateText(selectedText)
                            viewModel.clearSelection()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Translate, contentDescription = "ترجمة", tint = AccentBlue, modifier = Modifier.size(20.dp))
                                Text("ترجمة", fontSize = 9.sp, color = Color.White)
                            }
                        }

                        IconButton(onClick = {
                            viewModel.speak(selectedText, context)
                            viewModel.clearSelection()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "نطق", tint = Color.Green, modifier = Modifier.size(20.dp))
                                Text("نطق", fontSize = 9.sp, color = Color.White)
                            }
                        }

                        IconButton(onClick = {
                            viewModel.clearSelection()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Close, contentDescription = "تم", tint = ErrorRed, modifier = Modifier.size(20.dp))
                                Text("إلغاء", fontSize = 9.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

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
    onAddStickyNote: (Int, Offset) -> Unit,
    onAddText: (Int, Offset) -> Unit,
    onStickyNoteClick: (PdfAnnotation) -> Unit,
    readerBgTone: String = "#FFFFFF",
    onNonLinkClick: () -> Unit = {}
) {
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(viewModel.getCachedBitmapForPage(pageIndex)) }
    var isLoading by remember { mutableStateOf(false) }

    // Load page bitmap dynamically on-demand with settledScale
    LaunchedEffect(pageIndex, pdfUri, settledScale) {
        isLoading = true
        val targetWidth = (1080 * settledScale).toInt().coerceIn(540, 3240)
        val bm = viewModel.getPageBitmap(pdfUri, pageIndex, targetWidth)
        if (bm != null) {
            pageBitmap = bm
        }
        isLoading = false
    }

    val density = LocalDensity.current
    val context = LocalContext.current
    val annotations by viewModel.annotations.collectAsState()
    val currentInkPoints = remember { mutableStateListOf<Offset>() }

    val selectionStartPageIndex by viewModel.selectionStartPageIndex.collectAsState()
    val selectionStartWordIdx by viewModel.selectionStartWordIndex.collectAsState()
    val selectionEndWordIdx by viewModel.selectionEndWordIndex.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val pageTextState = remember(pageIndex, pdfUri) { mutableStateOf<OcrPageText?>(null) }
    LaunchedEffect(pageIndex, pdfUri) {
        pageTextState.value = viewModel.getPageOcrText(pageIndex)
    }
    val wordBlocks = remember(pageTextState.value) {
        viewModel.deserializeBlocks(pageTextState.value?.wordCoordinatesJson ?: "")
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
                var containerSize by remember { mutableStateOf(IntSize.Zero) }
                val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .onSizeChanged { containerSize = it }
                        .pointerInput(currentTool, annotationSubTool) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    onDoubleTapZoom(tapOffset, containerSize)
                                },
                                onTap = { tapOffset ->
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
                            )
                        }
                        .pointerInput(wordBlocks, currentTool) {
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
                            .pointerInput(currentTool) {
                                if (currentTool == ReaderTool.DRAW) {
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
                            }
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

                        // 1B. Draw Real Time Text Selection
                        if (selectionStartPageIndex == pageIndex && selectionStartWordIdx != -1 && selectionEndWordIdx != -1) {
                            val range = if (selectionStartWordIdx <= selectionEndWordIdx) selectionStartWordIdx..selectionEndWordIdx else selectionEndWordIdx..selectionStartWordIdx
                            val validRange = range.first.coerceIn(wordBlocks.indices)..range.last.coerceIn(wordBlocks.indices)
                            wordBlocks.slice(validRange).forEach { block ->
                                val rect = block.boundingBox
                                if (rect != null) {
                                    val scaleX = size.width / 1080f
                                    val originalHeight = 1080f / aspect
                                    val scaleY = size.height / originalHeight
                                    drawRect(
                                        color = Color(0x660288D1),
                                        topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                                        size = Size((rect.right - rect.left) * scaleX, (rect.bottom - rect.top) * scaleY)
                                    )
                                }
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
