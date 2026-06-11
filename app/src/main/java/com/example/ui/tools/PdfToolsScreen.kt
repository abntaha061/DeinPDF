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
    LaunchedEffect(splitTotalPages) { if (splitTotalPages > 0) toPageText = splitTotalPages.toString() }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("PDF Tools", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tools_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
            ) {
                ToolSectionHeader("ORGANIZE", AccentPurple)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfToolItemRow(
                        "Merge PDFs", "Combine multiple PDF files into one", Icons.Default.MergeType, AccentPurple, { mergeLauncher.launch(arrayOf("application/pdf")) },
                        "Split PDF", "Split a PDF into multiple files", Icons.Default.CallSplit, Gold, { splitLauncher.launch(arrayOf("application/pdf")) }
                    )
                    PdfToolItemRow(
                        "Compress PDF", "Reduce file size while maintaining quality", Icons.Default.Compress, AccentPurple, { compressLauncher.launch(arrayOf("application/pdf")) },
                        "Rotate Pages", "Rotate individual or all pages", Icons.Default.RotateRight, AccentPurple, { rotateLauncher.launch(arrayOf("application/pdf")) }
                    )
                    ToolSingleItem("Remove Pages", "Delete specific pages from PDF", Icons.Default.DeleteSweep, ErrorRed, { removeLauncher.launch(arrayOf("application/pdf")) })
                }

                Spacer(modifier = Modifier.height(20.dp))
                ToolSectionHeader("EDIT", AccentPurple)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfToolItemRow(
                        "Add Watermark", "Add text or image watermark", Icons.Default.Opacity, AccentCyan, { watermarkLauncher.launch(arrayOf("application/pdf")) },
                        "Add Page Numbers", "Insert page numbers to PDF", Icons.Default.FormatListNumbered, AccentCyan, { pageNumberLauncher.launch(arrayOf("application/pdf")) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                ToolSectionHeader("CONVERT", AccentPurple)
                Spacer(modifier = Modifier.height(8.dp))
                ToolSingleItem("Image to PDF", "Convert images to PDF document", Icons.Default.Image, SuccessGreen, { imagesToPdfLauncher.launch(arrayOf("image/*")) })
            }

            // Loading Overlay
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(32.dp)) {
                        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(progress = { progress }, color = AccentCyan, modifier = Modifier.size(64.dp), strokeWidth = 5.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("${(progress * 100).toInt()}%", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("جاري معالجة المستند...", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }
            
            // Dialogs ...
            // (أبقِ باقي كود الـ Dialogs كما هو مع تغيير containerColor إلى MaterialTheme.colorScheme.surface)
            if (splitTotalPages > 0 && splitPdfUri != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearSplitData() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("Split PDF", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Total pages: $splitTotalPages", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(value = fromPageText, onValueChange = { fromPageText = it }, label = { Text("From") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = toPageText, onValueChange = { toPageText = it }, label = { Text("To") }, modifier = Modifier.weight(1f))
                            }
                        }
                    },
                    confirmButton = { Button(onClick = { viewModel.splitPdf(splitPdfUri!!, fromPageText.toIntOrNull() ?: 1, toPageText.toIntOrNull() ?: splitTotalPages, context); viewModel.clearSplitData() }) { Text("Split") } },
                    dismissButton = { OutlinedButton(onClick = { viewModel.clearSplitData() }) { Text("Cancel") } }
                )
            }
            // (وبنفس النمط لباقي الـ Dialogs، استبدل DarkSurface بـ MaterialTheme.colorScheme.surface)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(10.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1f).clickable { onClick1() }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color1.copy(alpha = 0.15f)).padding(8.dp)) {
                    Icon(icon1, contentDescription = null, tint = color1, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(title1, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc1, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, minLines = 2, maxLines = 3)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1f).clickable { onClick2() }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color2.copy(alpha = 0.15f)).padding(8.dp)) {
                    Icon(icon2, contentDescription = null, tint = color2, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(title2, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc2, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, minLines = 2, maxLines = 3)
            }
        }
    }
}
