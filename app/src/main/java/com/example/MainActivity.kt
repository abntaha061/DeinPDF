package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as PdfReaderApp
        
        // Build MainViewModel using our Global Dependency Injection container
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    return MainViewModel(app.pdfRepository, app.settingsDataStore) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        val viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val appLockEnabled by viewModel.appLock.collectAsState()
            var isAppUnlocked by remember(appLockEnabled) { mutableStateOf(!appLockEnabled) }

            LaunchedEffect(appLockEnabled) {
                if (appLockEnabled && !isAppUnlocked) {
                    // Ensure the activity is fully RESUMED before launching BiometricPrompt
                    while (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        kotlinx.coroutines.delay(100)
                    }
                    showBiometricPrompt(
                        app = app,
                        onSuccess = { isAppUnlocked = true },
                        onFailure = {}
                    )
                }
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                if (appLockEnabled && !isAppUnlocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(AccentBlue.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "قفل بصمة الإصبع",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = "التطبيق آمن ومقفل",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "الرجاء إلغاء القفل بالبصمة لاستئناف قراءة الـ PDF ومراجعة الكلمات المترجمة",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            Button(
                                onClick = {
                                    showBiometricPrompt(
                                        app = app,
                                        onSuccess = { isAppUnlocked = true },
                                        onFailure = {}
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إلغاء قفل البصمة",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    AppNavigation(
                        intentUri = intent?.data,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun showBiometricPrompt(
        app: PdfReaderApp,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (app.appBiometricManager.isBiometricAvailable()) {
            app.appBiometricManager.showBiometricPrompt(
                activity = this,
                title = "تفويض الدخول",
                subtitle = "أثبت بصمتك لفتح تطبيق قارئ الألمانية والترجمة",
                onSuccess = onSuccess,
                onFailure = onFailure
            )
        } else {
            // Biometrics not registered / no hardware -> fallback automatically to unlocked
            onSuccess()
        }
    }
}
