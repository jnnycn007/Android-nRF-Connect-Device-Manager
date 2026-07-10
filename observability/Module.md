# Module observability

Integration with **nRF Cloud Monitoring & Diagnostics Service (MDS)**. 

This module allows devices to upload binary "chunks" of data (e.g., logs, core dumps) to the 
nRF Cloud via the mobile app using Bluetooth LE as transport protocol.

`ObservabilityManager` (which retrieves chunks and uploads them to nRF Cloud) is decoupled from
how the underlying Bluetooth LE connection is obtained. This is achieved through
`ChunksEmitter`, a small interface exposing only `state` and `chunks`, which
`ObservabilityManager.connect()` collects from.

## Key Components

- **`ObservabilityManager`**: The main interface for retrieving chunks and uploading them to
  nRF Cloud. It only depends on `ChunksEmitter`; it never touches a `Peripheral` or
  `CentralManager` directly.
- **`ChunksEmitter`**: Interface exposing the `state` and `chunks` of a source of diagnostics
  chunks, regardless of how the peripheral is connected.
- **`MonitoringAndDiagnosticsProfile`**: A `Profile` implementation of `ChunksEmitter`
  from the Kotlin BLE Library. It can be attached to any `Peripheral`, connected elsewhere in the
  application, using `peripheral.profile(...)`. It never connects or disconnects the peripheral
  itself - that remains the responsibility of whoever owns it.
- **`MonitoringAndDiagnosticsConnection`**: A helper that owns the whole Bluetooth LE connection
  (connecting, auto-reconnecting, bond state monitoring) and attaches
  `MonitoringAndDiagnosticsProfile` to it. Used internally by `ObservabilityManager` for the
  `connect(peripheral, centralManager)` and `connect(environment, device)` overloads; useful on
  its own for apps that don't otherwise manage a Kotlin BLE Library `Peripheral`.
- **`Chunk`**: Represents a piece of data retrieved from the device.
- **`ChunksUploader`**: An object for uploading enqueued chunks to nRF Cloud.

## Connecting

Depending on how the application manages its Bluetooth LE connection, use one of:

```kotlin
val observabilityManager = ObservabilityManager.create(context)

// 1. The application already attached MonitoringAndDiagnosticsProfile (or another
//    ChunksEmitter implementation) to its own, already connected Peripheral.
val profile = MonitoringAndDiagnosticsProfile()
peripheral.profile(profile)
observabilityManager.connect(profile /*, required = true */)

// 2. Same as above, but the profile is optional. If required is
//    set to true and absent, the peripheral would disconnect automatically.
observabilityManager.connect(peripheral, required = false)

// 3. The application has a Peripheral, but nothing else connects it, e.g. it exists solely
//    for this feature. The manager will connect it using the given CentralManager.
observabilityManager.connect(peripheral, centralManager)

// 4. For applications that use native BluetoothDevice or the legacy
//    Android BLE Library, instead of the new Kotlin BLE Library.
observabilityManager.connect(environment, bluetoothDevice)

// Observe the state (connection status, chunks pending/uploaded, etc.)
lifecycleScope.launch {
    observabilityManager.state.collect { state ->
        val connected = state.state is ChunksEmitter.State.Ready
        val pending = state.chunksPending
        // Update UI...
    }
}

// Stop collecting. If the manager owns the connection (options 2 and 4 above), it is also
// disconnected. Otherwise, the peripheral, owned by the caller, is left untouched.
observabilityManager.disconnect()
```

## Logging

`MonitoringAndDiagnosticsProfile` and `MonitoringAndDiagnosticsConnection` log using the Kotlin
Util Library's `Log.Sink<Category>` API, where `Category` is this library's own
`Category.MDS` category. 

By default, `logger` is `null` and nothing is logged; assign a `Log.Sink<Category>` to observe log events.
`MonitoringAndDiagnosticsConnection` also implements `Log.IdentifiableEmitter<String>`, tagging
every log entry with the peripheral's MAC address, and forwards its `logger` down to the
`MonitoringAndDiagnosticsProfile` it owns.

# Package no.nordicsemi.android.observability

Contains the `ObservabilityManager` interface and its factory methods.

# Package no.nordicsemi.android.observability.data

Contains public data models used by the manager, such as `Chunk` and `ChunksConfig`, and the
`ChunksEmitter` interface decoupling `ObservabilityManager` from emitter's implementation.

# Package no.nordicsemi.android.observability.bluetooth

Contains Bluetooth-related classes for the Monitoring & Diagnostic Service: the
`MonitoringAndDiagnosticsProfile` implementation of `ChunksEmitter`, and the
`MonitoringAndDiagnosticsConnection` helper that owns the Bluetooth LE connection.

# Package no.nordicsemi.android.observability.internet

Contains `ChunksUploader`, an object responsible for uploading chunks to nRF Cloud, powered by Memfault.

# Package no.nordicsemi.android.observability.log

Contains `Category`, the `Log.Category` used when logging events from this library.
