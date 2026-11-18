package bd.robin.hisabkhata.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import bd.robin.hisabkhata.logic.BarcodeBits
import java.io.FileOutputStream

object BarcodeArt {
    fun label(name: String, price: String, code: String): Bitmap? {
        val clean = code.trim()
        if (clean.isEmpty()) return null
        val matrix = runCatching { BarcodeBits.code128(clean, 640, 180) }.getOrNull() ?: return null
        val width = 720
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
        }
        canvas.drawText(name.take(28), 24f, 48f, title)
        canvas.drawText(price, 24f, 88f, body)
        val dots = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix.get(x, y)) dots.setPixel(x, y, Color.BLACK)
            }
        }
        val bars = Bitmap.createScaledBitmap(dots, width - 48, 160, false)
        canvas.drawBitmap(bars, 24f, 110f, null)
        dots.recycle()
        bars.recycle()
        canvas.drawText(clean, 24f, 320f, body)
        return bitmap
    }

    fun share(context: Context, bitmap: Bitmap, fileName: String) {
        val file = Files.cacheFile(context, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = Files.uriFor(context, file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, fileName))
    }
}
