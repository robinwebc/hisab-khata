package bd.robin.hisabkhata.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import bd.robin.hisabkhata.logic.PinHash

class PasskeyStore(context: Context) {
    private val prefs: SharedPreferences = open(context)

    fun has(): Boolean = hash().isNotBlank() && salt().isNotBlank()

    fun matches(pin: String): Boolean = PinHash.matches(pin, salt(), hash())

    fun save(pin: String) {
        val salt = PinHash.newSalt()
        prefs.edit().putString(SALT, salt).putString(HASH, PinHash.hash(pin, salt)).commit()
    }

    fun clear() {
        prefs.edit().remove(HASH).remove(SALT).commit()
    }

    fun migrate(hash: String, salt: String) {
        if (has() || hash.isBlank() || salt.isBlank()) return
        prefs.edit().putString(HASH, hash).putString(SALT, salt).commit()
    }

    private fun hash(): String = prefs.getString(HASH, "").orEmpty()
    private fun salt(): String = prefs.getString(SALT, "").orEmpty()

    private companion object {
        const val HASH = "hash"
        const val SALT = "salt"

        fun open(context: Context): SharedPreferences {
            return runCatching {
                val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                EncryptedSharedPreferences.create(
                    context,
                    "nsedo_passkey",
                    master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                context.getSharedPreferences("nsedo_passkey_plain", Context.MODE_PRIVATE)
            }
        }
    }
}
