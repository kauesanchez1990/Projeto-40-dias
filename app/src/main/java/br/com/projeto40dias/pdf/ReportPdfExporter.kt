package br.com.projeto40dias.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import br.com.projeto40dias.domain.WeekReport
import br.com.projeto40dias.ui.components.fmt
import br.com.projeto40dias.ui.components.fmtDate
import java.io.File
import java.io.FileOutputStream

/** Gera o relatório em PDF (A4) com o PdfDocument nativo do Android — sem bibliotecas externas. */
object ReportPdfExporter {

    data class Header(
        val name: String,
        val period: String,
        val initialWeight: Double,
        val currentWeight: Double?,
        val targetWeight: Double,
        val dayText: String,
    )

    fun export(context: Context, header: Header, weeks: List<WeekReport>, final: WeekReport): File {
        val doc = PdfDocument()
        val w = 595; val h = 842; val margin = 40f
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var c = page.canvas
        var y = margin

        val title = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val h2 = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(40, 90, 0); isAntiAlias = true }
        val body = Paint().apply { textSize = 11f; color = Color.DKGRAY; isAntiAlias = true }
        val small = Paint().apply { textSize = 9f; color = Color.GRAY; isAntiAlias = true }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        fun ensure(space: Float) {
            if (y + space > h - margin) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
                c = page.canvas
                y = margin
            }
        }
        fun text(s: String, p: Paint = body, dy: Float = 16f) { ensure(dy); c.drawText(s, margin, y, p); y += dy }

        text("PROJETO 40 DIAS — Relatório", title, 30f)
        text("${header.name} · ${header.period}", body)
        text(header.dayText, body)
        y += 6f
        text("Peso inicial: ${fmt(header.initialWeight)} kg   ·   Peso atual (medido): ${header.currentWeight?.let { fmt(it) + " kg" } ?: "sem registro"}   ·   Meta: ${fmt(header.targetWeight)} kg", body)
        y += 8f

        fun block(r: WeekReport) {
            ensure(150f)
            c.drawLine(margin, y - 10f, w - margin, y - 10f, line)
            text(r.title, h2, 20f)
            text("Período: ${fmtDate(r.from)} a ${fmtDate(r.to)}")
            text("Peso início: ${r.weightStart?.let { fmt(it) + " kg" } ?: "—"}   Peso fim: ${r.weightEnd?.let { fmt(it) + " kg" } ?: "—"}   Variação: ${r.weightDelta?.let { (if (it > 0) "+" else "") + fmt(it) + " kg" } ?: "—"}")
            text("Média de calorias: ${r.avgKcal?.let { "%.0f kcal".format(it) } ?: "—"}   Média de proteínas: ${r.avgProtein?.let { "%.0f g".format(it) } ?: "—"}")
            text("Treinos programados: ${r.sessionsScheduled}   Treinos realizados: ${r.sessionsDone}")
            text("Adesão à dieta: ${r.dietAdherencePct?.let { "$it%" } ?: "—"}   Adesão aos exercícios: ${r.exerciseAdherencePct?.let { "$it%" } ?: "—"}")
            text("Circunferência abdominal: ${r.waistCm?.let { fmt(it) + " cm" } ?: "—"}")
            y += 10f
        }

        block(final.copy(title = "RESUMO DOS 40 DIAS"))
        weeks.forEach { block(it) }

        y += 10f
        text("Avisos: metas calóricas são estimativas. Resultados de bioimpedância variam conforme hidratação e condições", small, 12f)
        text("de medição. Pesos e medidas mostrados são apenas os registrados pelo usuário. Este app não substitui", small, 12f)
        text("nutricionista, médico ou profissional de educação física.", small, 12f)

        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "relatorio_projeto40dias.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Exportar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
