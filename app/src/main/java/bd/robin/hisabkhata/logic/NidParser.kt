package bd.robin.hisabkhata.logic

data class NidDraft(val number: String?, val name: String?)

fun parseNid(raw: String): NidDraft {
    val text = raw.normalizeDigits()
    val number = Regex("""(?<!\d)(\d{17}|\d{13}|\d{10})(?!\d)""").find(text)?.groupValues?.get(1)
    val name = Regex("""(?:name|নাম)\s*[:：]?\s*(.+)""", RegexOption.IGNORE_CASE)
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.any { ch -> ch.isLetter() } }
        ?.take(80)
    return NidDraft(number, name)
}
