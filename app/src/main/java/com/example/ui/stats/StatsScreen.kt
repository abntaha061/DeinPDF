package com.example.ui.stats

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.ReadHistory
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val readHistory by viewModel.readHistory.collectAsState()
    val weeklyReadTimeMin by viewModel.weeklyReadTimeMin.collectAsState()
    val weeklyPages by viewModel.weeklyPages.collectAsState()
    val vocabulary by viewModel.vocabulary.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStats()
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("إحصائيات القراءة والتعلم", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("stats_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentCyan.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AccentCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(48.dp), tint = AccentCyan)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("نظرة عامة على أدائك هذا الأسبوع", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Text("أنت تقرأ بمعدل متميز! استمر في الالتزام اليومي.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick metrics cards grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = AccentCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$weeklyReadTimeMin دقيقة", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Text("وقت القراءة الأسبوعي", fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = AccentBlue)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$weeklyPages صفحة", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Text("الصفحات المقروءة هذا الأسبوع", fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // Vocabulary analytics section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Gold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("مؤشرات الحفظ والمفردات", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        val mastered = vocabulary.count { it.isMastered }
                        val inProgress = vocabulary.size - mastered

                        // Progress bar for mastering vocabularies
                        val progressFraction = if (vocabulary.isNotEmpty()) mastered.toFloat() / vocabulary.size.toFloat() else 0f
                        Text(
                            "نسبة الكلمات المتقنة تماماً: ${(progressFraction * 100).toInt()}%",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = progressFraction,
                            color = SuccessGreen,
                            trackColor = DarkBorder,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("$inProgress", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gold)
                                Text("كلمات تحت المراجعة", fontSize = 11.sp, color = TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$mastered", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                Text("كلمات تم حفظها كلياً", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // History log header
            item {
                Text(
                    "جلسات القراءة السابقة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AccentCyan,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // History lists
            if (readHistory.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.HistoryToggleOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = TextMuted)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("لا توجد سجلات حالياً", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("ابدأ بقراءة الكتب لعرض إحصائيات جلساتك المخصصة.", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(readHistory) { session ->
                    ReadHistoryItemRow(session = session)
                }
            }
        }
    }
}

@Composable
fun ReadHistoryItemRow(session: ReadHistory) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.15f))
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = AccentBlue)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.pdfName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "المدة: ${session.durationMs / 1000} ثانية",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        "•",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        "${session.pagesRead} صفحة",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
            
            // Format nice simple dates
            val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            Text(
                df.format(Date(session.openedAt)),
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
