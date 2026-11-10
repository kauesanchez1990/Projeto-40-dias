package br.com.projeto40dias.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.db.MealTemplateEntity
import br.com.projeto40dias.data.db.TemplateItemWithFood
import br.com.projeto40dias.data.prefs.Goals
import br.com.projeto40dias.data.repo.MealPlan
import br.com.projeto40dias.domain.Macros
import br.com.projeto40dias.domain.NutritionCalculator
import br.com.projeto40dias.domain.Per100
import br.com.projeto40dias.ui.components.KindPill
import br.com.projeto40dias.ui.components.MacroBar
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.NumberField
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.StatTile
import br.com.projeto40dias.ui.components.TileRow
import br.com.projeto40dias.ui.components.ValueKind
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmt0
import br.com.projeto40dias.ui.components.parseNum
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DietPlanViewModel(private val c: AppContainer) : ViewModel() {
    val plan = c.nutrition.observePlan().asState(this, emptyList())
    val goals = c.settings.goals.asState(this, Goals())
    val query = MutableStateFlow("")
    val results = query.flatMapLatest { c.nutrition.searchFoods(it.trim()) }.asState(this, emptyList())

    fun saveMeal(t: MealTemplateEntity) = viewModelScope.launch { c.nutrition.saveMeal(t) }
    fun removeMeal(id: Long) = viewModelScope.launch { c.nutrition.removeMeal(id) }
    fun addItem(mealId: Long, foodId: Long, g: Double) = viewModelScope.launch { c.nutrition.addTemplateItem(mealId, foodId, g) }
    fun setGrams(itemId: Long, g: Double) = viewModelScope.launch { c.nutrition.setTemplateGrams(itemId, g) }
    fun removeItem(itemId: Long) = viewModelScope.launch { c.nutrition.removeTemplateItem(itemId) }
}

private fun TemplateItemWithFood.macros() = NutritionCalculator.portion(Per100(kcal100, protein100, carbs100, fat100), grams)

@Composable
fun DietPlanScreen(onBack: () -> Unit) {
    val vm = appViewModel { DietPlanViewModel(it) }
    val plan by vm.plan.collectAsState()
    val goals by vm.goals.collectAsState()
    val q by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val ctx = LocalContext.current

    var editingMeal by remember { mutableStateOf<MealTemplateEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<MealTemplateEntity?>(null) }
    var addingTo by remember { mutableStateOf<Long?>(null) }
    var editingItem by remember { mutableStateOf<TemplateItemWithFood?>(null) }

    SubScreen("Editar dieta", onBack, actions = {
        IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Nova refeição", tint = P40Colors.Lime) }
    }) {
        item { PlanSummary(plan, goals) }
        item {
            Notice("As alterações valem para hoje (se a refeição ainda não foi marcada como feita) e para os próximos dias. " +
                "Os dias anteriores não mudam. Os lembretes seguem os horários definidos aqui.")
        }
        items(plan, key = { it.template.id }) { mp ->
            PlanMealCard(
                mp,
                onEdit = { editingMeal = mp.template },
                onTime = { pickTime(ctx, mp.template.time) { t -> vm.saveMeal(mp.template.copy(time = t)) } },
                onRemove = { removing = mp.template },
                onAdd = { vm.query.value = ""; addingTo = mp.template.id },
                onItem = { editingItem = it },
            )
        }
        item {
            Button(
                onClick = { creating = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = P40Colors.Lime, contentColor = Color.Black),
            ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Nova refeição") }
        }
    }

    if (creating) MealDialog(null, onDismiss = { creating = false }) { vm.saveMeal(it); creating = false }
    editingMeal?.let { t -> MealDialog(t, onDismiss = { editingMeal = null }) { vm.saveMeal(it); editingMeal = null } }
    removing?.let { t ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Excluir ${t.label}?") },
            text = { Text("A refeição sai do cardápio e dos lembretes a partir de hoje. O que já foi registrado nos dias anteriores continua no histórico.") },
            confirmButton = { TextButton(onClick = { vm.removeMeal(t.id); removing = null }) { Text("Excluir", color = P40Colors.Danger) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
    addingTo?.let { mealId ->
        FoodSearchDialog(q, { vm.query.value = it }, results, onDismiss = { addingTo = null }) { food, g ->
            vm.addItem(mealId, food.id, g); addingTo = null
        }
    }
    editingItem?.let { item -> PlanItemDialog(item, onDismiss = { editingItem = null }, onSave = { vm.setGrams(item.id, it) }, onRemove = { vm.removeItem(item.id) }) }
}

@Composable
private fun PlanSummary(plan: List<MealPlan>, goals: Goals) {
    val total = NutritionCalculator.sum(plan.filter { !it.template.optional }.map { it.macros })
    val goal = Macros(goals.kcal.toDouble(), goals.protein.toDouble(), goals.carbs.toDouble(), goals.fat.toDouble())
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Cardápio padrão (sem opcionais)", Modifier.weight(1f))
            KindPill(ValueKind.ESTIMADO)
        }
        Spacer(Modifier.height(8.dp))
        TileRow {
            StatTile("Planejado", "${fmt0(total.kcal)} kcal", Modifier.weight(1f), valueColor = P40Colors.Lime)
            StatTile("Meta", "${fmt0(goal.kcal)} kcal", Modifier.weight(1f), kind = ValueKind.META)
            StatTile("Refeições", "${plan.size}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        MacroBar("Proteínas", total.protein, goal.protein, "g", P40Colors.Protein)
        MacroBar("Carboidratos", total.carbs, goal.carbs, "g", P40Colors.Carbs)
        MacroBar("Gorduras", total.fat, goal.fat, "g", P40Colors.Fat)
    }
}

@Composable
private fun PlanMealCard(
    mp: MealPlan, onEdit: () -> Unit, onTime: () -> Unit, onRemove: () -> Unit, onAdd: () -> Unit,
    onItem: (TemplateItemWithFood) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val t = mp.template
    val m = mp.macros
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onTime) { Text(t.time, fontWeight = FontWeight.Bold, color = P40Colors.Lime) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name.uppercase(), fontSize = 11.sp, color = P40Colors.TextSecondary, fontWeight = FontWeight.Bold)
                    if (t.optional) { Spacer(Modifier.width(6.dp)); Pill("OPCIONAL", P40Colors.TextSecondary) }
                }
                Text(t.label, style = MaterialTheme.typography.titleMedium)
                Text("${fmt0(m.kcal)} kcal · P ${fmt0(m.protein)} · C ${fmt0(m.carbs)} · G ${fmt0(m.fat)}", fontSize = 11.sp, color = P40Colors.TextSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar nome e horário") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Adicionar alimento") }, onClick = { menu = false; onAdd() })
                    DropdownMenuItem(text = { Text("Excluir refeição", color = P40Colors.Danger) }, onClick = { menu = false; onRemove() })
                }
            }
        }
        HorizontalDivider(color = P40Colors.Track, modifier = Modifier.padding(vertical = 6.dp))
        mp.items.forEach { fi ->
            Row(Modifier.fillMaxWidth().clickable { onItem(fi) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fi.name, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmt0(fi.grams)} g", fontSize = 12.sp, color = P40Colors.Lime, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Pill(fi.state.label, P40Colors.TextSecondary)
                    }
                }
                Text("${fmt0(fi.macros().kcal)} kcal", fontSize = 12.sp, color = P40Colors.TextSecondary)
            }
        }
        if (mp.items.isEmpty()) Text("Nenhum alimento ainda.", color = P40Colors.TextSecondary, fontSize = 12.sp)
        TextButton(onClick = onAdd) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Adicionar alimento") }
    }
}

