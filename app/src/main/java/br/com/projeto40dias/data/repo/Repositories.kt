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
import br.com.projeto40dias.data.db.Session
import br.com.projeto40dias.data.db.SupplementLogEntity
import br.com.projeto40dias.data.db.TemplateItemWithFood
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

data class MealPlan(val template: MealTemplateEntity, val items: List<TemplateItemWithFood>) {
    val macros: Macros get() = NutritionCalculator.sum(items.map { NutritionCalculator.portion(Per100(it.kcal100, it.protein100, it.carbs100, it.fat100), it.grams) })
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
            val active = meal.templates().map { it.id }.toSet()
            val items = meal.allTemplateItems().filter { it.mealId in active }
                .map { DayMealItemEntity(date = d, mealId = it.mealId, foodId = it.foodId, grams = it.grams) }
            meal.insertDayItems(items)
            meal.markInitialized(DayInitializedEntity(d))
        }
    }

    fun observeDay(date: LocalDate): Flow<DayNutrition> =
        combine(meal.observeAllTemplates(), meal.observeDayItems(date.toString()), meal.observeLogs(date.toString())) { t, items, logs ->
            val doneIds = logs.filter { it.done }.map { it.mealId }.toSet()
            val withItems = items.map { it.mealId }.toSet()
            // Refeições removidas do cardápio continuam aparecendo nos dias em que foram registradas.
            val visible = t.filter { !it.archived || it.id in withItems || it.id in doneIds }
            DayNutrition(date, visible.map { m -> MealDay(m, items.filter { it.mealId == m.id }, m.id in doneIds) })
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
        syncDays(mealId, except = date.toString())
    }

    suspend fun updateMealTemplate(t: MealTemplateEntity) = meal.upsertTemplate(t.copy(updatedAt = System.currentTimeMillis()))

    // ---- Edição do cardápio padrão ----

    /** Cardápio padrão ativo com os alimentos de cada refeição. */
    fun observePlan(): Flow<List<MealPlan>> =
        combine(meal.observeTemplates(), meal.observeTemplateItems()) { t, items ->
            t.map { m -> MealPlan(m, items.filter { it.mealId == m.id }) }
        }

    /** Cria (id = 0) ou atualiza uma refeição do cardápio. */
    suspend fun saveMeal(t: MealTemplateEntity): Long {
        val now = System.currentTimeMillis()
        return if (t.id == 0L) meal.insertTemplate(t.copy(position = meal.maxPosition() + 1, updatedAt = now))
        else { meal.upsertTemplate(t.copy(updatedAt = now)); t.id }
    }

    /** Remove a refeição do cardápio. Dias anteriores e refeições já concluídas são preservados. */
    suspend fun removeMeal(mealId: Long) = db.withTransaction {
        meal.archiveTemplate(mealId, System.currentTimeMillis())
        for (d in meal.initializedFrom(today())) {
            if (meal.isMealDone(d, mealId) == 0) meal.clearDayItems(d, mealId)
        }
    }

    suspend fun addTemplateItem(mealId: Long, foodId: Long, grams: Double) = db.withTransaction {
        meal.insertTemplateItem(MealTemplateItemEntity(mealId = mealId, foodId = foodId, grams = grams))
        syncDays(mealId)
    }

    suspend fun setTemplateGrams(itemId: Long, grams: Double) = db.withTransaction {
        val item = meal.templateItem(itemId) ?: return@withTransaction
        meal.updateTemplateItemGrams(itemId, grams)
        syncDays(item.mealId)
    }

    suspend fun replaceTemplateFood(itemId: Long, foodId: Long, grams: Double) = db.withTransaction {
        val item = meal.templateItem(itemId) ?: return@withTransaction
        meal.replaceTemplateItemFood(itemId, foodId, grams)
        syncDays(item.mealId)
    }

    suspend fun removeTemplateItem(itemId: Long) = db.withTransaction {
        val item = meal.templateItem(itemId) ?: return@withTransaction
        meal.deleteTemplateItem(itemId)
        syncDays(item.mealId)
    }

    /**
     * Leva a composição padrão da refeição para hoje e para os dias futuros já abertos,
     * exceto onde a refeição já foi marcada como concluída (o que foi consumido não muda).
     */
    private suspend fun syncDays(mealId: Long, except: String? = null) {
        val template = meal.templateItemsOf(mealId)
        for (d in meal.initializedFrom(today())) {
            if (d == except || meal.isMealDone(d, mealId) > 0) continue
            meal.clearDayItems(d, mealId)
            meal.insertDayItems(template.map { DayMealItemEntity(date = d, mealId = mealId, foodId = it.foodId, grams = it.grams) })
        }
    }

    private fun today() = LocalDate.now().toString()

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

