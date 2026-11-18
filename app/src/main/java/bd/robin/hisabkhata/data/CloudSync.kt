package bd.robin.hisabkhata.data

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import bd.robin.hisabkhata.HisabApp
import bd.robin.hisabkhata.logic.CloudChoice
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.cloudChoice
import bd.robin.hisabkhata.logic.entryCloudMap
import bd.robin.hisabkhata.logic.partyCloudMap
import bd.robin.hisabkhata.logic.profileCloudMap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

object CloudDb {
    @Volatile private var ready = false

    fun firestore(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        if (!ready) {
            synchronized(this) {
                if (!ready) {
                    runCatching {
                        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(true)
                            .build()
                    }
                    ready = true
                }
            }
        }
        return db
    }
}

sealed interface SyncOutcome {
    data object Ok : SyncOutcome
    data object NotConfigured : SyncOutcome
    data object NeedGoogle : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

sealed interface SyncPhase {
    data object Idle : SyncPhase
    data object Syncing : SyncPhase
    data class Saved(val at: Long) : SyncPhase
    data class Error(val message: String) : SyncPhase
}

class SyncRepository(
    private val context: Context,
    private val repo: LedgerRepository,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()
    private val phaseState = MutableStateFlow<SyncPhase>(SyncPhase.Idle)
    val phase: StateFlow<SyncPhase> = phaseState

    suspend fun sync(): SyncOutcome = mutex.withLock {
        if (!FirebaseGate.ready(context)) return SyncOutcome.NotConfigured
        val current = settings.current()
        if (current.session != Kind.SESSION_GOOGLE || current.accountUid.isBlank()) return SyncOutcome.NeedGoogle
        val authUid = FirebaseAuth.getInstance().currentUser?.uid
        if (authUid == null || authUid != current.accountUid) return SyncOutcome.NeedGoogle
        if (repo.activeUid() != authUid) repo.bind(authUid)
        phaseState.value = SyncPhase.Syncing
        return runCatching {
            val root = CloudDb.firestore().collection("users").document(authUid)
            mergeParties(root)
            mergeEntries(root)
            mergeCash(root)
            mergeProducts(root)
            mergeSales(root)
            mergeProfile(root, settings.current())
            val at = System.currentTimeMillis()
            settings.update(notify = false) { it.copy(lastSyncAt = at) }
            phaseState.value = SyncPhase.Saved(at)
            SyncOutcome.Ok
        }.getOrElse { error ->
            val message = friendly(error)
            phaseState.value = SyncPhase.Error(message)
            SyncOutcome.Failed(message)
        }
    }

    private suspend fun mergeParties(root: DocumentReference) {
        merge(root, "parties", repo.listParties(), { it.id }, { it.updatedAt }, { row ->
            partyCloudMap(
                row.id, row.type, row.name, row.phone, row.address, row.note,
                row.useCustomLimit, row.creditLimit, row.useDailyLimit, row.dailyLimit,
                row.verifyStatus, row.createdAt, row.updatedAt,
            ) + liveFlags()
        }) { doc ->
            repo.applyRemoteParty(doc.toParty())
        }
    }

    private suspend fun mergeEntries(root: DocumentReference) {
        merge(root, "entries", repo.listEntries(), { it.id }, { it.updatedAt }, { row ->
            entryCloudMap(row.id, row.partyId, row.type, row.amount, row.note, row.date, row.createdAt, row.updatedAt) + liveFlags()
        }) { doc ->
            val partyId = doc.getString("partyId") ?: return@merge
            if (repo.getParty(partyId) != null) repo.applyRemoteEntry(doc.toEntry())
        }
    }

    private suspend fun mergeCash(root: DocumentReference) {
        merge(root, "cash", repo.listCash(), { it.id }, { it.updatedAt }, { row ->
            mapOf(
                "id" to row.id,
                "type" to row.type,
                "category" to row.category,
                "amount" to row.amount,
                "note" to row.note,
                "date" to row.date,
                "createdAt" to row.createdAt,
                "updatedAt" to row.updatedAt,
            ) + liveFlags()
        }) { doc -> repo.applyRemoteCash(doc.toCash()) }
    }

    private suspend fun mergeProducts(root: DocumentReference) {
        merge(root, "products", repo.listProducts(), { it.id }, { it.updatedAt }, { row ->
            mapOf(
                "id" to row.id,
                "name" to row.name,
                "price" to row.price,
                "stock" to (row.stock.toLong()),
                "barcode" to row.barcode,
                "note" to row.note,
                "createdAt" to row.createdAt,
                "updatedAt" to row.updatedAt,
            ) + liveFlags()
        }) { doc -> repo.applyRemoteProduct(doc.toProduct()) }
    }

    private suspend fun mergeSales(root: DocumentReference) {
        merge(root, "sales", repo.listSales(), { it.id }, { it.createdAt }, { row ->
            mapOf(
                "id" to row.id,
                "productId" to row.productId,
                "productName" to row.productName,
                "qty" to row.qty.toLong(),
                "unitPrice" to row.unitPrice,
                "amount" to row.amount,
                "date" to row.date,
                "createdAt" to row.createdAt,
                "updatedAt" to row.createdAt,
            ) + liveFlags()
        }) { doc -> repo.applyRemoteSale(doc.toSale()) }
    }

    private suspend fun <T> merge(
        root: DocumentReference,
        name: String,
        local: List<T>,
        idOf: (T) -> String,
        updatedOf: (T) -> Long,
        toMap: (T) -> Map<String, Any>,
        apply: suspend (DocumentSnapshot) -> Unit,
    ) {
        val remote = root.collection(name).get().await().documents.associateBy { it.id }
        val tombs = repo.listDeletions().filter { it.collection == name }.associateBy { it.id }
        val writes = mutableListOf<Pair<String, Map<String, Any>>>()
        val localById = local.associateBy(idOf)
        for (row in local) {
            val id = idOf(row)
            val stamp = updatedOf(row)
            val tomb = tombs[id]
            if (tomb != null && tomb.deletedAt >= stamp) {
                writes += id to soft(id, tomb.deletedAt)
                continue
            }
            val doc = remote[id]
            val choice = if (doc == null) {
                CloudChoice.KEEP_LOCAL
            } else {
                cloudChoice(stamp, doc.updatedStamp(), doc.deletedStamp())
            }
            if (choice == CloudChoice.KEEP_LOCAL) writes += id to toMap(row)
        }
        for (tomb in tombs.values) {
            if (tomb.id in localById) continue
            val doc = remote[tomb.id]
            if (doc == null || tomb.deletedAt >= maxOf(doc.updatedStamp(), doc.deletedStamp())) {
                writes += tomb.id to soft(tomb.id, tomb.deletedAt)
            }
        }
        commit(root.collection(name), writes)
        for (doc in remote.values) {
            val localRow = localById[doc.id]
            val tomb = tombs[doc.id]
            val localStamp = when {
                tomb != null && (localRow == null || tomb.deletedAt >= updatedOf(localRow)) -> tomb.deletedAt
                localRow != null -> updatedOf(localRow)
                else -> null
            }
            when (cloudChoice(localStamp, doc.updatedStamp(), doc.deletedStamp())) {
                CloudChoice.KEEP_LOCAL -> Unit
                CloudChoice.TOMBSTONE -> repo.applyRemoteDeletion(doc.id, name, doc.deletedStamp().coerceAtLeast(doc.updatedStamp()))
                CloudChoice.TAKE_REMOTE -> if (doc.deletedStamp() > 0L) {
                    repo.applyRemoteDeletion(doc.id, name, doc.deletedStamp())
                } else {
                    apply(doc)
                }
            }
        }
    }

    private suspend fun mergeProfile(root: DocumentReference, local: AppSettings) {
        val ref = root.collection("profile").document("shop")
        val remote = ref.get().await()
        if (shouldPushProfile(local, remote)) {
            val stamp = if (local.profileUpdatedAt == 0L) System.currentTimeMillis() else local.profileUpdatedAt
            ref.set(
                profileCloudMap(local.ownerName, local.shopName, local.phone, local.address, local.businessType, stamp) + mapOf(
                    "language" to local.language,
                    "theme" to local.theme,
                    "defaultCreditLimit" to local.defaultCreditLimit,
                    "limitMode" to local.limitMode,
                    "dailyLimitEnabled" to local.dailyLimitEnabled,
                    "defaultDailyLimit" to local.defaultDailyLimit,
                ),
                SetOptions.merge(),
            ).await()
            if (local.profileUpdatedAt != stamp) {
                settings.update(notify = false) { it.copy(profileUpdatedAt = stamp) }
            }
        } else if (remote.exists()) {
            val stamp = remote.getLong("updatedAt") ?: 0L
            settings.update(notify = false) {
                it.copy(
                    ownerName = remote.getString("ownerName") ?: it.ownerName,
                    shopName = remote.getString("shopName") ?: it.shopName,
                    phone = remote.getString("phone") ?: it.phone,
                    address = remote.getString("address") ?: it.address,
                    businessType = remote.getString("businessType") ?: it.businessType,
                    language = remote.getString("language") ?: it.language,
                    theme = remote.getString("theme") ?: it.theme,
                    defaultCreditLimit = remote.getLong("defaultCreditLimit") ?: it.defaultCreditLimit,
                    limitMode = remote.getString("limitMode") ?: it.limitMode,
                    dailyLimitEnabled = remote.getBoolean("dailyLimitEnabled") ?: it.dailyLimitEnabled,
                    defaultDailyLimit = remote.getLong("defaultDailyLimit") ?: it.defaultDailyLimit,
                    profileUpdatedAt = stamp,
                )
            }
        }
    }

    private fun shouldPushProfile(local: AppSettings, remote: DocumentSnapshot): Boolean {
        if (!remote.exists()) return true
        val remoteUpdated = remote.getLong("updatedAt") ?: 0L
        val localHas = local.shopName.isNotBlank() || local.ownerName.isNotBlank() || local.phone.isNotBlank() || local.address.isNotBlank()
        if (local.profileUpdatedAt < 0L) return false
        if (local.profileUpdatedAt == 0L && !localHas) return false
        val stamp = if (local.profileUpdatedAt == 0L) Long.MAX_VALUE / 4 else local.profileUpdatedAt
        return stamp >= remoteUpdated
    }

    private suspend fun commit(collection: com.google.firebase.firestore.CollectionReference, writes: List<Pair<String, Map<String, Any>>>) {
        writes.distinctBy { it.first }.chunked(400).forEach { chunk ->
            val batch = collection.firestore.batch()
            chunk.forEach { (id, data) -> batch.set(collection.document(id), data, SetOptions.merge()) }
            batch.commit().await()
        }
    }

    private fun liveFlags() = mapOf("deleted" to false, "deletedAt" to 0L)

    private fun soft(id: String, deletedAt: Long) = mapOf(
        "id" to id,
        "deleted" to true,
        "deletedAt" to deletedAt,
        "updatedAt" to deletedAt,
    )

    private fun friendly(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("PERMISSION_DENIED", true) ->
                "Firestore rules এই অ্যাকাউন্টকে লিখতে দিচ্ছে না।"
            raw.contains("NOT_FOUND", true) || raw.contains("UNAVAILABLE", true) || raw.contains("unavailable", true) ->
                "Cloud Firestore এখন পাওয়া যাচ্ছে না। ডেটা এই ফোনে আছে এবং পরে আবার সেভ হবে।"
            raw.isBlank() -> "সিঙ্ক হয়নি"
            else -> raw.take(180)
        }
    }
}

