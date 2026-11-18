package bd.robin.hisabkhata.logic

import kotlinx.serialization.Serializable

@Serializable
data class ProfileBackup(
    val ownerName: String = "",
    val shopName: String = "",
    val phone: String = "",
    val address: String = "",
    val businessType: String = "",
    val language: String = "bn",
    val theme: String = "light",
    val defaultCreditLimit: Long = 5000,
    val limitMode: String = Kind.MODE_WARN,
    val dailyLimitEnabled: Boolean = false,
    val defaultDailyLimit: Long = 2000,
)

@Serializable
data class PartyBackup(
    val id: String,
    val type: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val note: String = "",
    val useCustomLimit: Boolean = false,
    val creditLimit: Long = 0,
    val useDailyLimit: Boolean = false,
    val dailyLimit: Long = 0,
    val verifyStatus: String = Kind.VERIFY_NONE,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class EntryBackup(
    val id: String,
    val partyId: String,
    val type: String,
    val amount: Long,
    val note: String = "",
    val date: Long,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class CashBackup(
    val id: String,
    val type: String,
    val category: String = "other",
    val amount: Long,
    val note: String = "",
    val date: Long,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class ProductBackup(
    val id: String,
    val name: String,
    val price: Long = 0,
    val stock: Int = 0,
    val barcode: String = "",
    val note: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class SaleBackup(
    val id: String,
    val productId: String,
    val productName: String = "",
    val qty: Int = 1,
    val unitPrice: Long = 0,
    val amount: Long = 0,
    val date: Long = 0,
    val createdAt: Long = 0,
)

@Serializable
data class DeletionBackup(
    val id: String,
    val collection: String,
    val deletedAt: Long,
)

@Serializable
data class SensitiveBackup(
    val partyId: String,
    val faceModel: String = "",
    val faceEmbeddingB64: String = "",
    val faceConsentAt: Long? = null,
    val nidNumber: String = "",
    val nidName: String = "",
)

@Serializable
data class BackupBundle(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val app: String = "HisabKhata",
    val profile: ProfileBackup = ProfileBackup(),
    val parties: List<PartyBackup> = emptyList(),
    val entries: List<EntryBackup> = emptyList(),
    val cash: List<CashBackup> = emptyList(),
    val products: List<ProductBackup> = emptyList(),
    val sales: List<SaleBackup> = emptyList(),
    val deletions: List<DeletionBackup> = emptyList(),
    val sensitive: List<SensitiveBackup> = emptyList(),
)

class BackupVersionException : Exception("newer backup")
