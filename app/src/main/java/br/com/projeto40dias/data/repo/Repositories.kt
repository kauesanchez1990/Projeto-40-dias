package br.com.projeto40dias.data.repo

import androidx.room.withTransaction
import br.com.projeto40dias.data.db.AppDatabase
import br.com.projeto40dias.data.db.BodyMeasurementEntity
import br.com.projeto40dias.data.db.CheckinEntity
import br.com.projeto40dias.data.db.DayInitializedEntity
import br.com.projeto40dias.data.db.DayItemWithFood
import br.com.projeto40dias.data.db.DayMealItemEntity
import br.com.projeto40dias.data.db.ExerciseLogEntity
import br.com.projeto40dias.data.db.ExerciseTemplateEntity
import br.com.projeto40dias.data.db.FoodEntity
import br.com.projeto40dias.data.db.MealLogEntity
import br.com.projeto40dias.data.db.MealTemplateEntity
import br.com.projeto40dias.data.db.MealTemplateItemEntity
import br.com.projeto40dias.data.db.ProjectEntity
import br.com.projeto40dias.data.db.SubstitutionGroup
import br.com.projeto40dias.data.db.SupplementLogEntity
import br.com.projeto40dias.data.db.WaterLogEntity
import br.com.projeto40dias.domain.DaySummary
import br.com.projeto40dias.domain.Macros
import br.com.projeto40dias.domain.NutritionCalculator
import br.com.projeto40dias.domain.Per100
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

fun DayItemWithFood.per100() = Per100(kcal100, protein100, carbs100, fat100)
fun DayItemWithFood.macros(): Macros = NutritionCalculator.portion(per100(), grams)
fun FoodEntity.per100() = Per100(kcal100, protein100, carbs100, fat100)

data class MealDay(
    val template: MealTemplateEntity,
    val items: List<DayItemWithFood>,
    val done: Boolean,
) {
    val macros: Macros get() = NutritionCalculator.sum(items.map { it.macros() })
}

data class DayNutrition(val date: LocalDate, val meals: List<MealDay>) {
    /** Consumido = somente refeições marcadas como concluídas. */
    val consumed: Macros get() = NutritionCalculator.sum(meals.filter { it.done }.map { it.macros })
    val planned: Macros get() = NutritionCalculator.sum(meals.filter { !it.template.optional }.map { it.macros })
    val mealsDone: Int get() = meals.count { it.done && !it.template.optional }
    val mealsScheduled: Int get() = meals.count { !it.template.optional }
}

// ------------------------------------------------------------------ Nutrição

class NutritionRepository(private val db: AppDatabase, private val awaitReady: suspend () -> Unit = {}) {
    private val meal = db.mealDao()
    private val food = db.foodDao()

    /** Copia o cardápio padrão para o dia (uma única vez). */
    suspend fun ensureDay(date: LocalDate) {
        awaitReady()
        val d = date.toString()
        if (meal.isInitialized(d) > 0) return
        db.withTransaction {
            if (meal.isInitialized(d) > 0) return@withTransaction
            val items = meal.allTemplateItems().map { DayMealItemEntity(date = d, mealId = it.mealId, foodId = it.foodId, grams = it.grams) }
            meal.insertDayItems(items)
            meal.markInitialized(DayInitializedEntity(d))
        }
    }

    fun observeDay(date: LocalDate): Flow<DayNutrition> =
        combine(meal.observeTemplates(), meal.observeDayItems(date.toString()), meal.observeLogs(date.toString())) { t, items, logs ->
            val doneIds = logs.filter { it.done }.map { it.mealId }.toSet()
            DayNutrition(date, t.map { m -> MealDay(m, items.filter { it.mealId == m.id }, m.id in doneIds) })
        }

    suspend fun setMealDone(date: LocalDate, mealId: Long, done: Boolean) =
        meal.upsertLog(MealLogEntity(date.toString(), mealId, done))

    suspend fun updateGrams(itemId: Long, grams: Double) = meal.updateGrams(itemId, grams, System.currentTimeMillis())

