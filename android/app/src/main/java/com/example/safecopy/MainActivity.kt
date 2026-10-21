package com.example.safecopy

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.safecopy.domain.auth.SessionManager
import com.example.safecopy.ui.navigation.SafeCopyNavHost
import com.example.safecopy.ui.theme.SafecopyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Запрет скриншотов и записи экрана. По умолчанию включён, как в макете настроек.
        // TODO(этап 8): управлять из настроек, на API 33+ добавить setRecentsScreenshotEnabled(false).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            SafecopyTheme {
                val isUnlocked by sessionManager.isUnlocked.collectAsStateWithLifecycle()
                SafeCopyNavHost(isUnlocked = isUnlocked)
            }
        }
    }
}
