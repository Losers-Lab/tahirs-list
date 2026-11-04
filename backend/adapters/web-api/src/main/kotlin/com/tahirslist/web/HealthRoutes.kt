package com.tahirslist.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/** Liveness payload; the exact JSON body is part of the orchestration contract. */
@Serializable
data class HealthResponse(val status: String)

/**
 * Liveness probe for the assembled application. Public by design (orchestrators
 * must be able to confirm the web adapter is up without credentials).
 */
fun Route.healthRoutes() {
    get("/v1/health") {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
    }
}