    suspend fun replaceFood(itemId: Long, foodId: Long, grams: Double) =
        meal.replaceFood(itemId, foodId, grams, System.currentTimeMillis())

    suspend fun addItem(date: LocalDate, mealId: Long, foodId: Long, grams: Double) =
        meal.insertDayItem(DayMealItemEntity(date = date.toString(), mealId = mealId, foodId = foodId, grams = grams))

    suspend fun deleteItem(itemId: Long) = meal.deleteDayItem(itemId)

    /** Torna a composição do dia o novo padrão para os próximos dias. */
    suspend fun saveMealAsTemplate(date: LocalDate, mealId: Long) = db.withTransaction {
        val items = meal.dayItemsOf(date.toString(), mealId)
        meal.clearTemplateItems(mealId)
        meal.insertTemplateItems(items.map { MealTemplateItemEntity(mealId = mealId, foodId = it.foodId, grams = it.grams) })
    }

    suspend fun updateMealTemplate(t: MealTemplateEntity) = meal.upsertTemplate(t.copy(updatedAt = System.currentTimeMillis()))

    fun observeTemplates() = meal.observeTemplates()
    fun searchFoods(q: String) = food.search(q)
    fun foodsByGroup(g: SubstitutionGroup) = food.byGroup(g)
    fun allFoods() = food.observeAll()
    suspend fun food(id: Long) = food.get(id)
    suspend fun saveFood(f: FoodEntity) = food.upsert(f.copy(updatedAt = System.currentTimeMillis()))
    fun historyDates() = meal.observeHistoryDates()
}

// ------------------------------------------------------------------ Treinos

data class ExerciseWithLog(val template: ExerciseTemplateEntity, val log: ExerciseLogEntity?) {
    val done get() = log?.done == true
}

data class SessionDay(val session: String, val name: String, val optional: Boolean, val exercises: List<ExerciseWithLog>) {
    val done get() = exercises.isNotEmpty() && exercises.all { it.done }
}

class TrainingRepository(private val db: AppDatabase) {
    private val dao = db.trainingDao()

    fun observeDay(date: LocalDate): Flow<List<SessionDay>> =
        combine(dao.observeTemplatesForDay(date.dayOfWeek.value), dao.observeLogs(date.toString())) { t, logs ->
            groupSessions(t, logs)
        }

    fun observeWeekTemplates() = dao.observeTemplates()

