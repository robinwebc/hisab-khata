package bd.robin.hisabkhata.data

import android.content.Context
import androidx.room.withTransaction
import bd.robin.hisabkhata.logic.BackupBundle
import bd.robin.hisabkhata.logic.CashBackup
import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.DeletionBackup
import bd.robin.hisabkhata.logic.EntryBackup
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.logic.PartyBackup
import bd.robin.hisabkhata.logic.ProductBackup
import bd.robin.hisabkhata.logic.SaleBackup
import bd.robin.hisabkhata.logic.SensitiveBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LedgerRepository(
    private val db: HisabDatabase,
    private val vault: VaultDatabase,
    private val context: Context,
) {
    fun observeParties(): Flow<List<PartyEntity>> = db.parties().observeAll()
    fun observeBalances(): Flow<List<BalanceRow>> = db.entries().observeBalances()
    fun observeRecent(): Flow<List<EntryEntity>> = db.entries().observeRecent()
    fun observeAllEntries(): Flow<List<EntryEntity>> = db.entries().observeAll()
    fun observeEntries(partyId: String): Flow<List<EntryEntity>> = db.entries().observeForParty(partyId)
    fun observeParty(id: String): Flow<PartyEntity?> = db.parties().observe(id)
    fun observeCash(): Flow<List<CashEntity>> = db.cash().observeAll()
    fun observeProducts(): Flow<List<ProductEntity>> = db.products().observeAll()
    fun observeProduct(id: String): Flow<ProductEntity?> = db.products().observe(id)
    fun observeSales(productId: String): Flow<List<SaleEntity>> = db.sales().observeForProduct(productId)
    fun observeSensitive(id: String): Flow<SensitiveEntity?> = vault.sensitive().observe(id)
    fun observeAllSensitive(): Flow<List<SensitiveEntity>> = vault.sensitive().observeAll()

    suspend fun getParty(id: String) = withIo { db.parties().get(id) }
    suspend fun getEntry(id: String) = withIo { db.entries().get(id) }
    suspend fun getSensitive(id: String) = withIo { vault.sensitive().get(id) }
    suspend fun getProduct(id: String) = withIo { db.products().get(id) }
    suspend fun productByBarcode(code: String) = withIo { db.products().byBarcode(code.trim()) }
    suspend fun balanceOf(partyId: String) = withIo { db.entries().balance(partyId) }
    suspend fun creditOnDay(partyId: String, dayMillis: Long): Long = withIo {
        val start = Dates.startOfDay(dayMillis)
        db.entries().creditBetween(partyId, start, Dates.endExclusive(Dates.localDate(start)))
    }

    suspend fun saveParty(party: PartyEntity) = withIo { db.parties().upsert(party) }

    suspend fun deleteParty(id: String) = withIo {
        val party = db.parties().get(id) ?: return@withIo
        val entryIds = db.entries().idsForParty(id)
        val sensitive = vault.sensitive().get(id)
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.parties().delete(id)
            db.deletions().upsert(DeletionEntity(id, "parties", now))
            entryIds.forEach { db.deletions().upsert(DeletionEntity(it, "entries", now)) }
        }
        vault.sensitive().delete(id)
        deleteFile(party.photoPath)
        deleteFile(sensitive?.facePhotoPath)
        deleteFile(sensitive?.nidPhotoPath)
    }

    suspend fun saveEntry(entry: EntryEntity) = withIo { db.entries().upsert(entry) }

    suspend fun deleteEntry(id: String) = withIo {
        db.withTransaction {
            db.entries().delete(id)
            db.deletions().upsert(DeletionEntity(id, "entries", System.currentTimeMillis()))
        }
    }

    suspend fun saveCash(entry: CashEntity) = withIo { db.cash().upsert(entry) }

    suspend fun deleteCash(id: String) = withIo {
        db.withTransaction {
            db.cash().delete(id)
            db.deletions().upsert(DeletionEntity(id, "cash", System.currentTimeMillis()))
        }
    }

    suspend fun saveProduct(product: ProductEntity) = withIo { db.products().upsert(product) }

    suspend fun deleteProduct(id: String) = withIo {
        db.withTransaction {
            db.products().delete(id)
            db.deletions().upsert(DeletionEntity(id, "products", System.currentTimeMillis()))
        }
    }

    suspend fun sell(productId: String, qty: Int, addCash: Boolean): String? = withIo {
        val product = db.products().get(productId) ?: return@withIo "missing"
        if (qty <= 0) return@withIo "qty"
        if (product.stock < qty) return@withIo "stock"
        val now = System.currentTimeMillis()
        val total = product.price * qty
        db.withTransaction {
            db.products().upsert(product.copy(stock = product.stock - qty, updatedAt = now))
            db.sales().upsert(
                SaleEntity(
                    id = UUID.randomUUID().toString(),
                    productId = product.id,
                    productName = product.name,
                    qty = qty,
                    unitPrice = product.price,
                    amount = total,
                    date = now,
                    createdAt = now,
                ),
            )
            if (addCash && total > 0) {
                db.cash().upsert(
                    CashEntity(
                        id = UUID.randomUUID().toString(),
                        type = Kind.INCOME,
                        category = "sale",
                        amount = total,
                        note = product.name,
                        date = Dates.startOfDay(now),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
        null
    }

    suspend fun saveSensitive(row: SensitiveEntity) = withIo { vault.sensitive().upsert(row) }

    suspend fun deleteFace(partyId: String) = withIo {
        val row = vault.sensitive().get(partyId) ?: return@withIo
        deleteFile(row.facePhotoPath)
        vault.sensitive().upsert(
            row.copy(
                faceEmbedding = null,
                facePhotoPath = "",
                faceConsentAt = null,
                faceModel = "",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteNid(partyId: String) = withIo {
        val row = vault.sensitive().get(partyId) ?: return@withIo
        deleteFile(row.nidPhotoPath)
        vault.sensitive().upsert(
            row.copy(
                nidNumber = "",
                nidName = "",
                nidPhotoPath = "",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun snapshot(includeSensitive: Boolean): BackupBundle = withIo {
        val sensitive = if (includeSensitive) {
            vault.sensitive().listAll().map { row ->
                SensitiveBackup(
                    partyId = row.partyId,
                    faceModel = row.faceModel,
                    faceEmbeddingB64 = row.faceEmbedding?.toBase64().orEmpty(),
                    faceConsentAt = row.faceConsentAt,
                    nidNumber = row.nidNumber,
                    nidName = row.nidName,
                )
            }
        } else {
            emptyList()
        }
        BackupBundle(
            exportedAt = System.currentTimeMillis(),
            parties = db.parties().listAll().map { it.toBackup() },
            entries = db.entries().listAll().map { it.toBackup() },
            cash = db.cash().listAll().map { it.toBackup() },
            products = db.products().listAll().map { it.toBackup() },
            sales = db.sales().listAll().map { it.toBackup() },
            deletions = db.deletions().listAll().map { DeletionBackup(it.id, it.collection, it.deletedAt) },
            sensitive = sensitive,
        )
    }

    suspend fun replaceFrom(bundle: BackupBundle) = withIo {
        db.withTransaction {
            db.entries().clear()
            db.sales().clear()
            db.cash().clear()
            db.products().clear()
            db.deletions().clear()
            db.parties().clear()
            db.parties().upsertAll(bundle.parties.map { it.toEntity() })
            db.entries().upsertAll(bundle.entries.map { it.toEntity() })
            db.cash().upsertAll(bundle.cash.map { it.toEntity() })
            db.products().upsertAll(bundle.products.map { it.toEntity() })
            db.sales().upsertAll(bundle.sales.map { it.toEntity() })
            db.deletions().upsertAll(bundle.deletions.map { DeletionEntity(it.id, it.collection, it.deletedAt) })
        }
        if (bundle.sensitive.isNotEmpty()) {
            vault.sensitive().clear()
            bundle.sensitive.forEach { item ->
                vault.sensitive().upsert(
                    SensitiveEntity(
                        partyId = item.partyId,
                        faceEmbedding = item.faceEmbeddingB64.fromBase64(),
                        facePhotoPath = "",
                        faceConsentAt = item.faceConsentAt,
                        faceModel = item.faceModel,
                        nidNumber = item.nidNumber,
                        nidName = item.nidName,
                        nidPhotoPath = "",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun eraseBusinessData() = withIo {
        val parties = db.parties().listAll()
        val sensitive = vault.sensitive().listAll()
        db.withTransaction {
            db.entries().clear()
            db.sales().clear()
            db.cash().clear()
            db.products().clear()
            db.deletions().clear()
            db.parties().clear()
        }
        vault.sensitive().clear()
        parties.forEach { deleteFile(it.photoPath) }
        sensitive.forEach {
            deleteFile(it.facePhotoPath)
            deleteFile(it.nidPhotoPath)
        }
    }

    suspend fun listParties() = withIo { db.parties().listAll() }
    suspend fun listEntries() = withIo { db.entries().listAll() }
    suspend fun listCash() = withIo { db.cash().listAll() }
    suspend fun listProducts() = withIo { db.products().listAll() }
    suspend fun listSales() = withIo { db.sales().listAll() }
    suspend fun listDeletions() = withIo { db.deletions().listAll() }

    suspend fun applyRemoteParty(party: PartyEntity) = saveParty(party)
    suspend fun applyRemoteEntry(entry: EntryEntity) = saveEntry(entry)
    suspend fun applyRemoteCash(entry: CashEntity) = saveCash(entry)
    suspend fun applyRemoteProduct(product: ProductEntity) = saveProduct(product)

    suspend fun applyRemoteDeletion(id: String, collection: String) = withIo {
        when (collection) {
            "parties" -> deleteParty(id)
            "entries" -> db.entries().delete(id)
            "cash" -> db.cash().delete(id)
            "products" -> db.products().delete(id)
        }
    }

    private suspend fun <T> withIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}

private fun PartyEntity.toBackup() = PartyBackup(
    id, type, name, phone, address, note, useCustomLimit, creditLimit,
    useDailyLimit, dailyLimit, verifyStatus, createdAt, updatedAt,
)

private fun PartyBackup.toEntity() = PartyEntity(
    id, type, name, phone, address, note, "", useCustomLimit, creditLimit,
    useDailyLimit, dailyLimit, verifyStatus, createdAt, updatedAt,
)

private fun EntryEntity.toBackup() = EntryBackup(id, partyId, type, amount, note, date, createdAt, updatedAt)
private fun EntryBackup.toEntity() = EntryEntity(id, partyId, type, amount, note, date, createdAt, updatedAt)
private fun CashEntity.toBackup() = CashBackup(id, type, category, amount, note, date, createdAt, updatedAt)
private fun CashBackup.toEntity() = CashEntity(id, type, category, amount, note, date, createdAt, updatedAt)
private fun ProductEntity.toBackup() = ProductBackup(id, name, price, stock, barcode, note, createdAt, updatedAt)
private fun ProductBackup.toEntity() = ProductEntity(id, name, price, stock, barcode, note, createdAt, updatedAt)
private fun SaleEntity.toBackup() = SaleBackup(id, productId, productName, qty, unitPrice, amount, date, createdAt)
private fun SaleBackup.toEntity() = SaleEntity(id, productId, productName, qty, unitPrice, amount, date, createdAt)
