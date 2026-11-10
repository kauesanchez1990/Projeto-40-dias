package br.com.projeto40dias.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.db.ExerciseTemplateEntity
import br.com.projeto40dias.data.db.ExerciseType
import br.com.projeto40dias.data.db.Session
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.NumberField
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class TrainingPlanViewModel(private val c: AppContainer) : ViewModel() {
    val week = c.training.observeWeekTemplates().asState(this, emptyList())
    val dow = MutableStateFlow(LocalDate.now().dayOfWeek.value)

    fun updateSession(dow: Int, old: Session, new: Session, name: String, time: String) =
        viewModelScope.launch { c.training.updateSession(dow, old, new, name, time) }
    fun addSession(dow: Int, s: Session, name: String, time: String, first: ExerciseTemplateEntity) =
        viewModelScope.launch { c.training.addSession(dow, s, name, time, first) }
    fun removeSession(dow: Int, s: Session) = viewModelScope.launch { c.training.removeSession(dow, s) }
    fun saveExercise(e: ExerciseTemplateEntity) = viewModelScope.launch { c.training.saveExercise(e) }
    fun removeExercise(id: Long) = viewModelScope.launch { c.training.removeExercise(id) }
    fun move(e: ExerciseTemplateEntity, delta: Int) = viewModelScope.launch { c.training.moveExercise(e, delta) }
}

private val dowLabels = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
private val dowFull = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

private fun ExerciseType.label() = when (this) {
    ExerciseType.FORCA -> "Musculação"; ExerciseType.CORRIDA -> "Corrida"; ExerciseType.MOBILIDADE -> "Mobilidade"
}

private fun defaultTime(s: Session) = when (s) { Session.MANHA -> "09:00"; Session.NOITE -> "20:00"; Session.DIA -> "10:00" }

/** Sessão (período do dia) com seus exercícios ativos. */
private data class PlanSession(val session: Session, val name: String, val time: String, val exercises: List<ExerciseTemplateEntity>)

/** Estado dos diálogos. */
private sealed interface PlanDialog {
    data class NewSession(val dow: Int) : PlanDialog
    data class EditSession(val dow: Int, val s: PlanSession) : PlanDialog
    data class RemoveSession(val dow: Int, val s: PlanSession) : PlanDialog
    data class Exercise(val dow: Int, val session: Session, val e: ExerciseTemplateEntity?) : PlanDialog
}

