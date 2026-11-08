# 05 — System class diagram (conceptual): SC-209

Analysis-level shape only: entities, one control class per use case, boundary
classes, ports as interfaces, and adapter stubs. No tables, columns, JSON shapes
or framework annotations (those are Phase 7).

## 5.1 Overview

```mermaid
classDiagram
    direction LR

    class McpHttpEndpoint
    class AgentRequestGate
    class SearchRestaurantsTool
    class GetRestaurantTool
    class AgentResultMapper

    class SearchListingsForAgent
    class GetListingForAgent
    class SearchRequestParser
    class CertificateFactsPolicy

    class ListingSearchQuery
    class RestaurantListingRepository
    class HalalCertificationReviewRepository
    class GeocoderPort
    class AgentRateLimiter

    McpHttpEndpoint --> AgentRequestGate : admits
    McpHttpEndpoint --> SearchRestaurantsTool : dispatches
    McpHttpEndpoint --> GetRestaurantTool : dispatches
    SearchRestaurantsTool --> SearchRequestParser : validates
    SearchRestaurantsTool --> SearchListingsForAgent : executes
    SearchRestaurantsTool --> AgentResultMapper : presents
    GetRestaurantTool --> GetListingForAgent : executes
    GetRestaurantTool --> AgentResultMapper : presents
    SearchListingsForAgent --> GeocoderPort : resolves place
    SearchListingsForAgent --> ListingSearchQuery : searches
    GetListingForAgent --> RestaurantListingRepository : loads
    GetListingForAgent --> HalalCertificationReviewRepository : reads review
    GetListingForAgent --> CertificateFactsPolicy : derives facts
    AgentRequestGate --> AgentRateLimiter : budget
```

## 5.2 Domain / read model

```mermaid
classDiagram
    class RestaurantListing {
        +ListingId id
        +String name
        +Address address
        +LatLng location
        +Cuisine[] cuisines
        +Price? price
        +Rating? rating
        +Boolean? isHandCut
        +Boolean? isDelivery
        +HalalScope halalScope
        +CrossContamination crossContamination
        +VerificationStatus verificationStatus
    }

    class ListingSearchResult {
        +ListingId id
        +String name
        +Address address
        +LatLng location
        +Cuisine? cuisine
        +Boolean? isHandCut
        +HalalScope halalScope
        +Boolean? isDelivery
        +VerificationStatus verificationStatus
        +Rating? rating
        +Double distanceMiles
    }

    class HalalCertificationReview {
        +ReviewId id
        +VerificationState state
        +String? certifier
        +Instant? decidedAt
        +LocalDate? expiresOn
        +CertificateReference? certificate
    }

    class SearchLocation {
        <<choice>>
    }
    class PlaceText {
        +String value
    }
    class Coordinates {
        +LatLng value
    }

    class AgentSearchRequest {
        +SearchLocation location
        +double radiusMiles
        +ListingSearchFilters filters
        +int offset
        +int limit
    }

    class AgentSearchResult {
        +ListingSearchResult[] results
        +int count
        +int? nextOffset
    }

    class AgentListingDetail {
        +RestaurantListing listing
        +CertificateFacts? certificate
    }

    class CertificateFacts {
        +String? certifier
        +Instant reviewedOn
        +LocalDate? expiresOn
        +CertificateReference certificate
    }

    SearchLocation <|-- PlaceText
    SearchLocation <|-- Coordinates
    AgentSearchRequest --> SearchLocation
    AgentSearchResult "1" o-- "0..*" ListingSearchResult
    AgentListingDetail --> RestaurantListing
    AgentListingDetail --> CertificateFacts
    CertificateFacts ..> HalalCertificationReview : derived from
```

## 5.3 Application (controls and policy)

```mermaid
classDiagram
    class SearchListingsForAgent {
        +execute(request: AgentSearchRequest) AgentSearchResult
    }
    class GetListingForAgent {
        +execute(id: ListingId) AgentListingDetail
    }
    class SearchRequestParser {
        +parse(raw: RawArguments) AgentSearchRequest
        +parseListingId(raw: String) ListingId
    }
    class CertificateFactsPolicy {
        +derive(listing: RestaurantListing, latestApproved: HalalCertificationReview?) CertificateFacts?
    }

    class InvalidSearchArgumentsException {
        +String reason
    }
    class PlaceNotResolvedException
    class AgentListingNotFoundException

    SearchRequestParser ..> InvalidSearchArgumentsException : throws
    SearchListingsForAgent ..> PlaceNotResolvedException : throws
    GetListingForAgent ..> AgentListingNotFoundException : throws
    GetListingForAgent ..> CertificateFactsPolicy : uses
```

## 5.4 Boundary

