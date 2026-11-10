package br.com.projeto40dias.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Perfil do usuário (dados da avaliação de referência). Todos editáveis. */
data class Profile(
    val name: String = "Kaue Sanchez",
    val sex: String = "Masculino",
    val age: Int = 36,
    val heightCm: Double = 171.0,
    val initialWeightKg: Double = 83.9,
    val muscleKg: Double = 38.8,
    val fatMassKg: Double = 17.2,
    val fatPct: Double = 20.5,
    val visceral: Double = 7.0,
    val waistHipRatio: Double = 0.97,
    val bmrKcal: Int = 1810,
    val bioDate: String = "2026-11-10",
)

/** Metas editáveis. */
data class Goals(
    val kcal: Int = 2400,
    val protein: Int = 175,
    val carbs: Int = 270,
    val fat: Int = 70,
    val waterMl: Int = 3000,
    val targetWeightKg: Double = 78.5,
)

/**
 * Configuração dos lembretes. Os horários das refeições e dos treinos vêm do cardápio
 * e do treino configurados — aqui ficam só as opções de aviso.
 */
data class ReminderSettings(
    val meals: Boolean = true,
    /** Minutos de antecedência do aviso de refeição (0 = na hora). */
    val mealsLeadMin: Int = 0,
    val training: Boolean = true,
    val trainingLeadMin: Int = 15,
    val water: Boolean = true,
    val waterStart: String = "08:00",
    val waterEnd: String = "22:00",
    val waterIntervalMin: Int = 120,
    val creatine: Boolean = false,
    val creatineTime: String = "08:00",
    val weighIn: Boolean = false,
    val weighInTime: String = "07:30",
)

class SettingsRepository(private val context: Context) {

    private object K {
        val name = stringPreferencesKey("p_name")
        val sex = stringPreferencesKey("p_sex")
        val age = intPreferencesKey("p_age")
        val height = doublePreferencesKey("p_height")
        val initWeight = doublePreferencesKey("p_init_weight")
        val muscle = doublePreferencesKey("p_muscle")
        val fatMass = doublePreferencesKey("p_fat_mass")
        val fatPct = doublePreferencesKey("p_fat_pct")
        val visceral = doublePreferencesKey("p_visceral")
        val whr = doublePreferencesKey("p_whr")
        val bmr = intPreferencesKey("p_bmr")
        val bioDate = stringPreferencesKey("p_bio_date")

        val kcal = intPreferencesKey("g_kcal")
        val protein = intPreferencesKey("g_protein")
        val carbs = intPreferencesKey("g_carbs")
        val fat = intPreferencesKey("g_fat")
        val water = intPreferencesKey("g_water")
        val target = doublePreferencesKey("g_target")

        val disclaimerAccepted = booleanPreferencesKey("disclaimer_ok")
        val rMeals = booleanPreferencesKey("r2_meals")
        val rMealsLead = intPreferencesKey("r2_meals_lead")
        val rTraining = booleanPreferencesKey("r2_training")
        val rTrainingLead = intPreferencesKey("r2_training_lead")
        val rWater = booleanPreferencesKey("r2_water")
        val rWaterStart = stringPreferencesKey("r2_water_start")
        val rWaterEnd = stringPreferencesKey("r2_water_end")
        val rWaterInterval = intPreferencesKey("r2_water_interval")
        // Chaves da versão anterior, reaproveitadas para creatina e pesagem.
        val rCreatine = booleanPreferencesKey("r_on_CREATINA")
        val rCreatineTime = stringPreferencesKey("r_time_CREATINA")
        val rWeighIn = booleanPreferencesKey("r_on_PESAGEM")
        val rWeighInTime = stringPreferencesKey("r_time_PESAGEM")
    }

    private val d = Profile()
    private val g = Goals()