@Composable
fun TrainingPlanScreen(onBack: () -> Unit) {
    val vm = appViewModel { TrainingPlanViewModel(it) }
    val week by vm.week.collectAsState()
    val dow by vm.dow.collectAsState()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }

    val sessions = week.filter { it.dayOfWeek == dow }
        .groupBy { it.session }
        .map { (s, list) ->
            val sorted = list.sortedBy { it.position }
            PlanSession(s, sorted.first().sessionName, sorted.first().sessionTime, sorted)
        }
        .sortedBy { it.time }

    SubScreen("Editar treino", onBack) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { d ->
                    val sel = d == dow
                    val count = week.count { it.dayOfWeek == d }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) P40Colors.Lime else P40Colors.Card)
                            .clickable { vm.dow.value = d }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val fg = if (sel) Color.Black else P40Colors.Text
                        Text(dowLabels[d - 1], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
                        Text(if (count == 0) "—" else "$count", fontSize = 12.sp, color = if (sel) Color.Black else P40Colors.TextSecondary)
                    }
                }
            }
        }

        if (sessions.isEmpty()) item {
            P40Card { Text("Nada programado na ${dowFull[dow - 1]}. Toque em “Nova sessão” para adicionar.", color = P40Colors.TextSecondary) }
        }

        sessions.forEach { s ->
            item(key = "${dow}_${s.session.name}") {
                PlanSessionCard(
                    s,
                    onTime = { pickTime(ctx, s.time) { t -> vm.updateSession(dow, s.session, s.session, s.name, t) } },
                    onEdit = { dialog = PlanDialog.EditSession(dow, s) },
                    onRemove = { dialog = PlanDialog.RemoveSession(dow, s) },
                    onAdd = { dialog = PlanDialog.Exercise(dow, s.session, null) },
                    onExercise = { dialog = PlanDialog.Exercise(dow, s.session, it) },
                    onMove = { e, d -> vm.move(e, d) },
                )
            }
        }

        if (sessions.size < Session.entries.size) item {
            Button(
                onClick = { dialog = PlanDialog.NewSession(dow) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = P40Colors.Lime, contentColor = Color.Black),
            ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Nova sessão na ${dowFull[dow - 1]}") }
        }

        item {
            Notice("Cada dia pode ter até três sessões (manhã, noite e dia inteiro). Os lembretes de treino seguem os horários definidos aqui. " +
                "Exercícios removidos continuam no histórico dos dias em que foram registrados.")
        }
    }

    when (val d = dialog) {
        null -> {}
        is PlanDialog.NewSession -> SessionDialog(
            title = "Nova sessão",
            free = Session.entries.filter { e -> sessions.none { it.session == e } },
            initial = null, isNew = true,
            onDismiss = { dialog = null },
        ) { s, name, time, first ->
            if (first != null) vm.addSession(d.dow, s, name, time, first)
            dialog = null
        }
        is PlanDialog.EditSession -> SessionDialog(
            title = "Editar sessão",
            free = Session.entries.filter { e -> e == d.s.session || sessions.none { it.session == e } },
            initial = d.s, isNew = false,
            onDismiss = { dialog = null },
        ) { s, name, time, _ ->
            vm.updateSession(d.dow, d.s.session, s, name, time)
            dialog = null
        }
        is PlanDialog.RemoveSession -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Excluir ${d.s.name}?") },
            text = { Text("A sessão de ${d.s.session.label.lowercase()} da ${dowFull[d.dow - 1]} sai do treino e dos lembretes. O histórico é mantido.") },
            confirmButton = { TextButton(onClick = { vm.removeSession(d.dow, d.s.session); dialog = null }) { Text("Excluir", color = P40Colors.Danger) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
        is PlanDialog.Exercise -> ExerciseDialog(
            e = d.e,
            onDismiss = { dialog = null },
            onRemove = d.e?.let { e -> { vm.removeExercise(e.id); dialog = null } },
        ) { name, type, presc, sets, optional ->
            val base = d.e ?: ExerciseTemplateEntity(
                dayOfWeek = d.dow, session = d.session, sessionName = "", name = name, type = type,
                prescription = presc, position = 0, sessionTime = defaultTime(d.session),
            )
            vm.saveExercise(base.copy(name = name, type = type, prescription = presc, targetSets = sets, optional = optional))
            dialog = null
        }
    }
}

@Composable
private fun PlanSessionCard(
    s: PlanSession, onTime: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit, onAdd: () -> Unit,
    onExercise: (ExerciseTemplateEntity) -> Unit, onMove: (ExerciseTemplateEntity, Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onTime) { Text(s.time, fontWeight = FontWeight.Bold, color = P40Colors.Lime) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(s.session.label.uppercase(), fontSize = 11.sp, color = P40Colors.TextSecondary, fontWeight = FontWeight.Bold)
                Text(s.name, style = MaterialTheme.typography.titleMedium)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar nome, período e horário") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Adicionar exercício") }, onClick = { menu = false; onAdd() })
                    DropdownMenuItem(text = { Text("Excluir sessão", color = P40Colors.Danger) }, onClick = { menu = false; onRemove() })
                }
            }
        }
        HorizontalDivider(color = P40Colors.Track, modifier = Modifier.padding(vertical = 6.dp))
        s.exercises.forEachIndexed { i, e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onExercise(e) }.padding(vertical = 6.dp)) {
                    Text(e.name, fontWeight = FontWeight.SemiBold)
                    Text(e.prescription, fontSize = 12.sp, color = P40Colors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(e.type.label().uppercase(), P40Colors.TextSecondary)
                        if (e.optional) Pill("OPCIONAL", P40Colors.Lime)
                    }
                }
                IconButton(onClick = { onMove(e, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Subir") }
                IconButton(onClick = { onMove(e, 1) }, enabled = i < s.exercises.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Descer") }
            }
        }
        TextButton(onClick = onAdd) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Adicionar exercício") }
    }
}

