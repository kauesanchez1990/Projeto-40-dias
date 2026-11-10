package br.com.projeto40dias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.db.ProjectEntity
import br.com.projeto40dias.data.prefs.Goals
import br.com.projeto40dias.data.prefs.Profile
import br.com.projeto40dias.domain.NutritionCalculator
import br.com.projeto40dias.domain.ProjectCalculator
import br.com.projeto40dias.ui.components.ClickableRow
import br.com.projeto40dias.ui.components.KindPill
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.NumberField
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.components.ScreenTitle
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.ValueKind
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmt0
import br.com.projeto40dias.ui.components.fmtDate
import br.com.projeto40dias.ui.components.parseNum
import br.com.projeto40dias.ui.nav.Routes
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    val profile = c.settings.profile.asState(this, Profile())
    val goals = c.settings.goals.asState(this, Goals())
    val project = c.project.active().asState(this, null)
    val projects = c.project.all().asState(this, emptyList())

    fun saveProfile(p: Profile) = viewModelScope.launch { c.settings.saveProfile(p) }
    fun saveGoals(g: Goals) = viewModelScope.launch {
        c.settings.saveGoals(g)
        c.project.active().first()?.let { c.project.update(it.copy(targetWeightKg = g.targetWeightKg)) }
    }
    fun setStart(d: LocalDate) = viewModelScope.launch {
        c.project.active().first()?.let { c.project.update(it.copy(startDate = d.toString())) }
    }
    fun restart(d: LocalDate) = viewModelScope.launch { c.project.restart(d, goals.value.targetWeightKg) }
}

