package bd.robin.hisabkhata.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name")
    fun observeAll(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :id")
    fun observe(id: String): Flow<PartyEntity?>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun get(id: String): PartyEntity?

    @Query("SELECT * FROM parties")
    suspend fun listAll(): List<PartyEntity>

    @Upsert
    suspend fun upsert(party: PartyEntity)

    @Upsert
    suspend fun upsertAll(parties: List<PartyEntity>)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM parties")
    suspend fun clear()
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE partyId = :partyId ORDER BY date DESC, createdAt DESC")
    fun observeForParty(partyId: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries ORDER BY date DESC, createdAt DESC LIMIT 40")
    fun observeRecent(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries")
    suspend fun listAll(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): EntryEntity?

    @Query("SELECT id FROM entries WHERE partyId = :partyId")
    suspend fun idsForParty(partyId: String): List<String>

    @Query(
        """
        SELECT partyId, SUM(CASE WHEN type = 'credit' THEN amount ELSE -amount END) AS balance
        FROM entries GROUP BY partyId
        """,
    )
    fun observeBalances(): Flow<List<BalanceRow>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'credit' THEN amount ELSE -amount END), 0)
        FROM entries WHERE partyId = :partyId
        """,
    )
    suspend fun balance(partyId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM entries
        WHERE partyId = :partyId AND type = 'credit' AND date >= :start AND date < :end
        """,
    )
    suspend fun creditBetween(partyId: String, start: Long, end: Long): Long

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM entries")
    suspend fun clear()
}

@Dao
interface CashDao {
    @Query("SELECT * FROM cash ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<CashEntity>>

    @Query("SELECT * FROM cash")
    suspend fun listAll(): List<CashEntity>

    @Query("SELECT * FROM cash WHERE id = :id")
    suspend fun get(id: String): CashEntity?

    @Upsert
    suspend fun upsert(entry: CashEntity)

    @Upsert
    suspend fun upsertAll(entries: List<CashEntity>)

    @Query("DELETE FROM cash WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cash")
    suspend fun clear()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :code AND barcode != '' LIMIT 1")
    suspend fun byBarcode(code: String): ProductEntity?

    @Query("SELECT * FROM products")
    suspend fun listAll(): List<ProductEntity>

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE productId = :productId ORDER BY date DESC LIMIT 20")
    fun observeForProduct(productId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales")
    suspend fun listAll(): List<SaleEntity>

    @Upsert
    suspend fun upsert(sale: SaleEntity)

    @Upsert
    suspend fun upsertAll(sales: List<SaleEntity>)

    @Query("DELETE FROM sales")
    suspend fun clear()
}

@Dao
interface DeletionDao {
    @Query("SELECT * FROM deletions")
    suspend fun listAll(): List<DeletionEntity>

    @Upsert
    suspend fun upsert(row: DeletionEntity)

    @Upsert
    suspend fun upsertAll(rows: List<DeletionEntity>)

    @Query("DELETE FROM deletions")
    suspend fun clear()
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
