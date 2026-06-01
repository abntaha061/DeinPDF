package com.example.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.utils.PermissionManager
import com.google.accompanist.permissions.*

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val color: Color
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(0) }

    val pages = listOf(
        OnboardingPage(
            Icons.Default.PictureAsPdf,
            "قارئ PDF الشامل",
            "اقرأ، راجع، وتعلّم الألمانية — كل شيء في تطبيق واحد ذكي",
            AccentBlue
        ),
        OnboardingPage(
            Icons.Default.Translate,
            "ترجمة عربي ⇄ ألماني",
            "اضغط أي كلمة لترجمتها فوراً مع نطق ألماني صحيح وشرح شامل",
            AccentPurple
        ),
        OnboardingPage(
            Icons.Default.Folder,
            "نحتاج إذن الملفات",
            "لفتح وحفظ ملفات PDF من جهازك، نحتاج إذن الوصول للتخزين",
            Gold
        )
    )

    // Storage permission
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(android.Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Page icon
            AnimatedContent(targetState = currentPage, label = "pageIcon") { page ->
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(pages[page].color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        pages[page].icon,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = pages[page].color
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(targetState = currentPage, label = "pageText") { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        pages[page].title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pages[page].subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Page dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == currentPage) AccentBlue else DarkBorder)
                    )
                }
            }

            // Permission status card (only on last page)
            if (currentPage == 2) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        PermissionRow("قراءة الملفات", storagePermission.status.isGranted)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            PermissionRow(
                                "إدارة كل الملفات (Android 11+)",
                                android.os.Environment.isExternalStorageManager()
                            )
                        }
                    }
                }

                if (!storagePermission.status.isGranted && storagePermission.status.shouldShowRationale) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "رُفض الإذن — افتح الإعدادات وامنح الإذن يدوياً للمتابعة",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentPage < pages.size - 1) {
                    Button(
                        onClick = { currentPage++ },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text("التالي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    // Grant permission button
                    Button(
                        onClick = {
                            if (storagePermission.status.isGranted) {
                                handleAllFilesAccess(context, settingsLauncher, onFinish)
                            } else if (storagePermission.status.shouldShowRationale) {
                                // Open app settings
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                settingsLauncher.launch(intent)
                            } else {
                                storagePermission.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold)
                    ) {
                        Icon(Icons.Default.LockOpen, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("منح إذن الملفات", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                    }

                    TextButton(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تخطي الآن ودخول التطبيق", color = TextMuted)
                    }
                }
            }
        }
    }

    // Auto-advance when permission granted
    LaunchedEffect(storagePermission.status.isGranted) {
        if (storagePermission.status.isGranted && currentPage == 2) {
            onFinish()
        }
    }
}

@Composable
fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (granted) SuccessGreen else ErrorRed,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun handleAllFilesAccess(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onFinish: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!android.os.Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                launcher.launch(intent)
            } catch (e: Exception) {
                launcher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            onFinish()
        }
    } else {
        onFinish()
    }
}
