package br.com.projeto40dias.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Item do dia com os dados do alimento já resolvidos. */
data class DayItemWithFood(
    val id: Long,
    val date: String,
    val mealId: Long,
    val foodId: Long,
    val grams: Double,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val state: FoodState,
    val substitutionGroup: SubstitutionGroup?,
)

data class TemplateItemWithFood(
    val id: Long,
    val mealId: Long,
    val foodId: Long,
    val grams: Double,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val state: FoodState,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY name")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :q || '%' ORDER BY name")
    fun search(q: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE substitutionGroup = :group ORDER BY name")
    fun byGroup(group: SubstitutionGroup): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun get(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): FoodEntity?

    @Upsert
    suspend fun upsert(food: FoodEntity): Long

    @Insert
    suspend fun insertAll(foods: List<FoodEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_templates ORDER BY position")
    fun observeTemplates(): Flow<List<MealTemplateEntity>>

    @Query("SELECT * FROM meal_templates ORDER BY position")
    suspend fun templates(): List<MealTemplateEntity>

    @Insert
    suspend fun insertTemplate(t: MealTemplateEntity): Long

    @Upsert
    suspend fun upsertTemplate(t: MealTemplateEntity)

    @Insert
    suspend fun insertTemplateItems(items: List<MealTemplateItemEntity>)

    @Query("SELECT * FROM meal_template_items")
    suspend fun allTemplateItems(): List<MealTemplateItemEntity>

    @Query(
        """SELECT i.id, i.mealId, i.foodId, i.grams, f.name, f.kcal100, f.protein100, f.carbs100, f.fat100, f.state
           FROM meal_template_items i JOIN foods f ON f.id = i.foodId ORDER BY i.id"""
    )
    fun observeTemplateItems(): Flow<List<TemplateItemWithFood>>

    @Query("DELETE FROM meal_template_items WHERE mealId = :mealId")
    suspend fun clearTemplateItems(mealId: Long)

    // ---- Dia ----
    @Query("SELECT COUNT(*) FROM day_initialized WHERE date = :date")
    suspend fun isInitialized(date: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markInitialized(d: DayInitializedEntity)

    @Insert
    suspend fun insertDayItems(items: List<DayMealItemEntity>)

    @Insert
    suspend fun insertDayItem(item: DayMealItemEntity): Long

    @Query("UPDATE day_meal_items SET grams = :grams, updatedAt = :now WHERE id = :id")
    suspend fun updateGrams(id: Long, grams: Double, now: Long)

    @Query("UPDATE day_meal_items SET foodId = :foodId, grams = :grams, updatedAt = :now WHERE id = :id")
    suspend fun replaceFood(id: Long, foodId: Long, grams: Double, now: Long)

    @Query("DELETE FROM day_meal_items WHERE id = :id")
    suspend fun deleteDayItem(id: Long)

    @Query("SELECT * FROM day_meal_items WHERE date = :date AND mealId = :mealId")
    suspend fun dayItemsOf(date: String, mealId: Long): List<DayMealItemEntity>

    @Query(
        """SELECT i.id, i.date, i.mealId, i.foodId, i.grams, f.name, f.kcal100, f.protein100, f.carbs100,
                  f.fat100, f.state, f.substitutionGroup
           FROM day_meal_items i JOIN foods f ON f.id = i.foodId
           WHERE i.date = :date ORDER BY i.id"""
    )
    fun observeDayItems(date: String): Flow<List<DayItemWithFood>>

    @Query(
        """SELECT i.id, i.date, i.mealId, i.foodId, i.grams, f.name, f.kcal100, f.protein100, f.carbs100,
                  f.fat100, f.state, f.substitutionGroup
           FROM day_meal_items i JOIN foods f ON f.id = i.foodId
           WHERE i.date BETWEEN :from AND :to"""
    )
    fun observeItemsBetween(from: String, to: String): Flow<List<DayItemWithFood>>

    @Query("SELECT * FROM meal_logs WHERE date = :date")
    fun observeLogs(date: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE date BETWEEN :from AND :to")
    fun observeLogsBetween(from: String, to: String): Flow<List<MealLogEntity>>

    @Upsert
    suspend fun upsertLog(log: MealLogEntity)

    @Query("SELECT DISTINCT date FROM day_initialized ORDER BY date DESC")
    fun observeHistoryDates(): Flow<List<String>>
}

@Dao
interface TrainingDao {
    @Query("SELECT * FROM exercise_templates ORDER BY dayOfWeek, session, position")
    fun observeTemplates(): Flow<List<ExerciseTemplateEntity>>

    @Query("SELECT * FROM exercise_templates WHERE dayOfWeek = :dow ORDER BY session, position")
    fun observeTemplatesForDay(dow: Int): Flow<List<ExerciseTemplateEntity>>

    @Query("SELECT * FROM exercise_templates")
    suspend fun allTemplates(): List<ExerciseTemplateEntity>

    @Insert
    suspend fun insertTemplates(list: List<ExerciseTemplateEntity>)

    @Query("SELECT COUNT(*) FROM exercise_templates")
    suspend fun countTemplates(): Int

    @Query("SELECT * FROM exercise_logs WHERE date = :date")
    fun observeLogs(date: String): Flow<List<ExerciseLogEntity>>

    @Query("SELECT * FROM exercise_logs WHERE date BETWEEN :from AND :to")
    fun observeLogsBetween(from: String, to: String): Flow<List<ExerciseLogEntity>>

    @Query("SELECT * FROM exercise_logs WHERE templateId = :templateId AND done = 1 ORDER BY date DESC")
    fun observeHistory(templateId: Long): Flow<List<ExerciseLogEntity>>

    @Query("SELECT * FROM exercise_logs WHERE date = :date AND templateId = :templateId")
    suspend fun log(date: String, templateId: Long): ExerciseLogEntity?

    @Upsert
    suspend fun upsertLog(log: ExerciseLogEntity)
}

@Dao
interface SupplementDao {
    @Query("SELECT * FROM supplements WHERE active = 1")
    fun observeActive(): Flow<List<SupplementEntity>>

    @Insert
    suspend fun insertAll(list: List<SupplementEntity>)

    @Upsert
    suspend fun upsert(s: SupplementEntity)

    @Query("SELECT COUNT(*) FROM supplements")
    suspend fun count(): Int

    @Query("SELECT * FROM supplement_logs WHERE date = :date")
    fun observeLogs(date: String): Flow<List<SupplementLogEntity>>

    @Upsert
    suspend fun upsertLog(log: SupplementLogEntity)
}

data class DailyWater(val date: String, val total: Int)

@Dao
interface WaterDao {
    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_logs WHERE date = :date")
    fun observeTotal(date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_logs WHERE date = :date")
    suspend fun total(date: String): Int

    @Query("SELECT * FROM water_logs WHERE date = :date ORDER BY timestamp DESC")
    fun observeEntries(date: String): Flow<List<WaterLogEntity>>

    @Query("SELECT date, SUM(ml) AS total FROM water_logs GROUP BY date ORDER BY date DESC LIMIT 60")
    fun observeDaily(): Flow<List<DailyWater>>

    @Insert
    suspend fun insert(log: WaterLogEntity)

    @Delete
    suspend fun delete(log: WaterLogEntity)
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_measurements ORDER BY date ASC, id ASC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Upsert
    suspend fun upsert(m: BodyMeasurementEntity)

    @Delete
    suspend fun delete(m: BodyMeasurementEntity)

    @Query("SELECT COUNT(*) FROM body_measurements")
    suspend fun count(): Int
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE active = 1 ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects ORDER BY id DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE active = 1 ORDER BY id DESC LIMIT 1")
    suspend fun active(): ProjectEntity?

    @Insert
    suspend fun insert(p: ProjectEntity): Long

    @Upsert
    suspend fun upsert(p: ProjectEntity)

    @Query("UPDATE projects SET active = 0")
    suspend fun deactivateAll()
}

@Dao
interface CheckinDao {
    @Query("SELECT * FROM checkins WHERE date = :date")
    fun observe(date: String): Flow<CheckinEntity?>

    @Upsert
    suspend fun upsert(c: CheckinEntity)
}
