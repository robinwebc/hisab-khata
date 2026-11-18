package bd.robin.hisabkhata.data

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class Session {
    val unlocked = MutableStateFlow(false)
    fun lock() { unlocked.value = false }
    fun unlock() { unlocked.value = true }
}

class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val session = Session()
    val db = HisabDatabase.create(app)
    val vault = VaultDatabase.create(app)
    val settings = SettingsRepository(app, scope)
    val repo = LedgerRepository(db, vault, app)
    val faces by lazy { FaceEngine(app) }
    val auth = AuthGateway(app)
    val sync = SyncRepository(app, repo, settings)
}
