package br.com.projeto40dias.domain

import java.time.LocalDate

/** Resumo de um dia, já calculado a partir do banco. */
data class DaySummary(
    val date: LocalDate,
    val kcal: Double,
    val protein: Double,
    val mealsDone: Int,
    val mealsScheduled: Int,
    val exercisesDone: Int,
    val exercisesScheduled: Int,
    val sessionsDone: Int,
    val sessionsScheduled: Int,
)

data class DatedValue(val date: LocalDate, val value: Double)

data class WeekReport(
    val title: String,
    val from: LocalDate,
    val to: LocalDate,
    val weightStart: Double?,
    val weightEnd: Double?,
    val weightDelta: Double?,
    val avgKcal: Double?,
    val avgProtein: Double?,
    val sessionsScheduled: Int,
    val sessionsDone: Int,
    val dietAdherencePct: Int?,
    val exerciseAdherencePct: Int?,
    val waistCm: Double?,
    val daysWithData: Int,
)

object ReportCalculator {

    /**
     * Gera o relatório de um período. Só dias até [today] entram nas médias e na adesão —
     * dias futuros não contam como falha.
     */
    fun period(
        title: String, from: LocalDate, to: LocalDate, today: LocalDate,
        days: List<DaySummary>, weights: List<DatedValue>, waists: List<DatedValue>,
    ): WeekReport {
        val lastCountable = if (to.isAfter(today)) today else to
        val inRange = days.filter { !it.date.isBefore(from) && !it.date.isAfter(lastCountable) }
        val fed = inRange.filter { it.mealsDone > 0 }

        val w = weights.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
        // Peso inicial da semana: última medição até o 1º dia (ou a 1ª dentro da semana).
        val wStart = weights.filter { !it.date.isAfter(from) }.maxByOrNull { it.date }?.value ?: w.firstOrNull()?.value
        val wEnd = w.lastOrNull()?.value
        val waist = waists.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.maxByOrNull { it.date }?.value

        val mealsSched = inRange.sumOf { it.mealsScheduled }
        val exSched = inRange.sumOf { it.exercisesScheduled }

        return WeekReport(
            title = title, from = from, to = to,
            weightStart = wStart, weightEnd = wEnd,
            weightDelta = if (wStart != null && wEnd != null) wEnd - wStart else null,
            avgKcal = fed.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average(),
            avgProtein = fed.takeIf { it.isNotEmpty() }?.map { it.protein }?.average(),
            sessionsScheduled = inRange.sumOf { it.sessionsScheduled },
            sessionsDone = inRange.sumOf { it.sessionsDone },
            dietAdherencePct = if (mealsSched > 0) pct(inRange.sumOf { it.mealsDone }, mealsSched) else null,
            exerciseAdherencePct = if (exSched > 0) pct(inRange.sumOf { it.exercisesDone }, exSched) else null,
            waistCm = waist,
            daysWithData = fed.size,
        )
    }

    fun weekly(start: LocalDate, length: Int, today: LocalDate, days: List<DaySummary>, weights: List<DatedValue>, waists: List<DatedValue>): List<WeekReport> =
        ProjectCalculator.phases.filter { it.firstDay <= length }.map { ph ->
            val from = ProjectCalculator.dateOfDay(start, ph.firstDay)
            val to = ProjectCalculator.dateOfDay(start, minOf(ph.lastDay, length))
            period(ph.title, from, to, today, days, weights, waists)
        }

    fun pct(done: Int, total: Int): Int = if (total <= 0) 0 else ((done.coerceAtMost(total) * 100.0) / total).toInt()
}
