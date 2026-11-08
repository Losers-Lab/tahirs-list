package com.tahirslist.certifiers.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RobotsPolicyTest : FunSpec({

    val ua = "TahirsListBot/0.1"

    test("allows everything when there are no rules") {
        RobotsPolicy.parse("", ua).allows("/anything") shouldBe true
    }

    test("applies the wildcard group") {
        val policy = RobotsPolicy.parse(
            """
            User-agent: *
            Disallow: /private
            Allow: /private/public
            """.trimIndent(),
            ua,
        )

        policy.allows("/private/secret") shouldBe false
        policy.allows("/private/public/page") shouldBe true
        policy.allows("/open") shouldBe true
    }

    test("prefers the most specific matching group") {
        val policy = RobotsPolicy.parse(
            """
            User-agent: *
            Disallow: /

            User-agent: TahirsListBot
            Allow: /
            """.trimIndent(),
            ua,
        )

        policy.allows("/listing") shouldBe true
    }

    test("longest match wins") {
        val policy = RobotsPolicy.parse(
            """
            User-agent: *
            Allow: /a
            Disallow: /a/b
            """.trimIndent(),
            ua,
        )

        policy.allows("/a/x") shouldBe true
        policy.allows("/a/b/x") shouldBe false
    }

    test("supports the end anchor") {
        val policy = RobotsPolicy.parse(
            """
            User-agent: *
            Disallow: /*.json$
            """.trimIndent(),
            ua,
        )

        policy.allows("/data.json") shouldBe false
        policy.allows("/data.json?x=1") shouldBe true
    }

    test("an empty disallow means allow all") {
        val policy = RobotsPolicy.parse(
            """
            User-agent: *
            Disallow:
            """.trimIndent(),
            ua,
        )

        policy.allows("/anything") shouldBe true
    }

    test("comments and blank lines are ignored") {
        val policy = RobotsPolicy.parse(
            """
            # a comment
            User-agent: *

            Disallow: /secret   # inline comment
            """.trimIndent(),
            ua,
        )

        policy.allows("/secret/page") shouldBe false
        policy.allows("/public") shouldBe true
    }
})
