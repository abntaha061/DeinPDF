package com.example.ui.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.PdfReaderApp
import com.example.ui.theme.*
import com.example.viewmodel.ReaderViewModel

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
    
    // Create ReaderViewModel manually using Global Manual Dependency Injection container
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
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()

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
    val showToc by viewModel.showToc.collectAsState()
    val showQaChat by viewModel.showQaChat.collectAsState()

    var showTtsSpeedDialog by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Coroutine to render dynamically
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage, pdfUri) {
        isLoadingPage = true
        pageBitmap = viewModel.getPageBitmap(pdfUri, currentPage)
        isLoadingPage = false
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
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

                        // Bookmark Check/Toggle
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
                            // AI Summary
                            val textOnPage = "صفحة رقم ${currentPage + 1} من كتاب ألماني يحتوي على مفردات وتدريبات عملية."
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
                visible = isToolbarVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Slider / Tracker Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("السابق", color = Color.White, fontSize = 12.dp.value.sp, modifier = Modifier.clickable {
                                if (currentPage > 0) viewModel.setPage(currentPage - 1)
                            })
                            
                            Slider(
                                value = if (pageCount > 1) currentPage.toFloat() / (pageCount - 1) else 0f,
                                onValueChange = { value ->
                                    val targetPage = (value * (pageCount - 1)).toInt()
                                    viewModel.setPage(targetPage)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            
                            Text("التالي", color = Color.White, fontSize = 12.dp.value.sp, modifier = Modifier.clickable {
                                if (currentPage < pageCount - 1) viewModel.setPage(currentPage + 1)
                            })
                        }

                        // Toolbar controls
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            // View Mode Toggle
                            IconButton(onClick = { viewModel.showViewModeDialog() }) {
                                Icon(
                                    when (viewMode) {
                                        ViewMode.CONTINUOUS -> Icons.Default.MenuBook
                                        ViewMode.SINGLE -> Icons.Default.Layers
                                        ViewMode.HORIZONTAL -> Icons.Default.SwapHoriz
                                    },
                                    contentDescription = "طريقة العرض",
                                    tint = AccentBlue
                                )
                            }

                            // Zoom Out
                            IconButton(onClick = { viewModel.zoomOut() }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "تكبير", tint = Color.White)
                            }

                            // Zoom State Indicator
                            Text(
                                "${(zoomLevel * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Zoom In
                            IconButton(onClick = { viewModel.zoomIn() }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "تصغير", tint = Color.White)
                            }

                            // TTS play/pause
                            IconButton(onClick = { 
                                val sampleGerman = "Guten Tag! Willkommen in der deutschen Sprachausbildung."
                                viewModel.toggleTts(context, sampleGerman)
                            }, modifier = Modifier.testTag("reader_tts_toggle")) {
                                Icon(
                                    if (isTtsPlaying) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                    contentDescription = "استماع للنطق",
                                    tint = if (isTtsPlaying) SuccessGreen else Color.White
                                )
                            }

                            // Set translating word selection mode
                            IconButton(onClick = { viewModel.setTool(ReaderTool.TRANSLATE) }) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = "ترجمة المفردات باللمس",
                                    tint = if (currentTool == ReaderTool.TRANSLATE) Gold else Color.White
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
            // Main Pdf Workspace Render
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewModel.toggleToolbar() }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
            ) {
                if (isLoadingPage) {
                    CircularProgressIndicator(color = AccentBlue)
                } else {
                    pageBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "محتوى الصفحة PDF",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures { clickOffset ->
                                        if (currentTool == ReaderTool.TRANSLATE) {
                                            viewModel.translateWordAtOffset(clickOffset, currentPage)
                                        }
                                    }
                                }
                        )
                    } ?: Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "خطأ تحميل", modifier = Modifier.size(80.dp), tint = TextMuted)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("خطأ في قراءة ملف PDF", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("يرجى التأكد من أن الملف يدعم القراءة.", color = TextMuted, textAlign = TextAlign.Center)
                    }
                }
            }

            // Search Panel Overlay
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

            // Translation Popup detailed view. Displays: Pronunciation, word breakdown, add vocabulary action
            AnimatedVisibility(
                visible = showTranslationCard && translationResult != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
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
                                // Close button
                                IconButton(onClick = { viewModel.hideTranslation() }) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextMuted)
                                }
                                
                                Text(
                                    "ترجمة ف glossary",
                                    fontSize = 12.sp,
                                    color = Gold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Original Vocab & Translation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        result.originalWord,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    result.phonetics?.let { phon ->
                                        Text(
                                            phon,
                                            fontSize = 13.sp,
                                            color = AccentCyan,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                
                                Icon(Icons.Default.SwapHoriz, contentDescription = "إلى اللغات", tint = TextMuted)
                                
                                Text(
                                    result.translatedText,
                                    fontSize = 22.sp,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                )
                            }
                            
                            // Word class / POS label
                            result.partOfSpeech?.let { pos ->
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

                            // Examples section
                            result.example?.let { ex ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkCard)
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    Column {
                                        Text("مثال للاستخدام:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(ex, color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Add button / Close
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Add vocab action
                                Button(
                                    onClick = { 
                                        viewModel.saveWord(result)
                                        // Auto update list
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                    modifier = Modifier.weight(1f).testTag("btn_add_vocabulary")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إضافة لمفرداتي المراجعة", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                // Pronunciation trigger
                                OutlinedButton(
                                    onClick = { viewModel.speak(result.originalWord, context) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, AccentCyan)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = "استماع", tint = AccentCyan)
                                }
                            }
                        }
                    }
                }
            }

            // Q&A Chat bottom sheet overlay (Powered by Gemini model)
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
                        var chatAnswer by remember { mutableStateOf("مرحباً بك! أنا مساعد القراءة الذكي. اسألني عن معاني الكلمات، أو لخص قواعد الصفحة الحالية.") }
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

                        // Response panel
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

                        // Input field
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = chatQuestion,
                                onValueChange = { chatQuestion = it },
                                placeholder = { Text("مثال: ما هي الأفعال التي تتطلب حالة Dativ؟", fontSize = 12.sp, color = TextMuted) },
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
                                            val fullText = "قواعد اللغة الألمانية الاساسية تتضمن Nominativ Akkusativ Dativ."
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

            // AI summary box popup
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
        }
    }
}
