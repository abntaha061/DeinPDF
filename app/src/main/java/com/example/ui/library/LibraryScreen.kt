package com.example.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PdfFile
import com.example.ui.components.SearchBar
import com.example.ui.home.PdfGridCard
import com.example.ui.home.PdfListItem
import com.example.ui.home.ViewMode
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPdf: (Uri, Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val allFiles by viewModel.allFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var selectedCategory by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf(SortMode.DATE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    val categories = listOf(
        "all" to "الكل",
        "documents" to "📄 مستندات",
        "books" to "📚 كتب",
        "reports" to "📊 تقارير",
        "tests" to "📝 اختبارات"
    )

    val displayFiles = remember(allFiles, selectedCategory, sortMode, searchQuery, searchResults) {
        val base = if (searchQuery.isBlank()) {
            if (selectedCategory == "all") allFiles else allFiles.filter { it.category == selectedCategory }
        } else searchResults

        when (sortMode) {
            SortMode.DATE -> base.sortedByDescending { it.lastOpened }
            SortMode.NAME -> base.sortedBy { it.name }
            SortMode.SIZE -> base.sortedByDescending { it.size }
            SortMode.PROGRESS -> base.sortedByDescending { it.readProgress }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = { Text("المكتبة الكاملة", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }) {
                            Icon(
                                if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, null, tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                                Text("ترتيب حسب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                SortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = { sortMode = mode; showSortMenu = false },
                                        leadingIcon = {
                                            if (sortMode == mode) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ScrollableTabRow(
                    selectedTabIndex = categories.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 12.dp,
                    indicator = {},
                    divider = {}
                ) {
                    categories.forEach { (key, label) ->
                        val selected = selectedCategory == key
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = key },
                            label = { Text(label, fontSize = 13.sp) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (displayFiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📂", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("لا توجد ملفات في هذه الفئة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    LibraryStats(files = displayFiles)
                }
                items(displayFiles) { file ->
                    PdfGridCard(
                        file = file,
                        onOpen = { onOpenPdf(Uri.parse(file.uri), file.id) },
                        onFavorite = { viewModel.toggleFavorite(file) },
                        onDelete = { viewModel.deleteFile(file) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { LibraryStats(files = displayFiles) }
                items(displayFiles) { file ->
                    PdfListItem(
                        file = file,
                        onOpen = { onOpenPdf(Uri.parse(file.uri), file.id) },
                        onFavorite = { viewModel.toggleFavorite(file) },
                        onDelete = { viewModel.deleteFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryStats(files: List<PdfFile>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${files.size} ملف", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        val totalSize = files.sumOf { it.size }
        Text(formatSize(totalSize), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        val avgProgress = if (files.isEmpty()) 0f else files.map { it.readProgress }.average().toFloat()
        Text("التقدم: ${(avgProgress * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
    }
}

enum class SortMode(val label: String) {
    DATE("حسب الأحدث"),
    NAME("حسب الاسم أبجدياً"),
    SIZE("حسب الحجم الأكبر"),
    PROGRESS("حسب نسبة التقدم")
}