```mermaid
classDiagram
    class McpHttpEndpoint {
        +handle(jsonRpcRequest) jsonRpcResponse
    }
    class AgentRequestGate {
        +allow(clientKey) boolean
    }
    class SearchRestaurantsTool {
        +call(arguments: RawArguments) ToolResult
    }
    class GetRestaurantTool {
        +call(arguments: RawArguments) ToolResult
    }
    class AgentResultMapper {
        +toCards(result: AgentSearchResult) StructuredSearchResult
        +toDetail(detail: AgentListingDetail) StructuredDetail
        +toError(message: String) ToolResult
    }

    McpHttpEndpoint --> AgentRequestGate
    McpHttpEndpoint --> SearchRestaurantsTool
    McpHttpEndpoint --> GetRestaurantTool
    SearchRestaurantsTool --> AgentResultMapper
    GetRestaurantTool --> AgentResultMapper
```

## 5.5 Ports and adapter stubs

```mermaid
classDiagram
    class ListingSearchQuery {
        <<interface>>
        +searchNearby(centre, radiusMiles, filters, offset, limit) ListingSearchResult[]
    }
    class RestaurantListingRepository {
        <<interface>>
        +findById(id) RestaurantListing?
    }
    class HalalCertificationReviewRepository {
        <<interface>>
        +findLatestApprovedByListing(id) HalalCertificationReview?
    }
    class GeocoderPort {
        <<interface>>
        +geocode(address) GeocodedPlace?
    }
    class AgentRateLimiter {
        <<interface>>
        +tryAcquire(key) boolean
    }

    class ExposedListingSearchQuery
    class ExposedRestaurantListingRepository
    class ExposedHalalCertificationReviewRepository
    class PhotonGeocoder
    class Bucket4jAgentRateLimiter

    ListingSearchQuery <|.. ExposedListingSearchQuery
    RestaurantListingRepository <|.. ExposedRestaurantListingRepository
    HalalCertificationReviewRepository <|.. ExposedHalalCertificationReviewRepository
    GeocoderPort <|.. PhotonGeocoder
    AgentRateLimiter <|.. Bucket4jAgentRateLimiter
```

## 5.6 Operation inventory (every use-case step resolves to an operation)

| Use case step | Operation |
|---|---|
| UC-01.2 | `McpHttpEndpoint.handle` → `AgentRequestGate.allow` → `SearchRestaurantsTool.call` |
| UC-01.3 | `SearchRequestParser.parse` (throws `InvalidSearchArgumentsException`) |
| UC-01.4 | `SearchListingsForAgent.execute` → `GeocoderPort.geocode` (place branch) |
| UC-01.5 | `ListingSearchQuery.searchNearby` |
| UC-01.6 | `AgentResultMapper.toCards`; `SearchRestaurantsTool.call` returns the tool result |
| UC-01.A1 | Same as main, with the place branch skipped |
| UC-01.A2 | `SearchRestaurantsTool.call` maps an empty `AgentSearchResult` |
| UC-01.A3 | `AgentSearchResult.nextOffset` |
| UC-01.A4 | Hand-off to UC-02 (`GetRestaurantTool.call`) |
| UC-01.E1 | `SearchRequestParser.parse` → `InvalidSearchArgumentsException` → `AgentResultMapper.toError` |
| UC-01.E2 | `GeocoderPort.geocode` returns null → `PlaceNotResolvedException` → `AgentResultMapper.toError` |
| UC-01.E3 | `GeocoderPort.geocode` throws provider failure → `AgentResultMapper.toError` |
| UC-01.E4 | `AgentRequestGate.allow` false → HTTP 429 |
| UC-01.E5 | `McpHttpEndpoint` protocol error |
| UC-01.E6 | `AgentRequestGate` origin refusal → HTTP 403 |
| UC-01.E7 | `ListingSearchQuery.searchNearby` failure → `AgentResultMapper.toError` (generic) |
| UC-02.2 | `McpHttpEndpoint.handle` → `AgentRequestGate.allow` → `GetRestaurantTool.call` |
| UC-02.3 | `SearchRequestParser.parseListingId` |
| UC-02.4 | `GetListingForAgent.execute` → `RestaurantListingRepository.findById` |
| UC-02.5 | `HalalCertificationReviewRepository.findLatestApprovedByListing` → `CertificateFactsPolicy.derive` |
| UC-02.6 | `AgentResultMapper.toDetail` |
| UC-02.A1 | `CertificateFactsPolicy.derive` returns null |
| UC-02.A2 | `AgentResultMapper.toDetail` reports scope and items independently |
| UC-02.E1 | `RestaurantListingRepository.findById` null → `AgentListingNotFoundException` |
| UC-02.E2 | `SearchRequestParser.parseListingId` throws `InvalidSearchArgumentsException` |
| UC-02.E3–E6 | Gate refusal / protocol error / generic error mapping |

## 5.7 Consistency checks

- Every entity/value object from Phase 1 appears (listing, review, location,
  cuisine, price, rating, hand-cut, delivery, halal scope, cross-contamination,
  verification, halal item set are carried through the existing types).
- Every use case has exactly one control class: UC-01 → `SearchListingsForAgent`,
  UC-02 → `GetListingForAgent`.
- Every external dependency is behind an interface: search query, listing
  repository, review repository, geocoder, rate limiter.
- No orphan classes; every class is used by a Phase 6 sequence or a work item.
- No framework or persistence detail leaks into this phase.
