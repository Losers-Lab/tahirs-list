package com.tahirslist.application.agent

/**
 * Actionable failures of the agent surface. The MCP adapter turns each into a
 * tool result with `isError = true` and a message safe to show a model; the
 * transport-level refusals (429/403) stay at HTTP.
 */

/** A caller-supplied argument violates the public contract. */
class InvalidSearchArgumentsException(message: String) : IllegalArgumentException(message)

/** The geocoder answered successfully but found no place matching the text. */
class PlaceNotResolvedException :
    Exception("No place matched that text. Try a nearby city, or supply coordinates.")

/** The requested listing id does not exist. */
class AgentListingNotFoundException :
    Exception("Restaurant not found. Use an id returned by search_restaurants.")
