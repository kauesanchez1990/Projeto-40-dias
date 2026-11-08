package br.com.projeto40dias.domain

import kotlin.math.max

/** Totais de energia e macronutrientes. */
data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
    operator fun minus(o: Macros) = Macros(kcal - o.kcal, protein - o.protein, carbs - o.carbs, fat - o.fat)
    companion object { val ZERO = Macros() }
}

/** Valores nutricionais por 100 g. */
data class Per100(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

object NutritionCalculator {

    /** Macros de uma porção: valor por 100 g × gramas / 100. */
    fun portion(per100: Per100, grams: Double): Macros {
        val f = max(grams, 0.0) / 100.0
        return Macros(per100.kcal * f, per100.protein * f, per100.carbs * f, per100.fat * f)
    }

    fun sum(items: List<Macros>): Macros = items.fold(Macros.ZERO) { acc, m -> acc + m }

    /** Quanto ainda falta para a meta (nunca negativo). */
    fun remaining(goal: Macros, consumed: Macros) = Macros(
        max(goal.kcal - consumed.kcal, 0.0),
        max(goal.protein - consumed.protein, 0.0),
        max(goal.carbs - consumed.carbs, 0.0),
        max(goal.fat - consumed.fat, 0.0),
    )

    /**
     * Gramas de um substituto que entregam o mesmo valor do nutriente-chave do original.
     * Proteínas → iguala proteína; carboidratos e frutas → iguala carboidrato.
     */
    fun equivalentGrams(original: Per100, originalGrams: Double, substitute: Per100, byProtein: Boolean): Double {
        val target = if (byProtein) original.protein * originalGrams / 100 else original.carbs * originalGrams / 100
        val per100 = if (byProtein) substitute.protein else substitute.carbs
        if (per100 <= 0.0) return originalGrams
        return roundTo5(target / per100 * 100)
    }

    fun roundTo5(g: Double): Double = (Math.round(g / 5.0) * 5).toDouble().coerceAtLeast(5.0)

    /** kcal derivadas dos macros (4/4/9) — usado para checar consistência das metas. */
    fun kcalFromMacros(protein: Double, carbs: Double, fat: Double) = protein * 4 + carbs * 4 + fat * 9

    /** Menor meta calórica que o app aceita sem alertar — evita restrição extrema. */
    fun safeMinimumKcal(bmrKcal: Int): Int = max(bmrKcal, 1500)
}
