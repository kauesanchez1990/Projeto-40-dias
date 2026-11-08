package br.com.projeto40dias.data.db

import androidx.room.withTransaction
import java.time.LocalDate

/**
 * Dados iniciais: banco de alimentos (valores por 100 g, referência aproximada
 * TACO/USDA), cardápio, treinos, suplementos, avaliação inicial e projeto.
 * Executado uma única vez — nunca sobrescreve dados do usuário.
 */
object SeedData {

    private data class F(
        val name: String, val kcal: Double, val p: Double, val c: Double, val g: Double,
        val state: FoodState, val group: SubstitutionGroup? = null,
    )

    private val P = SubstitutionGroup.PROTEINA
    private val C = SubstitutionGroup.CARBOIDRATO
    private val FR = SubstitutionGroup.FRUTA
    private val CRU = FoodState.CRU
    private val COZ = FoodState.COZIDO
    private val PR = FoodState.PRONTO

    private val foods = listOf(
        // Proteínas
        F("Peito de frango grelhado", 159.0, 32.0, 0.0, 2.5, COZ, P),
        F("Frango desfiado cozido", 163.0, 31.5, 0.0, 3.2, COZ, P),
        F("Peito de frango cru", 119.0, 21.5, 0.0, 3.0, CRU),
        F("Patinho grelhado", 219.0, 35.9, 0.0, 7.3, COZ, P),
        F("Tilápia grelhada", 128.0, 26.2, 0.0, 2.7, COZ, P),
        F("Peixe branco grelhado (merluza)", 122.0, 26.6, 0.0, 1.2, COZ, P),
        F("Peixe grelhado", 128.0, 26.2, 0.0, 2.7, COZ),
        F("Atum em água (drenado)", 116.0, 25.5, 0.0, 0.8, PR, P),
        F("Lombo suíno magro assado", 210.0, 35.7, 0.0, 6.4, COZ, P),
        F("Ovos inteiros cozidos", 146.0, 13.3, 0.6, 9.5, COZ, P),
        F("Whey protein", 400.0, 80.0, 8.0, 6.0, PR),
        F("Iogurte proteico", 65.0, 9.0, 5.5, 0.5, PR),
        F("Queijo minas frescal", 264.0, 17.4, 3.2, 20.2, PR),
        F("Queijo cottage", 98.0, 11.1, 3.4, 4.3, PR),
        F("Leite desnatado", 35.0, 3.4, 4.9, 0.1, PR),
        // Carboidratos
        F("Arroz branco cozido", 128.0, 2.5, 28.1, 0.2, COZ, C),
        F("Arroz integral cozido", 124.0, 2.6, 25.8, 1.0, COZ, C),
        F("Arroz branco cru", 358.0, 7.2, 78.8, 0.3, CRU),
        F("Batata-doce cozida", 77.0, 0.6, 18.4, 0.1, COZ, C),
        F("Batata-doce crua", 118.0, 1.3, 28.2, 0.1, CRU),
        F("Batata inglesa cozida", 52.0, 1.2, 11.9, 0.0, COZ, C),
        F("Mandioca cozida", 125.0, 0.6, 30.1, 0.3, COZ, C),
        F("Macarrão cozido", 158.0, 5.8, 30.9, 0.9, COZ, C),
        F("Inhame cozido", 116.0, 1.5, 27.5, 0.1, COZ, C),
        F("Feijão carioca cozido", 76.0, 4.8, 13.6, 0.5, COZ),
        F("Pão integral", 253.0, 9.4, 49.9, 3.7, PR),
        F("Aveia em flocos", 394.0, 13.9, 66.6, 8.5, CRU),
        // Frutas
        F("Banana", 98.0, 1.3, 26.0, 0.1, CRU, FR),
        F("Maçã", 56.0, 0.3, 15.2, 0.0, CRU, FR),
        F("Mamão", 40.0, 0.5, 10.4, 0.1, CRU, FR),
        F("Laranja", 37.0, 1.0, 8.9, 0.1, CRU, FR),
        F("Morango", 30.0, 0.9, 6.8, 0.3, CRU, FR),
        F("Abacaxi", 48.0, 0.9, 12.3, 0.1, CRU, FR),
        // Vegetais, gorduras e outros
        F("Salada de folhas", 15.0, 1.2, 2.5, 0.2, CRU),
        F("Legumes cozidos (mix)", 30.0, 1.5, 6.0, 0.3, COZ),
        F("Azeite de oliva", 884.0, 0.0, 0.0, 100.0, PR),
        F("Castanhas (mix)", 600.0, 15.0, 20.0, 52.0, PR),
        F("Pasta de amendoim", 588.0, 25.0, 20.0, 50.0, PR),
        F("Café sem açúcar", 2.0, 0.1, 0.3, 0.0, PR),
    )

