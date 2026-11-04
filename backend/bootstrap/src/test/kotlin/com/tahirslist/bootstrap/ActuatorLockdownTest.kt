package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe

/**
 * The Spring Boot actuator surface is gone with the framework. The equivalent
 * lockdown assertion on Ktor is stronger than the old "not exposed" check: the
 * deny-by-default guard rejects every `/actuator/<path>` path with the generic 401
 * envelope, so no management payload can ever be reached anonymously.
 *
 * (In Spring only `/actuator/health` was exposed — with details never rendered —
 * and the rest 404'd/401'd. Ktor has no actuator at all: `/v1/health` is the
 * only health surface and it is public; everything else needs a token.)
 */
class ActuatorLockdownTest : KtorPostgresTest() {

    init {
        test("GET /actuator/health is denied by the deny-by-default guard (401, no payload)") {
            val resp = get("/actuator/health")

            // 401 — not 200, and no Spring-style {"status":"UP"} aggregate.
            resp.status shouldBe 401
            resp.code() shouldBe "unauthorized"
            resp.body.contains("\"status\"") shouldBe false
        }

        test("every other actuator endpoint is denied too (never 2xx, never a payload)") {
            for (path in listOf("info", "env", "beans", "mappings", "configprops", "metrics", "loggers")) {
                val resp = get("/actuator/$path")
                resp.status shouldBe 401
                resp.code() shouldBe "unauthorized"
                // The only forbidden outcome is a 2xx carrying actuator data.
                (resp.status in 200..299) shouldBe false
            }
        }

        test("/error (Spring's error dispatch path) does not exist either") {
            val resp = get("/error")

            resp.status shouldBe 401
            resp.code() shouldBe "unauthorized"
        }

        test("the Ktor liveness probe is the only health surface, and it is public") {
            val resp = get("/v1/health")

            resp.status shouldBe 200
            resp.body shouldBe """{"status":"ok"}"""
        }
    }
}
