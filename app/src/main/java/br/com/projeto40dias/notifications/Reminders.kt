package br.com.projeto40dias.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.projeto40dias.AppContainer
import br.com.projeto40dias.MainActivity
import br.com.projeto40dias.P40App
import br.com.projeto40dias.R
import br.com.projeto40dias.data.db.Session
import br.com.projeto40dias.domain.ReminderPlanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

// =================================================================== Canais e exibição

object Notifier {
    const val CH_MEALS = "refeicoes"
    const val CH_WATER = "agua"
    const val CH_TRAINING = "treinos"
    const val CH_OTHER = "lembretes"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_MEALS, "Refeições", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso no horário de cada refeição do cardápio"
            },
            NotificationChannel(CH_WATER, "Hidratação", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Lembretes para beber água ao longo do dia"
            },
            NotificationChannel(CH_TRAINING, "Treinos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso antes de cada sessão de treino"
            },
            NotificationChannel(CH_OTHER, "Outros lembretes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Creatina e pesagem semanal"
            },
        ).forEach { nm.createNotificationChannel(it) }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(
        context: Context, id: Int, channel: String, title: String, text: String,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        actions.forEach { b.addAction(it) }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (_: SecurityException) { /* permissão revogada */ }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}

// =================================================================== Agendamento

/** Um alarme planejado. [code] identifica o lembrete (e a notificação). */
data class PlannedAlarm(
    val code: Int, val at: LocalDateTime, val kind: String,
    val id: Long = 0, val dow: Int = 0, val session: String = "",
)

/**
 * Agenda cada lembrete com o AlarmManager (horário exato, inclusive com o aparelho em repouso).
 * Os horários das refeições e treinos vêm do cardápio e do treino configurados — ao editar
 * um deles, tudo é reagendado automaticamente. Cada alarme, ao disparar, agenda o próximo.
 */
object ReminderScheduler {
    const val KIND_MEAL = "meal"
    const val KIND_TRAINING = "training"
    const val KIND_WATER = "water"
    const val KIND_CREATINE = "creatine"
    const val KIND_WEIGH = "weigh"

    private const val MEAL_BASE = 100_000
    private const val TRAINING_BASE = 200_000
    const val WATER_CODE = 300_000
    private const val CREATINE_CODE = 300_001
    private const val WEIGH_CODE = 300_002
    const val TEST_CODE = 300_099

    private const val PREFS = "alarms"
    private const val KEY_CODES = "codes"
    private val lock = Mutex()

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** Calcula e agenda o próximo disparo de todos os lembretes ativos. */
    suspend fun scheduleAll(context: Context) = lock.withLock {
        val ctx = context.applicationContext
        val c = (ctx as P40App).container
        c.seeded.await()
        val plan = plan(c, LocalDateTime.now())

        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        val codes = plan.map { it.code }.toSet()
        (old - codes).forEach { am.cancel(pending(ctx, it, null)) }
        plan.forEach { set(ctx, am, it) }
        prefs.edit().putStringSet(KEY_CODES, codes.map { it.toString() }.toSet()).apply()
    }

    private suspend fun plan(c: AppContainer, now: LocalDateTime): List<PlannedAlarm> {
        val s = c.settings.currentReminders()
        val out = mutableListOf<PlannedAlarm>()
        if (s.meals) {
            c.db.mealDao().templates().forEach { m ->
                out += PlannedAlarm(MEAL_BASE + m.id.toInt(),
                    ReminderPlanner.nextDaily(now, ReminderPlanner.parseTime(m.time), s.mealsLeadMin), KIND_MEAL, id = m.id)
            }
        }
        if (s.training) {
            c.db.trainingDao().activeTemplates().groupBy { it.dayOfWeek to it.session }.forEach { (k, list) ->
                val (dow, session) = k
                val time = ReminderPlanner.parseTime(list.first().sessionTime)
                out += PlannedAlarm(TRAINING_BASE + dow * 10 + session.ordinal,
                    ReminderPlanner.nextWeekly(now, DayOfWeek.of(dow), time, s.trainingLeadMin), KIND_TRAINING,
                    dow = dow, session = session.name)
            }
        }
        if (s.water) {
            ReminderPlanner.nextWater(now, ReminderPlanner.parseTime(s.waterStart), ReminderPlanner.parseTime(s.waterEnd), s.waterIntervalMin)
                ?.let { out += PlannedAlarm(WATER_CODE, it, KIND_WATER) }
        }
        if (s.creatine) out += PlannedAlarm(CREATINE_CODE, ReminderPlanner.nextDaily(now, ReminderPlanner.parseTime(s.creatineTime)), KIND_CREATINE)
        if (s.weighIn) out += PlannedAlarm(WEIGH_CODE,
            ReminderPlanner.nextWeekly(now, DayOfWeek.MONDAY, ReminderPlanner.parseTime(s.weighInTime)), KIND_WEIGH)
        return out
    }

