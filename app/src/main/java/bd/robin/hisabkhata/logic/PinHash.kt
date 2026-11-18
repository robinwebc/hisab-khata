package bd.robin.hisabkhata.logic

import java.security.MessageDigest
import java.util.UUID

object PinHash {
    fun newSalt(): String = UUID.randomUUID().toString()

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = "$salt:$pin".toByteArray()
        repeat(2000) { bytes = digest.digest(bytes) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, salt: String, expected: String): Boolean {
        if (pin.length < 4 || expected.isBlank() || salt.isBlank()) return false
        return hash(pin, salt) == expected
    }
}
