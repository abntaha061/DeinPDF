package com.example.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ScanButton(
    isScanning: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Button(
        onClick = onScan,
        enabled = !isScanning,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .testTag("scan_button")
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isScanning) {
                        listOf(TextMuted, TextMuted)
                    } else {
                        listOf(AccentBlue, AccentPurple)
                    }
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Refresh else Icons.Default.Search,
                contentDescription = "Scan icon",
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .then(if (isScanning) Modifier.rotate(angle) else Modifier)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isScanning) "جاري المسح..." else "🔍 مسح",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun EmptyRecentState(
    isScanning: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(DarkCard, shape = RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📁",
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "لا توجد ملفات حديثة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "اضغط على مسح للبحث عن ملفات الـ PDF في جهازك",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            ScanButton(
                isScanning = isScanning,
                onScan = onScan
            )
        }
    }
}
