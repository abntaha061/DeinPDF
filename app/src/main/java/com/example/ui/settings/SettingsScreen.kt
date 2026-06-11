package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val appLockEnabled by viewModel.appLock.collectAsState()

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    // حالات (States) للتحكم في ظهور النوافذ المنبثقة السفلية
    var showThemeSheet by remember { mutableStateOf(false) }
    var showSourceLangSheet by remember { mutableStateOf(false) }
    var showTargetLangSheet by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showScrollSheet by remember { mutableStateOf(false) }
    var showPdfModeSheet by remember { mutableStateOf(false) }

    // حالات (States) للخيارات (مؤقتة حتى يتم ربطها بالـ ViewModel لاحقاً)
    var sourceLang by remember { mutableStateOf("de") }
    var targetLang by remember { mutableStateOf("ar") }
    var voice by remember { mutableStateOf("female") }
    var scrollDirection by remember { mutableStateOf("vertical") }
    var pdfMode by remember { mutableStateOf("continuous") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات والتخصيص", fontWeight = FontWeight.Black, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface, titleContentColor = Color.White)
            )
        },
        containerColor = DarkSurface // لضمان لون الخلفية المناسب
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== THEME =====
            item { SettingsSectionHeader("🎨 المظهر والعرض") }

            item {
                SettingsCard {
                    SettingsSelectionRow(
                        label = "وضع الألوان",
                        selectedValueDisplay = themeMode.arabicName,
                        onClick = { showThemeSheet = true }
                    )
                }
            }

            // ===== PERMISSIONS =====
            item { SettingsSectionHeader("🔒 الأذونات") }

            item {
                SettingsCard {
                    PermissionSettingRow(
                        label = "الوصول للملفات",
                        description = "قراءة وحفظ ملفات PDF الخاصة بك",
                        icon = Icons.Default.Folder,
                        isGranted = true, // default allowed internally
                        onGrant = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            settingsLauncher.launch(intent)
                        }
                    )
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    PermissionSettingRow(
                        label = "إدارة كل الملفات (Android 11+)",
                        description = "للوصول الشامل للذاكرة في الأجهزة الحديثة",
                        icon = Icons.Default.Storage,
                        isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            android.os.Environment.isExternalStorageManager() else true,
                        onGrant = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    settingsLauncher.launch(intent)
                                } catch (e: Exception) {
                                    settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            }
                        }
                    )
                }
            }

            // ===== TRANSLATION =====
            item { SettingsSectionHeader("🌍 الترجمة والنطق") }

            item {
                SettingsCard {
                    SettingsSelectionRow(
                        label = "لغة المصدر الافتراضية",
                        selectedValueDisplay = when(sourceLang) { "de" -> "🇩🇪 ألماني"; "ar" -> "🇸🇦 عربي"; else -> "🤖 تلقائي" },
                        onClick = { showSourceLangSheet = true }
                    )
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSelectionRow(
                        label = "لغة الترجمة الفرعية",
                        selectedValueDisplay = when(targetLang) { "ar" -> "🇸🇦 عربي"; "de" -> "🇩🇪 ألماني"; else -> "🇬🇧 إنجليزي" },
                        onClick = { showTargetLangSheet = true }
                    )
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSelectionRow(
                        label = "صوت القارئ الافتراضي",
                        selectedValueDisplay = if (voice == "female") "أنثى (افتراضي)" else "ذكر",
                        onClick = { showVoiceSheet = true }
                    )
                }
            }

            // TTS Speed
            item {
                var ttsSpeed by remember { mutableStateOf(1f) }
                SettingsCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("سرعة المساعد الصوتي", color = Color.White)
                        Text("${ttsSpeed}x", color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = ttsSpeed,
                        onValueChange = { ttsSpeed = (it * 4).toInt() / 4f },
                        valueRange = 0.5f..2f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0.5x", color = TextMuted, fontSize = 11.sp)
                        Text("1.0x (طبيعي)", color = TextMuted, fontSize = 11.sp)
                        Text("2.0x", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }

            // ===== READING =====
            item { SettingsSectionHeader("📖 إعدادات القراءة") }

            item {
                var fontSize by remember { mutableStateOf(16) }
                SettingsCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("حجم خط الترجمة والملاحظات", color = Color.White)
                        Text("${fontSize}pt", color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { fontSize = it.toInt() },
                        valueRange = 8f..32f,
                        colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                    )
                }
            }

            item {
                SettingsCard {
                    SettingsSelectionRow(
                        label = "اتجاه التمرير",
                        selectedValueDisplay = if (scrollDirection == "vertical") "↕ رأسي" else "↔ أفقي",
                        onClick = { showScrollSheet = true }
                    )
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSelectionRow(
                        label = "وضع صفحات PDF",
                        selectedValueDisplay = when(pdfMode) { "continuous" -> "متواصل (Continuous)"; "single" -> "صفحة بصفحة"; else -> "وضع كتاب" },
                        onClick = { showPdfModeSheet = true }
                    )
                }
            }

            // ===== ACCESSIBILITY =====
            item { SettingsSectionHeader("♿ إمكانية الوصول") }

            item {
                var highContrast by remember { mutableStateOf(false) }
                var reduceMotion by remember { mutableStateOf(false) }
                SettingsCard {
                    SettingsSwitchRow("تباين ألوان عالٍ (High Contrast)", "تسهيل القراءة وتوضيح النصوص الضعيفة", Icons.Default.Contrast, highContrast) { highContrast = it }
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchRow("تقليل الحركة التفاعلية", "توفير بطارية الجهاز وتقليل المؤثرات الحركية", Icons.Default.Animation, reduceMotion) { reduceMotion = it }
                }
            }

            // ===== SECURITY =====
            item { SettingsSectionHeader("🔐 الخصوصية والأمان") }

            item {
                var screenSecurity by remember { mutableStateOf(false) }
                SettingsCard {
                    SettingsSwitchRow(
                        "قفل بصمة الإصبع",
                        "طلب المصادقة بالبصمة عند تشغيل التطبيق يدوياً",
                        Icons.Default.Fingerprint,
                        appLockEnabled
                    ) { viewModel.setAppLock(it) }
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchRow("إخفاء محتوى الشاشة الأخيرة", "منع لقطات الشاشة أو إظهار المحتويات في التطبيقات المفتوحة", Icons.Default.VisibilityOff, screenSecurity) { screenSecurity = it }
                }
            }

            // ===== ABOUT =====
            item { SettingsSectionHeader("ℹ️ عن التطبيق") }
            item {
                SettingsCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإصدار الحالي", color = Color.White)
                        Text("2.0.1 (تحديث ذكي)", color = TextMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("اسم المطور", color = Color.White)
                        Text("محمد (Mohammed)", color = AccentBlue)
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }

        // ════════ النوافذ المنبثقة السفلية (Bottom Sheets) ════════

        if (showThemeSheet) {
            SelectionBottomSheet(
                title = "وضع الألوان",
                options = ThemeMode.values().map { it to it.arabicName },
                selectedValue = themeMode,
                onSelect = { viewModel.setThemeMode(it) },
                onDismiss = { showThemeSheet = false }
            )
        }

        if (showSourceLangSheet) {
            SelectionBottomSheet(
                title = "لغة المصدر الافتراضية",
                options = listOf("de" to "🇩🇪 ألماني", "ar" to "🇸🇦 عربي", "auto" to "🤖 تلقائي"),
                selectedValue = sourceLang,
                onSelect = { sourceLang = it },
                onDismiss = { showSourceLangSheet = false }
            )
        }

        if (showTargetLangSheet) {
            SelectionBottomSheet(
                title = "لغة الترجمة الفرعية",
                options = listOf("ar" to "🇸🇦 عربي", "de" to "🇩🇪 ألماني", "en" to "🇬🇧 إنجليزي"),
                selectedValue = targetLang,
                onSelect = { targetLang = it },
                onDismiss = { showTargetLangSheet = false }
            )
        }

        if (showVoiceSheet) {
            SelectionBottomSheet(
                title = "صوت القارئ الافتراضي",
                options = listOf("female" to "أنثى (افتراضي)", "male" to "ذكر"),
                selectedValue = voice,
                onSelect = { voice = it },
                onDismiss = { showVoiceSheet = false }
            )
        }

        if (showScrollSheet) {
            SelectionBottomSheet(
                title = "اتجاه التمرير",
                options = listOf("vertical" to "↕ رأسي", "horizontal" to "↔ أفقي"),
                selectedValue = scrollDirection,
                onSelect = { scrollDirection = it },
                onDismiss = { showScrollSheet = false }
            )
        }

        if (showPdfModeSheet) {
            SelectionBottomSheet(
                title = "وضع صفحات PDF",
                options = listOf("continuous" to "متواصل (Continuous)", "single" to "صفحة بصفحة", "book" to "وضع كتاب"),
                selectedValue = pdfMode,
                onSelect = { pdfMode = it },
                onDismiss = { showPdfModeSheet = false }
            )
        }
    }
}

