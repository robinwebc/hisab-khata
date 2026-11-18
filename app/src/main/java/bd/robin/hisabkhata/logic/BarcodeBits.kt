package bd.robin.hisabkhata.logic

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer

object BarcodeBits {
    fun code128(text: String, width: Int = 560, height: Int = 160): BitMatrix {
        val clean = text.trim()
        require(clean.isNotEmpty())
        return Code128Writer().encode(clean, BarcodeFormat.CODE_128, width.coerceAtMost(420), height.coerceAtMost(80))
    }
}
