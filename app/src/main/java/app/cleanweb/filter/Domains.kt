package app.cleanweb.filter

/** Hostname helpers for the quick-block screen. */
object Domains {
    // Common second-level suffixes; enough to find "the site" without the full public suffix list.
    private val SECOND_LEVEL = setOf(
        "co.uk", "org.uk", "com.ua", "org.ua", "net.ua", "in.ua", "kiev.ua", "kyiv.ua", "gov.ua",
        "com.ru", "org.ru", "net.ru", "msk.ru", "spb.ru", "com.au", "co.jp", "co.kr", "com.br",
        "com.tr", "com.cn", "co.in", "co.il", "com.pl", "com.by", "co.za", "github.io", "blogspot.com",
        "appspot.com", "cloudfront.net", "herokuapp.com", "netlify.app", "vercel.app", "pages.dev",
    )

    /** Registrable domain: ads6.ads.kingads.digital → kingads.digital */
    fun site(host: String): String {
        val parts = host.lowercase().trimEnd('.').split('.')
        if (parts.size <= 2) return parts.joinToString(".")
        val last2 = parts.takeLast(2).joinToString(".")
        return if (last2 in SECOND_LEVEL) parts.takeLast(3).joinToString(".") else last2
    }

    /** Hosts from any http(s) links in shared text. */
    fun hostsInText(text: String): List<String> =
        Regex("https?://([A-Za-z0-9.-]+)", RegexOption.IGNORE_CASE).findAll(text)
            .mapNotNull { RuleSet.normalizeDomain(it.groupValues[1]) }
            .distinct().toList()

    /** Sites whose blocking breaks a lot of the internet; the UI asks before blocking them. */
    private val ESSENTIAL = setOf(
        "google.com", "googleapis.com", "gstatic.com", "googlevideo.com", "youtube.com", "ytimg.com",
        "googleusercontent.com", "android.com", "gvt1.com", "apple.com", "icloud.com", "samsung.com",
        "samsungapps.com", "microsoft.com", "live.com", "facebook.com", "fbcdn.net", "instagram.com",
        "whatsapp.net", "whatsapp.com", "telegram.org", "t.me", "cloudflare.com", "akamaized.net",
        "akamai.net", "cloudfront.net", "amazonaws.com", "jsdelivr.net", "github.com", "githubusercontent.com",
        "yandex.ru", "yandex.net", "vk.com", "userapi.com", "mail.ru", "wikipedia.org",
    )

    fun isEssential(site: String) = site in ESSENTIAL
}
