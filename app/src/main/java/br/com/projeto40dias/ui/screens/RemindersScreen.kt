package br.com.projeto40dias.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.prefs.ReminderSettings
import br.com.projeto40dias.domain.ReminderPlanner
import br.com.projeto40dias.notifications.Notifier
import br.com.projeto40dias.notifications.ReminderScheduler
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.nav.Routes
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.launch

class RemindersViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.reminders.asState(this, ReminderSettings())
    val meals = c.nutrition.observeTemplates().asState(this, emptyList())
    val training = c.training.observeWeekTemplates().asState(this, emptyList())

    /** Salva; o app reagenda os alarmes automaticamente ao perceber a mudança. */
    fun update(f: (ReminderSettings) -> ReminderSettings) = viewModelScope.launch {
        c.settings.saveReminders(f(c.settings.currentReminders()))
    }
}

private val dowNames = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

@Composable
fun RemindersScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val vm = appViewModel { RemindersViewModel(it) }
    val s by vm.settings.collectAsState()
    val meals by vm.meals.collectAsState()
    val training by vm.training.collectAsState()

    var granted by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    var exact by remember { mutableStateOf(ReminderScheduler.canExact(ctx)) }
    var testSent by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    // Reavalia as permissões quando o usuário volta das configurações do Android.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            granted = Notifier.canNotify(ctx)
            exact = ReminderScheduler.canExact(ctx)
        }
    }

    fun ensurePermission() {
        if (!granted && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    SubScreen("Lembretes", onBack) {
        if (!granted) item {
            P40Card {
                Notice("As notificações estão bloqueadas. Permita para receber os lembretes.", warning = true)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = P40Colors.Lime, contentColor = Color.Black),
                ) { Text("Permitir notificações") }
            }
        }
        if (!exact && Build.VERSION.SDK_INT >= 31) item {
            P40Card {
                Notice("O Android está bloqueando alarmes exatos — os avisos podem chegar alguns minutos atrasados.", warning = true)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Permitir alarmes no horário exato") }
            }
        }

        // ---------------- Refeições
        item {
            P40Card {
                ToggleRow("Refeições", "Aviso no horário de cada refeição, com os alimentos e o botão “Marcar como feita”.", s.meals) {
                    ensurePermission(); vm.update { r -> r.copy(meals = it) }
                }
                if (s.meals) {
                    LeadChips(s.mealsLeadMin, listOf(0, 10, 15, 30)) { m -> vm.update { it.copy(mealsLeadMin = m) } }
                    Spacer(Modifier.height(6.dp))
                    if (meals.isEmpty()) Text("Nenhuma refeição no cardápio.", fontSize = 12.sp, color = P40Colors.TextSecondary)
                    meals.forEach { m ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(m.time, fontWeight = FontWeight.Bold, color = P40Colors.Lime, modifier = Modifier.width(56.dp))
                            Text(m.label + if (m.optional) " (opcional)" else "", fontSize = 14.sp)
                        }
                    }
                }
                TextButton(onClick = { onNavigate(Routes.DIET_PLAN) }) { Text("Editar refeições e horários", color = P40Colors.Lime) }
            }
        }

        // ---------------- Água
        item {
            P40Card {
                ToggleRow("Beber água", "Lembra a cada intervalo, só enquanto a meta diária não for atingida. Tem botões +250 ml e +500 ml.", s.water) {
                    ensurePermission(); vm.update { r -> r.copy(water = it) }
                }
                if (s.water) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickTime(ctx, s.waterStart) { t -> vm.update { it.copy(waterStart = t) } } }) { Text("Das ${s.waterStart}") }
                        OutlinedButton(onClick = { pickTime(ctx, s.waterEnd) { t -> vm.update { it.copy(waterEnd = t) } } }) { Text("Até ${s.waterEnd}") }
                    }
                    Text("Intervalo", fontSize = 12.sp, color = P40Colors.TextSecondary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 90, 120, 180).forEach { m ->
                            FilterChip(s.waterIntervalMin == m, { vm.update { it.copy(waterIntervalMin = m) } },
                                label = { Text(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60}h${m % 60}") }, colors = chipColors())
                        }
                    }
                    val slots = ReminderPlanner.waterSlots(ReminderPlanner.parseTime(s.waterStart), ReminderPlanner.parseTime(s.waterEnd), s.waterIntervalMin)
                    Text(
                        if (slots.isEmpty()) "Horário final precisa ser depois do inicial."
                        else "${slots.size} avisos por dia: " + slots.joinToString(" · ") { "%02d:%02d".format(it.hour, it.minute) },
                        fontSize = 12.sp, color = if (slots.isEmpty()) P40Colors.Danger else P40Colors.TextSecondary,
                    )
                }
            }
        }

        // ---------------- Treinos
        item {
            P40Card {
                ToggleRow("Treinos", "Aviso antes de cada sessão programada, com a lista de exercícios.", s.training) {
                    ensurePermission(); vm.update { r -> r.copy(training = it) }
                }
                if (s.training) {
                    LeadChips(s.trainingLeadMin, listOf(0, 15, 30, 60)) { m -> vm.update { it.copy(trainingLeadMin = m) } }
                    Spacer(Modifier.height(6.dp))
                    training.groupBy { it.dayOfWeek to it.session }.entries
                        .sortedWith(compareBy({ it.key.first }, { it.value.first().sessionTime }))
                        .forEach { (k, list) ->
                            val f = list.first()
                            Row(Modifier.fillMaxWidth()) {
                                Text("${dowNames[k.first - 1]} ${f.sessionTime}", fontWeight = FontWeight.Bold, color = P40Colors.Lime, modifier = Modifier.width(92.dp))
                                Text(f.sessionName + if (list.all { it.optional }) " (opcional)" else "", fontSize = 14.sp)
                            }
                        }
                }
                TextButton(onClick = { onNavigate(Routes.TRAINING_PLAN) }) { Text("Editar treino e horários", color = P40Colors.Lime) }
            }
        }

        // ---------------- Outros
        item {
            P40Card {
                ToggleRow("Creatina", "Todos os dias, às ${s.creatineTime}.", s.creatine) { ensurePermission(); vm.update { r -> r.copy(creatine = it) } }
                if (s.creatine) TextButton(onClick = { pickTime(ctx, s.creatineTime) { t -> vm.update { it.copy(creatineTime = t) } } }) {
                    Text("Alterar horário", color = P40Colors.Lime)
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow("Pesagem semanal", "Toda segunda-feira, às ${s.weighInTime}.", s.weighIn) { ensurePermission(); vm.update { r -> r.copy(weighIn = it) } }
                if (s.weighIn) TextButton(onClick = { pickTime(ctx, s.weighInTime) { t -> vm.update { it.copy(weighInTime = t) } } }) {
                    Text("Alterar horário", color = P40Colors.Lime)
                }
            }
        }

        item {
            Column {
                OutlinedButton(onClick = { ensurePermission(); ReminderScheduler.test(ctx); testSent = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enviar notificação de teste")
                }
                if (testSent) Text("Se nada apareceu, confira a permissão de notificações do app.", fontSize = 12.sp, color = P40Colors.TextSecondary)
            }
        }
        item {
            Notice("Os lembretes funcionam offline e continuam após reiniciar o celular. Em alguns aparelhos (Xiaomi, Samsung, Motorola…) " +
                "é preciso tirar o app da otimização de bateria para os avisos não atrasarem.")
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, fontSize = 12.sp, color = P40Colors.TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = P40Colors.Lime, checkedThumbColor = Color.Black))
    }
}

@Composable
private fun LeadChips(selected: Int, options: List<Int>, onPick: (Int) -> Unit) {
    SectionLabel("Avisar")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { m ->
            FilterChip(selected == m, { onPick(m) }, label = { Text(if (m == 0) "Na hora" else "$m min antes") }, colors = chipColors())
        }
    }
}
