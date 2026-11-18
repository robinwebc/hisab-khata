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
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                val boot = getSharedPreferences("boot", MODE_PRIVATE)
                val pin = boot.getBoolean("hasPin", false) && boot.getBoolean("lockOnBackground", true)
                if (boot.getBoolean("quickUnlock", false) || pin) {
                    container.session.lock()
                }
            }
        })
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as HisabApp).container
