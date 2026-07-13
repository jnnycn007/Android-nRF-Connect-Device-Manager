/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.observability

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.observability.bluetooth.MonitoringAndDiagnosticsConnection
import no.nordicsemi.android.observability.bluetooth.MonitoringAndDiagnosticsProfile
import no.nordicsemi.android.observability.data.ChunksEmitter
import no.nordicsemi.android.observability.data.ChunksEmitter.State.Ready
import no.nordicsemi.android.observability.data.PersistentChunkQueue
import no.nordicsemi.android.observability.internal.Scope
import no.nordicsemi.android.observability.internet.ChunksUploader
import no.nordicsemi.android.observability.log.Category
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.environment.android.NativeAndroidEnvironment
import no.nordicsemi.kotlin.log.Log
import kotlin.time.Duration.Companion.milliseconds

internal class ObservabilityManagerImpl(
    context: Context,
) : ObservabilityManager {
    /** The Application Context. */
    private val context = context.applicationContext

    private val _state = MutableStateFlow(ObservabilityManager.State())
    override val state: StateFlow<ObservabilityManager.State> = _state.asStateFlow()

    override var logger: Log.Sink<Category>? = null
        set(value) {
            field = value
            ownedConnection?.logger = value
            uploadManager?.logger = value
        }

    private var job: Job? = null
    /** Set only when this manager created and owns the connection, see [connect]. */
    private var ownedConnection: MonitoringAndDiagnosticsConnection? = null
    private var chunkQueue: PersistentChunkQueue? = null
    private var uploadManager: ChunksUploader? = null

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun connect(source: ChunksEmitter) {
        check(job == null) { "Already connected" }
        source.logger = source.logger ?: logger

        job = Scope.launch {
            var connection: Job? = null

            // Collect the state of the profile and update the state flow.
            source.state
                .onEach { state ->
                    _state.value = _state.value.copy(state = state)

                    if (state is Ready) {
                        connection = launch {
                            chunkQueue = PersistentChunkQueue(
                                context = context,
                                deviceId = state.config.deviceId
                            ).also { queue ->
                                queue.chunks
                                    .onEach {
                                        _state.value = _state.value.copy(chunks = it)
                                    }
                                    .launchIn(this)
                            }
                            uploadManager = ChunksUploader(
                                config = state.config,
                                chunkQueue = chunkQueue
                            ).also { manager ->
                                manager.logger = logger
                                manager.status
                                    .onEach { state ->
                                        _state.value = _state.value.copy(uploadingState = state)

                                        // If the Project Key is invalid, shut it down.
                                        if (state is ChunksUploader.State.Unauthorized) {
                                            if (source is MonitoringAndDiagnosticsProfile) {
                                                source.close()
                                            }
                                            disconnect()
                                        }
                                    }
                                    .launchIn(this)
                                // Upload any chunks that were already in the queue.
                                manager.uploadChunks()
                            }

                            try {
                                awaitCancellation()
                            } finally {
                                // Manager is closing. Flush any remaining chunks.
                                withContext(NonCancellable) {
                                    uploadManager?.uploadChunks()
                                    uploadManager?.close()
                                    uploadManager = null
                                }

                                // Remove all uploaded chunks from the queue.
                                chunkQueue?.deleteUploaded()
                                chunkQueue = null
                            }
                        }
                    } else {
                        // The profile is not (yet, or no longer) connected.
                        // The profile may reconnect on its own; wait for the next Connected state.
                        connection?.cancel()
                        connection = null
                    }
                }
                .onCompletion {
                    _state.value = _state.value.copy(state = ChunksEmitter.State.Disconnected)
                }
                .launchIn(this)

            // Collect the chunks received from the device and upload them to the cloud.
            source.chunks
                .onEach {
                    // Mind, that that has to be called from a non-main Dispatcher
                    withContext(NonCancellable) {
                        uploadManager?.addChunks(listOf(it))
                    }
                }
                // Don't upload chunks immediately, but debounce the flow to avoid
                // multiple uploads in a short time.
                .debounce(300.milliseconds)
                .onEach {
                    uploadManager?.uploadChunks()
                }
                .launchIn(this)
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun connect(peripheral: Peripheral, centralManager: CentralManager) {
        check(job == null) { "Already connected" }

        val connection = MonitoringAndDiagnosticsConnection(centralManager, peripheral, Scope)
            .also { it.logger = logger }
        ownedConnection = connection
        connection.start()
        connect(connection.profile)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun connect(environment: NativeAndroidEnvironment, device: BluetoothDevice) {
        check(job == null) { "Already connected" }

        val connection = MonitoringAndDiagnosticsConnection(environment, device, Scope)
            .also { it.logger = logger }
        ownedConnection = connection
        connection.start()
        connect(connection.profile)
    }

    override fun disconnect() {
        job?.cancel()
        job = null

        // If this manager created the connection itself, close it too.
        ownedConnection?.close()
        ownedConnection = null
    }
}
