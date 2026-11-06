package app.cleanweb.filter

enum class Verdict { NONE, BLOCKED, ALLOWED }

/** Read-only matcher over a [RuleSet]. Exceptions (`@@`, whitelist) always win. */
class FilterEngine(private val rules: RuleSet) {
    val ruleCount: Int get() = rules.size

    fun check(domain: String): Verdict {
        if (rules.allowExact.contains(domain)) return Verdict.ALLOWED
        var blocked = rules.blockExact.contains(domain)
        var d = domain
        while (true) {
            if (rules.allowWild.contains(d)) return Verdict.ALLOWED
            if (!blocked && rules.blockWild.contains(d)) blocked = true
            val dot = d.indexOf('.')
            if (dot < 0) break
            d = d.substring(dot + 1)
        }
        return if (blocked) Verdict.BLOCKED else Verdict.NONE
    }

    companion object {
        val EMPTY = FilterEngine(RuleSet())
    }
}
