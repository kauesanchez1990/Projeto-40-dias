package br.com.projeto40dias.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.projeto40dias.MainActivity
import br.com.projeto40dias.P40App
import br.com.projeto40dias.R
import br.com.projeto40dias.data.prefs.Reminder
import br.com.projeto40dias.data.prefs.ReminderKey
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

object Notifier {
    const val CHANNEL = "lembretes"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Refeições, suplementos, hidratação e pesagem"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) { /* permissão revogada */ }
    }
}

/** Worker disparado pelo WorkManager para cada lembrete. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val key = runCatching { ReminderKey.valueOf(inputData.getString(KEY) ?: "") }.getOrNull() ?: return Result.success()
        val app = applicationContext as P40App
        val reminder = app.container.settings.currentReminders().firstOrNull { it.key == key }
        val isTest = inputData.getBoolean(TEST, false)
        if (!isTest && reminder?.enabled != true) return Result.success()

        val now = LocalTime.now()
        val (title, text) = when (key) {
            ReminderKey.HIDRATACAO -> {
                // Lembrete de água só entre 8h e 22h, e só se a meta ainda não foi batida.
                if (!isTest && (now.hour < 8 || now.hour >= 22)) return Result.success()
                val total = app.container.tracking.waterTotalNow(LocalDate.now())
                val goal = app.container.settings.currentGoals().waterMl
                if (!isTest && total >= goal) return Result.success()
                "Hora de beber água 💧" to "Você está em ${total} ml de ${goal} ml hoje."
            }
            ReminderKey.CREATINA -> "Creatina" to "Tome sua dose de 3 a 5 g — inclusive em dia de descanso."
            ReminderKey.PESAGEM -> "Pesagem semanal" to "Pese-se em jejum, após ir ao banheiro, e registre em MINHA EVOLUÇÃO."
            else -> key.label to "Hora da refeição: ${key.label.lowercase()}. Marque como concluída na aba DIETA."
        }
        Notifier.show(applicationContext, 1000 + key.ordinal, title, text)
        return Result.success()
    }

    companion object {
        const val KEY = "key"
        const val TEST = "test"
    }
}

/** Agenda/cancela lembretes periódicos no WorkManager. */
object ReminderScheduler {
    private fun name(k: ReminderKey) = "reminder_${k.name}"

    fun applyAll(context: Context, reminders: List<Reminder>) = reminders.forEach { apply(context, it) }

    fun apply(context: Context, r: Reminder) {
        val wm = WorkManager.getInstance(context)
        if (!r.enabled) {
            wm.cancelUniqueWork(name(r.key)); return
        }
        val data = Data.Builder().putString(ReminderWorker.KEY, r.key.name).build()
        val request = when (r.key) {
            ReminderKey.HIDRATACAO ->
                PeriodicWorkRequestBuilder<ReminderWorker>(2, TimeUnit.HOURS)
                    .setInitialDelay(delayUntil(r.time), TimeUnit.MILLISECONDS)
            ReminderKey.PESAGEM ->
                PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(delayUntilWeekly(r.time, DayOfWeek.MONDAY), TimeUnit.MILLISECONDS)
            else ->
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntil(r.time), TimeUnit.MILLISECONDS)
        }.setInputData(data).addTag("reminder").build()
        // UPDATE substitui o agendamento quando o horário é alterado.
        wm.enqueueUniquePeriodicWork(name(r.key), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Dispara uma notificação de teste em ~5 segundos. */
    fun test(context: Context) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(ReminderWorker.KEY, ReminderKey.HIDRATACAO.name).putBoolean(ReminderWorker.TEST, true).build())
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    fun parseTime(t: String): LocalTime = runCatching { LocalTime.parse(t) }.getOrDefault(LocalTime.of(8, 0))

    fun delayUntil(time: String, now: LocalDateTime = LocalDateTime.now()): Long {
        var target = now.toLocalDate().atTime(parseTime(time))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }

    fun delayUntilWeekly(time: String, day: DayOfWeek, now: LocalDateTime = LocalDateTime.now()): Long {
        var target = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(parseTime(time))
        if (!target.isAfter(now)) target = target.plusWeeks(1)
        return Duration.between(now, target).toMillis()
    }
}
