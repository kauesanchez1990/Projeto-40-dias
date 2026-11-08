package br.com.projeto40dias.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.db.CheckinEntity
import br.com.projeto40dias.data.db.ExerciseLogEntity
import br.com.projeto40dias.data.db.ExerciseTemplateEntity
import br.com.projeto40dias.data.db.ExerciseType
import br.com.projeto40dias.data.repo.ExerciseWithLog
import br.com.projeto40dias.data.repo.SessionDay
import br.com.projeto40dias.domain.Recovery
import br.com.projeto40dias.domain.RunCalculator
import br.com.projeto40dias.ui.components.ChartPoint
import br.com.projeto40dias.ui.components.DateSwitcher
import br.com.projeto40dias.ui.components.KindPill
import br.com.projeto40dias.ui.components.LineChart
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.NumberField
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.components.ProgressBar
import br.com.projeto40dias.ui.components.ScreenTitle
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.ValueKind
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmtDate
import br.com.projeto40dias.ui.components.parseNum
import br.com.projeto40dias.ui.nav.Routes
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModel(private val c: AppContainer) : ViewModel() {
    val date = MutableStateFlow(c.today.value)
    val today = c.today
    val sessions = date.flatMapLatest { c.training.observeDay(it) }.asState(this, emptyList())
    val week = c.training.observeWeekTemplates().asState(this, emptyList())
    val checkin = date.flatMapLatest { c.tracking.checkin(it) }.asState(this, null)

    /** Peso usado na estimativa de kcal da corrida: última pesagem medida, senão o peso inicial do perfil. */
    val weightForEstimate = combine(c.body.all(), c.settings.profile) { m, p ->
        m.filter { it.weightKg != null }.maxByOrNull { it.date }?.weightKg ?: p.initialWeightKg
    }.asState(this, 82.5)

    fun history(id: Long): Flow<List<ExerciseLogEntity>> = c.training.history(id)
    fun setDone(templateId: Long, done: Boolean) = viewModelScope.launch { c.training.setDone(date.value, templateId, done) }
    fun save(log: ExerciseLogEntity) = viewModelScope.launch { c.training.saveLog(log) }
    fun saveCheckin(f: Int, s: Int, sl: Int) = viewModelScope.launch { c.tracking.saveCheckin(CheckinEntity(date.value.toString(), f, s, sl)) }
}

private val dowShort = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")

@Composable
fun TrainingScreen(onNavigate: (String) -> Unit) {
    val vm = appViewModel { TrainingViewModel(it) }
    val date by vm.date.collectAsState()
    val today by vm.today.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val week by vm.week.collectAsState()
    val checkin by vm.checkin.collectAsState()
    val weightEst by vm.weightForEstimate.collectAsState()
    var logging by remember { mutableStateOf<ExerciseWithLog?>(null) }

    val ck = checkin
    val recovered = ck != null && Recovery.isRecovered(ck.fatigue, ck.soreness, ck.sleep)
    val fatigued = ck != null && Recovery.showFatigueWarning(ck.fatigue, ck.soreness, ck.sleep)

    ScreenList {
        item { ScreenTitle("MEUS TREINOS", "Musculação, corrida e recuperação") }
        item { DateSwitcher(date, today) { vm.date.value = it } }
        item { WeekStrip(date, today, week) { vm.date.value = it } }
        item { RestTimer() }

        if (fatigued) item {
            Notice("Check-in indica fadiga ou recuperação insuficiente. Reduza a intensidade, priorize mobilidade e descanso. Corridas fortes e treinos extras não são recomendados hoje.", warning = true)
        }

        if (sessions.isEmpty()) item { P40Card { Text("Nada programado para este dia.", color = P40Colors.TextSecondary) } }

        items(sessions, key = { it.session + it.name + it.optional }) { s ->
            if (s.optional && !recovered) {
                P40Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill("OPCIONAL", P40Colors.TextSecondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (ck == null) "Faça o check-in de recuperação (abaixo) para liberar este treino opcional."
                        else "Liberado apenas quando você estiver bem recuperado (fadiga e dor ≤ 2, sono ≥ 3). Hoje, descanse.",
                        fontSize = 12.sp, color = P40Colors.TextSecondary,
                    )
                }
            } else {
                SessionCard(s, onToggle = { e, d -> vm.setDone(e.template.id, d) }, onLog = { logging = it },
                    onHistory = { onNavigate(Routes.history(it.template.id)) })
            }
        }

        if (sessions.any { it.optional }) item { CheckinCard(ck) { a, b, d -> vm.saveCheckin(a, b, d) } }

        item { Notice("Dados de corrida são apenas os que você digitar — o app não inventa GPS nem frequência cardíaca. Integração com relógios e Health Connect pode ser adicionada no futuro.") }
    }

    logging?.let { e ->
        val history by remember(e.template.id) { vm.history(e.template.id) }.collectAsState(initial = emptyList())
        LogDialog(e, date, history.firstOrNull { it.date != date.toString() }, weightEst, onDismiss = { logging = null }) {
            vm.save(it); logging = null
        }
    }
}

