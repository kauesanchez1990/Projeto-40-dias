package br.com.projeto40dias.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.db.BodyMeasurementEntity
import br.com.projeto40dias.data.db.ProjectEntity
import br.com.projeto40dias.data.prefs.Goals
import br.com.projeto40dias.data.prefs.Profile
import br.com.projeto40dias.domain.DatedValue
import br.com.projeto40dias.domain.DaySummary
import br.com.projeto40dias.domain.ProjectCalculator
import br.com.projeto40dias.domain.ReportCalculator
import br.com.projeto40dias.domain.WeekReport
import br.com.projeto40dias.pdf.ReportPdfExporter
import br.com.projeto40dias.ui.components.ChartPoint
import br.com.projeto40dias.ui.components.LineChart
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.components.ProgressBar
import br.com.projeto40dias.ui.components.ProgressRing
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.StatTile
import br.com.projeto40dias.ui.components.TileRow
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmt0
import br.com.projeto40dias.ui.components.fmtDate
import br.com.projeto40dias.ui.components.fmtShort
import br.com.projeto40dias.ui.nav.Routes
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// =================================================================== HIDRATAÇÃO

@OptIn(ExperimentalCoroutinesApi::class)
class WaterViewModel(private val c: AppContainer) : ViewModel() {
    val total = c.today.flatMapLatest { c.tracking.waterTotal(it) }.asState(this, 0)
    val entries = c.today.flatMapLatest { c.tracking.waterEntries(it) }.asState(this, emptyList())
    val daily = c.tracking.waterDaily().asState(this, emptyList())
    val goals = c.settings.goals.asState(this, Goals())
    fun add(ml: Int) = viewModelScope.launch { c.tracking.addWater(c.today.value, ml) }
    fun delete(e: br.com.projeto40dias.data.db.WaterLogEntity) = viewModelScope.launch { c.tracking.deleteWater(e) }
    fun setGoal(ml: Int) = viewModelScope.launch { c.settings.setWaterGoal(ml.coerceIn(1000, 6000)) }
}

@Composable
fun WaterScreen(onBack: () -> Unit) {
    val vm = appViewModel { WaterViewModel(it) }
    val total by vm.total.collectAsState()
    val entries by vm.entries.collectAsState()
    val daily by vm.daily.collectAsState()
    val goals by vm.goals.collectAsState()
    val goal = goals.waterMl
    val pct = if (goal > 0) total.toFloat() / goal else 0f

    SubScreen("Hidratação", onBack) {
        item {
            P40Card {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(pct, 170.dp, 14.dp, P40Colors.Water) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${fmt(total / 1000.0, 2)} L", fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text("${(pct * 100).toInt()}% da meta", fontSize = 12.sp, color = P40Colors.TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TileRow {
                        StatTile("Consumido", "${total} ml", Modifier.weight(1f), valueColor = P40Colors.Water)
                        StatTile("Restante", "${(goal - total).coerceAtLeast(0)} ml", Modifier.weight(1f))
                        StatTile("Meta", "${fmt(goal / 1000.0)} L", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(200, 300, 500).forEach { ml ->
                            Button(onClick = { vm.add(ml) }, colors = ButtonDefaults.buttonColors(containerColor = P40Colors.Water, contentColor = Color.Black)) {
                                Text("+$ml ml", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            P40Card {
                SectionLabel("Ajustar meta")
                Text("Aumente em dias de treino longo, calor ou muita transpiração.", fontSize = 12.sp, color = P40Colors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setGoal(goal - 250) }) { Text("−250 ml") }
                    Text("${fmt(goal / 1000.0, 2)} L", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.setGoal(goal + 250) }) { Text("+250 ml") }
                }
            }
        }
        if (entries.isNotEmpty()) {
            item { SectionLabel("Registros de hoje") }
            items(entries, key = { it.id }) { e ->
                val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.timestamp), ZoneId.systemDefault()).toLocalTime()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d:%02d".format(t.hour, t.minute), color = P40Colors.TextSecondary, modifier = Modifier.width(56.dp))
                    Text("${e.ml} ml", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { vm.delete(e) }) { Icon(Icons.Filled.Delete, "Remover", tint = P40Colors.TextSecondary) }
                }
            }
        }
        item { SectionLabel("Histórico diário") }
        items(daily, key = { it.date }) { d ->
            Column {
                Row {
                    Text(fmtDate(LocalDate.parse(d.date)), Modifier.weight(1f))
                    Text("${fmt(d.total / 1000.0, 2)} L", fontWeight = FontWeight.Bold, color = if (d.total >= goal) P40Colors.Lime else P40Colors.Text)
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar(d.total.toFloat() / goal, P40Colors.Water, 5.dp)
            }
        }
    }
}

// =================================================================== SUPLEMENTOS

