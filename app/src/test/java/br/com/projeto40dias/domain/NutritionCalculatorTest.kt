package br.com.projeto40dias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionCalculatorTest {
    private val frango = Per100(159.0, 32.0, 0.0, 2.5)
    private val tilapia = Per100(128.0, 26.2, 0.0, 2.7)
    private val arroz = Per100(128.0, 2.5, 28.1, 0.2)
    private val batataDoce = Per100(77.0, 0.6, 18.4, 0.1)

    @Test fun `porcao calcula por 100 g`() {
        val m = NutritionCalculator.portion(frango, 150.0)
        assertEquals(238.5, m.kcal, 0.01)
        assertEquals(48.0, m.protein, 0.01)
        assertEquals(3.75, m.fat, 0.01)
    }

    @Test fun `gramas negativas viram zero`() {
        assertEquals(0.0, NutritionCalculator.portion(frango, -10.0).kcal, 0.0)
    }

    @Test fun `soma e quanto falta`() {
        val total = NutritionCalculator.sum(listOf(NutritionCalculator.portion(frango, 150.0), NutritionCalculator.portion(arroz, 150.0)))
        assertEquals(238.5 + 192.0, total.kcal, 0.01)
        val goal = Macros(2400.0, 175.0, 270.0, 70.0)
        val left = NutritionCalculator.remaining(goal, total)
        assertEquals(2400.0 - 430.5, left.kcal, 0.01)
        // nunca negativo
        assertEquals(0.0, NutritionCalculator.remaining(Macros(100.0, 1.0, 1.0, 1.0), total).kcal, 0.0)
    }

    @Test fun `substituicao de proteina iguala proteina`() {
        val g = NutritionCalculator.equivalentGrams(frango, 150.0, tilapia, byProtein = true)
        // 48 g de proteína / 26,2 g por 100 g = 183 g → arredonda para 185
        assertEquals(185.0, g, 0.0)
    }

    @Test fun `substituicao de carboidrato iguala carboidrato`() {
        val g = NutritionCalculator.equivalentGrams(arroz, 150.0, batataDoce, byProtein = false)
        // 42,15 g carb / 18,4 = 229 g → 230
        assertEquals(230.0, g, 0.0)
    }

    @Test fun `cardapio padrao fica perto da meta`() {
        // Cardápio do projeto (refeições 1 a 6) com os valores do banco de alimentos.
        val p = mapOf(
            "banana" to Per100(98.0, 1.3, 26.0, 0.1), "whey" to Per100(400.0, 80.0, 8.0, 6.0), "cafe" to Per100(2.0, 0.1, 0.3, 0.0),
            "ovo" to Per100(146.0, 13.3, 0.6, 9.5), "pao" to Per100(253.0, 9.4, 49.9, 3.7), "aveia" to Per100(394.0, 13.9, 66.6, 8.5),
            "mamao" to Per100(40.0, 0.5, 10.4, 0.1), "frango" to frango, "arroz" to arroz, "feijao" to Per100(76.0, 4.8, 13.6, 0.5),
            "salada" to Per100(15.0, 1.2, 2.5, 0.2), "legumes" to Per100(30.0, 1.5, 6.0, 0.3), "azeite" to Per100(884.0, 0.0, 0.0, 100.0),
            "iogurte" to Per100(65.0, 9.0, 5.5, 0.5), "castanhas" to Per100(600.0, 15.0, 20.0, 52.0), "morango" to Per100(30.0, 0.9, 6.8, 0.3),
            "frangoDesf" to Per100(163.0, 31.5, 0.0, 3.2), "queijo" to Per100(264.0, 17.4, 3.2, 20.2), "peixe" to tilapia, "batataDoce" to batataDoce,
        )
        val menu = listOf(
            "banana" to 120.0, "whey" to 25.0, "cafe" to 200.0,
            "ovo" to 100.0, "pao" to 60.0, "aveia" to 20.0, "mamao" to 150.0,
            "frango" to 150.0, "arroz" to 150.0, "feijao" to 100.0, "salada" to 150.0, "legumes" to 100.0, "azeite" to 5.0,
            "iogurte" to 170.0, "aveia" to 30.0, "castanhas" to 10.0, "morango" to 100.0,
            "pao" to 60.0, "frangoDesf" to 60.0, "queijo" to 20.0,
            "peixe" to 150.0, "batataDoce" to 200.0, "legumes" to 200.0, "azeite" to 5.0,
        )
        val t = NutritionCalculator.sum(menu.map { (k, g) -> NutritionCalculator.portion(p.getValue(k), g) })
        println("Cardápio: %.0f kcal · P %.0f · C %.0f · G %.0f".format(t.kcal, t.protein, t.carbs, t.fat))
        assertTrue(t.kcal in 2100.0..2600.0)
        assertTrue(t.protein >= 160.0)
    }

    @Test fun `meta minima segura`() {
        assertEquals(1800, NutritionCalculator.safeMinimumKcal(1800))
        assertEquals(1500, NutritionCalculator.safeMinimumKcal(1200))
    }
}