    private fun set(ctx: Context, am: AlarmManager, a: PlannedAlarm) {
        val trigger = a.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pending(ctx, a.code, a)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun pending(ctx: Context, code: Int, a: PlannedAlarm?): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE)
        if (a != null) {
            i.putExtra(ReminderReceiver.EXTRA_KIND, a.kind).putExtra(ReminderReceiver.EXTRA_CODE, a.code)
                .putExtra(ReminderReceiver.EXTRA_ID, a.id).putExtra(ReminderReceiver.EXTRA_DOW, a.dow)
                .putExtra(ReminderReceiver.EXTRA_SESSION, a.session)
        }
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Mostra uma notificação de teste imediatamente. */
    fun test(context: Context) = Notifier.show(context, TEST_CODE, Notifier.CH_MEALS,
        "Notificações funcionando ✅", "Você vai receber os avisos de refeição, água e treino nos horários configurados.")
}

// =================================================================== Receptores

/** Recebe os alarmes e as ações das notificações ("Marcar como feita", "+250 ml"). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as P40App
        val result = goAsync()
        app.container.scope.launch {
            try {
                handle(app, intent)
            } catch (_: Throwable) {
                // Um erro num lembrete não pode impedir o agendamento dos próximos.
            } finally {
                if (intent.action == ACTION_FIRE) runCatching { ReminderScheduler.scheduleAll(app) }
                result.finish()
            }
        }
    }

    private suspend fun handle(app: P40App, intent: Intent) {
        val c = app.container
        c.seeded.await()
        val today = LocalDate.now()
        when (intent.action) {
            ACTION_MEAL_DONE -> {
                val id = intent.getLongExtra(EXTRA_ID, 0)
                val date = intent.getStringExtra(EXTRA_DATE)?.let { LocalDate.parse(it) } ?: today
                c.nutrition.ensureDay(date)
                c.nutrition.setMealDone(date, id, true)
                Notifier.cancel(app, intent.getIntExtra(EXTRA_CODE, 0))
            }
            ACTION_WATER_ADD -> {
                c.tracking.addWater(today, intent.getIntExtra(EXTRA_ML, 250))
                Notifier.cancel(app, ReminderScheduler.WATER_CODE)
            }
            ACTION_FIRE -> fire(app, c, intent, today)
        }
    }

    private suspend fun fire(app: P40App, c: AppContainer, intent: Intent, today: LocalDate) {
        val s = c.settings.currentReminders()
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        when (intent.getStringExtra(EXTRA_KIND)) {
            ReminderScheduler.KIND_MEAL -> {
                if (!s.meals) return
                val id = intent.getLongExtra(EXTRA_ID, 0)
                val t = c.db.mealDao().template(id) ?: return
                if (t.archived) return
                // Com antecedência que cruza a meia-noite, a refeição é a do dia seguinte.
                val date = if (s.mealsLeadMin > 0 && LocalDateTime.now().toLocalTime().isAfter(ReminderPlanner.parseTime(t.time))) today.plusDays(1) else today
                c.nutrition.ensureDay(date)
                val meal = c.nutrition.observeDay(date).first().meals.firstOrNull { it.template.id == id } ?: return
                if (meal.done) return
                val foods = meal.items.joinToString("\n") { "• ${it.name} — ${it.grams.roundToInt()} g" }
                val text = (if (foods.isBlank()) "Confira o cardápio na aba DIETA." else foods) +
                    "\n≈ ${meal.macros.kcal.roundToInt()} kcal · P ${meal.macros.protein.roundToInt()} g"
                val done = action(app, code + 1, "Marcar como feita", Intent(app, ReminderReceiver::class.java)
                    .setAction(ACTION_MEAL_DONE).putExtra(EXTRA_ID, id).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATE, date.toString()))
                Notifier.show(app, code, Notifier.CH_MEALS, "🍽️ ${t.label} · ${t.time}", text, listOf(done))
            }
            ReminderScheduler.KIND_TRAINING -> {
                if (!s.training) return
                val dow = intent.getIntExtra(EXTRA_DOW, 0)
                val session = runCatching { Session.valueOf(intent.getStringExtra(EXTRA_SESSION) ?: "") }.getOrNull() ?: return
                val list = c.db.trainingDao().sessionTemplates(dow, session)
                if (list.isEmpty()) return
                val date = if (today.dayOfWeek.value == dow) today else today.plusDays(1)
                val done = list.filter { !it.optional }.all { c.db.trainingDao().log(date.toString(), it.id)?.done == true }
                if (done && list.any { !it.optional }) return
                val first = list.first()
                val title = "🏋️ ${first.sessionName} · ${first.sessionTime}" + if (list.all { it.optional }) " (opcional)" else ""
                val text = "${session.label}: " + list.joinToString(", ") { it.name }
                Notifier.show(app, code, Notifier.CH_TRAINING, title, text)
            }
            ReminderScheduler.KIND_WATER -> {
                if (!s.water) return
                val total = c.tracking.waterTotalNow(today)
                val goal = c.settings.currentGoals().waterMl
                if (total >= goal) return
                val a250 = action(app, code + 1, "+250 ml", Intent(app, ReminderReceiver::class.java).setAction(ACTION_WATER_ADD).putExtra(EXTRA_ML, 250))
                val a500 = action(app, code + 2, "+500 ml", Intent(app, ReminderReceiver::class.java).setAction(ACTION_WATER_ADD).putExtra(EXTRA_ML, 500))
                Notifier.show(app, code, Notifier.CH_WATER, "💧 Hora de beber água",
                    "Você está em $total ml de $goal ml hoje. Faltam ${goal - total} ml.", listOf(a250, a500))
            }
            ReminderScheduler.KIND_CREATINE -> if (s.creatine)
                Notifier.show(app, code, Notifier.CH_OTHER, "Creatina", "Tome sua dose de 3 a 5 g — inclusive em dia de descanso.")
            ReminderScheduler.KIND_WEIGH -> if (s.weighIn)
                Notifier.show(app, code, Notifier.CH_OTHER, "Pesagem semanal", "Pese-se em jejum, após ir ao banheiro, e registre em MINHA EVOLUÇÃO.")
        }
    }

    private fun action(ctx: Context, requestCode: Int, label: String, intent: Intent): NotificationCompat.Action {
        val pi = PendingIntent.getBroadcast(ctx, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    companion object {
        const val ACTION_FIRE = "br.com.projeto40dias.REMINDER_FIRE"
        const val ACTION_MEAL_DONE = "br.com.projeto40dias.MEAL_DONE"
        const val ACTION_WATER_ADD = "br.com.projeto40dias.WATER_ADD"
        const val EXTRA_KIND = "kind"
        const val EXTRA_CODE = "code"
        const val EXTRA_ID = "id"
        const val EXTRA_DOW = "dow"
        const val EXTRA_SESSION = "session"
        const val EXTRA_DATE = "date"
        const val EXTRA_ML = "ml"
    }
}

/** Reagenda os lembretes após reiniciar o aparelho, atualizar o app ou mudar a hora. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as P40App
        val result = goAsync()
        app.container.scope.launch {
            try { ReminderScheduler.scheduleAll(app) } catch (_: Throwable) { } finally { result.finish() }
        }
    }
}
