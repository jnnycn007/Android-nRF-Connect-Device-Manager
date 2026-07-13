package no.nordicsemi.android.observability.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import no.nordicsemi.android.observability.ObservabilityManager
import no.nordicsemi.android.observability.log.Category
import no.nordicsemi.kotlin.log.Log

/**
 * The API of the source of diagnostics chunks.
 *
 * The implementation emits chunks, that can be sent to nRF Cloud using [ObservabilityManager].
 */
interface ChunksEmitter {

    /** The current state of the source. */
    val state: StateFlow<State>

    /**
     * A flow of streamed data (chunks).
     */
    val chunks: Flow<ByteArray>

    /**
     * The log sink for events produced by this emitter.
     */
    var logger: Log.Sink<Category>?

    /**
     * Represents the state of emitter.
     */
    sealed class State {
        /**
         * The initial state, when the emitter gets initialized.
         */
        data object Initializing : State()

        /**
         * The emitter is set up and streaming diagnostic data.
         *
         * @property config The configuration to be used to send chunks to the cloud.
         */
        data class Ready(val config: ChunksConfig) : State()

        /**
         * The emitter is stopped.
         */
        data object Disconnected : State()
    }
}