package br.com.projeto40dias.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import br.com.projeto40dias.data.db.MeasureSource
import br.com.projeto40dias.data.prefs.Goals
import br.com.projeto40dias.ui.components.ChartPoint
import br.com.projeto40dias.ui.components.KindPill
import br.com.projeto40dias.ui.components.LineChart
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.NumberField
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.components.ScreenTitle
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.ValueKind
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmtDate
import br.com.projeto40dias.ui.components.parseNum
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.launch
import java.time.LocalDate

class EvolutionViewModel(private val c: AppContainer) : ViewModel() {
    val measurements = c.body.all().asState(this, emptyList())
    val goals = c.settings.goals.asState(this, Goals())
    fun save(m: BodyMeasurementEntity) = viewModelScope.launch { c.body.save(m) }
    fun delete(m: BodyMeasurementEntity) = viewModelScope.launch { c.body.delete(m) }
}

enum class Metric(val label: String, val unit: String, val decimals: Int, val get: (BodyMeasurementEntity) -> Double?) {
    PESO("Peso", "kg", 1, { it.weightKg }),
    GORDURA_PCT("Gordura %", "%", 1, { it.fatPct }),
    MASSA_GORDA("Massa gorda", "kg", 1, { it.fatMassKg }),
    MUSCULO("Massa muscular", "kg", 1, { it.muscleKg }),
    CINTURA("Cintura", "cm", 1, { it.waistCm }),
    QUADRIL("Quadril", "cm", 1, { it.hipCm }),
    VISCERAL("Visceral", "nível", 0, { it.visceral }),
    RCQ("Cintura/quadril", "", 2, { it.waistHipRatio }),
}

@Composable
fun EvolutionScreen() {
    val vm = appViewModel { EvolutionViewModel(it) }
    val list by vm.measurements.collectAsState()
    val goals by vm.goals.collectAsState()
    var metric by remember { mutableStateOf(Metric.PESO) }
    var editing by remember { mutableStateOf<BodyMeasurementEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    ScreenList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle("MINHA EVOLUÇÃO", "Medidas e composição corporal") }
                Button(onClick = { creating = true }, colors = ButtonDefaults.buttonColors(containerColor = P40Colors.Lime, contentColor = Color.Black)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Registrar")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Legenda:", fontSize = 11.sp, color = P40Colors.TextSecondary)
                KindPill(ValueKind.MEDIDO); KindPill(ValueKind.ESTIMADO); KindPill(ValueKind.META)
            }
        }

        // ---- Gráfico
        item {
            P40Card {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Metric.PESO, Metric.GORDURA_PCT, Metric.MASSA_GORDA, Metric.MUSCULO, Metric.CINTURA, Metric.VISCERAL, Metric.RCQ).forEach { m ->
                        FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.label) }, colors = chipColors())
                    }
                }
                Spacer(Modifier.height(12.dp))
                val pts = list.mapNotNull { m -> metric.get(m)?.let { ChartPoint(LocalDate.parse(m.date), it) } }
                    .groupBy { it.date }.map { it.value.last() }
                LineChart(pts, metric.unit, goal = if (metric == Metric.PESO) goals.targetWeightKg else null, decimals = metric.decimals)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindPill(ValueKind.MEDIDO)
                    Text("  Apenas valores medidos e registrados por você.", fontSize = 11.sp, color = P40Colors.TextSecondary)
                }
            }
        }

        // ---- Comparação inicial × atual
        item {
            P40Card {
                SectionLabel("Avaliação inicial × última avaliação")
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("", Modifier.weight(1.3f))
                    Text("Inicial", Modifier.weight(1f), fontSize = 11.sp, color = P40Colors.TextSecondary)
                    Text("Atual", Modifier.weight(1f), fontSize = 11.sp, color = P40Colors.TextSecondary)
                    Text("Var.", Modifier.weight(0.9f), fontSize = 11.sp, color = P40Colors.TextSecondary)
                }
                Metric.entries.forEach { m ->
                    val first = list.earliest(m.get); val last = list.latest(m.get)
                    if (first != null && last != null) {
                        val d = last.first - first.first
                        // Para massa muscular, aumentar é positivo; nas demais, reduzir é positivo.
                        val good = if (m == Metric.MUSCULO) d >= 0 else d <= 0
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.label, Modifier.weight(1.3f), fontSize = 13.sp)
                            Text(fmt(first.first, m.decimals), Modifier.weight(1f), fontSize = 13.sp)
                            Text(fmt(last.first, m.decimals), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text((if (d > 0) "+" else "") + fmt(d, m.decimals), Modifier.weight(0.9f), fontSize = 13.sp,
                                color = if (d == 0.0) P40Colors.TextSecondary else if (good) P40Colors.Lime else P40Colors.Warning)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Meta de peso: ${fmt(goals.targetWeightKg)} kg  ", fontSize = 12.sp)
                    KindPill(ValueKind.META)
                }
            }
        }

        item {
            Notice("Resultados de bioimpedância podem variar conforme hidratação, horário, alimentação e treino. Para comparar, meça sempre nas mesmas condições (ex.: manhã, em jejum). O app nunca estima resultados de bioimpedância — registre somente medições reais.")
        }

        item { SectionLabel("Registros (${list.size})") }
        items(list.reversed(), key = { it.id }) { m ->
            P40Card(onClick = { editing = m }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDate(LocalDate.parse(m.date)), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Pill(m.source.label.uppercase(), P40Colors.Measured)
                }
                val parts = listOfNotNull(
                    m.weightKg?.let { "Peso ${fmt(it)} kg" }, m.waistCm?.let { "Cintura ${fmt(it)} cm" }, m.hipCm?.let { "Quadril ${fmt(it)} cm" },
                    m.fatPct?.let { "Gordura ${fmt(it)}%" }, m.fatMassKg?.let { "M. gorda ${fmt(it)} kg" }, m.muscleKg?.let { "M. muscular ${fmt(it)} kg" },
                    m.visceral?.let { "Visceral ${fmt(it, 0)}" }, m.waistHipRatio?.let { "RCQ ${fmt(it, 2)}" },
                )
                Text(parts.joinToString(" · "), fontSize = 12.sp, color = P40Colors.TextSecondary)
                if (m.notes.isNotBlank()) Text(m.notes, fontSize = 12.sp, color = P40Colors.TextSecondary)
            }
        }
    }

    if (creating) MeasurementDialog(null, onDismiss = { creating = false }, onDelete = null) { vm.save(it); creating = false }
    editing?.let { m -> MeasurementDialog(m, onDismiss = { editing = null }, onDelete = { vm.delete(m); editing = null }) { vm.save(it); editing = null } }
}