private fun DocumentSnapshot.updatedStamp(): Long = getLong("updatedAt") ?: getLong("createdAt") ?: 0L

private fun DocumentSnapshot.deletedStamp(): Long {
    val explicit = getLong("deletedAt") ?: 0L
    if (explicit > 0L) return explicit
    return if (getBoolean("deleted") == true) updatedStamp() else 0L
}

private fun DocumentSnapshot.toParty() = PartyEntity(
    id = id,
    type = getString("type") ?: Kind.CUSTOMER,
    name = getString("name").orEmpty(),
    phone = getString("phone").orEmpty(),
    address = getString("address").orEmpty(),
    note = getString("note").orEmpty(),
    photoPath = "",
    useCustomLimit = getBoolean("useCustomLimit") ?: false,
    creditLimit = getLong("creditLimit") ?: 0L,
    useDailyLimit = getBoolean("useDailyLimit") ?: false,
    dailyLimit = getLong("dailyLimit") ?: 0L,
    verifyStatus = getString("verifyStatus") ?: Kind.VERIFY_NONE,
    createdAt = getLong("createdAt") ?: updatedStamp(),
    updatedAt = updatedStamp(),
)

private fun DocumentSnapshot.toEntry() = EntryEntity(
    id = id,
    partyId = getString("partyId").orEmpty(),
    type = getString("type") ?: Kind.CREDIT,
    amount = getLong("amount") ?: 0L,
    note = getString("note").orEmpty(),
    date = getLong("date") ?: updatedStamp(),
    createdAt = getLong("createdAt") ?: updatedStamp(),
    updatedAt = updatedStamp(),
)

