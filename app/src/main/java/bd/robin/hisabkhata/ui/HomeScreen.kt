package bd.robin.hisabkhata.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.data.SyncPhase
import bd.robin.hisabkhata.logic.BalanceTone
import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.balanceTone
import bd.robin.hisabkhata.logic.formatTaka
import bd.robin.hisabkhata.ui.theme.LocalExtra
import bd.robin.hisabkhata.ui.theme.NsBrand
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val extra = LocalExtra.current
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    val parties by container.repo.observeParties().collectAsStateWithLifecycle(emptyList())
    val balances by container.repo.observeBalances().collectAsStateWithLifecycle(emptyList())
    val recent by container.repo.observeRecent().collectAsStateWithLifecycle(emptyList())
    val cash by container.repo.observeCash().collectAsStateWithLifecycle(emptyList())
    val balanceMap = balances.associate { it.partyId to it.balance }
    var query by rememberSaveable { mutableStateOf("") }
    val shop = settings?.shopName?.ifBlank { stringResource(R.string.shop_fallback) } ?: stringResource(R.string.shop_fallback)
    var get = 0L
    var give = 0L
    var duePeople = 0
    parties.forEach { party ->
        val balance = balanceMap[party.id] ?: 0L
        when (balanceTone(party.type, balance)) {
            BalanceTone.GET -> {
                get += abs(balance)
                if (party.type == Kind.CUSTOMER) duePeople++
            }
            BalanceTone.GIVE -> give += abs(balance)
            BalanceTone.SETTLED -> Unit
        }
    }
    val today = Dates.today()
    val income = cash.filter { it.type == Kind.INCOME && Dates.localDate(it.date) == today }.sumOf { it.amount }
    val expense = cash.filter { it.type == Kind.EXPENSE && Dates.localDate(it.date) == today }.sumOf { it.amount }
    val hour = LocalTime.now().hour
    val greet = stringResource(
        when (hour) {
            in 5..11 -> R.string.greet_morning
            in 12..16 -> R.string.greet_afternoon
            in 17..20 -> R.string.greet_evening
            else -> R.string.greet_night
        },
    )
    val phase by container.sync.phase.collectAsStateWithLifecycle()
    val names = parties.associateBy { it.id }
    val shownRecent = recent.filter { entry ->
        val party = names[entry.partyId]
        query.isBlank() || party?.name?.contains(query, true) == true || party?.phone?.contains(query) == true || entry.note.contains(query, true)
    }
    val matched = if (query.isBlank()) emptyList() else parties.filter {
        it.name.contains(query, true) || it.phone.contains(query)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = NsBrand)
            Text("$greet", style = MaterialTheme.typography.headlineSmall)
            Text(shop, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings?.shopName.isNullOrBlank()) {
                Surface(onClick = { onOpen("profile") }, shape = RoundedCornerShape(14.dp), color = extra.softGet, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.home_profile_hint), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = extra.get)
                }
            } else if (duePeople > 0) {
                Text(stringResource(R.string.home_due_count, duePeople), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            SyncLine(phase, settings?.lastSyncAt ?: 0L, settings?.language ?: "bn")
            BigButton(stringResource(R.string.quick_sell), modifier = Modifier.padding(top = 10.dp).tag("quick_sell_home")) { onOpen("quicksell") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToneCard(stringResource(R.string.home_get), formatTaka(get), extra.softGive, extra.give, Modifier.weight(1f).tag("total_get"))
                ToneCard(stringResource(R.string.home_give), formatTaka(give), extra.softGet, extra.get, Modifier.weight(1f).tag("total_give"))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToneCard(stringResource(R.string.home_today_in), formatTaka(income), extra.softGet, extra.get, Modifier.weight(1f))
                ToneCard(stringResource(R.string.home_today_out), formatTaka(expense), extra.softGive, extra.give, Modifier.weight(1f))
            }
        }
        item {
            val actions = listOf(
                QuickAction(stringResource(R.string.home_add_customer), Icons.Rounded.PersonAdd, "home_add_customer", "edit?id=&face=0"),
                QuickAction(stringResource(R.string.home_credit), Icons.Rounded.South, "home_credit", "pick/credit", "sell"),
                QuickAction(stringResource(R.string.home_payment), Icons.Rounded.Payments, "home_payment", "pick/payment", "recovery"),
                QuickAction(stringResource(R.string.home_cash), Icons.Rounded.AccountBalanceWallet, "home_cash", "cash"),
                QuickAction(stringResource(R.string.home_scan_slip), Icons.Rounded.DocumentScanner, "home_scan_slip", "scan/slip"),
                QuickAction(stringResource(R.string.home_scan_barcode), Icons.Rounded.QrCodeScanner, "home_scan_barcode", "scan/barcode"),
                QuickAction(stringResource(R.string.home_scan_face), Icons.Rounded.Face, "home_scan_face", "scan/face?partyId="),
                QuickAction(stringResource(R.string.home_reports), Icons.Rounded.BarChart, "home_reports", "reports"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { action ->
                            Surface(
                                onClick = { onOpen(action.route) },
                                modifier = Modifier.weight(1f).tag(action.tag),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 1.dp,
                            ) {
                                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val tint = when (action.money) {
                                        "sell" -> extra.give
                                        "recovery" -> extra.get
                                        else -> NsBrand
                                    }
                                    Icon(action.icon, null, tint = tint)
                                    Text(action.title, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                        repeat(4 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().tag("home_search"),
                label = { Text(stringResource(R.string.home_search)) },
                singleLine = true,
            )
        }
        if (matched.isNotEmpty()) {
            items(matched, key = { it.id }) { party ->
                PartyRow(party.name, party.phone, party.photoPath, balanceTone(party.type, balanceMap[party.id] ?: 0L), balanceMap[party.id] ?: 0L) {
                    onOpen("detail/${party.id}")
                }
            }
        }
        item { Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium) }
        if (shownRecent.isEmpty()) {
            item { EmptyHint(stringResource(R.string.home_empty)) }
        } else {
            items(shownRecent, key = { it.id }) { entry ->
                val party = names[entry.partyId]
                val title = party?.name ?: stringResource(R.string.not_found)
                val label = if (entry.type == Kind.CREDIT) stringResource(R.string.credit) else stringResource(R.string.payment)
                Surface(
                    onClick = { party?.let { onOpen("detail/${it.id}") } },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${Dates.format(entry.date, settings?.language ?: "bn")} · $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            formatTaka(entry.amount),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (entry.type == Kind.CREDIT) extra.give else extra.get,
                        )
                    }
                }
            }
        }
    }
    if (settings?.offerDriveRestore == true) {
        val restoreContext = androidx.compose.ui.platform.LocalContext.current
        val restoreScope = androidx.compose.runtime.rememberCoroutineScope()
        ConfirmDialog(
            title = stringResource(R.string.backup_offer_title),
            body = stringResource(R.string.backup_offer_body),
            confirm = stringResource(R.string.backup_restore_now),
            onConfirm = {
                val activity = restoreContext as? android.app.Activity ?: return@ConfirmDialog
                restoreScope.launch {
                    val gate = container.drive.ensureAccess(activity)
                    if (gate is bd.robin.hisabkhata.data.DriveResult.Ok) container.drive.restore()
                    container.settings.update(notify = false) { it.copy(offerDriveRestore = false) }
                }
            },
            onDismiss = {
                restoreScope.launch { container.settings.update(notify = false) { it.copy(offerDriveRestore = false) } }
            },
        )
    }
}

private data class QuickAction(val title: String, val icon: ImageVector, val tag: String, val route: String, val money: String = "")

@Composable
private fun SyncLine(phase: SyncPhase, lastSyncAt: Long, lang: String) {
    val text = when (phase) {
        SyncPhase.Syncing -> stringResource(R.string.sync_syncing)
        is SyncPhase.Error -> phase.message
        is SyncPhase.Saved -> stringResource(R.string.sync_saved) + " · " + syncClock(phase.at, lang)
        SyncPhase.Idle -> if (lastSyncAt > 0L) {
            stringResource(R.string.sync_saved) + " · " + syncClock(lastSyncAt, lang)
        } else {
            stringResource(R.string.sync_waiting)
        }
    }
    val color = if (phase is SyncPhase.Error) LocalExtra.current.give else MaterialTheme.colorScheme.onSurfaceVariant
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 8.dp).tag("sync_status"))
}

private fun syncClock(millis: Long, lang: String): String {
    val locale = if (lang == "en") java.util.Locale.US else java.util.Locale.forLanguageTag("bn-BD")
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a", locale))
}

@Composable
fun PartyRow(name: String, phone: String, photo: String?, tone: BalanceTone, balance: Long, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val color = when (tone) {
        BalanceTone.GET -> extra.give
        BalanceTone.GIVE -> extra.get
        BalanceTone.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val word = when (tone) {
        BalanceTone.GET -> stringResource(R.string.balance_get)
        BalanceTone.GIVE -> stringResource(R.string.balance_give)
        BalanceTone.SETTLED -> stringResource(R.string.balance_settled)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PartyAvatar(name, photo, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(word, color = color, style = MaterialTheme.typography.labelMedium)
                Text(formatTaka(abs(balance)), color = color, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
