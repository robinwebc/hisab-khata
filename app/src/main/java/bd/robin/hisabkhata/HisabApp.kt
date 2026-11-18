package bd.robin.hisabkhata

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import bd.robin.hisabkhata.data.AppContainer
import bd.robin.hisabkhata.data.CloudDb
import kotlinx.coroutines.launch

class HisabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CloudDb.firestore()
        val prefs = getSharedPreferences("boot", MODE_PRIVATE)
        if (prefs.getString("session", "") == "google") {
            val uid = prefs.getString("uid", "").orEmpty()
            if (uid.isNotBlank()) {
                container.repo.bind(uid)
                container.scope.launch {
                    container.repo.claimLegacy(uid)
                    container.signals.poke()
                }
            }
        }
        if (!prefs.contains("lang")) {
            prefs.edit().putString("lang", "bn").apply()
        }
        val lang = prefs.getString("lang", "bn") ?: "bn"
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (!current.startsWith(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
        container.scope.launch {
            val current = container.settings.current()
            if (current.pinHash.isNotBlank()) {
                container.passkey.migrate(current.pinHash, current.pinSalt)
                container.settings.update(notify = false) { it.copy(pinHash = "", pinSalt = "") }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                val boot = getSharedPreferences("boot", MODE_PRIVATE)
                boot.edit().putLong("backgroundAt", System.currentTimeMillis()).apply()
                val timeout = boot.getInt("passkeyTimeout", 1)
                if (boot.getBoolean("lockOnBackground", true) && timeout <= 0 && container.passkey.has()) {
                    container.session.lockPin()
                }
                if (boot.getBoolean("quickUnlock", false) && timeout <= 0) {
                    container.session.lock()
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                val boot = getSharedPreferences("boot", MODE_PRIVATE)
                if (!boot.getBoolean("lockOnBackground", true)) return
                val timeout = boot.getInt("passkeyTimeout", 1)
                if (timeout <= 0) return
                val at = boot.getLong("backgroundAt", 0L)
                if (at > 0 && System.currentTimeMillis() - at >= timeout * 60_000L) {
                    container.session.lock()
                }
            }
        })
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as HisabApp).container
