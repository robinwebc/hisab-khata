package bd.robin.hisabkhata.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.data.EntryEntity
import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.LimitKind
import bd.robin.hisabkhata.logic.checkLimit
import bd.robin.hisabkhata.logic.dropDigit
import bd.robin.hisabkhata.logic.effectiveCreditLimit
import bd.robin.hisabkhata.logic.effectiveDailyLimit
import bd.robin.hisabkhata.logic.formatTaka
import bd.robin.hisabkhata.logic.pushDigit
import bd.robin.hisabkhata.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(partyId: String, initialType: String, entryId: String, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    val party by container.repo.observeParty(partyId).collectAsStateWithLifecycle(null)
    val entries by container.repo.observeEntries(partyId).collectAsStateWithLifecycle(emptyList())
    var type by rememberSaveable { mutableStateOf(if (initialType == Kind.PAYMENT) Kind.PAYMENT else Kind.CREDIT) }
    var digits by rememberSaveable { mutableStateOf("0") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(Dates.startOfDay(System.currentTimeMillis())) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    val zero = stringResource(R.string.entry_zero)
    val saved = stringResource(R.string.saved)
    val blockedText = stringResource(R.string.entry_blocked)
    val current = party
    LaunchedEffect(entries, loaded) {
        if (loaded) return@LaunchedEffect
        val existing = entries.firstOrNull { it.id == entryId }
        if (entryId.isNotBlank() && existing == null && entries.isEmpty()) return@LaunchedEffect
        if (existing != null) {
            type = existing.type
            digits = existing.amount.toString()
            note = existing.note
            date = existing.date
        } else {
            PendingCapture.slip?.let { slip ->
                slip.amount?.let { digits = it.toString() }
                slip.day?.let { date = Dates.startOfDay(it) }
                if (note.isBlank()) note = context.getString(R.string.slip_note)
                PendingCapture.slip = null
            }
        }
        loaded = true
    }
    if (current == null) {
        SimpleMissing(onBack)
        return
    }
    val amount = digits.toLongOrNull() ?: 0L
    val balance = entries.sumOf { if (it.type == Kind.CREDIT) it.amount else -it.amount }
    val todayCredit = entries.filter { it.type == Kind.CREDIT && Dates.sameDay(it.date, date) }.sumOf { it.amount }
    val editing = entries.firstOrNull { it.id == entryId }
    val previousCredit = if (editing?.type == Kind.CREDIT) editing.amount else 0L
    val previousToday = if (editing?.type == Kind.CREDIT && Dates.sameDay(editing.date, date)) editing.amount else 0L
    val limit = effectiveCreditLimit(current.useCustomLimit, current.creditLimit, settings?.defaultCreditLimit ?: 5000)
    val daily = effectiveDailyLimit(
        current.useDailyLimit,
        current.dailyLimit,
        settings?.dailyLimitEnabled == true,
        settings?.defaultDailyLimit ?: 0,
    )
    val decision = checkLimit(
        bd.robin.hisabkhata.logic.LimitInput(
            isCredit = type == Kind.CREDIT,
            amount = amount,
            currentBalance = balance,
            todayCredit = todayCredit,
            creditLimit = limit,
            dailyLimit = daily,
            mode = settings?.limitMode ?: Kind.MODE_WARN,
            previousCredit = previousCredit,
            previousCreditSameDay = previousToday,
        ),
    )
    val title = when {
        current.type == Kind.SUPPLIER && type == Kind.CREDIT -> stringResource(R.string.entry_credit_supplier)
        current.type == Kind.SUPPLIER -> stringResource(R.string.entry_payment_supplier)
        type == Kind.CREDIT -> stringResource(R.string.entry_credit_customer)
        else -> stringResource(R.string.entry_payment_customer)
    }
    val extra = LocalExtra.current
    Scaffold(topBar = { SimpleBar(title, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(current.name, style = MaterialTheme.typography.titleMedium)
                Row {
                    FilterChip(
                        type == Kind.CREDIT,
                        { type = Kind.CREDIT },
                        { Text(stringResource(R.string.credit)) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = extra.softGive,
                            selectedLabelColor = extra.give,
                        ),
                    )
                    FilterChip(
                        type == Kind.PAYMENT,
                        { type = Kind.PAYMENT },
                        { Text(stringResource(R.string.payment)) },
                        modifier = Modifier.padding(start = 8.dp),
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = extra.softGet,
                            selectedLabelColor = extra.get,
                        ),
                    )
                }
                Text(
                    formatTaka(amount),
                    color = if (type == Kind.CREDIT) extra.give else extra.get,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(vertical = 8.dp).tag("entry_amount"),
                )
                if (decision.kind != LimitKind.OK) {
                    val message = if (decision.kind == LimitKind.OVER_DAILY) {
                        stringResource(R.string.warning_daily, formatTaka(decision.used), formatTaka(decision.limit))
                    } else {
                        stringResource(R.string.warning_total, formatTaka(decision.used), formatTaka(decision.limit))
                    }
                    Text(message, color = extra.give, modifier = Modifier.padding(bottom = 8.dp).tag("limit_warning"))
                    if (decision.blocked) Text(blockedText, color = extra.give)
                }
                TextButton(onClick = { showDate = true }) { Text(stringResource(R.string.entry_date) + ": " + Dates.format(date, settings?.language ?: "bn")) }
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.entry_note)) })
            }
            Keypad(
                onDigit = { digits = pushDigit(digits, it) },
                onDelete = { digits = dropDigit(digits) },
                onClear = { digits = "0" },
            )
            BigButton(
                text = if (decision.warned) stringResource(R.string.entry_save_anyway) else stringResource(R.string.entry_save),
                enabled = !decision.blocked,
                modifier = Modifier.padding(vertical = 8.dp).tag("entry_save"),
                container = if (decision.warned) extra.amber else MaterialTheme.colorScheme.primary,
            ) {
                if (amount <= 0) {
                    context.toast(zero)
                    return@BigButton
                }
                scope.launch {
                    val now = System.currentTimeMillis()
                    container.repo.saveEntry(
                        EntryEntity(
                            id = entryId.ifBlank { UUID.randomUUID().toString() },
                            partyId = partyId,
                            type = type,
                            amount = amount,
                            note = note.trim(),
                            date = Dates.startOfDay(date),
                            createdAt = editing?.createdAt ?: now,
                            updatedAt = now,
                        ),
                    )
                    context.toast(saved)
                    onDone()
                }
            }
        }
    }
    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toPickerUtc(date))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Dates.fromPickerUtc(it) }
                    showDate = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}
