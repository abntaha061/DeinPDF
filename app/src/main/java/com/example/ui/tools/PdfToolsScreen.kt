package com.example.ui.tools

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit
) {
    var activeToolName by remember { mutableStateOf<String?>(null) }
    var operationProgress by remember { mutableStateOf(0f) }
    var isOperating by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("أدوات PDF الشاملة", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tools_back_button")) {
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AccentPurple),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BuildCircle, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("محرك معالجة مستندات PDF", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "أدوات مساعدة لضغط وتصدير وتعديل ملفات الـ PDF محلياً بدقة متناهية.",
                            color = TextPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    "الأدوات المتوفرة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AccentCyan,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Grid layout details
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PdfToolItemRow(
                        title1 = "ضغط حجم الملف",
                        desc1 = "تقليل حجم مستند PDF مع الحفاظ على وضوح المستند للمذاكرة.",
                        icon1 = Icons.Default.Compress,
                        color1 = AccentBlue,
                        onClick1 = { activeToolName = "ضغط حجم الملف" },
                        
                        title2 = "دمج ملفات PDF",
                        desc2 = "تجميع عدة ملفات PDF منفصلة في ملف واحد منسق.",
                        icon2 = Icons.Default.MergeType,
                        color2 = AccentPurple,
                        onClick2 = { activeToolName = "دمج ملفات PDF" }
                    )

                    PdfToolItemRow(
                        title1 = "تحويل الصور إلى PDF",
                        desc1 = "تحويل صور التلخيصات والشات والورق إلى كتاب PDF الكتروني.",
                        icon1 = Icons.Default.CardMembership, // placeholder for layout
                        color1 = Gold,
                        onClick1 = { activeToolName = "تحويل الصور إلى PDF" },

                        title2 = "تقسيم ملف PDF",
                        desc2 = "استخراج صفحات محددة وفصول من الكتاب وتصديرها بشكل منفصل.",
                        icon2 = Icons.Default.CallSplit,
                        color2 = AccentCyan,
                        onClick2 = { activeToolName = "تقسيم ملف PDF" }
                    )
                }
            }

            // Processing overlay dialog for mock actions
            if (activeToolName != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, AccentCyan),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center)
                        .testTag("tool_action_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            activeToolName!!,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (!isOperating) {
                            Text(
                                "هل ترغب في بدء تشغيل أداة [ $activeToolName ] بشكل فوري ومحلي؟",
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { activeToolName = null },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إلغاء")
                                }
                                Button(
                                    onClick = { 
                                        isOperating = true
                                        // Mock operation progress loop
                                        operationProgress = 0f
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                    modifier = Modifier.weight(1f).testTag("start_tool_button")
                                ) {
                                    Text("بدء العملية", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Progress bar
                            LaunchedEffect(isOperating) {
                                while (operationProgress < 1.0f) {
                                    kotlinx.coroutines.delay(100)
                                    operationProgress += 0.05f
                                }
                                isOperating = false
                                showSuccessDialog = true
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = operationProgress,
                                color = AccentCyan,
                                trackColor = DarkBorder,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "جاري معالجة المستند محلياً... ${(operationProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Success dialog confirmation
            if (showSuccessDialog) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, SuccessGreen),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = SuccessGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("تمت العملية بنجاح!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("تم تصنيف وحفظ الملف الجديد مضافاً إلى مكتبتك بنجاح.", color = TextMuted, textAlign = TextAlign.Center, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { 
                                showSuccessDialog = false
                                activeToolName = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حسناً", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfToolItemRow(
    title1: String,
    desc1: String,
    icon1: ImageVector,
    color1: Color,
    onClick1: () -> Unit,

    title2: String,
    desc2: String,
    icon2: ImageVector,
    color2: Color,
    onClick2: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .weight(1f)
                .clickable { onClick1() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color1.copy(alpha = 0.15f))
                        .padding(8.dp)
                ) {
                    Icon(icon1, contentDescription = null, tint = color1)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title1, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc1, color = TextMuted, fontSize = 11.sp, minLines = 2, maxLines = 3)
            }
        }

        // Card 2
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .weight(1f)
                .clickable { onClick2() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color2.copy(alpha = 0.15f))
                        .padding(8.dp)
                ) {
                    Icon(icon2, contentDescription = null, tint = color2)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title2, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc2, color = TextMuted, fontSize = 11.sp, minLines = 2, maxLines = 3)
            }
        }
    }
}