    private data class M(val name: String, val label: String, val time: String, val optional: Boolean, val items: List<Pair<String, Double>>)

    private val meals = listOf(
        M("Refeição 1", "Pré-treino", "08:00", false, listOf("Banana" to 120.0, "Whey protein" to 25.0, "Café sem açúcar" to 200.0)),
        M("Refeição 2", "Pós-treino", "11:30", false, listOf("Ovos inteiros cozidos" to 100.0, "Pão integral" to 60.0, "Aveia em flocos" to 20.0, "Mamão" to 150.0)),
        M("Refeição 3", "Almoço", "14:00", false, listOf(
            "Peito de frango grelhado" to 150.0, "Arroz branco cozido" to 150.0, "Feijão carioca cozido" to 100.0,
            "Salada de folhas" to 150.0, "Legumes cozidos (mix)" to 100.0, "Azeite de oliva" to 5.0)),
        M("Refeição 4", "Lanche", "17:00", false, listOf("Iogurte proteico" to 170.0, "Aveia em flocos" to 30.0, "Castanhas (mix)" to 10.0, "Morango" to 100.0)),
        M("Refeição 5", "Pré-treino noturno", "19:00", false, listOf("Pão integral" to 60.0, "Frango desfiado cozido" to 60.0, "Queijo minas frescal" to 20.0)),
        M("Refeição 6", "Jantar", "21:30", false, listOf("Peixe grelhado" to 150.0, "Batata-doce cozida" to 200.0, "Legumes cozidos (mix)" to 200.0, "Azeite de oliva" to 5.0)),
        M("Ceia", "Opcional", "22:30", true, listOf("Queijo cottage" to 120.0)),
    )

    private val S = ExerciseType.FORCA
    private val R = ExerciseType.CORRIDA
    private val MOB = ExerciseType.MOBILIDADE

    private fun ex(dow: Int, session: Session, sessionName: String, items: List<Triple<String, String, Int?>>, type: ExerciseType = S, optional: Boolean = false) =
        items.mapIndexed { i, (n, presc, sets) ->
            ExerciseTemplateEntity(dayOfWeek = dow, session = session, sessionName = sessionName, name = n, type = type,
                prescription = presc, targetSets = sets, optional = optional, position = i)
        }

