package br.com.projeto40dias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    // 10/11/2026 é uma terça-feira.
    private val now = LocalDateTime.of(2026, 11, 10, 13, 0)

    @Test fun `refeicao ainda hoje`() {
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 0), ReminderPlanner.nextDaily(now, LocalTime.of(14, 0)))
    }

    @Test fun `refeicao que ja passou vai para amanha`() {
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 0), ReminderPlanner.nextDaily(now, LocalTime.of(8, 0)))
    }

    @Test fun `horario exato de agora vai para amanha`() {
        assertEquals(LocalDateTime.of(2026, 11, 11, 13, 0), ReminderPlanner.nextDaily(now, LocalTime.of(13, 0)))
    }

    @Test fun `antecedencia desconta minutos`() {
        assertEquals(LocalDateTime.of(2026, 11, 10, 13, 45), ReminderPlanner.nextDaily(now, LocalTime.of(14, 0), 15))
        // 13:10 com 15 min de antecedência = 12:55, já passou -> amanhã
        assertEquals(LocalDateTime.of(2026, 11, 11, 12, 55), ReminderPlanner.nextDaily(now, LocalTime.of(13, 10), 15))
    }

    @Test fun `treino semanal`() {
        // Terça 20:00 ainda hoje
        assertEquals(LocalDateTime.of(2026, 11, 10, 19, 45), ReminderPlanner.nextWeekly(now, DayOfWeek.TUESDAY, LocalTime.of(20, 0), 15))
        // Terça 09:00 já passou -> próxima terça
        assertEquals(LocalDateTime.of(2026, 11, 17, 9, 0), ReminderPlanner.nextWeekly(now, DayOfWeek.TUESDAY, LocalTime.of(9, 0)))
        // Segunda -> próxima segunda
        assertEquals(LocalDateTime.of(2026, 11, 16, 9, 0), ReminderPlanner.nextWeekly(now, DayOfWeek.MONDAY, LocalTime.of(9, 0)))
        // Domingo
        assertEquals(LocalDateTime.of(2026, 11, 15, 9, 0), ReminderPlanner.nextWeekly(now, DayOfWeek.SUNDAY, LocalTime.of(9, 0)))
    }

    @Test fun `antecedencia que cruza a meia noite`() {
        // Quarta 00:10 com 15 min -> terça 23:55
        assertEquals(LocalDateTime.of(2026, 11, 10, 23, 55), ReminderPlanner.nextWeekly(now, DayOfWeek.WEDNESDAY, LocalTime.of(0, 10), 15))
    }

    @Test fun `agua dentro da janela`() {
        val s = LocalTime.of(8, 0); val e = LocalTime.of(22, 0)
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 0), ReminderPlanner.nextWater(now, s, e, 120))
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 0), ReminderPlanner.nextWater(now, s, e, 90)) // 8:00, 9:30, 11:00, 12:30, 14:00
        assertEquals(LocalDateTime.of(2026, 11, 10, 13, 30), ReminderPlanner.nextWater(now, s, e, 30))
        // Depois do fim -> amanhã no início
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 0), ReminderPlanner.nextWater(now.withHour(22).withMinute(5), s, e, 120))
        // Último horário incluído (22:00)
        assertEquals(LocalDateTime.of(2026, 11, 10, 22, 0), ReminderPlanner.nextWater(now.withHour(21), s, e, 120))
        assertNull(ReminderPlanner.nextWater(now, e, s, 120))
        assertNull(ReminderPlanner.nextWater(now, s, e, 0))
    }

    @Test fun `lista de horarios de agua`() {
        assertEquals(8, ReminderPlanner.waterSlots(LocalTime.of(8, 0), LocalTime.of(22, 0), 120).size)
        assertEquals(LocalTime.of(23, 0), ReminderPlanner.waterSlots(LocalTime.of(20, 0), LocalTime.of(23, 59), 180).last())
    }
}
