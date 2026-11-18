package bd.robin.hisabkhata.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import bd.robin.hisabkhata.data.Files
import bd.robin.hisabkhata.data.PartyEntity
import bd.robin.hisabkhata.data.PdfStatement
import bd.robin.hisabkhata.data.SensitiveEntity
import bd.robin.hisabkhata.data.StatementLabels
import bd.robin.hisabkhata.data.StatementRow
import bd.robin.hisabkhata.data.toBlob
import bd.robin.hisabkhata.logic.BalanceTone
import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.balanceTone
import bd.robin.hisabkhata.logic.effectiveCreditLimit
import bd.robin.hisabkhata.logic.effectiveDailyLimit
import bd.robin.hisabkhata.logic.formatTaka
import bd.robin.hisabkhata.logic.localBdPhone
import bd.robin.hisabkhata.logic.maskNid
import bd.robin.hisabkhata.logic.normalizeBdPhone
import bd.robin.hisabkhata.logic.remainingLimit
import bd.robin.hisabkhata.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.abs

@Composable
fun PartiesScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val parties by container.repo.observeParties().collectAsStateWithLifecycle(emptyList())
    val balances by container.repo.observeBalances().collectAsStateWithLifecycle(emptyList())
    val map = balances.associate { it.partyId to it.balance }
    var filter by rememberSaveable { mutableStateOf(Kind.CUSTOMER) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = parties.filter { party ->
        (filter == "all" || party.type == filter) &&
            (query.isBlank() || party.name.contains(query, true) || party.phone.contains(query))
    }.sortedByDescending { abs(map[it.id] ?: 0L) }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.parties_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().tag("party_search"), label = { Text(stringResource(R.string.parties_search)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(filter == Kind.CUSTOMER, { filter = Kind.CUSTOMER }, { Text(stringResource(R.string.parties_customers)) })
                FilterChip(filter == Kind.SUPPLIER, { filter = Kind.SUPPLIER }, { Text(stringResource(R.string.parties_suppliers)) })
                FilterChip(filter == "all", { filter = "all" }, { Text(stringResource(R.string.parties_all)) })
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shown.isEmpty()) item { EmptyHint(stringResource(R.string.parties_empty)) }
                items(shown, key = { it.id }) { party ->
                    PartyRow(party.name, party.phone, party.photoPath, balanceTone(party.type, map[party.id] ?: 0), map[party.id] ?: 0) {
                        onOpen("detail/${party.id}")
                    }
                }
            }
            BigButton(
                if (filter == Kind.SUPPLIER) stringResource(R.string.add_supplier) else stringResource(R.string.add_customer),
                modifier = Modifier.padding(vertical = 8.dp).navigationBarsPadding().tag("add_party"),
            ) { onOpen("edit?id=&face=0") }
        }
    }
}

