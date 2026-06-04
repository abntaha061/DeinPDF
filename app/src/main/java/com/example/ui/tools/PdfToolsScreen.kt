package com.example.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val showSuccess by viewModel.showSuccess.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val splitPdfUri by viewModel.splitPdfUri.collectAsState()
    val splitTotalPages by viewModel.splitTotalPages.collectAsState()

    var fromPageText by remember { mutableStateOf("1") }
    var toPageText by remember { mutableStateOf("") }

    LaunchedEffect(splitTotalPages) {
        if (splitTotalPages > 0) {
            toPageText = splitTotalPages.toString()
        }
    }

    // Launchers for picking documents
    val compressLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.compressPdf(it, context)
        }
    }

    val mergeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.size >= 2) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModel.mergePdfs(uris, context)
        } else if (uris.isNotEmpty()) {
            android.widget.Toast.makeText(context, "الرجاء اختيار ملفين على الأقل للدمج", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val imagesToPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModel.imagesToPdf(uris, context)
        }
    }

    val splitLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.loadPdfForSplit(it, context)
        }
    }

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
                            Text("محرك معالجة مستندات PDF الحقيقي", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "أدوات متطورة لضغط وتصدير وتعديل ملفات الـ PDF محلياً مع الحفظ التلقائي في مكتبتك.",
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
                        desc1 = "تقليل الحجم بشكل مذهل مع الحفاظ على وضوح الخطوط والقراءة.",
                        icon1 = Icons.Default.Compress,
                        color1 = AccentBlue,
                        onClick1 = { compressLauncher.launch(arrayOf("application/pdf")) },
                        
                        title2 = "دمج ملفات PDF",
                        desc2 = "تجميع ملفات PDF متعددة في ملف واحد منسق بجودة فائقة.",
                        icon2 = Icons.Default.MergeType,
                        color2 = AccentPurple,
                        onClick2 = { mergeLauncher.launch(arrayOf("application/pdf")) }
                    )

                    PdfToolItemRow(
                        title1 = "تحويل الصور إلى PDF",
                        desc1 = "تحويل صور التلخيصات والشروحات إلى مستند PDF واحد متناسق.",
                        icon1 = Icons.Default.Image,
                        color1 = Gold,
                        onClick1 = { imagesToPdfLauncher.launch(arrayOf("image/*")) },

                        title2 = "تقسيم ملف PDF",
                        desc2 = "استخراج نطاق صفحات فصول محددة وحفظها كمستند منفصل.",
                        icon2 = Icons.Default.CallSplit,
                        color2 = AccentCyan,
                        onClick2 = { splitLauncher.launch(arrayOf("application/pdf")) }
                    )
                }
            }

            // Real Loading progress overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, AccentCyan),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                color = AccentCyan,
                                trackColor = DarkBorder,
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 5.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "${(progress * 100).toInt()}%",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("جاري معالجة المستند محلياً...", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Split Pages Configuration Dialog
            if (splitTotalPages > 0 && splitPdfUri != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearSplitData() },
                    containerColor = DarkSurface,
                    title = {
                        Text(
                            "تحديد نطاق تقسيم الـ PDF",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                "عدد الصفحات الكلي في هذا الملف: $splitTotalPages صفحة.",
                                color = TextPrimary.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                            Text(
                                "حدد نطاق الصفحات التي تود استخراجها وحفظها كملف منفصل:",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = fromPageText,
                                    onValueChange = { fromPageText = it },
                                    label = { Text("من صفحة", color = AccentCyan) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = AccentCyan,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedLabelColor = AccentCyan,
                                        unfocusedLabelColor = TextMuted
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = toPageText,
                                    onValueChange = { toPageText = it },
                                    label = { Text("إلى صفحة", color = AccentCyan) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = AccentCyan,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedLabelColor = AccentCyan,
                                        unfocusedLabelColor = TextMuted
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val from = fromPageText.toIntOrNull() ?: 1
                                val to = toPageText.toIntOrNull() ?: splitTotalPages
                                viewModel.splitPdf(splitPdfUri!!, from, to, context)
                                viewModel.clearSplitData()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                        ) {
                            Text("تقسيم وحفظ", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { viewModel.clearSplitData() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // Real Success Confirmation dialog
            if (showSuccess) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissSuccess() },
                    containerColor = DarkSurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تمت العملية بنجاح", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Text(successMessage, color = TextPrimary, lineHeight = 22.sp, fontSize = 13.sp)
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissSuccess() },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حسناً", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Real Error Dialog representation
            if (errorMessage.isNotBlank()) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissError() },
                    containerColor = DarkSurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حدث خطأ", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Text(errorMessage, color = TextPrimary, fontSize = 13.sp)
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissError() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حسناً", fontWeight = FontWeight.Bold)
                        }
                    }
                )
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
