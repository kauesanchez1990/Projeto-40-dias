package br.com.projeto40dias.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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
            Room.databaseBuilder(context, AppDatabase::class.java, "projeto40dias.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: refeições/exercícios podem ser removidos (arquivados) e cada sessão de treino tem horário. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_templates ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercise_templates ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercise_templates ADD COLUMN sessionTime TEXT NOT NULL DEFAULT '09:00'")
                db.execSQL("UPDATE exercise_templates SET sessionTime = '20:00' WHERE session = 'NOITE'")
                db.execSQL("UPDATE exercise_templates SET sessionTime = '10:00' WHERE session = 'DIA'")
            }
        }
    }
}