@Composable
fun PickScreen(purpose: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val container = LocalContainer.current
    val parties by container.repo.observeParties().collectAsStateWithLifecycle(emptyList())
    val title = if (purpose == "payment") stringResource(R.string.pick_payment) else stringResource(R.string.pick_credit)
    val slipName = PendingCapture.slip?.name
    Scaffold(topBar = { SimpleBar(title, onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (purpose == "slip" && !slipName.isNullOrBlank()) {
                item {
                    BigButton(stringResource(R.string.scan_new_party, slipName)) {
                        PendingCapture.suggestedName = slipName
                        onOpen("edit?id=&face=0")
                    }
                }
            }
            if (parties.isEmpty()) item { EmptyHint(stringResource(R.string.pick_empty)) }
            items(parties, key = { it.id }) { party ->
                TextButton(onClick = {
                    val type = if (purpose == "payment") Kind.PAYMENT else Kind.CREDIT
                    if (purpose == "slip") onOpen("entry/${party.id}?type=credit&popHome=1")
                    else onOpen("entry/${party.id}?type=$type")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(party.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    val party by container.repo.observeParty(id).collectAsStateWithLifecycle(null)
    val entries by container.repo.observeEntries(id).collectAsStateWithLifecycle(emptyList())
    val sensitive by container.repo.observeSensitive(id).collectAsStateWithLifecycle(null)
    var deleteParty by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<String?>(null) }
    var remind by remember { mutableStateOf(false) }
    val current = party
    if (current == null) {
        SimpleMissing(onBack)
        return
    }
    val balance = entries.sumOf { if (it.type == Kind.CREDIT) it.amount else -it.amount }
    val tone = balanceTone(current.type, balance)
    val extra = LocalExtra.current
    val limit = effectiveCreditLimit(current.useCustomLimit, current.creditLimit, settings?.defaultCreditLimit ?: 5000)
    val daily = effectiveDailyLimit(current.useDailyLimit, current.dailyLimit, settings?.dailyLimitEnabled == true, settings?.defaultDailyLimit ?: 0)
    val left = remainingLimit(balance, limit)
    val over = balance > limit
    val lang = settings?.language ?: "bn"
    val shop = settings?.shopName?.ifBlank { stringResource(R.string.shop_fallback) } ?: ""
    val noApp = stringResource(R.string.remind_no_app)
    val noPhone = stringResource(R.string.remind_no_phone)
    val pdfFail = stringResource(R.string.statement_failed)

    fun reminder(): String = context.getString(
        R.string.remind_body,
        current.name,
        shop,
        formatTaka(abs(balance)),
    )

    fun sharePdf() {
        scope.launch {
            runCatching {
                val file = Files.cacheFile(context, "statement-${current.id.take(8)}.pdf")
                writeStatement(context, file, current, entries, shop, lang)
                val uri = Files.uriFor(context, file)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, context.getString(R.string.statement_share)))
            }.onFailure { context.toast(pdfFail) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(current.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back)) } },
            actions = { IconButton(onClick = { onOpen("edit?id=$id&face=0") }) { Text(stringResource(R.string.edit)) } },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PartyAvatar(current.name, current.photoPath, 64.dp)
                    Column {
                        Text(if (current.type == Kind.SUPPLIER) stringResource(R.string.supplier) else stringResource(R.string.customer), style = MaterialTheme.typography.labelLarge)
                        if (current.phone.isNotBlank()) Text(current.phone)
                        VerifyChip(current.verifyStatus)
                    }
                }
            }
            item {
                val color = when (tone) {
                    BalanceTone.GET -> extra.get
                    BalanceTone.GIVE -> extra.give
                    BalanceTone.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    when (tone) {
                        BalanceTone.GET -> stringResource(R.string.balance_get)
                        BalanceTone.GIVE -> stringResource(R.string.balance_give)
                        BalanceTone.SETTLED -> stringResource(R.string.balance_settled)
                    } + " " + formatTaka(abs(balance)),
                    color = color,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.limit_total_label, formatTaka(limit)))
                Text(
                    if (over) stringResource(R.string.limit_over, formatTaka(balance - limit))
                    else stringResource(R.string.remaining_limit, formatTaka(left)),
                    modifier = Modifier.tag("remaining_limit"),
                    color = if (over) extra.give else extra.get,
                )
                if (daily != null) Text(stringResource(R.string.daily_limit_label, formatTaka(daily)))
                LinearProgressIndicator(
                    progress = { (balance.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                if (over) Text(stringResource(R.string.over_limit_chip), color = extra.give)
                val faceOn = sensitive?.faceEmbedding != null
                Text(if (faceOn) stringResource(R.string.face_enrolled) else stringResource(R.string.face_not_enrolled))
                if (!sensitive?.nidNumber.isNullOrBlank()) Text(stringResource(R.string.nid_saved) + " " + maskNid(sensitive?.nidNumber.orEmpty()))
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(stringResource(R.string.credit), "add_credit") { onOpen("entry/$id?type=credit") }
                    ActionChip(stringResource(R.string.payment), "add_payment") { onOpen("entry/$id?type=payment") }
                    ActionChip(stringResource(R.string.remind), "remind") { remind = true }
                    ActionChip(stringResource(R.string.statement), "statement") { sharePdf() }
                    ActionChip(stringResource(R.string.verify), "verify") { onOpen("verify/$id") }
                    ActionChip(stringResource(R.string.face_enroll), "enroll_face") { onOpen("scan/face?partyId=$id") }
                    ActionChip(stringResource(R.string.nid), "scan_nid") { onOpen("scan/nid/$id") }
                    if (current.phone.isNotBlank()) {
                        ActionChip(stringResource(R.string.call), "call") {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${localBdPhone(current.phone)}")))
                        }
                    }
                }
            }
            if (entries.isEmpty()) item { EmptyHint(stringResource(R.string.entries_empty)) }
            items(entries, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (entry.type == Kind.CREDIT) stringResource(R.string.credit) else stringResource(R.string.payment), style = MaterialTheme.typography.titleSmall)
                        Text(Dates.format(entry.date, lang) + if (entry.note.isBlank()) "" else " · ${entry.note}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatTaka(entry.amount), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onOpen("entry/$id?type=${entry.type}&entryId=${entry.id}") }) { Text(stringResource(R.string.edit)) }
                    IconButton(onClick = { deleteEntry = entry.id }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                }
            }
            item {
                TextButton(onClick = { deleteParty = true }) { Text(stringResource(R.string.delete_party), color = extra.give) }
            }
        }
    }
    if (deleteParty) {
        ConfirmDialog(stringResource(R.string.delete_party), stringResource(R.string.delete_party_body), stringResource(R.string.delete), {
            scope.launch {
                container.repo.deleteParty(id)
                onBack()
            }
        }, { deleteParty = false })
    }
    deleteEntry?.let { entryId ->
        ConfirmDialog(stringResource(R.string.delete_entry), stringResource(R.string.delete_entry_body), stringResource(R.string.delete), {
            scope.launch { container.repo.deleteEntry(entryId) }
            deleteEntry = null
        }, { deleteEntry = null })
    }
    if (remind) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { remind = false },
            title = { Text(stringResource(R.string.remind)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(reminder())
                    BigButton(stringResource(R.string.remind_sms)) {
                        if (current.phone.isBlank()) context.toast(noPhone) else runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${localBdPhone(current.phone)}")).apply {
                                putExtra("sms_body", reminder())
                            })
                        }.onFailure { context.toast(noApp) }
                    }
                    BigButton(stringResource(R.string.remind_whatsapp)) {
                        val digits = normalizeBdPhone(current.phone)
                        if (digits.length < 11) context.toast(noPhone) else runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(reminder())}")))
                        }.onFailure { context.toast(noApp) }
                    }
                    val shareLabel = stringResource(R.string.remind_share)
                    BigOutlined(shareLabel) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reminder())
                        }, shareLabel))
                    }
                }
            },
            confirmButton = { TextButton({ remind = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun ActionChip(text: String, id: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(onClick = onClick, modifier = Modifier.tag(id)) { Text(text) }
}

@Composable
private fun VerifyChip(status: String) {
    val label = when (status) {
        Kind.VERIFY_OTP -> stringResource(R.string.badge_otp)
        Kind.VERIFY_CALL -> stringResource(R.string.badge_call)
        else -> return
    }
    Text(label, color = LocalExtra.current.get, style = MaterialTheme.typography.labelLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyEditScreen(id: String, face: Int, onSaved: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    val existing by container.repo.observeParty(id).collectAsStateWithLifecycle(null)
    var type by rememberSaveable { mutableStateOf(Kind.CUSTOMER) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var limitText by rememberSaveable { mutableStateOf("") }
    var dailyOn by rememberSaveable { mutableStateOf(false) }
    var dailyText by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf("") }
    var rememberFace by rememberSaveable { mutableStateOf(false) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = shot
        if (ok && uri != null) Files.decodeUri(context, uri)?.let { bmp ->
            photo = Files.saveJpeg(context, bmp, "photos", "tmp_${System.currentTimeMillis()}.jpg")
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Files.decodeUri(context, uri)?.let { bmp ->
            photo = Files.saveJpeg(context, bmp, "photos", "tmp_${System.currentTimeMillis()}.jpg")
        }
    }
    LaunchedEffect(existing?.id) {
        if (loaded) return@LaunchedEffect
        if (id.isNotBlank() && existing == null) return@LaunchedEffect
        existing?.let {
            type = it.type
            name = it.name
            phone = it.phone
            address = it.address
            note = it.note
            photo = it.photoPath
            if (it.useCustomLimit) limitText = it.creditLimit.toString()
            dailyOn = it.useDailyLimit
            if (it.useDailyLimit) dailyText = it.dailyLimit.toString()
        }
        if (name.isBlank()) name = PendingCapture.suggestedName ?: PendingCapture.slip?.name.orEmpty()
        if (photo.isBlank()) photo = PendingCapture.photoPath.orEmpty()
        loaded = true
    }
    DisposableEffect(Unit) { onDispose { if (face == 1) PendingCapture.clearFace() } }
    val needName = stringResource(R.string.required_name)
    val saved = stringResource(R.string.saved)
    Scaffold(topBar = { SimpleBar(if (id.isBlank()) stringResource(R.string.add_customer) else stringResource(R.string.edit), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == Kind.CUSTOMER, { type = Kind.CUSTOMER }, { Text(stringResource(R.string.customer)) })
                FilterChip(type == Kind.SUPPLIER, { type = Kind.SUPPLIER }, { Text(stringResource(R.string.supplier)) })
            }
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth().tag("party_name"), label = { Text(stringResource(R.string.party_name)) }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth().tag("party_phone"), label = { Text(stringResource(R.string.party_phone)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
            OutlinedTextField(address, { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.party_address)) })
            OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.party_note)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PartyAvatar(name.ifBlank { "?" }, photo, 56.dp)
                BigOutlined(stringResource(R.string.photo_camera), Modifier.weight(1f)) {
                    val uri = Files.captureUri(context)
                    shot = uri
                    camera.launch(uri)
                }
                BigOutlined(stringResource(R.string.scan_gallery), Modifier.weight(1f)) { gallery.launch("image/*") }
            }
            OutlinedTextField(
                limitText,
                { limitText = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth().tag("party_limit"),
                label = { Text(stringResource(R.string.party_limit)) },
                supportingText = { Text(stringResource(R.string.party_limit_help, formatTaka(settings?.defaultCreditLimit ?: 5000))) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.party_daily), modifier = Modifier.weight(1f))
                Switch(dailyOn, { dailyOn = it })
            }
            if (dailyOn) {
                OutlinedTextField(dailyText, { dailyText = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.party_daily_help)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (face == 1 && PendingCapture.faceEmbedding != null) {
                Text(stringResource(R.string.face_consent))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.face_remember), modifier = Modifier.weight(1f))
                    Switch(rememberFace, { rememberFace = it }, modifier = Modifier.tag("remember_face"))
                }
                Text(stringResource(R.string.face_local_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BigButton(stringResource(R.string.party_save), modifier = Modifier.tag("party_save").navigationBarsPadding()) {
                if (name.isBlank()) {
                    context.toast(needName)
                    return@BigButton
                }
                scope.launch {
                    val now = System.currentTimeMillis()
                    val partyId = id.ifBlank { UUID.randomUUID().toString() }
                    val previous = if (id.isBlank()) null else container.repo.getParty(id)
                    var storedPhoto = photo
                    if (storedPhoto.isNotBlank() && !storedPhoto.contains(partyId)) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(storedPhoto)
                        if (bmp != null) storedPhoto = Files.saveJpeg(context, bmp, "photos", "$partyId.jpg")
                    }
                    container.repo.saveParty(
                        PartyEntity(
                            id = partyId,
                            type = type,
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            note = note.trim(),
                            photoPath = storedPhoto,
                            useCustomLimit = limitText.isNotBlank(),
                            creditLimit = limitText.toLongOrNull() ?: 0,
                            useDailyLimit = dailyOn,
                            dailyLimit = dailyText.toLongOrNull() ?: 0,
                            verifyStatus = previous?.verifyStatus ?: Kind.VERIFY_NONE,
                            createdAt = previous?.createdAt ?: now,
                            updatedAt = now,
                        ),
                    )
                    val embedding = PendingCapture.faceEmbedding
                    if (rememberFace && embedding != null) {
                        val facePhoto = PendingCapture.photoPath?.let { path ->
                            android.graphics.BitmapFactory.decodeFile(path)?.let { bmp ->
                                Files.saveJpeg(context, bmp, "faces", "$partyId.jpg")
                            }
                        }.orEmpty()
                        val old = container.repo.getSensitive(partyId)
                        container.repo.saveSensitive(
                            SensitiveEntity(
                                partyId = partyId,
                                faceEmbedding = embedding.toBlob(),
                                facePhotoPath = facePhoto,
                                faceConsentAt = now,
                                faceModel = PendingCapture.faceModel.orEmpty(),
                                nidNumber = old?.nidNumber.orEmpty(),
                                nidName = old?.nidName.orEmpty(),
                                nidPhotoPath = old?.nidPhotoPath.orEmpty(),
                                updatedAt = now,
                            ),
                        )
                    }
                    context.toast(saved)
                    onSaved(partyId)
                }
            }
        }
    }
}

private fun writeStatement(
    context: android.content.Context,
    file: File,
    party: PartyEntity,
    entries: List<bd.robin.hisabkhata.data.EntryEntity>,
    shop: String,
    lang: String,
) {
    val sorted = entries.sortedWith(compareBy({ it.date }, { it.createdAt }))
    var running = 0L
    val rows = sorted.map { entry ->
        running += if (entry.type == Kind.CREDIT) entry.amount else -entry.amount
        StatementRow(
            date = Dates.format(entry.date, lang),
            note = entry.note,
            credit = if (entry.type == Kind.CREDIT) formatTaka(entry.amount) else "",
            payment = if (entry.type == Kind.PAYMENT) formatTaka(entry.amount) else "",
            balance = formatTaka(running),
        )
    }
    PdfStatement.write(
        context = context,
        file = file,
        shop = shop,
        customer = party.name,
        phone = party.phone,
        labels = StatementLabels(
            title = context.getString(R.string.statement_title),
            date = context.getString(R.string.statement_col_date),
            note = context.getString(R.string.statement_col_note),
            credit = context.getString(R.string.statement_col_credit),
            payment = context.getString(R.string.statement_col_payment),
            balance = context.getString(R.string.statement_col_balance),
            total = context.getString(R.string.statement_total_due),
            generated = context.getString(R.string.statement_generated, Dates.format(System.currentTimeMillis(), lang)),
            appName = context.getString(R.string.app_name),
        ),
        rows = rows,
        closing = formatTaka(running),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back)) }
        },
    )
}

@Composable
fun SimpleMissing(onBack: () -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text(stringResource(R.string.not_found))
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
