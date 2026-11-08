@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.projeto40dias.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.projeto40dias.data.db.BodyMeasurementEntity
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime

fun <T> Flow<T>.asState(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

/** Lista rolável padrão das telas. */
@Composable
fun ScreenList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Tela secundária com barra superior e botão voltar. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = P40Colors.Background),
        )
        ScreenList(content = content)
    }
}

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

fun pickTime(context: Context, initial: String, onPicked: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(8, 0))
    TimePickerDialog(context, { _, h, m -> onPicked("%02d:%02d".format(h, m)) }, t.hour, t.minute, true).show()
}

/** Último valor MEDIDO de um campo, com a data. */
fun <T : Any> List<BodyMeasurementEntity>.latest(selector: (BodyMeasurementEntity) -> T?): Pair<T, LocalDate>? =
    sortedWith(compareBy({ it.date }, { it.id })).lastOrNull { selector(it) != null }?.let { selector(it)!! to LocalDate.parse(it.date) }

fun <T : Any> List<BodyMeasurementEntity>.earliest(selector: (BodyMeasurementEntity) -> T?): Pair<T, LocalDate>? =
    sortedWith(compareBy({ it.date }, { it.id })).firstOrNull { selector(it) != null }?.let { selector(it)!! to LocalDate.parse(it.date) }
