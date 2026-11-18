package bd.robin.hisabkhata.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.BuildConfig
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Files
import bd.robin.hisabkhata.data.FirebaseGate
import bd.robin.hisabkhata.data.MlKit
import bd.robin.hisabkhata.data.PhoneOtp
import bd.robin.hisabkhata.data.SensitiveEntity
import bd.robin.hisabkhata.data.cropFace
import bd.robin.hisabkhata.data.toBlob
import bd.robin.hisabkhata.data.toFloats
import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.SlipParser
import bd.robin.hisabkhata.logic.bestFaceMatch
import bd.robin.hisabkhata.logic.formatTaka
import bd.robin.hisabkhata.logic.parseNid
import bd.robin.hisabkhata.logic.parseTaka
import bd.robin.hisabkhata.logic.toE164Bd
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

@Composable
fun SlipScanScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ocrFail = stringResource(R.string.scan_ocr_failed)
    val need = stringResource(R.string.scan_need_amount)
    fun applyText(text: String) {
        val draft = SlipParser.parse(text)
        raw = text
        name = draft.name.orEmpty()
        amount = draft.amount?.toString().orEmpty()
    }
    ScannerScaffold(
        title = stringResource(R.string.scan_slip_title),
        onBack = onBack,
        front = false,
        analyzer = null,
        onReady = { capture = it },
        onError = { error = it },
        error = error,
    ) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (raw.isNotBlank()) {
            Text(stringResource(R.string.scan_review), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth().tag("slip_name"), label = { Text(stringResource(R.string.party_name)) })
            OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth().tag("slip_amount"), label = { Text(stringResource(R.string.amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text(raw.take(400), style = MaterialTheme.typography.bodySmall)
            BigButton(stringResource(R.string.scan_use), modifier = Modifier.tag("slip_use")) {
                val taka = parseTaka(amount)
                if (taka == null || taka <= 0) {
                    context.toast(need)
                } else {
                    val draft = SlipParser.parse(raw).copy(amount = taka, name = name.ifBlank { null })
                    PendingCapture.slip = draft
                    PendingCapture.suggestedName = name.ifBlank { null }
                    onOpen("pick/slip")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(stringResource(R.string.scan_capture), Modifier.weight(1f).tag("slip_capture")) {
                val shot = capture
                if (shot == null) return@BigButton
                busy = true
                takePicture(context, shot, { bitmap ->
                    scope.launch {
                        val text = runCatching { withContext(Dispatchers.Default) { MlKit.readText(bitmap) } }.getOrElse {
                            context.toast(ocrFail)
                            ""
                        }
                        applyText(text.ifBlank { raw })
                        busy = false
                    }
                }, {
                    busy = false
                    context.toast(ocrFail)
                })
            }
            val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val bitmap = Files.decodeUri(context, uri) ?: return@rememberLauncherForActivityResult
                busy = true
                scope.launch {
                    val text = runCatching { MlKit.readText(bitmap) }.getOrDefault("")
                    if (text.isBlank()) context.toast(ocrFail)
                    applyText(text)
                    busy = false
                }
            }
            BigOutlined(stringResource(R.string.scan_gallery), Modifier.weight(1f)) { gallery.launch("image/*") }
        }
        BigOutlined(stringResource(R.string.scan_sample), Modifier.tag("sample_slip")) { applyText(SlipParser.SAMPLE.trim()) }
        BigOutlined(stringResource(R.string.scan_manual)) { onOpen("pick/credit") }
    }
}

@Composable
fun BarcodeScanScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var manual by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var addMode by remember { mutableStateOf(false) }
    var sell by remember { mutableStateOf<bd.robin.hisabkhata.data.ProductEntity?>(null) }
    val unknown = stringResource(R.string.barcode_unknown)
    val analyzer = remember {
        BarcodeAnalyzer { code ->
            manual = code
        }
    }
    fun lookup() {
        val code = manual.trim()
        if (code.isBlank()) return
        scope.launch {
            val product = container.repo.productByBarcode(code)
            if (addMode || product == null) {
                message = if (product == null) unknown else product.name
                if (product == null) {
                    PendingCapture.barcode = code
                    onOpen("product/edit?id=")
                }
            } else {
                sell = product
            }
        }
    }
    ScannerScaffold(
        title = stringResource(R.string.barcode_title),
        onBack = onBack,
        front = false,
        analyzer = analyzer,
        onReady = {},
        onError = { message = it },
        error = message.ifBlank { null },
    ) {
        Text(stringResource(R.string.barcode_hint))
        Row {
            androidx.compose.material3.FilterChip(!addMode, { addMode = false }, { Text(stringResource(R.string.barcode_mode_sell)) })
            androidx.compose.material3.FilterChip(addMode, { addMode = true }, { Text(stringResource(R.string.barcode_mode_add)) }, modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(manual, { manual = it }, modifier = Modifier.fillMaxWidth().tag("barcode_field"), label = { Text(stringResource(R.string.barcode_manual)) }, singleLine = true)
        BigButton(stringResource(R.string.barcode_lookup), modifier = Modifier.tag("barcode_lookup")) { lookup() }
        if (message.isNotBlank()) Text(message)
    }
    sell?.let { SellDialog(it) { sell = null } }
    LaunchedEffect(manual) {
        if (manual.length >= 6 && !addMode) {
            val product = container.repo.productByBarcode(manual.trim())
            if (product != null) sell = product
        }
    }
}

@Composable
fun FaceScanScreen(partyId: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val people by container.repo.observeAllSensitive().collectAsStateWithLifecycle(emptyList())
    val parties by container.repo.observeParties().collectAsStateWithLifecycle(emptyList())
    var consented by remember { mutableStateOf(partyId.isBlank()) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var front by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var matchId by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableStateOf(0) }
    val noFace = stringResource(R.string.face_no_face)
    val saved = stringResource(R.string.face_saved)
    val engineName = if (container.faces.usingMobileFaceNet) stringResource(R.string.face_model_tflite) else stringResource(R.string.face_model_local)
    fun matchProbe(embedding: FloatArray, path: String) {
        val gallery = people.mapNotNull { row ->
            val vector = row.faceEmbedding?.toFloats() ?: return@mapNotNull null
            if (row.faceModel != container.faces.modelName || vector.isEmpty()) return@mapNotNull null
            row.partyId to vector
        }
        val hit = bestFaceMatch(embedding, gallery, container.faces.threshold)
        if (hit == null) {
            message = context.getString(R.string.face_no_match)
            PendingCapture.photoPath = path
            PendingCapture.faceEmbedding = embedding
            PendingCapture.faceModel = container.faces.modelName
            matchId = null
        } else {
            score = (hit.score * 100).toInt().coerceIn(0, 100)
            message = context.getString(R.string.face_match)
            matchId = hit.partyId
        }
    }
    fun runSample() {
        scope.launch {
            busy = true
            val bitmap = sampleFaceBitmap()
            val embedding = withContext(Dispatchers.Default) { container.faces.embed(bitmap) }
            val known = container.repo.listParties()
            val target = known.firstOrNull { it.id == "sample-karim" } ?: known.firstOrNull { it.type == bd.robin.hisabkhata.logic.Kind.CUSTOMER }
            if (target == null) {
                message = context.getString(R.string.face_no_match)
                busy = false
                return@launch
            }
            val old = container.repo.getSensitive(target.id)
            val photo = Files.saveJpeg(context, bitmap, "faces", "${target.id}.jpg")
            container.repo.saveSensitive(
                SensitiveEntity(
                    partyId = target.id,
                    faceEmbedding = embedding.toBlob(),
                    facePhotoPath = photo,
                    faceConsentAt = old?.faceConsentAt ?: System.currentTimeMillis(),
                    faceModel = container.faces.modelName,
                    nidNumber = old?.nidNumber.orEmpty(),
                    nidName = old?.nidName.orEmpty(),
                    nidPhotoPath = old?.nidPhotoPath.orEmpty(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val gallery = listOf(target.id to embedding)
            val hit = bestFaceMatch(embedding, gallery, container.faces.threshold)
            score = ((hit?.score ?: 0f) * 100).toInt().coerceIn(0, 100)
            message = context.getString(R.string.face_match)
            matchId = target.id
            busy = false
        }
    }
    LaunchedEffect(partyId) {
        if (!BuildConfig.DEBUG || partyId.isNotBlank()) return@LaunchedEffect
        val shot = context.findActivity()?.intent?.getStringExtra("nsedo_shot")
        if (shot != "face") return@LaunchedEffect
        repeat(25) {
            if (container.repo.listParties().any { it.type == bd.robin.hisabkhata.logic.Kind.CUSTOMER }) {
                runSample()
                return@LaunchedEffect
            }
            delay(200)
        }
    }
    if (partyId.isNotBlank() && !consented) {
        Scaffold(topBar = { SimpleBar(stringResource(R.string.face_enroll_title), onBack) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).tag("face_register"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.face_consent), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.face_local_note))
                BigButton(stringResource(R.string.face_consent_yes), modifier = Modifier.tag("face_consent")) { consented = true }
                BigOutlined(stringResource(R.string.cancel)) { onBack() }
            }
        }
        return
    }
    ScannerScaffold(
        title = if (partyId.isBlank()) stringResource(R.string.face_scan_title) else stringResource(R.string.face_enroll_title),
        onBack = onBack,
        front = front,
        analyzer = null,
        onReady = { capture = it },
        onError = { message = it },
        error = message.ifBlank { null },
        extra = {
            TextButton(onClick = { front = !front }) { Text(stringResource(R.string.scan_flip)) }
        },
    ) {
        Text(stringResource(R.string.face_engine, engineName))
        Text(stringResource(R.string.face_local_note), style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message)
        BigButton(stringResource(R.string.scan_capture), modifier = Modifier.tag("face_capture")) {
            val shot = capture ?: return@BigButton
            busy = true
            takePicture(context, shot, { bitmap ->
                scope.launch {
                    val face = runCatching { MlKit.largestFace(bitmap) }.getOrNull()
                    if (face == null) {
                        message = noFace
                        busy = false
                        return@launch
                    }
                    val crop = cropFace(bitmap, face.boundingBox, face.headEulerAngleZ)
                    val embedding = withContext(Dispatchers.Default) { container.faces.embed(crop) }
                    val path = Files.saveJpeg(context, crop, "faces", "pending_${System.currentTimeMillis()}.jpg")
                    if (partyId.isNotBlank()) {
                        val old = container.repo.getSensitive(partyId)
                        container.repo.saveSensitive(
                            SensitiveEntity(
                                partyId = partyId,
                                faceEmbedding = embedding.toBlob(),
                                facePhotoPath = Files.saveJpeg(context, crop, "faces", "$partyId.jpg"),
                                faceConsentAt = System.currentTimeMillis(),
                                faceModel = container.faces.modelName,
                                nidNumber = old?.nidNumber.orEmpty(),
                                nidName = old?.nidName.orEmpty(),
                                nidPhotoPath = old?.nidPhotoPath.orEmpty(),
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        context.toast(saved)
                        onBack()
                    } else {
                        matchProbe(embedding, path)
                    }
                    busy = false
                }
            }, {
                busy = false
                message = noFace
            })
        }
        if (partyId.isBlank()) {
            BigOutlined(stringResource(R.string.face_sample), modifier = Modifier.tag("face_sample")) { runSample() }
        }
        if (matchId != null) {
            val found = parties.find { it.id == matchId }
            Text(found?.name ?: stringResource(R.string.face_match), style = MaterialTheme.typography.titleLarge, modifier = Modifier.tag("face_result_name"))
            Text(found?.phone.orEmpty())
            Text(stringResource(R.string.face_score, score), modifier = Modifier.tag("face_result"))
            BigButton(stringResource(R.string.face_open), modifier = Modifier.tag("face_open")) { onOpen("detail/$matchId") }
        } else if (message.isNotBlank() && partyId.isBlank()) {
            BigButton(stringResource(R.string.face_create), modifier = Modifier.tag("face_create")) { onOpen("edit?id=&face=1") }
        }
        if (partyId.isNotBlank()) {
            TextButton(onClick = {
                scope.launch {
                    container.repo.deleteFace(partyId)
                    context.toast(context.getString(R.string.face_removed))
                    onBack()
                }
            }) { Text(stringResource(R.string.face_delete)) }
        }
    }
}

@Composable
fun NidScanScreen(partyId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var message by remember { mutableStateOf("") }
    val saved = stringResource(R.string.saved)
    val missing = stringResource(R.string.nid_not_found)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val bitmap = uri?.let { Files.decodeUri(context, it) } ?: return@rememberLauncherForActivityResult
        photo = Files.saveJpeg(context, bitmap, "nid", "pending.jpg")
        scope.launch {
            val text = runCatching { MlKit.readText(bitmap) }.getOrDefault("")
            val draft = parseNid(text)
            if (draft.number != null) number = draft.number
            if (draft.name != null) name = draft.name
            if (draft.number == null) message = missing
        }
    }
    ScannerScaffold(stringResource(R.string.nid_title), onBack, false, null, { capture = it }, { message = it }, message.ifBlank { null }) {
        Text(stringResource(R.string.nid_help))
        Text(stringResource(R.string.nid_local_note), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.nid_name)) })
        OutlinedTextField(number, { number = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth().tag("nid_number"), label = { Text(stringResource(R.string.nid_number)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (message.isNotBlank()) Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(stringResource(R.string.scan_capture), Modifier.weight(1f).tag("nid_capture")) {
                val shot = capture ?: return@BigButton
                takePicture(context, shot, { bitmap ->
                    photo = Files.saveJpeg(context, bitmap, "nid", "pending.jpg")
                    scope.launch {
                        val text = runCatching { MlKit.readText(bitmap) }.getOrDefault("")
                        val draft = parseNid(text)
                        if (draft.number != null) number = draft.number else message = missing
                        if (draft.name != null) name = draft.name
                    }
                }, { message = missing })
            }
            BigOutlined(stringResource(R.string.scan_gallery), Modifier.weight(1f)) { gallery.launch("image/*") }
        }
        BigButton(stringResource(R.string.nid_save), modifier = Modifier.tag("nid_save")) {
            scope.launch {
                val old = container.repo.getSensitive(partyId)
                val stored = if (photo.isBlank()) old?.nidPhotoPath.orEmpty() else {
                    val bmp = android.graphics.BitmapFactory.decodeFile(photo)
                    if (bmp != null) Files.saveJpeg(context, bmp, "nid", "$partyId.jpg") else photo
                }
                container.repo.saveSensitive(
                    SensitiveEntity(
                        partyId = partyId,
                        faceEmbedding = old?.faceEmbedding,
                        facePhotoPath = old?.facePhotoPath.orEmpty(),
                        faceConsentAt = old?.faceConsentAt,
                        faceModel = old?.faceModel.orEmpty(),
                        nidNumber = number.trim(),
                        nidName = name.trim(),
                        nidPhotoPath = stored,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                context.toast(saved)
                onBack()
            }
        }
        TextButton(onClick = { scope.launch { container.repo.deleteNid(partyId); onBack() } }) {
            Text(stringResource(R.string.nid_delete))
        }
    }
}

@Composable
fun VerifyScreen(partyId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val party by container.repo.observeParty(partyId).collectAsStateWithLifecycle(null)
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val current = party
    val otp = remember { PhoneOtp(context as android.app.Activity) }
    val unavailable = stringResource(R.string.verify_otp_unavailable)
    val invalid = stringResource(R.string.verify_phone_invalid)
    val ok = stringResource(R.string.otp_ok)
    val sentText = stringResource(R.string.otp_sent)
    if (current == null) {
        SimpleMissing(onBack)
        return
    }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.verify_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(current.name, style = MaterialTheme.typography.titleLarge)
            Text(current.phone.ifBlank { stringResource(R.string.remind_no_phone) })
            Text(stringResource(R.string.verify_call))
            if (current.phone.isNotBlank()) {
                BigOutlined(stringResource(R.string.call)) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${bd.robin.hisabkhata.logic.localBdPhone(current.phone)}")))
                }
            }
            BigButton(stringResource(R.string.verify_call_done), modifier = Modifier.tag("verify_call")) {
                scope.launch {
                    container.repo.saveParty(current.copy(verifyStatus = Kind.VERIFY_CALL, updatedAt = System.currentTimeMillis()))
                    context.toast(ok)
                    onBack()
                }
            }
            BigOutlined(stringResource(R.string.verify_otp), modifier = Modifier.tag("verify_otp")) {
                if (!FirebaseGate.ready(context)) {
                    message = unavailable
                    return@BigOutlined
                }
                val e164 = toE164Bd(current.phone)
                if (e164 == null) {
                    message = invalid
                    return@BigOutlined
                }
                otp.send(e164, { sent = true; message = sentText }, {
                    scope.launch {
                        container.repo.saveParty(current.copy(verifyStatus = Kind.VERIFY_OTP, updatedAt = System.currentTimeMillis()))
                        context.toast(ok)
                        onBack()
                    }
                }, { message = it })
            }
            if (sent) {
                OutlinedTextField(code, { code = it.filter { ch -> ch.isDigit() }.take(6) }, modifier = Modifier.fillMaxWidth().tag("otp_code"), label = { Text(stringResource(R.string.verify_code)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                BigButton(stringResource(R.string.verify_confirm)) {
                    scope.launch {
                        val success = runCatching { otp.confirm(code) }.getOrDefault(false)
                        if (success) {
                            container.repo.saveParty(current.copy(verifyStatus = Kind.VERIFY_OTP, updatedAt = System.currentTimeMillis()))
                            context.toast(ok)
                            onBack()
                        } else message = context.getString(R.string.otp_failed)
                    }
                }
            }
            if (message.isNotBlank()) Text(message)
            if (current.verifyStatus != Kind.VERIFY_NONE) {
                TextButton(onClick = {
                    scope.launch {
                        container.repo.saveParty(current.copy(verifyStatus = Kind.VERIFY_NONE, updatedAt = System.currentTimeMillis()))
                        onBack()
                    }
                }) { Text(stringResource(R.string.verify_clear)) }
            }
        }
    }
}

private fun sampleFaceBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val skin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(196, 146, 110) }
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 32, 28) }
    canvas.drawOval(30f, 16f, 130f, 140f, skin)
    canvas.drawCircle(62f, 70f, 6f, ink)
    canvas.drawCircle(98f, 70f, 6f, ink)
    canvas.drawArc(58f, 88f, 102f, 120f, 10f, 160f, false, ink.apply { style = Paint.Style.STROKE; strokeWidth = 3f })
    canvas.drawRect(48f, 150f, 112f, 200f, skin)
    return bitmap
}

@Composable
private fun ScannerScaffold(
    title: String,
    onBack: () -> Unit,
    front: Boolean,
    analyzer: ImageAnalysis.Analyzer?,
    onReady: (ImageCapture) -> Unit,
    onError: (String) -> Unit,
    error: String?,
    extra: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    val denied = stringResource(R.string.scan_camera_denied)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.CAMERA) }
    Scaffold(topBar = { SimpleBar(title, onBack) }, modifier = Modifier.tag("scanner_root")) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (granted) {
                CameraPane(front, analyzer, onReady, onError, Modifier.fillMaxWidth().height(240.dp).tag("camera_preview"))
            } else {
                Text(denied)
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.scan_open_settings)) }
            }
            if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            extra()
            content()
        }
    }
}

