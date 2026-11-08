package br.com.projeto40dias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectAndReportTest {
    private val start = LocalDate.of(2026, 11, 8)

    @Test fun `dia 12 de 40 equivale a 30 por cento`() {
        val s = ProjectCalculator.status(start, start.plusDays(11))
        assertEquals(12, s.dayNumber)
        assertEquals(0.30f, s.progress, 0.001f)
        assertEquals(28, s.daysRemaining)
        assertEquals(2, s.phase?.index)
    }

    @Test fun `antes do inicio e apos o fim`() {
        val before = ProjectCalculator.status(start, start.minusDays(3))
        assertFalse(before.started); assertEquals(0, before.dayNumber); assertEquals(40, before.daysRemaining)
        val after = ProjectCalculator.status(start, start.plusDays(60))
        assertTrue(after.finished); assertEquals(40, after.dayNumber); assertEquals(0, after.daysRemaining); assertEquals(1f, after.progress, 0f)
    }

    @Test fun `fases cobrem os 40 dias`() {
        (1..40).forEach { assertTrue("dia $it", ProjectCalculator.phaseFor(it) != null) }
        assertEquals("Últimos 5 dias – Recuperação e avaliação", ProjectCalculator.phaseFor(36)?.title)
        assertEquals(start.plusDays(39), ProjectCalculator.end(start))
    }

    @Test fun `recuperacao`() {
        assertTrue(Recovery.isRecovered(1, 2, 4))
        assertFalse(Recovery.isRecovered(3, 1, 5))
        assertTrue(Recovery.showFatigueWarning(4, 1, 4))
        assertTrue(Recovery.showFatigueWarning(1, 1, 2))
        assertFalse(Recovery.showFatigueWarning(2, 2, 3))
    }

    @Test fun `corrida ritmo e duracao`() {
        assertEquals(360, RunCalculator.paceSecPerKm(7.0, 42 * 60))
        assertEquals("6:00 /km", RunCalculator.formatPace(360))
        assertEquals(2730, RunCalculator.parseDuration("45:30"))
        assertEquals(3930, RunCalculator.parseDuration("1:05:30"))
        assertEquals(2700, RunCalculator.parseDuration("45"))
        assertNull(RunCalculator.parseDuration("abc"))
        assertNull(RunCalculator.paceSecPerKm(0.0, 100))
        assertEquals(577, RunCalculator.estimatedKcal(82.5, 7.0))
    }

    private fun day(offset: Long, kcal: Double, meals: Int, ex: Int, exSched: Int, sess: Int, sessSched: Int) =
        DaySummary(start.plusDays(offset), kcal, kcal / 15, meals, 6, ex, exSched, sess, sessSched)

    @Test fun `relatorio semanal`() {
        val days = (0L..6L).map { day(it, if (it < 5) 2300.0 else 0.0, if (it < 5) 6 else 0, 6, 7, 1, 2) }
        val weights = listOf(DatedValue(start.minusDays(1), 82.5), DatedValue(start.plusDays(3), 82.0), DatedValue(start.plusDays(6), 81.6))
        val waists = listOf(DatedValue(start.plusDays(6), 90.0))
        val today = start.plusDays(6)
        val r = ReportCalculator.period("S1", start, start.plusDays(6), today, days, weights, waists)
        assertEquals(82.5, r.weightStart!!, 0.0)
        assertEquals(81.6, r.weightEnd!!, 0.0)
        assertEquals(-0.9, r.weightDelta!!, 1e-9)
        assertEquals(2300.0, r.avgKcal!!, 0.0) // só dias com refeição concluída
        assertEquals(5, r.daysWithData)
        assertEquals(30 * 100 / 42, r.dietAdherencePct)
        assertEquals(42 * 100 / 49, r.exerciseAdherencePct)
        assertEquals(14, r.sessionsScheduled); assertEquals(7, r.sessionsDone)
        assertEquals(90.0, r.waistCm!!, 0.0)
    }

    @Test fun `dias futuros nao contam como falha`() {
        val days = (0L..6L).map { day(it, 2300.0, 6, 7, 7, 2, 2) }
        val r = ReportCalculator.period("S1", start, start.plusDays(6), start.plusDays(2), days, emptyList(), emptyList())
        assertEquals(100, r.dietAdherencePct)
        assertEquals(6, r.sessionsScheduled)
        assertNull(r.weightDelta)
    }

    @Test fun `seis relatorios para 40 dias`() {
        val w = ReportCalculator.weekly(start, 40, start, emptyList(), emptyList(), emptyList())
        assertEquals(6, w.size)
        assertEquals(start.plusDays(35), w.last().from)
        assertEquals(start.plusDays(39), w.last().to)
    }
}