// ════════ مكونات الواجهة المساعدة (UI Components) ════════

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        title,
        color = AccentBlue,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

// المكون الجديد لعرض الخيار القابل للضغط (لفتح النافذة المنبثقة)
@Composable
fun SettingsSelectionRow(
    label: String,
    selectedValueDisplay: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(selectedValueDisplay, color = AccentBlue, fontSize = 13.sp)
        }
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "تغيير الخيار", tint = TextMuted)
    }
}

@Composable
fun SettingsSwitchRow(
    label: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(description, color = TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentBlue)
        )
    }
}

@Composable
fun PermissionSettingRow(
    label: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (isGranted) SuccessGreen else ErrorRed, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(description, color = TextMuted, fontSize = 11.sp)
        }
        if (!isGranted) {
            TextButton(onClick = onGrant) {
                Text("منح الإذن", color = Gold, fontSize = 12.sp)
            }
        } else {
            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
        }
    }
}

// ════════ النافذة المنبثقة الذكية (Generic Bottom Sheet) ════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectionBottomSheet(
    title: String,
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            options.forEach { (item, display) ->
                val isSelected = item == selectedValue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(item)
                            onDismiss() // إغلاق النافذة تلقائياً بعد الاختيار
                        }
                        .background(if (isSelected) AccentBlue.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = display,
                        color = if (isSelected) AccentBlue else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp
                    )
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = "تم الاختيار", tint = AccentBlue)
                    }
                }
            }
        }
    }
}
