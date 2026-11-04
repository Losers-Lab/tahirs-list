package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe

/**
 * Boot smoke test: proves the assembled Ktor application boots (CIO engine +
 * Koin graph + a live PostGIS datasource + Flyway migration) and serves the
 * `/v1/health` liveness probe.
 *
 * The exact JSON body is part of the orchestration contract, so it is asserted
 * verbatim.
 */
class HealthEndpointTest : KtorPostgresTest() {

    init {
        test("GET /v1/health returns 200 with status ok") {
            val resp = get("/v1/health")

            resp.status shouldBe 200
            resp.body shouldBe """{"status":"ok"}"""
            resp.json()["status"].asString() shouldBe "ok"
        }
    }
}