    val profile: Flow<Profile> = context.dataStore.data.map { p ->
        Profile(
            name = p[K.name] ?: d.name, sex = p[K.sex] ?: d.sex, age = p[K.age] ?: d.age,
            heightCm = p[K.height] ?: d.heightCm, initialWeightKg = p[K.initWeight] ?: d.initialWeightKg,
            muscleKg = p[K.muscle] ?: d.muscleKg, fatMassKg = p[K.fatMass] ?: d.fatMassKg,
            fatPct = p[K.fatPct] ?: d.fatPct, visceral = p[K.visceral] ?: d.visceral,
            waistHipRatio = p[K.whr] ?: d.waistHipRatio, bmrKcal = p[K.bmr] ?: d.bmrKcal,
            bioDate = p[K.bioDate] ?: d.bioDate,
        )
    }

    val goals: Flow<Goals> = context.dataStore.data.map { p ->
        Goals(
            kcal = p[K.kcal] ?: g.kcal, protein = p[K.protein] ?: g.protein, carbs = p[K.carbs] ?: g.carbs,
            fat = p[K.fat] ?: g.fat, waterMl = p[K.water] ?: g.waterMl, targetWeightKg = p[K.target] ?: g.targetWeightKg,
        )
    }

    private val r = ReminderSettings()

    val reminders: Flow<ReminderSettings> = context.dataStore.data.map { p ->
        ReminderSettings(
            meals = p[K.rMeals] ?: r.meals, mealsLeadMin = p[K.rMealsLead] ?: r.mealsLeadMin,
            training = p[K.rTraining] ?: r.training, trainingLeadMin = p[K.rTrainingLead] ?: r.trainingLeadMin,
            water = p[K.rWater] ?: r.water, waterStart = p[K.rWaterStart] ?: r.waterStart,
            waterEnd = p[K.rWaterEnd] ?: r.waterEnd, waterIntervalMin = p[K.rWaterInterval] ?: r.waterIntervalMin,
            creatine = p[K.rCreatine] ?: r.creatine, creatineTime = p[K.rCreatineTime] ?: r.creatineTime,
            weighIn = p[K.rWeighIn] ?: r.weighIn, weighInTime = p[K.rWeighInTime] ?: r.weighInTime,
        )
    }

    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data.map { it[K.disclaimerAccepted] ?: false }

    suspend fun saveProfile(x: Profile) = context.dataStore.edit { p ->
        p[K.name] = x.name; p[K.sex] = x.sex; p[K.age] = x.age; p[K.height] = x.heightCm
        p[K.initWeight] = x.initialWeightKg; p[K.muscle] = x.muscleKg; p[K.fatMass] = x.fatMassKg
        p[K.fatPct] = x.fatPct; p[K.visceral] = x.visceral; p[K.whr] = x.waistHipRatio
        p[K.bmr] = x.bmrKcal; p[K.bioDate] = x.bioDate
    }

    suspend fun saveGoals(x: Goals) = context.dataStore.edit { p ->
        p[K.kcal] = x.kcal; p[K.protein] = x.protein; p[K.carbs] = x.carbs; p[K.fat] = x.fat
        p[K.water] = x.waterMl; p[K.target] = x.targetWeightKg
    }

    suspend fun setWaterGoal(ml: Int) = context.dataStore.edit { it[K.water] = ml }

    suspend fun saveReminders(x: ReminderSettings) = context.dataStore.edit { p ->
        p[K.rMeals] = x.meals; p[K.rMealsLead] = x.mealsLeadMin
        p[K.rTraining] = x.training; p[K.rTrainingLead] = x.trainingLeadMin
        p[K.rWater] = x.water; p[K.rWaterStart] = x.waterStart; p[K.rWaterEnd] = x.waterEnd
        p[K.rWaterInterval] = x.waterIntervalMin
        p[K.rCreatine] = x.creatine; p[K.rCreatineTime] = x.creatineTime
        p[K.rWeighIn] = x.weighIn; p[K.rWeighInTime] = x.weighInTime
    }

    suspend fun acceptDisclaimer() = context.dataStore.edit { it[K.disclaimerAccepted] = true }

    suspend fun currentGoals(): Goals = goals.first()
    suspend fun currentReminders(): ReminderSettings = reminders.first()
}
