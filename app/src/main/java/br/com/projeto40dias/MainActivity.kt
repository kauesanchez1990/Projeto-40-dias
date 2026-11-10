package br.com.projeto40dias

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.com.projeto40dias.ui.screens.OpeningScreen
import br.com.projeto40dias.notifications.Notifier
import br.com.projeto40dias.ui.nav.P40NavHost
import br.com.projeto40dias.ui.theme.P40Theme

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            P40Theme {
                // Abertura só na inicialização (não repete ao girar a tela).
                var opening by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (!opening) P40NavHost()
                    AnimatedVisibility(visible = opening, enter = EnterTransition.None, exit = fadeOut(tween(450))) {
                        OpeningScreen(onFinished = { opening = false; requestNotificationsOnce() })
                    }
                }
            }
        }
        if (savedInstanceState != null) requestNotificationsOnce()
    }

    /** No Android 13+ pede a permissão de notificação uma vez (os lembretes vêm ligados por padrão). */
    private fun requestNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33 || Notifier.canNotify(this)) return
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        // Atualiza a data atual (caso o app tenha ficado aberto de um dia para o outro).
        (application as P40App).container.refreshToday()
    }
}
