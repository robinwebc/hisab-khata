package bd.robin.hisabkhata.data

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class Session {
    val deviceUnlocked = MutableStateFlow(false)
    val pinUnlocked = MutableStateFlow(false)

    fun unlockDevice() {
        deviceUnlocked.value = true
    }

    fun unlock() {
        deviceUnlocked.value = true
        pinUnlocked.value = true
    }

    fun lock() {
        deviceUnlocked.value = false
        pinUnlocked.value = false
    }

    fun lockPin() {
        pinUnlocked.value = false
    }
}

class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val signals = SyncSignals()
    val session = Session()
    val db = HisabDatabase.create(app)
    val vault = VaultDatabase.create(app)
    val settings = SettingsRepository(app, scope, signals)
    val repo = LedgerRepository(db, vault, app, signals)
    val faces by lazy { FaceEngine(app) }
    val auth = AuthGateway(app)
    val passkey = PasskeyStore(app)
    val sync = SyncRepository(app, repo, settings)
    val drive = DriveBackup(app, repo, settings)
    val account = AccountGate(auth, repo, settings, session, sync)
    val syncCenter = SyncCenter(app, sync, signals, scope)
    val driveCenter = DriveCenter(app, settings, signals, scope)
}
