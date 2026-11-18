package bd.robin.hisabkhata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val phase by container.sync.phase.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf("all") }
    val names = parties.associateBy { it.id }
    val shown = parties.filter { party ->
        (kind == "all" || party.type == kind) &&
            (query.isBlank() || party.name.contains(query, true) || party.phone.contains(query))
    }.sortedByDescending { abs(balanceMap[it.id] ?: 0L) }
    val customers = parties.count { it.type == Kind.CUSTOMER }
    val suppliers = parties.count { it.type == Kind.SUPPLIER }
    val shownRecent = recent.filter { entry ->
        val party = names[entry.partyId]
        query.isBlank() || party?.name?.contains(query, true) == true || party?.phone?.contains(query) == true || entry.note.contains(query, true)
    }

    Column(Modifier.fillMaxSize().background(HeaderRed)) {
        RedHeader(badge = duePeople, onInbox = { onOpen("inbox") }, onSupport = { onOpen("support") })
        SyncLine(phase, settings?.lastSyncAt ?: 0L, settings?.language ?: "bn")
        Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = Color.White) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item {
                        KhataTools(onOpen)
                    }
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ToneCard(stringResource(R.string.total_receive), formatTaka(get), extra.softGive, extra.give, Modifier.weight(1f).tag("total_get"))
                            ToneCard(stringResource(R.string.total_pay), formatTaka(give), extra.softGet, extra.get, Modifier.weight(1f).tag("total_give"))
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).tag("home_search"),
                            label = { Text(stringResource(R.string.home_search)) },
                            singleLine = true,
                            trailingIcon = {
                                Row {
                                    TextButton(onClick = { kind = if (kind == Kind.CUSTOMER) Kind.SUPPLIER else Kind.CUSTOMER }) { Text("%") }
                                    TextButton(onClick = { onOpen("more") }, modifier = Modifier.tag("home_download")) { Text("↓") }
                                }
                            },
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth().background(SheetGrey).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.party_count, customers, suppliers), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.receive_pay_legend), color = extra.give, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (shown.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.home_empty)) }
                    } else {
                        items(shown, key = { it.id }) { party ->
                            val balance = balanceMap[party.id] ?: 0L
                            val tone = balanceTone(party.type, balance)
                            val color = if (tone == BalanceTone.GET) extra.give else extra.get
                            val days = ((System.currentTimeMillis() - party.createdAt) / 86_400_000L).coerceAtLeast(0)
                            Surface(onClick = { onOpen("detail/${party.id}") }, color = Color.White, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PartyAvatar(party.name, party.photoPath, 44.dp)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(party.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Text(
                                            (if (party.type == Kind.SUPPLIER) stringResource(R.string.supplier) else stringResource(R.string.customer)) +
                                                "  |  " + stringResource(R.string.days_ago, days),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(formatTaka(abs(balance)), color = color, style = MaterialTheme.typography.titleMedium)
                                    Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (shownRecent.isNotEmpty()) {
                        item { Text(stringResource(R.string.home_recent), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
                        items(shownRecent.take(5), key = { it.id }) { entry ->
                            val party = names[entry.partyId]
                            Text(
                                (party?.name ?: "") + "  " + formatTaka(entry.amount),
                                color = if (entry.type == Kind.CREDIT) extra.give else extra.get,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { onOpen("edit?id=&face=0") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).tag("add_party"),
                    containerColor = HeaderRed,
                    contentColor = Color.White,
                ) { Icon(Icons.Rounded.PersonAdd, stringResource(R.string.add_customer)) }
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

@Composable
private fun KhataTools(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(SheetGrey).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FeatureGlyph(Icons.Rounded.Store, stringResource(R.string.tool_multi), Color(0xFF2E7D32), "tool_multi") { onOpen("profile") }
            FeatureGlyph(Icons.Rounded.Inventory2, stringResource(R.string.tool_stock), Color(0xFFE53935), "tool_stock") { onOpen("products") }
            FeatureGlyph(Icons.Rounded.Notes, stringResource(R.string.tool_notes), Color(0xFFFB8C00), "tool_notes") { onOpen("reports") }
            FeatureGlyph(Icons.Rounded.Notifications, stringResource(R.string.tool_group), Color(0xFFF9A825), "tool_group") { onOpen("parties") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FeatureGlyph(Icons.Rounded.Backup, stringResource(R.string.tool_backup), Color(0xFF2E7D32), "tool_backup") { onOpen("settings") }
            FeatureGlyph(Icons.Rounded.Sms, stringResource(R.string.tool_sms), HeaderRed, "tool_sms") { onOpen("parties") }
            FeatureGlyph(Icons.Rounded.AccountBalanceWallet, stringResource(R.string.tool_cashbox), Color(0xFFFB8C00), "tool_cash") { onOpen("cash") }
            Box(Modifier.size(52.dp))
        }
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
