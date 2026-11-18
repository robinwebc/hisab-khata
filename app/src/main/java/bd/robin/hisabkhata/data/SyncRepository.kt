package bd.robin.hisabkhata.data

import android.content.Context
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.entryCloudMap
import bd.robin.hisabkhata.logic.partyCloudMap
import bd.robin.hisabkhata.logic.profileCloudMap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

sealed interface SyncOutcome {
    data object Ok : SyncOutcome
    data object NotConfigured : SyncOutcome
    data object NeedGoogle : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

class SyncRepository(
    private val context: Context,
    private val repo: LedgerRepository,
    private val settings: SettingsRepository,
) {
    suspend fun sync(): SyncOutcome {
        if (!FirebaseGate.ready(context)) return SyncOutcome.NotConfigured
        val current = settings.current()
        if (current.session != Kind.SESSION_GOOGLE) return SyncOutcome.NeedGoogle
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return SyncOutcome.NeedGoogle
        return runCatching {
            val root = FirebaseFirestore.getInstance().collection("users").document(uid)
            pushDeletions(root, repo.listDeletions())
            pushParties(root, repo.listParties())
            pushEntries(root, repo.listEntries())
            pushCash(root, repo.listCash())
            pushProducts(root, repo.listProducts())
            root.collection("profile").document("main").set(
                profileCloudMap(
                    current.ownerName,
                    current.shopName,
                    current.phone,
                    current.address,
                    current.businessType,
                    System.currentTimeMillis(),
                ),
            ).await()
            pull(root)
            settings.update { it.copy(lastSyncAt = System.currentTimeMillis()) }
            SyncOutcome.Ok
        }.getOrElse { SyncOutcome.Failed(it.message ?: "sync") }
    }

    private suspend fun pushDeletions(
        root: com.google.firebase.firestore.DocumentReference,
        rows: List<DeletionEntity>,
    ) {
        rows.chunked(400).forEach { chunk ->
            val batch = root.firestore.batch()
            chunk.forEach { row ->
                batch.set(
                    root.collection("deletions").document(row.id),
                    mapOf("id" to row.id, "collection" to row.collection, "deletedAt" to row.deletedAt),
                )
                val collection = root.collection(row.collection).document(row.id)
                batch.delete(collection)
            }
            batch.commit().await()
        }
    }

    private suspend fun pushParties(root: com.google.firebase.firestore.DocumentReference, rows: List<PartyEntity>) {
        rows.chunked(400).forEach { chunk ->
            val batch = root.firestore.batch()
            chunk.forEach { row ->
                batch.set(
                    root.collection("parties").document(row.id),
                    partyCloudMap(
                        row.id, row.type, row.name, row.phone, row.address, row.note,
                        row.useCustomLimit, row.creditLimit, row.useDailyLimit, row.dailyLimit,
                        row.verifyStatus, row.createdAt, row.updatedAt,
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }
    }

    private suspend fun pushEntries(root: com.google.firebase.firestore.DocumentReference, rows: List<EntryEntity>) {
        rows.chunked(400).forEach { chunk ->
            val batch = root.firestore.batch()
            chunk.forEach { row ->
                batch.set(
                    root.collection("entries").document(row.id),
                    entryCloudMap(row.id, row.partyId, row.type, row.amount, row.note, row.date, row.createdAt, row.updatedAt),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }
    }

    private suspend fun pushCash(root: com.google.firebase.firestore.DocumentReference, rows: List<CashEntity>) {
        rows.chunked(400).forEach { chunk ->
            val batch = root.firestore.batch()
            chunk.forEach { row ->
                batch.set(
                    root.collection("cash").document(row.id),
                    mapOf(
                        "id" to row.id,
                        "type" to row.type,
                        "category" to row.category,
                        "amount" to row.amount,
                        "note" to row.note,
                        "date" to row.date,
                        "createdAt" to row.createdAt,
                        "updatedAt" to row.updatedAt,
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }
    }

    private suspend fun pushProducts(root: com.google.firebase.firestore.DocumentReference, rows: List<ProductEntity>) {
        rows.chunked(400).forEach { chunk ->
            val batch = root.firestore.batch()
            chunk.forEach { row ->
                batch.set(
                    root.collection("products").document(row.id),
                    mapOf(
                        "id" to row.id,
                        "name" to row.name,
                        "price" to row.price,
                        "stock" to row.stock,
                        "barcode" to row.barcode,
                        "note" to row.note,
                        "createdAt" to row.createdAt,
                        "updatedAt" to row.updatedAt,
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }
    }

    private suspend fun pull(root: com.google.firebase.firestore.DocumentReference) {
        val remoteDeletions = root.collection("deletions").get().await()
        for (doc in remoteDeletions.documents) {
            val id = doc.getString("id") ?: doc.id
            val collection = doc.getString("collection") ?: continue
            val whenDeleted = doc.getLong("deletedAt") ?: 0L
            val localUpdated = localUpdatedAt(id, collection)
            if (localUpdated == null || whenDeleted >= localUpdated) {
                repo.applyRemoteDeletion(id, collection)
            }
        }
        pullParties(root)
        pullEntries(root)
        pullCash(root)
        pullProducts(root)
    }

    private suspend fun localUpdatedAt(id: String, collection: String): Long? = when (collection) {
        "parties" -> repo.getParty(id)?.updatedAt
        "entries" -> repo.getEntry(id)?.updatedAt
        "cash" -> repo.listCash().firstOrNull { it.id == id }?.updatedAt
        "products" -> repo.getProduct(id)?.updatedAt
        else -> null
    }

    private suspend fun pullParties(root: com.google.firebase.firestore.DocumentReference) {
        val snap = root.collection("parties").get().await()
        for (doc in snap.documents) {
            val updated = doc.getLong("updatedAt") ?: 0L
            val existing = repo.getParty(doc.id)
            if (existing == null || updated > existing.updatedAt) {
                repo.applyRemoteParty(doc.toParty(existing))
            }
        }
    }

    private suspend fun pullEntries(root: com.google.firebase.firestore.DocumentReference) {
        val snap = root.collection("entries").get().await()
        for (doc in snap.documents) {
            val updated = doc.getLong("updatedAt") ?: 0L
            val existing = repo.getEntry(doc.id)
            if (existing == null || updated > existing.updatedAt) {
                val partyId = doc.getString("partyId") ?: continue
                if (repo.getParty(partyId) == null) continue
                repo.applyRemoteEntry(
                    EntryEntity(
                        id = doc.id,
                        partyId = partyId,
                        type = doc.getString("type") ?: Kind.CREDIT,
                        amount = doc.getLong("amount") ?: 0L,
                        note = doc.getString("note").orEmpty(),
                        date = doc.getLong("date") ?: updated,
                        createdAt = doc.getLong("createdAt") ?: updated,
                        updatedAt = updated,
                    ),
                )
            }
        }
    }

    private suspend fun pullCash(root: com.google.firebase.firestore.DocumentReference) {
        val snap = root.collection("cash").get().await()
        val existing = repo.listCash().associateBy { it.id }
        for (doc in snap.documents) {
            val updated = doc.getLong("updatedAt") ?: 0L
            val local = existing[doc.id]
            if (local == null || updated > local.updatedAt) {
                repo.applyRemoteCash(
                    CashEntity(
                        id = doc.id,
                        type = doc.getString("type") ?: Kind.EXPENSE,
                        category = doc.getString("category") ?: "other",
                        amount = doc.getLong("amount") ?: 0L,
                        note = doc.getString("note").orEmpty(),
                        date = doc.getLong("date") ?: updated,
                        createdAt = doc.getLong("createdAt") ?: updated,
                        updatedAt = updated,
                    ),
                )
            }
        }
    }

    private suspend fun pullProducts(root: com.google.firebase.firestore.DocumentReference) {
        val snap = root.collection("products").get().await()
        for (doc in snap.documents) {
            val updated = doc.getLong("updatedAt") ?: 0L
            val local = repo.getProduct(doc.id)
            if (local == null || updated > local.updatedAt) {
                repo.applyRemoteProduct(
                    ProductEntity(
                        id = doc.id,
                        name = doc.getString("name").orEmpty(),
                        price = doc.getLong("price") ?: 0L,
                        stock = doc.getLong("stock")?.toInt() ?: 0,
                        barcode = doc.getString("barcode").orEmpty(),
                        note = doc.getString("note").orEmpty(),
                        createdAt = doc.getLong("createdAt") ?: updated,
                        updatedAt = updated,
                    ),
                )
            }
        }
    }
}

private fun DocumentSnapshot.toParty(existing: PartyEntity?) = PartyEntity(
    id = id,
    type = getString("type") ?: Kind.CUSTOMER,
    name = getString("name").orEmpty(),
    phone = getString("phone").orEmpty(),
    address = getString("address").orEmpty(),
    note = getString("note").orEmpty(),
    photoPath = existing?.photoPath.orEmpty(),
    useCustomLimit = getBoolean("useCustomLimit") ?: false,
    creditLimit = getLong("creditLimit") ?: 0L,
    useDailyLimit = getBoolean("useDailyLimit") ?: false,
    dailyLimit = getLong("dailyLimit") ?: 0L,
    verifyStatus = getString("verifyStatus") ?: existing?.verifyStatus ?: Kind.VERIFY_NONE,
    createdAt = getLong("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis(),
    updatedAt = getLong("updatedAt") ?: 0L,
)
