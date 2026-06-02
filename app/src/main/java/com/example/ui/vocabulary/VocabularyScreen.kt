package com.example.ui.vocabulary

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.speech.tts.TextToSpeech
import android.content.Intent
import android.net.Uri
import java.util.Locale
import com.example.data.model.VocabularyWord
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val vocabulary by viewModel.vocabulary.collectAsState()
    val dueForReview by viewModel.dueForReview.collectAsState()

    var ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    
    DisposableEffect(context) {
        val tempTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRef.value?.language = Locale.GERMAN
            }
        }
        ttsRef.value = tempTts
        onDispose {
            tempTts.shutdown()
        }
    }

    val speakWord = { text: String ->
        val hasArabic = text.any { it in '\u0600'..'\u06FF' }
        ttsRef.value?.language = if (hasArabic) Locale("ar") else Locale.GERMAN
        ttsRef.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab_tts")
    }

    val openArabdict = { wordStr: String ->
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.arabdict.com/de/deutsch-arabisch/${Uri.encode(wordStr)}")
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var reviewModeActive by remember { mutableStateOf(false) }

    // Filter matching results
    val displayedVocabulary = if (searchQuery.isBlank()) {
        vocabulary
    } else {
        vocabulary.filter {
            it.originalWord.contains(searchQuery, ignoreCase = true) ||
            it.translatedWord.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("قاموس مفرداتي", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vocabulary_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Flashcards banner review trigger if any words are due
                if (dueForReview.isNotEmpty() && !reviewModeActive) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, Gold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("vocabulary_review_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "لديك ${dueForReview.size} كلمات جاهزة للمراجعة!",
                                    color = Gold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "استخدم المراجعة التكرارية المتباعدة لتثبيت الحفظ.",
                                    color = TextPrimary.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = { reviewModeActive = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("راجع الآن", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Plain statistics row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${vocabulary.size}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AccentBlue)
                            Text("إجمالي الكلمات", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val masteredCount = vocabulary.count { it.isMastered }
                            Text("$masteredCount", fontSize = 24.sp, fontWeight = FontWeight.Black, color = SuccessGreen)
                            Text("متقنة تماماً", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في الكلمات الألمانية أو العربية...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                // List
                if (displayedVocabulary.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextMuted)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("لا توجد كلمات حالياً", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("انقر على الكلمات أثناء القراءة لإضافتها هنا.", color = TextMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedVocabulary) { word ->
                            VocabularyItemCard(
                                word = word,
                                onClick = {
                                    speakWord(word.originalWord)
                                    openArabdict(word.originalWord)
                                },
                                onDictClick = {
                                    speakWord(word.originalWord)
                                    openArabdict(word.originalWord)
                                }
                            )
                        }
                    }
                }
            }

            // Interactive Flashcard Review Dialog overlay
            if (reviewModeActive) {
                SpacedRepetitionQuizOverlay(
                    dueWords = dueForReview,
                    onComplete = { 
                        reviewModeActive = false
                        viewModel.loadStats()
                    },
                    onReviewWord = { word, correct ->
                        viewModel.markWordReviewed(word, correct)
                    }
                )
            }
        }
    }
}

@Composable
fun VocabularyItemCard(
    word: VocabularyWord,
    onClick: () -> Unit,
    onDictClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, if (word.isMastered) SuccessGreen.copy(alpha = 0.5f) else DarkBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        word.originalWord,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    IconButton(
                        onClick = onDictClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "قاموس Arabdict",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Text(
                    word.translatedWord,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    word.partOfSpeech?.let { pos ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(pos, fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (word.isMastered) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SuccessGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("متقنة", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Text(
                    "المراجعة القادمة: متباعدة",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            word.example?.let { ex ->
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = DarkBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    ex,
                    fontSize = 12.sp,
                    color = TextPrimary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SpacedRepetitionQuizOverlay(
    dueWords: List<VocabularyWord>,
    onComplete: () -> Unit,
    onReviewWord: (VocabularyWord, Boolean) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var revealAnswer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (dueWords.isEmpty() || currentIndex >= dueWords.size) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, SuccessGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(72.dp), tint = Gold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("مبروك! أحسنت العمل", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "لقد قمت بمراجعة جميع الكلمات المطلوبة لهذا اليوم بنجاح.",
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إنهاء الجلسة", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val currentWord = dueWords[currentIndex]

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Gold),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiz_card")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "مراجعة: ${currentIndex + 1}/${dueWords.size}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onComplete) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = ErrorRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        currentWord.originalWord,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    AnimatedVisibility(
                        visible = revealAnswer,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                currentWord.translatedWord,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                textAlign = TextAlign.Center
                            )
                            currentWord.partOfSpeech?.let {
                                Text(
                                    it,
                                    fontSize = 13.sp,
                                    color = AccentBlue,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(50.dp))

                    if (!revealAnswer) {
                        Button(
                            onClick = { revealAnswer = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("كشف الترجمة", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    onReviewWord(currentWord, false)
                                    revealAnswer = false
                                    currentIndex++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("لم أذكرها", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    onReviewWord(currentWord, true)
                                    revealAnswer = false
                                    currentIndex++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تذكرتها!", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
