package com.example.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    // Split
    val splitPdfUri by viewModel.splitPdfUri.collectAsState()
    val splitTotalPages by viewModel.splitTotalPages.collectAsState()
    var fromPageText by remember { mutableStateOf("1") }
    var toPageText by remember { mutableStateOf("") }
    LaunchedEffect(splitTotalPages) {
        if (splitTotalPages > 0) toPageText = splitTotalPages.toString()
    }

    // Rotate
    val rotateUri by viewModel.rotateUri.collectAsState()
    var showRotateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(rotateUri) { if (rotateUri != null) showRotateDialog = true }

    // Remove Pages
    val removeUri by viewModel.removeUri.collectAsState()
    val removeTotalPages by viewModel.removeTotalPages.collectAsState()
    var removePagesText by remember { mutableStateOf("") }

    // Watermark
    val watermarkUri by viewModel.watermarkUri.collectAsState()
    var watermarkText by remember { mutableStateOf("") }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    LaunchedEffect(watermarkUri) { if (watermarkUri != null) showWatermarkDialog = true }

    // Page Numbers
    val pageNumberUri by viewModel.pageNumberUri.collectAsState()
    var showPageNumberDialog by remember { mutableStateOf(false) }
    LaunchedEffect(pageNumberUri) { if (pageNumberUri != null) showPageNumberDialog = true }

    // Launchers
    val compressLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; viewModel.compressPdf(it, context) }
    }
    val mergeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size >= 2) { uris.forEach { uri -> try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }; viewModel.mergePdfs(uris, context) }
        else if (uris.isNotEmpty()) android.widget.Toast.makeText(context, "الرجاء اختيار ملفين على الأقل", android.widget.Toast.LENGTH_LONG).show()
    }
    val imagesToPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { uris.forEach { uri -> try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }; viewModel.imagesToPdf(uris, context) }
    }
    val splitLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; viewModel.loadPdfForSplit(it, context) }
    }
    val rotateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; viewModel.loadPdfForRotate(it) }
    }
    val removeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; viewModel.loadPdfForRemovePages(it, context) }
    }
    val watermarkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; viewModel.loadPdfForWatermark(it) }
    }
    val pageNumberLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}; viewModel.loadPdfForPageNumbers(it) }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("PDF Tools", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tools_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
            ) {
                // ORGANIZE
                ToolSectionHeader("ORGANIZE", Color(0xFF6C63FF))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfToolItemRow(
                        title1 = "Merge PDFs", desc1 = "Combine multiple PDF files into one",
                        icon1 = Icons.Default.MergeType, color1 = Color(0xFF6C63FF),
                        onClick1 = { mergeLauncher.launch(arrayOf("application/pdf")) },
                        title2 = "Split PDF", desc2 = "Split a PDF into multiple files",
                        icon2 = Icons.Default.CallSplit, color2 = Color(0xFFFF9800),
                        onClick2 = { splitLauncher.launch(arrayOf("application/pdf")) }
                    )
                    PdfToolItemRow(
                        title1 = "Compress PDF", desc1 = "Reduce file size while maintaining quality",
                        icon1 = Icons.Default.Compress, color1 = Color(0xFF6C63FF),
                        onClick1 = { compressLauncher.launch(arrayOf("application/pdf")) },
                        title2 = "Rotate Pages", desc2 = "Rotate individual or all pages",
                        icon2 = Icons.Default.RotateRight, color2 = Color(0xFF6C63FF),
                        onClick2 = { rotateLauncher.launch(arrayOf("application/pdf")) }
                    )
                    ToolSingleItem(
                        title = "Remove Pages", desc = "Delete specific pages from PDF",
                        icon = Icons.Default.DeleteSweep, color = Color(0xFFEF5350),
                        onClick = { removeLauncher.launch(arrayOf("application/pdf")) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // EDIT
                ToolSectionHeader("EDIT", Color(0xFF6C63FF))
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfToolItemRow(
                        title1 = "Add Watermark", desc1 = "Add text or image watermark",
                        icon1 = Icons.Default.Opacity, color1 = Color(0xFF26C6DA),
                        onClick1 = { watermarkLauncher.launch(arrayOf("application/pdf")) },
                        title2 = "Add Page Numbers", desc2 = "Insert page numbers to PDF",
                        icon2 = Icons.Default.FormatListNumbered, color2 = Color(0xFF26C6DA),
                        onClick2 = { pageNumberLauncher.launch(arrayOf("application/pdf")) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // CONVERT
                ToolSectionHeader("CONVERT", Color(0xFF6C63FF))
                Spacer(modifier = Modifier.height(8.dp))
                ToolSingleItem(
                    title = "Image to PDF", desc = "Convert images to PDF document",
                    icon = Icons.Default.Image, color = Color(0xFF26A69A),
                    onClick = { imagesToPdfLauncher.launch(arrayOf("image/*")) }
                )

                Spacer(modifier = Modifier.height(80.dp))
            }

            // Loading Overlay
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, AccentCyan),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                progress = { progress }, color = AccentCyan,
                                trackColor = DarkBorder, modifier = Modifier.size(64.dp), strokeWidth = 5.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("${(progress * 100).toInt()}%", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("جاري معالجة المستند...", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Split Dialog
            if (splitTotalPages > 0 && splitPdfUri != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearSplitData() },
                    containerColor = DarkSurface,
                    title = { Text("Split PDF", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Total pages: $splitTotalPages", color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = fromPageText, onValueChange = { fromPageText = it },
                                    label = { Text("From", color = AccentCyan) }, singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AccentCyan, unfocusedBorderColor = DarkBorder),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = toPageText, onValueChange = { toPageText = it },
                                    label = { Text("To", color = AccentCyan) }, singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AccentCyan, unfocusedBorderColor = DarkBorder),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val from = fromPageText.toIntOrNull() ?: 1
                            val to = toPageText.toIntOrNull() ?: splitTotalPages
                            viewModel.splitPdf(splitPdfUri!!, from, to, context)
                            viewModel.clearSplitData()
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                            Text("Split", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.clearSplitData() }, border = BorderStroke(1.dp, DarkBorder)) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                )
            }

            // Rotate Dialog
            if (showRotateDialog && rotateUri != null) {
                AlertDialog(
                    onDismissRequest = { showRotateDialog = false; viewModel.clearRotateData() },
                    containerColor = DarkSurface,
                    title = { Text("Rotate Pages", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Select rotation angle:", color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(90, 180, 270).forEach { angle ->
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.rotatePages(rotateUri!!, angle, context)
                                            showRotateDialog = false
                                            viewModel.clearRotateData()
                                        },
                                        border = BorderStroke(1.dp, AccentCyan),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("${angle}°", color = AccentCyan, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        OutlinedButton(onClick = { showRotateDialog = false; viewModel.clearRotateData() }, border = BorderStroke(1.dp, DarkBorder)) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                )
            }

            // Remove Pages Dialog
            if (removeTotalPages > 0 && removeUri != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearRemoveData(); removePagesText = "" },
                    containerColor = DarkSurface,
                    title = { Text("Remove Pages", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Total pages: $removeTotalPages", color = TextPrimary)
                            Text("Enter page numbers separated by commas (e.g. 1,3,5):", color = TextMuted, fontSize = 12.sp)
                            OutlinedTextField(
                                value = removePagesText, onValueChange = { removePagesText = it },
                                label = { Text("Pages to remove", color = AccentCyan) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AccentCyan, unfocusedBorderColor = DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val pages = removePagesText.split(",").mapNotNull { it.trim().toIntOrNull() }
                            if (pages.isNotEmpty()) {
                                viewModel.removePages(removeUri!!, pages, context)
                                viewModel.clearRemoveData()
                                removePagesText = ""
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) {
                            Text("Remove", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.clearRemoveData(); removePagesText = "" }, border = BorderStroke(1.dp, DarkBorder)) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                )
            }

            // Watermark Dialog
            if (showWatermarkDialog && watermarkUri != null) {
                AlertDialog(
                    onDismissRequest = { showWatermarkDialog = false; viewModel.clearWatermarkData(); watermarkText = "" },
                    containerColor = DarkSurface,
                    title = { Text("Add Watermark", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Enter watermark text:", color = TextPrimary)
                            OutlinedTextField(
                                value = watermarkText, onValueChange = { watermarkText = it },
                                label = { Text("Watermark text", color = AccentCyan) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AccentCyan, unfocusedBorderColor = DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (watermarkText.isNotBlank()) {
                                viewModel.addWatermark(watermarkUri!!, watermarkText, context)
                                showWatermarkDialog = false
                                viewModel.clearWatermarkData()
                                watermarkText = ""
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26C6DA))) {
                            Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showWatermarkDialog = false; viewModel.clearWatermarkData(); watermarkText = "" }, border = BorderStroke(1.dp, DarkBorder)) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                )
            }

            // Page Numbers Dialog
            if (showPageNumberDialog && pageNumberUri != null) {
                AlertDialog(
                    onDismissRequest = { showPageNumberDialog = false; viewModel.clearPageNumberData() },
                    containerColor = DarkSurface,
                    title = { Text("Add Page Numbers", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Page numbers will be added at the bottom center of each page.", color = TextPrimary)
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.addPageNumbers(pageNumberUri!!, context)
                            showPageNumberDialog = false
                            viewModel.clearPageNumberData()
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26C6DA))) {
                            Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showPageNumberDialog = false; viewModel.clearPageNumberData() }, border = BorderStroke(1.dp, DarkBorder)) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                )
            }

            // Success Dialog
            if (showSuccess) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissSuccess() },
                    containerColor = DarkSurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Success", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = { Text(successMessage, color = TextPrimary, fontSize = 13.sp) },
                    confirmButton = {
                        Button(onClick = { viewModel.dismissSuccess() }, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), modifier = Modifier.fillMaxWidth()) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Error Dialog
            if (errorMessage.isNotBlank()) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissError() },
                    containerColor = DarkSurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Error", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    },
                    text = { Text(errorMessage, color = TextPrimary, fontSize = 13.sp) },
                    confirmButton = {
                        Button(onClick = { viewModel.dismissError() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), modifier = Modifier.fillMaxWidth()) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ToolSectionHeader(title: String, color: Color) {
    Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
}

@Composable
fun ToolSingleItem(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(desc, color = TextMuted, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
fun PdfToolItemRow(
    title1: String, desc1: String, icon1: ImageVector, color1: Color, onClick1: () -> Unit,
    title2: String, desc2: String, icon2: ImageVector, color2: Color, onClick2: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.weight(1f).clickable { onClick1() }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color1.copy(alpha = 0.15f)).padding(8.dp)) {
                    Icon(icon1, contentDescription = null, tint = color1, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(title1, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc1, color = TextMuted, fontSize = 11.sp, minLines = 2, maxLines = 3)
            }
        }
        if (title2.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f).clickable { onClick2() }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color2.copy(alpha = 0.15f)).padding(8.dp)) {
                        Icon(icon2, contentDescription = null, tint = color2, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(title2, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(desc2, color = TextMuted, fontSize = 11.sp, minLines = 2, maxLines = 3)
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
