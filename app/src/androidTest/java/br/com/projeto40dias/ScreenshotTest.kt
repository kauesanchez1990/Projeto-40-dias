package br.com.projeto40dias

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import br.com.projeto40dias.notifications.ReminderReceiver
import br.com.projeto40dias.notifications.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Abre o app real, navega pelas telas novas e salva capturas em files/screenshots.
 * Rodado pelo workflow "Capturas de tela" (não faz parte do build normal).
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val dir = File(instr.targetContext.filesDir, "screenshots").apply { deleteRecursively(); mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(900)
        val bmp: Bitmap = instr.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(text: String, timeout: Long = 15_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun SemanticsNodeInteractionCollection.click() = onFirst().performClick()

    private fun text(t: String) = rule.onAllNodesWithText(t).click()
    private fun desc(d: String) = rule.onAllNodesWithContentDescription(d).click()

    /** Aba da barra inferior (o ícone não tem descrição quando o rótulo aparece). */
    private fun tab(label: String) =
        rule.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).click()
    private fun scrollTo(t: String) = rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(t))

    /** Executa uma etapa; se falhar, registra o erro e segue para as próximas capturas. */
    private fun step(name: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            File(dir, "erros.txt").appendText("$name: ${t.javaClass.simpleName}: ${t.message?.take(300)}\n")
            runCatching { text("Cancelar") }
        }
    }

    @Test
    fun telasNovas() {
        // Tela de abertura (relógio do Compose pausado para pegar a animação no meio)
        step("abertura") {
            rule.mainClock.autoAdvance = false
            rule.mainClock.advanceTimeBy(1_200)
            Thread.sleep(1_000)
            instr.uiAutomation.takeScreenshot()?.let { bmp ->
                FileOutputStream(File(dir, "00_abertura.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        rule.mainClock.autoAdvance = true

        // Aviso inicial
        runCatching { waitText("Entendi", 8_000); text("Entendi") }
        rule.waitForIdle()
        step("inicio") { shot("00_inicio_aviso") }

        // ---------------- Dieta
        step("dieta") {
            tab("DIETA"); waitText("MINHA DIETA")
            shot("01_dieta_com_botao_editar")
            desc("Editar dieta"); waitText("Pré-treino")
            shot("02_editar_dieta")
            scrollTo("Jantar")
            shot("03_editar_dieta_rolada")
            scrollTo("Almoço"); text("Almoço")
            waitText("Editar refeição")
            shot("04_editar_refeicao")
            text("Cancelar")
        }

        // ---------------- Treinos
        step("treino") {
            tab("TREINOS"); waitText("MEUS TREINOS")
            shot("05_treinos_com_horario")
            desc("Editar treino"); waitText("Editar treino")
            text("SEG"); waitText("Supino reto")
            shot("06_editar_treino_segunda")
            text("Supino reto"); waitText("Editar exercício")
            shot("07_editar_exercicio")
            text("Cancelar")
            scrollTo("Nova sessão na segunda"); text("Nova sessão na segunda"); waitText("Nova sessão")
            shot("08_nova_sessao")
            text("Cancelar")
        }

        // ---------------- Lembretes
        step("lembretes") {
            tab("PERFIL")
            scrollTo("Lembretes e notificações"); text("Lembretes e notificações")
            waitText("Refeições")
            shot("09_lembretes")
            scrollTo("Treinos")
            shot("10_lembretes_agua_treinos")
            scrollTo("Enviar notificação de teste")
            shot("11_lembretes_outros")
        }

        // ---------------- Notificações reais (refeição e água), disparadas pelo próprio app
        step("notificacoes") {
            val c = (instr.targetContext.applicationContext as P40App).container
            val lunch = runBlocking { c.db.mealDao().templates().first { it.label == "Almoço" } }
            fun fire(kind: String, code: Int, id: Long = 0) = instr.targetContext.sendBroadcast(
                Intent(instr.targetContext, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE)
                    .putExtra(ReminderReceiver.EXTRA_KIND, kind).putExtra(ReminderReceiver.EXTRA_CODE, code)
                    .putExtra(ReminderReceiver.EXTRA_ID, id),
            )
            fire(ReminderScheduler.KIND_MEAL, 100_000 + lunch.id.toInt(), lunch.id)
            Thread.sleep(1500)
            fire(ReminderScheduler.KIND_WATER, ReminderScheduler.WATER_CODE)
            Thread.sleep(2500)
            instr.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
            Thread.sleep(2500)
            shot("12_notificacoes")
            instr.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        }
    }
}