    suspend fun setDone(date: LocalDate, templateId: Long, done: Boolean) {
        val current = dao.log(date.toString(), templateId)
        dao.upsertLog((current ?: ExerciseLogEntity(date = date.toString(), templateId = templateId))
            .copy(done = done, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveLog(log: ExerciseLogEntity) {
        val current = dao.log(log.date, log.templateId)
        dao.upsertLog(log.copy(id = current?.id ?: 0, updatedAt = System.currentTimeMillis()))
    }

    fun history(templateId: Long) = dao.observeHistory(templateId)

    companion object {
        fun groupSessions(t: List<ExerciseTemplateEntity>, logs: List<ExerciseLogEntity>): List<SessionDay> {
            val byTemplate = logs.associateBy { it.templateId }
            return t.groupBy { Triple(it.session, it.sessionName, it.optional) }
                .map { (k, list) ->
                    SessionDay(k.first.label, k.second, k.third,
                        list.sortedBy { it.position }.map { ExerciseWithLog(it, byTemplate[it.id]) })
                }
                .sortedBy { it.optional }
        }
    }
}

// ------------------------------------------------------------------ Água, suplementos e check-in

class TrackingRepository(private val db: AppDatabase) {
    private val water = db.waterDao()
    private val supp = db.supplementDao()
    private val checkin = db.checkinDao()

    fun waterTotal(date: LocalDate) = water.observeTotal(date.toString())
    fun waterEntries(date: LocalDate) = water.observeEntries(date.toString())
    fun waterDaily() = water.observeDaily()
    suspend fun addWater(date: LocalDate, ml: Int) = water.insert(WaterLogEntity(date = date.toString(), ml = ml))
    suspend fun deleteWater(e: WaterLogEntity) = water.delete(e)
    suspend fun waterTotalNow(date: LocalDate) = water.total(date.toString())

    fun supplements() = supp.observeActive()
    fun supplementLogs(date: LocalDate) = supp.observeLogs(date.toString())
    suspend fun setSupplement(date: LocalDate, id: Long, done: Boolean) =
        supp.upsertLog(SupplementLogEntity(date.toString(), id, done))

    fun checkin(date: LocalDate) = checkin.observe(date.toString())
    suspend fun saveCheckin(c: CheckinEntity) = checkin.upsert(c.copy(updatedAt = System.currentTimeMillis()))
}

// ------------------------------------------------------------------ Evolução corporal e projeto

class BodyRepository(private val db: AppDatabase) {
    fun all() = db.bodyDao().observeAll()
    suspend fun save(m: BodyMeasurementEntity) = db.bodyDao().upsert(m.copy(updatedAt = System.currentTimeMillis()))
    suspend fun delete(m: BodyMeasurementEntity) = db.bodyDao().delete(m)
}

class ProjectRepository(private val db: AppDatabase) {
    fun active() = db.projectDao().observeActive()
    fun all() = db.projectDao().observeAll()
    suspend fun update(p: ProjectEntity) = db.projectDao().upsert(p)

    /** Inicia novo ciclo de 40 dias. Todo o histórico (refeições, treinos, medidas) é mantido. */
    suspend fun restart(start: LocalDate, targetWeight: Double) = db.withTransaction {
        db.projectDao().deactivateAll()
        db.projectDao().insert(ProjectEntity(startDate = start.toString(), targetWeightKg = targetWeight))
    }
}

// ------------------------------------------------------------------ Relatórios

class ReportRepository(private val db: AppDatabase) {

    /** Resumo por dia entre [from] e [to] (inclusive). */
    fun daySummaries(from: LocalDate, to: LocalDate): Flow<List<DaySummary>> {
        val f = from.toString(); val t = to.toString()
        return combine(
            db.mealDao().observeTemplates(),
            db.mealDao().observeItemsBetween(f, t),
            db.mealDao().observeLogsBetween(f, t),
            db.trainingDao().observeTemplates(),
            db.trainingDao().observeLogsBetween(f, t),
        ) { mealT, items, mealLogs, exT, exLogs ->
            val optionalMeals = mealT.filter { it.optional }.map { it.id }.toSet()
            val scheduledMeals = mealT.count { !it.optional }
            val itemsByDate = items.groupBy { it.date }
            val mealLogsByDate = mealLogs.groupBy { it.date }
            val exLogsByDate = exLogs.groupBy { it.date }

            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { day ->
                val ds = day.toString()
                val doneMeals = mealLogsByDate[ds].orEmpty().filter { it.done }.map { it.mealId }.toSet()
                val consumed = NutritionCalculator.sum(itemsByDate[ds].orEmpty().filter { it.mealId in doneMeals }.map { it.macros() })
                val dayEx = exT.filter { it.dayOfWeek == day.dayOfWeek.value && !it.optional }
                val logsHere = exLogsByDate[ds].orEmpty()
                val sessions = groupSessionsForReport(dayEx, logsHere)
                DaySummary(
                    date = day,
                    kcal = consumed.kcal,
                    protein = consumed.protein,
                    mealsDone = doneMeals.count { it !in optionalMeals },
                    mealsScheduled = scheduledMeals,
                    exercisesDone = dayEx.count { e -> logsHere.any { it.templateId == e.id && it.done } },
                    exercisesScheduled = dayEx.size,
                    sessionsDone = sessions.count { it.done },
                    sessionsScheduled = sessions.size,
                )
            }.toList()
        }
    }

    private fun groupSessionsForReport(t: List<ExerciseTemplateEntity>, logs: List<ExerciseLogEntity>) =
        TrainingRepository.groupSessions(t, logs)
}