@Composable
fun ProfileScreen(onNavigate: (String) -> Unit) {
    val vm = appViewModel { ProfileViewModel(it) }
    val p by vm.profile.collectAsState()
    val g by vm.goals.collectAsState()
    val project by vm.project.collectAsState()
    val projects by vm.projects.collectAsState()
    val ctx = LocalContext.current
    var editProfile by remember { mutableStateOf(false) }
    var editGoals by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }

    ScreenList {
        item { ScreenTitle("PERFIL", p.name) }

        item {
            P40Card(onClick = { editProfile = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Dados pessoais e avaliação de referência", Modifier.weight(1f))
                    Text("Editar", color = P40Colors.Lime, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Line("Sexo / idade", "${p.sex} · ${p.age} anos")
                Line("Altura", "${fmt0(p.heightCm)} cm")
                Line("Peso inicial", "${fmt(p.initialWeightKg)} kg")
                Line("Massa muscular esquelética", "${fmt(p.muscleKg)} kg")
                Line("Massa de gordura", "${fmt(p.fatMassKg)} kg")
                Line("Percentual de gordura", "${fmt(p.fatPct)}%")
                Line("Gordura visceral", "nível ${fmt0(p.visceral)}")
                Line("Relação cintura/quadril", fmt(p.waistHipRatio, 2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Line("Metabolismo basal", "${fmt0(p.bmrKcal.toDouble())} kcal", Modifier.weight(1f)); KindPill(ValueKind.ESTIMADO)
                }
                Text("Bioimpedância de referência: ${fmtDate(LocalDate.parse(p.bioDate))}", fontSize = 11.sp, color = P40Colors.TextSecondary)
            }
        }

        item {
            P40Card(onClick = { editGoals = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Metas", Modifier.weight(1f)); KindPill(ValueKind.META)
                    Spacer(Modifier.width(8.dp)); Text("Editar", color = P40Colors.Lime, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Line("Calorias", "${fmt0(g.kcal.toDouble())} kcal/dia")
                Line("Proteínas", "${g.protein} g")
                Line("Carboidratos", "${g.carbs} g")
                Line("Gorduras", "${g.fat} g")
                Line("Água", "${fmt(g.waterMl / 1000.0)} L/dia")
                Line("Peso-meta", "${fmt(g.targetWeightKg)} kg")
            }
        }

        item {
            val pr = project
            P40Card {
                SectionLabel("Projeto atual")
                Spacer(Modifier.height(8.dp))
                if (pr != null) {
                    val start = LocalDate.parse(pr.startDate)
                    Line("Início", fmtDate(start))
                    Line("Término", fmtDate(ProjectCalculator.end(start, pr.lengthDays)))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickDate(ctx, start) { vm.setStart(it) } }) { Text("Ajustar data de início") }
                        OutlinedButton(onClick = { confirmRestart = true }) { Text("Reiniciar") }
                    }
                }
            }
        }

        item {
            P40Card {
                Nav("Lembretes e notificações", Icons.Filled.Notifications) { onNavigate(Routes.REMINDERS) }
                Nav("Editar dieta e horários", Icons.Filled.Restaurant) { onNavigate(Routes.DIET_PLAN) }
                @Suppress("DEPRECATION")
                Nav("Editar treino e horários", Icons.Filled.DirectionsRun) { onNavigate(Routes.TRAINING_PLAN) }
                Nav("Progressão dos 40 dias", Icons.Filled.CalendarMonth) { onNavigate(Routes.PROGRESS) }
                Nav("Relatórios e PDF", Icons.Filled.Insights) { onNavigate(Routes.REPORTS) }
                Nav("Hidratação", Icons.Filled.WaterDrop) { onNavigate(Routes.WATER) }
                Nav("Suplementação", Icons.Filled.Medication) { onNavigate(Routes.SUPPLEMENTS) }
            }
        }

        if (projects.size > 1) {
            item { SectionLabel("Histórico de projetos") }
            items(projects, key = { it.id }) { pj -> ProjectRow(pj) }
        }

        item {
            P40Card {
                SectionLabel("Segurança e responsabilidade")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Este aplicativo é uma ferramenta de acompanhamento pessoal e não substitui nutricionista, médico ou profissional de educação física.\n\n" +
                        "• Metas calóricas são estimativas.\n" +
                        "• A perda de gordura visceral não pode ser direcionada isoladamente.\n" +
                        "• Bioimpedância varia conforme hidratação e condições de medição.\n" +
                        "• O app não aceita metas calóricas abaixo do metabolismo basal (restrição extrema).\n" +
                        "• Com sinais de fadiga, o app não sugere treinos intensos ou opcionais.",
                    fontSize = 12.sp, color = P40Colors.TextSecondary,
                )
            }
        }
        item { Text("PROJETO 40 DIAS · v1.0 · dados salvos apenas neste aparelho", fontSize = 11.sp, color = P40Colors.TextSecondary) }
    }

    if (editProfile) ProfileDialog(p, onDismiss = { editProfile = false }) { vm.saveProfile(it); editProfile = false }
    if (editGoals) GoalsDialog(g, p.bmrKcal, onDismiss = { editGoals = false }) { vm.saveGoals(it); editGoals = false }
    if (confirmRestart) AlertDialog(
        onDismissRequest = { confirmRestart = false },
        title = { Text("Reiniciar projeto?") },
        text = { Text("Um novo ciclo de 40 dias começa hoje. Todo o histórico (refeições, treinos, medidas e relatórios anteriores) continua salvo.") },
        confirmButton = { TextButton(onClick = { vm.restart(LocalDate.now()); confirmRestart = false }) { Text("Reiniciar") } },
        dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun ProjectRow(pj: ProjectEntity) {
    val s = LocalDate.parse(pj.startDate)
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${fmtDate(s)} → ${fmtDate(ProjectCalculator.end(s, pj.lengthDays))}", Modifier.weight(1f))
            if (pj.active) Pill("ATUAL", P40Colors.Lime) else Pill("ARQUIVADO", P40Colors.TextSecondary)
        }
        Text("Meta: ${fmt(pj.targetWeightKg)} kg", fontSize = 12.sp, color = P40Colors.TextSecondary)
    }
}

@Composable
private fun Line(k: String, v: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(1f), color = P40Colors.TextSecondary, fontSize = 13.sp)
        Text(v, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun Nav(label: String, icon: ImageVector, onClick: () -> Unit) {
    ClickableRow(onClick) {
        Icon(icon, null, tint = P40Colors.Lime)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = P40Colors.TextSecondary)
    }
}

