package bd.robin.hisabkhata.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.data.FirebaseGate
import bd.robin.hisabkhata.ui.theme.NsBrand
import bd.robin.hisabkhata.ui.theme.NsInk
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    val lang = settings?.language ?: "bn"
    var busy by remember { mutableStateOf(false) }
    val googleReady = FirebaseGate.googleReady(context)
    val failed = stringResource(R.string.google_failed)
    val unavailable = stringResource(R.string.google_unavailable_body)

    Column(Modifier.fillMaxSize().background(NsInk)) {
        Column(
            Modifier.statusBarsPadding().padding(horizontal = 28.dp, vertical = 36.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = NsBrand, shadowElevation = 8.dp) {
                Image(
                    painterResource(R.drawable.ic_nsedo_mark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(92.dp).padding(10.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.tagline),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = lang != "en",
                        onClick = { scope.launch { switchLanguage(context, container, "bn") } },
                        label = { Text(stringResource(R.string.lang_bangla)) },
                        modifier = Modifier.tag("lang_bn"),
                    )
                    FilterChip(
                        selected = lang == "en",
                        onClick = { scope.launch { switchLanguage(context, container, "en") } },
                        label = { Text(stringResource(R.string.lang_english)) },
                        modifier = Modifier.tag("lang_en"),
                    )
                }
                Text(stringResource(R.string.login_cloud_note), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                BigButton(
                    stringResource(R.string.login_google),
                    enabled = !busy,
                    modifier = Modifier.tag("google_button"),
                    onClick = {
                        if (!googleReady) {
                            context.toast(unavailable)
                            return@BigButton
                        }
                        val activity = context as? android.app.Activity ?: return@BigButton
                        scope.launch {
                            busy = true
                            runCatching { container.auth.signInWithGoogle(activity) }
                                .onSuccess { container.account.onGoogle(it) }
                                .onFailure { context.toast(failed) }
                            busy = false
                        }
                    },
                )
                Text(
                    stringResource(R.string.login_passkey_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private suspend fun switchLanguage(context: android.content.Context, container: bd.robin.hisabkhata.data.AppContainer, code: String) {
    container.settings.update { it.copy(language = code) }
    container.settings.writeBootLanguage(code)
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    (context as? android.app.Activity)?.recreate()
}