private fun DocumentSnapshot.toCash() = CashEntity(
    id = id,
    type = getString("type") ?: Kind.EXPENSE,
    category = getString("category") ?: "other",
    amount = getLong("amount") ?: 0L,
    note = getString("note").orEmpty(),
    date = getLong("date") ?: updatedStamp(),
    createdAt = getLong("createdAt") ?: updatedStamp(),
    updatedAt = updatedStamp(),
)

private fun DocumentSnapshot.toProduct() = ProductEntity(
    id = id,
    name = getString("name").orEmpty(),
    price = getLong("price") ?: 0L,
    stock = getLong("stock")?.toInt() ?: 0,
    barcode = getString("barcode").orEmpty(),
    note = getString("note").orEmpty(),
    createdAt = getLong("createdAt") ?: updatedStamp(),
    updatedAt = updatedStamp(),
)

private fun DocumentSnapshot.toSale() = SaleEntity(
    id = id,
    productId = getString("productId").orEmpty(),
    productName = getString("productName").orEmpty(),
    qty = getLong("qty")?.toInt() ?: 0,
    unitPrice = getLong("unitPrice") ?: 0L,
    amount = getLong("amount") ?: 0L,
    date = getLong("date") ?: updatedStamp(),
    createdAt = getLong("createdAt") ?: updatedStamp(),
)

