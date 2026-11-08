package br.com.projeto40dias.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.db.BodyMeasurementEntity
import br.com.projeto40dias.data.db.CheckinEntity
import br.com.projeto40dias.data.db.ProjectEntity
import br.com.projeto40dias.data.db.SupplementEntity
import br.com.projeto40dias.data.prefs.Goals
import br.com.projeto40dias.data.prefs.Profile
import br.com.projeto40dias.data.repo.DayNutrition
import br.com.projeto40dias.data.repo.SessionDay
import br.com.projeto40dias.domain.Motivation
import br.com.projeto40dias.domain.ProjectCalculator
import br.com.projeto40dias.domain.Recovery
import br.com.projeto40dias.ui.components.KindPill
import br.com.projeto40dias.ui.components.MacroBar
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.ProgressBar
import br.com.projeto40dias.ui.components.ProgressRing
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.StatTile
import br.com.projeto40dias.ui.components.TileRow
import br.com.projeto40dias.ui.components.ValueKind
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmt0
import br.com.projeto40dias.ui.components.fmtDate
import br.com.projeto40dias.ui.components.fmtShort
import br.com.projeto40dias.ui.components.weekdayName
import br.com.projeto40dias.ui.nav.Routes
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val profile: Profile = Profile(),
    val goals: Goals = Goals(),
    val project: ProjectEntity? = null,
    val nutrition: DayNutrition? = null,
    val water: Int = 0,
    val sessions: List<SessionDay> = emptyList(),
    val measurements: List<BodyMeasurementEntity> = emptyList(),
    val supplements: List<SupplementEntity> = emptyList(),
    val suppDone: Set<Long> = emptySet(),
    val checkin: CheckinEntity? = null,
)