/** Criar ou editar uma sessão. Na criação, já pede o primeiro exercício. */
@Composable
private fun SessionDialog(
    title: String, free: List<Session>, initial: PlanSession?, isNew: Boolean, onDismiss: () -> Unit,
    onSave: (Session, String, String, ExerciseTemplateEntity?) -> Unit,
) {
    val ctx = LocalContext.current
    var session by remember { mutableStateOf(initial?.session ?: free.firstOrNull() ?: Session.MANHA) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var time by remember { mutableStateOf(initial?.time ?: defaultTime(session)) }
    var exName by remember { mutableStateOf("") }
    var exType by remember { mutableStateOf(ExerciseType.FORCA) }
    var exPresc by remember { mutableStateOf("") }
    var exSets by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Período", fontSize = 12.sp, color = P40Colors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    free.forEach { s ->
                        FilterChip(session == s, {
                            if (isNew && time == defaultTime(session)) time = defaultTime(s)
                            session = s
                        }, label = { Text(s.label) }, colors = chipColors())
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nome (ex.: Superiores A)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickTime(ctx, time) { time = it } }) { Text("Horário: $time") }
                if (isNew) {
                    Spacer(Modifier.height(4.dp))
                    Text("Primeiro exercício", fontWeight = FontWeight.SemiBold)
                    ExerciseFields(exName, { exName = it }, exType, { exType = it }, exPresc, { exPresc = it }, exSets, { exSets = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (!isNew || exName.isNotBlank()),
                onClick = {
                    val first = if (isNew) ExerciseTemplateEntity(
                        dayOfWeek = 0, session = session, sessionName = name.trim(), name = exName.trim(), type = exType,
                        prescription = exPresc.trim(), targetSets = setsOf(exType, exSets), position = 0, sessionTime = time,
                    ) else null
                    onSave(session, name.trim(), time, first)
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun setsOf(type: ExerciseType, text: String): Int? =
    if (type == ExerciseType.FORCA) text.trim().toIntOrNull()?.takeIf { it in 1..20 } else null

@Composable
private fun ExerciseFields(
    name: String, onName: (String) -> Unit, type: ExerciseType, onType: (ExerciseType) -> Unit,
    presc: String, onPresc: (String) -> Unit, sets: String, onSets: (String) -> Unit,
) {
    OutlinedTextField(name, onName, label = { Text("Exercício (ex.: Supino reto)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ExerciseType.entries.forEach { t -> FilterChip(type == t, { onType(t) }, label = { Text(t.label()) }, colors = chipColors()) }
    }
    OutlinedTextField(presc, onPresc, label = { Text(if (type == ExerciseType.CORRIDA) "Prescrição (ex.: 40 min · Zona 2)" else "Prescrição (ex.: 4 × 8–10)") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    if (type == ExerciseType.FORCA) NumberField(sets, onSets, "Séries (meta)")
}

@Composable
private fun ExerciseDialog(
    e: ExerciseTemplateEntity?, onDismiss: () -> Unit, onRemove: (() -> Unit)?,
    onSave: (String, ExerciseType, String, Int?, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(e?.name ?: "") }
    var type by remember { mutableStateOf(e?.type ?: ExerciseType.FORCA) }
    var presc by remember { mutableStateOf(e?.prescription ?: "") }
    var sets by remember { mutableStateOf(e?.targetSets?.toString() ?: "") }
    var optional by remember { mutableStateOf(e?.optional ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e == null) "Novo exercício" else "Editar exercício") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseFields(name, { name = it }, type, { type = it }, presc, { presc = it }, sets, { sets = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(optional, { optional = it }, colors = CheckboxDefaults.colors(checkedColor = P40Colors.Lime, checkmarkColor = Color.Black))
                    Text("Opcional (liberado só com boa recuperação)", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), type, presc.trim(), setsOf(type, sets), optional) }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remover", color = P40Colors.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