@Composable
private fun WeekStrip(date: LocalDate, today: LocalDate, week: List<ExerciseTemplateEntity>, onPick: (LocalDate) -> Unit) {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val sel = d == date
            val hasRun = week.any { it.dayOfWeek == i + 1 && it.type == ExerciseType.CORRIDA }
            val hasStrength = week.any { it.dayOfWeek == i + 1 && it.type == ExerciseType.FORCA && !it.optional }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) P40Colors.Lime else P40Colors.Card)
                    .clickable(enabled = !d.isAfter(today)) { onPick(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fg = if (sel) Color.Black else if (d.isAfter(today)) P40Colors.TextSecondary else P40Colors.Text
                Text(dowShort[i], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
                Text("${d.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                Text(
                    when { hasStrength && hasRun -> "💪🏃"; hasStrength -> "💪"; hasRun -> "🏃"; else -> "🧘" },
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(s: SessionDay, onToggle: (ExerciseWithLog, Boolean) -> Unit, onLog: (ExerciseWithLog) -> Unit, onHistory: (ExerciseWithLog) -> Unit) {
    val done = s.exercises.count { it.done }
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.session.uppercase(), fontSize = 11.sp, color = P40Colors.TextSecondary, fontWeight = FontWeight.Bold)
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = if (s.done) P40Colors.Lime else P40Colors.Text)
            }
            if (s.optional) Pill("OPCIONAL", P40Colors.Lime)
            Spacer(Modifier.width(8.dp))
            Text("$done/${s.exercises.size}", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(if (s.exercises.isEmpty()) 0f else done.toFloat() / s.exercises.size, height = 5.dp)
        Spacer(Modifier.height(4.dp))
        s.exercises.forEach { e ->
            Row(Modifier.fillMaxWidth().clickable { onLog(e) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(e.done, { onToggle(e, it) }, colors = CheckboxDefaults.colors(checkedColor = P40Colors.Lime, checkmarkColor = Color.Black))
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(e.template.name, fontWeight = FontWeight.SemiBold, color = if (e.done) P40Colors.TextSecondary else P40Colors.Text)
                    Text(e.template.prescription, fontSize = 12.sp, color = P40Colors.TextSecondary)
                    summary(e.log)?.let { Text(it, fontSize = 12.sp, color = P40Colors.Lime) }
                }
                if (e.template.type != ExerciseType.MOBILIDADE) {
                    IconButton(onClick = { onHistory(e) }) { Icon(Icons.Filled.History, "Histórico", tint = P40Colors.TextSecondary) }
                }
            }
        }
        Text("Toque no exercício para registrar carga, repetições, séries e observações.", fontSize = 11.sp, color = P40Colors.TextSecondary)
    }
}

private fun summary(l: ExerciseLogEntity?): String? {
    if (l == null) return null
    val parts = mutableListOf<String>()
    if (l.sets != null && l.reps != null) parts += "${l.sets}×${l.reps}" else l.sets?.let { parts += "$it séries" }
    l.weightKg?.let { parts += "${fmt(it)} kg" }
    l.distanceKm?.let { parts += "${fmt(it, 2)} km" }
    l.durationSec?.let { parts += RunCalculator.formatDuration(it) }
    if (l.distanceKm != null && l.durationSec != null) parts += RunCalculator.formatPace(RunCalculator.paceSecPerKm(l.distanceKm, l.durationSec))
    l.heartRateAvg?.let { parts += "$it bpm" }
    if (l.notes.isNotBlank()) parts += "“${l.notes}”"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun LogDialog(
    e: ExerciseWithLog, date: LocalDate, previous: ExerciseLogEntity?, weightKg: Double,
    onDismiss: () -> Unit, onSave: (ExerciseLogEntity) -> Unit,
) {
    val l = e.log
    val isRun = e.template.type == ExerciseType.CORRIDA
    var sets by remember { mutableStateOf((l?.sets ?: e.template.targetSets)?.toString() ?: "") }
    var reps by remember { mutableStateOf(l?.reps?.toString() ?: "") }
    var weight by remember { mutableStateOf(l?.weightKg?.let { fmt(it) } ?: "") }
    var dist by remember { mutableStateOf(l?.distanceKm?.let { fmt(it, 2) } ?: "") }
    var time by remember { mutableStateOf(l?.durationSec?.let { RunCalculator.formatDuration(it) } ?: "") }
    var hr by remember { mutableStateOf(l?.heartRateAvg?.toString() ?: "") }
    var notes by remember { mutableStateOf(l?.notes ?: "") }
    var done by remember { mutableStateOf(l?.done ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.template.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.template.prescription, fontSize = 12.sp, color = P40Colors.TextSecondary)
                previous?.let { Text("Última vez (${fmtDate(LocalDate.parse(it.date))}): ${summary(it) ?: "concluído"}", fontSize = 12.sp, color = P40Colors.Estimated) }
                if (isRun) {
                    NumberField(dist, { dist = it }, "Distância", unit = "km")
                    OutlinedTextField(time, { time = it }, label = { Text("Tempo total (mm:ss ou h:mm:ss)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    NumberField(hr, { hr = it }, "FC média (opcional)", unit = "bpm")
                    val dk = parseNum(dist); val ts = RunCalculator.parseDuration(time)
                    if (dk != null && ts != null) {
                        Text("Ritmo médio: ${RunCalculator.formatPace(RunCalculator.paceSecPerKm(dk, ts))}", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("≈ ${RunCalculator.estimatedKcal(weightKg, dk)} kcal  ", fontSize = 13.sp)
                            KindPill(ValueKind.ESTIMADO)
                        }
                    }
                } else if (e.template.type == ExerciseType.FORCA) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumberField(sets, { sets = it }, "Séries", Modifier.weight(1f))
                        NumberField(reps, { reps = it }, "Reps", Modifier.weight(1f))
                    }
                    NumberField(weight, { weight = it }, "Carga utilizada", unit = "kg")
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(done, { done = it }, colors = CheckboxDefaults.colors(checkedColor = P40Colors.Lime, checkmarkColor = Color.Black))
                    Text("Concluído")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ExerciseLogEntity(
                        id = l?.id ?: 0, date = date.toString(), templateId = e.template.id, done = done,
                        weightKg = parseNum(weight), reps = reps.trim().toIntOrNull(), sets = sets.trim().toIntOrNull(),
                        distanceKm = parseNum(dist), durationSec = RunCalculator.parseDuration(time).takeIf { time.isNotBlank() },
                        heartRateAvg = hr.trim().toIntOrNull(), notes = notes.trim(),
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Cronômetro de descanso entre séries. */
@Composable
private fun RestTimer() {
    var total by remember { mutableIntStateOf(90) }
    var left by remember { mutableIntStateOf(90) }
    var running by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(running) {
        while (running && left > 0) {
            delay(1000)
            left -= 1
        }
        if (running && left == 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            running = false
        }
    }

    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null, tint = P40Colors.Lime)
            Spacer(Modifier.width(8.dp))
            SectionLabel("Descanso", Modifier.weight(1f))
            Text("%d:%02d".format(left / 60, left % 60), fontSize = 28.sp, fontWeight = FontWeight.Black,
                color = if (left == 0) P40Colors.Lime else P40Colors.Text)
            IconButton(onClick = { if (left == 0) left = total; running = !running }) {
                Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (running) "Pausar" else "Iniciar", tint = P40Colors.Lime)
            }
            IconButton(onClick = { running = false; left = total }) { Icon(Icons.Filled.Refresh, "Reiniciar") }
        }
        ProgressBar(if (total > 0) left.toFloat() / total else 0f, height = 5.dp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(45, 60, 90, 120, 180).forEach { s ->
                FilterChip(selected = total == s, onClick = { total = s; left = s; running = false },
                    label = { Text(if (s < 60) "${s}s" else "${s / 60}:${"%02d".format(s % 60)}") }, colors = chipColors())
            }
        }
    }
}

// ------------------------------------------------------------------ Histórico de um exercício

class HistoryViewModel(c: AppContainer, id: Long) : ViewModel() {
    val logs = c.training.history(id).asState(this, emptyList())
    val template = c.training.observeWeekTemplates().map { l -> l.firstOrNull { it.id == id } }.asState(this, null)
}

@Composable
fun ExerciseHistoryScreen(templateId: Long, onBack: () -> Unit) {
    val vm = appViewModel { HistoryViewModel(it, templateId) }
    val logs by vm.logs.collectAsState()
    val t by vm.template.collectAsState()
    val isRun = t?.type == ExerciseType.CORRIDA

    SubScreen(t?.name ?: "Histórico", onBack) {
        item {
            P40Card {
                SectionLabel(if (isRun) "Distância (km)" else "Carga utilizada (kg)")
                Spacer(Modifier.height(8.dp))
                val pts = logs.mapNotNull { l ->
                    val v = if (isRun) l.distanceKm else l.weightKg
                    v?.let { ChartPoint(LocalDate.parse(l.date), it) }
                }
                LineChart(pts, if (isRun) "km" else "kg", decimals = if (isRun) 2 else 1)
            }
        }
        if (isRun) item {
            P40Card {
                SectionLabel("Ritmo médio (min/km)")
                Spacer(Modifier.height(8.dp))
                val pts = logs.mapNotNull { l ->
                    val p = if (l.distanceKm != null && l.durationSec != null) RunCalculator.paceSecPerKm(l.distanceKm, l.durationSec) else null
                    p?.let { ChartPoint(LocalDate.parse(l.date), it / 60.0) }
                }
                LineChart(pts, "min/km", color = P40Colors.Estimated, decimals = 2)
            }
        }
        if (logs.isEmpty()) item { Text("Nenhum registro concluído ainda.", color = P40Colors.TextSecondary) }
        items(logs, key = { it.id }) { l ->
            P40Card {
                Text(fmtDate(LocalDate.parse(l.date)), fontWeight = FontWeight.Bold)
                Text(summary(l) ?: "Concluído", fontSize = 13.sp, color = P40Colors.TextSecondary)
            }
        }
    }
}
