package com.tahirslist.bootstrap.image

import com.tahirslist.application.image.ImageFetchException
import java.net.InetAddress
import java.net.URI

/**
 * A rule a fetch target must satisfy before any socket is opened. The photo
 * pass fetches URLs that originate in a third party's JSON (a Commons
 * `thumburl`, a Places media name), so the target is untrusted input.
 */
fun interface UrlPolicy {

    /** @throws ImageFetchException when [uri] must not be fetched. */
    fun check(uri: URI)
}

/**
 * The production policy: **https only, public hosts only**.
 *
 * A plain-http target can be redirected or read by anything on the path, and a
 * loopback/private/link-local target would let third-party JSON aim our own
 * network (metadata services, the PostGIS container, the S3 endpoint) at
 * itself. IP literals are checked directly; a hostname is resolved and every
 * resolved address is checked.
 */
object PublicHttpsOnly : UrlPolicy {

    override fun check(uri: URI) {
        val display = uri.toString().substringBefore('?')
        if (uri.scheme?.lowercase() != "https") {
            throw ImageFetchException("refusing non-https image url $display")
        }
        val host = uri.host ?: throw ImageFetchException("image url has no host: $display")
        if (host.equals("localhost", ignoreCase = true)) {
            throw ImageFetchException("refusing loopback image host: $display")
        }
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        if (addresses.isEmpty()) throw ImageFetchException("cannot resolve image host: $display")
        addresses.firstOrNull {
            it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress
        }?.let {
            throw ImageFetchException("refusing private image host: $display")
        }
    }
}
