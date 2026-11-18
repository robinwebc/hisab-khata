package bd.robin.hisabkhata.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bd.robin.hisabkhata.logic.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsStore by preferencesDataStore("hisab_settings")

data class AppSettings(
    val language: String = "bn",
    val theme: String = Kind.THEME_LIGHT,
    val ownerName: String = "",
    val shopName: String = "",
    val phone: String = "",
    val address: String = "",
    val businessType: String = "",
    val photoPath: String = "",
    val session: String = Kind.SESSION_NONE,
    val accountEmail: String = "",
    val accountName: String = "",
    val accountUid: String = "",
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val lockOnBackground: Boolean = true,
    val defaultCreditLimit: Long = 5000,
    val limitMode: String = Kind.MODE_WARN,
    val dailyLimitEnabled: Boolean = false,
    val defaultDailyLimit: Long = 2000,
    val lastSyncAt: Long = 0,
)

sealed interface Boot {
    data object Loading : Boot
    data class Ready(val settings: AppSettings) : Boot
}

class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    private val store = context.settingsStore
    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }
    private val bootState = MutableStateFlow<Boot>(Boot.Loading)
    val boot: StateFlow<Boot> = bootState

    init {
        scope.launch {
            store.data.collect { prefs ->
                val value = prefs.toSettings()
                mirror(value)
                bootState.value = Boot.Ready(value)
            }
        }
    }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            prefs.write(block(prefs.toSettings()))
        }
    }

    suspend fun current(): AppSettings = when (val ready = bootState.value) {
        is Boot.Ready -> ready.settings
        Boot.Loading -> store.data.map { it.toSettings() }.first()
    }

    private fun mirror(settings: AppSettings) {
        context.getSharedPreferences(BOOT, Context.MODE_PRIVATE).edit()
            .putString("lang", settings.language)
            .putString("theme", settings.theme)
            .putBoolean("hasPin", settings.pinHash.isNotBlank())
            .putBoolean("lockOnBackground", settings.lockOnBackground)
            .apply()
    }

    fun writeBootLanguage(lang: String) {
        context.getSharedPreferences(BOOT, Context.MODE_PRIVATE).edit()
            .putString("lang", lang)
            .commit()
    }

    private fun Preferences.toSettings() = AppSettings(
        language = this[Keys.language] ?: "bn",
        theme = this[Keys.theme] ?: Kind.THEME_LIGHT,
        ownerName = this[Keys.ownerName] ?: "",
        shopName = this[Keys.shopName] ?: "",
        phone = this[Keys.phone] ?: "",
        address = this[Keys.address] ?: "",
        businessType = this[Keys.businessType] ?: "",
        photoPath = this[Keys.photoPath] ?: "",
        session = this[Keys.session] ?: Kind.SESSION_NONE,
        accountEmail = this[Keys.accountEmail] ?: "",
        accountName = this[Keys.accountName] ?: "",
        accountUid = this[Keys.accountUid] ?: "",
        pinHash = this[Keys.pinHash] ?: "",
        pinSalt = this[Keys.pinSalt] ?: "",
        biometricEnabled = this[Keys.biometric] ?: false,
        lockOnBackground = this[Keys.lockOnBackground] ?: true,
        defaultCreditLimit = this[Keys.defaultCreditLimit] ?: 5000L,
        limitMode = this[Keys.limitMode] ?: Kind.MODE_WARN,
        dailyLimitEnabled = this[Keys.dailyLimitEnabled] ?: false,
        defaultDailyLimit = this[Keys.defaultDailyLimit] ?: 2000L,
        lastSyncAt = this[Keys.lastSyncAt] ?: 0L,
    )

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.language] = settings.language
        this[Keys.theme] = settings.theme
        this[Keys.ownerName] = settings.ownerName
        this[Keys.shopName] = settings.shopName
        this[Keys.phone] = settings.phone
        this[Keys.address] = settings.address
        this[Keys.businessType] = settings.businessType
        this[Keys.photoPath] = settings.photoPath
        this[Keys.session] = settings.session
        this[Keys.accountEmail] = settings.accountEmail
        this[Keys.accountName] = settings.accountName
        this[Keys.accountUid] = settings.accountUid
        this[Keys.pinHash] = settings.pinHash
        this[Keys.pinSalt] = settings.pinSalt
        this[Keys.biometric] = settings.biometricEnabled
        this[Keys.lockOnBackground] = settings.lockOnBackground
        this[Keys.defaultCreditLimit] = settings.defaultCreditLimit
        this[Keys.limitMode] = settings.limitMode
        this[Keys.dailyLimitEnabled] = settings.dailyLimitEnabled
        this[Keys.defaultDailyLimit] = settings.defaultDailyLimit
        this[Keys.lastSyncAt] = settings.lastSyncAt
    }

    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val ownerName = stringPreferencesKey("ownerName")
        val shopName = stringPreferencesKey("shopName")
        val phone = stringPreferencesKey("phone")
        val address = stringPreferencesKey("address")
        val businessType = stringPreferencesKey("businessType")
        val photoPath = stringPreferencesKey("photoPath")
        val session = stringPreferencesKey("session")
        val accountEmail = stringPreferencesKey("accountEmail")
        val accountName = stringPreferencesKey("accountName")
        val accountUid = stringPreferencesKey("accountUid")
        val pinHash = stringPreferencesKey("pinHash")
        val pinSalt = stringPreferencesKey("pinSalt")
        val biometric = booleanPreferencesKey("biometric")
        val lockOnBackground = booleanPreferencesKey("lockOnBackground")
        val defaultCreditLimit = longPreferencesKey("defaultCreditLimit")
        val limitMode = stringPreferencesKey("limitMode")
        val dailyLimitEnabled = booleanPreferencesKey("dailyLimitEnabled")
        val defaultDailyLimit = longPreferencesKey("defaultDailyLimit")
        val lastSyncAt = longPreferencesKey("lastSyncAt")
    }

    companion object {
        const val BOOT = "boot"
    }
}
