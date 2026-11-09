package com.tahirslist.web

import com.tahirslist.application.listing.SearchSettings
import io.ktor.server.application.ApplicationCall

/**
 * The freshness header for a successful location search (SC-199). Called only on
 * the 200 path, so every error keeps the platform `no-store` default.
 */
internal object SearchCachePolicy {
    fun applyTo(call: ApplicationCall, settings: SearchSettings) {
        call.setCacheControl(settings.cacheControl())
    }
}
