package com.example.ui.reader

import android.app.Activity
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
            
            val centroidInAspected = Offset(tapInContainerX, tapInContainerY) - Offset(width / 2f, height / 2f)
            val maxX = (width * 1.2f) / 2f
            val maxY = (height * 1.2f) / 2f
            
            docOffset = Offset(
                x = (-centroidInAspected.x * 1.2f).coerceIn(-maxX, maxX),
                y = (-centroidInAspected.y * 1.2f).coerceIn(-maxY, maxY)
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
                visible = isToolbarVisible || isEditingMode,
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
                visible = isToolbarVisible || isEditingMode,
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
                        .pointerInput(docScale) {
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
                                                val centroidInAspected = centroid - Offset(containerSize.width / 2f, containerSize.height / 2f)
                                                val newOffset = (docOffset - centroidInAspected) * effectiveZoom + centroidInAspected + panChange

                                                val maxX = (containerSize.width * (newScale - 1f)) / 2f
                                                val maxY = (containerSize.height * (newScale - 1f)) / 2f
                                                docOffset = Offset(
                                                    x = newOffset.x.coerceIn(-maxX, maxX),
                                                    y = newOffset.y.coerceIn(-maxY, maxY)
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
                                            val maxX = (containerSize.width * (docScale - 1f)) / 2f
                                            val maxY = (containerSize.height * (docScale - 1f)) / 2f

                                            val proposedX = docOffset.x + panChange.x
                                            val proposedY = docOffset.y + panChange.y

                                            val boundedX = proposedX.coerceIn(-maxX, maxX)
                                            val boundedY = proposedY.coerceIn(-maxY, maxY)

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
                            translationY = currentOffsetY
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
                            onValueChange = { viewModel.search(it) },
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
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                translationResult?.let { result ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, Gold),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
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
                                        onClick = { viewModel.speak(result.originalWord, context) },
                                        modifier = Modifier.testTag("speak_vocal_word_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "استماع",
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
                                            color = Color.White
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
    onStickyNoteClick: (PdfAnnotation) -> Unit
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Load page bitmap dynamically on-demand with settledScale
    LaunchedEffect(pageIndex, pdfUri, settledScale) {
        isLoading = true
        val targetWidth = (1080 * settledScale).toInt().coerceIn(540, 3240)
        pageBitmap = viewModel.getPageBitmap(pdfUri, pageIndex, targetWidth)
        isLoading = false
    }

    val density = LocalDensity.current
    val context = LocalContext.current
    val annotations by viewModel.annotations.collectAsState()
    val currentInkPoints = remember { mutableStateListOf<Offset>() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
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
                                            viewModel.translateWordAtOffset(tapOffset, canvasSize, pageIndex, context)
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
                                            viewModel.translateWordAtOffset(tapOffset, canvasSize, pageIndex, context)
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
