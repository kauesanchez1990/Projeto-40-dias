@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.projeto40dias.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.data.db.FoodEntity
import br.com.projeto40dias.data.db.FoodState
import br.com.projeto40dias.data.db.SubstitutionGroup
import br.com.projeto40dias.data.repo.per100
import br.com.projeto40dias.domain.NutritionCalculator
import br.com.projeto40dias.ui.components.NumberField
import br.com.projeto40dias.ui.components.P40Card
import br.com.projeto40dias.ui.components.Pill
import br.com.projeto40dias.ui.components.SectionLabel
import br.com.projeto40dias.ui.components.Notice
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
class FoodsViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val foods = query.flatMapLatest { c.nutrition.searchFoods(it.trim()) }.asState(this, emptyList())
    val group = MutableStateFlow(SubstitutionGroup.PROTEINA)
    val groupFoods = group.flatMapLatest { c.nutrition.foodsByGroup(it) }.asState(this, emptyList())
    fun save(f: FoodEntity) = viewModelScope.launch { c.nutrition.saveFood(f) }
}

@Composable
fun FoodsScreen(onBack: () -> Unit) {
    val vm = appViewModel { FoodsViewModel(it) }
    val q by vm.query.collectAsState()
    val foods by vm.foods.collectAsState()
    var editing by remember { mutableStateOf<FoodEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    SubScreen("Banco de alimentos", onBack, actions = {
        IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Novo alimento", tint = P40Colors.Lime) }
    }) {
        item {
            OutlinedTextField(q, { vm.query.value = it }, label = { Text("Pesquisar alimento") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item { Notice("Valores por 100 g. Referência aproximada (TACO/USDA). Toque em um alimento para corrigir os valores ou cadastre os seus com o botão +.") }
        items(foods, key = { it.id }) { f ->
            P40Card(onClick = { editing = f }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Pill(f.state.label, P40Colors.TextSecondary)
                    if (f.custom) { Spacer(Modifier.width(4.dp)); Pill("MEU", P40Colors.Lime) }
                }
                Text("${fmt0(f.kcal100)} kcal · P ${fmt(f.protein100)} g · C ${fmt(f.carbs100)} g · G ${fmt(f.fat100)} g",
                    fontSize = 12.sp, color = P40Colors.TextSecondary)
            }
        }
    }

    if (creating) FoodEditDialog(null, onDismiss = { creating = false }) { vm.save(it); creating = false }
    editing?.let { f -> FoodEditDialog(f, onDismiss = { editing = null }) { vm.save(it); editing = null } }
}

@Composable
private fun FoodEditDialog(food: FoodEntity?, onDismiss: () -> Unit, onSave: (FoodEntity) -> Unit) {
    var name by remember { mutableStateOf(food?.name ?: "") }
    var kcal by remember { mutableStateOf(food?.kcal100?.let { fmt(it) } ?: "") }
    var p by remember { mutableStateOf(food?.protein100?.let { fmt(it) } ?: "") }
    var cb by remember { mutableStateOf(food?.carbs100?.let { fmt(it) } ?: "") }
    var fat by remember { mutableStateOf(food?.fat100?.let { fmt(it) } ?: "") }
    var state by remember { mutableStateOf(food?.state ?: FoodState.COZIDO) }
    var group by remember { mutableStateOf(food?.substitutionGroup) }
    val valid = name.isNotBlank() && listOf(kcal, p, cb, fat).all { parseNum(it) != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (food == null) "Novo alimento" else "Editar alimento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Valores por 100 g", fontSize = 12.sp, color = P40Colors.TextSecondary)
                NumberField(kcal, { kcal = it }, "Calorias", unit = "kcal")
                NumberField(p, { p = it }, "Proteínas", unit = "g")
                NumberField(cb, { cb = it }, "Carboidratos", unit = "g")
                NumberField(fat, { fat = it }, "Gorduras", unit = "g")
                Text("Peso considerado", fontSize = 12.sp, color = P40Colors.TextSecondary)
                Column {
                    FoodState.entries.forEach { s ->
                        FilterChip(selected = state == s, onClick = { state = s }, label = { Text(s.label, fontSize = 11.sp) }, colors = chipColors())
                    }
                }
                Text("Grupo de substituição", fontSize = 12.sp, color = P40Colors.TextSecondary)
                Column {
                    FilterChip(selected = group == null, onClick = { group = null }, label = { Text("Nenhum", fontSize = 11.sp) }, colors = chipColors())
                    SubstitutionGroup.entries.forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.label, fontSize = 11.sp) }, colors = chipColors())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val base = food ?: FoodEntity(name = "", kcal100 = 0.0, protein100 = 0.0, carbs100 = 0.0, fat100 = 0.0, state = state, custom = true)
                onSave(base.copy(name = name.trim(), kcal100 = parseNum(kcal)!!, protein100 = parseNum(p)!!, carbs100 = parseNum(cb)!!,
                    fat100 = parseNum(fat)!!, state = state, substitutionGroup = group))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun chipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = P40Colors.Lime, selectedLabelColor = Color.Black)

@Composable
fun SubstitutionsScreen(onBack: () -> Unit) {
    val vm = appViewModel { FoodsViewModel(it) }
    val group by vm.group.collectAsState()
    val foods by vm.groupFoods.collectAsState()
    var refId by remember(group) { mutableStateOf<Long?>(null) }
    var grams by remember(group) { mutableStateOf("150") }
    val ref = foods.firstOrNull { it.id == refId } ?: foods.firstOrNull()
    val g = parseNum(grams) ?: 0.0
    val byProtein = group == SubstitutionGroup.PROTEINA

    SubScreen("Substituições", onBack) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubstitutionGroup.entries.forEach { sg ->
                    FilterChip(selected = sg == group, onClick = { vm.group.value = sg }, label = { Text(sg.label) }, colors = chipColors())
                }
            }
        }
        if (ref != null) {
            item {
                P40Card {
                    SectionLabel("Alimento de referência")
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(ref.name, fontWeight = FontWeight.Bold)
                            Text("toque em um alimento abaixo para usá-lo como referência", fontSize = 11.sp, color = P40Colors.TextSecondary)
                        }
                        NumberField(grams, { grams = it }, "Qtd.", Modifier.width(110.dp), unit = "g")
                    }
                    val rm = NutritionCalculator.portion(ref.per100(), g)
                    Text("${fmt0(rm.kcal)} kcal · P ${fmt(rm.protein)} g · C ${fmt(rm.carbs)} g · G ${fmt(rm.fat)} g", fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp))
                    Text("Equivalência calculada por ${if (byProtein) "proteína" else "carboidrato"}, arredondada a 5 g.",
                        fontSize = 11.sp, color = P40Colors.TextSecondary)
                }
            }
            items(foods.filter { it.id != ref.id }, key = { it.id }) { f ->
                val eq = NutritionCalculator.equivalentGrams(ref.per100(), g, f.per100(), byProtein)
                val fm = NutritionCalculator.portion(f.per100(), eq)
                val rm = NutritionCalculator.portion(ref.per100(), g)
                P40Card(onClick = { refId = f.id; grams = fmt0(eq).replace(".", "") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.SemiBold)
                            Text("peso ${f.state.label}", fontSize = 11.sp, color = P40Colors.TextSecondary)
                        }
                        Text("${fmt0(eq)} g", fontWeight = FontWeight.ExtraBold, color = P40Colors.Lime, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Diff("kcal", fm.kcal, rm.kcal); Diff("Prot.", fm.protein, rm.protein)
                        Diff("Carb.", fm.carbs, rm.carbs); Diff("Gord.", fm.fat, rm.fat)
                    }
                }
            }
        }
    }
}

@Composable
private fun Diff(label: String, v: Double, ref: Double) {
    val d = v - ref
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = P40Colors.TextSecondary)
        Text(fmt0(v), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text((if (d >= 0) "+" else "") + fmt0(d), fontSize = 10.sp, color = if (kotlin.math.abs(d) < 1) P40Colors.TextSecondary else P40Colors.Estimated)
    }
}
