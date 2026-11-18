package bd.robin.hisabkhata

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import bd.robin.hisabkhata.data.AppContainer

class HisabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val prefs = getSharedPreferences("boot", MODE_PRIVATE)
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
                if (boot.getBoolean("hasPin", false) && boot.getBoolean("lockOnBackground", true)) {
                    container.session.lock()
                }
            }
        })
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as HisabApp).container
