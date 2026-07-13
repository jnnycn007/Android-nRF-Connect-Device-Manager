package no.nordicsemi.android.mcumgr.sample.di.module;

import android.content.Context
import dagger.Module
import dagger.Provides
import no.nordicsemi.android.mcumgr.sample.di.McuMgrScope
import no.nordicsemi.android.observability.ObservabilityManager
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.log.Log
import no.nordicsemi.kotlin.log.timber.Timber

@Module
class ObservabilityModule {

    @Provides
    @McuMgrScope
    fun provideObservabilityManager(context: Context, centralManager: CentralManager, peripheral: Peripheral): ObservabilityManager {
        val om = ObservabilityManager.create(context)
        om.logger = Log.Sink.Timber { _, _ -> true }
        om.connect(peripheral, centralManager)
        return om
    }
}
