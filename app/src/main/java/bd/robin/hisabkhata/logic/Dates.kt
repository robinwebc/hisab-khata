package bd.robin.hisabkhata.logic

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(millis: Long): Long = startOfDay(localDate(millis))

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endExclusive(date: LocalDate): Long = startOfDay(date.plusDays(1))

    fun today(): LocalDate = LocalDate.now(zone)

    fun format(millis: Long, lang: String): String {
        val locale = if (lang == "en") Locale.US else Locale.forLanguageTag("bn-BD")
        return localDate(millis).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }

    fun monthTitle(month: YearMonth, lang: String): String {
        val locale = if (lang == "en") Locale.US else Locale.forLanguageTag("bn-BD")
        return month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }

    fun shortDay(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d/M", Locale.US))

    fun toPickerUtc(localMillis: Long): Long =
        localDate(localMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerUtc(utcMillis: Long): Long {
        val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return startOfDay(date)
    }

    fun sameDay(a: Long, b: Long): Boolean = localDate(a) == localDate(b)
}
