package br.com.projeto40dias.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.projeto40dias.ui.theme.P40Colors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

// ----------------------------------------------------------- Formatação pt-BR

val BR: Locale = Locale("pt", "BR")
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val shortFmt = DateTimeFormatter.ofPattern("dd/MM")

fun fmt(v: Double, decimals: Int = 1): String = NumberFormat.getNumberInstance(BR).apply {
    minimumFractionDigits = decimals; maximumFractionDigits = decimals
}.format(v)

fun fmt0(v: Double): String = NumberFormat.getIntegerInstance(BR).format(Math.round(v))
fun fmtDate(d: LocalDate): String = d.format(dateFmt)
fun fmtShort(d: LocalDate): String = d.format(shortFmt)
fun weekdayName(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, BR).replaceFirstChar { it.uppercase() }

/** Converte texto digitado ("82,5" ou "82.5") em número. */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

// ----------------------------------------------------------- Blocos visuais

@Composable
fun P40Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(containerColor = P40Colors.Card)
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = shape, colors = colors) { Column(Modifier.padding(16.dp), content = content) }
    } else {
        Card(modifier = m, shape = shape, colors = colors) { Column(Modifier.padding(16.dp), content = content) }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = P40Colors.TextSecondary, modifier = modifier)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = P40Colors.Text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = P40Colors.TextSecondary)
    }
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = if (filled) Color.Black else color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

/** Selo de natureza do dado: MEDIDO, ESTIMADO ou META. */
enum class ValueKind(val label: String, val color: Color) {
    MEDIDO("MEDIDO", P40Colors.Measured), ESTIMADO("ESTIMADO", P40Colors.Estimated), META("META", P40Colors.Goal)
}

@Composable
fun KindPill(kind: ValueKind) = Pill(kind.label, kind.color)

@Composable
fun ProgressRing(
    progress: Float, size: Dp, stroke: Dp = 12.dp, color: Color = P40Colors.Lime,
    content: @Composable () -> Unit = {},
) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(P40Colors.Track, -90f, 360f, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * anim, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = P40Colors.Lime, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(P40Colors.Track)) {
        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun MacroBar(label: String, consumed: Double, goal: Double, unit: String, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = P40Colors.Text)
            Text("${fmt0(consumed)} / ${fmt0(goal)} $unit", style = MaterialTheme.typography.bodyMedium, color = P40Colors.TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(if (goal > 0) (consumed / goal).toFloat() else 0f, color)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, kind: ValueKind? = null, valueColor: Color = P40Colors.Text) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(P40Colors.CardHigh).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontSize = 10.sp, color = P40Colors.TextSecondary, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
                modifier = Modifier.weight(1f, fill = false))
            if (kind != null) { Spacer(Modifier.width(4.dp)); KindPill(kind) }
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        if (sub != null) Text(sub, fontSize = 11.sp, color = P40Colors.TextSecondary)
    }
}

@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
fun Notice(text: String, warning: Boolean = false) {
    val c = if (warning) P40Colors.Warning else P40Colors.TextSecondary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.10f)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (warning) Icons.Filled.WarningAmber else Icons.Filled.Info, null, tint = c, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (warning) P40Colors.Warning else P40Colors.TextSecondary)
    }
}

@Composable
fun DateSwitcher(date: LocalDate, today: LocalDate, onChange: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Dia anterior") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (date == today) "Hoje" else weekdayName(date), style = MaterialTheme.typography.titleMedium)
            Text(fmtDate(date), style = MaterialTheme.typography.bodySmall, color = P40Colors.TextSecondary)
        }
        if (date != today) TextButton(onClick = { onChange(today) }) { Text("Hoje") }
        IconButton(onClick = { onChange(date.plusDays(1)) }, enabled = date.isBefore(today)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo dia")
        }
    }
}

@Composable
fun NumberField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier, unit: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = if (unit != null) { { Text(unit) } } else null,
        modifier = modifier.fillMaxWidth(),
    )
}

// ----------------------------------------------------------- Gráfico de linha interativo

data class ChartPoint(val date: LocalDate, val value: Double)

/**
 * Gráfico de linha desenhado com Canvas (sem dependências).
 * Toque em um ponto para ver o valor. [goal] desenha a linha tracejada de META.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>, unit: String, modifier: Modifier = Modifier,
    color: Color = P40Colors.Measured, goal: Double? = null, decimals: Int = 1,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text("Sem registros medidos ainda", color = P40Colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val sorted = points.sortedBy { it.date }
    var selected by remember(sorted) { mutableStateOf(sorted.lastIndex) }
    val values = sorted.map { it.value } + listOfNotNull(goal)
    val minV = values.min(); val maxV = values.max()
    val pad = if (abs(maxV - minV) < 1e-6) 1.0 else (maxV - minV) * 0.15
    val lo = minV - pad; val hi = maxV + pad

    Column(modifier.fillMaxWidth()) {
        val sel = sorted.getOrNull(selected)
        if (sel != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${fmt(sel.value, decimals)} $unit", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
                Spacer(Modifier.width(8.dp))
                Text(fmtDate(sel.date), color = P40Colors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.weight(1f))
                val first = sorted.first().value
                if (sorted.size > 1) {
                    val d = sel.value - first
                    Text((if (d > 0) "+" else "") + fmt(d, decimals) + " desde o início", fontSize = 11.sp, color = P40Colors.TextSecondary,
                        modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).pointerInput(sorted) {
                detectTapGestures { pos ->
                    if (sorted.size == 1) { selected = 0; return@detectTapGestures }
                    val step = size.width.toFloat() / (sorted.size - 1)
                    selected = ((pos.x / step) + 0.5f).toInt().coerceIn(0, sorted.lastIndex)
                }
            }
        ) {
            val w = size.width; val h = size.height
            fun yOf(v: Double) = (h - ((v - lo) / (hi - lo) * h)).toFloat()
            fun xOf(i: Int) = if (sorted.size == 1) w / 2 else w * i / (sorted.size - 1)

            // grade
            for (k in 0..3) {
                val gy = h * k / 3f
                drawLine(P40Colors.Track, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            // meta
            if (goal != null) {
                drawLine(P40Colors.Goal.copy(alpha = 0.7f), Offset(0f, yOf(goal)), Offset(w, yOf(goal)), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
            // linha
            val path = Path()
            sorted.forEachIndexed { i, p -> if (i == 0) path.moveTo(xOf(i), yOf(p.value)) else path.lineTo(xOf(i), yOf(p.value)) }
            drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
            sorted.forEachIndexed { i, p ->
                val r = if (i == selected) 9f else 5f
                drawCircle(color, r, Offset(xOf(i), yOf(p.value)))
                if (i == selected) drawCircle(Color.Black, 4f, Offset(xOf(i), yOf(p.value)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtShort(sorted.first().date), fontSize = 10.sp, color = P40Colors.TextSecondary)
            if (goal != null) Text("- - - META ${fmt(goal, decimals)} $unit", fontSize = 10.sp, color = P40Colors.Goal.copy(alpha = 0.8f))
            Text(fmtShort(sorted.last().date), fontSize = 10.sp, color = P40Colors.TextSecondary)
        }
    }
}

@Composable
fun ClickableRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