@Composable
private fun ProfileDialog(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(p.name) }
    var sex by remember { mutableStateOf(p.sex) }
    var age by remember { mutableStateOf(p.age.toString()) }
    var height by remember { mutableStateOf(fmt0(p.heightCm)) }
    var w by remember { mutableStateOf(fmt(p.initialWeightKg)) }
    var mus by remember { mutableStateOf(fmt(p.muscleKg)) }
    var fm by remember { mutableStateOf(fmt(p.fatMassKg)) }
    var fp by remember { mutableStateOf(fmt(p.fatPct)) }
    var vis by remember { mutableStateOf(fmt0(p.visceral)) }
    var whr by remember { mutableStateOf(fmt(p.waistHipRatio, 2)) }
    var bmr by remember { mutableStateOf(p.bmrKcal.toString()) }
    var bio by remember { mutableStateOf(LocalDate.parse(p.bioDate)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar perfil") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sex, { sex = it }, label = { Text("Sexo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(age, { age = it }, "Idade", unit = "anos")
                NumberField(height, { height = it }, "Altura", unit = "cm")
                NumberField(w, { w = it }, "Peso inicial", unit = "kg")
                NumberField(mus, { mus = it }, "Massa muscular esquelética", unit = "kg")
                NumberField(fm, { fm = it }, "Massa de gordura", unit = "kg")
                NumberField(fp, { fp = it }, "Percentual de gordura", unit = "%")
                NumberField(vis, { vis = it }, "Gordura visceral (nível)")
                NumberField(whr, { whr = it }, "Relação cintura/quadril")
                NumberField(bmr, { bmr = it }, "Metabolismo basal estimado", unit = "kcal")
                OutlinedButton(onClick = { pickDate(ctx, bio) { bio = it } }) { Text("Data da bioimpedância: ${fmtDate(bio)}") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(p.copy(
                    name = name.trim().ifEmpty { p.name }, sex = sex.trim(), age = age.trim().toIntOrNull() ?: p.age,
                    heightCm = parseNum(height) ?: p.heightCm, initialWeightKg = parseNum(w) ?: p.initialWeightKg,
                    muscleKg = parseNum(mus) ?: p.muscleKg, fatMassKg = parseNum(fm) ?: p.fatMassKg, fatPct = parseNum(fp) ?: p.fatPct,
                    visceral = parseNum(vis) ?: p.visceral, waistHipRatio = parseNum(whr) ?: p.waistHipRatio,
                    bmrKcal = bmr.trim().toIntOrNull() ?: p.bmrKcal, bioDate = bio.toString(),
                ))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun GoalsDialog(g: Goals, bmr: Int, onDismiss: () -> Unit, onSave: (Goals) -> Unit) {
    var kcal by remember { mutableStateOf(g.kcal.toString()) }
    var p by remember { mutableStateOf(g.protein.toString()) }
    var c by remember { mutableStateOf(g.carbs.toString()) }
    var f by remember { mutableStateOf(g.fat.toString()) }
    var water by remember { mutableStateOf(g.waterMl.toString()) }
    var target by remember { mutableStateOf(fmt(g.targetWeightKg)) }

    val k = kcal.trim().toIntOrNull()
    val pi = p.trim().toIntOrNull(); val ci = c.trim().toIntOrNull(); val fi = f.trim().toIntOrNull()
    val min = NutritionCalculator.safeMinimumKcal(bmr)
    val tooLow = k != null && k < min
    val fromMacros = if (pi != null && ci != null && fi != null) NutritionCalculator.kcalFromMacros(pi.toDouble(), ci.toDouble(), fi.toDouble()) else null
    val valid = k != null && !tooLow && pi != null && ci != null && fi != null && water.trim().toIntOrNull() != null && parseNum(target) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar metas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(kcal, { kcal = it }, "Calorias", unit = "kcal")
                NumberField(p, { p = it }, "Proteínas", unit = "g")
                NumberField(c, { c = it }, "Carboidratos", unit = "g")
                NumberField(f, { f = it }, "Gorduras", unit = "g")
                if (fromMacros != null) Text("Macros somam ≈ ${fmt0(fromMacros)} kcal (4/4/9)", fontSize = 12.sp, color = P40Colors.TextSecondary)
                NumberField(water, { water = it }, "Água", unit = "ml")
                NumberField(target, { target = it }, "Peso-meta", unit = "kg")
                if (tooLow) Notice("Meta abaixo de ${min} kcal (metabolismo basal informado). O app não permite restrição calórica extrema — converse com um nutricionista.", warning = true)
                else Notice("Metas calóricas são estimativas. Ajuste com orientação profissional.")
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(Goals(k!!, pi!!, ci!!, fi!!, water.trim().toInt(), parseNum(target)!!))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
