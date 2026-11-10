package br.com.projeto40dias.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Calcula o próximo disparo de cada tipo de lembrete. Sem dependências do Android. */
object ReminderPlanner {

    fun parseTime(t: String, default: LocalTime = LocalTime.of(8, 0)): LocalTime =
        runCatching { LocalTime.parse(t) }.getOrDefault(default)

    /** Próxima ocorrência diária de [time], avisando [leadMin] minutos antes. Sempre depois de [now]. */
    fun nextDaily(now: LocalDateTime, time: LocalTime, leadMin: Int = 0): LocalDateTime {
        var t = now.toLocalDate().atTime(time).minusMinutes(leadMin.toLong())
        while (!t.isAfter(now)) t = t.plusDays(1)
        return t
    }

    /** Próxima ocorrência semanal (dia [dow] às [time]), avisando [leadMin] minutos antes. */
    fun nextWeekly(now: LocalDateTime, dow: DayOfWeek, time: LocalTime, leadMin: Int = 0): LocalDateTime {
        var t = now.toLocalDate().with(TemporalAdjusters.previousOrSame(dow)).atTime(time).minusMinutes(leadMin.toLong())
        while (!t.isAfter(now)) t = t.plusWeeks(1)
        return t
    }

    /**
     * Próximo lembrete de água: de [start] até [end] (inclusive), a cada [intervalMin] minutos.
     * Retorna null se a janela for inválida.
     */
    fun nextWater(now: LocalDateTime, start: LocalTime, end: LocalTime, intervalMin: Int): LocalDateTime? {
        if (intervalMin <= 0 || end.isBefore(start)) return null
        for (offset in 0L..1L) {
            val day = now.toLocalDate().plusDays(offset)
            var t = day.atTime(start)
            val last = day.atTime(end)
            while (!t.isAfter(last)) {
                if (t.isAfter(now)) return t
                t = t.plusMinutes(intervalMin.toLong())
            }
        }
        return null
    }

    /** Todos os horários de água de um dia (para mostrar na tela de lembretes). */
    fun waterSlots(start: LocalTime, end: LocalTime, intervalMin: Int): List<LocalTime> {
        if (intervalMin <= 0 || end.isBefore(start)) return emptyList()
        val out = mutableListOf<LocalTime>()
        var t = start
        while (!t.isAfter(end) && out.size < 96) {
            out += t
            val next = t.plusMinutes(intervalMin.toLong())
            if (next.isBefore(t)) break // passou da meia-noite
            t = next
        }
        return out
    }
}
