package com.example.ui.bookmarks

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Bookmark
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenPdf: (Uri, Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val bookmarks by viewModel.allBookmarks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإشارات المحفوظة", fontWeight = FontWeight.Black, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🔖", fontSize = 56.sp)
                    Text("لا توجد إشارات مرجعية مضافة", color = TextMuted, fontWeight = FontWeight.SemiBold)
                    Text("أثناء قراءة أي ملف، اضغط على 🔖 لحفظ الصفحة للرجوع إليها", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "${bookmarks.size} إشارة مرجعية مضافة",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkItem(
                        bookmark = bookmark,
                        onClick = { onOpenPdf(Uri.parse(bookmark.pdfPath), bookmark.pdfId) },
                        onDelete = { viewModel.deleteBookmark(bookmark) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("ar")) }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored marker bar
            Box(
                modifier = Modifier
                    .size(8.dp, 48.dp)
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = try {
                            Color(android.graphics.Color.parseColor(bookmark.colorHex))
                        } catch (e: Exception) { AccentBlue },
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Bookmark icon
            Icon(
                Icons.Default.Bookmark,
                null,
                tint = try { Color(android.graphics.Color.parseColor(bookmark.colorHex)) } catch (e: Exception) { AccentBlue },
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.label.ifBlank { "صفحة ${bookmark.page + 1}" },
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "صفحة ${bookmark.page + 1} • ${dateFormat.format(Date(bookmark.createdAt))}",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
