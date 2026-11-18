package bd.robin.hisabkhata.data

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max

object MlKit {
    private val text by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val barcodes by lazy { BarcodeScanning.getClient() }
    private val faces by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.12f)
                .build(),
        )
    }

    suspend fun readText(bitmap: Bitmap): String =
        text.process(InputImage.fromBitmap(bitmap, 0)).await().text

    suspend fun readBarcodes(bitmap: Bitmap): List<String> =
        barcodes.process(InputImage.fromBitmap(bitmap, 0)).await().mapNotNull { it.rawValue }

    suspend fun largestFace(bitmap: Bitmap): Face? =
        faces.process(InputImage.fromBitmap(bitmap, 0)).await()
            .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
}

fun cropFace(source: Bitmap, box: Rect, rollDegrees: Float): Bitmap {
    val cx = box.exactCenterX()
    val cy = box.exactCenterY()
    val side = max(box.width(), box.height()) * 1.35f
    val left = (cx - side / 2f).toInt().coerceIn(0, source.width - 2)
    val top = (cy - side / 2f).toInt().coerceIn(0, source.height - 2)
    val right = (cx + side / 2f).toInt().coerceIn(left + 1, source.width)
    val bottom = (cy + side / 2f).toInt().coerceIn(top + 1, source.height)
    val crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    if (kotlin.math.abs(rollDegrees) < 8f) return crop
    val matrix = Matrix().apply { postRotate(-rollDegrees) }
    return Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, matrix, true)
}

fun downscale(source: Bitmap, maxSide: Int = 1600): Bitmap {
    val maxEdge = max(source.width, source.height)
    if (maxEdge <= maxSide) return source
    val scale = maxSide.toFloat() / maxEdge
    return Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}
