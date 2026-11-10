package br.com.projeto40dias.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Todas as datas são gravadas como texto ISO (yyyy-MM-dd) — ordenável e legível,
 * e facilita uma futura sincronização em nuvem (campo updatedAt em cada registro).
 */

/** Estado do alimento em que a quantidade é pesada. */
enum class FoodState(val label: String) { CRU("cru"), COZIDO("cozido/preparado"), PRONTO("pronto p/ consumo") }

/** Grupos usados na tela de substituições. */
enum class SubstitutionGroup(val label: String) { PROTEINA("Proteínas"), CARBOIDRATO("Carboidratos"), FRUTA("Frutas") }

/** Banco de alimentos — valores nutricionais por 100 g. */
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val state: FoodState,
    val substitutionGroup: SubstitutionGroup? = null,
    val custom: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Refeição do cardápio padrão (modelo). */
@Entity(tableName = "meal_templates")
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val label: String,
    val time: String, // HH:mm
    val position: Int,
    val optional: Boolean = false,
    /** Refeição removida do cardápio. Fica guardada para não apagar o histórico dos dias anteriores. */
    val archived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Alimento dentro do cardápio padrão. */
@Entity(tableName = "meal_template_items", indices = [Index("mealId")])
data class MealTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodId: Long,
    val grams: Double,
)

/**
 * Itens de uma refeição em um dia específico. São copiados do cardápio padrão
 * quando o dia é aberto pela primeira vez — assim, editar ou substituir um alimento
 * em um dia não altera o histórico dos dias anteriores.
 */
@Entity(tableName = "day_meal_items", indices = [Index("date"), Index(value = ["date", "mealId"])])
data class DayMealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealId: Long,
    val foodId: Long,
    val grams: Double,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Marca se uma refeição foi concluída em determinado dia. */
@Entity(tableName = "meal_logs", primaryKeys = ["date", "mealId"])
data class MealLogEntity(
    val date: String,
    val mealId: Long,
    val done: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Controle para saber se os itens do dia já foram criados. */
@Entity(tableName = "day_initialized")
data class DayInitializedEntity(@PrimaryKey val date: String)

enum class ExerciseType { FORCA, CORRIDA, MOBILIDADE }
enum class Session(val label: String) { MANHA("Manhã"), NOITE("Noite"), DIA("Dia") }

/** Exercício programado na semana (modelo). dayOfWeek: 1 = segunda … 7 = domingo. */
@Entity(tableName = "exercise_templates", indices = [Index("dayOfWeek")])
data class ExerciseTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val session: Session,
    val sessionName: String,
    val name: String,
    val type: ExerciseType,
    val prescription: String, // ex.: "4 × 8–10", "35–45 min · Zona 2"
    val targetSets: Int? = null,
    val optional: Boolean = false,
    val position: Int,
    /** Horário da sessão (HH:mm) — o mesmo para todos os exercícios da sessão. */
    val sessionTime: String = "09:00",
    /** Exercício removido do treino. Fica guardado para não apagar o histórico. */
    val archived: Boolean = false,
)

/** Registro do exercício realizado em um dia (força ou corrida). */
@Entity(
    tableName = "exercise_logs",
    indices = [Index(value = ["date", "templateId"], unique = true), Index("templateId")],
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val templateId: Long,
    val done: Boolean = false,
    // Força
    val weightKg: Double? = null,
    val reps: Int? = null,
    val sets: Int? = null,
    // Corrida (nada é inventado: só o que o usuário digitar)
    val distanceKm: Double? = null,
    val durationSec: Int? = null,
    val heartRateAvg: Int? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String,
    val notes: String,
    val active: Boolean = true,
)

@Entity(tableName = "supplement_logs", primaryKeys = ["date", "supplementId"])
data class SupplementLogEntity(
    val date: String,
    val supplementId: Long,
    val done: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "water_logs", indices = [Index("date")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val ml: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Origem de uma medição — tudo aqui é MEDIDO, nunca estimado. */
enum class MeasureSource(val label: String) {
    BIOIMPEDANCIA("Bioimpedância"), BALANCA("Balança"), FITA("Fita métrica"), MANUAL("Manual")
}

@Entity(tableName = "body_measurements", indices = [Index("date")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val source: MeasureSource,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val fatPct: Double? = null,
    val fatMassKg: Double? = null,
    val muscleKg: Double? = null,
    val visceral: Double? = null,
    val waistHipRatio: Double? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Cada ciclo de 40 dias. Reiniciar cria um novo projeto e arquiva o anterior (histórico preservado). */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: String,
    val lengthDays: Int = 40,
    val targetWeightKg: Double,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Check-in diário de recuperação (escala 1–5). */
@Entity(tableName = "checkins")
data class CheckinEntity(
    @PrimaryKey val date: String,
    val fatigue: Int,     // 1 = descansado … 5 = exausto
    val soreness: Int,    // 1 = sem dor … 5 = muita dor
    val sleep: Int,       // 1 = péssimo … 5 = ótimo
    val updatedAt: Long = System.currentTimeMillis(),
)
