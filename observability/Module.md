# Module observability

The `observability` module provides integration with nRF Cloud Monitoring & Diagnostics Service (MDS). It allows devices to upload binary "chunks" of data (e.g., logs, core dumps) to the cloud via the mobile app.

## Key Components

- **`ObservabilityManager`**: The main interface for managing the connection to a device supporting MDS. It handles chunk retrieval from the device and subsequent upload to nRF Cloud.
- **`Chunk`**: Represents a piece of data retrieved from the device.

## Example

```kotlin
val observabilityManager = ObservabilityManager.create(context)

// Connect to a device
observabilityManager.connect(environment, bluetoothDevice)

// Observe the state (connection status, chunks pending/uploaded, etc.)
lifecycleScope.launch {
    observabilityManager.state.collect { state ->
        val connected = state.state is State.Connected
        val pending = state.chunksPending
        // Update UI...
    }
}

// Disconnect when done
observabilityManager.disconnect()
```

# Package no.nordicsemi.android.observability

Contains the `ObservabilityManager` interface and its factory methods.

# Package no.nordicsemi.android.observability.data

Contains public data models used by the manager, such as `Chunk` and `ChunksConfig`.

# Package no.nordicsemi.android.observability.bluetooth

Contains Bluetooth-related classes for the Monitoring & Diagnostic Service.
