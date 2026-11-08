package br.com.projeto40dias

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.projeto40dias.data.db.AppDatabase
import br.com.projeto40dias.data.db.ExerciseLogEntity
import br.com.projeto40dias.data.db.SeedData
import br.com.projeto40dias.data.repo.NutritionRepository
import br.com.projeto40dias.data.repo.TrainingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Verifica, em aparelho/emulador: refeições registradas, cálculo de macros,
 * conclusão/edição de treinos e persistência após fechar e reabrir o banco.
 * Rodar: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "teste_persistencia.db"
    private val today = LocalDate.of(2026, 11, 8)

    private fun open() = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()

    @Test
    fun dadosSobrevivemAoFecharEReabrir() = runTest {
        ctx.deleteDatabase(name)

        var db = open()
        SeedData.seedIfEmpty(db, today, 78.5)
        val nutrition = NutritionRepository(db)
        nutrition.ensureDay(today)

        val day = nutrition.observeDay(today).first()
        assertEquals(7, day.meals.size)
        assertEquals(0.0, day.consumed.kcal, 0.0)

        // Conclui a refeição 1 (banana 120 g + whey 25 g + café 200 g ≈ 222 kcal)
        val r1 = day.meals.first()
        nutrition.setMealDone(today, r1.template.id, true)
        val after = nutrition.observeDay(today).first()
        assertEquals(r1.macros.kcal, after.consumed.kcal, 0.01)
        assertTrue(after.consumed.kcal in 200.0..240.0)

        // Edita quantidade da banana
        val banana = r1.items.first { it.name == "Banana" }
        nutrition.updateGrams(banana.id, 200.0)

        // Conclui e edita um treino (segunda-feira = supino reto)
        val training = TrainingRepository(db)
        val monday = LocalDate.of(2026, 11, 9)
        val supino = training.observeDay(monday).first().first().exercises.first()
        training.setDone(monday, supino.template.id, true)
        training.saveLog(ExerciseLogEntity(date = monday.toString(), templateId = supino.template.id, done = true, weightKg = 40.0, reps = 10, sets = 4))

        db.close()

        // "Fecha o app" e reabre
        db = open()
        val reopened = NutritionRepository(db).observeDay(today).first()
        assertTrue(reopened.meals.first().done)
        assertEquals(200.0, reopened.meals.first().items.first { it.name == "Banana" }.grams, 0.0)
        val log = TrainingRepository(db).observeDay(monday).first().first().exercises.first().log
        assertEquals(40.0, log?.weightKg ?: 0.0, 0.0)
        assertTrue(log?.done == true)

        // Seed não roda de novo nem duplica dados
        SeedData.seedIfEmpty(db, today, 78.5)
        assertEquals(7, NutritionRepository(db).observeDay(today).first().meals.size)
        db.close()
        ctx.deleteDatabase(name)
    }
}
