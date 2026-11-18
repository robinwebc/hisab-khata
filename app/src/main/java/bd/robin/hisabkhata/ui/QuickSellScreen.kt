package bd.robin.hisabkhata.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Files
import bd.robin.hisabkhata.data.MlKit
import bd.robin.hisabkhata.data.ProductEntity
import bd.robin.hisabkhata.logic.SlipParser
import bd.robin.hisabkhata.logic.formatTaka
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class CartLine(val id: String, val name: String, val price: Long, var qty: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSellScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val parties by container.repo.observeParties().collectAsStateWithLifecycle(emptyList())
    val cart = remember { mutableStateListOf<CartLine>() }
    var unknown by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var checkout by remember { mutableStateOf(false) }
    var credit by remember { mutableStateOf(false) }
    var partyId by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var message by remember { mutableStateOf("") }
    val saved = stringResource(R.string.quick_sell_saved)
    val stock = stringResource(R.string.product_not_enough)
    val analyzer = remember {
        BarcodeAnalyzer(gapMs = 700) { code ->
            scope.launch {
                val product = container.repo.productByBarcode(code)
                if (product == null) {
                    unknown = code
                    name = ""
                    price = ""
                } else {
                    val line = cart.find { it.id == product.id }
                    if (line == null) cart.add(CartLine(product.id, product.name, product.price, 1))
                    else line.qty += 1
                    beep(context)
                }
            }
        }
    }
    val total = cart.sumOf { it.price * it.qty }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.quick_sell), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CameraPane(
                front = false,
                analyzer = analyzer,
                onReady = { capture = it },
                onError = { message = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quick_sell_total, formatTaka(total)), style = MaterialTheme.typography.titleLarge)
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cart, key = { it.id }) { line ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp)) {
                                Text("${line.name} × ${line.qty}", modifier = Modifier.weight(1f))
                                Text(formatTaka(line.price * line.qty))
                            }
                        }
                    }
                }
                BigButton(stringResource(R.string.quick_sell_done), enabled = cart.isNotEmpty(), modifier = Modifier.tag("quick_sell_done")) { checkout = true }
                BigOutlined(stringResource(R.string.quick_sell_slip), modifier = Modifier.tag("quick_sell_slip")) {
                    val shot = capture ?: return@BigOutlined
                    takePicture(context, shot, { bitmap ->
                        scope.launch {
                            val text = withContext(Dispatchers.Default) { MlKit.readText(bitmap) }
                            val draft = SlipParser.parse(text)
                            val amount = draft.amount
                            if (amount == null || amount <= 0) {
                                message = context.getString(R.string.quick_sell_slip_empty)
                            } else {
                                Files.saveJpeg(context, bitmap, "slips", "slip_${System.currentTimeMillis()}.jpg")
                                container.repo.recordSlipSale(amount, draft.name ?: context.getString(R.string.quick_sell_slip), null, false)
                                context.toast(saved)
                            }
                        }
                    }, { message = context.getString(R.string.scan_camera_error) })
                }
            }
        }
    }
    unknown?.let { code ->
        ModalBottomSheet(onDismissRequest = { unknown = null }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quick_sell_unknown, code), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth().tag("quick_name"), label = { Text(stringResource(R.string.product_name)) })
                OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth().tag("quick_price"), label = { Text(stringResource(R.string.product_price)) })
                BigButton(stringResource(R.string.save)) {
                    val taka = price.toLongOrNull() ?: return@BigButton
                    if (name.isBlank()) return@BigButton
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val product = ProductEntity(UUID.randomUUID().toString(), name.trim(), taka, 100, code, "", now, now)
                        container.repo.saveProduct(product)
                        cart.add(CartLine(product.id, product.name, product.price, 1))
                        beep(context)
                        unknown = null
                    }
                }
            }
        }
    }
    if (checkout) {
        AlertDialog(
            onDismissRequest = { checkout = false },
            title = { Text(stringResource(R.string.quick_sell_done)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.quick_sell_total, formatTaka(total)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!credit, { credit = false }, { Text(stringResource(R.string.quick_sell_cash)) })
                        FilterChip(credit, { credit = true }, { Text(stringResource(R.string.quick_sell_credit)) })
                    }
                    if (credit) {
                        parties.take(8).forEach { party ->
                            FilterChip(partyId == party.id, { partyId = party.id }, { Text(party.name) })
                        }
                    }
                }
            },
            confirmButton = {
                BigButton(stringResource(R.string.save), modifier = Modifier.tag("quick_confirm")) {
                    scope.launch {
                        val error = container.repo.checkout(cart.map { it.id to it.qty }, partyId.ifBlank { null }, credit && partyId.isNotBlank())
                        if (error == "stock") message = stock else if (error != null) message = error else {
                            cart.clear()
                            context.toast(saved)
                        }
                        checkout = false
                    }
                }
            },
            dismissButton = {},
        )
    }
}

private fun beep(context: android.content.Context) {
    runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP, 70) }
    val vibrator = context.getSystemService(Vibrator::class.java)
    if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    else @Suppress("DEPRECATION") vibrator?.vibrate(40)
}

@Composable
fun PasskeySetupScreen() {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("new") }
    var first by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val title = if (step == "confirm") stringResource(R.string.pin_again) else stringResource(R.string.passkey_new)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.passkey_help), style = MaterialTheme.typography.bodyLarge)
        Text("•".repeat(digits.length), style = MaterialTheme.typography.headlineMedium)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        Keypad(
            onDigit = { if (digits.length < 6) digits += it.toString() },
            onDelete = { digits = digits.dropLast(1) },
            onClear = { digits = "" },
        )
        BigButton(stringResource(R.string.done), modifier = Modifier.tag("passkey_done")) {
            if (digits.length < 4) {
                message = context.getString(R.string.pin_too_short)
                return@BigButton
            }
            if (step == "new") {
                first = digits
                digits = ""
                step = "confirm"
            } else if (digits != first) {
                message = context.getString(R.string.pin_mismatch)
                digits = ""
            } else {
                scope.launch {
                    container.passkey.save(digits)
                    container.session.unlock()
                    context.toast(context.getString(R.string.saved))
                }
            }
        }
    }
}