class AccountGate(
    private val auth: AuthGateway,
    private val repo: LedgerRepository,
    private val settings: SettingsRepository,
    private val session: Session,
    private val sync: SyncRepository,
) {
    suspend fun onGoogle(profile: GoogleProfile) {
        val current = settings.current()
        val switching = current.accountUid.isNotBlank() && current.accountUid != profile.uid
        repo.bind(profile.uid)
        if (!switching) repo.claimLegacy(profile.uid)
        settings.update {
            val base = if (switching) {
                it.copy(
                    ownerName = profile.name,
                    shopName = "",
                    phone = "",
                    address = "",
                    businessType = "",
                    profileUpdatedAt = -1,
                )
            } else {
                it
            }
            base.copy(
                session = Kind.SESSION_GOOGLE,
                accountUid = profile.uid,
                accountName = profile.name.ifBlank { base.accountName },
                accountEmail = profile.email.ifBlank { base.accountEmail },
                ownerName = base.ownerName.ifBlank { profile.name },
                quickUnlockEnabled = if (current.accountUid == profile.uid) current.quickUnlockEnabled else true,
            )
        }
        session.unlock()
        sync.sync()
    }

    suspend fun signOut() {
        runCatching { sync.sync() }
        auth.signOut()
        repo.bind("")
        settings.update(notify = false) { it.copy(session = Kind.SESSION_NONE) }
        session.lock()
    }
}

class SyncCenter(
    private val app: Application,
    private val sync: SyncRepository,
    signals: SyncSignals,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            signals.ticks.debounce(1000).collect {
                if (sync.sync() is SyncOutcome.Failed) enqueue()
            }
        }
    }

    private fun enqueue() {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("nsedo-cloud-sync", ExistingWorkPolicy.KEEP, request)
    }
}

class CloudSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = (applicationContext as HisabApp).container.sync.sync()
        return if (outcome is SyncOutcome.Failed) Result.retry() else Result.success()
    }
}
