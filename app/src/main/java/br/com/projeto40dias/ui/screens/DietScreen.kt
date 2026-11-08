package br.com.projeto40dias.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
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
import br.com.projeto40dias.data.db.DayItemWithFood
import br.com.projeto40dias.data.db.FoodEntity
import br.com.projeto40dias.data.db.MealTemplateEntity
import br.com.projeto40dias.data.db.SubstitutionGroup
import br.com.projeto40dias.data.prefs.Goals
import br.com.projeto40dias.data.repo.DayNutrition
import br.com.projeto40dias.data.repo.MealDay
import br.com.projeto40dias.data.repo.macros
import br.com.projeto40dias.data.repo.per100
import br.com.projeto40dias.domain.Macros
import br.com.projeto40dias.domain.NutritionCalculator
import br.com.projeto40dias.ui.components.DateSwitcher
import br.com.projeto40dias.ui.components.KindPill
import br.com.projeto40dias.ui.components.MacroBar
import br.com.projeto40dias.ui.components.Notice
import br.com.projeto40dias.ui.components.NumberField
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.components.ProgressBar
import br.com.projeto40dias.ui.components.ScreenTitle
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.StatTile
import br.com.projeto40dias.ui.components.TileRow
import br.com.projeto40dias.ui.components.ValueKind
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmt0
import br.com.projeto40dias.ui.components.parseNum
import br.com.projeto40dias.ui.nav.Routes
import br.com.projeto40dias.ui.nav.appViewModel
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DietViewModel(private val c: AppContainer) : ViewModel() {
    val date = MutableStateFlow(c.today.value)
    val today = c.today

    val day: kotlinx.coroutines.flow.StateFlow<DayNutrition?> =
        date.onEach { c.nutrition.ensureDay(it) }.flatMapLatest { c.nutrition.observeDay(it) }.asState(this, null)
    val goals = c.settings.goals.asState(this, Goals())

    val query = MutableStateFlow("")
    val results = query.flatMapLatest { c.nutrition.searchFoods(it.trim()) }.asState(this, emptyList())

    fun groupFoods(g: SubstitutionGroup): Flow<List<FoodEntity>> = c.nutrition.foodsByGroup(g)

    fun setDone(mealId: Long, done: Boolean) = viewModelScope.launch { c.nutrition.setMealDone(date.value, mealId, done) }
    fun setGrams(itemId: Long, g: Double) = viewModelScope.launch { c.nutrition.updateGrams(itemId, g) }
    fun replace(itemId: Long, foodId: Long, g: Double) = viewModelScope.launch { c.nutrition.replaceFood(itemId, foodId, g) }
    fun remove(itemId: Long) = viewModelScope.launch { c.nutrition.deleteItem(itemId) }
    fun add(mealId: Long, foodId: Long, g: Double) = viewModelScope.launch { c.nutrition.addItem(date.value, mealId, foodId, g) }
    fun saveAsTemplate(mealId: Long) = viewModelScope.launch { c.nutrition.saveMealAsTemplate(date.value, mealId) }
    fun updateTemplate(t: MealTemplateEntity) = viewModelScope.launch { c.nutrition.updateMealTemplate(t) }
}

@Composable
fun DietScreen(onNavigate: (String) -> Unit) {
    val vm = appViewModel { DietViewModel(it) }
    val date by vm.date.collectAsState()
    val today by vm.today.collectAsState()
    val day by vm.day.collectAsState()
    val goals by vm.goals.collectAsState()
    val ctx = LocalContext.current

    var editing by remember { mutableStateOf<DayItemWithFood?>(null) }
    var addingTo by remember { mutableStateOf<Long?>(null) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    ScreenList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle("MINHA DIETA", "Cardápio, consumo e substituições") }
                IconButton(onClick = { onNavigate(Routes.SUBSTITUTIONS) }) { Icon(Icons.Filled.SwapHoriz, "Substituições", tint = P40Colors.Lime) }
                IconButton(onClick = { onNavigate(Routes.FOODS) }) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Filled.MenuBook, "Banco de alimentos", tint = P40Colors.Lime)
                }
            }
        }
        item { DateSwitcher(date, today) { vm.date.value = it } }

        val d = day
        if (d != null) {
            item { DaySummaryCard(d.consumed, d.planned, goals) }
            items(d.meals, key = { it.template.id }) { meal ->
                MealCard(
                    meal = meal,
                    onToggle = { vm.setDone(meal.template.id, it) },
                    onItem = { editing = it },
                    onAdd = { addingTo = meal.template.id },
                    onSaveTemplate = { vm.saveAsTemplate(meal.template.id); savedMsg = "Cardápio padrão de ${meal.template.label} atualizado para os próximos dias." },
                    onEditTime = { pickTime(ctx, meal.template.time) { t -> vm.updateTemplate(meal.template.copy(time = t)) } },
                )
            }
        }
        item {
            Notice("Valores calculados a partir do banco de alimentos (por 100 g) e das quantidades registradas — são estimativas. " +
                "Cada alimento indica se o peso é cru ou cozido/preparado. Editar um dia não altera os dias anteriores.")
        }
    }

    editing?.let { item ->
        ItemEditDialog(item, vm, onDismiss = { editing = null })
    }
    addingTo?.let { mealId ->
        FoodPickerDialog(vm, onDismiss = { addingTo = null }) { food, g -> vm.add(mealId, food.id, g); addingTo = null }
    }
    savedMsg?.let { msg ->
        AlertDialog(onDismissRequest = { savedMsg = null }, confirmButton = { TextButton({ savedMsg = null }) { Text("OK") } },
            title = { Text("Salvo") }, text = { Text(msg) })
    }
}

