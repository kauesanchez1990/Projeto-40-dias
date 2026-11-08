package br.com.projeto40dias

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.projeto40dias.ui.nav.P40NavHost
import br.com.projeto40dias.ui.theme.P40Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { P40Theme { P40NavHost() } }
    }

    override fun onResume() {
        super.onResume()
        // Atualiza a data atual (caso o app tenha ficado aberto de um dia para o outro).
        (application as P40App).container.refreshToday()
    }
}