@OptIn(ExperimentalCoroutinesApi::class)
class SupplementsViewModel(private val c: AppContainer) : ViewModel() {
    val list = c.tracking.supplements().asState(this, emptyList())
    val done = c.today.flatMapLatest { c.tracking.supplementLogs(it) }.asState(this, emptyList())
    fun toggle(id: Long, d: Boolean) = viewModelScope.launch { c.tracking.setSupplement(c.today.value, id, d) }
}

@Composable
fun SupplementsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = appViewModel { SupplementsViewModel(it) }
    val list by vm.list.collectAsState()
    val logs by vm.done.collectAsState()
    val doneIds = logs.filter { it.done }.map { it.supplementId }.toSet()

    SubScreen("Suplementação", onBack) {
        items(list, key = { it.id }) { s ->
            P40Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.id in doneIds, { vm.toggle(s.id, it) }, colors = CheckboxDefaults.colors(checkedColor = P40Colors.Lime, checkmarkColor = Color.Black))
                    Column(Modifier.weight(1f)) {
                        Text(s.name.uppercase(), fontWeight = FontWeight.Bold)
                        Text(s.dose, color = P40Colors.Lime, fontSize = 13.sp)
                        Text(s.notes, color = P40Colors.TextSecondary, fontSize = 12.sp)
                    }
                    if (s.id in doneIds) Pill("FEITO HOJE", P40Colors.Lime)
                }
            }
        }
        item { OutlinedButton(onClick = { onNavigate(Routes.REMINDERS) }) { Text("Configurar lembretes") } }
        item { Notice("A dose de whey já está incluída na Refeição 1 do cardápio e entra no cálculo de proteínas quando a refeição é concluída.") }
    }
}

// =================================================================== PROGRESSÃO E RELATÓRIOS

data class ProjectData(
    val project: ProjectEntity,
    val days: List<DaySummary>,
    val measurements: List<BodyMeasurementEntity>,
    val profile: Profile,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDataViewModel(private val c: AppContainer) : ViewModel() {
    val today = c.today
    val data = c.project.active().filterNotNull().flatMapLatest { p ->
        val start = LocalDate.parse(p.startDate)
        combine(c.reports.daySummaries(start, ProjectCalculator.end(start, p.lengthDays)), c.body.all(), c.settings.profile) { d, m, pr ->
            ProjectData(p, d, m, pr)
        }
    }.asState(this, null)
}

private fun ProjectData.weights() = measurements.mapNotNull { m -> m.weightKg?.let { DatedValue(LocalDate.parse(m.date), it) } }
private fun ProjectData.waists() = measurements.mapNotNull { m -> m.waistCm?.let { DatedValue(LocalDate.parse(m.date), it) } }

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProjectDataViewModel(it) }
    val data by vm.data.collectAsState()
    val today by vm.today.collectAsState()
    val d = data

    SubScreen("Progressão dos 40 dias", onBack) {
        if (d == null) { item { Text("Carregando…", color = P40Colors.TextSecondary) }; return@SubScreen }
        val start = LocalDate.parse(d.project.startDate)
        val st = ProjectCalculator.status(start, today, d.project.lengthDays)
        val past = d.days.filter { !it.date.isAfter(today) }

        item {
            P40Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(st.progress, 110.dp, 10.dp) {
                        Text("${(st.progress * 100).toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (st.started) "Dia ${st.dayNumber} de ${st.length}" else "Começa em ${fmtDate(start)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${fmtDate(start)} → ${fmtDate(ProjectCalculator.end(start, st.length))}", fontSize = 12.sp, color = P40Colors.TextSecondary)
                        Text("Dias concluídos: ${st.daysCompleted} · restantes: ${st.daysRemaining}", fontSize = 12.sp, color = P40Colors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                TileRow {
                    StatTile("Treinos", "${past.sumOf { it.sessionsDone }}/${past.sumOf { it.sessionsScheduled }}", Modifier.weight(1f), sub = "realizados/programados")
                    StatTile("Refeições", "${past.sumOf { it.mealsDone }}/${past.sumOf { it.mealsScheduled }}", Modifier.weight(1f), sub = "concluídas/programadas")
                }
            }
        }
        items(ProjectCalculator.phases, key = { it.index }) { ph ->
            val from = ProjectCalculator.dateOfDay(start, ph.firstDay)
            val to = ProjectCalculator.dateOfDay(start, ph.lastDay)
            val current = st.phase?.index == ph.index && !st.finished
            val doneP = st.dayNumber > ph.lastDay || st.finished
            val inPhase = (st.dayNumber - ph.firstDay + 1).coerceIn(0, ph.lastDay - ph.firstDay + 1)
            val total = ph.lastDay - ph.firstDay + 1
            P40Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ph.title, fontWeight = FontWeight.Bold, color = if (current) P40Colors.Lime else P40Colors.Text)
                        Text("Dias ${ph.firstDay}–${ph.lastDay} · ${fmtShort(from)} a ${fmtShort(to)}", fontSize = 12.sp, color = P40Colors.TextSecondary)
                    }
                    when {
                        doneP -> Pill("CONCLUÍDA", P40Colors.Lime, filled = true)
                        current -> Pill("ATUAL", P40Colors.Lime)
                        else -> Pill("A SEGUIR", P40Colors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(if (doneP) 1f else inPhase.toFloat() / total, height = 5.dp)
            }
        }
        item {
            P40Card {
                SectionLabel("Evolução do peso no projeto")
                Spacer(Modifier.height(8.dp))
                LineChart(d.weights().filter { !it.date.isBefore(start.minusDays(7)) }.map { ChartPoint(it.date, it.value) }, "kg", goal = d.project.targetWeightKg)
            }
        }
        item {
            P40Card {
                SectionLabel("Evolução da cintura")
                Spacer(Modifier.height(8.dp))
                LineChart(d.waists().filter { !it.date.isBefore(start.minusDays(7)) }.map { ChartPoint(it.date, it.value) }, "cm")
            }
        }
    }
}

@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProjectDataViewModel(it) }
    val data by vm.data.collectAsState()
    val today by vm.today.collectAsState()
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val d = data

    SubScreen("Relatórios", onBack) {
        if (d == null) { item { Text("Carregando…", color = P40Colors.TextSecondary) }; return@SubScreen }
        val start = LocalDate.parse(d.project.startDate)
        val end = ProjectCalculator.end(start, d.project.lengthDays)
        val weeks = ReportCalculator.weekly(start, d.project.lengthDays, today, d.days, d.weights(), d.waists())
        val final = ReportCalculator.period("Relatório final dos 40 dias", start, end, today, d.days, d.weights(), d.waists())
        val st = ProjectCalculator.status(start, today, d.project.lengthDays)

        item {
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            val file = withContext(Dispatchers.IO) {
                                ReportPdfExporter.export(
                                    ctx,
                                    ReportPdfExporter.Header(
                                        d.profile.name, "${fmtDate(start)} a ${fmtDate(end)}", d.profile.initialWeightKg,
                                        d.measurements.latest { it.weightKg }?.first, d.project.targetWeightKg,
                                        if (st.finished) "Projeto concluído" else "Dia ${st.dayNumber} de ${st.length} (relatório parcial)",
                                    ),
                                    weeks, final,
                                )
                            }
                            ReportPdfExporter.share(ctx, file)
                        }.onFailure { error = it.message }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = P40Colors.Lime, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Exportar relatório em PDF", fontWeight = FontWeight.Bold) }
        }
        error?.let { item { Notice("Falha ao exportar: $it", warning = true) } }
        item { ReportCard(final.copy(title = if (st.finished) "RELATÓRIO FINAL · 40 DIAS" else "RESUMO ATÉ HOJE · ${st.dayNumber}/40"), highlight = true) }
        items(weeks, key = { it.title }) { w -> ReportCard(w, highlight = false, future = w.from.isAfter(today)) }
        item { Notice("Médias de calorias e proteínas consideram somente dias com refeições concluídas e são estimativas. Pesos e cintura são os valores medidos que você registrou.") }
    }
}

