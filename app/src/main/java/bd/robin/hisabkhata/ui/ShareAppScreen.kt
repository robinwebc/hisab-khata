package bd.robin.hisabkhata.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import bd.robin.hisabkhata.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

const val DOWNLOAD_URL = "https://github.com/robinwebc/hisab-khata/releases/latest"

fun downloadQrBitmap(size: Int = 720): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(DOWNLOAD_URL, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    val ink = 0xFF0F6B56.toInt()
    val paper = 0xFFFFFFFF.toInt()
    for (y in 0 until size) {
        val row = y * size
        for (x in 0 until size) {
            pixels[row + x] = if (matrix.get(x, y)) ink else paper
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun ShareAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val qr = remember { downloadQrBitmap() }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.share_app), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.share_app_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.share_app_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_app_scan),
                    modifier = Modifier.padding(18.dp).size(260.dp).tag("download_qr"),
                )
            }
            Text(stringResource(R.string.share_app_scan), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            BigOutlined(stringResource(R.string.share_app_send), modifier = Modifier.tag("share_link")) {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_message, DOWNLOAD_URL))
                        },
                        context.getString(R.string.share_app),
                    ),
                )
            }
        }
    }
}
