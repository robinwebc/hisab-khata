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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.logic.BalanceTone
import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.balanceTone
import bd.robin.hisabkhata.logic.formatTaka
import bd.robin.hisabkhata.ui.theme.LocalExtra
import java.time.LocalTime
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
    val names = parties.associateBy { it.id }
    val shownRecent = recent.filter { entry ->
        val party = names[entry.partyId]
        query.isBlank() || party?.name?.contains(query, true) == true || party?.phone?.contains(query) == true || entry.note.contains(query, true)
    }
    val matched = if (query.isBlank()) emptyList() else parties.filter {
        it.name.contains(query, true) || it.phone.contains(query)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("$greet, $shop", style = MaterialTheme.typography.headlineSmall)
            if (settings?.shopName.isNullOrBlank()) {
                TextButton(onClick = { onOpen("profile") }) { Text(stringResource(R.string.home_profile_hint)) }
            } else if (duePeople > 0) {
                Text(stringResource(R.string.home_due_count, duePeople), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToneCard(stringResource(R.string.home_get), formatTaka(get), extra.softGet, extra.get, Modifier.weight(1f))
                ToneCard(stringResource(R.string.home_give), formatTaka(give), extra.softGive, extra.give, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToneCard(stringResource(R.string.home_today_in), formatTaka(income), extra.softAmber, extra.amber, Modifier.weight(1f))
                ToneCard(stringResource(R.string.home_today_out), formatTaka(expense), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
        item {
            val actions = listOf(
                Triple(stringResource(R.string.home_add_customer), extra.get, "home_add_customer" to "edit?id=&face=0"),
                Triple(stringResource(R.string.home_credit), extra.give, "home_credit" to "pick/credit"),
                Triple(stringResource(R.string.home_payment), extra.get, "home_payment" to "pick/payment"),
                Triple(stringResource(R.string.home_cash), extra.amber, "home_cash" to "cash"),
                Triple(stringResource(R.string.home_scan_slip), extra.get, "home_scan_slip" to "scan/slip"),
                Triple(stringResource(R.string.home_scan_barcode), extra.amber, "home_scan_barcode" to "scan/barcode"),
                Triple(stringResource(R.string.home_scan_face), extra.give, "home_scan_face" to "scan/face?partyId="),
                Triple(stringResource(R.string.home_reports), extra.get, "home_reports" to "reports"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (title, tint, route) ->
                            ActionTile(title, tint, route.first) { onOpen(route.second) }
                        }
                        if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
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
                TextButton(onClick = { party?.let { onOpen("detail/${it.id}") } }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text("${Dates.format(entry.date, settings?.language ?: "bn")} · $label", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatTaka(entry.amount), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun PartyRow(name: String, phone: String, photo: String?, tone: BalanceTone, balance: Long, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val color = when (tone) {
        BalanceTone.GET -> extra.get
        BalanceTone.GIVE -> extra.give
        BalanceTone.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val word = when (tone) {
        BalanceTone.GET -> stringResource(R.string.balance_get)
        BalanceTone.GIVE -> stringResource(R.string.balance_give)
        BalanceTone.SETTLED -> stringResource(R.string.balance_settled)
    }
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
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
