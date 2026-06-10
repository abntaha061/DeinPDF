package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BottomNavTab {
    HOME, LIBRARY, FOLDERS, SETTINGS
}

data class BottomNavItem(
    val tab: BottomNavTab,
    val label: String,
    val icon: ImageVector
)

@Composable
fun AppBottomNavBar(
    currentTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem(BottomNavTab.HOME,     "الرئيسية", Icons.Default.Home),
        BottomNavItem(BottomNavTab.LIBRARY,  "المكتبة",  Icons.Default.Folder),
        BottomNavItem(BottomNavTab.FOLDERS,  "المجلدات", Icons.Default.Folder),
        BottomNavItem(BottomNavTab.SETTINGS, "الإعدادات",Icons.Default.Settings),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF111827))
            .navigationBarsPadding()
    ) {
        // Top divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                BottomNavTabItem(
                    item = item,
                    isSelected = currentTab == item.tab,
                    onSelected = { onTabSelected(item.tab) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavTabItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF3B82F6) else Color(0xFF6B7280),
        animationSpec = tween(250),
        label = "iconColor"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF3B82F6) else Color(0xFF6B7280),
        animationSpec = tween(250),
        label = "labelColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelected
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.15f)
                    else Color.Transparent
                )
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
