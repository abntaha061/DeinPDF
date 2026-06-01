package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== SearchBar component =====
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "بحث عن ملفات PDF..."
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.heightIn(min = 48.dp),
        placeholder = { Text(placeholder, color = TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, null, tint = TextMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlue,
            unfocusedBorderColor = DarkBorder,
            focusedContainerColor = DarkCard,
            unfocusedContainerColor = DarkCard,
            cursorColor = AccentBlue,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

// ===== Loading Overlay =====
@Composable
fun LoadingOverlay(
    isVisible: Boolean,
    message: String = "جاري المعالجة والتحميل...",
    progress: Float? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(32.dp),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (progress != null) {
                        CircularProgressIndicator(
                            progress = { progress },
                            color = AccentBlue,
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 5.dp
                        )
                        Text("${(progress * 100).toInt()}%", color = AccentBlue, fontWeight = FontWeight.Bold)
                    } else {
                        CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(56.dp))
                    }
                    Text(message, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ===== Confirm Dialog =====
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "تأكيد",
    cancelText: String = "إلغاء",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(18.dp),
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text(message, color = TextMuted, lineHeight = 22.sp, fontSize = 14.sp) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) ErrorRed else AccentBlue
                ),
                shape = RoundedCornerShape(10.dp)
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText, color = TextMuted)
            }
        }
    )
}

// ===== Color Picker Row =====
@Composable
fun ColorPickerRow(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(
        Color(0xFFFBBF24), // Yellow
        Color(0xFF4ADE80), // Green
        Color(0xFF60A5FA), // Blue
        Color(0xFFF472B6), // Pink
        Color(0xFFFF8C00), // Orange
        Color(0xFFEF4444), // Red
        Color(0xFF8B5CF6), // Purple
        Color(0xFF06B6D4)  // Cyan
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(color)
                    .border(
                        if (selectedColor == color) BorderStroke(3.dp, Color.White)
                        else BorderStroke(0.dp, Color.Transparent),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

// ===== Info Row =====
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color = TextMuted
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ===== Section Divider =====
@Composable
fun SectionDivider(title: String = "") {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
        if (title.isNotBlank()) {
            Text(
                title,
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
        }
    }
}

// ===== Empty State View =====
@Composable
fun EmptyStateView(
    emoji: String,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 64.sp)
        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text(subtitle, color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

// ===== File Info Sheet =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInfoSheet(
    fileName: String,
    fileSize: Long,
    pageCount: Int,
    readProgress: Float,
    lastOpened: Long,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val fmt = remember { SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale.forLanguageTag("ar")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkCard
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .navigationBarsPadding()
        ) {
            Text("معلومات الملف", fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            InfoRow(Icons.Default.Description, "اسم الملف", fileName, AccentBlue)
            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(Icons.Default.Storage, "الحجم", formatSize(fileSize), AccentPurple)
            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(Icons.Default.Pages, "عدد الصفحات", "$pageCount صفحة", Gold)
            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(Icons.Default.Percent, "التقدم", "${(readProgress * 100).toInt()}%", SuccessGreen)
            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(Icons.Default.AccessTime, "آخر فتح", fmt.format(Date(lastOpened)), TextMuted)
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
}

// ===== Tag Chip component =====
@Composable
fun TagChip(
    label: String,
    color: Color = AccentBlue,
    onRemove: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(0.15f),
        border = BorderStroke(1.dp, color.copy(0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (onRemove != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = color,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onRemove() }
                )
            }
        }
    }
}