data class SessionDay(
    val session: String, val name: String, val optional: Boolean, val exercises: List<ExerciseWithLog>, val time: String = "",
) {
    val done get() = exercises.isNotEmpty() && exercises.all { it.done }
}

class TrainingRepository(private val db: AppDatabase) {
    private val dao = db.trainingDao()

    fun observeDay(date: LocalDate): Flow<List<SessionDay>> =
        combine(dao.observeTemplatesForDay(date.dayOfWeek.value), dao.observeLogs(date.toString())) { t, logs ->
            // Exercícios removidos do treino só aparecem nos dias em que foram registrados.
            val logged = logs.map { it.templateId }.toSet()
            groupSessions(t.filter { !it.archived || it.id in logged }, logs)
        }

    fun observeWeekTemplates() = dao.observeTemplates()
    fun observeTemplate(id: Long) = dao.observeTemplate(id)

    // ---- Edição do treino semanal ----

    /** Altera nome, período e horário de uma sessão inteira (todos os exercícios dela). */
    suspend fun updateSession(dow: Int, old: Session, new: Session, name: String, time: String) =
        dao.updateSession(dow, old, new, name, time)

    /** Cria uma sessão com o primeiro exercício. */
    suspend fun addSession(dow: Int, session: Session, name: String, time: String, first: ExerciseTemplateEntity) =
        dao.insertTemplate(first.copy(id = 0, dayOfWeek = dow, session = session, sessionName = name, sessionTime = time, position = 0))

    suspend fun removeSession(dow: Int, session: Session) = dao.archiveSession(dow, session)

    /** Cria (id = 0) ou atualiza um exercício. Novo exercício entra no fim da sessão. */
    suspend fun saveExercise(e: ExerciseTemplateEntity) = db.withTransaction {
        if (e.id == 0L) {
            val list = dao.sessionTemplates(e.dayOfWeek, e.session)
            val first = list.firstOrNull()
            dao.insertTemplate(e.copy(
                position = (list.maxOfOrNull { it.position } ?: -1) + 1,
                sessionName = first?.sessionName ?: e.sessionName,
                sessionTime = first?.sessionTime ?: e.sessionTime,
            ))
            Unit
        } else {
            dao.upsertTemplates(listOf(e))
        }
        Unit
    }

    suspend fun removeExercise(id: Long) = dao.archiveTemplate(id)

    /** Move o exercício uma posição para cima (-1) ou para baixo (+1) dentro da sessão. */
    suspend fun moveExercise(e: ExerciseTemplateEntity, delta: Int) = db.withTransaction {
        val list = dao.sessionTemplates(e.dayOfWeek, e.session).toMutableList()
        val i = list.indexOfFirst { it.id == e.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@withTransaction
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        dao.upsertTemplates(list.mapIndexed { idx, t -> t.copy(position = idx) })
    }

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
                        list.sortedBy { it.position }.map { ExerciseWithLog(it, byTemplate[it.id]) },
                        time = list.first().sessionTime)
                }
                .sortedWith(compareBy({ it.optional }, { it.time }))
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
