package bd.robin.hisabkhata.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.BuildConfig
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.AppSettings
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.data.Files
import bd.robin.hisabkhata.data.FirebaseGate
import bd.robin.hisabkhata.data.SyncOutcome
import bd.robin.hisabkhata.logic.BackupBundle
import bd.robin.hisabkhata.logic.BackupVersionException
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.PinHash
import bd.robin.hisabkhata.logic.ProfileBackup
import bd.robin.hisabkhata.logic.dropDigit
import bd.robin.hisabkhata.logic.pushDigit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    var includeSensitive by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupBundle?>(null) }
    var confirmOut by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.backup_done)
    val failed = stringResource(R.string.backup_failed)
    val newer = stringResource(R.string.backup_version)
    val restored = stringResource(R.string.backup_restored)
    val sampleDone = stringResource(R.string.sample_done)
    val erased = stringResource(R.string.saved)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { writeBackup(context, container, uri, includeSensitive) }
                .onSuccess { context.toast(saved) }
                .onFailure { context.toast(failed) }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty")
                val bundle = json.decodeFromString<BackupBundle>(text)
                if (bundle.version > 1) throw BackupVersionException()
                pending = bundle
            }.onFailure {
                context.toast(if (it is BackupVersionException) newer else failed)
            }
        }
    }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.more_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(settings?.shopName?.ifBlank { stringResource(R.string.shop_fallback) } ?: "", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (settings?.session == Kind.SESSION_GOOGLE) stringResource(R.string.signed_in_as, settings.accountEmail.ifBlank { settings.accountName })
                else stringResource(R.string.guest_mode),
            )
            BigButton(stringResource(R.string.profile), modifier = Modifier.tag("open_profile")) { onOpen("profile") }
            BigButton(stringResource(R.string.report_title), modifier = Modifier.tag("open_reports")) { onOpen("reports") }
            BigButton(stringResource(R.string.settings_title), modifier = Modifier.tag("open_settings")) { onOpen("settings") }
            Text(stringResource(R.string.data_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.backup_include_sensitive), modifier = Modifier.weight(1f))
                Switch(includeSensitive, { includeSensitive = it })
            }
            Text(stringResource(R.string.backup_sensitive_help), style = MaterialTheme.typography.bodySmall)
            BigOutlined(stringResource(R.string.backup_export)) {
                export.launch("hisab-khata-${LocalDate.now()}.json")
            }
            BigOutlined(stringResource(R.string.backup_share)) {
                scope.launch {
                    runCatching {
                        val file = Files.cacheFile(context, "hisab-khata-backup.json")
                        val bundle = backupBundle(container, includeSensitive)
                        file.writeText(json.encodeToString(bundle))
                        val uri = Files.uriFor(context, file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, context.getString(R.string.backup_share)))
                    }.onFailure { context.toast(failed) }
                }
            }
            BigOutlined(stringResource(R.string.backup_import)) { restore.launch(arrayOf("application/json", "text/*")) }
            BigOutlined(stringResource(R.string.sample_data), modifier = Modifier.tag("sample_data")) {
                scope.launch {
                    SampleSafe.seed(container)
                    context.toast(sampleDone)
                }
            }
            Text(stringResource(R.string.sync), style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    settings?.session != Kind.SESSION_GOOGLE -> stringResource(R.string.sync_off_guest)
                    !FirebaseGate.ready(context) -> stringResource(R.string.sync_off_firebase)
                    else -> stringResource(R.string.sync_now)
                },
            )
            BigButton(stringResource(R.string.sync_now), modifier = Modifier.tag("sync_now")) {
                scope.launch {
                    when (val outcome = container.sync.sync()) {
                        SyncOutcome.Ok -> context.toast(context.getString(R.string.sync_ok))
                        SyncOutcome.NotConfigured -> context.toast(context.getString(R.string.sync_off_firebase))
                        SyncOutcome.NeedGoogle -> context.toast(context.getString(R.string.sync_need_google))
                        is SyncOutcome.Failed -> context.toast(outcome.message)
                    }
                }
            }
            BigOutlined(stringResource(R.string.sign_out)) { confirmOut = true }
            TextButton(onClick = { confirmErase = true }) { Text(stringResource(R.string.reset_data), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.about_body, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    pending?.let { bundle ->
        ConfirmDialog(stringResource(R.string.backup_restore_title), stringResource(R.string.backup_restore_body), stringResource(R.string.save), {
            scope.launch {
                container.repo.replaceFrom(bundle)
                container.settings.update {
                    it.copy(
                        ownerName = bundle.profile.ownerName.ifBlank { it.ownerName },
                        shopName = bundle.profile.shopName.ifBlank { it.shopName },
                        phone = bundle.profile.phone.ifBlank { it.phone },
                        address = bundle.profile.address.ifBlank { it.address },
                        businessType = bundle.profile.businessType.ifBlank { it.businessType },
                        language = bundle.profile.language.ifBlank { it.language },
                        theme = bundle.profile.theme.ifBlank { it.theme },
                        defaultCreditLimit = bundle.profile.defaultCreditLimit,
                        limitMode = bundle.profile.limitMode,
                        dailyLimitEnabled = bundle.profile.dailyLimitEnabled,
                        defaultDailyLimit = bundle.profile.defaultDailyLimit,
                    )
                }
                context.toast(restored)
                pending = null
            }
        }, { pending = null })
    }
    if (confirmOut) {
        ConfirmDialog(stringResource(R.string.sign_out), stringResource(R.string.sign_out_body), stringResource(R.string.yes), {
            scope.launch {
                container.auth.signOut()
                container.settings.update { it.copy(session = Kind.SESSION_NONE, accountUid = "", accountEmail = "", accountName = "") }
                container.session.lock()
                confirmOut = false
            }
        }, { confirmOut = false })
    }
    if (confirmErase) {
        ConfirmDialog(stringResource(R.string.reset_data), stringResource(R.string.reset_data_body), stringResource(R.string.delete), {
            scope.launch {
                container.repo.eraseBusinessData()
                confirmErase = false
                context.toast(erased)
            }
        }, { confirmErase = false })
    }
}

private object SampleSafe {
    suspend fun seed(container: bd.robin.hisabkhata.data.AppContainer) {
        bd.robin.hisabkhata.data.SampleSeeder.seed(container.repo)
    }
}

private suspend fun backupBundle(container: bd.robin.hisabkhata.data.AppContainer, sensitive: Boolean): BackupBundle {
    val settings = container.settings.current()
    return container.repo.snapshot(sensitive).copy(
        profile = ProfileBackup(
            ownerName = settings.ownerName,
            shopName = settings.shopName,
            phone = settings.phone,
            address = settings.address,
            businessType = settings.businessType,
            language = settings.language,
            theme = settings.theme,
            defaultCreditLimit = settings.defaultCreditLimit,
            limitMode = settings.limitMode,
            dailyLimitEnabled = settings.dailyLimitEnabled,
            defaultDailyLimit = settings.defaultDailyLimit,
        ),
    )
}

private suspend fun writeBackup(context: android.content.Context, container: bd.robin.hisabkhata.data.AppContainer, uri: Uri, sensitive: Boolean) {
    val text = json.encodeToString(backupBundle(container, sensitive))
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("stream")
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    var owner by rememberSaveable { mutableStateOf("") }
    var shop by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var business by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(settings) {
        if (!loaded && settings != null) {
            owner = settings.ownerName
            shop = settings.shopName
            phone = settings.phone
            address = settings.address
            business = settings.businessType
            photo = settings.photoPath
            loaded = true
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = shot ?: return@rememberLauncherForActivityResult
        if (ok) Files.decodeUri(context, uri)?.let { photo = Files.saveJpeg(context, it, "photos", "profile.jpg") }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Files.decodeUri(context, uri)?.let { photo = Files.saveJpeg(context, it, "photos", "profile.jpg") }
    }
    val saved = stringResource(R.string.saved)
    Scaffold(topBar = { SimpleBar(stringResource(R.string.profile), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PartyAvatar(owner.ifBlank { shop.ifBlank { "?" } }, photo, 72.dp)
                Column {
                    TextButton(onClick = { val uri = Files.captureUri(context); shot = uri; camera.launch(uri) }) { Text(stringResource(R.string.photo_camera)) }
                    TextButton(onClick = { gallery.launch("image/*") }) { Text(stringResource(R.string.scan_gallery)) }
                }
            }
            OutlinedTextField(owner, { owner = it }, modifier = Modifier.fillMaxWidth().tag("profile_owner"), label = { Text(stringResource(R.string.profile_owner)) })
            OutlinedTextField(shop, { shop = it }, modifier = Modifier.fillMaxWidth().tag("profile_shop"), label = { Text(stringResource(R.string.profile_shop)) })
            OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.party_phone)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(address, { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.party_address)) })
            Text(stringResource(R.string.profile_business))
            Kind.businesses.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        FilterChip(business == key, { business = key }, { Text(businessLabel(key)) })
                    }
                }
            }
            BigButton(stringResource(R.string.save), modifier = Modifier.tag("profile_save")) {
                scope.launch {
                    container.settings.update {
                        it.copy(ownerName = owner.trim(), shopName = shop.trim(), phone = phone.trim(), address = address.trim(), businessType = business, photoPath = photo)
                    }
                    context.toast(saved)
                    onBack()
                }
            }
        }
    }
}

