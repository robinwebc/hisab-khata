package bd.robin.hisabkhata.logic

fun digitsOnly(raw: String): String = raw.normalizeDigits().filter { it.isDigit() }

fun normalizeBdPhone(raw: String): String {
    var digits = digitsOnly(raw)
    if (digits.startsWith("00880")) digits = digits.removePrefix("00")
    return when {
        digits.startsWith("880") && digits.length >= 13 -> digits.take(13)
        digits.length == 11 && digits.startsWith("01") -> "88$digits"
        digits.length == 10 && digits.startsWith("1") -> "880$digits"
        else -> digits
    }
}

fun toE164Bd(raw: String): String? {
    val normalized = normalizeBdPhone(raw)
    return if (normalized.length == 13 && normalized.startsWith("8801")) "+$normalized" else null
}

fun localBdPhone(raw: String): String {
    val normalized = normalizeBdPhone(raw)
    return if (normalized.startsWith("880") && normalized.length == 13) {
        "0" + normalized.drop(3)
    } else {
        raw.trim()
    }
}

fun maskNid(number: String): String {
    val digits = digitsOnly(number)
    if (digits.length <= 4) return digits
    return "•".repeat(digits.length - 4) + digits.takeLast(4)
}
