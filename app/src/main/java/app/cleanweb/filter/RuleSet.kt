package app.cleanweb.filter

/**
 * Domain rules collected from filter lists.
 *
 * Supports hosts files, plain domain lists and the domain part of
 * AdBlock Plus / uBlock Origin / AdGuard syntax (`||domain^`, `@@||domain^`).
 * Rules that need the full URL or the page (paths, cosmetic `##`, most `$` options)
 * cannot be applied at the DNS level and are skipped.
 */
class RuleSet {
    internal val blockExact = HashSet<String>()
    internal val blockWild = HashSet<String>()
    internal val allowExact = HashSet<String>()
    internal val allowWild = HashSet<String>()

    val size: Int get() = blockExact.size + blockWild.size + allowExact.size + allowWild.size

    /** Adds one line of a filter list. Returns true if it produced a rule. */
    fun addLine(raw: String): Boolean {
        val line = raw.trim()
        if (line.isEmpty()) return false
        val c = line[0]
        if (c == '!' || c == '[' || c == '#') return false
        if (line.contains('#') && COSMETIC.any { line.contains(it) }) return false

        if (line.startsWith("@@")) {
            val d = adblockDomain(line.substring(2)) ?: return false
            allowWild.add(d)
            return true
        }
        if (line.startsWith("||")) {
            val d = adblockDomain(line) ?: return false
            blockWild.add(d)
            return true
        }
        if (c == '|' || c == '/') return false

        val hash = line.indexOf('#')
        val body = if (hash >= 0) line.substring(0, hash).trim() else line
        val parts = body.split(WHITESPACE)
        if (parts.size >= 2 && looksLikeIp(parts[0])) {
            var added = false
            for (i in 1 until parts.size) {
                val d = normalizeDomain(parts[i]) ?: continue
                if (d in HOSTS_IGNORED) continue
                blockExact.add(d)
                added = true
            }
            return added
        }
        if (parts.size == 1) {
            val d = normalizeDomain(parts[0]) ?: return false
            blockExact.add(d)
            return true
        }
        return false
    }

    fun addAll(lines: Sequence<String>): Int = lines.count { addLine(it) }

    /** Blocks a domain and all its subdomains. */
    fun block(domain: String): Boolean = normalizeDomain(domain)?.let { blockWild.add(it); true } ?: false

    /** Allows a domain and all its subdomains, overriding any block rule. */
    fun allow(domain: String): Boolean = normalizeDomain(domain)?.let { allowWild.add(it); true } ?: false

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val COSMETIC = listOf("##", "#@#", "#?#", "#$#", "#%#")
        private val HOSTS_IGNORED = setOf(
            "localhost", "localhost.localdomain", "local", "broadcasthost",
            "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
            "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
        )
        // Options that still mean "block this whole domain" when applied at DNS level.
        private val DNS_OPTIONS = setOf(
            "third-party", "3p", "first-party", "1p", "important", "all",
            "document", "doc", "popup",
        )

        private fun adblockDomain(rule: String): String? {
            if (!rule.startsWith("||")) return null
            var body = rule.substring(2)
            val dollar = body.indexOf('$')
            if (dollar >= 0) {
                val opts = body.substring(dollar + 1).split(',')
                if (opts.any { it.trim().lowercase().removePrefix("~") !in DNS_OPTIONS }) return null
                body = body.substring(0, dollar)
            }
            body = body.removeSuffix("|").removeSuffix("^")
            return normalizeDomain(body)
        }

        private fun looksLikeIp(s: String): Boolean =
            s.isNotEmpty() && s.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F' } &&
                (s.contains(':') || s.count { it == '.' } == 3)

        fun normalizeDomain(raw: String): String? {
            val d = raw.trim().lowercase().trimEnd('.')
            if (d.length < 3 || d.length > 253) return null
            if (!d.contains('.') || d.startsWith('.') || d.startsWith('-') || d.contains("..")) return null
            if (d.any { !(it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.' || it == '_') }) return null
            if (d.all { it.isDigit() || it == '.' }) return null // bare IPv4
            return d
        }
    }
}
