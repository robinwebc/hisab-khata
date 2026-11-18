package bd.robin.hisabkhata.logic

object Kind {
    const val CUSTOMER = "customer"
    const val SUPPLIER = "supplier"
    const val CREDIT = "credit"
    const val PAYMENT = "payment"
    const val INCOME = "income"
    const val EXPENSE = "expense"
    const val VERIFY_NONE = "none"
    const val VERIFY_CALL = "call"
    const val VERIFY_OTP = "otp"
    const val SESSION_NONE = "none"
    const val SESSION_GUEST = "guest"
    const val SESSION_GOOGLE = "google"
    const val MODE_WARN = "warn"
    const val MODE_BLOCK = "block"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    val categories = listOf(
        "rent",
        "salary",
        "transport",
        "utilities",
        "stock",
        "food",
        "sale",
        "other",
    )

    val businesses = listOf(
        "grocery",
        "pharmacy",
        "clothing",
        "electronics",
        "restaurant",
        "hardware",
        "mobile",
        "other",
    )
}

enum class BalanceTone { GET, GIVE, SETTLED }

fun balanceTone(partyType: String, balance: Long): BalanceTone {
    if (balance == 0L) return BalanceTone.SETTLED
    val positive = balance > 0
    return if (partyType == Kind.CUSTOMER) {
        if (positive) BalanceTone.GET else BalanceTone.GIVE
    } else {
        if (positive) BalanceTone.GIVE else BalanceTone.GET
    }
}

fun signedBalance(partyType: String, balance: Long): Long {
    return when (balanceTone(partyType, balance)) {
        BalanceTone.GET -> kotlin.math.abs(balance)
        BalanceTone.GIVE -> kotlin.math.abs(balance)
        BalanceTone.SETTLED -> 0L
    }
}