    private val training: List<ExerciseTemplateEntity> =
        ex(1, Session.MANHA, "Superiores A", listOf(
            Triple("Supino reto", "4 × 8–10", 4), Triple("Supino inclinado", "3 × 10", 3), Triple("Crossover", "3 × 12", 3),
            Triple("Desenvolvimento", "3 × 10", 3), Triple("Elevação lateral", "3 × 15", 3), Triple("Tríceps corda", "3 × 12", 3))) +
        ex(1, Session.NOITE, "Corrida leve", listOf(Triple("Corrida leve", "35–45 min · Zona 2 · ritmo confortável", null)), R) +
        ex(2, Session.MANHA, "Inferiores A", listOf(
            Triple("Agachamento", "4 × 8–10", 4), Triple("Leg press", "3 × 12", 3), Triple("Cadeira extensora", "3 × 12", 3),
            Triple("Afundo", "3 × 10 por perna", 3), Triple("Panturrilha", "4 × 15", 4), Triple("Prancha", "3 × 40 s", 3))) +
        ex(3, Session.MANHA, "Corrida intervalada", listOf(
            Triple("Corrida intervalada", "Aquecimento 10 min · 6 × 2 min fortes / 2 min trote · desaquecimento 10 min", null)), R) +
        ex(3, Session.NOITE, "Superiores B", listOf(
            Triple("Puxada frontal", "4 × 10", 4), Triple("Remada baixa", "3 × 10", 3), Triple("Remada unilateral", "3 × 12", 3),
            Triple("Pulldown", "3 × 12", 3), Triple("Rosca direta", "3 × 12", 3), Triple("Rosca martelo", "3 × 12", 3),
            Triple("Face pull", "3 × 15", 3))) +
        ex(4, Session.DIA, "Recuperação", listOf(
            Triple("Mobilidade", "15–20 min", null), Triple("Alongamento leve", "10 min", null)), MOB) +
        ex(4, Session.DIA, "Recuperação", listOf(Triple("Caminhada", "Opcional · leve", null)), MOB, optional = true) +
        ex(5, Session.MANHA, "Inferiores B", listOf(
            Triple("Terra romeno", "3 × 10", 3), Triple("Mesa flexora", "3 × 12", 3), Triple("Elevação pélvica", "3 × 12", 3),
            Triple("Cadeira flexora unilateral", "3 × 12", 3), Triple("Panturrilha sentada", "3 × 15", 3), Triple("Abdominal infra", "3 × 15", 3))) +
        ex(6, Session.DIA, "Descanso e mobilidade", listOf(Triple("Mobilidade", "15–20 min", null)), MOB) +
        ex(7, Session.MANHA, "Corrida longa", listOf(
            Triple("Corrida longa", "Contínua · Zona 2 · 7 km → progredir até 9–10 km conforme recuperação", null)), R) +
        ex(7, Session.NOITE, "Superiores e core (opcional)", listOf(
            Triple("Elevação lateral", "3 × 15", 3), Triple("Crucifixo inverso", "3 × 15", 3), Triple("Rosca alternada", "3 × 12", 3),
            Triple("Tríceps pulley", "3 × 12", 3), Triple("Abdominal na polia", "3 × 15", 3), Triple("Prancha lateral", "3 × 30 s", 3)),
            optional = true)

    suspend fun seedIfEmpty(db: AppDatabase, today: LocalDate, targetWeight: Double) {
        if (db.foodDao().count() > 0) return
        db.withTransaction {
            val ids = db.foodDao().insertAll(foods.map {
                FoodEntity(name = it.name, kcal100 = it.kcal, protein100 = it.p, carbs100 = it.c, fat100 = it.g,
                    state = it.state, substitutionGroup = it.group)
            })
            val byName = foods.map { it.name }.zip(ids).toMap()

            meals.forEachIndexed { index, m ->
                val mealId = db.mealDao().insertTemplate(
                    MealTemplateEntity(name = m.name, label = m.label, time = m.time, position = index, optional = m.optional)
                )
                db.mealDao().insertTemplateItems(m.items.map { (food, g) ->
                    MealTemplateItemEntity(mealId = mealId, foodId = byName.getValue(food), grams = g)
                })
            }

            db.trainingDao().insertTemplates(training)

            db.supplementDao().insertAll(listOf(
                SupplementEntity(name = "Creatina", dose = "3 a 5 g", notes = "Diariamente, inclusive nos dias de descanso."),
                SupplementEntity(name = "Whey protein", dose = "25 a 30 g por dose", notes = "Para complementar a meta diária de proteína."),
            ))

            // Avaliação inicial — MEDIDA por bioimpedância em 06/08/2026.
            db.bodyDao().upsert(BodyMeasurementEntity(
                date = "2026-08-06", source = MeasureSource.BIOIMPEDANCIA, weightKg = 82.5, fatPct = 19.7,
                fatMassKg = 16.3, muscleKg = 38.5, visceral = 6.0, waistHipRatio = 0.93,
                notes = "Avaliação inicial",
            ))

            db.projectDao().insert(ProjectEntity(startDate = today.toString(), targetWeightKg = targetWeight))
        }
    }
}
