package com.tahirslist.application.agent

import com.tahirslist.domain.restaurant.LatLng

/**
 * How the agent asked to locate the search: free text resolved by the geocoder,
 * or an exact point. Exactly one is present in a valid [AgentSearchRequest].
 */
sealed interface SearchLocation {

    /** Free text (city, address, postal code) resolved server-side. */
    data class Place(val text: String) : SearchLocation

    /** An exact WGS84 centre supplied by the caller. */
    data class Coordinates(val center: LatLng) : SearchLocation
}
