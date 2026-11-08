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
    val age: Int = 35,
    val heightCm: Double = 171.0,
    val initialWeightKg: Double = 82.5,
    val muscleKg: Double = 38.5,
    val fatMassKg: Double = 16.3,
    val fatPct: Double = 19.7,
    val visceral: Double = 6.0,
    val waistHipRatio: Double = 0.93,
    val bmrKcal: Int = 1800,
    val bioDate: String = "2026-08-06",
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

enum class ReminderKey(val label: String, val defaultTime: String) {
    PRE_TREINO("Pré-treino", "08:00"),
    POS_TREINO("Pós-treino", "11:30"),
    ALMOCO("Almoço", "14:00"),
    LANCHE("Lanche", "17:00"),
    PRE_TREINO_NOITE("Pré-treino noturno", "19:00"),
    JANTAR("Jantar", "21:30"),
    CREATINA("Creatina", "08:00"),
    HIDRATACAO("Hidratação (a cada 2 h, 8h–22h)", "08:00"),
    PESAGEM("Pesagem semanal (segunda)", "07:30"),
}

data class Reminder(val key: ReminderKey, val enabled: Boolean, val time: String)

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
        fun enabled(k: ReminderKey) = booleanPreferencesKey("r_on_${k.name}")
        fun time(k: ReminderKey) = stringPreferencesKey("r_time_${k.name}")
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

    val reminders: Flow<List<Reminder>> = context.dataStore.data.map { p ->
        ReminderKey.entries.map { k -> Reminder(k, p[K.enabled(k)] ?: false, p[K.time(k)] ?: k.defaultTime) }
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

    suspend fun saveReminder(r: Reminder) = context.dataStore.edit { p ->
        p[K.enabled(r.key)] = r.enabled; p[K.time(r.key)] = r.time
    }

    suspend fun acceptDisclaimer() = context.dataStore.edit { it[K.disclaimerAccepted] = true }

    suspend fun currentGoals(): Goals = goals.first()
    suspend fun currentReminders(): List<Reminder> = reminders.first()
}
