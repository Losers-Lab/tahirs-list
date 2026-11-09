package com.tahirslist.ingestion.pipeline

import com.tahirslist.certifiers.core.CertifierHttp
import com.tahirslist.certifiers.core.SourceSnapshot
import com.tahirslist.certifiers.hfsaa.HfsaaLocator
import com.tahirslist.certifiers.hfsaa.HfsaaProfile
import com.tahirslist.certifiers.hma.HmaCertifiedOutlets
import com.tahirslist.certifiers.hma.HmaProfile
import com.tahirslist.certifiers.hms.HmsDirectory
import com.tahirslist.certifiers.hms.HmsProfile
import com.tahirslist.ingestion.HfsaaTranslator
import com.tahirslist.ingestion.HmaTranslator
import com.tahirslist.ingestion.HmsTranslator
import com.tahirslist.ingestion.SourceListing
import java.nio.file.Path

/**
 * One directory read, already translated into the app's source-listing model,
 * with the raw snapshots it came from. The feed is the only thing a run knows
 * about a certifier; the library and its model never leak past it.
 */
data class DirectoryPage(
    val sourceId: String,
    val listings: List<SourceListing>,
    val snapshots: List<SourceSnapshot>,
    val warnings: List<String> = emptyList(),
)

/** A certifier directory the run can read, translated and ready to filter. */
interface DirectoryFeed {
    val sourceId: String
    suspend fun fetch(refresh: Boolean = false): DirectoryPage
}

class HmsFeed(http: CertifierHttp) : DirectoryFeed {
    private val directory = HmsDirectory(http)
    private val translator = HmsTranslator()

    override val sourceId: String = "hms"

    override suspend fun fetch(refresh: Boolean): DirectoryPage {
        val page = directory.certifiedRestaurants(refresh)
        return DirectoryPage(sourceId, translator.translate(page), page.snapshots, page.warnings)
    }
}

class HfsaaFeed(http: CertifierHttp) : DirectoryFeed {
    private val locator = HfsaaLocator(http)
    private val translator = HfsaaTranslator()

    override val sourceId: String = "hfsaa"

    override suspend fun fetch(refresh: Boolean): DirectoryPage {
        val page = locator.entries(chapter = null, refresh = refresh)
        return DirectoryPage(sourceId, translator.translate(page), page.snapshots, page.warnings)
    }
}

class HmaFeed(http: CertifierHttp) : DirectoryFeed {
    private val outlets = HmaCertifiedOutlets(http)
    private val translator = HmaTranslator()

    override val sourceId: String = "hma"

    override suspend fun fetch(refresh: Boolean): DirectoryPage {
        val page = outlets.outlets(refresh)
        return DirectoryPage(sourceId, translator.translate(page), page.snapshots, page.warnings)
    }
}

/** The three Tier-1 feeds, each with its own allow-listed HTTP client. */
fun tierOneFeeds(cacheDir: Path): List<DirectoryFeed> = listOf(
    HmsFeed(CertifierHttp.createFor(HmsProfile, cacheDir)),
    HfsaaFeed(CertifierHttp.createFor(HfsaaProfile, cacheDir)),
    HmaFeed(CertifierHttp.createFor(HmaProfile, cacheDir)),
)
