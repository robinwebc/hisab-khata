package bd.robin.hisabkhata.logic

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BackupSeal {
    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        return cipher.doFinal(sealed.copyOfRange(12, sealed.size))
    }
}

object DrivePolicy {
    const val LEDGER = "nsedo-ledger.enc"
    const val SENSITIVE = "nsedo-sensitive.enc"
    const val INCLUDE_SENSITIVE_BY_DEFAULT = true

    fun uploadsSensitive(enabled: Boolean): Boolean = enabled

    fun backupFileIncluded(relativePath: String, includeSensitive: Boolean): Boolean {
        val rel = relativePath.replace('\\', '/').trimStart('/')
        val secret = rel.startsWith("faces/") || rel.startsWith("nid/")
        return includeSensitive || !secret
    }
}
