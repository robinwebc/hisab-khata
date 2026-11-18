package bd.robin.hisabkhata.logic

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

fun String.normalizeDigits(): String {
    val out = StringBuilder(length)
    for (ch in this) {
        val index = banglaDigits.indexOf(ch)
        if (index >= 0) out.append('0' + index) else out.append(ch)
    }
    return out.toString()
}

fun formatTaka(amount: Long): String {
    val nf = NumberFormat.getIntegerInstance(Locale.US)
    val body = nf.format(abs(amount))
    return (if (amount < 0) "-" else "") + "৳" + body
}

fun parseTaka(raw: String): Long? {
    val cleaned = raw.normalizeDigits().filter { it.isDigit() }
    if (cleaned.isBlank()) return null
    return cleaned.toLongOrNull()
}

fun parseAmountToken(token: String): Long? {
    val cleaned = token.normalizeDigits().replace(",", "").trim()
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value <= 0.0 || value > 100_000_000.0) return null
    return value.roundToLong()
}

fun pushDigit(current: String, digit: Int): String {
    val base = if (current == "0") "" else current
    val next = base + digit.toString()
    return if (next.length > 9) current else next
}

fun dropDigit(current: String): String {
    val next = current.dropLast(1)
    return next.ifEmpty { "0" }
}