@Composable
private fun businessLabel(key: String): String = when (key) {
    "grocery" -> stringResource(R.string.business_grocery)
    "pharmacy" -> stringResource(R.string.business_pharmacy)
    "clothing" -> stringResource(R.string.business_clothing)
    "electronics" -> stringResource(R.string.business_electronics)
    "restaurant" -> stringResource(R.string.business_restaurant)
    "hardware" -> stringResource(R.string.business_hardware)
    "mobile" -> stringResource(R.string.business_mobile)
    else -> stringResource(R.string.business_other)
}

@Composable
fun SettingsScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings ?: AppSettings()
    var limit by rememberSaveable(settings.defaultCreditLimit) { mutableStateOf(settings.defaultCreditLimit.toString()) }
    var daily by rememberSaveable(settings.defaultDailyLimit) { mutableStateOf(settings.defaultDailyLimit.toString()) }
    val canBio = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.language != "en", {
                    scope.launch { switchLanguage(context, container, "bn") }
                }, { Text(stringResource(R.string.lang_bangla)) })
                FilterChip(settings.language == "en", {
                    scope.launch { switchLanguage(context, container, "en") }
                }, { Text(stringResource(R.string.lang_english)) })
            }
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.theme != Kind.THEME_DARK, {
                    scope.launch { container.settings.update { it.copy(theme = Kind.THEME_LIGHT) } }
                }, { Text(stringResource(R.string.theme_light)) })
                FilterChip(settings.theme == Kind.THEME_DARK, {
                    scope.launch { container.settings.update { it.copy(theme = Kind.THEME_DARK) } }
                }, { Text(stringResource(R.string.theme_dark)) })
            }
            Text(stringResource(R.string.settings_limits), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(limit, { limit = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth().tag("default_limit"), label = { Text(stringResource(R.string.default_limit)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (settings.limitMode == Kind.MODE_BLOCK) stringResource(R.string.mode_block) else stringResource(R.string.mode_warn), modifier = Modifier.weight(1f))
                Switch(settings.limitMode == Kind.MODE_BLOCK, {
                    scope.launch { container.settings.update { current -> current.copy(limitMode = if (it) Kind.MODE_BLOCK else Kind.MODE_WARN) } }
                }, modifier = Modifier.tag("limit_mode"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.daily_limit_setting), modifier = Modifier.weight(1f))
                Switch(settings.dailyLimitEnabled, {
                    scope.launch { container.settings.update { current -> current.copy(dailyLimitEnabled = it) } }
                })
            }
            OutlinedTextField(daily, { daily = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.daily_limit_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            BigButton(stringResource(R.string.save)) {
                scope.launch {
                    container.settings.update {
                        it.copy(
                            defaultCreditLimit = limit.toLongOrNull() ?: it.defaultCreditLimit,
                            defaultDailyLimit = daily.toLongOrNull() ?: it.defaultDailyLimit,
                        )
                    }
                    context.toast(context.getString(R.string.saved))
                }
            }
            Text(stringResource(R.string.security_title), style = MaterialTheme.typography.titleMedium)
            BigButton(if (settings.pinHash.isBlank()) stringResource(R.string.pin_set) else stringResource(R.string.pin_change), modifier = Modifier.tag("open_pin")) { onOpen("pin") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lock_on_leave), modifier = Modifier.weight(1f))
                Switch(settings.lockOnBackground, { scope.launch { container.settings.update { current -> current.copy(lockOnBackground = it) } } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.biometric), modifier = Modifier.weight(1f))
                Switch(settings.biometricEnabled, {
                    if (!canBio) context.toast(context.getString(R.string.biometric_unavailable))
                    else scope.launch { container.settings.update { current -> current.copy(biometricEnabled = it) } }
                })
            }
        }
    }
}

