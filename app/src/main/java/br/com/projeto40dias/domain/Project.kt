package br.com.projeto40dias.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Phase(val index: Int, val title: String, val firstDay: Int, val lastDay: Int)

data class ProjectStatus(
    val dayNumber: Int,        // 0 = ainda não começou; 1..length
    val length: Int,
    val daysCompleted: Int,    // dias inteiros já passados
    val daysRemaining: Int,
    val progress: Float,       // 0..1
    val started: Boolean,
    val finished: Boolean,
    val phase: Phase?,
)

object ProjectCalculator {

    val phases = listOf(
        Phase(1, "Semana 1 – Adaptação", 1, 7),
        Phase(2, "Semana 2 – Consistência", 8, 14),
        Phase(3, "Semana 3 – Evolução", 15, 21),
        Phase(4, "Semana 4 – Intensificação controlada", 22, 28),
        Phase(5, "Semana 5 – Consolidação", 29, 35),
        Phase(6, "Últimos 5 dias – Recuperação e avaliação", 36, 40),
    )

    fun status(start: LocalDate, today: LocalDate, length: Int = 40): ProjectStatus {
        val diff = ChronoUnit.DAYS.between(start, today).toInt()
        val day = (diff + 1).coerceIn(0, length)
        val started = diff >= 0
        val finished = diff >= length
        val completed = diff.coerceIn(0, length)
        val dayNumber = if (!started) 0 else day
        return ProjectStatus(
            dayNumber = dayNumber,
            length = length,
            daysCompleted = completed,
            daysRemaining = (length - dayNumber).coerceAtLeast(0),
            progress = dayNumber.toFloat() / length,
            started = started,
            finished = finished,
            phase = phaseFor(dayNumber),
        )
    }

    fun phaseFor(day: Int): Phase? = phases.firstOrNull { day in it.firstDay..it.lastDay }

    fun dateOfDay(start: LocalDate, day: Int): LocalDate = start.plusDays((day - 1).toLong())

    fun end(start: LocalDate, length: Int = 40): LocalDate = start.plusDays((length - 1).toLong())
}

object Motivation {
    private val messages = listOf(
        "Consistência vence intensidade. Faça o simples bem feito hoje.",
        "Cada refeição registrada é um passo concreto rumo à meta.",
        "Músculo se preserva com proteína, treino e sono. Cuide dos três.",
        "Não é sobre o dia perfeito — é sobre não desistir dos 40.",
        "Hidratação em dia, foco em dia.",
        "O progresso aparece na média da semana, não em uma pesagem.",
        "Treine com intenção, recupere com disciplina.",
        "Zona 2 hoje constrói a base de amanhã.",
        "Você não precisa de motivação; precisa de rotina. Ela já está aqui.",
        "Respeite o descanso: é nele que o corpo se adapta.",
        "Pequenas escolhas, repetidas, mudam o espelho.",
        "Meta clara, plano claro, execução diária.",
        "Corpo cansado pede recuperação, não punição.",
        "Ritmo confortável também é treino. Paciência gera resultado.",
    )
    fun forDate(date: LocalDate): String = messages[(date.toEpochDay() % messages.size).toInt()]
}

/** Regras de recuperação: decide se o treino opcional pode ser sugerido. */
object Recovery {
    /** Recuperado = fadiga ≤ 2, dor muscular ≤ 2 e sono ≥ 3 (escalas 1–5). */
    fun isRecovered(fatigue: Int, soreness: Int, sleep: Int) = fatigue <= 2 && soreness <= 2 && sleep >= 3

    /** Sinais de fadiga importante — o app deixa de sugerir intensidade. */
    fun showFatigueWarning(fatigue: Int, soreness: Int, sleep: Int) = fatigue >= 4 || soreness >= 4 || sleep <= 2
}

object RunCalculator {
    /** Ritmo médio em segundos por km. */
    fun paceSecPerKm(distanceKm: Double, durationSec: Int): Int? =
        if (distanceKm > 0 && durationSec > 0) (durationSec / distanceKm).toInt() else null

    fun formatPace(secPerKm: Int?): String = secPerKm?.let { "%d:%02d /km".format(it / 60, it % 60) } ?: "—"

    /** Estimativa simples: ≈ 1 kcal por kg de peso por km. É ESTIMADO, não medido. */
    fun estimatedKcal(weightKg: Double, distanceKm: Double): Int = (weightKg * distanceKm).toInt()

    fun formatDuration(sec: Int?): String = sec?.let { "%d:%02d:%02d".format(it / 3600, (it % 3600) / 60, it % 60) } ?: "—"

    /** Aceita "45", "45:30" ou "1:05:30". */
    fun parseDuration(text: String): Int? {
        val parts = text.trim().split(":").map { it.trim().toIntOrNull() ?: return null }
        return when (parts.size) {
            1 -> parts[0] * 60
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }
}