@Composable
private fun MeasurementDialog(m: BodyMeasurementEntity?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (BodyMeasurementEntity) -> Unit) {
    val ctx = LocalContext.current
    fun s(v: Double?, d: Int = 1) = v?.let { fmt(it, d) } ?: ""
    var date by remember { mutableStateOf(m?.date?.let { LocalDate.parse(it) } ?: LocalDate.now()) }
    var source by remember { mutableStateOf(m?.source ?: MeasureSource.BALANCA) }
    var weight by remember { mutableStateOf(s(m?.weightKg)) }
    var waist by remember { mutableStateOf(s(m?.waistCm)) }
    var hip by remember { mutableStateOf(s(m?.hipCm)) }
    var fatPct by remember { mutableStateOf(s(m?.fatPct)) }
    var fatMass by remember { mutableStateOf(s(m?.fatMassKg)) }
    var muscle by remember { mutableStateOf(s(m?.muscleKg)) }
    var visceral by remember { mutableStateOf(s(m?.visceral, 0)) }
    var whr by remember { mutableStateOf(s(m?.waistHipRatio, 2)) }
    var notes by remember { mutableStateOf(m?.notes ?: "") }
    val bio = source == MeasureSource.BIOIMPEDANCIA

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m == null) "Nova medição" else "Editar medição") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text("Data: ${fmtDate(date)}") }
                Column {
                    MeasureSource.entries.forEach { src ->
                        FilterChip(selected = source == src, onClick = { source = src }, label = { Text(src.label) }, colors = chipColors())
                    }
                }
                NumberField(weight, { weight = it }, "Peso corporal", unit = "kg")
                NumberField(waist, { waist = it }, "Circunferência abdominal", unit = "cm")
                NumberField(hip, { hip = it }, "Circunferência do quadril", unit = "cm")
                if (bio) {
                    Text("Dados da bioimpedância (copie do laudo)", fontSize = 12.sp, color = P40Colors.TextSecondary)
                    NumberField(fatPct, { fatPct = it }, "Percentual de gordura", unit = "%")
                    NumberField(fatMass, { fatMass = it }, "Massa de gordura", unit = "kg")
                    NumberField(muscle, { muscle = it }, "Massa muscular esquelética", unit = "kg")
                    NumberField(visceral, { visceral = it }, "Gordura visceral (nível)")
                }
                NumberField(whr, { whr = it }, "Relação cintura/quadril")
                val w = parseNum(waist); val h = parseNum(hip)
                if (w != null && h != null && h > 0 && whr.isBlank()) {
                    TextButton(onClick = { whr = fmt(w / h, 2) }) { Text("Calcular RCQ a partir das medidas: ${fmt(w / h, 2)}") }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Observações (jejum, horário…)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    BodyMeasurementEntity(
                        id = m?.id ?: 0, date = date.toString(), source = source,
                        weightKg = parseNum(weight), waistCm = parseNum(waist), hipCm = parseNum(hip),
                        fatPct = if (bio) parseNum(fatPct) else null,
                        fatMassKg = if (bio) parseNum(fatMass) else null,
                        muscleKg = if (bio) parseNum(muscle) else null,
                        visceral = if (bio) parseNum(visceral) else null,
                        waistHipRatio = parseNum(whr), notes = notes.trim(),
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Excluir", color = P40Colors.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
