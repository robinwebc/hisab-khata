package bd.robin.hisabkhata.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.ui.theme.NsBrand
import kotlinx.coroutines.launch

@Composable
fun QuickUnlockScreen() {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    var message by remember { mutableStateOf("") }
    val email = settings?.accountEmail?.ifBlank { settings.accountName }.orEmpty()
    val failed = stringResource(R.string.quick_unlock_failed)
    val missing = stringResource(R.string.quick_unlock_unavailable)

    fun prompt() {
        val activity = context as? FragmentActivity ?: return
        val authenticators = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        val can = BiometricManager.from(context).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS && can != BiometricManager.BIOMETRIC_STATUS_UNKNOWN) {
            if (can == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                message = missing
            } else {
                message = failed
            }
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.quick_unlock_title))
            .setSubtitle(context.getString(R.string.quick_unlock_subtitle))
            .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < 30) {
            info.setNegativeButtonText(context.getString(R.string.cancel))
        }
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    container.session.unlockDevice()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        message = errString.toString()
                    }
                }
            },
        ).authenticate(info.build())
    }

    LaunchedEffect(Unit) { prompt() }

    Column(
        Modifier.fillMaxSize().background(NsBrand).navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = 0.12f)) {
            Image(
                painterResource(R.drawable.ic_nsedo_mark),
                contentDescription = null,
                modifier = Modifier.size(96.dp).padding(12.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.quick_unlock_title),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (email.isNotBlank()) {
            Text(email, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            stringResource(R.string.quick_unlock_subtitle),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (message.isNotBlank()) {
            Text(message, color = Color(0xFFFFE08A), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp))
        }
        BigButton(stringResource(R.string.quick_unlock_action), modifier = Modifier.tag("quick_unlock")) { prompt() }
        TextButton(onClick = { prompt() }) {
            Text(stringResource(R.string.quick_unlock_retry), color = Color.White)
        }
        TextButton(onClick = { scope.launch { container.account.signOut() } }, modifier = Modifier.tag("other_gmail")) {
            Text(stringResource(R.string.quick_unlock_other), color = Color.White)
        }
        TextButton(onClick = { scope.launch { container.account.signOut() } }, modifier = Modifier.tag("quick_sign_out")) {
            Text(stringResource(R.string.sign_out), color = Color.White.copy(alpha = 0.8f))
        }
    }
}
