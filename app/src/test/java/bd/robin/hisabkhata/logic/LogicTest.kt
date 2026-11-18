package bd.robin.hisabkhata.logic

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    @Test
    fun creditOverLimitWarnsAndBlockStopsSave() {
        val warned = checkLimit(
            LimitInput(
                isCredit = true,
                amount = 1500,
                currentBalance = 0,
                todayCredit = 0,
                creditLimit = 1000,
                dailyLimit = null,
                mode = Kind.MODE_WARN,
            ),
        )
        assertEquals(LimitKind.OVER_TOTAL, warned.kind)
        assertTrue(warned.warned)
        assertFalse(warned.blocked)

        val blocked = warned.copy(mode = Kind.MODE_BLOCK)
        assertTrue(blocked.blocked)
    }

    @Test
    fun paymentDoesNotTripCreditLimit() {
        val decision = checkLimit(
            LimitInput(
                isCredit = false,
                amount = 5000,
                currentBalance = 900,
                todayCredit = 900,
                creditLimit = 1000,
                dailyLimit = 1000,
                mode = Kind.MODE_BLOCK,
            ),
        )
        assertEquals(LimitKind.OK, decision.kind)
    }

    @Test
    fun dailyLimitUsesOnlyTodaysCredit() {
        val decision = checkLimit(
            LimitInput(
                isCredit = true,
                amount = 400,
                currentBalance = 400,
                todayCredit = 400,
                creditLimit = 5000,
                dailyLimit = 500,
                mode = Kind.MODE_WARN,
            ),
        )
        assertEquals(LimitKind.OVER_DAILY, decision.kind)
        assertEquals(400L, decision.used)
    }

    @Test
    fun editingDoesNotDoubleCountTheOldCredit() {
        val decision = checkLimit(
            LimitInput(
                isCredit = true,
                amount = 800,
                currentBalance = 800,
                todayCredit = 800,
                creditLimit = 1000,
                dailyLimit = 1000,
                mode = Kind.MODE_BLOCK,
                previousCredit = 800,
                previousCreditSameDay = 800,
            ),
        )
        assertEquals(LimitKind.OK, decision.kind)
    }

    @Test
    fun remainingLimitNeverGoesNegative() {
        assertEquals(0L, remainingLimit(1500, 1000))
        assertEquals(400L, remainingLimit(600, 1000))
    }

    @Test
    fun slipParserReadsTotalDateAndName() {
        val draft = SlipParser.parse(SlipParser.SAMPLE)
        assertEquals(400L, draft.amount)
        assertEquals(LocalDate.of(2026, 11, 18), draft.day)
        assertEquals("Karim Mia", draft.name)
    }

    @Test
    fun slipParserReadsBanglaDigits() {
        val draft = SlipParser.parse("নাম: করিম মিয়া\nতারিখ: ১৮/১১/২০২৬\nমোট: ১,২৫০")
        assertEquals(1250L, draft.amount)
        assertEquals(LocalDate.of(2026, 11, 18), draft.day)
        assertEquals("করিম মিয়া", draft.name)
    }

    @Test
    fun phoneNormalizesLocalAndInternational() {
        assertEquals("8801712345678", normalizeBdPhone("01712-345678"))
        assertEquals("+8801712345678", toE164Bd("01712345678"))
        assertEquals("01712345678", localBdPhone("+8801712345678"))
        assertNull(toE164Bd("12345"))
    }

    @Test
    fun nidMaskKeepsLastFour() {
        assertEquals("•••••••••1234", maskNid("1234567891234"))
    }

    @Test
    fun identicalFaceVectorsMatchAndOppositesDoNot() {
        val probe = FaceMath.l2normalize(floatArrayOf(1f, 0f, 0f, 0f))
        val hit = bestFaceMatch(probe, listOf("a" to probe.copyOf()), FaceMath.TFLITE_THRESHOLD)
        assertNotNull(hit)
        assertEquals("a", hit!!.partyId)
        val other = FaceMath.l2normalize(floatArrayOf(0f, 1f, 0f, 0f))
        assertNull(bestFaceMatch(probe, listOf("b" to other), FaceMath.TFLITE_THRESHOLD))
    }

    @Test
    fun cloudPartyMapHasNoSensitiveKeys() {
        val keys = partyCloudMap(
            id = "1",
            type = Kind.CUSTOMER,
            name = "করিম",
            phone = "017",
            address = "",
            note = "",
            useCustomLimit = false,
            creditLimit = 0,
            useDailyLimit = false,
            dailyLimit = 0,
            verifyStatus = Kind.VERIFY_NONE,
            createdAt = 1,
            updatedAt = 1,
        ).keys
        assertFalse(keys.any { it.contains("face") || it.contains("nid") || it.contains("photo") || it.contains("embedding") })
    }

    @Test
    fun sensitiveDriveBackupIsOnByDefaultAndSeparateFromTheLedger() {
        assertTrue(DrivePolicy.INCLUDE_SENSITIVE_BY_DEFAULT)
        assertTrue(DrivePolicy.uploadsSensitive(true))
        assertFalse(DrivePolicy.uploadsSensitive(false))
        assertEquals("nsedo-ledger.enc", DrivePolicy.LEDGER)
        assertEquals("nsedo-sensitive.enc", DrivePolicy.SENSITIVE)
        assertFalse(DrivePolicy.backupFileIncluded("faces/karim.jpg", includeSensitive = false))
        assertFalse(DrivePolicy.backupFileIncluded("nid/karim.jpg", includeSensitive = false))
        assertTrue(DrivePolicy.backupFileIncluded("faces/karim.jpg", includeSensitive = true))
        assertTrue(DrivePolicy.backupFileIncluded("slips/slip.jpg", includeSensitive = false))
    }

    @Test
    fun aesGcmSealRoundTripsAndHidesThePlaintext() {
        val key = ByteArray(32) { (it * 7).toByte() }
        val plain = "NID 1234567890123 face".toByteArray()
        val sealed = BackupSeal.seal(key, plain)
        assertFalse(String(sealed).contains("1234567890123"))
        assertEquals("NID 1234567890123 face", BackupSeal.open(key, sealed).decodeToString())
    }

    @Test
    fun ledgerCopyDropsNidAndFace() {
        val json = Json { encodeDefaults = true }
        val full = BackupBundle(
            sensitive = listOf(
                SensitiveBackup(partyId = "p", nidNumber = "1234567890123", faceEmbeddingB64 = "abc=="),
            ),
        )
        val ledger = full.copy(sensitive = emptyList())
        val text = json.encodeToString(ledger)
        assertFalse(text.contains("1234567890123"))
        assertFalse(text.contains("abc=="))
    }

    @Test
    fun code128LabelEncodesAProductBarcode() {
        val matrix = BarcodeBits.code128("8941100000017")
        assertTrue(matrix.width > 20)
        assertTrue(matrix.height > 10)
    }

    @Test
    fun backupRoundTripKeepsBanglaName() {
        val json = Json { ignoreUnknownKeys = true }
        val bundle = BackupBundle(
            exportedAt = 5,
            parties = listOf(PartyBackup(id = "a", type = Kind.CUSTOMER, name = "করিম")),
        )
        val back = json.decodeFromString<BackupBundle>(json.encodeToString(bundle))
        assertEquals("করিম", back.parties.first().name)
        assertTrue(back.sensitive.isEmpty())
    }

    @Test
    fun pinHashRejectsADifferentPin() {
        val salt = PinHash.newSalt()
        val hash = PinHash.hash("1234", salt)
        assertTrue(PinHash.matches("1234", salt, hash))
        assertFalse(PinHash.matches("9999", salt, hash))
    }

    @Test
    fun customerDueIsGetAndSupplierDueIsGive() {
        assertEquals(BalanceTone.GET, balanceTone(Kind.CUSTOMER, 100))
        assertEquals(BalanceTone.GIVE, balanceTone(Kind.SUPPLIER, 100))
        assertEquals(BalanceTone.SETTLED, balanceTone(Kind.CUSTOMER, 0))
    }

    @Test
    fun newerCloudCopyWinsAndSoftDeleteIsNotLost() {
        assertEquals(CloudChoice.TAKE_REMOTE, cloudChoice(100, 200, 0))
        assertEquals(CloudChoice.KEEP_LOCAL, cloudChoice(300, 200, 0))
        assertEquals(CloudChoice.TOMBSTONE, cloudChoice(100, 100, 150))
        assertEquals(CloudChoice.KEEP_LOCAL, cloudChoice(200, 100, 150))
    }

    @Test
    fun signInOnANewPhoneRestoresLiveRowsAndKeepsSoftDeletes() {
        val cloud = listOf(
            RemoteDoc("karim", updatedAt = 10),
            RemoteDoc("rahim", updatedAt = 8, deletedAt = 12),
            RemoteDoc("shop-sale", updatedAt = 4),
        )
        assertEquals(listOf("karim", "shop-sale"), liveIdsAfterRestore(cloud))
        assertFalse(liveIdsAfterRestore(cloud).contains("rahim"))
    }
}
