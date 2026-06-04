package com.example.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PdfFile
import com.example.ui.components.SearchBar
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPdf: (Uri, Long) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToVocabulary: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val recentFiles by viewModel.recentFiles.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val homeState by viewModel.homeState.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    LaunchedEffect(homeState.scanMessage) {
        if (homeState.scanMessage.isNotBlank()) {
            android.widget.Toast.makeText(context, homeState.scanMessage, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        android.util.Log.d("SCAN_DEBUG", "manageStorageLauncher callback triggered")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val isGranted = android.os.Environment.isExternalStorageManager()
            android.util.Log.d("SCAN_DEBUG", "After settings callback - isExternalStorageManager = $isGranted")
            if (isGranted) {
                viewModel.scanForPdfs()
            } else {
                android.widget.Toast.makeText(context, "لم يتم منح إذن الوصول الكامل للملفات", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("SCAN_DEBUG", "permissionLauncher callback triggered - isGranted = $isGranted")
        if (isGranted) {
            viewModel.scanForPdfs()
        } else {
            android.widget.Toast.makeText(context, "يرجى منح إذن الوصول للملفات للبدء بالبحث", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val checkAndScan = {
        android.util.Log.d("SCAN_DEBUG", "checkAndScan triggered")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val isManager = android.os.Environment.isExternalStorageManager()
            android.util.Log.d("SCAN_DEBUG", "Current isExternalStorageManager = $isManager")
            if (isManager) {
                viewModel.scanForPdfs()
            } else {
                try {
                    android.util.Log.d("SCAN_DEBUG", "Requesting ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    android.util.Log.d("SCAN_DEBUG", "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION failed, fallback to ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION: ${e.message}")
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        } else {
            val permission = android.Manifest.permission.READ_EXTERNAL_STORAGE
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            android.util.Log.d("SCAN_DEBUG", "Pre-R API check - READ_EXTERNAL_STORAGE isGranted = $isGranted")

            if (isGranted) {
                viewModel.scanForPdfs()
            } else {
                android.util.Log.d("SCAN_DEBUG", "Launching standard READ_EXTERNAL_STORAGE permission request")
                permissionLauncher.launch(permission)
            }
        }
    }

    // System File picker (SAF)
    val filePicker = rememberLauncherForActivityResult(
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
            viewModel.addPdfFromUri(it) { file ->
                file?.let { f -> onOpenPdf(Uri.parse(f.uri), f.id) }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "مكتبة PDF الشاملة",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }) {
                            Icon(
                                if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "تغيير العرض",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkSurface,
                        titleContentColor = Color.White
                    )
                )

                // Search bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = AccentBlue
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("الرئيسية") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        unselectedIconColor = TextMuted,
                        selectedTextColor = AccentBlue,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        onNavigateToLibrary()
                    },
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text("المكتبة") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        unselectedIconColor = TextMuted,
                        selectedTextColor = AccentBlue,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        onNavigateToBookmarks()
                    },
                    icon = { Icon(Icons.Default.Bookmark, null) },
                    label = { Text("الإشارات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        unselectedIconColor = TextMuted,
                        selectedTextColor = AccentBlue,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        onNavigateToVocabulary()
                    },
                    icon = { Icon(Icons.Default.Translate, null) },
                    label = { Text("المفردات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        unselectedIconColor = TextMuted,
                        selectedTextColor = AccentBlue,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePicker.launch(arrayOf("application/pdf")) },
                containerColor = AccentBlue,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("فتح ملف PDF") }
            )
        }
    ) { padding ->
        if (searchQuery.isNotBlank()) {
            SearchResults(
                results = searchResults,
                onOpen = { f -> onOpenPdf(Uri.parse(f.uri), f.id) },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Stats and Quick access
                item {
                    StatsRow(
                        totalFiles = recentFiles.size,
                        favCount = favorites.size
                    )
                }

                // Quick Navigation Shortcuts
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard("🛠️ أدوات PDF", AccentPurple, Modifier.weight(1f)) { onNavigateToTools() }
                        QuickActionCard("📊 الإحصائيات", AccentCyan, Modifier.weight(1f)) { onNavigateToStats() }
                    }
                }

                // Favorites horizontally scrolled row
                if (favorites.isNotEmpty()) {
                    item {
                        SectionTitle("⭐ المفضلة")
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(favorites) { file ->
                                FavoriteCard(
                                    file = file,
                                    onClick = { onOpenPdf(Uri.parse(file.uri), file.id) }
                                )
                            }
                        }
                    }
                }

                // Recent books / documents
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle("الأخيرة 🕐")
                        ScanButton(
                            isScanning = homeState.isScanning,
                            onScan = { checkAndScan() }
                        )
                    }
                }

                if (homeState.recentFiles.isEmpty()) {
                    item {
                        EmptyRecentState(
                            isScanning = homeState.isScanning,
                            onScan = { checkAndScan() }
                        )
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(homeState.recentFiles) { file ->
                                FavoriteCard(
                                    file = file,
                                    onClick = { onOpenPdf(Uri.parse(file.uri), file.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
fun StatsRow(totalFiles: Int, favCount: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("📁", "$totalFiles", "مسجل", AccentBlue, Modifier.weight(1f))
        StatCard("⭐", "$favCount", "مفضلة", Gold, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(emoji: String, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(emoji, fontSize = 24.sp)
            Column {
                Text(value, fontWeight = FontWeight.Black, fontSize = 22.sp, color = color)
                Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = Color.White,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun FavoriteCard(file: PdfFile, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (file.thumbnailPath.isNotBlank()) {
                    AsyncImage(
                        model = file.thumbnailPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("📄", fontSize = 28.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                file.name.removeSuffix(".pdf"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${(file.readProgress * 100).toInt()}% مقروء",
                style = MaterialTheme.typography.labelSmall,
                color = Gold
            )
        }
    }
}

@Composable
fun PdfGridView(
    files: List<PdfFile>,
    onOpen: (PdfFile) -> Unit,
    onFavorite: (PdfFile) -> Unit,
    onDelete: (PdfFile) -> Unit
) {
    val rows = files.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { file ->
                    PdfGridCard(
                        file = file,
                        modifier = Modifier.weight(1f),
                        onOpen = { onOpen(file) },
                        onFavorite = { onFavorite(file) },
                        onDelete = { onDelete(file) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfGridCard(
    file: PdfFile,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(AccentBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (file.thumbnailPath.isNotBlank()) {
                    AsyncImage(
                        model = file.thumbnailPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("📄", fontSize = 36.sp)
                }
                // Favorite star overlay
                if (file.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Gold),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("★", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                // Progress bar
                if (file.readProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { file.readProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = AccentBlue,
                        trackColor = Color.Transparent
                    )
                }
            }

            Column(Modifier.padding(10.dp)) {
                Text(
                    file.name.removeSuffix(".pdf"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${file.pageCount} صفحة",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        formatSize(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (file.isFavorite) "إزالة من المفضلة" else "إضافة للمفضلة") },
                onClick = { onFavorite(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Star, null) }
            )
            DropdownMenuItem(
                text = { Text("حذف الملف", color = ErrorRed) },
                onClick = { onDelete(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfListItem(
    file: PdfFile,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { showMenu = true }),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (file.thumbnailPath.isNotBlank()) {
                    AsyncImage(
                        model = file.thumbnailPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("📄", fontSize = 22.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    file.name.removeSuffix(".pdf"),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${file.pageCount} صفحة", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(formatSize(file.size), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    if (file.readProgress > 0f)
                        Text("${(file.readProgress * 100).toInt()}% مقروء", style = MaterialTheme.typography.labelSmall, color = AccentBlue)
                }
            }

            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, null, tint = TextMuted)
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (file.isFavorite) "إزالة من المفضلة" else "إضافة للمفضلة") },
                onClick = { onFavorite(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Star, null) }
            )
            DropdownMenuItem(
                text = { Text("حذف الملف", color = ErrorRed) },
                onClick = { onDelete(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) }
            )
        }
    }
}

@Composable
fun SearchResults(results: List<PdfFile>, onOpen: (PdfFile) -> Unit, modifier: Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (results.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد نتائج مطابقة", color = TextMuted)
                }
            }
        } else {
            items(results) { file ->
                PdfListItem(file = file, onOpen = { onOpen(file) }, onFavorite = {}, onDelete = {})
            }
        }
    }
}

@Composable
fun EmptyState(onPickFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("📁", fontSize = 64.sp)
        Text("لا توجد ملفات حديثة", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("اضغط على الزر التالي لاستيراد وتصفح ملفات الـ PDF الخاصة بك", color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp)
        Button(
            onClick = onPickFile,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("فتح أول ملف PDF")
        }
    }
}

fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    }
}

enum class ViewMode { GRID, LIST }