@Composable
private fun DaySummaryCard(consumed: Macros, planned: Macros, goals: Goals) {
    val goal = Macros(goals.kcal.toDouble(), goals.protein.toDouble(), goals.carbs.toDouble(), goals.fat.toDouble())
    val left = NutritionCalculator.remaining(goal, consumed)
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Consumido no dia", Modifier.weight(1f))
            KindPill(ValueKind.ESTIMADO)
        }
        Spacer(Modifier.height(8.dp))
        TileRow {
            StatTile("Consumido", "${fmt0(consumed.kcal)} kcal", Modifier.weight(1f), valueColor = P40Colors.Lime)
            StatTile("Meta", "${fmt0(goal.kcal)} kcal", Modifier.weight(1f), kind = ValueKind.META)
            StatTile("Falta", "${fmt0(left.kcal)} kcal", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        MacroBar("Proteínas · faltam ${fmt0(left.protein)} g", consumed.protein, goal.protein, "g", P40Colors.Protein)
        MacroBar("Carboidratos · faltam ${fmt0(left.carbs)} g", consumed.carbs, goal.carbs, "g", P40Colors.Carbs)
        MacroBar("Gorduras · faltam ${fmt0(left.fat)} g", consumed.fat, goal.fat, "g", P40Colors.Fat)
        Spacer(Modifier.height(6.dp))
        Text("Cardápio completo planejado: ${fmt0(planned.kcal)} kcal · P ${fmt0(planned.protein)} g · C ${fmt0(planned.carbs)} g · G ${fmt0(planned.fat)} g",
            fontSize = 11.sp, color = P40Colors.TextSecondary)
    }
}

@Composable
private fun MealCard(
    meal: MealDay, onToggle: (Boolean) -> Unit, onItem: (DayItemWithFood) -> Unit, onAdd: () -> Unit,
    onSaveTemplate: () -> Unit, onEditTime: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val m = meal.macros
    P40Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(meal.done, onToggle, colors = CheckboxDefaults.colors(checkedColor = P40Colors.Lime, checkmarkColor = Color.Black))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${meal.template.time} · ${meal.template.name.uppercase()}", fontSize = 11.sp, color = P40Colors.TextSecondary, fontWeight = FontWeight.Bold)
                    if (meal.template.optional) { Spacer(Modifier.width(6.dp)); Pill("OPCIONAL", P40Colors.TextSecondary) }
                }
                Text(meal.template.label, style = MaterialTheme.typography.titleMedium, color = if (meal.done) P40Colors.Lime else P40Colors.Text)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${fmt0(m.kcal)} kcal", fontWeight = FontWeight.Bold)
                Text("P ${fmt0(m.protein)} · C ${fmt0(m.carbs)} · G ${fmt0(m.fat)}", fontSize = 11.sp, color = P40Colors.TextSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Adicionar alimento") }, onClick = { menu = false; onAdd() })
                    DropdownMenuItem(text = { Text("Alterar horário") }, onClick = { menu = false; onEditTime() })
                    DropdownMenuItem(text = { Text("Salvar como cardápio padrão") }, onClick = { menu = false; onSaveTemplate() })
                }
            }
        }
        HorizontalDivider(color = P40Colors.Track, modifier = Modifier.padding(vertical = 6.dp))
        meal.items.forEach { fi ->
            val im = fi.macros()
            Row(Modifier.fillMaxWidth().clickable { onItem(fi) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fi.name, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmt0(fi.grams)} g", fontSize = 12.sp, color = P40Colors.Lime, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Pill(fi.state.label, P40Colors.TextSecondary)
                    }
                }
                Text("${fmt0(im.kcal)} kcal", fontSize = 12.sp, color = P40Colors.TextSecondary)
            }
        }
        if (meal.items.isEmpty()) Text("Nenhum alimento.", color = P40Colors.TextSecondary, fontSize = 12.sp)
        TextButton(onClick = onAdd) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Adicionar alimento") }
    }
}