@Composable
internal fun CameraPane(
    front: Boolean,
    analyzer: ImageAnalysis.Analyzer?,
    onReady: (ImageCapture) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val fail = stringResource(R.string.scan_camera_error)
    AndroidView(factory = { previewView }, modifier = modifier)
    LaunchedEffect(front, analyzer) {
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            if (analyzer != null) {
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            }
            onReady(capture)
        }.onFailure { onError(fail) }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

internal fun takePicture(context: android.content.Context, capture: ImageCapture, onBitmap: (android.graphics.Bitmap) -> Unit, onError: () -> Unit) {
    val file = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
    capture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bitmap = Files.decodeRotatedFile(file)
                if (bitmap != null) onBitmap(bitmap) else onError()
            }

            override fun onError(exception: ImageCaptureException) = onError()
        },
    )
}

@OptIn(ExperimentalGetImage::class)
internal class BarcodeAnalyzer(private val gapMs: Long = 1500, val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()
    private var last = ""
    private var lastAt = 0L
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstNotNullOfOrNull { it.rawValue } ?: return@addOnSuccessListener
                val now = System.currentTimeMillis()
                if (value == last && now - lastAt < gapMs) return@addOnSuccessListener
                last = value
                lastAt = now
                onCode(value)
            }
            .addOnCompleteListener { image.close() }
    }
}
