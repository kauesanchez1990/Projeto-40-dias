package br.com.projeto40dias.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodEntity::class,
        MealTemplateEntity::class,
        MealTemplateItemEntity::class,
        DayMealItemEntity::class,
        MealLogEntity::class,
        DayInitializedEntity::class,
        ExerciseTemplateEntity::class,
        ExerciseLogEntity::class,
        SupplementEntity::class,
        SupplementLogEntity::class,
        WaterLogEntity::class,
        BodyMeasurementEntity::class,
        ProjectEntity::class,
        CheckinEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun trainingDao(): TrainingDao
    abstract fun supplementDao(): SupplementDao
    abstract fun waterDao(): WaterDao
    abstract fun bodyDao(): BodyDao
    abstract fun projectDao(): ProjectDao
    abstract fun checkinDao(): CheckinDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "projeto40dias.db").build()
    }
}
