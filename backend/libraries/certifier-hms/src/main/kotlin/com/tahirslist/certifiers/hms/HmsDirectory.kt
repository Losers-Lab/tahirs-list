package com.tahirslist.certifiers.hms

import com.tahirslist.certifiers.core.CertifierHttp
import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.core.CertifierProfile
import com.tahirslist.certifiers.core.SourceParseException
import kotlin.coroutines.cancellation.CancellationException

/**
 * HMS certified-listing client.
 *
 * ```kotlin
 * val http = CertifierHttp.create()
 * val hms = HmsDirectory(http)
 * val restaurants = hms.certifiedRestaurants().records
 * ```
 *
 * One request returns the whole directory (687 records at the time of the
 * access spike). Coordinates are directory-supplied evidence; callers must
 * still confirm a location through their own geocoder.
 */
class HmsDirectory(
    private val http: CertifierHttp,
    val profile: CertifierProfile = HmsProfile,
) {
    suspend fun certifiedListing(refresh: Boolean = false): CertifierPage<HmsEstablishment> {
        val result = http.get(LISTING_URL, accept = "text/html", refresh = refresh)
        val records = try {
            HmsParser.parse(result.snapshot.body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceParseException) {
            throw e
        } catch (e: Exception) {
            throw SourceParseException("hms", "Failed to parse the HMS listing", e)
        }

        val warnings = buildList {
            val missingLocation = records.count { it.location == null }
            if (missingLocation > 0) {
                add("$missingLocation HMS establishments have no directory coordinates; geocoding is required")
            }
            val missingCity = records.count { it.address.city == null }
            if (missingCity > 0) {
                add("$missingCity HMS establishments have no city in the directory")
            }
        }
        return CertifierPage(profile, records, listOf(result.snapshot), warnings)
    }

    /** Convenience: the same page filtered to restaurant-category records. */
    suspend fun certifiedRestaurants(refresh: Boolean = false): CertifierPage<HmsEstablishment> {
        val page = certifiedListing(refresh)
        return page.copy(records = page.records.filter { it.isRestaurant })
    }

    companion object {
        const val LISTING_URL: String = "https://hmsusa.org/certified-listing/"
    }
}
