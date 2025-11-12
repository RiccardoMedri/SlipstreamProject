package com.cesenahome.ui.download

/**
 * Simple registry used by the application to expose download dependencies to the UI module.
 */
object DownloadDependenciesRegistry {

    @Volatile
    private var infra: DownloadInfra? = null

    @Volatile
    private var ui: DownloadUi? = null

    fun registerInfra(provider: DownloadInfra) {
        infra = provider
    }

    fun registerUi(provider: DownloadUi) {
        ui = provider
    }

    fun requireInfra(): DownloadInfra {
        return infra ?: error("DownloadInfra provider has not been initialized")
    }

    fun requireUi(): DownloadUi {
        return ui ?: error("DownloadUi provider has not been initialized")
    }
}