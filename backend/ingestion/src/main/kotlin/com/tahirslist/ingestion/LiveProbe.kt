package com.tahirslist.ingestion

import com.tahirslist.certifiers.core.CertifierHttp
import com.tahirslist.certifiers.hfsaa.HfsaaLocator
import com.tahirslist.certifiers.hma.HmaCertifiedOutlets
import com.tahirslist.certifiers.hms.HmsDirectory
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

/**
 * Ad-hoc live probe: one polite pass over the three Tier-1 directories.
 *
 * Prints the record counts, the fields each source actually provides, and the
 * metro counts the SC-211 sample can draw from. Exits non-zero if a source is
 * empty or a metro cannot reach 15 rows, so a broken assumption fails loudly.
 *
 * Run it in the container: `./scripts/certifier-live.sh`.
 */
fun main() = runBlocking {
    val cacheDir = Path.of(
        System.getenv("CERTIFIER_CACHE_DIR") ?: "/tmp/tahirs-list-certifier-cache",
    )
    println("Tahir's List certifier live probe")
    println("cache: $cacheDir")
    println()

    val http = CertifierHttp.create(cacheDir = cacheDir, log = { println("  http  $it") })
    val hms = HmsDirectory(http)
    val hfsaa = HfsaaLocator(http)
    val hma = HmaCertifiedOutlets(http)

    val hmsPage = hms.certifiedRestaurants()
    val hfsaaPage = hfsaa.entries()
    val hmaPage = hma.outlets()

    val hmsListings = HmsTranslator().translate(hmsPage)
    val hfsaaListings = HfsaaTranslator().translate(hfsaaPage)
    val hmaListings = HmaTranslator().translate(hmaPage)

    val dallas = hmsListings.count { it.city in DALLAS }
    val hmsNyc = hmsListings.count { it.city in NYC && it.region == "New York" }
    val hfsaaNyc = hfsaaListings.count { it.region == "NY" }
    val toronto = hmaListings.count { it.city in GTA }

    println()
    println("| source | records | with coords | with certificate | sample fields |")
    println("|---|---:|---:|---:|---|")
    println(
        "| HMS | ${hmsPage.records.size} | ${hmsListings.count { it.hasCoordinates }} | " +
            "${hmsPage.records.count { it.expiresOn != null }} (expiry) | " +
            "id, name, category, address, phone, lat/lng, status, expiry, features |",
    )
    println(
        "| HFSAA | ${hfsaaPage.records.size} | ${hfsaaListings.count { it.hasCoordinates }} | " +
            "${hfsaaListings.count { it.certificateRef != null }} | " +
            "certificate id, chapter, cuisine, address, phone, lat/lng, hours, place id |",
    )
    println(
        "| HMA | ${hmaPage.records.size} | 0 (none published) | " +
            "${hmaListings.count { it.certificationStatus != null }} (statement) | " +
            "slug, name, address, phone, website, certification statement |",
    )
    println()
    println("Metro draw (before dedupe):")
    println("  Dallas  HMS=$dallas")
    println("  NYC     HMS=$hmsNyc + HFSAA=$hfsaaNyc = ${hmsNyc + hfsaaNyc}")
    println("  Toronto HMA=$toronto")
    println()
    println("Warnings:")
    (hmsPage.warnings + hfsaaPage.warnings + hmaPage.warnings).forEach { println("  - $it") }
    if (hmsPage.warnings.isEmpty() && hfsaaPage.warnings.isEmpty() && hmaPage.warnings.isEmpty()) {
        println("  - none")
    }

    val failures = buildList {
        if (hmsPage.records.isEmpty()) add("HMS returned no restaurants")
        if (hfsaaPage.records.isEmpty()) add("HFSAA returned no entries")
        if (hmaPage.records.isEmpty()) add("HMA returned no outlets")
        if (dallas < 15) add("Dallas cannot reach 15 HMS restaurants (got $dallas)")
        if (hmsNyc + hfsaaNyc < 15) add("NYC cannot reach 15 rows (got ${hmsNyc + hfsaaNyc})")
        if (toronto < 15) add("Toronto cannot reach 15 HMA outlets (got $toronto)")
    }

    println()
    if (failures.isEmpty()) {
        println("PROBE OK — every Tier-1 source is reachable and every metro has at least 15 rows.")
    } else {
        println("PROBE FAILED")
        failures.forEach { println("  - $it") }
        exitProcess(1)
    }
}

