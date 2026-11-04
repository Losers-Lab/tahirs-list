package com.tahirslist.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/** The identity carried by the verified access JWT. */
@Serializable
data class MeResponse(val subjectId: String, val role: String)

/**
 * sc-131 representative protected route. Returns the identity carried by the
 * verified access JWT (account id as `sub`, plus the RBAC `role` claim). Without
 * a valid token the request is rejected with a generic 401 before it reaches
 * this handler.
 */
fun Route.meRoutes() {
    authenticate(JWT_AUTH_PROVIDER) {
        get("/v1/me") {
            val principal = call.jwtPrincipal()
                ?: throw ForbiddenException()
            call.respond(
                HttpStatusCode.OK,
                MeResponse(subjectId = principal.subject, role = principal.role),
            )
        }
    }
}