private data class DayPart(val n: DayNutrition, val water: Int, val sessions: List<SessionDay>, val supp: List<SupplementEntity>, val suppDone: Set<Long>)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    private val dayFlow = c.today.onEach { c.nutrition.ensureDay(it) }.flatMapLatest { d ->
        combine(
            c.nutrition.observeDay(d), c.tracking.waterTotal(d), c.training.observeDay(d),
            c.tracking.supplements(), c.tracking.supplementLogs(d),
        ) { n, w, s, sp, logs -> DayPart(n, w, s, sp, logs.filter { it.done }.map { it.supplementId }.toSet()) }
    }

    private val checkinFlow = c.today.flatMapLatest { c.tracking.checkin(it) }

    val state = combine(
        combine(c.settings.profile, c.settings.goals, c.project.active()) { a, b, pr -> Triple(a, b, pr) },
        dayFlow, c.body.all(), checkinFlow, c.today,
    ) { (p, g, proj), day, m, ck, today ->
        HomeState(today, p, g, proj, day.n, day.water, day.sessions, m, day.supp, day.suppDone, ck)
    }.asState(this, HomeState())

    fun toggleSupplement(id: Long, done: Boolean) = viewModelScope.launch { c.tracking.setSupplement(c.today.value, id, done) }
    fun saveCheckin(f: Int, s: Int, sl: Int) = viewModelScope.launch {
        c.tracking.saveCheckin(CheckinEntity(c.today.value.toString(), f, s, sl))
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsState()
    val project = s.project
    val start = project?.let { LocalDate.parse(it.startDate) } ?: s.today
    val status = ProjectCalculator.status(start, s.today, project?.lengthDays ?: 40)
    val target = project?.targetWeightKg ?: s.goals.targetWeightKg

    val weight = s.measurements.latest { it.weightKg }
    val fatPct = s.measurements.latest { it.fatPct }
    val fatMass = s.measurements.latest { it.fatMassKg }
    val visceral = s.measurements.latest { it.visceral }
    val lastBio = s.measurements.filter { it.fatMassKg != null && it.weightKg != null }.maxByOrNull { it.date }

    ScreenList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PROJETO 40 DIAS", style = MaterialTheme.typography.headlineMedium, color = P40Colors.Lime, fontWeight = FontWeight.Black)
                    Text("Olá, ${s.profile.name.substringBefore(' ')} · ${weekdayName(s.today)}, ${fmtShort(s.today)}",
                        color = P40Colors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- Progresso do projeto
        item {
            P40Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(status.progress, 132.dp, 12.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (status.started) "DIA ${status.dayNumber}" else "EM BREVE", fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("DE ${status.length}", fontSize = 11.sp, color = P40Colors.TextSecondary, fontWeight = FontWeight.Bold)
                            Text("${(status.progress * 100).toInt()}%", fontSize = 14.sp, color = P40Colors.Lime, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KeyValue("Peso inicial", "${fmt(s.profile.initialWeightKg)} kg")
                        KeyValue("Peso atual", weight?.let { "${fmt(it.first)} kg" } ?: "—", P40Colors.Lime)
                        KeyValue("Meta", "${fmt(target)} kg")
                        KeyValue("Dias restantes", "${status.daysRemaining}")
                        status.phase?.let { Text(it.title, fontSize = 11.sp, color = P40Colors.TextSecondary) }
                        if (!status.started) Text("Início em ${fmtDate(start)}", fontSize = 11.sp, color = P40Colors.Warning)
                    }
                }
                if (weight != null) {
                    Spacer(Modifier.height(12.dp))
                    val lost = s.profile.initialWeightKg - weight.first
                    val toGo = s.profile.initialWeightKg - target
                    ProgressBar(if (toGo > 0) (lost / toGo).toFloat() else 0f)
                    Spacer(Modifier.height(4.dp))
                    Text("${fmt(lost)} kg de ${fmt(toGo)} kg · peso medido em ${fmtDate(weight.second)}", fontSize = 11.sp, color = P40Colors.TextSecondary)
                }
            }
        }

        // ---- Mensagem do dia
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P40Colors.Lime.copy(alpha = 0.12f)).padding(14.dp)) {
                Text("“${Motivation.forDate(s.today)}”", color = P40Colors.Lime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        // ---- Composição corporal
        item { Column {
            SectionLabel("Composição corporal")
            Spacer(Modifier.height(8.dp))
            TileRow {
                StatTile("Gordura", fatPct?.let { "${fmt(it.first)}%" } ?: "—", Modifier.weight(1f),
                    sub = fatPct?.let { fmtDate(it.second) }, kind = ValueKind.MEDIDO)
                StatTile("Massa gorda", fatMass?.let { "${fmt(it.first)} kg" } ?: "—", Modifier.weight(1f),
                    sub = fatMass?.let { fmtDate(it.second) }, kind = ValueKind.MEDIDO)
                StatTile("Visceral", visceral?.let { "nível ${fmt0(it.first)}" } ?: "—", Modifier.weight(1f),
                    sub = visceral?.let { fmtDate(it.second) }, kind = ValueKind.MEDIDO)
            }
            // Estimativa transparente: assume que a variação de peso desde a última bioimpedância foi gordura.
            if (lastBio != null && weight != null && weight.second.isAfter(LocalDate.parse(lastBio.date))) {
                Spacer(Modifier.height(10.dp))
                val est = (lastBio.fatMassKg!! - (lastBio.weightKg!! - weight.first)).coerceAtLeast(0.0)
                TileRow {
                    StatTile("Gordura corporal estimada", "${fmt(est)} kg", Modifier.weight(1f),
                        sub = "Supõe que toda a variação de peso desde ${fmtDate(LocalDate.parse(lastBio.date))} foi gordura. Não é medição.",
                        kind = ValueKind.ESTIMADO, valueColor = P40Colors.Estimated)
                }
            }
        } }

        // ---- Nutrição de hoje
        item {
            val n = s.nutrition
            val consumed = n?.consumed
            P40Card(onClick = { onNavigate(Routes.DIET) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Calorias de hoje", Modifier.weight(1f))
                    Text("${n?.mealsDone ?: 0}/${n?.mealsScheduled ?: 6} refeições", fontSize = 12.sp, color = P40Colors.TextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt0(consumed?.kcal ?: 0.0), fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(" / ${fmt0(s.goals.kcal.toDouble())} kcal", color = P40Colors.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    KindPill(ValueKind.ESTIMADO)
                }
                ProgressBar(((consumed?.kcal ?: 0.0) / s.goals.kcal).toFloat())
                Spacer(Modifier.height(8.dp))
                MacroBar("Proteínas", consumed?.protein ?: 0.0, s.goals.protein.toDouble(), "g", P40Colors.Protein)
                MacroBar("Carboidratos", consumed?.carbs ?: 0.0, s.goals.carbs.toDouble(), "g", P40Colors.Carbs)
                MacroBar("Gorduras", consumed?.fat ?: 0.0, s.goals.fat.toDouble(), "g", P40Colors.Fat)
            }
        }

        // ---- Água e treinos
        item {
            val doneSessions = s.sessions.filter { !it.optional }.count { it.done }
            val totalSessions = s.sessions.count { !it.optional }
            TileRow {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(P40Colors.CardHigh).clickable { onNavigate(Routes.WATER) }.padding(12.dp)) {
                    SectionLabel("Água")
                    Text("${fmt(s.water / 1000.0)} L", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = P40Colors.Water)
                    Text("de ${fmt(s.goals.waterMl / 1000.0)} L", fontSize = 11.sp, color = P40Colors.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(s.water.toFloat() / s.goals.waterMl, P40Colors.Water, 6.dp)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(P40Colors.CardHigh).clickable { onNavigate(Routes.TRAINING) }.padding(12.dp)) {
                    SectionLabel("Treinos hoje")
                    Text(if (totalSessions == 0) "Descanso" else "$doneSessions/$totalSessions", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(s.sessions.filter { !it.optional }.joinToString(" · ") { it.name }.ifEmpty { "Recuperação" },
                        fontSize = 11.sp, color = P40Colors.TextSecondary, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(if (totalSessions > 0) doneSessions.toFloat() / totalSessions else 1f, height = 6.dp)
                }
            }
        }

        // ---- Suplementos
        if (s.supplements.isNotEmpty()) item {
            P40Card {
                SectionLabel("Suplementação de hoje")
                s.supplements.forEach { sp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sp.id in s.suppDone, onCheckedChange = { vm.toggleSupplement(sp.id, it) },
                            colors = CheckboxDefaults.colors(checkedColor = P40Colors.Lime, checkmarkColor = Color.Black))
                        Column {
                            Text(sp.name, fontWeight = FontWeight.SemiBold)
                            Text(sp.dose, fontSize = 12.sp, color = P40Colors.TextSecondary)
                        }
                    }
                }
            }
        }

        // ---- Check-in de recuperação
        item { CheckinCard(s.checkin, onSave = { a, b, d -> vm.saveCheckin(a, b, d) }) }

        // ---- Atalhos
        item { Column {
            TileRow {
                Shortcut("Hidratação", Icons.Filled.WaterDrop, Modifier.weight(1f)) { onNavigate(Routes.WATER) }
                Shortcut("Suplementos", Icons.Filled.Medication, Modifier.weight(1f)) { onNavigate(Routes.SUPPLEMENTS) }
            }
            Spacer(Modifier.height(10.dp))
            TileRow {
                Shortcut("40 dias", Icons.Filled.CalendarMonth, Modifier.weight(1f)) { onNavigate(Routes.PROGRESS) }
                Shortcut("Relatórios", Icons.Filled.Insights, Modifier.weight(1f)) { onNavigate(Routes.REPORTS) }
            }
        } }
        item { Notice("Calorias e macros são estimativas calculadas a partir do banco de alimentos. Não é possível direcionar a perda de gordura abdominal ou visceral isoladamente — ela acompanha a redução de gordura total.") }
    }
}

@Composable
private fun KeyValue(k: String, v: String, color: Color = P40Colors.Text) {
    Column {
        Text(k.uppercase(), fontSize = 10.sp, color = P40Colors.TextSecondary, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Text(v, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Shortcut(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(P40Colors.Card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = P40Colors.Lime, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

/** Check-in diário: fadiga, dor muscular e sono. Controla avisos e o treino opcional. */
@Composable
fun CheckinCard(c: CheckinEntity?, onSave: (Int, Int, Int) -> Unit) {
    val f = c?.fatigue ?: 0; val so = c?.soreness ?: 0; val sl = c?.sleep ?: 0
    P40Card {
        SectionLabel("Como você está hoje?")
        Spacer(Modifier.height(8.dp))
        ScaleRow("Fadiga", "1 descansado · 5 exausto", f) { onSave(it, so.takeIf { x -> x > 0 } ?: 1, sl.takeIf { x -> x > 0 } ?: 3) }
        ScaleRow("Dor muscular", "1 nenhuma · 5 muita", so) { onSave(f.takeIf { x -> x > 0 } ?: 1, it, sl.takeIf { x -> x > 0 } ?: 3) }
        ScaleRow("Sono", "1 péssimo · 5 ótimo", sl) { onSave(f.takeIf { x -> x > 0 } ?: 1, so.takeIf { x -> x > 0 } ?: 1, it) }
        if (c != null) {
            Spacer(Modifier.height(6.dp))
            when {
                Recovery.showFatigueWarning(c.fatigue, c.soreness, c.sleep) ->
                    Notice("Sinais de fadiga ou recuperação insuficiente. Priorize descanso, sono e treino leve hoje — o app não sugere treinos intensos ou opcionais nessas condições.", warning = true)
                Recovery.isRecovered(c.fatigue, c.soreness, c.sleep) ->
                    Text("Bem recuperado ✓ — treino opcional liberado quando programado.", fontSize = 12.sp, color = P40Colors.Lime)
                else -> Text("Recuperação moderada — siga o plano sem extras.", fontSize = 12.sp, color = P40Colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ScaleRow(label: String, hint: String, value: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(hint, fontSize = 10.sp, color = P40Colors.TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                val sel = n == value
                Box(
                    Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) P40Colors.Lime else P40Colors.CardHigh)
                        .clickable { onPick(n) },
                    contentAlignment = Alignment.Center,
                ) { Text("$n", color = if (sel) Color.Black else P40Colors.Text, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
