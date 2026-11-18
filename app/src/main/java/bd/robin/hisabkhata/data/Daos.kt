package bd.robin.hisabkhata.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties WHERE ownerUid = :uid ORDER BY name")
    fun observeAll(uid: String): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :id AND ownerUid = :uid")
    fun observe(id: String, uid: String): Flow<PartyEntity?>

    @Query("SELECT * FROM parties WHERE id = :id AND ownerUid = :uid")
    suspend fun get(id: String, uid: String): PartyEntity?

    @Query("SELECT * FROM parties WHERE ownerUid = :uid")
    suspend fun listAll(uid: String): List<PartyEntity>

    @Upsert
    suspend fun upsert(party: PartyEntity)

    @Upsert
    suspend fun upsertAll(parties: List<PartyEntity>)

    @Query("DELETE FROM parties WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM parties WHERE ownerUid = :uid")
    suspend fun clear(uid: String)

    @Query("UPDATE parties SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claim(uid: String)
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE partyId = :partyId AND ownerUid = :uid ORDER BY date DESC, createdAt DESC")
    fun observeForParty(partyId: String, uid: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE ownerUid = :uid ORDER BY date DESC, createdAt DESC LIMIT 40")
    fun observeRecent(uid: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE ownerUid = :uid")
    fun observeAll(uid: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE ownerUid = :uid")
    suspend fun listAll(uid: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id AND ownerUid = :uid")
    suspend fun get(id: String, uid: String): EntryEntity?

    @Query("SELECT id FROM entries WHERE partyId = :partyId AND ownerUid = :uid")
    suspend fun idsForParty(partyId: String, uid: String): List<String>

    @Query(
        """
        SELECT partyId, SUM(CASE WHEN type = 'credit' THEN amount ELSE -amount END) AS balance
        FROM entries WHERE ownerUid = :uid GROUP BY partyId
        """,
    )
    fun observeBalances(uid: String): Flow<List<BalanceRow>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'credit' THEN amount ELSE -amount END), 0)
        FROM entries WHERE partyId = :partyId AND ownerUid = :uid
        """,
    )
    suspend fun balance(partyId: String, uid: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM entries
        WHERE partyId = :partyId AND ownerUid = :uid AND type = 'credit' AND date >= :start AND date < :end
        """,
    )
    suspend fun creditBetween(partyId: String, uid: String, start: Long, end: Long): Long

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entries WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM entries WHERE ownerUid = :uid")
    suspend fun clear(uid: String)

    @Query("UPDATE entries SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claim(uid: String)
}

@Dao
interface CashDao {
    @Query("SELECT * FROM cash WHERE ownerUid = :uid ORDER BY date DESC, createdAt DESC")
    fun observeAll(uid: String): Flow<List<CashEntity>>

    @Query("SELECT * FROM cash WHERE ownerUid = :uid")
    suspend fun listAll(uid: String): List<CashEntity>

    @Query("SELECT * FROM cash WHERE id = :id AND ownerUid = :uid")
    suspend fun get(id: String, uid: String): CashEntity?

    @Upsert
    suspend fun upsert(entry: CashEntity)

    @Upsert
    suspend fun upsertAll(entries: List<CashEntity>)

    @Query("DELETE FROM cash WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM cash WHERE ownerUid = :uid")
    suspend fun clear(uid: String)

    @Query("UPDATE cash SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claim(uid: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE ownerUid = :uid ORDER BY name")
    fun observeAll(uid: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id AND ownerUid = :uid")
    fun observe(id: String, uid: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id AND ownerUid = :uid")
    suspend fun get(id: String, uid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :code AND barcode != '' AND ownerUid = :uid LIMIT 1")
    suspend fun byBarcode(code: String, uid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE ownerUid = :uid")
    suspend fun listAll(uid: String): List<ProductEntity>

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM products WHERE ownerUid = :uid")
    suspend fun clear(uid: String)

    @Query("UPDATE products SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claim(uid: String)
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE productId = :productId AND ownerUid = :uid ORDER BY date DESC LIMIT 20")
    fun observeForProduct(productId: String, uid: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE ownerUid = :uid")
    suspend fun listAll(uid: String): List<SaleEntity>

    @Upsert
    suspend fun upsert(sale: SaleEntity)

    @Upsert
    suspend fun upsertAll(sales: List<SaleEntity>)

    @Query("DELETE FROM sales WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM sales WHERE ownerUid = :uid")
    suspend fun clear(uid: String)

    @Query("UPDATE sales SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claim(uid: String)
}

@Dao
interface DeletionDao {
    @Query("SELECT * FROM deletions WHERE ownerUid = :uid")
    suspend fun listAll(uid: String): List<DeletionEntity>

    @Upsert
    suspend fun upsert(row: DeletionEntity)

    @Upsert
    suspend fun upsertAll(rows: List<DeletionEntity>)

    @Query("DELETE FROM deletions WHERE id = :id AND ownerUid = :uid")
    suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM deletions WHERE ownerUid = :uid")
    suspend fun clear(uid: String)

    @Query("UPDATE deletions SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claim(uid: String)
}

@Dao
interface SensitiveDao {
    @Query("SELECT * FROM sensitive")
    fun observeAll(): Flow<List<SensitiveEntity>>

    @Query("SELECT * FROM sensitive WHERE partyId = :id")
    fun observe(id: String): Flow<SensitiveEntity?>

    @Query("SELECT * FROM sensitive WHERE partyId = :id")
    suspend fun get(id: String): SensitiveEntity?

    @Query("SELECT * FROM sensitive")
    suspend fun listAll(): List<SensitiveEntity>

    @Upsert
    suspend fun upsert(row: SensitiveEntity)

    @Query("DELETE FROM sensitive WHERE partyId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sensitive")
    suspend fun clear()
}