/** Criar (t = null) ou editar uma refeição do cardápio. */
@Composable
private fun MealDialog(t: MealTemplateEntity?, onDismiss: () -> Unit, onSave: (MealTemplateEntity) -> Unit) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(t?.label ?: "") }
    var name by remember { mutableStateOf(t?.name ?: "") }
    var time by remember { mutableStateOf(t?.time ?: "12:00") }
    var optional by remember { mutableStateOf(t?.optional ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t == null) "Nova refeição" else "Editar refeição") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Nome (ex.: Almoço)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Rótulo (ex.: Refeição 3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickTime(ctx, time) { time = it } }) { Text("Horário: $time") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(optional, { optional = it }, colors = CheckboxDefaults.colors(checkedColor = P40Colors.Lime, checkmarkColor = Color.Black))
                    Text("Opcional (não conta na adesão nem no total planejado)", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = {
                    val l = label.trim()
                    val n = name.trim().ifBlank { l }
                    onSave(t?.copy(label = l, name = n, time = time, optional = optional)
                        ?: MealTemplateEntity(name = n, label = l, time = time, position = 0, optional = optional))
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PlanItemDialog(item: TemplateItemWithFood, onDismiss: () -> Unit, onSave: (Double) -> Unit, onRemove: () -> Unit) {
    var grams by remember(item.id) { mutableStateOf(fmt0(item.grams).replace(".", "")) }
    val g = parseNum(grams) ?: 0.0
    val m = NutritionCalculator.portion(Per100(item.kcal100, item.protein100, item.carbs100, item.fat100), g)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Peso ${item.state.label}", fontSize = 12.sp, color = P40Colors.TextSecondary)
                NumberField(grams, { grams = it }, "Quantidade", unit = "g")
                Text("${fmt0(m.kcal)} kcal · P ${fmt(m.protein)} g · C ${fmt(m.carbs)} g · G ${fmt(m.fat)} g", fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (g > 0) onSave(g); onDismiss() }) { Text("Salvar") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onRemove(); onDismiss() }) { Text("Remover", color = P40Colors.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
