package com.tahirslist.application.agent

import com.tahirslist.domain.restaurant.RestaurantListing

/**
 * The full detail an agent needs for one listing: the aggregate plus the
 * certificate facts, which are null unless the listing is VERIFIED with an
 * approved, archived certificate.
 */
data class AgentListingDetail(
    val listing: RestaurantListing,
    val certificate: CertificateFacts?,
)
