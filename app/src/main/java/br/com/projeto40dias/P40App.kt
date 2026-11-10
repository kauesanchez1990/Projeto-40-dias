package br.com.projeto40dias

import android.app.Application
import androidx.work.WorkManager
import br.com.projeto40dias.data.db.AppDatabase
import br.com.projeto40dias.data.db.SeedData
import br.com.projeto40dias.data.prefs.SettingsRepository
import br.com.projeto40dias.data.repo.BodyRepository
import br.com.projeto40dias.data.repo.NutritionRepository
import br.com.projeto40dias.data.repo.ProjectRepository
import br.com.projeto40dias.data.repo.ReportRepository
import br.com.projeto40dias.data.repo.TrackingRepository
import br.com.projeto40dias.data.repo.TrainingRepository
import br.com.projeto40dias.notifications.Notifier
import br.com.projeto40dias.notifications.ReminderScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Contêiner simples de dependências. Para sincronização futura em nuvem basta adicionar
 * uma fonte remota aos repositórios — a interface (ViewModels/telas) não muda.
 */
class AppContainer(app: Application) {
    val db = AppDatabase.build(app)
    val settings = SettingsRepository(app)

    /** Concluído quando os dados iniciais foram gravados (evita criar o dia antes do cardápio existir). */
    val seeded = CompletableDeferred<Unit>()
    val nutrition = NutritionRepository(db) { seeded.await() }
    val training = TrainingRepository(db)
    val tracking = TrackingRepository(db)
    val body = BodyRepository(db)
    val project = ProjectRepository(db)
    val reports = ReportRepository(db)

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today
    fun refreshToday() { _today.value = LocalDate.now() }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class P40App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        container.scope.launch {
            try {
                SeedData.seedIfEmpty(container.db, LocalDate.now(), container.settings.currentGoals().targetWeightKg)
            } finally {
                container.seeded.complete(Unit)
            }
            // Lembretes da versão anterior (WorkManager) foram substituídos por alarmes exatos.
            runCatching { WorkManager.getInstance(this@P40App).cancelAllWorkByTag("reminder") }
        }
        // Reagenda sempre que o cardápio, o treino ou as opções de lembrete mudarem.
        container.scope.launch {
            container.seeded.await()
            combine(
                container.db.mealDao().observeTemplates(),
                container.db.trainingDao().observeTemplates(),
                container.settings.reminders,
            ) { _, _, _ -> Unit }.collectLatest {
                delay(500)
                runCatching { ReminderScheduler.scheduleAll(this@P40App) }
            }
        }
    }
}
