package bd.robin.hisabkhata.logic

enum class LimitKind { OK, OVER_TOTAL, OVER_DAILY }

data class LimitInput(
    val isCredit: Boolean,
    val amount: Long,
    val currentBalance: Long,
    val todayCredit: Long,
    val creditLimit: Long,
    val dailyLimit: Long?,
    val mode: String,
    val previousCredit: Long = 0,
    val previousCreditSameDay: Long = 0,
)

data class LimitDecision(
    val kind: LimitKind,
    val mode: String,
    val limit: Long,
    val used: Long,
    val projected: Long,
) {
    val blocked: Boolean get() = kind != LimitKind.OK && mode == Kind.MODE_BLOCK
    val warned: Boolean get() = kind != LimitKind.OK && mode != Kind.MODE_BLOCK
}

fun checkLimit(input: LimitInput): LimitDecision {
    val balance = input.currentBalance - input.previousCredit
    val today = (input.todayCredit - input.previousCreditSameDay).coerceAtLeast(0)
    if (!input.isCredit || input.amount <= 0) {
        return LimitDecision(LimitKind.OK, input.mode, input.creditLimit, balance, balance)
    }
    val projected = balance + input.amount
    if (projected > input.creditLimit) {
        return LimitDecision(LimitKind.OVER_TOTAL, input.mode, input.creditLimit, balance, projected)
    }
    val daily = input.dailyLimit
    if (daily != null && today + input.amount > daily) {
        return LimitDecision(LimitKind.OVER_DAILY, input.mode, daily, today, today + input.amount)
    }
    return LimitDecision(LimitKind.OK, input.mode, input.creditLimit, projected, projected)
}

fun remainingLimit(balance: Long, creditLimit: Long): Long =
    (creditLimit - balance).coerceAtLeast(0)

fun effectiveCreditLimit(useCustom: Boolean, custom: Long, defaultLimit: Long): Long =
    if (useCustom && custom > 0) custom else defaultLimit

fun effectiveDailyLimit(
    partyDaily: Boolean,
    partyAmount: Long,
    globalEnabled: Boolean,
    globalAmount: Long,
): Long? = when {
    partyDaily && partyAmount > 0 -> partyAmount
    globalEnabled && globalAmount > 0 -> globalAmount
    else -> null
}
