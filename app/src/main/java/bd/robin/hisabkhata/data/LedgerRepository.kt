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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LedgerRepository(
    private val db: HisabDatabase,
    private val vault: VaultDatabase,
    private val context: Context,
    private val signals: SyncSignals,
) {
    private val uid = MutableStateFlow("")
    private val business = MutableStateFlow("main")

    fun bind(userId: String) {
        uid.value = userId
    }

    fun bindBusiness(id: String) {
        business.value = id.ifBlank { "main" }
    }

    fun activeBusiness(): String = business.value

    fun activeUid(): String = uid.value

    fun observeParties(): Flow<List<PartyEntity>> = inBusiness(scopedList { db.parties().observeAll(it) }) { it.businessId }
    fun observeBalances(): Flow<List<BalanceRow>> = observeAllEntries().let { entries ->
        combine(entries, business) { rows, _ ->
            rows.groupBy { it.partyId }.map { (id, list) ->
                BalanceRow(id, list.sumOf { if (it.type == Kind.CREDIT) it.amount else -it.amount })
            }
        }
    }
    fun observeRecent(): Flow<List<EntryEntity>> = inBusiness(scopedList { db.entries().observeRecent(it) }) { it.businessId }
    fun observeAllEntries(): Flow<List<EntryEntity>> = inBusiness(scopedList { db.entries().observeAll(it) }) { it.businessId }
    fun observeEntries(partyId: String): Flow<List<EntryEntity>> = inBusiness(scopedList { db.entries().observeForParty(partyId, it) }) { it.businessId }
    fun observeParty(id: String): Flow<PartyEntity?> = scopedOne { db.parties().observe(id, it) }
    fun observeCash(): Flow<List<CashEntity>> = inBusiness(scopedList { db.cash().observeAll(it) }) { it.businessId }
    fun observeProducts(): Flow<List<ProductEntity>> = inBusiness(scopedList { db.products().observeAll(it) }) { it.businessId }
    fun observeProduct(id: String): Flow<ProductEntity?> = scopedOne { db.products().observe(id, it) }
    fun observeSales(productId: String): Flow<List<SaleEntity>> = inBusiness(scopedList { db.sales().observeForProduct(productId, it) }) { it.businessId }
    fun observeSensitive(id: String): Flow<SensitiveEntity?> = vault.sensitive().observe(id)
    fun observeAllSensitive(): Flow<List<SensitiveEntity>> = vault.sensitive().observeAll()

    suspend fun getParty(id: String) = withIo { db.parties().get(id, uid.value) }
    suspend fun getEntry(id: String) = withIo { db.entries().get(id, uid.value) }
    suspend fun getSensitive(id: String) = withIo { vault.sensitive().get(id) }
    suspend fun getProduct(id: String) = withIo { db.products().get(id, uid.value) }
    suspend fun productByBarcode(code: String) = withIo { db.products().byBarcode(code.trim(), uid.value) }
    suspend fun balanceOf(partyId: String) = withIo { db.entries().balance(partyId, uid.value) }
    suspend fun creditOnDay(partyId: String, dayMillis: Long): Long = withIo {
        val start = Dates.startOfDay(dayMillis)
        db.entries().creditBetween(partyId, uid.value, start, Dates.endExclusive(Dates.localDate(start)))
    }

    suspend fun claimLegacy(userId: String) = withIo {
        if (userId.isBlank()) return@withIo
        db.withTransaction {
            db.parties().claim(userId)
            db.entries().claim(userId)
            db.cash().claim(userId)
            db.products().claim(userId)
            db.sales().claim(userId)
            db.deletions().claim(userId)
        }
    }

    suspend fun saveParty(party: PartyEntity) = withIo {
        val id = uid.value
        if (id.isBlank()) return@withIo
        db.parties().upsert(party.copy(ownerUid = id, businessId = party.businessId.ifBlank { business.value }))
        changed()
    }

    suspend fun deleteParty(id: String) = withIo {
        val user = uid.value
        val party = db.parties().get(id, user) ?: return@withIo
        val entryIds = db.entries().idsForParty(id, user)
        val sensitive = vault.sensitive().get(id)
        val now = System.currentTimeMillis()
        db.withTransaction {
            entryIds.forEach { db.deletions().upsert(DeletionEntity(it, "entries", now, user)) }
            db.deletions().upsert(DeletionEntity(id, "parties", now, user))
            db.parties().delete(id, user)
        }
        vault.sensitive().delete(id)
        deleteFile(party.photoPath)
        deleteFile(sensitive?.facePhotoPath)
        deleteFile(sensitive?.nidPhotoPath)
        changed()
    }

    suspend fun saveEntry(entry: EntryEntity) = withIo {
        val id = uid.value
        if (id.isBlank()) return@withIo
        db.entries().upsert(entry.copy(ownerUid = id, businessId = entry.businessId.ifBlank { business.value }))
        changed()
    }

    suspend fun deleteEntry(id: String) = withIo {
        val user = uid.value
        db.withTransaction {
            db.entries().delete(id, user)
            db.deletions().upsert(DeletionEntity(id, "entries", System.currentTimeMillis(), user))
        }
        changed()
    }

    suspend fun saveCash(entry: CashEntity) = withIo {
        val id = uid.value
        if (id.isBlank()) return@withIo
        db.cash().upsert(entry.copy(ownerUid = id, businessId = entry.businessId.ifBlank { business.value }))
        changed()
    }

    suspend fun deleteCash(id: String) = withIo {
        val user = uid.value
        db.withTransaction {
            db.cash().delete(id, user)
            db.deletions().upsert(DeletionEntity(id, "cash", System.currentTimeMillis(), user))
        }
        changed()
    }

    suspend fun saveProduct(product: ProductEntity) = withIo {
        val id = uid.value
        if (id.isBlank()) return@withIo
        db.products().upsert(product.copy(ownerUid = id, businessId = product.businessId.ifBlank { business.value }))
        changed()
    }

    suspend fun deleteProduct(id: String) = withIo {
        val user = uid.value
        db.withTransaction {
            db.products().delete(id, user)
            db.deletions().upsert(DeletionEntity(id, "products", System.currentTimeMillis(), user))
        }
        changed()
    }

    suspend fun sell(productId: String, qty: Int, addCash: Boolean): String? = withIo {
        val user = uid.value
        if (user.isBlank()) return@withIo "missing"
        val product = db.products().get(productId, user) ?: return@withIo "missing"
        if (qty <= 0) return@withIo "qty"
        if (product.stock < qty) return@withIo "stock"
        val now = System.currentTimeMillis()
        val total = product.price * qty
        db.withTransaction {
            db.products().upsert(product.copy(stock = product.stock - qty, updatedAt = now, ownerUid = user))
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
                    ownerUid = user,
                    businessId = business.value,
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
                        ownerUid = user,
                        businessId = business.value,
                    ),
                )
            }
        }
        changed()
        null
    }

    suspend fun checkout(lines: List<Pair<String, Int>>, partyId: String?, asCredit: Boolean): String? = withIo {
        val user = uid.value
        if (user.isBlank()) return@withIo "missing"
        if (lines.isEmpty()) return@withIo "empty"
        val now = System.currentTimeMillis()
        val prepared = lines.map { (id, qty) ->
            val product = db.products().get(id, user) ?: return@withIo "missing"
            if (qty <= 0) return@withIo "qty"
            if (product.stock < qty) return@withIo "stock"
            product to qty
        }
        var total = 0L
        val note = prepared.joinToString(", ") { (product, qty) -> "${product.name} x$qty" }
        db.withTransaction {
            prepared.forEach { (product, qty) ->
                val line = product.price * qty
                total += line
                db.products().upsert(product.copy(stock = product.stock - qty, updatedAt = now, ownerUid = user))
                db.sales().upsert(
                    SaleEntity(
                        id = UUID.randomUUID().toString(),
                        productId = product.id,
                        productName = product.name,
                        qty = qty,
                        unitPrice = product.price,
                        amount = line,
                        date = now,
                        createdAt = now,
                        ownerUid = user,
                        businessId = business.value,
                    ),
                )
            }
            if (asCredit && !partyId.isNullOrBlank()) {
                db.entries().upsert(
                    EntryEntity(
                        id = UUID.randomUUID().toString(),
                        partyId = partyId,
                        type = Kind.CREDIT,
                        amount = total,
                        note = note,
                        date = Dates.startOfDay(now),
                        createdAt = now,
                        updatedAt = now,
                        ownerUid = user,
                        businessId = business.value,
                    ),
                )
            } else if (total > 0) {
                db.cash().upsert(
                    CashEntity(
                        id = UUID.randomUUID().toString(),
                        type = Kind.INCOME,
                        category = "sale",
                        amount = total,
                        note = note,
                        date = Dates.startOfDay(now),
                        createdAt = now,
                        updatedAt = now,
                        ownerUid = user,
                        businessId = business.value,
                    ),
                )
            }
        }
        changed()
        null
    }

    suspend fun recordSlipSale(amount: Long, note: String, partyId: String?, asCredit: Boolean) = withIo {
        val user = uid.value
        if (user.isBlank() || amount <= 0) return@withIo
        val now = System.currentTimeMillis()
        if (asCredit && !partyId.isNullOrBlank()) {
            db.entries().upsert(
                EntryEntity(
                    id = UUID.randomUUID().toString(),
                    partyId = partyId,
                    type = Kind.CREDIT,
                    amount = amount,
                    note = note,
                    date = Dates.startOfDay(now),
                    createdAt = now,
                    updatedAt = now,
                    ownerUid = user,
                    businessId = business.value,
                ),
            )
        } else {
            db.cash().upsert(
                CashEntity(
                    id = UUID.randomUUID().toString(),
                    type = Kind.INCOME,
                    category = "sale",
                    amount = amount,
                    note = note,
                    date = Dates.startOfDay(now),
                    createdAt = now,
                    updatedAt = now,
                    ownerUid = user,
                    businessId = business.value,
                ),
            )
        }
        changed()
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
        val user = uid.value
        val sensitive = if (includeSensitive) {
            vault.sensitive().listAll().map { row ->
                SensitiveBackup(
                    partyId = row.partyId,
                    faceModel = row.faceModel,
                    faceEmbeddingB64 = row.faceEmbedding?.toBase64().orEmpty(),
                    faceConsentAt = row.faceConsentAt,
                    nidNumber = row.nidNumber,
                    nidName = row.nidName,
                    facePhotoRel = relOf(row.facePhotoPath),
                    nidPhotoRel = relOf(row.nidPhotoPath),
                )
            }
        } else {
            emptyList()
        }
        BackupBundle(
            exportedAt = System.currentTimeMillis(),
            parties = db.parties().listAll(user).map { it.toBackup() },
            entries = db.entries().listAll(user).map { it.toBackup() },
            cash = db.cash().listAll(user).map { it.toBackup() },
            products = db.products().listAll(user).map { it.toBackup() },
            sales = db.sales().listAll(user).map { it.toBackup() },
            deletions = db.deletions().listAll(user).map { DeletionBackup(it.id, it.collection, it.deletedAt) },
            sensitive = sensitive,
        )
    }

    suspend fun replaceFrom(bundle: BackupBundle) = withIo {
        val user = uid.value
        db.withTransaction {
            db.entries().clear(user)
            db.sales().clear(user)
            db.cash().clear(user)
            db.products().clear(user)
            db.deletions().clear(user)
            db.parties().clear(user)
            db.parties().upsertAll(bundle.parties.map { it.toEntity().copy(ownerUid = user) })
            db.entries().upsertAll(bundle.entries.map { it.toEntity().copy(ownerUid = user) })
            db.cash().upsertAll(bundle.cash.map { it.toEntity().copy(ownerUid = user) })
            db.products().upsertAll(bundle.products.map { it.toEntity().copy(ownerUid = user) })
            db.sales().upsertAll(bundle.sales.map { it.toEntity().copy(ownerUid = user) })
            db.deletions().upsertAll(bundle.deletions.map { DeletionEntity(it.id, it.collection, it.deletedAt, user) })
        }
        if (bundle.sensitive.isNotEmpty()) applySensitiveRows(bundle.sensitive)
        changed()
    }

    suspend fun applySensitive(rows: List<SensitiveBackup>) = withIo {
        if (rows.isEmpty()) return@withIo
        applySensitiveRows(rows)
    }

    private suspend fun applySensitiveRows(rows: List<SensitiveBackup>) {
        val now = System.currentTimeMillis()
        rows.forEach { item ->
            vault.sensitive().upsert(
                SensitiveEntity(
                    partyId = item.partyId,
                    faceEmbedding = item.faceEmbeddingB64.fromBase64(),
                    facePhotoPath = resolveStored(item.facePhotoRel),
                    faceConsentAt = item.faceConsentAt,
                    faceModel = item.faceModel,
                    nidNumber = item.nidNumber,
                    nidName = item.nidName,
                    nidPhotoPath = resolveStored(item.nidPhotoRel),
                    updatedAt = now,
                ),
            )
        }
    }

    private fun relOf(path: String): String {
        if (path.isBlank()) return ""
        val root = context.filesDir.absolutePath.trimEnd('/')
        val abs = File(path).absolutePath
        return if (abs.startsWith(root)) abs.removePrefix(root).trimStart('/') else ""
    }

    private fun resolveStored(rel: String): String {
        if (rel.isBlank() || rel.contains("..")) return ""
        val file = File(context.filesDir, rel)
        return if (file.exists()) file.absolutePath else ""
    }

    suspend fun eraseBusinessData() = withIo {
        val user = uid.value
        val parties = db.parties().listAll(user)
        val entries = db.entries().listAll(user)
        val cash = db.cash().listAll(user)
        val products = db.products().listAll(user)
        val sales = db.sales().listAll(user)
        val now = System.currentTimeMillis()
        val sensitive = parties.mapNotNull { vault.sensitive().get(it.id) }
        db.withTransaction {
            parties.forEach { db.deletions().upsert(DeletionEntity(it.id, "parties", now, user)) }
            entries.forEach { db.deletions().upsert(DeletionEntity(it.id, "entries", now, user)) }
            cash.forEach { db.deletions().upsert(DeletionEntity(it.id, "cash", now, user)) }
            products.forEach { db.deletions().upsert(DeletionEntity(it.id, "products", now, user)) }
            sales.forEach { db.deletions().upsert(DeletionEntity(it.id, "sales", now, user)) }
            db.entries().clear(user)
            db.sales().clear(user)
            db.cash().clear(user)
            db.products().clear(user)
            db.parties().clear(user)
        }
        parties.forEach { vault.sensitive().delete(it.id) }
        parties.forEach { deleteFile(it.photoPath) }
        sensitive.forEach {
            deleteFile(it.facePhotoPath)
            deleteFile(it.nidPhotoPath)
        }
        changed()
    }

    suspend fun listParties() = withIo { db.parties().listAll(uid.value) }
    suspend fun listEntries() = withIo { db.entries().listAll(uid.value) }
    suspend fun listCash() = withIo { db.cash().listAll(uid.value) }
    suspend fun listProducts() = withIo { db.products().listAll(uid.value) }
    suspend fun listSales() = withIo { db.sales().listAll(uid.value) }
    suspend fun listDeletions() = withIo { db.deletions().listAll(uid.value) }

    suspend fun applyRemoteParty(party: PartyEntity) = withIo {
        db.parties().upsert(party.copy(ownerUid = uid.value))
        db.deletions().delete(party.id, uid.value)
    }

    suspend fun applyRemoteEntry(entry: EntryEntity) = withIo {
        db.entries().upsert(entry.copy(ownerUid = uid.value))
        db.deletions().delete(entry.id, uid.value)
    }

    suspend fun applyRemoteCash(entry: CashEntity) = withIo {
        db.cash().upsert(entry.copy(ownerUid = uid.value))
        db.deletions().delete(entry.id, uid.value)
    }

    suspend fun applyRemoteProduct(product: ProductEntity) = withIo {
        db.products().upsert(product.copy(ownerUid = uid.value))
        db.deletions().delete(product.id, uid.value)
    }

    suspend fun applyRemoteSale(sale: SaleEntity) = withIo {
        db.sales().upsert(sale.copy(ownerUid = uid.value))
        db.deletions().delete(sale.id, uid.value)
    }

    suspend fun applyRemoteDeletion(id: String, collection: String, deletedAt: Long) = withIo {
        val user = uid.value
        when (collection) {
            "parties" -> {
                val entryIds = db.entries().idsForParty(id, user)
                val party = db.parties().get(id, user)
                entryIds.forEach { db.deletions().upsert(DeletionEntity(it, "entries", deletedAt, user)) }
                db.deletions().upsert(DeletionEntity(id, "parties", deletedAt, user))
                db.parties().delete(id, user)
                party?.let { deleteFile(it.photoPath) }
            }
            "entries" -> {
                db.entries().delete(id, user)
                db.deletions().upsert(DeletionEntity(id, "entries", deletedAt, user))
            }
            "cash" -> {
                db.cash().delete(id, user)
                db.deletions().upsert(DeletionEntity(id, "cash", deletedAt, user))
            }
            "products" -> {
                db.products().delete(id, user)
                db.deletions().upsert(DeletionEntity(id, "products", deletedAt, user))
            }
            "sales" -> {
                db.sales().delete(id, user)
                db.deletions().upsert(DeletionEntity(id, "sales", deletedAt, user))
            }
        }
    }

    private fun <T> inBusiness(flow: Flow<List<T>>, idOf: (T) -> String): Flow<List<T>> =
        combine(flow, business) { rows, biz -> rows.filter { belongs(idOf(it), biz) } }

    private fun belongs(row: String, biz: String) = (row.isBlank() && biz == "main") || row == biz

    private fun <T> scopedList(query: (String) -> Flow<List<T>>): Flow<List<T>> = uid.flatMapLatest { id ->
        if (id.isBlank()) flowOf(emptyList()) else query(id)
    }

    private fun <T> scopedOne(query: (String) -> Flow<T?>): Flow<T?> = uid.flatMapLatest { id ->
        if (id.isBlank()) flowOf(null) else query(id)
    }

    private fun changed() {
        signals.poke()
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
