package bd.robin.hisabkhata.ui

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.data.FirebaseGate
import bd.robin.hisabkhata.logic.Kind
import bd.robin.hisabkhata.ui.theme.Leaf
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    val lang = settings?.language ?: "bn"
    var explain by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val googleReady = FirebaseGate.googleReady(context)
    val failed = stringResource(R.string.google_failed)

    Column(Modifier.fillMaxSize().background(Leaf)) {
        Column(Modifier.statusBarsPadding().padding(24.dp)) {
            Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.tagline), color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                Text(stringResource(R.string.login_offline_note), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                if (googleReady) {
                    BigButton(
                        stringResource(R.string.login_google),
                        enabled = !busy,
                        modifier = Modifier.tag("google_button"),
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@BigButton
                            scope.launch {
                                busy = true
                                runCatching { container.auth.signInWithGoogle(activity) }
                                    .onSuccess { profile ->
                                        container.settings.update {
                                            it.copy(
                                                session = Kind.SESSION_GOOGLE,
                                                accountUid = profile.uid,
                                                accountName = profile.name,
                                                accountEmail = profile.email,
                                                ownerName = it.ownerName.ifBlank { profile.name },
                                            )
                                        }
                                    }
                                    .onFailure { context.toast(failed) }
                                busy = false
                            }
                        },
                    )
                    BigOutlined(stringResource(R.string.login_guest), modifier = Modifier.tag("guest_button")) {
                        scope.launch { container.settings.update { it.copy(session = Kind.SESSION_GUEST) } }
                    }
                } else {
                    BigButton(stringResource(R.string.login_guest), modifier = Modifier.tag("guest_button")) {
                        scope.launch { container.settings.update { it.copy(session = Kind.SESSION_GUEST) } }
                    }
                    BigOutlined(stringResource(R.string.login_google), modifier = Modifier.tag("google_button")) {
                        explain = true
                    }
                    Text(stringResource(R.string.login_google_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.google_unavailable_title)) },
            text = { Text(stringResource(R.string.google_unavailable_body)) },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    scope.launch { container.settings.update { it.copy(session = Kind.SESSION_GUEST) } }
                }) { Text(stringResource(R.string.google_try_guest)) }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private suspend fun switchLanguage(context: android.content.Context, container: bd.robin.hisabkhata.data.AppContainer, code: String) {
    container.settings.update { it.copy(language = code) }
    container.settings.writeBootLanguage(code)
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    (context as? android.app.Activity)?.recreate()
}
