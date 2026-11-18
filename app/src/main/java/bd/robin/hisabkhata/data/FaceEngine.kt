package bd.robin.hisabkhata.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import bd.robin.hisabkhata.logic.FaceMath
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

class FaceEngine(context: Context) {
    private val tflite = TfliteEmbedder.tryCreate(context)
    val modelName: String = if (tflite != null) MODEL_TFLITE else MODEL_LOCAL
    val threshold: Float = if (tflite != null) FaceMath.TFLITE_THRESHOLD else FaceMath.LOCAL_THRESHOLD
    val usingMobileFaceNet: Boolean get() = tflite != null

    fun embed(face: Bitmap): FloatArray = synchronized(this) {
        val remote = tflite?.embed(face)
        if (remote != null) return remote
        localEmbed(face)
    }

    private fun localEmbed(face: Bitmap): FloatArray {
        val size = 64
        val scaled = Bitmap.createScaledBitmap(face, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val gray = FloatArray(pixels.size) { index ->
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
        }
        return FaceMath.gridEmbed(gray, size, size)
    }

    private class TfliteEmbedder(private val interpreter: Interpreter) {
        private val inputShape = interpreter.getInputTensor(0).shape()
        private val outputShape = interpreter.getOutputTensor(0).shape()

        fun embed(face: Bitmap): FloatArray? = runCatching {
            val batch = inputShape.getOrNull(0)?.takeIf { it > 0 } ?: 1
            val height = inputShape.getOrNull(1) ?: 112
            val width = inputShape.getOrNull(2) ?: 112
            val channels = inputShape.getOrNull(3) ?: 3
            val scaled = Bitmap.createScaledBitmap(face, width, height, true)
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            val input = Array(batch) { Array(height) { Array(width) { FloatArray(channels) } } }
            for (b in 0 until batch) {
                var cursor = 0
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = pixels[cursor++]
                        input[b][y][x][0] = (((pixel shr 16) and 0xFF) - 127.5f) / 128f
                        if (channels > 1) input[b][y][x][1] = (((pixel shr 8) and 0xFF) - 127.5f) / 128f
                        if (channels > 2) input[b][y][x][2] = ((pixel and 0xFF) - 127.5f) / 128f
                    }
                }
            }
            val dim = outputShape.last()
            val rows = outputShape.first().takeIf { it > 0 } ?: 1
            val output = Array(rows) { FloatArray(dim) }
            interpreter.run(input, output)
            FaceMath.l2normalize(output[0])
        }.onFailure { Log.w(TAG, "embed failed", it) }.getOrNull()

        companion object {
            fun tryCreate(context: Context): TfliteEmbedder? = runCatching {
                val buffer = context.assets.openFd(ASSET).use { fd ->
                    FileInputStream(fd.fileDescriptor).channel.use { channel ->
                        channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                    }
                }
                val interpreter = Interpreter(buffer, Interpreter.Options().apply { numThreads = 4 })
                Log.i(TAG, "input ${interpreter.getInputTensor(0).shape().contentToString()} output ${interpreter.getOutputTensor(0).shape().contentToString()}")
                TfliteEmbedder(interpreter)
            }.onFailure { Log.w(TAG, "MobileFaceNet not loaded", it) }.getOrNull()
        }
    }

    companion object {
        const val MODEL_TFLITE = "mobilefacenet"
        const val MODEL_LOCAL = "local-grid"
        private const val ASSET = "mobile_face_net.tflite"
        private const val TAG = "HisabFace"
    }
}
