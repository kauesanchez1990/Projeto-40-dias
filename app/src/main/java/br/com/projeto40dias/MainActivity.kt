package br.com.projeto40dias

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import br.com.projeto40dias.notifications.Notifier
import br.com.projeto40dias.ui.nav.P40NavHost
import br.com.projeto40dias.ui.theme.P40Theme

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { P40Theme { P40NavHost() } }
        requestNotificationsOnce()
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
