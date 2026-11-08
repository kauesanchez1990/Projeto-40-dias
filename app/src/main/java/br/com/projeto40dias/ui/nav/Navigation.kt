package br.com.projeto40dias.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.P40App
import br.com.projeto40dias.ui.screens.DietScreen
import br.com.projeto40dias.ui.screens.EvolutionScreen
import br.com.projeto40dias.ui.screens.ExerciseHistoryScreen
import br.com.projeto40dias.ui.screens.FoodsScreen
import br.com.projeto40dias.ui.screens.HomeScreen
import br.com.projeto40dias.ui.screens.ProfileScreen
import br.com.projeto40dias.ui.screens.ProgressScreen
import br.com.projeto40dias.ui.screens.RemindersScreen
import br.com.projeto40dias.ui.screens.ReportsScreen
import br.com.projeto40dias.ui.screens.SubstitutionsScreen
import br.com.projeto40dias.ui.screens.SupplementsScreen
import br.com.projeto40dias.ui.screens.TrainingScreen
import br.com.projeto40dias.ui.screens.WaterScreen
import br.com.projeto40dias.ui.theme.P40Colors
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"; const val DIET = "diet"; const val TRAINING = "training"
    const val EVOLUTION = "evolution"; const val PROFILE = "profile"
    const val WATER = "water"; const val SUPPLEMENTS = "supplements"; const val PROGRESS = "progress"
    const val REPORTS = "reports"; const val REMINDERS = "reminders"; const val FOODS = "foods"
    const val SUBSTITUTIONS = "substitutions"; const val HISTORY = "history/{id}"
    fun history(id: Long) = "history/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Suppress("DEPRECATION")
private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Filled.Home),
    Tab(Routes.DIET, "Dieta", Icons.Filled.Restaurant),
    Tab(Routes.TRAINING, "Treinos", Icons.Filled.DirectionsRun),
    Tab(Routes.EVOLUTION, "Evolução", Icons.Filled.ShowChart),
    Tab(Routes.PROFILE, "Perfil", Icons.Filled.Person),
)

/** Cria um ViewModel com acesso ao contêiner de dependências. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as P40App
    return viewModel(factory = viewModelFactory { initializer { create(app.container) } })
}

@Composable
fun P40NavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val app = LocalContext.current.applicationContext as P40App
    val accepted by app.container.settings.disclaimerAccepted.collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = P40Colors.Background,
        bottomBar = {
            NavigationBar(containerColor = P40Colors.Surface) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black, indicatorColor = P40Colors.Lime,
                            selectedTextColor = P40Colors.Lime, unselectedIconColor = P40Colors.TextSecondary,
                            unselectedTextColor = P40Colors.TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(inner)) {
            composable(Routes.HOME) { HomeScreen(onNavigate = { nav.navigate(it) }) }
            composable(Routes.DIET) { DietScreen(onNavigate = { nav.navigate(it) }) }
            composable(Routes.TRAINING) { TrainingScreen(onNavigate = { nav.navigate(it) }) }
            composable(Routes.EVOLUTION) { EvolutionScreen() }
            composable(Routes.PROFILE) { ProfileScreen(onNavigate = { nav.navigate(it) }) }
            composable(Routes.WATER) { WaterScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SUPPLEMENTS) { SupplementsScreen(onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }) }
            composable(Routes.PROGRESS) { ProgressScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.REPORTS) { ReportsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.REMINDERS) { RemindersScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.FOODS) { FoodsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SUBSTITUTIONS) { SubstitutionsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.HISTORY, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ExerciseHistoryScreen(templateId = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
        }
    }

    if (!accepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Antes de começar") },
            text = {
                Text(
                    "O PROJETO 40 DIAS é uma ferramenta de acompanhamento pessoal e não substitui nutricionista, " +
                        "médico ou profissional de educação física.\n\n" +
                        "• Metas calóricas são estimativas.\n" +
                        "• Não é possível direcionar a perda de gordura visceral ou abdominal isoladamente.\n" +
                        "• Resultados de bioimpedância variam conforme hidratação e condições de medição.\n" +
                        "• O app não recomenda restrições calóricas extremas nem treinos intensos com sinais de fadiga."
                )
            },
            confirmButton = { TextButton(onClick = { scope.launch { app.container.settings.acceptDisclaimer() } }) { Text("Entendi") } },
        )
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
