@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package bd.robin.hisabkhata

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.ui.AppNav
import bd.robin.hisabkhata.ui.LocalContainer
import bd.robin.hisabkhata.ui.LockScreen
import bd.robin.hisabkhata.ui.LoginScreen
import bd.robin.hisabkhata.ui.theme.HisabTheme
import bd.robin.hisabkhata.ui.theme.Leaf

class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        applyStoredLocale(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyStoredLocale(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        setContent {
            val boot by container.settings.boot.collectAsStateWithLifecycle()
            val unlocked by container.session.unlocked.collectAsStateWithLifecycle()
            val settings = (boot as? Boot.Ready)?.settings
            HisabTheme(dark = settings?.theme == Kind.THEME_DARK) {
                CompositionLocalProvider(LocalContainer provides container) {
                    Box(Modifier.semantics { testTagsAsResourceId = true }.fillMaxSize()) {
                        when {
                            settings == null -> Splash()
                            settings.session == Kind.SESSION_NONE -> LoginScreen()
                            settings.pinHash.isNotBlank() && !unlocked -> LockScreen()
                            else -> AppNav()
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Leaf), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

internal fun applyStoredLocale(context: Context) {
    val lang = context.getSharedPreferences("boot", Context.MODE_PRIVATE).getString("lang", "bn") ?: "bn"
    val locale = java.util.Locale.forLanguageTag(lang)
    java.util.Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    @Suppress("DEPRECATION")
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}
