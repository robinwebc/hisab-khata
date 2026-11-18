package bd.robin.hisabkhata.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.data.CashEntity
import bd.robin.hisabkhata.data.ProductEntity
import bd.robin.hisabkhata.logic.BalanceTone
import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.balanceTone
import bd.robin.hisabkhata.logic.dropDigit
import bd.robin.hisabkhata.logic.formatTaka
import bd.robin.hisabkhata.logic.pushDigit
import bd.robin.hisabkhata.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

@Composable
fun categoryLabel(key: String): String = when (key) {
    "rent" -> stringResource(R.string.category_rent)
    "salary" -> stringResource(R.string.category_salary)
    "transport" -> stringResource(R.string.category_transport)
    "utilities" -> stringResource(R.string.category_utilities)
    "stock" -> stringResource(R.string.category_stock)
    "food" -> stringResource(R.string.category_food)
    "sale" -> stringResource(R.string.category_sale)
    "other" -> stringResource(R.string.category_other)
    else -> key
}

@Composable
fun CashScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val cash by container.repo.observeCash().collectAsStateWithLifecycle(emptyList())
    val extra = LocalExtra.current
    var filter by rememberSaveable { mutableStateOf("all") }
    val income = cash.filter { it.type == Kind.INCOME }.sumOf { it.amount }
    val expense = cash.filter { it.type == Kind.EXPENSE }.sumOf { it.amount }
    val shown = cash.filter { filter == "all" || it.type == filter }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { SimpleBar(stringResource(R.string.cash_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ToneCard(stringResource(R.string.cash_balance), formatTaka(income - expense), extra.softGet, extra.get, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(filter == "all", { filter = "all" }, { Text(stringResource(R.string.cash_all)) })
                FilterChip(filter == Kind.INCOME, { filter = Kind.INCOME }, { Text(stringResource(R.string.cash_income)) })
                FilterChip(filter == Kind.EXPENSE, { filter = Kind.EXPENSE }, { Text(stringResource(R.string.cash_expense)) })
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (shown.isEmpty()) item { EmptyHint(stringResource(R.string.cash_empty)) }
                items(shown, key = { it.id }) { row ->
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(categoryLabel(row.category), style = MaterialTheme.typography.titleSmall)
                            Text(Dates.format(row.date, "bn") + if (row.note.isBlank()) "" else " · ${row.note}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatTaka(row.amount), color = if (row.type == Kind.INCOME) extra.get else extra.give)
                        IconButton(onClick = { scope.launch { container.repo.deleteCash(row.id) } }) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.delete))
                        }
                    }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                BigButton(stringResource(R.string.cash_add_income), Modifier.weight(1f).tag("cash_in"), container = extra.get) { onOpen("cash/edit?id=&type=income") }
                BigButton(stringResource(R.string.cash_add_expense), Modifier.weight(1f).tag("cash_out"), container = extra.give) { onOpen("cash/edit?id=&type=expense") }
            }
        }
    }
}

@Composable
fun CashEditScreen(id: String, initialType: String, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(initialType) }
    var digits by rememberSaveable { mutableStateOf("0") }
    var note by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(if (initialType == Kind.INCOME) "sale" else "other") }
    var custom by rememberSaveable { mutableStateOf("") }
    val saved = stringResource(R.string.saved)
    val zero = stringResource(R.string.entry_zero)
    Scaffold(topBar = { SimpleBar(if (type == Kind.INCOME) stringResource(R.string.cash_add_income) else stringResource(R.string.cash_add_expense), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(16.dp)) {
            Row {
                FilterChip(type == Kind.INCOME, { type = Kind.INCOME }, { Text(stringResource(R.string.cash_income)) })
                FilterChip(type == Kind.EXPENSE, { type = Kind.EXPENSE }, { Text(stringResource(R.string.cash_expense)) }, modifier = Modifier.padding(start = 8.dp))
            }
            Text(formatTaka(digits.toLongOrNull() ?: 0L), style = MaterialTheme.typography.headlineLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Kind.categories.take(4).forEach { key ->
                    FilterChip(category == key, { category = key }, { Text(categoryLabel(key)) })
                }
            }
            OutlinedTextField(custom, { custom = it }, label = { Text(stringResource(R.string.category_custom)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.entry_note)) }, modifier = Modifier.fillMaxWidth())
            Keypad({ digits = pushDigit(digits, it) }, { digits = dropDigit(digits) }, { digits = "0" })
            BigButton(stringResource(R.string.save), modifier = Modifier.padding(top = 8.dp)) {
                val amount = digits.toLongOrNull() ?: 0L
                if (amount <= 0) {
                    context.toast(zero)
                    return@BigButton
                }
                scope.launch {
                    val now = System.currentTimeMillis()
                    container.repo.saveCash(
                        CashEntity(
                            id = id.ifBlank { UUID.randomUUID().toString() },
                            type = type,
                            category = custom.ifBlank { category },
                            amount = amount,
                            note = note.trim(),
                            date = Dates.startOfDay(now),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    context.toast(saved)
                    onDone()
                }
            }
        }
    }
}

@Composable
fun ProductsScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val products by container.repo.observeProducts().collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var sell by remember { mutableStateOf<ProductEntity?>(null) }
    val shown = products.filter { query.isBlank() || it.name.contains(query, true) || it.barcode.contains(query) }
    val extra = LocalExtra.current
    Scaffold(topBar = {
        SimpleBar(stringResource(R.string.products_title))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.parties_search)) }, singleLine = true)
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shown.isEmpty()) item { EmptyHint(stringResource(R.string.products_empty)) }
                items(shown, key = { it.id }) { product ->
                    val stockColor = when {
                        product.stock <= 0 -> extra.give
                        product.stock <= 2 -> extra.amber
                        else -> extra.get
                    }
                    androidx.compose.material3.Surface(onClick = { onOpen("product/edit?id=${product.id}") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text(formatTaka(product.price) + " · " + stringResource(R.string.stock_left, product.stock), color = stockColor)
                                if (product.stock <= 0) Text(stringResource(R.string.product_stock_out), color = extra.give)
                                else if (product.stock <= 2) Text(stringResource(R.string.product_stock_low), color = extra.amber)
                            }
                            TextButton(onClick = { sell = product }, modifier = Modifier.tag("sell_${product.id}")) { Text(stringResource(R.string.product_sell)) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                BigButton(stringResource(R.string.add_product), Modifier.weight(1f)) { onOpen("product/edit?id=") }
                BigButton(stringResource(R.string.barcode_title), Modifier.weight(1f).tag("open_barcode")) { onOpen("scan/barcode") }
            }
        }
    }
    sell?.let { product -> SellDialog(product, { sell = null }) }
}

