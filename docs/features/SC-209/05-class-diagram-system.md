> **Purpose:** show the system shape before technology · **Derived from:** 01, 02 · **Delta:** +2 controls, +5 boundaries, +2 policy helpers, +1 port

# 05 — System class diagram: SC-209

## Overview

```mermaid
classDiagram
    McpHttpEndpoint --> AgentRequestGate
    McpHttpEndpoint --> SearchRestaurantsTool
    McpHttpEndpoint --> GetRestaurantTool
    SearchRestaurantsTool --> SearchRequestParser
    SearchRestaurantsTool --> SearchListingsForAgent
    SearchRestaurantsTool --> AgentResultMapper
    GetRestaurantTool --> GetListingForAgent
    GetRestaurantTool --> AgentResultMapper
    SearchListingsForAgent --> GeocoderPort
    SearchListingsForAgent --> ListingSearchQuery
    GetListingForAgent --> RestaurantListingRepository
    GetListingForAgent --> HalalCertificationReviewRepository
    GetListingForAgent --> CertificateFactsPolicy
    AgentRequestGate --> AgentRateLimiter
```

## Domain

```mermaid
classDiagram
    class RestaurantListing
    class ListingSearchResult
    class HalalCertificationReview
    class LatLng
    class Cuisine
    class Price
    class Rating
    class AgentSearchRequest
    class AgentSearchResult
    class AgentListingDetail
    class CertificateFacts
    class SearchLocation
    class PlaceText
    class Coordinates
    SearchLocation <|-- PlaceText
    SearchLocation <|-- Coordinates
    AgentSearchRequest --> SearchLocation
    AgentSearchResult "1" o-- "0..*" ListingSearchResult
    AgentListingDetail --> RestaurantListing
    AgentListingDetail --> CertificateFacts
    CertificateFacts ..> HalalCertificationReview : derived from
    RestaurantListing --> LatLng
    RestaurantListing --> Cuisine
    RestaurantListing --> Price
    RestaurantListing --> Rating
```

## Application

```mermaid
classDiagram
    class SearchListingsForAgent {
        +execute(request) AgentSearchResult
    }
    class GetListingForAgent {
        +execute(id) AgentListingDetail
    }
    class SearchRequestParser {
        +parse(raw) AgentSearchRequest
        +parseListingId(raw) ListingId
    }
    class CertificateFactsPolicy {
        +derive(listing, latestApproved) CertificateFacts?
    }
    class InvalidSearchArgumentsException
    class PlaceNotResolvedException
    class AgentListingNotFoundException
    SearchRequestParser ..> InvalidSearchArgumentsException
    SearchListingsForAgent ..> PlaceNotResolvedException
    GetListingForAgent ..> AgentListingNotFoundException
    GetListingForAgent ..> CertificateFactsPolicy
```

## Boundary

```mermaid
classDiagram
    class McpHttpEndpoint {
        +handle(request) response
    }
    class AgentRequestGate {
        +allow(clientKey) boolean
    }
    class SearchRestaurantsTool {
        +call(arguments) ToolResult
    }
    class GetRestaurantTool {
        +call(arguments) ToolResult
    }
    class AgentResultMapper {
        +toCards(result) StructuredSearchResult
        +toDetail(detail) StructuredDetail
        +toError(message) ToolResult
    }
    McpHttpEndpoint --> AgentRequestGate
    McpHttpEndpoint --> SearchRestaurantsTool
    McpHttpEndpoint --> GetRestaurantTool
    SearchRestaurantsTool --> AgentResultMapper
    GetRestaurantTool --> AgentResultMapper
```

## Ports

```mermaid
classDiagram
    class ListingSearchQuery <<interface>>
    class RestaurantListingRepository <<interface>>
    class HalalCertificationReviewRepository <<interface>>
    class GeocoderPort <<interface>>
    class AgentRateLimiter <<interface>>
```

| Port | Operation | Adapter stub | Store / external |
|---|---|---|---|
| `ListingSearchQuery` | `searchNearby(centre, radius, filters, offset, limit)` | `ExposedListingSearchQuery` | PostgreSQL/PostGIS |
| `RestaurantListingRepository` | `findById(id)` | `ExposedRestaurantListingRepository` | PostgreSQL/PostGIS |
| `HalalCertificationReviewRepository` | `findLatestApprovedByListing(id)` | `ExposedHalalCertificationReviewRepository` | PostgreSQL/PostGIS |
| `GeocoderPort` | `geocode(address)` | `PhotonGeocoder` | Photon API (HTTP) |
| `AgentRateLimiter` | `tryAcquire(key)` | `Bucket4jAgentRateLimiter` | in-process |

UC-01 and UC-02 are existing goals; this feature serves them through the MCP channel.

## Operation inventory

| Use case step | Operation |
|---|---|
| UC-01.2–3 | `McpHttpEndpoint.handle` → `AgentRequestGate.allow` → `SearchRestaurantsTool.call` → `SearchRequestParser.parse` |
| UC-01.4 | `SearchListingsForAgent.execute` → `GeocoderPort.geocode` |
| UC-01.5–6 | `ListingSearchQuery.searchNearby` → `AgentResultMapper.toCards` |
| UC-01.A1–A3 | Coordinates skip the place branch; empty/nextOffset are mapped |
| UC-01.A4 | Hand-off to `GetRestaurantTool.call` |
| UC-01.E1 | `SearchRequestParser.parse` throws → `AgentResultMapper.toError` |
| UC-01.E2–E3 | `GeocoderPort.geocode` null/throws → `PlaceNotResolvedException` / provider error → `toError` |
| UC-01.E4–E7 | Gate false / protocol / origin / store failure at the boundary |
| UC-02.2–3 | `McpHttpEndpoint.handle` → gate → `GetRestaurantTool.call` → `parseListingId` |
| UC-02.4–6 | `GetListingForAgent.execute` → `findById` → `findLatestApprovedByListing` → `CertificateFactsPolicy.derive` → `toDetail` |
| UC-02.A1–A2 | Policy returns null; scope/items reported independently |
| UC-02.E1–E6 | `findById` null → `AgentListingNotFoundException`; parse/transport/store failures |

**Check:** every UC step resolves to an operation; every external dependency sits
behind an interface; one control class per use case; no orphan classes.
