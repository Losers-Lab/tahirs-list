package com.tahirslist.certifiers.core

/**
 * Minimal RFC 9309 robots.txt evaluation: the most specific matching group for
 * our user-agent wins, and the longest matching rule decides (with `Allow`
 * winning ties). `Crawl-delay` is intentionally ignored — our own
 * [FetchPolicy.minInterval] is stricter and applies to every host.
 */
class RobotsPolicy internal constructor(
    private val rules: List<Rule>,
) {
    internal data class Rule(val allow: Boolean, val pattern: String)

    /** True when [pathAndQuery] may be fetched. An empty rule set allows all. */
    fun allows(pathAndQuery: String): Boolean {
        if (rules.isEmpty()) return true
        val match = rules
            .filter { it.matches(pathAndQuery) }
            .maxWithOrNull(compareBy({ it.pattern.length }, { it.allow }))
        return match == null || match.allow
    }

    private fun Rule.matches(path: String): Boolean {
        val body = pattern.removeSuffix("$")
        val anchoredAtEnd = pattern.endsWith("$")
        val regex = buildString {
            append('^')
            body.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    else -> append(Regex.escape(ch.toString()))
                }
            }
            if (anchoredAtEnd) append('$')
        }
        return Regex(regex).containsMatchIn(path)
    }

    companion object {
        val allowAll = RobotsPolicy(emptyList())

        fun parse(robotsTxt: String, userAgent: String): RobotsPolicy {
            val token = userAgent.substringBefore('/').trim().lowercase()
            val groups = mutableListOf<Group>()
            var current: Group? = null
            var lastWasAgent = false

            for (rawLine in robotsTxt.lineSequence()) {
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val separator = line.indexOf(':')
                if (separator < 0) continue
                val field = line.substring(0, separator).trim().lowercase()
                val value = line.substring(separator + 1).trim()

                when (field) {
                    "user-agent" -> {
                        if (!lastWasAgent) {
                            val group = Group()
                            groups += group
                            current = group
                        }
                        current?.agents?.add(value.lowercase())
                        lastWasAgent = true
                    }

                    "allow", "disallow" -> {
                        lastWasAgent = false
                        if (value.isEmpty()) continue
                        current?.rules?.add(Rule(allow = field == "allow", pattern = value))
                    }

                    else -> lastWasAgent = false
                }
            }

            val matching = groups.filter { group ->
                group.agents.any { agent -> agent != "*" && token.isNotEmpty() && agent == token }
            }.ifEmpty {
                groups.filter { "*" in it.agents }
            }

            return RobotsPolicy(matching.flatMap { it.rules })
        }

        private data class Group(
            val agents: MutableList<String> = mutableListOf(),
            val rules: MutableList<Rule> = mutableListOf(),
        )
    }
}
