package bd.robin.hisabkhata.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bd.robin.hisabkhata.HisabApp
import bd.robin.hisabkhata.logic.BackupBundle
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed interface DriveResult {
    data object Ok : DriveResult
    data object NeedsConsent : DriveResult
    data class Failed(val message: String) : DriveResult
}

class DriveBackup(
    private val context: Context,
    private val repo: LedgerRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun backup(): DriveResult = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext DriveResult.NeedsConsent
        try {
            upload(token)
            DriveResult.Ok
        } catch (error: Throwable) {
            failed(error)
        }
    }

    suspend fun restore(): DriveResult = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext DriveResult.NeedsConsent
        try {
            val id = find(token) ?: return@withContext DriveResult.Failed("কোনো ব্যাকআপ পাওয়া যায়নি।")
            val secret = key(fetchRemote = true)
            val bundle = readZip(openSeal(secret, download(token, id)))
            repo.replaceFrom(bundle)
            settings.update(notify = false) { it.copy(offerDriveRestore = false, lastBackupError = "") }
            DriveResult.Ok
        } catch (error: Throwable) {
            failed(error)
        }
    }

    suspend fun ensureAccess(activity: Activity): DriveResult {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        val result = Identity.getAuthorizationClient(activity).authorize(request).await()
        if (result.accessToken.isNullOrBlank()) {
            val pending = result.pendingIntent ?: return DriveResult.Failed("Drive অনুমতি হয়নি।")
            activity.startIntentSenderForResult(pending.intentSender, CONSENT_CODE, null, 0, 0, 0)
            return DriveResult.NeedsConsent
        }
        return DriveResult.Ok
    }

    private suspend fun upload(token: String) {
        val current = settings.current()
        val bundle = repo.snapshot(current.backupIncludeSensitive)
        val zip = zip(json.encodeToString(bundle), current.backupIncludeSensitive)
        val secret = key(fetchRemote = true)
        publishKey(current.accountUid, secret)
        val sealed = seal(secret, zip)
        val existing = find(token)
        if (existing == null) create(token, sealed) else replace(token, existing, sealed)
        settings.update(notify = false) { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupError = "") }
    }

    private suspend fun accessToken(): String? {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        return runCatching {
            Identity.getAuthorizationClient(context).authorize(request).await().accessToken
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun key(fetchRemote: Boolean): ByteArray {
        val prefs = context.getSharedPreferences("nsedo_drive_key", Context.MODE_PRIVATE)
        prefs.getString("device", null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        if (fetchRemote) {
            val remote = remoteKey()
            if (remote != null) {
                prefs.edit().putString("device", Base64.encodeToString(remote, Base64.NO_WRAP)).apply()
                return remote
            }
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("device", Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }

    private suspend fun remoteKey(): ByteArray? {
        val uid = settings.current().accountUid
        if (uid.isBlank()) return null
        val snap = CloudDb.firestore().collection("users").document(uid).collection("profile").document("backupKey").get().await()
        val encoded = snap.getString("keyB64") ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private suspend fun publishKey(uid: String, secret: ByteArray) {
        if (uid.isBlank()) return
        val encoded = Base64.encodeToString(secret, Base64.NO_WRAP)
        CloudDb.firestore().collection("users").document(uid).collection("profile").document("backupKey")
            .set(mapOf("keyB64" to encoded, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    private fun zip(ledger: String, sensitive: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("ledger.json"))
            zip.write(ledger.toByteArray())
            zip.closeEntry()
            listOf(context.filesDir to "files/", File(context.cacheDir, "share") to "cache/").forEach { (root, prefix) ->
                if (!root.exists()) return@forEach
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val rel = file.relativeTo(root).invariantSeparatorsPath
                    if (!sensitive && (rel.startsWith("faces") || rel.startsWith("nid"))) return@forEach
                    zip.putNextEntry(ZipEntry(prefix + rel))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private fun readZip(bytes: ByteArray): BackupBundle {
        var bundle: BackupBundle? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zip.readBytes()
                    when {
                        entry.name == "ledger.json" -> bundle = json.decodeFromString(data.decodeToString())
                        entry.name.startsWith("files/") -> write(context.filesDir, entry.name.removePrefix("files/"), data)
                        entry.name.startsWith("cache/") -> write(File(context.cacheDir, "share"), entry.name.removePrefix("cache/"), data)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return bundle ?: error("ledger missing")
    }

    private fun write(root: File, rel: String, data: ByteArray) {
        val file = File(root, rel)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    private fun find(token: String): String? {
        val query = java.net.URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val text = http(token, "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name)&q=$query")
        return Regex(""""id"\s*:\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
    }

    private fun create(token: String, bytes: ByteArray) {
        val boundary = "nsedo${System.currentTimeMillis()}"
        val meta = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
        val body = ByteArrayOutputStream()
        body.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
        body.write("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
        body.write(bytes)
        body.write("\r\n--$boundary--".toByteArray())
        http(token, "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", "POST", body.toByteArray(), "multipart/related; boundary=$boundary")
    }

    private fun replace(token: String, id: String, bytes: ByteArray) {
        http(token, "https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media", "PATCH", bytes, "application/octet-stream")
    }

    private fun download(token: String, id: String): ByteArray =
        httpBytes(token, "https://www.googleapis.com/drive/v3/files/$id?alt=media")

    private fun http(token: String, endpoint: String, method: String = "GET", body: ByteArray? = null, type: String? = null): String =
        httpBytes(token, endpoint, method, body, type).decodeToString()

    private fun httpBytes(token: String, endpoint: String, method: String = "GET", body: ByteArray? = null, type: String? = null): ByteArray {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (type != null) conn.setRequestProperty("Content-Type", type)
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        if (code !in 200..299) error(bytes.decodeToString().ifBlank { "http $code" })
        return bytes
    }

    private suspend fun failed(error: Throwable): DriveResult {
        val message = explain(error)
        settings.update(notify = false) { it.copy(lastBackupError = message) }
        return DriveResult.Failed(message)
    }

    private fun explain(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("accessNotConfigured", true) || raw.contains("SERVICE_DISABLED", true) || raw.contains("drive.googleapis.com", true) ->
                "Google Drive API এখনো চালু নয়। Cloud console-এ Drive API enable করুন এবং OAuth-এ drive.appdata যোগ করুন।"
            raw.contains("403") || raw.contains("insufficient", true) ->
                "Drive অনুমতি বাকি। এখনই ব্যাকআপ চাপলে সম্মতি আসবে।"
            raw.isBlank() -> "ব্যাকআপ হয়নি"
            else -> raw.take(180)
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE_NAME = "nsedo-ledger.enc"
        const val CONSENT_CODE = 4102
    }
}

private fun seal(key: ByteArray, plain: ByteArray): ByteArray {
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    return iv + cipher.doFinal(plain)
}

private fun openSeal(key: ByteArray, sealed: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
    return cipher.doFinal(sealed.copyOfRange(12, sealed.size))
}

class DriveCenter(
    private val app: Application,
    settings: SettingsRepository,
    signals: SyncSignals,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            signals.ticks.debounce(20_000).collect {
                val wifi = settings.current().backupWifiOnly
                val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                    .setConstraints(net(wifi))
                    .setInitialDelay(20, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(app).enqueueUniqueWork("nsedo-drive-backup", ExistingWorkPolicy.KEEP, request)
            }
        }
        val daily = PeriodicWorkRequestBuilder<DriveBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(net(false))
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("nsedo-drive-daily", ExistingPeriodicWorkPolicy.KEEP, daily)
    }

    private fun net(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()
}

class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HisabApp
        if (app.container.settings.current().offerDriveRestore) return Result.success()
        val outcome = app.container.drive.backup()
        return if (outcome is DriveResult.Failed && !outcome.message.contains("অনুমতি") && !outcome.message.contains("API")) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