private val DALLAS = setOf(
    "Dallas", "Plano", "Richardson", "Irving", "Garland", "Carrollton", "Arlington", "Frisco",
    "McKinney", "Addison", "Lewisville", "Grapevine", "Mesquite", "Grand Prairie",
    "Farmers Branch", "Coppell", "Allen", "Denton", "Euless", "Bedford", "Hurst", "Rowlett",
    "Rockwall", "DeSoto", "Duncanville", "Cedar Hill", "Wylie", "Murphy", "Sachse",
    "The Colony", "Little Elm", "Prosper", "Celina", "Sunnyvale", "Balch Springs", "Lancaster",
    "Wilmer", "Seagoville", "Hutchins", "Glenn Heights", "Red Oak", "Midlothian", "Mansfield",
    "Kennedale", "Saginaw", "Haltom City", "North Richland Hills", "Watauga", "Keller",
    "Southlake", "Colleyville", "Flower Mound", "Highland Village", "Argyle", "Corinth",
    "Lake Dallas", "Hickory Creek", "Shady Shores", "Oak Point", "Aubrey", "Savannah",
    "Providence Village",
)

private val NYC = setOf(
    "New York", "Brooklyn", "Queens", "Bronx", "Staten Island", "Long Island City",
    "Astoria", "Flushing", "Jackson Heights", "Jamaica", "Manhattan", "Bayside",
    "Fresh Meadows", "Elmhurst", "Woodside", "Ridgewood", "Maspeth", "Corona",
    "Forest Hills", "Rego Park", "Kew Gardens", "Ozone Park", "South Ozone Park",
    "Richmond Hill", "Howard Beach", "Far Rockaway", "Rockaway Park", "Whitestone",
    "College Point", "Bay Terrace", "Douglaston", "Little Neck", "Glen Oaks", "Bellerose",
    "Floral Park", "New Hyde Park", "Jamaica Estates", "Briarwood", "Hillcrest",
    "Kew Gardens Hills", "Middle Village", "Glendale", "Sunnyside", "Woodhaven",
    "East Elmhurst", "St. Albans", "Cambria Heights", "Laurelton", "Rosedale",
    "Springfield Gardens", "Arverne", "Edgemere", "Broad Channel", "Breezy Point",
    "Neponsit", "Belle Harbor", "Great Kills", "Tottenville", "New Dorp", "Stapleton",
    "Port Richmond", "Eltingville", "Annadale", "Huguenot", "Prince's Bay", "Oakwood",
    "Midland Beach", "South Beach", "Willowbrook", "Graniteville", "Bulls Head",
    "New Springville", "Travis", "Rossville", "Charleston", "Richmond Valley",
)

private val GTA = setOf(
    "Toronto", "Mississauga", "Brampton", "Scarborough", "Etobicoke", "North York",
    "Vaughan", "Markham", "Oakville", "Richmond Hill", "Ajax", "Pickering", "Whitby",
    "Oshawa", "Burlington", "Milton", "Newmarket", "Aurora", "King City", "Woodbridge",
    "Concord", "Thornhill", "Unionville", "Stouffville", "Bowmanville", "Clarington",
    "Georgetown", "Acton", "Bolton", "Caledon", "Tottenham", "Beeton", "Alliston",
    "Bradford", "Innisfil", "Barrie", "Orangeville", "Mono", "Erin", "Halton Hills",
)