@Composable
fun SellDialog(product: ProductEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var qty by remember { mutableIntStateOf(1) }
    var addCash by remember { mutableStateOf(true) }
    val notEnough = stringResource(R.string.product_not_enough)
    val saved = stringResource(R.string.saved_sale)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stock_left, product.stock))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (qty > 1) qty-- }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Text("$qty", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { qty++ }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
                Text(stringResource(R.string.sell_total, formatTaka(product.price * qty)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.product_add_cash), modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(addCash, { addCash = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val error = container.repo.sell(product.id, qty, addCash)
                    if (error == "stock") context.toast(notEnough) else {
                        context.toast(saved)
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.product_sell)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ProductEditScreen(id: String, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val existing by container.repo.observeProduct(id).collectAsStateWithLifecycle(null)
    var name by rememberSaveable { mutableStateOf(PendingCapture.suggestedName.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(PendingCapture.slip?.amount?.toString().orEmpty()) }
    var stock by rememberSaveable { mutableStateOf("1") }
    var barcode by rememberSaveable { mutableStateOf(PendingCapture.barcode.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existing?.id) {
        if (!loaded && (id.isBlank() || existing != null)) {
            existing?.let {
                name = it.name
                price = it.price.toString()
                stock = it.stock.toString()
                barcode = it.barcode
                note = it.note
            }
            if (barcode.isBlank()) barcode = PendingCapture.barcode.orEmpty()
            loaded = true
            PendingCapture.suggestedName = null
            PendingCapture.barcode = null
        }
    }
    val need = stringResource(R.string.required_name)
    val saved = stringResource(R.string.saved)
    Scaffold(topBar = { SimpleBar(stringResource(R.string.product_name), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth().tag("product_name"), label = { Text(stringResource(R.string.product_name)) })
            OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth().tag("product_price"), label = { Text(stringResource(R.string.product_price)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(stock, { stock = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.product_stock)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(barcode, { barcode = it }, modifier = Modifier.fillMaxWidth().tag("product_barcode"), label = { Text(stringResource(R.string.product_barcode)) })
            OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.party_note)) })
            BigButton(stringResource(R.string.product_save), modifier = Modifier.tag("product_save")) {
                if (name.isBlank()) {
                    context.toast(need)
                    return@BigButton
                }
                scope.launch {
                    val now = System.currentTimeMillis()
                    val previous = if (id.isBlank()) null else container.repo.getProduct(id)
                    container.repo.saveProduct(
                        ProductEntity(
                            id = id.ifBlank { UUID.randomUUID().toString() },
                            name = name.trim(),
                            price = price.toLongOrNull() ?: 0,
                            stock = stock.toIntOrNull() ?: 0,
                            barcode = barcode.trim(),
                            note = note.trim(),
                            createdAt = previous?.createdAt ?: now,
                            updatedAt = now,
                        ),
                    )
                    context.toast(saved)
                    onDone()
                }
            }
            if (id.isNotBlank()) {
                TextButton(onClick = { scope.launch { container.repo.deleteProduct(id); onDone() } }) {
                    Text(stringResource(R.string.product_delete), color = LocalExtra.current.give)
                }
            }
        }
    }
}

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val lang = (boot as? Boot.Ready)?.settings?.language ?: "bn"
    val entries by container.repo.observeAllEntries().collectAsStateWithLifecycle(emptyList())
    val cash by container.repo.observeCash().collectAsStateWithLifecycle(emptyList())
    val parties by container.repo.observeParties().collectAsStateWithLifecycle(emptyList())
    val balances by container.repo.observeBalances().collectAsStateWithLifecycle(emptyList())
    var monthly by rememberSaveable { mutableStateOf(false) }
    var day by remember { mutableStateOf(Dates.today()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val extra = LocalExtra.current
    val map = balances.associate { it.partyId to it.balance }
    var get = 0L
    var give = 0L
    parties.forEach { party ->
        val balance = map[party.id] ?: 0
        when (balanceTone(party.type, balance)) {
            BalanceTone.GET -> get += kotlin.math.abs(balance)
            BalanceTone.GIVE -> give += kotlin.math.abs(balance)
            BalanceTone.SETTLED -> Unit
        }
    }
    val periodEntries = entries.filter {
        val date = Dates.localDate(it.date)
        if (monthly) YearMonth.from(date) == month else date == day
    }
    val periodCash = cash.filter {
        val date = Dates.localDate(it.date)
        if (monthly) YearMonth.from(date) == month else date == day
    }
    val credit = periodEntries.filter { it.type == Kind.CREDIT }.sumOf { it.amount }
    val payment = periodEntries.filter { it.type == Kind.PAYMENT }.sumOf { it.amount }
    val income = periodCash.filter { it.type == Kind.INCOME }.sumOf { it.amount }
    val expense = periodCash.filter { it.type == Kind.EXPENSE }.sumOf { it.amount }
    val chart = if (monthly) {
        val length = month.lengthOfMonth()
        (1..length).map { dom ->
            val date = month.atDay(dom)
            val c = entries.filter { Dates.localDate(it.date) == date && it.type == Kind.CREDIT }.sumOf { it.amount }
            val p = entries.filter { Dates.localDate(it.date) == date && it.type == Kind.PAYMENT }.sumOf { it.amount }
            Dates.shortDay(date) to (c.toFloat() to p.toFloat())
        }.filter { it.second.first > 0f || it.second.second > 0f }
    } else {
        (0..6).map { offset ->
            val date = day.minusDays((6 - offset).toLong())
            val inn = cash.filter { Dates.localDate(it.date) == date && it.type == Kind.INCOME }.sumOf { it.amount }
            val out = cash.filter { Dates.localDate(it.date) == date && it.type == Kind.EXPENSE }.sumOf { it.amount }
            Dates.shortDay(date) to (inn.toFloat() to out.toFloat())
        }
    }
    val categories = periodCash.filter { it.type == Kind.EXPENSE }.groupBy { it.category }.map { it.key to it.value.sumOf { row -> row.amount } }.sortedByDescending { it.second }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.report_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                FilterChip(!monthly, { monthly = false }, { Text(stringResource(R.string.report_daily)) }, modifier = Modifier.tag("report_daily"))
                FilterChip(monthly, { monthly = true }, { Text(stringResource(R.string.report_monthly)) }, modifier = Modifier.padding(start = 8.dp).tag("report_monthly"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (monthly) month = month.minusMonths(1) else day = day.minusDays(1) }) { Text("‹") }
                Text(if (monthly) Dates.monthTitle(month, lang) else Dates.format(Dates.startOfDay(day), lang), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { if (monthly) month = month.plusMonths(1) else day = day.plusDays(1) }) { Text("›") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToneCard(stringResource(R.string.report_outstanding) + " " + stringResource(R.string.home_get), formatTaka(get), extra.softGet, extra.get, Modifier.weight(1f))
                ToneCard(stringResource(R.string.home_give), formatTaka(give), extra.softGive, extra.give, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToneCard(if (monthly) stringResource(R.string.report_credit) else stringResource(R.string.report_in), formatTaka(if (monthly) credit else income), extra.softAmber, extra.amber, Modifier.weight(1f))
                ToneCard(if (monthly) stringResource(R.string.report_payment) else stringResource(R.string.report_out), formatTaka(if (monthly) payment else expense), extra.softGive, extra.give, Modifier.weight(1f))
            }
            Text(stringResource(R.string.report_net) + " " + formatTaka(income - expense), style = MaterialTheme.typography.titleMedium)
            if (chart.isEmpty()) EmptyHint(stringResource(R.string.report_empty))
            else {
                Text(if (monthly) stringResource(R.string.credit) + " / " + stringResource(R.string.payment) else stringResource(R.string.report_in) + " / " + stringResource(R.string.report_out))
                GroupedBars(chart, if (monthly) extra.give else extra.get, if (monthly) extra.get else extra.amber)
            }
            Text(stringResource(R.string.report_expense_breakdown), style = MaterialTheme.typography.titleMedium)
            if (categories.isEmpty()) Text(stringResource(R.string.report_empty))
            categories.forEach { (key, total) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categoryLabel(key))
                    Text(formatTaka(total))
                }
            }
        }
    }
}

