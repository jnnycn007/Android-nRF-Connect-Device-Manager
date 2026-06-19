# Module ota

The `ota` module provides integration with nRF Cloud Over-the-Air (OTA) firmware update services. It allows checking for the latest firmware releases for a device and downloading them.

## Key Components

- **`OtaManager`**: Used to query nRF Cloud for available updates and download firmware binaries.
- **`MemfaultManager`**: An Mcu Manager group (id = 128) used to retrieve device metadata (serial number, current software version, etc.) required for update checks.

## Example

```kotlin
val otaManager = OtaManager()

// Check for the latest release for a device connected via transport
try {
    val releaseInfo = otaManager.getLatestRelease(transport)
    when (releaseInfo) {
        is ReleaseInformation.UpdateAvailable -> {
            val releaseData = releaseInfo.data
            // Download the update
            val binary = otaManager.download(releaseData.location)
            // Use ImageManager from :mcumgr-core to perform the DFU with the binary
        }
        is ReleaseInformation.UpToDate -> {
            // Device is up to date
        }
    }
} catch (e: Exception) {
    // Handle error
}
```

# Package no.nordicsemi.android.ota

Contains the `OtaManager` and core data classes like `ReleaseInformation` and `DeviceInfo`.

# Package no.nordicsemi.android.ota.mcumgr

Contains the `MemfaultManager` used for communication with the device via SMP.
