package com.tahirslist.certifiers.hfsaa

import com.tahirslist.certifiers.core.CertifierHttp
import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.core.CertifierProfile
import com.tahirslist.certifiers.core.FetchResult
import com.tahirslist.certifiers.core.SourceBlockedException
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * HFSAA public-locator client.
 *
 * ```kotlin
 * val hfsaa = HfsaaLocator(CertifierHttp.createFor(HfsaaProfile))
 * val newYork = hfsaa.entries(chapter = "New York")
 * ```
 *
 * One POST returns every chapter (313 entries at the time of the access spike).
 * If the publishable key has been rotated, the client re-discovers it once from
 * the public locator bundle — the same credential the browser already receives —
 * rather than failing the run.
 */
class HfsaaLocator(
    private val http: CertifierHttp,
    private val project: HfsaaProject = HfsaaProject.Default,
    val profile: CertifierProfile = HfsaaProfile,
    private val keyDiscovery: (suspend () -> String?)? = null,
) {
    private var activeKey: String = project.publishableKey

    suspend fun entries(
        chapter: String? = null,
        refresh: Boolean = false,
    ): CertifierPage<HfsaaEntry> {
        val result = fetch(refresh)
        val parsed = HfsaaParser.parse(result.snapshot.body)
        val records = if (chapter.isNullOrBlank()) {
            parsed.entries
        } else {
            parsed.entries.filter { it.chapter.equals(chapter, ignoreCase = true) }
        }

        val warnings = buildList {
            if (parsed.skipped > 0) add("${parsed.skipped} HFSAA rows were unusable (missing id/name)")
            if (!chapter.isNullOrBlank() && records.isEmpty()) add("HFSAA has no entries for chapter '$chapter'")
        }
        return CertifierPage(profile, records, listOf(result.snapshot), warnings)
    }

    private suspend fun fetch(refresh: Boolean): FetchResult = try {
        rpc(activeKey, refresh)
    } catch (e: SourceBlockedException) {
        // Only a rotated publishable key returns 401. A 403 is a real refusal:
        // retrying it, or re-discovering a key to retry, would be bypassing the
        // source's policy.
        if (e.status != 401) throw e
        val discovered = (keyDiscovery ?: ::discoverKey)() ?: throw e
        activeKey = discovered
        rpc(discovered, refresh)
    }

    private suspend fun rpc(key: String, refresh: Boolean): FetchResult = http.postJson(
        url = project.locatorUrl,
        json = "{}",
        headers = mapOf(
            "apikey" to key,
            "Authorization" to "Bearer $key",
        ),
        refresh = refresh,
    )

    /**
     * Reads the same public credential the locator page ships: fetch the app
     * shell, find its JS bundle, extract the `sb_publishable_…` key.
     */
    private suspend fun discoverKey(): String? {
        val home = try {
            http.get(HfsaaProject.LOCATOR_HOME, accept = "text/html").snapshot.body
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }

        val bundlePath = Regex("""<script[^>]+src="([^"]*assets/[^"]+\.js)"""")
            .find(home)?.groupValues?.get(1) ?: return null
        val bundleUrl = if (bundlePath.startsWith("http")) {
            bundlePath
        } else {
            HfsaaProject.LOCATOR_HOME.trimEnd('/') + "/" + bundlePath.trimStart('/')
        }
        // Never fetch a script the page merely points at: it must stay on the
        // locator's own allow-listed host, over HTTPS.
        val bundleUri = runCatching { URI(bundleUrl) }.getOrNull() ?: return null
        if (!bundleUri.scheme.equals("https", ignoreCase = true)) return null
        if (bundleUri.host?.lowercase() !in profile.allowedHosts.map { it.lowercase() }) return null

        val bundle = try {
            http.get(bundleUrl, accept = "text/javascript").snapshot.body
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return Regex("""sb_publishable_[A-Za-z0-9_-]+""").find(bundle)?.value
    }
}
