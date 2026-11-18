package bd.robin.hisabkhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "parties", indices = [Index("type"), Index("name")])
data class PartyEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val phone: String,
    val address: String,
    val note: String,
    val photoPath: String,
    val useCustomLimit: Boolean,
    val creditLimit: Long,
    val useDailyLimit: Boolean,
    val dailyLimit: Long,
    val verifyStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerUid: String = "",
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("partyId"), Index("date")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val partyId: String,
    val type: String,
    val amount: Long,
    val note: String,
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerUid: String = "",
)

@Entity(tableName = "cash", indices = [Index("date")])
data class CashEntity(
    @PrimaryKey val id: String,
    val type: String,
    val category: String,
    val amount: Long,
    val note: String,
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerUid: String = "",
)

@Entity(tableName = "products", indices = [Index("barcode"), Index("name")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Long,
    val stock: Int,
    val barcode: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerUid: String = "",
)

@Entity(tableName = "sales", indices = [Index("productId"), Index("date")])
data class SaleEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val qty: Int,
    val unitPrice: Long,
    val amount: Long,
    val date: Long,
    val createdAt: Long,
    val ownerUid: String = "",
)

@Entity(tableName = "deletions")
data class DeletionEntity(
    @PrimaryKey val id: String,
    val collection: String,
    val deletedAt: Long,
    val ownerUid: String = "",
)

@Entity(tableName = "sensitive")
data class SensitiveEntity(
    @PrimaryKey val partyId: String,
    val faceEmbedding: ByteArray?,
    val facePhotoPath: String,
    val faceConsentAt: Long?,
    val faceModel: String,
    val nidNumber: String,
    val nidName: String,
    val nidPhotoPath: String,
    val updatedAt: Long,
)

data class BalanceRow(
    val partyId: String,
    val balance: Long,
)