@Composable
private fun ReportCard(r: WeekReport, highlight: Boolean, future: Boolean = false) {
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, fontWeight = FontWeight.Bold, color = if (highlight) P40Colors.Lime else P40Colors.Text, modifier = Modifier.weight(1f))
            if (future) Pill("A SEGUIR", P40Colors.TextSecondary)
        }
        Text("${fmtDate(r.from)} a ${fmtDate(r.to)}", fontSize = 12.sp, color = P40Colors.TextSecondary)
        if (!future) {
        Spacer(Modifier.height(8.dp))
        TileRow {
            StatTile("Peso início", r.weightStart?.let { "${fmt(it)} kg" } ?: "—", Modifier.weight(1f))
            StatTile("Peso fim", r.weightEnd?.let { "${fmt(it)} kg" } ?: "—", Modifier.weight(1f))
            StatTile("Variação", r.weightDelta?.let { (if (it > 0) "+" else "") + fmt(it) + " kg" } ?: "—", Modifier.weight(1f),
                valueColor = r.weightDelta?.let { if (it <= 0) P40Colors.Lime else P40Colors.Warning } ?: P40Colors.Text)
        }
        Spacer(Modifier.height(8.dp))
        TileRow {
            StatTile("Média kcal", r.avgKcal?.let { fmt0(it) } ?: "—", Modifier.weight(1f))
            StatTile("Média prot.", r.avgProtein?.let { "${fmt0(it)} g" } ?: "—", Modifier.weight(1f))
            StatTile("Cintura", r.waistCm?.let { "${fmt(it)} cm" } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        TileRow {
            StatTile("Treinos", "${r.sessionsDone}/${r.sessionsScheduled}", Modifier.weight(1f), sub = "feitos/programados")
            StatTile("Dieta", r.dietAdherencePct?.let { "$it%" } ?: "—", Modifier.weight(1f), sub = "adesão")
            StatTile("Exercícios", r.exerciseAdherencePct?.let { "$it%" } ?: "—", Modifier.weight(1f), sub = "adesão")
        }
    }
    }
}
