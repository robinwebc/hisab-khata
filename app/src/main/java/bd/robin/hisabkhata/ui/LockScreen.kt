package bd.robin.hisabkhata.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import kotlinx.coroutines.launch

@Composable
fun LockScreen() {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var fails by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var confirmReset by remember { mutableStateOf(false) }
    val wrong = stringResource(R.string.lock_wrong)
    val wait = stringResource(R.string.lock_wait)
    val canBio = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun submit(value: String) {
        val current = settings ?: return
        if (System.currentTimeMillis() < lockedUntil) {
            message = wait
            return
        }
        if (value.length < 4) return
        if (container.passkey.matches(value)) {
            container.session.unlock()
        } else {
            fails += 1
            pin = ""
            if (fails >= 5) {
                lockedUntil = System.currentTimeMillis() + 30_000
                fails = 0
                message = wait
            } else {
                message = wrong
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), color = HeaderRed, style = MaterialTheme.typography.headlineMedium)
        Text(settings?.accountEmail?.ifBlank { settings.accountName }.orEmpty(), color = Color(0xFF666666), modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
        Text(stringResource(R.string.pin_login), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 18.dp)) {
            repeat(4) { index ->
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(
                        if (index < pin.length) HeaderRed else Color(0xFFE0E0E0),
                    ),
                )
            }
        }
        if (message.isNotBlank()) Text(message, color = HeaderRed, modifier = Modifier.padding(bottom = 8.dp))
        Surface(onClick = { confirmReset = true }, shape = RoundedCornerShape(24.dp), color = Color(0xFFF2F2F2), modifier = Modifier.tag("pin_forgot")) {
            Text(stringResource(R.string.pin_forgot_pill), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Keypad(
            onDigit = {
                if (pin.length < 6) {
                    pin += it.toString()
                    if (pin.length == 4 && container.passkey.matches(pin)) submit(pin)
                    if (pin.length == 6) submit(pin)
                }
            },
            onDelete = { pin = pin.dropLast(1) },
            onClear = { pin = "" },
        )
        if (canBio && settings?.biometricEnabled == true) {
            TextButton(onClick = {
                val activity = context as? FragmentActivity ?: return@TextButton
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            container.session.unlock()
                        }
                    },
                )
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(context.getString(R.string.biometric_prompt))
                        .setNegativeButtonText(context.getString(R.string.pin_use))
                        .build(),
                )
            }) { Text(stringResource(R.string.biometric), color = HeaderRed) }
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.lock_reset_title),
            body = stringResource(R.string.lock_reset_body),
            confirm = stringResource(R.string.lock_reset_confirm),
            onConfirm = {
                confirmReset = false
                val activity = context as? android.app.Activity ?: return@ConfirmDialog
                scope.launch {
                    runCatching { container.auth.signInWithGoogle(activity) }
                        .onSuccess { profile ->
                            val uid = container.settings.current().accountUid
                            if (profile.uid != uid) {
                                context.toast(context.getString(R.string.passkey_wrong_account))
                            } else {
                                container.passkey.clear()
                                container.session.lock()
                            }
                        }
                        .onFailure { context.toast(context.getString(R.string.google_failed)) }
                }
            },
            onDismiss = { confirmReset = false },
        )
    }
}
