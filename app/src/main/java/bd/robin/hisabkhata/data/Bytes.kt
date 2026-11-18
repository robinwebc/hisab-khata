package bd.robin.hisabkhata.data

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun FloatArray.toBlob(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (value in this) buffer.putFloat(value)
    return buffer.array()
}

fun ByteArray.toFloats(): FloatArray {
    if (isEmpty() || size % 4 != 0) return FloatArray(0)
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.float }
}

fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

fun String.fromBase64(): ByteArray? =
    if (isBlank()) null else runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()
