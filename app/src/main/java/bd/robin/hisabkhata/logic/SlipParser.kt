package bd.robin.hisabkhata.logic

import java.time.LocalDate

data class SlipDraft(
    val amount: Long?,
    val day: LocalDate?,
    val name: String?,
    val raw: String,
)

object SlipParser {
    const val SAMPLE = """
Rahim Store
Date: 18/11/2026
Name: Karim Mia
Rice 180
Oil 220
Total 400
"""

    private val labeledAmount = Regex(
        """(?:grand\s*total|total|amount|মোট|টোটাল|টাকা|taka|tk|৳)\s*[:：=]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val anyAmount = Regex("""(?<!\d)([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]{1,2})?)(?!\d)""")
    private val isoDate = Regex("""(20\d{2})-(\d{1,2})-(\d{1,2})""")
    private val slashDate = Regex("""(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""")
    private val nameLine = Regex(
        """(?:customer|name|নাম|খদ্দের|ক্রেতা)\s*[:：]\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): SlipDraft {
        val text = raw.normalizeDigits()
        return SlipDraft(
            amount = amount(text),
            day = date(text),
            name = name(text),
            raw = raw.trim(),
        )
    }

    private fun amount(text: String): Long? {
        val labeled = labeledAmount.findAll(text).mapNotNull { parseAmountToken(it.groupValues[1]) }.lastOrNull()
        if (labeled != null) return labeled
        return anyAmount.findAll(text)
            .mapNotNull { token ->
                val value = parseAmountToken(token.groupValues[1]) ?: return@mapNotNull null
                val digits = token.groupValues[1].filter { it.isDigit() }
                if (digits.length >= 10) return@mapNotNull null
                if (digits.length == 4 && value in 1900..2100) return@mapNotNull null
                value
            }
            .maxOrNull()
    }

    private fun date(text: String): LocalDate? {
        isoDate.find(text)?.let { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        slashDate.find(text)?.let { match ->
            return dmy(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }
        return null
    }

    private fun dmy(day: Int, month: Int, yearRaw: Int): LocalDate? {
        val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
            ?: runCatching { LocalDate.of(year, day, month) }.getOrNull()
    }

    private fun name(text: String): String? {
        val found = nameLine.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (found.isBlank()) return null
        val digits = found.count { it.isDigit() }
        if (digits > found.length / 2) return null
        return found.take(80)
    }
}