private suspend fun switchLanguage(context: android.content.Context, container: bd.robin.hisabkhata.data.AppContainer, code: String) {
    container.settings.update { it.copy(language = code) }
    container.settings.writeBootLanguage(code)
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    (context as? android.app.Activity)?.recreate()
}

@Composable
fun PinSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    var step by rememberSaveable { mutableStateOf("new") }
    var first by rememberSaveable { mutableStateOf("") }
    var digits by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(settings?.pinHash) {
        if (settings != null && step == "new" && first.isBlank() && settings.pinHash.isNotBlank() && digits.isBlank()) step = "old"
    }
    val title = when (step) {
        "old" -> stringResource(R.string.pin_current)
        "confirm" -> stringResource(R.string.pin_again)
        else -> stringResource(R.string.pin_new)
    }
    Scaffold(topBar = { SimpleBar(title, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("•".repeat(digits.length.coerceAtLeast(0)), style = MaterialTheme.typography.headlineMedium)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            Keypad(
                onDigit = { if (digits.length < 6) digits += it.toString() },
                onDelete = { digits = dropDigit(digits).let { if (it == "0") "" else it } },
                onClear = { digits = "" },
            )
            BigButton(stringResource(R.string.done), modifier = Modifier.tag("pin_done")) {
                if (digits.length < 4) {
                    message = context.getString(R.string.pin_too_short)
                    return@BigButton
                }
                when (step) {
                    "old" -> {
                        val ok = settings != null && PinHash.matches(digits, settings.pinSalt, settings.pinHash)
                        if (!ok) message = context.getString(R.string.pin_wrong) else {
                            step = "new"
                            digits = ""
                            message = ""
                        }
                    }
                    "new" -> {
                        first = digits
                        digits = ""
                        step = "confirm"
                        message = ""
                    }
                    else -> {
                        if (digits != first) {
                            message = context.getString(R.string.pin_mismatch)
                            digits = ""
                        } else {
                            scope.launch {
                                val salt = PinHash.newSalt()
                                val hash = withContext(Dispatchers.Default) { PinHash.hash(digits, salt) }
                                container.settings.update { it.copy(pinHash = hash, pinSalt = salt) }
                                container.session.unlock()
                                context.toast(context.getString(R.string.saved))
                                onBack()
                            }
                        }
                    }
                }
            }
            if (step == "new" && settings?.pinHash?.isNotBlank() == true) {
                TextButton(onClick = {
                    scope.launch {
                        container.settings.update { it.copy(pinHash = "", pinSalt = "", biometricEnabled = false) }
                        container.session.unlock()
                        onBack()
                    }
                }) { Text(stringResource(R.string.pin_remove)) }
            }
        }
    }
}
