package bd.robin.hisabkhata.logic

/**
 * Allow-listed Firestore fields. Face prints, NID, and local photo paths
 * are not parameters, so they cannot be uploaded by accident.
 */
fun partyCloudMap(
    id: String,
    type: String,
    name: String,
    phone: String,
    address: String,
    note: String,
    useCustomLimit: Boolean,
    creditLimit: Long,
    useDailyLimit: Boolean,
    dailyLimit: Long,
    verifyStatus: String,
    createdAt: Long,
    updatedAt: Long,
): Map<String, Any> = mapOf(
    "id" to id,
    "type" to type,
    "name" to name,
    "phone" to phone,
    "address" to address,
    "note" to note,
    "useCustomLimit" to useCustomLimit,
    "creditLimit" to creditLimit,
    "useDailyLimit" to useDailyLimit,
    "dailyLimit" to dailyLimit,
    "verifyStatus" to verifyStatus,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun entryCloudMap(
    id: String,
    partyId: String,
    type: String,
    amount: Long,
    note: String,
    date: Long,
    createdAt: Long,
    updatedAt: Long,
): Map<String, Any> = mapOf(
    "id" to id,
    "partyId" to partyId,
    "type" to type,
    "amount" to amount,
    "note" to note,
    "date" to date,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

fun profileCloudMap(
    ownerName: String,
    shopName: String,
    phone: String,
    address: String,
    businessType: String,
    updatedAt: Long,
): Map<String, Any> = mapOf(
    "ownerName" to ownerName,
    "shopName" to shopName,
    "phone" to phone,
    "address" to address,
    "businessType" to businessType,
    "updatedAt" to updatedAt,
)
