package bd.robin.hisabkhata.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.formatTaka
import java.io.File
import java.io.FileOutputStream

data class StatementLabels(
    val title: String,
    val date: String,
    val note: String,
    val credit: String,
    val payment: String,
    val balance: String,
    val total: String,
    val generated: String,
    val appName: String,
)

data class StatementRow(
    val date: String,
    val note: String,
    val credit: String,
    val payment: String,
    val balance: String,
)

object PdfStatement {
    fun write(
        context: Context,
        file: File,
        shop: String,
        customer: String,
        phone: String,
        labels: StatementLabels,
        rows: List<StatementRow>,
        closing: String,
    ) {
        val typeface = ResourcesCompat.getFont(context, R.font.noto_sans_bengali_regular)
        val bold = ResourcesCompat.getFont(context, R.font.noto_sans_bengali_bold) ?: typeface
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val titlePaint = paint(bold, 18f, Color.WHITE)
        val headPaint = paint(bold, 12f, Color.DKGRAY)
        val body = paint(typeface, 11f, Color.BLACK)
        val muted = paint(typeface, 10f, Color.DKGRAY)
        var pageNumber = 1
        var page = document.startPage(info(pageNumber, pageWidth, pageHeight))
        var canvas = page.canvas
        var y = drawHeader(canvas, pageWidth, shop, customer, phone, labels, titlePaint, body, muted)

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(info(pageNumber, pageWidth, pageHeight))
            canvas = page.canvas
            y = margin + 16f
        }

        val columns = listOf(
            margin to labels.date,
            130f to labels.note,
            320f to labels.credit,
            400f to labels.payment,
            480f to labels.balance,
        )
        columns.forEach { (x, text) -> canvas.drawText(text, x, y, headPaint) }
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, muted)
        y += 16f
        rows.forEach { row ->
            if (y > pageHeight - 72f) {
                newPage()
            }
            canvas.drawText(row.date, margin, y, body)
            canvas.drawText(ellipsize(row.note.ifBlank { "—" }, body, 170f), 130f, y, body)
            canvas.drawText(row.credit, 320f, y, body)
            canvas.drawText(row.payment, 400f, y, body)
            canvas.drawText(row.balance, 480f, y, body)
            y += 18f
        }
        y += 10f
        canvas.drawLine(margin, y, pageWidth - margin, y, muted)
        y += 22f
        canvas.drawText("${labels.total}: $closing", margin, y, headPaint)
        y += 28f
        canvas.drawText(labels.generated, margin, y, muted)
        canvas.drawText(labels.appName, margin, pageHeight - 28f, muted)
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
    }

    private fun drawHeader(
        canvas: Canvas,
        pageWidth: Int,
        shop: String,
        customer: String,
        phone: String,
        labels: StatementLabels,
        title: Paint,
        body: Paint,
        muted: Paint,
    ): Float {
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 92f, Paint().apply { color = Color.parseColor("#0E7A45") })
        canvas.drawText(labels.appName, 36f, 36f, title)
        canvas.drawText(shop.ifBlank { labels.appName }, 36f, 62f, title.apply { textSize = 14f })
        title.textSize = 18f
        var y = 120f
        canvas.drawText(labels.title, 36f, y, body.apply { textSize = 16f; typeface = title.typeface })
        body.textSize = 12f
        y += 22f
        canvas.drawText(customer, 36f, y, body)
        if (phone.isNotBlank()) {
            y += 16f
            canvas.drawText(phone, 36f, y, muted)
        }
        return y + 28f
    }

    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.take(end) + "…") > max) end--
        return text.take(end) + "…"
    }

    private fun paint(typeface: Typeface?, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color
    }

    private fun info(number: Int, width: Int, height: Int) =
        PdfDocument.PageInfo.Builder(width, height, number).create()

    fun closingLabel(type: String, balance: Long): String {
        val tone = if (type == Kind.SUPPLIER) -balance else balance
        return formatTaka(kotlin.math.abs(balance).let { if (tone < 0) -it else it })
    }
}