@Composable
private fun ItemEditDialog(item: DayItemWithFood, vm: DietViewModel, onDismiss: () -> Unit) {
    var grams by remember(item.id) { mutableStateOf(fmt0(item.grams).replace(".", "")) }
    var showSubs by remember { mutableStateOf(false) }
    val g = parseNum(grams) ?: 0.0
    val m = NutritionCalculator.portion(item.per100(), g)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Peso ${item.state.label}", fontSize = 12.sp, color = P40Colors.TextSecondary)
                NumberField(grams, { grams = it }, "Quantidade", unit = "g")
                Text("${fmt0(m.kcal)} kcal · P ${fmt(m.protein)} g · C ${fmt(m.carbs)} g · G ${fmt(m.fat)} g", fontSize = 13.sp)
                OutlinedButton(onClick = { showSubs = !showSubs }) {
                    Icon(Icons.Filled.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("Substituir alimento")
                }
                if (showSubs) SubstitutePicker(item, g, vm) { food, newG -> vm.replace(item.id, food.id, newG); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = { if (g > 0) vm.setGrams(item.id, g); onDismiss() }) { Text("Salvar") } },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.remove(item.id); onDismiss() }) { Text("Remover", color = P40Colors.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Lista de substitutos do mesmo grupo (com gramas equivalentes) ou busca geral. */
@Composable
private fun SubstitutePicker(item: DayItemWithFood, grams: Double, vm: DietViewModel, onPick: (FoodEntity, Double) -> Unit) {
    val group = item.substitutionGroup
    val q by vm.query.collectAsState()
    val list: List<FoodEntity> = if (group != null) {
        val flow = remember(group) { vm.groupFoods(group) }
        flow.collectAsState(initial = emptyList()).value
    } else {
        vm.results.collectAsState().value
    }
    Column {
        if (group == null) {
            OutlinedTextField(q, { vm.query.value = it }, label = { Text("Buscar alimento") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            Text("${group.label} — quantidade equivalente em ${if (group == SubstitutionGroup.PROTEINA) "proteína" else "carboidrato"}",
                fontSize = 11.sp, color = P40Colors.TextSecondary)
        }
        LazyColumn(Modifier.heightIn(max = 260.dp)) {
            items(list.filter { it.id != item.foodId }, key = { it.id }) { f ->
                val eq = if (group != null) NutritionCalculator.equivalentGrams(item.per100(), grams, f.per100(), group == SubstitutionGroup.PROTEINA) else grams
                val fm = NutritionCalculator.portion(f.per100(), eq)
                Column(Modifier.fillMaxWidth().clickable { onPick(f, eq) }.padding(vertical = 6.dp)) {
                    Text("${f.name} · ${fmt0(eq)} g", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${fmt0(fm.kcal)} kcal · P ${fmt0(fm.protein)} · C ${fmt0(fm.carbs)} · G ${fmt0(fm.fat)} (${f.state.label})",
                        fontSize = 11.sp, color = P40Colors.TextSecondary)
                }
            }
        }
    }
}

/** Busca no banco de alimentos e adiciona ao cardápio do dia. */
@Composable
fun FoodPickerDialog(vm: DietViewModel, onDismiss: () -> Unit, onPick: (FoodEntity, Double) -> Unit) {
    val q by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    var selected by remember { mutableStateOf<FoodEntity?>(null) }
    var grams by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selected == null) "Adicionar alimento" else selected!!.name) },
        text = {
            val sel = selected
            if (sel == null) {
                Column {
                    OutlinedTextField(q, { vm.query.value = it }, label = { Text("Pesquisar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(results, key = { it.id }) { f ->
                            Column(Modifier.fillMaxWidth().clickable { selected = f }.padding(vertical = 8.dp)) {
                                Text(f.name, fontWeight = FontWeight.SemiBold)
                                Text("100 g ${f.state.label}: ${fmt0(f.kcal100)} kcal · P ${fmt(f.protein100)} · C ${fmt(f.carbs100)} · G ${fmt(f.fat100)}",
                                    fontSize = 11.sp, color = P40Colors.TextSecondary)
                            }
                        }
                    }
                }
            } else {
                val g = parseNum(grams) ?: 0.0
                val m = NutritionCalculator.portion(sel.per100(), g)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Peso ${sel.state.label}", fontSize = 12.sp, color = P40Colors.TextSecondary)
                    NumberField(grams, { grams = it }, "Quantidade", unit = "g")
                    Text("${fmt0(m.kcal)} kcal · P ${fmt(m.protein)} g · C ${fmt(m.carbs)} g · G ${fmt(m.fat)} g")
                }
            }
        },
        confirmButton = {
            val sel = selected
            if (sel != null) TextButton(onClick = { parseNum(grams)?.takeIf { it > 0 }?.let { onPick(sel, it) } }) { Text("Adicionar") }
        },
        dismissButton = {
            TextButton(onClick = { if (selected != null) selected = null else onDismiss() }) { Text(if (selected != null) "Voltar" else "Cancelar") }
        },
    )
}
