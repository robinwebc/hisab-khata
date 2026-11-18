package bd.robin.hisabkhata.data

import kotlinx.coroutines.flow.MutableSharedFlow

class SyncSignals {
    val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    fun poke() {
        ticks.tryEmit(Unit)
    }
}
