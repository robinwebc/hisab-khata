package bd.robin.hisabkhata.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import bd.robin.hisabkhata.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object FirebaseGate {
    fun ready(context: Context): Boolean {
        if (!BuildConfig.FIREBASE_CONFIGURED) return false
        val projectId = runCatching {
            val res = context.resources.getIdentifier("project_id", "string", context.packageName)
            if (res == 0) "" else context.getString(res)
        }.getOrDefault("")
        return projectId.isNotBlank() && !projectId.contains("placeholder", ignoreCase = true)
    }

    fun googleReady(context: Context): Boolean = ready(context) && BuildConfig.WEB_CLIENT_ID.isNotBlank()
}

data class GoogleProfile(val uid: String, val name: String, val email: String)

class AuthGateway(context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signInWithGoogle(activity: Activity): GoogleProfile = withContext(Dispatchers.Main) {
        check(FirebaseGate.googleReady(activity))
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = credentialManager.getCredential(activity, request)
        val google = GoogleIdTokenCredential.createFrom(result.credential.data)
        val credential = GoogleAuthProvider.getCredential(google.idToken, null)
        val user = FirebaseAuth.getInstance().signInWithCredential(credential).await().user
            ?: error("no user")
        GoogleProfile(user.uid, user.displayName.orEmpty(), user.email.orEmpty())
    }

    fun signOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
    }
}

class PhoneOtp(private val activity: Activity) {
    private var verificationId: String? = null

    fun send(e164: String, onSent: () -> Unit, onInstant: () -> Unit, onError: (String) -> Unit) {
        val auth = otpAuth()
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    auth.signInWithCredential(credential).addOnCompleteListener {
                        runCatching { auth.signOut() }
                        if (it.isSuccessful) onInstant() else onError(it.exception?.message ?: "otp")
                    }
                }

                override fun onVerificationFailed(error: FirebaseException) {
                    onError(error.message ?: "otp")
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    onSent()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun confirm(code: String): Boolean {
        val id = verificationId ?: return false
        val auth = otpAuth()
        val credential = PhoneAuthProvider.getCredential(id, code.trim())
        val user = auth.signInWithCredential(credential).await().user
        runCatching { auth.signOut() }
        return user != null
    }

    private fun otpAuth(): FirebaseAuth {
        val name = "hisab-otp"
        val existing = FirebaseApp.getApps(activity).firstOrNull { it.name == name }
        val app = existing ?: FirebaseApp.initializeApp(
            activity,
            FirebaseOptions.fromResource(activity) ?: error("Firebase options missing"),
            name,
        ) ?: error("Firebase app missing")
        return FirebaseAuth.getInstance(app)
    }
}
