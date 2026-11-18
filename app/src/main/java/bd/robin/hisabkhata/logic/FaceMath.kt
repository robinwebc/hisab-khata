package bd.robin.hisabkhata.logic

import kotlin.math.sqrt

object FaceMath {
    const val TFLITE_THRESHOLD = 0.62f
    const val LOCAL_THRESHOLD = 0.93f

    fun l2normalize(values: FloatArray, epsilon: Float = 1e-10f): FloatArray {
        var sum = 0f
        for (value in values) sum += value * value
        val norm = sqrt(sum.toDouble()).toFloat()
        val denom = norm + epsilon
        return FloatArray(values.size) { values[it] / denom }
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size || left.isEmpty()) return 0f
        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        for (i in left.indices) {
            dot += left[i] * right[i]
            leftNorm += left[i] * left[i]
            rightNorm += right[i] * right[i]
        }
        val denom = sqrt(leftNorm.toDouble()).toFloat() * sqrt(rightNorm.toDouble()).toFloat()
        if (denom <= 1e-8f) return 0f
        return dot / denom
    }

    /**
     * 8x8 block mean, spread, and gradient energy of a grayscale face.
     * Used only when the TFLite model cannot load.
     */
    fun gridEmbed(gray: FloatArray, width: Int, height: Int): FloatArray {
        require(gray.size == width * height && width >= 8 && height >= 8)
        val cells = 8
        val cellW = width / cells
        val cellH = height / cells
        val features = FloatArray(cells * cells * 3)
        var index = 0
        for (by in 0 until cells) {
            for (bx in 0 until cells) {
                var sum = 0f
                var sumSq = 0f
                var grad = 0f
                var count = 0
                val y0 = by * cellH
                val x0 = bx * cellW
                for (y in y0 until y0 + cellH) {
                    for (x in x0 until x0 + cellW) {
                        val pixel = gray[y * width + x]
                        sum += pixel
                        sumSq += pixel * pixel
                        if (x + 1 < width) grad += kotlin.math.abs(gray[y * width + x + 1] - pixel)
                        if (y + 1 < height) grad += kotlin.math.abs(gray[(y + 1) * width + x] - pixel)
                        count++
                    }
                }
                val mean = sum / count
                val variance = (sumSq / count) - mean * mean
                features[index++] = mean
                features[index++] = sqrt(variance.coerceAtLeast(0f))
                features[index++] = grad / count
            }
        }
        return l2normalize(features)
    }
}

data class FaceHit(val partyId: String, val score: Float)

fun bestFaceMatch(
    probe: FloatArray,
    gallery: List<Pair<String, FloatArray>>,
    threshold: Float,
): FaceHit? {
    val scored = gallery.mapNotNull { (id, embedding) ->
        if (embedding.size != probe.size) null else FaceHit(id, FaceMath.cosine(probe, embedding))
    }.sortedByDescending { it.score }
    val top = scored.firstOrNull() ?: return null
    if (top.score < threshold) return null
    val second = scored.getOrNull(1)?.score
    if (second != null && top.score - second < 0.04f && top.score < 0.97f) return null
    return top
}
