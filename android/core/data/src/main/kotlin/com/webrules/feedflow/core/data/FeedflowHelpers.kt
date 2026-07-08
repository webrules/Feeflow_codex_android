package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowCookie
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class CommunitySettingsManager {
    private val disabled = linkedSetOf<ForumSite>()
    fun isEnabled(site: ForumSite): Boolean = site == ForumSite.Rss || !disabled.contains(site)
    fun toggle(site: ForumSite) {
        if (site == ForumSite.Rss) return
        if (!disabled.add(site)) disabled.remove(site)
    }
    val visibleSites: List<ForumSite> get() = ForumSite.entries.filter(::isEnabled)
}

data class OAuthOption(val name: String, val url: String)

data class SiteLoginConfig(
    val site: ForumSite,
    val cookieDomain: String,
    val loginUrl: String,
    val requiredCookieName: String? = null,
    val authCookieNameFragments: List<String> = emptyList(),
    val oauthOptions: List<OAuthOption> = emptyList(),
) {
    fun hasAuthenticatedSession(
        cookies: List<FeedflowCookie>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        return siteCookies(cookies).any { cookie ->
            if (cookie.value.isBlank() || cookie.expiresAtMillis?.let { it < nowMillis } == true) return@any false
            requiredCookieName?.let { cookie.name == it } == true ||
                authCookieNameFragments.any { fragment -> cookie.name.contains(fragment, ignoreCase = true) }
        }
    }

    fun siteCookies(cookies: List<FeedflowCookie>): List<FeedflowCookie> =
        cookies.filter { it.domain.lowercase().trimStart('.').endsWith(cookieDomain) }

    fun isLoginUrl(url: String?): Boolean = url?.contains(loginUrl.substringAfter("://").substringBefore("?")) == true
    fun isPostLoginNavigation(url: String?): Boolean = url?.contains(cookieDomain) == true && !isLoginUrl(url)
    fun shouldCheckCookies(url: String?): Boolean = url?.contains(cookieDomain) == true

    companion object {
        fun forSite(site: ForumSite): SiteLoginConfig? = when (site) {
            ForumSite.Rss -> null
            ForumSite.FourD4Y -> SiteLoginConfig(
                site = site,
                cookieDomain = "4d4y.com",
                loginUrl = "https://www.4d4y.com/forum/logging.php?action=login",
                authCookieNameFragments = listOf("auth", "login", "member"),
            )
            ForumSite.LinuxDo -> SiteLoginConfig(
                site = site,
                cookieDomain = "linux.do",
                loginUrl = "https://linux.do/login",
                authCookieNameFragments = listOf("_t", "remember_user_token"),
                oauthOptions = listOf("Google", "GitHub", "X", "Discord", "Apple", "Passkey").map { OAuthOption(it, "") },
            )
            ForumSite.V2ex -> SiteLoginConfig(
                site = site,
                cookieDomain = "v2ex.com",
                loginUrl = "https://v2ex.com/signin",
                authCookieNameFragments = listOf("a2"),
                oauthOptions = listOf(OAuthOption("Google", ""), OAuthOption("Solana", "")),
            )
            ForumSite.HackerNews -> SiteLoginConfig(
                site = site,
                cookieDomain = "ycombinator.com",
                loginUrl = "https://news.ycombinator.com/login",
                requiredCookieName = "user",
            )
            ForumSite.Zhihu -> SiteLoginConfig(
                site = site,
                cookieDomain = "zhihu.com",
                loginUrl = "https://www.zhihu.com/signin",
                requiredCookieName = "z_c0",
            )
        }
}
}

class LocalizationManager {
    var currentLanguage: String = "en"
    private val en = mapOf(
        "login" to "Login", "cancel" to "Cancel", "done" to "Done", "save" to "Save",
        "close" to "Close", "error" to "Error", "select_community" to "Select Community",
        "settings" to "Settings", "bookmarks" to "Bookmarks", "ai_assistant" to "AI Assistant",
        "reply" to "Reply", "reply_failed" to "Reply failed", "new_thread" to "New Thread",
        "post_failed" to "Post failed", "thread_title" to "Thread title", "share_thoughts" to "Share your thoughts",
        "signed_in" to "Signed in", "signed_out" to "Signed out", "logout" to "Logout",
        "save_session" to "Save session", "login_with_browser" to "Login with browser",
        "login_to_site" to "Login to site", "login_success" to "Login success",
        "communities" to "Communities", "manage_feeds" to "Manage feeds",
        "daily_rss_summary" to "Daily RSS Summary", "browser" to "Browser",
        "thread_bookmarks" to "Thread Bookmarks", "url_bookmarks" to "URL Bookmarks",
    )
    private val zh = en.mapValues { (_, value) -> value }
    fun localizedString(key: String): String = (if (currentLanguage == "zh") zh else en)[key] ?: key
}

object SpeechServiceState {
    var isSpeaking: Boolean = false
        private set
    fun stop() {
        isSpeaking = false
    }
}

object ImageZoom {
    fun clampScale(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
}

fun String.decodeHtmlEntities(): String {
    val named = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&nbsp;" to " ", "&mdash;" to "—", "&ndash;" to "–")
    var decoded = this
    named.forEach { (entity, value) -> decoded = decoded.replace(entity, value) }
    decoded = Regex("""&#(\d+);""").replace(decoded) { match ->
        match.groupValues[1].toIntOrNull()?.takeIf { it in 0..0x10FFFF }?.let { String(Character.toChars(it)) } ?: match.value
    }
    decoded = Regex("""&#x([0-9a-fA-F]+);""").replace(decoded) { match ->
        match.groupValues[1].toIntOrNull(16)?.takeIf { it in 0..0x10FFFF }?.let { String(Character.toChars(it)) } ?: match.value
    }
    return decoded
}

fun String.stripTags(): String = replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()

fun String.jsonUnescape(): String =
    replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\u0026", "&")

data class ParsedFeedItem(
    val title: String,
    val link: String,
    val content: String,
    val timeAgo: String = "Recent",
    val author: String = "Author",
    val id: String = link,
)
data class OpmlFeed(val title: String, val url: String)

class RssParser(private val data: ByteArray) {
    fun parse(): List<ParsedFeedItem> {
        val xml = data.toString(Charsets.UTF_8)
        val domItems = runCatching {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(ByteArrayInputStream(data))
        val rssItems = document.getElementsByTagName("item")
        if (rssItems.length > 0) {
            (0 until rssItems.length).map { index ->
                val element = rssItems.item(index) as Element
                val link = element.textAny("link")
                ParsedFeedItem(
                    title = element.textAny("title").decodeHtmlEntities(),
                    link = link,
                    content = element.firstText("content:encoded", "encoded", "content", "description", "summary").decodeHtmlEntities(),
                    timeAgo = formatFeedDate(element.firstText("pubDate", "updated", "published", "dc:date")),
                    author = element.firstText("author", "dc:creator", "creator").ifBlank { "Author" },
                    id = element.firstText("guid", "id").ifBlank { link },
                )
            }
        } else {
            val entries = document.getElementsByTagNameNS("*", "entry")
            (0 until entries.length).map { index ->
                val element = entries.item(index) as Element
                val link = element.atomLink()
                ParsedFeedItem(
                    title = element.textAny("title").decodeHtmlEntities(),
                    link = link,
                    content = element.firstText("content", "summary", "description").decodeHtmlEntities(),
                    timeAgo = formatFeedDate(element.firstText("updated", "published", "dc:date", "pubDate")),
                    author = element.firstText("name", "author", "dc:creator", "creator").ifBlank { "Author" },
                    id = element.firstText("id", "guid").ifBlank { link },
                )
            }
        }
        }.getOrDefault(emptyList())
        return domItems.ifEmpty { parseByRegex(xml) }
    }

    private fun formatFeedDate(dateString: String): String {
        val value = dateString.trim()
        if (value.isEmpty()) return "Recent"
        val now = Instant.now()
        val parsed = runCatching {
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US).parse(value, Instant::from)
        }.getOrElse {
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: return "Recent"
        val seconds = Duration.between(parsed, now).seconds
        return when {
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3_600}h"
            else -> "${seconds / 86_400}d"
        }
    }

        private fun parseByRegex(xml: String): List<ParsedFeedItem> {
            val rssItems = Regex("""<item\b[^>]*>(.*?)</item>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(xml)
                .map { it.groupValues[1] }
                .toList()
            if (rssItems.isNotEmpty()) {
                return rssItems.map { block ->
                    val link = block.tagText("link")
                    ParsedFeedItem(
                        title = block.tagText("title").decodeHtmlEntities(),
                        link = link,
                        content = block.tagText("content:encoded", "encoded", "content", "description", "summary").decodeHtmlEntities(),
                        timeAgo = formatFeedDate(block.tagText("pubDate", "updated", "published", "dc:date")),
                        author = block.tagText("author", "dc:creator", "creator").ifBlank { "Author" },
                        id = block.tagText("guid", "id").ifBlank { link },
                    )
                }
            }
            return Regex("""<entry\b[^>]*>(.*?)</entry>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(xml)
                .map { it.groupValues[1] }
                .map { block ->
                    val link = block.atomLinkText()
                    ParsedFeedItem(
                        title = block.tagText("title").decodeHtmlEntities(),
                        link = link,
                        content = block.tagText("content", "summary", "description").decodeHtmlEntities(),
                        timeAgo = formatFeedDate(block.tagText("updated", "published", "dc:date", "pubDate")),
                        author = block.tagText("name", "author", "dc:creator", "creator").ifBlank { "Author" },
                        id = block.tagText("id", "guid").ifBlank { link },
                    )
                }
                .toList()
        }
    }

    private fun String.tagText(vararg tags: String): String =
        tags.firstNotNullOfOrNull { tag ->
            val escaped = Regex.escape(tag)
            Regex("""<$escaped\b[^>]*>(.*?)</$escaped>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(this)
                ?.groupValues
                ?.get(1)
                ?.stripCdata()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun String.stripCdata(): String =
        replace(Regex("""<!\[CDATA\[""", RegexOption.IGNORE_CASE), "")
            .replace("]]>", "")

    private fun String.atomLinkText(): String {
        Regex("""<link\b([^>]*)/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(this)
            .forEach { match ->
                val attributes = match.groupValues[1]
                val rel = Regex("""\brel=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attributes)?.groupValues?.get(1).orEmpty()
                val href = Regex("""\bhref=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attributes)?.groupValues?.get(1).orEmpty()
                if (href.isNotBlank() && (rel.isBlank() || rel == "alternate")) return href
            }
            return tagText("link")
    }

class OpmlParser(private val data: ByteArray) {
    fun parse(): List<OpmlFeed> = runCatching {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(data))
        val outlines = document.getElementsByTagName("outline")
        (0 until outlines.length).mapNotNull { index ->
            val element = outlines.item(index) as Element
            val url = element.getAttribute("xmlUrl")
            if (url.isBlank()) {
                null
            } else {
                OpmlFeed(element.getAttribute("text").ifBlank { element.getAttribute("title").ifBlank { url } }, url)
            }
        }
    }.getOrDefault(emptyList())
}

private fun Element.text(tag: String): String =
    (getElementsByTagName(tag).item(0)?.textContent ?: getElementsByTagNameNS("*", tag).item(0)?.textContent).orEmpty()

private fun Element.textAny(tag: String): String =
    getElementsByTagName(tag).item(0)?.textContent?.trim()
        ?: getElementsByTagNameNS("*", tag.substringAfter(':')).item(0)?.textContent?.trim()
        ?: ""

private fun Element.firstText(vararg tags: String): String =
    tags.firstNotNullOfOrNull { tag -> textAny(tag).takeIf { it.isNotBlank() } }.orEmpty()

private fun Element.atomLink(): String {
    val links = getElementsByTagNameNS("*", "link")
    for (index in 0 until links.length) {
        val link = links.item(index) as? Element ?: continue
        val rel = link.getAttribute("rel")
        val href = link.getAttribute("href")
        if (href.isNotBlank() && (rel.isBlank() || rel == "alternate")) return href
    }
    return textAny("link")
}

object RssContentCleaner {
    fun clean(html: String): String {
        var processed = html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        processed = Regex("<img[^>]+src=[\"']([^\"'>]+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
            .replace(processed) { "\n[IMAGE:${it.groupValues[1]}]\n" }
        processed = Regex("<h([1-6])\\b[^>]*>([\\s\\S]*?)</h\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(processed) { match ->
                val level = match.groupValues[1].toIntOrNull()?.coerceIn(1, 6) ?: 2
                "\n\n${"#".repeat(level)} ${match.groupValues[2].stripHtmlInline()}\n\n"
            }
        processed = Regex("<blockquote\\b[^>]*>([\\s\\S]*?)</blockquote>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(processed) { match ->
                val quote = clean(match.groupValues[1])
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                if (quote.isBlank()) "" else "\n\n[QUOTE]$quote[/QUOTE]\n\n"
            }
        processed = Regex("<li\\b[^>]*>([\\s\\S]*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(processed) { "\n• ${it.groupValues[1].stripHtmlInline()}" }
        processed = processed
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|section|article|main|tr|table|ul|ol)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<(p|div|section|article|main|tr|table|ul|ol)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
        processed = Regex("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(processed) {
                val href = it.groupValues[1]
                if (href.startsWith("#") || href.startsWith("javascript:", ignoreCase = true)) {
                    it.groupValues[2].stripHtmlInline()
                } else {
                    val title = it.groupValues[2].stripHtmlInline().ifBlank { href }
                    "[LINK:$href|$title]"
                }
            }
        return processed
            .replace(Regex("<[^>]+>"), "")
            .decodeHtmlEntities()
            .replace("\u00A0", " ")
            .lineSequence()
            .map { line -> line.trim().replace(Regex("[ \\t]{2,}"), " ") }
            .joinToString("\n")
            .replace(Regex("(\\s*\\n\\s*){3,}"), "\n\n")
            .trim()
    }

    private fun String.stripHtmlInline(): String =
        replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), "")
            .decodeHtmlEntities()
            .replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

object RssArticleExtractor {
    fun extract(html: String, pageUrl: String): String {
        val normalized = html
            .replace(Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<noscript[^>]*>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<svg[^>]*>[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(nav|header|footer|aside|form)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")

        val candidates = buildList {
            Regex("<article\\b[^>]*>([\\s\\S]*?)</article>", RegexOption.IGNORE_CASE)
                .findAll(normalized)
                .forEach { add(it.groupValues[1]) }
            Regex("<main\\b[^>]*>([\\s\\S]*?)</main>", RegexOption.IGNORE_CASE)
                .findAll(normalized)
                .forEach { add(it.groupValues[1]) }
            Regex(
                """<([a-z0-9]+)\b[^>]*(?:role=["']main["']|class=["'][^"']*(?:post-content|entry-content|article-content|article-body|story-body|post-body|content-body|markdown-body|rich-text|prose)[^"']*["'])[^>]*>([\s\S]*?)</\1>""",
                RegexOption.IGNORE_CASE,
            ).findAll(normalized).forEach { add(it.groupValues[2]) }
            bodyHtml(normalized)?.let(::add)
        }

        return candidates
            .map { RssContentCleaner.clean(absolutizeUrls(it, pageUrl)) }
            .filter { it.length >= 80 }
            .maxByOrNull { textScore(it) }
            .orEmpty()
    }

    fun isMeaningfullyMoreComplete(summary: String, article: String): Boolean {
        val current = summary.trim()
        val expanded = article.trim()
        if (expanded.isBlank() || expanded == current) return false
        if (current.isBlank()) return true
        val minimumGain = if (current.length < 280) 80 else current.length
        return expanded.length >= current.length + minimumGain
    }

    private fun bodyHtml(html: String): String? =
        Regex("<body\\b[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: html.takeIf { it.contains("<html", ignoreCase = true) }

    private fun textScore(text: String): Int =
        text.length + Regex("""\n\s*\n""").findAll(text).count() * 120

    private fun absolutizeUrls(html: String, pageUrl: String): String {
        if (pageUrl.isBlank()) return html
        val base = runCatching { URI(pageUrl) }.getOrNull() ?: return html
        return Regex("""\b(src|href)=["']([^"']+)["']""", RegexOption.IGNORE_CASE).replace(html) { match ->
            val attribute = match.groupValues[1]
            val value = match.groupValues[2]
            val absolute = when {
                value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
                value.startsWith("//") -> "${base.scheme ?: "https"}:$value"
                value.startsWith("#") || value.startsWith("mailto:", ignoreCase = true) || value.startsWith("javascript:", ignoreCase = true) -> value
                else -> runCatching { base.resolve(value).toString() }.getOrDefault(value)
            }
            "$attribute=\"$absolute\""
        }
    }
}

data class HnItem(
    val id: Int,
    val type: String? = null,
    val by: String? = null,
    val time: Long = 0,
    val text: String? = null,
    val url: String? = null,
    val title: String? = null,
    val score: Int? = null,
    val descendants: Int? = null,
    val kids: List<Int>? = null,
    val deleted: Boolean = false,
    val dead: Boolean = false,
)

object HackerNewsJson {
    fun parseIds(json: String): List<Int> =
        Regex("\\d+").findAll(json).map { it.value.toInt() }.toList()

    fun parseItem(json: String): HnItem? {
        val id = int(json, "id") ?: return null
        return HnItem(
            id = id,
            type = string(json, "type"),
            by = string(json, "by"),
            time = int(json, "time")?.toLong() ?: 0L,
            text = string(json, "text"),
            url = string(json, "url"),
            title = string(json, "title"),
            score = int(json, "score"),
            descendants = int(json, "descendants"),
            kids = kids(json),
            deleted = bool(json, "deleted"),
            dead = bool(json, "dead"),
        )
    }

    private fun string(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\/", "/")
            ?.replace("\\n", "\n")

    private fun int(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun bool(json: String, key: String): Boolean =
        Regex("\"$key\"\\s*:\\s*true").containsMatchIn(json)

    private fun kids(json: String): List<Int>? =
        Regex("\"kids\"\\s*:\\s*\\[([^]]*)]").find(json)?.groupValues?.get(1)
            ?.let { Regex("\\d+").findAll(it).map { match -> match.value.toInt() }.toList() }
}

object HackerNewsSearchJson {
    fun parse(json: String, community: Community): SearchResult {
        val objectIdRegex = Regex("\"objectID\"\\s*:\\s*\"?(\\d+)\"?")
        val hits = objectIdRegex.findAll(json).mapNotNull { match ->
            val segment = json.substring(
                startIndex = (match.range.first - 2_000).coerceAtLeast(0),
                endIndex = (match.range.last + 500).coerceAtMost(json.length),
            )
            val id = match.groupValues[1]
            val title = string(segment, "title") ?: string(segment, "story_title") ?: return@mapNotNull null
            FeedThread(
                id = id,
                title = title.decodeJsonString(),
                content = (string(segment, "url") ?: string(segment, "story_url")).orEmpty().decodeJsonString(),
                author = User(
                    id = string(segment, "author").orEmpty().ifBlank { "unknown" },
                    username = string(segment, "author").orEmpty().ifBlank { "unknown" },
                    avatar = "person.circle.fill",
                ),
                community = community,
                timeAgo = int(segment, "created_at_i")?.toLong()?.let(HackerNewsContentCleaner::timeAgo).orEmpty(),
                likeCount = int(segment, "points") ?: 0,
                commentCount = int(segment, "num_comments") ?: 0,
            )
        }.toList()
        val hasMore = int(json, "nbPages")?.let { pageCount ->
            val page = int(json, "page") ?: 0
            page + 1 < pageCount
        } ?: false
        return SearchResult(hits, hasMore)
    }

    private fun string(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|null)").find(json)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }

    private fun int(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun String.decodeJsonString(): String =
        replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .decodeHtmlEntities()
}

object HackerNewsContentCleaner {
    fun clean(html: String): String {
        var processed = html
            .replace("<p>", "\n\n")
            .replace("<pre><code>", "\n```\n")
            .replace("</code></pre>", "\n```\n")
            .replace("<i>", "_")
            .replace("</i>", "_")
        processed = Regex("<a href=\"([^\"]+)\"[^>]*>([^<]+)</a>", RegexOption.IGNORE_CASE)
            .replace(processed) { it.groupValues[1] }
        processed = processed.replace(Regex("<[^>]+>"), "")
        return processed
            .replace("&hellip;", "...")
            .decodeHtmlEntities()
            .replace(Regex("(\\s*\\n\\s*){3,}"), "\n\n")
            .replace(Regex("\\n\\s+\\n"), "\n\n")
            .trim()
    }

    fun timeAgo(epochSeconds: Long, now: Instant = Instant.now()): String {
        val seconds = Duration.between(Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC)).seconds
        return when {
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3_600}h"
            else -> "${seconds / 86_400}d"
        }
    }
}

    object FourD4YParser {
        private val gb18030: Charset = Charset.forName("GB18030")

        fun decodeForumBytes(bytes: ByteArray): String = String(bytes, gb18030)

        fun encodeFormValue(value: String): String = URLEncoder.encode(value, gb18030.name())

        fun extractSid(html: String): String? =
            Regex("""sid=([a-zA-Z0-9]+)""").find(html)?.groupValues?.get(1)

        fun extractFormHash(html: String): String? {
            val patterns = listOf(
                """formhash=([a-zA-Z0-9]+)""",
                """name=["']formhash["'][^>]*value=["']([a-zA-Z0-9]+)["']""",
                """value=["']([a-zA-Z0-9]+)["'][^>]*name=["']formhash["']""",
                """formhash\s*[:=]\s*["']?([a-zA-Z0-9]+)["']?""",
            )
            return patterns.firstNotNullOfOrNull { pattern ->
                Regex(pattern, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            }
        }

        fun isLoggedInIndex(html: String): Boolean {
            val hasForumLinks = html.contains("forumdisplay.php?fid=", ignoreCase = true)
            val hasLogout = html.contains("action=logout", ignoreCase = true) || html.contains("退出")
            val hasLoginWithoutLogout = html.contains("action=login", ignoreCase = true) && !hasLogout
            val isChallenge = html.contains("cloudflare", ignoreCase = true) || html.contains("checking your browser", ignoreCase = true)
            return hasForumLinks && hasLogout && !hasLoginWithoutLogout && !isChallenge
        }

        fun parseCategories(html: String): List<Community> =
            // Collapse newlines to avoid DOT_MATCHES_ALL cross-tag greediness,
            // then use non-greedy capture without DOT_MATCHES_ALL for safe single-line matching
            Regex("""<a\b[^>]*?href=["']([^"']*forumdisplay\.php\?[^"']*fid=(\d+)[^"']*)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE))
                .findAll(html.replace("\n", " ").replace("\r", " "))
                .mapNotNull { match ->
                    val fid = match.groupValues[2]
                    if (fid.isBlank()) return@mapNotNull null
                    val name = match.groupValues[3].stripTags().decodeHtmlEntities().trim()
                    if (name.isBlank()) return@mapNotNull null
                    fid to name
                }
                .groupBy({ it.first }, { it.second })
                .mapNotNull { (fid, names) ->
                    // Pick best name: prefer non-"4D4Y", non-numeric, meaningful names
                    val name = names.firstOrNull { n -> n.isNotBlank() && n != "4D4Y" && !n.all { it.isDigit() } }
                        ?: names.firstOrNull { n -> n.isNotBlank() && n != "4D4Y" }
                        ?: return@mapNotNull null
                    Community(
                        id = fid,
                        name = name,
                        description = "",
                        category = "4D4Y",
                        activeToday = 0,
                        onlineNow = 0,
                    )
                }
                .toList()

        fun parseThreadRows(html: String, community: Community): List<FeedThread> {
            val rowThreads = Regex("""<tbody[^>]*id=["'](?:normalthread_|thread_)(\d+)["'][^>]*>(.*?)</tbody>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(html)
                .mapNotNull { match ->
                    val tid = match.groupValues[1]
                    val row = match.groupValues[2]
                    val titleRegex = Regex("""href=["'](?:https?://(?:www\.)?4d4y\.com/forum/)?viewthread\.php\?[^"']*?\btid=\d+[^"']*["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    val title = titleRegex.findAll(row).mapNotNull { it.groupValues[1].cleanThreadListTitle().takeIf { t -> t.isNotBlank() } }.firstOrNull()
                        ?: return@mapNotNull null
                    val author = extractThreadListAuthor(row)
                    val authorId = author?.first.orEmpty()
                    val authorName = author?.second ?: "Unknown"
                    val replies = extractThreadListReplyCount(row)
                    FeedThread(
                        id = tid,
                        title = title,
                        content = "",
                        author = User(
                            id = authorId,
                            username = authorName,
                            avatar = extractThreadListAvatar(row, authorId),
                        ),
                        community = community,
                        timeAgo = "",
                        likeCount = 0,
                        commentCount = replies,
                        lastPostTime = extractThreadListLastPostTime(row),
                        lastPosterName = extractThreadListLastPoster(row),
                    )
                }
                .toList()
            return rowThreads.ifEmpty { parseThreadLinksFallback(html, community) }
        }

        fun parseSearchThreads(html: String): List<FeedThread> {
            val community = Community("search", "Search", "", "4d4y", 0, 0)
            return Regex(
                """<a\b[^>]*href=["'](?:https?://(?:www\.)?4d4y\.com/forum/)?viewthread\.php\?[^"']*?\btid=(\d+)[^"']*["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
                .findAll(html)
                .mapNotNull { match ->
                    val title = match.groupValues[2].cleanThreadListTitle()
                    if (title.isBlank()) return@mapNotNull null
                    val context = html.surroundingThreadListBlock(match.range)
                    val author = extractThreadListAuthor(context)
                    val authorId = author?.first.orEmpty()
                    FeedThread(
                        id = match.groupValues[1],
                        title = title,
                        content = "",
                        author = User(
                            id = authorId,
                            username = author?.second ?: "Unknown",
                            avatar = extractThreadListAvatar(context, authorId),
                        ),
                        community = community,
                        timeAgo = extractThreadListCreatedTime(context),
                        likeCount = 0,
                        commentCount = extractThreadListReplyCount(context),
                        lastPostTime = extractThreadListLastPostTime(context),
                        lastPosterName = extractThreadListLastPoster(context),
                    )
                }
                .distinctBy { it.id }
                .toList()
        }

        private fun parseThreadLinksFallback(html: String, community: Community): List<FeedThread> =
            Regex("""<a\b[^>]*?href=["'](?:https?://(?:www\.)?4d4y\.com/forum/)?viewthread\.php\?[^"']*?\btid=(\d+)[^"']*["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(html)
                .mapNotNull { match ->
                    val tid = match.groupValues[1]
                    val title = match.groupValues[2].cleanThreadListTitle()
                    if (title.isBlank()) return@mapNotNull null
                    val context = html.surroundingThreadListBlock(match.range)
                    val author = extractThreadListAuthor(context)
                    val authorId = author?.first.orEmpty()
                    val authorName = author?.second ?: "Unknown"
                    val replies = extractThreadListReplyCount(context)
                    FeedThread(
                        id = tid,
                        title = title,
                        content = "",
                        author = User(
                            id = authorId,
                            username = authorName,
                            avatar = extractThreadListAvatar(context, authorId),
                        ),
                        community = community,
                        timeAgo = extractThreadListCreatedTime(context),
                        likeCount = 0,
                        commentCount = replies,
                        lastPostTime = extractThreadListLastPostTime(context),
                        lastPosterName = extractThreadListLastPoster(context),
                    )
                }
                .distinctBy { it.id }
                .toList()

        private fun String.cleanThreadListTitle(): String =
            stripTags()
                .decodeHtmlEntities()
                .replace(Regex("""\s+"""), " ")
                .trim()

        private fun extractThreadListAuthor(context: String): Pair<String, String>? {
            val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            val authorScope = listOf(
                """<td[^>]*class=["'][^"']*\bauthor\b[^"']*["'][^>]*>(.*?)</td>""",
                """<(?:p|div)[^>]*class=["'][^"']*(?:\bauthor\b|\bby\b)[^"']*["'][^>]*>(.*?)</(?:p|div)>""",
            ).firstNotNullOfOrNull { pattern ->
                Regex(pattern, options).find(context)?.groupValues?.get(1)
            } ?: context

            val profileLink = Regex(
                """<a\b[^>]*href=["']([^"']*(?:space(?:\.php|-)|member\.php|profile-)[^"']*)["'][^>]*>(.*?)</a>""",
                options,
            ).findAll(authorScope).firstNotNullOfOrNull { match ->
                val href = match.groupValues[1].decodeHtmlEntities()
                val name = match.groupValues[2].stripTags().decodeHtmlEntities().trim()
                if (name.isBlank()) return@firstNotNullOfOrNull null
                val uid = listOf(
                    """(?:^|[?&])uid=(\d+)""",
                    """(?:space|profile)-uid-(\d+)""",
                    """space-(\d+)-""",
                    """uid-(\d+)""",
                ).firstNotNullOfOrNull { pattern ->
                    Regex(pattern, RegexOption.IGNORE_CASE).find(href)?.groupValues?.get(1)
                }
                (uid ?: name) to name
            }
            if (profileLink != null) return profileLink

            val uidMatch = Regex(
                """(?:^|[?&])uid=(\d+)[^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(authorScope.decodeHtmlEntities())
            if (uidMatch != null) {
                val name = uidMatch.groupValues[2].stripTags().decodeHtmlEntities().trim()
                if (name.isNotBlank()) return uidMatch.groupValues[1] to name
            }
            val name = listOf(
                """class=["'][^"']*author[^"']*["'][^>]*>\s*<a[^>]*>([^<]+)</a>""",
                """class=["'][^"']*by[^"']*["'][^>]*>\s*<a[^>]*>([^<]+)</a>""",
            ).firstNotNullOfOrNull { pattern ->
                Regex(pattern, options)
                    .find(authorScope)
                    ?.groupValues
                    ?.get(1)
                    ?.stripTags()
                    ?.decodeHtmlEntities()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            return name?.let { "" to it }
        }

        private fun extractThreadListAvatar(context: String, uid: String): String {
            val raw = listOf(
                """<img[^>]+class=["'][^"']*avatar[^"']*["'][^>]+(?:src|data-src)=["']([^"']+)["']""",
                """<img[^>]+(?:src|data-src)=["']([^"']+)["'][^>]+class=["'][^"']*avatar[^"']*["']""",
                """<img[^>]+?(?:src|data-src|file)=["']([^"']*(?:avatar|uc_server|face|head)[^"']*)["']""",
            ).firstNotNullOfOrNull { pattern ->
                Regex(pattern, RegexOption.IGNORE_CASE).find(context)?.groupValues?.get(1)
            }
            if (raw != null) {
                val value = raw.decodeHtmlEntities().trim()
                return when {
                    value.startsWith("//") -> "https:$value"
                    value.startsWith("http://", ignoreCase = true) -> "https://${value.substringAfter("://")}"
                    value.startsWith("http") -> value
                    value.startsWith("/") -> "https://www.4d4y.com$value"
                    value.startsWith("uc_server/") -> "https://www.4d4y.com/$value"
                    value.startsWith("data/avatar/") -> "https://img02.4d4y.com/forum/uc_server/$value"
                    else -> "https://www.4d4y.com/forum/$value"
                }
            }
            return if (uid.isNotBlank()) avatarUrlForUid(uid) else "person.circle"
        }

        private fun String.surroundingThreadListBlock(range: IntRange): String {
            val before = substring(0, range.first.coerceIn(0, length))
            val after = substring(range.last.coerceIn(0, length - 1) + 1)
            val containers = listOf("<tr", "<li", "<tbody")
            val start = containers
                .mapNotNull { before.lastIndexOf(it, ignoreCase = true).takeIf { index -> index >= 0 } }
                .maxOrNull()
                ?: (range.first - 800).coerceAtLeast(0)
            val endOffset = listOf("</tr>", "</li>", "</tbody>")
                .mapNotNull { after.indexOf(it, ignoreCase = true).takeIf { index -> index >= 0 }?.plus(it.length) }
                .minOrNull()
                ?: 1_200
            val end = (range.last + 1 + endOffset).coerceAtMost(length)
            return substring(start, end)
        }

        private fun extractThreadListCreatedTime(context: String): String =
            listOf(
                """<p>\s*<a[^>]+href=["']space\.php\?uid=\d+[^"']*["'][^>]*>[^<]+</a>\s*/\s*([^<]+)</p>""",
                """((?:昨天|前天|\d+\s*(?:分钟前|小时前|天前)))""",
                """<em>\s*([^<]*\d{1,2}[-/.]\d{1,2}[^<]*)</em>""",
            ).firstNotNullOfOrNull { pattern ->
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(context)
                    ?.groupValues
                    ?.get(1)
                    ?.stripTags()
                    ?.decodeHtmlEntities()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.orEmpty()

        private fun extractThreadListReplyCount(context: String): Int =
            listOf(
                """<td[^>]*class=["'][^"']*nums[^"']*["'][^>]*>.*?<strong>\s*(\d+)\s*</strong>""",
                """<a[^>]+class=["'][^"']*num[^"']*["'][^>]*>\s*(\d+)\s*</a>""",
                """(?:回复|回覆|回帖|repl(?:y|ies))[^0-9]{0,12}(\d+)""",
                """<strong>\s*(\d+)\s*</strong>""",
                """class=["'][^"']*(?:reply|num|count)[^"']*["'][^>]*>\s*(\d+)""",
            ).firstNotNullOfOrNull { pattern ->
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(context)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            } ?: 0

        private fun extractThreadListLastPostTime(context: String): String? {
            val block = extractThreadListLastPostBlock(context) ?: context
            return listOf(
                """redirect\.php\?tid=\d+[^"']*goto=lastpost[^"']*["'][^>]*>([^<]+)</a>""",
                """(\d{4}-\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2})""",
                """(\d{1,2}:\d{2})""",
                """((?:昨天|前天|\d+\s*(?:分钟前|小时前|天前)))""",
                """<cite[^>]*>([\s\S]*?)</cite>""",
            ).firstNotNullOfOrNull { pattern ->
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(block)
                    ?.groupValues
                    ?.get(1)
                    ?.stripTags()
                    ?.decodeHtmlEntities()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }

        private fun extractThreadListLastPoster(context: String): String? {
            val block = extractThreadListLastPostBlock(context) ?: context
            return listOf(
                """space\.php\?username=[^"']+["'][^>]*>([^<]+)</a>\s*/\s*<a[^>]+href=["']redirect\.php\?tid=\d+[^"']*goto=lastpost""",
                """<em[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>""",
                """lastpost[^>]*>.*?<a[^>]*>([^<]+)</a>""",
            ).firstNotNullOfOrNull { pattern ->
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(block)
                    ?.groupValues
                    ?.get(1)
                    ?.stripTags()
                    ?.decodeHtmlEntities()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }

        private fun extractThreadListLastPostBlock(context: String): String? =
            Regex("""<td[^>]*class=["'][^"']*lastpost[^"']*["'][^>]*>([\s\S]*?)(?:</td>|</tr>|</li>|$)""", RegexOption.IGNORE_CASE)
                .find(context)
                ?.groupValues
                ?.get(1)

        fun avatarUrlForUid(uid: String): String {
            val numeric = uid.trim().toLongOrNull() ?: return "person.circle"
            val padded = numeric.toString().padStart(9, '0')
            return "https://img02.4d4y.com/forum/uc_server/data/avatar/${padded.substring(0, 3)}/${padded.substring(3, 5)}/${padded.substring(5, 7)}/${padded.substring(7, 9)}_avatar_middle.jpg"
        }

        fun cleanContent(html: String): String {
            var processed = html
                .replace(Regex("""<font\s+size=["']1["'][^>]*>\s*<a[^>]+href=["']https?://(?:www\.)?4d4y\.com/forum/viewthread\.php\?tid=\d+[^"']*["'][^>]*>[^<]*</a>\s*</font>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("""<a[^>]+href=["'][^"']*redirect\.php\?goto=findpost[^"']*["'][^>]+target=["']_blank["'][^>]*>\s*\d+#?\s*</a>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("""<div class=["']t_attach["'][\s\S]*?</div>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<ignore_js_op>[\s\S]*?</ignore_js_op>""", RegexOption.IGNORE_CASE), "")
            processed = Regex("""<blockquote[^>]*>([\s\S]*?)</blockquote>""", RegexOption.IGNORE_CASE)
                .replace(processed) { "[QUOTE]${it.groupValues[1].stripTags().decodeHtmlEntities()}[/QUOTE]" }
            processed = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
                .replace(processed) {
                    val src = it.groupValues[1]
                    if (src.contains("smilies", ignoreCase = true) || src.contains("avatar", ignoreCase = true)) "" else "\n[IMAGE:$src]\n"
                }
            processed = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .replace(processed) {
                    val href = it.groupValues[1]
                    if (href.startsWith("#") || href.startsWith("javascript:", ignoreCase = true)) {
                        it.groupValues[2].stripTags()
                    } else {
                        "[LINK:$href|${it.groupValues[2].stripTags().decodeHtmlEntities().ifBlank { href }}]"
                    }
                }
            return processed.stripTags().decodeHtmlEntities().replace(Regex("(\\s*\\n\\s*){3,}"), "\n\n").trim()
        }
    }

    data class V2exTopic(
        val id: String,
        val title: String,
        val author: String,
        val avatar: String,
        val replies: Int,
    )

    data class V2exReply(
        val id: String,
        val author: String,
        val avatar: String,
        val content: String,
        val timeAgo: String,
    )

    object V2exParser {
        val tabs = listOf("tech", "creative", "play", "apple", "jobs", "deals", "city", "qna", "hot", "all", "r2", "xna", "planet")

        fun extractOnce(html: String): String? {
            val patterns = listOf(
                """name=["']once["'][^>]*value=["'](\d+)["']""",
                """value=["'](\d+)["'][^>]*name=["']once["']""",
                """var\s+once\s*=\s*["']?(\d+)["']?""",
                """['"]once['"]\s*:\s*['"]?(\d+)['"]?""",
                """once=(\d+)""",
            )
            return patterns.firstNotNullOfOrNull { Regex(it, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) }
        }

        fun parseThreadList(html: String): List<V2exTopic> =
            html.split(Regex("""class=["'][^"']*cell item[^"']*["']""", RegexOption.IGNORE_CASE))
                .drop(1)
                .mapNotNull { cell ->
                    val topic = Regex("""<a href=["']/t/(\d+)[^"']*["'] class=["']topic-link["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(cell)
                        ?: return@mapNotNull null
                    val author = Regex("""href=["']/member/([^"']+)["']""", RegexOption.IGNORE_CASE).find(cell)?.groupValues?.get(1).orEmpty()
                    val avatar = extractAvatar(cell)
                    val replies = Regex("""class=["']count_livid["']>(\d+)</a>""", RegexOption.IGNORE_CASE).find(cell)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    V2exTopic(topic.groupValues[1], topic.groupValues[2].stripTags().decodeHtmlEntities(), author, avatar, replies)
                }

        fun parseReplies(html: String): List<V2exReply> =
            html.split(Regex("""id=["']r_""", RegexOption.IGNORE_CASE)).drop(1).mapNotNull { block ->
                val id = Regex("""^(\d+)""").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val author = Regex("""class=["']dark["'][^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty()
                val avatar = extractAvatar(block)
                val content = Regex("""class=["']reply_content["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.let(::cleanContent).orEmpty()
                val time = Regex("""class=["']ago["'][^>]*>([^<]+)</span>""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty()
                V2exReply(id, author.decodeHtmlEntities(), avatar, content, time)
            }

        fun parseTopicTitle(html: String): String =
            (
                Regex("""class=["']topic_full_title["'][^>]*>([\s\S]*?)</""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                    ?: Regex("""<h1[^>]*>([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                    ?: ""
                ).stripTags().decodeHtmlEntities()

        fun parseTopicContent(html: String): String =
            Regex("""class=["']topic_content["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::cleanContent).orEmpty()

        fun parseTopicAuthor(html: String): String =
            Regex("""href=["']/member/([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1).orEmpty()

        fun parseTopicAvatar(html: String, author: String): String {
            if (author.isNotBlank()) {
                val authorBlock = Regex(
                    """<a[^>]+href=["']/member/${Regex.escape(author)}["'][^>]*>\s*(<img[^>]+>)""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ).find(html)?.groupValues?.get(1)
                extractAvatar(authorBlock.orEmpty()).takeIf { it.isNotBlank() }?.let { return it }
            }
            return extractAvatar(html)
        }

        fun parseSearch(body: String): SearchResult {
            val root = ZhihuJson.parse(body)?.obj() ?: return SearchResult(emptyList(), false)
            val total = root.int("total") ?: 0
            val hits = root.arr("hits") ?: return SearchResult(emptyList(), false)
            val seen = mutableSetOf<String>()
            val threads = hits.mapNotNull { element ->
                val obj = element.obj() ?: return@mapNotNull null
                val source = obj.obj("_source") ?: return@mapNotNull null
                val id = source.longId("id")?.toString() ?: return@mapNotNull null
                if (!seen.add(id)) return@mapNotNull null
                val title = source.str("title")?.decodeHtmlEntities()?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val content = source.str("content")?.decodeHtmlEntities()?.trim().orEmpty()
                val member = source.str("member")?.trim().orEmpty()
                val replies = source.int("replies") ?: 0
                val created = source.str("created").orEmpty()
                FeedThread(
                    id = id,
                    title = title,
                    content = content,
                    author = User(member, member.ifBlank { "V2EX" }, "person.circle"),
                    community = Community("v2ex", "V2EX", "", "V2EX", 0, 0),
                    timeAgo = created.take(10),
                    likeCount = 0,
                    commentCount = replies,
                )
            }
            val from = root.int("from") ?: 0
            return SearchResult(threads, from + threads.size < total)
        }

        fun cleanContent(html: String): String =
            Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
                .replace(html) { "\n[IMAGE:${normalizeUrl(it.groupValues[1])}]\n" }
                .replace("<br>", "\n")
                .replace("<p>", "\n")
                .stripTags()
                .decodeHtmlEntities()

        fun normalizeUrl(url: String): String = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://v2ex.com$url"
            else -> url
        }

        private fun extractAvatar(html: String): String {
            val tag = Regex(
                """<img\b(?=[^>]*class=["'][^"']*\bavatar\b[^"']*["'])[^>]*>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(html)?.value ?: return ""
            val raw = Regex("""(?:src|data-src)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)
                ?.groupValues
                ?.get(1)
                ?: return ""
            return normalizeUrl(raw.decodeHtmlEntities())
        }
    }

    object ZhihuParser {
        val categories = listOf(
            Community("recommend", "Recommendations", "Zhihu personalized recommendations", "Zhihu", 0, 0),
            Community("hot", "Hot", "Zhihu hot list", "Zhihu", 0, 0),
        )

        fun normalizedAvatarUrl(url: String?, template: String? = null): String {
            val candidate = url?.takeIf { it.isNotBlank() } ?: template.orEmpty()
            if (candidate.isBlank()) return ""
            return candidate.replace("{size}", "80").replace("&amp;", "&").let { if (it.startsWith("//")) "https:$it" else it }
        }

        fun threadId(type: String, id: String): String = "${type}_$id"

        fun shouldKeepRecommendation(type: String, voteCount: Int, followerCount: Int = 0, isFollowing: Boolean = false): Boolean = when {
            type == "feed_advert" -> false
            type == "answer" -> isFollowing || voteCount >= 10
            type == "article" || type == "zvideo" -> isFollowing || followerCount >= 50 || voteCount >= 20
            type == "question" -> true
            else -> false
        }

        fun cleanHtml(html: String): String {
            val seenImages = linkedSetOf<String>()
            var processed = html.replace(Regex("""<noscript[\s\S]*?</noscript>""", RegexOption.IGNORE_CASE), "")
            processed = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
                .replace(processed) { "[LINK:${it.groupValues[1]}|${it.groupValues[2].stripTags().decodeHtmlEntities()}]" }
            processed = Regex("""<img[^>]+(?:src|data-original|data-actualsrc)=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
                .replace(processed) {
                    val url = normalizedAvatarUrl(it.groupValues[1])
                    val key = url.substringBefore("?")
                    if (url.startsWith("data:") || url.contains("equation") || !seenImages.add(key)) "" else "\n[IMAGE:$url]\n"
                }
            return processed.stripTags().decodeHtmlEntities().replace(Regex("\\s+"), " ").trim()
        }

        fun extractSearchUrls(html: String): List<String> =
            Regex("""https?://www\.zhihu\.com/(?:question/\d+(?:/answer/\d+)?|p/\d+)""")
                .findAll(html)
                .map { it.value }
                .distinct()
                .toList()

        fun typeDescription(type: String?): String = when (type) {
            "answer" -> "回答"
            "article" -> "文章"
            "zvideo" -> "视频"
            "question" -> "问题"
            "pin" -> "想法"
            else -> type ?: "未知"
        }

        fun effectiveTitle(target: JsonObject): String {
            val type = target.str("type")
            if (type == "answer") {
                target.obj("question").str("title")?.takeIf { it.isNotEmpty() }?.let { return it.decodeHtmlEntities() }
            }
            return (target.str("title") ?: "Untitled").decodeHtmlEntities()
        }

        fun hotQuestionId(item: JsonObject, target: JsonObject): String? {
            item.str("card_id")
                ?.takeIf { it.startsWith("Q_") }
                ?.removePrefix("Q_")
                ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?.let { return it }
            (target.longId("id")?.toString() ?: target.str("id"))
                ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?.let { return it }
            listOfNotNull(target.str("url"), item.str("url")).forEach { url ->
                Regex("""/(?:question|questions)/(\d+)""")
                    .find(url)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { return it }
            }
            return (item.longId("id")?.toString() ?: item.str("id"))
                ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        }

        fun hotTitle(item: JsonObject, target: JsonObject): String =
            listOfNotNull(
                target.obj("title_area").str("text"),
                target.str("title"),
                target.obj("question").str("title"),
                item.str("title"),
            ).firstOrNull { it.isNotBlank() }?.decodeHtmlEntities() ?: "Untitled"

        fun hotExcerpt(item: JsonObject, target: JsonObject): String {
            val firstChild = item.arr("children")?.firstOrNull()?.obj()
            return listOfNotNull(
                target.obj("excerpt_area").str("text"),
                target.str("excerpt"),
                target.str("detail"),
                firstChild.str("excerpt"),
                firstChild.str("content"),
            ).firstOrNull { it.isNotBlank() }?.let(::cleanHtml).orEmpty()
        }

        fun hotAuthor(item: JsonObject, target: JsonObject): JsonObject? {
            val firstChild = item.arr("children")?.firstOrNull()?.obj()
            return target.obj("author")
                ?: firstChild.obj("author")
                ?: firstChild.obj("target").obj("author")
                ?: firstChild.obj("object").obj("author")
        }

        fun filterReason(target: JsonObject): String? {
            val votes = target.int("voteup_count") ?: 0
            val author = target.obj("author")
            val followers = author.int("follower_count") ?: 0
            val isFollowing = author.bool("is_following") ?: false
            return when (target.str("type")) {
                "answer" -> if (votes < 10 && !isFollowing) "规则：回答；赞数 < 10，未关注作者" else null
                "article" -> if ((followers < 50 || votes < 20) && !isFollowing) "规则：文章" else null
                "zvideo" -> if (followers < 50 && votes < 20 && !isFollowing) "规则：视频" else null
                else -> null
            }
        }

        fun author(obj: JsonObject?, fallbackName: String = "匿名用户"): User = User(
            id = obj.str("id") ?: obj.str("url_token") ?: "",
            username = obj.str("name")?.takeIf { it.isNotBlank() } ?: fallbackName,
            avatar = normalizedAvatarUrl(obj.str("avatar_url"), obj.str("avatar_url_template")),
            role = obj.str("headline")?.takeIf { it.isNotBlank() },
        )

        fun formatTimestamp(seconds: Int?): String {
            if (seconds == null || seconds <= 0) return ""
            return HackerNewsContentCleaner.timeAgo(seconds.toLong())
        }

        fun cleanSearchText(text: String): String =
            text.replace(Regex("""</?(?:em|b|strong|span|highlight)[^>]*>""", RegexOption.IGNORE_CASE), "")
                .stripTags()
                .decodeHtmlEntities()
                .trim()
    }

    object DiscourseParser {
        fun parseCategories(json: String): List<Community> {
            val parsed = Regex("""\{[^{}]*"id"\s*:\s*\d+[^{}]*"name"\s*:\s*"[^"]+"[^{}]*\}""")
                .findAll(json)
                .mapNotNull {
                    val block = it.value
                    val id = jsonInt(block, "id")?.toString() ?: return@mapNotNull null
                    val name = jsonString(block, "name") ?: return@mapNotNull null
                    Community(
                        id = id,
                        name = name.decodeHtmlEntities(),
                        description = jsonString(block, "description").orEmpty(),
                        category = jsonString(block, "slug").orEmpty().ifBlank { "linux_do" },
                        activeToday = jsonInt(block, "topic_count") ?: 0,
                        onlineNow = 0,
                    )
                }
                .toMutableList()
            if (parsed.none { it.id == "latest" }) {
                parsed.add(0, Community("latest", "Latest", "Latest topics", "linux_do", 0, 0))
            }
            return parsed
        }

        private fun jsonString(json: String, key: String): String? =
            Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)

        private fun jsonInt(json: String, key: String): Int? =
            Regex(""""$key"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()

        fun cleanCooked(html: String, baseUrl: String = "https://linux.do"): String {
            var processed = html
                .replace(Regex("""<span[^>]*class=["'][^"']*(?:avatar|site-icon|crawler-post-meta)[^"']*["'][\s\S]*?</span>""", RegexOption.IGNORE_CASE), "")
            processed = Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE)
                .replace(processed) {
                    val tag = it.value
                    val src = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1).orEmpty()
                    val alt = Regex("""alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1).orEmpty()
                    if (tag.contains("emoji", ignoreCase = true) || src.contains("emoji", ignoreCase = true)) {
                        alt
                    } else if (src.isNotBlank()) {
                        "\n[IMAGE:${resolveUrl(src, baseUrl)}]\n"
                    } else {
                        alt
                    }
                }
            return processed
                .replace("<br>", "\n")
                .replace("</p>", "\n\n")
                .stripTags()
                .decodeHtmlEntities()
                .replace(Regex("\\s+\\n"), "\n")
                .trim()
        }

        fun resolveAvatar(template: String, baseUrl: String = "https://linux.do"): String =
            resolveUrl(template.replace("{size}", "64"), baseUrl)

        private fun resolveUrl(url: String, baseUrl: String): String = when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }

        fun relativeTime(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
            return HackerNewsContentCleaner.timeAgo(instant.epochSecond)
        }

        fun parseTopicList(json: String, community: Community): List<FeedThread> {
            val root = ZhihuJson.parse(json)?.obj() ?: return emptyList()
            val usersById = mutableMapOf<Int, JsonObject>()
            root.arr("users")?.forEach { element ->
                val user = element.obj() ?: return@forEach
                user.int("id")?.let { usersById[it] = user }
            }
            val topics = root.obj("topic_list").arr("topics") ?: root.arr("topics") ?: return emptyList()
            return topics.mapNotNull { element ->
                val topic = element.obj() ?: return@mapNotNull null
                val id = topic.int("id")?.toString() ?: return@mapNotNull null
                val posters = topic.arr("posters")
                val originalPosterId = posters?.firstOrNull()?.obj()?.int("user_id")
                val posterUser = originalPosterId?.let { usersById[it] }
                val authorName = posterUser.str("username") ?: "Linux.do"
                val avatar = posterUser.str("avatar_template")?.let { resolveAvatar(it) } ?: "person.circle"
                FeedThread(
                    id = id,
                    title = (topic.str("title") ?: "").decodeHtmlEntities(),
                    content = (topic.str("excerpt") ?: "").stripTags().decodeHtmlEntities(),
                    author = User(originalPosterId?.toString() ?: "linux_do", authorName, avatar),
                    community = community,
                    timeAgo = relativeTime(topic.str("created_at")),
                    likeCount = topic.int("like_count") ?: 0,
                    commentCount = (topic.int("posts_count") ?: 1).minus(1).coerceAtLeast(0),
                    lastPostTime = relativeTime(topic.str("last_posted_at")),
                )
            }
        }

        fun parseThreadDetail(json: String, threadId: String): Triple<FeedThread, List<Comment>, Int?> {
            val root = ZhihuJson.parse(json)?.obj()
            val title = (root.str("title") ?: "Linux.do").decodeHtmlEntities()
            val categoryId = root.int("category_id")?.toString().orEmpty()
            val community = Community(
                categoryId.ifBlank { "latest" },
                categoryId.ifBlank { "Latest" },
                "",
                "linux_do",
                0,
                0,
            )
            val posts = root.obj("post_stream").arr("posts").orEmpty().mapNotNull { element ->
                val post = element.obj() ?: return@mapNotNull null
                val username = post.str("username") ?: "anonymous"
                val avatar = post.str("avatar_template")?.let { resolveAvatar(it) } ?: "person.circle"
                Comment(
                    id = post.int("id")?.toString() ?: return@mapNotNull null,
                    author = User(post.int("user_id")?.toString() ?: username, username, avatar),
                    content = cleanCooked(post.str("cooked").orEmpty()),
                    timeAgo = relativeTime(post.str("created_at")),
                    likeCount = post.int("reply_count") ?: 0,
                )
            }
            val firstPost = posts.firstOrNull()
            val postsCount = root.int("posts_count") ?: posts.size
            val thread = FeedThread(
                id = threadId,
                title = title,
                content = firstPost?.content.orEmpty(),
                author = firstPost?.author ?: User("linux_do", "Linux.do", "terminal.fill"),
                community = community,
                timeAgo = firstPost?.timeAgo.orEmpty(),
                likeCount = firstPost?.likeCount ?: 0,
                commentCount = (postsCount - 1).coerceAtLeast(0),
            )
            val totalPages = null
            return Triple(thread, posts.drop(1), totalPages)
        }

        fun parseSearch(json: String): SearchResult {
            val root = ZhihuJson.parse(json)?.obj() ?: return SearchResult(emptyList(), false)
            val postsByTopicId = root.arr("posts")
                .orEmpty()
                .mapNotNull { element ->
                    val post = element.obj() ?: return@mapNotNull null
                    val topicId = post.int("topic_id") ?: return@mapNotNull null
                    topicId to post
                }
                .toMap()
            val threads = root.arr("topics").orEmpty().mapNotNull { element ->
                val topic = element.obj() ?: return@mapNotNull null
                val topicId = topic.int("id") ?: return@mapNotNull null
                val post = postsByTopicId[topicId]
                val username = post.str("username") ?: "Unknown"
                val avatar = post.str("avatar_template")?.let { resolveAvatar(it) } ?: "person.circle"
                FeedThread(
                    id = topicId.toString(),
                    title = topic.str("title").orEmpty().decodeHtmlEntities(),
                    content = cleanCooked(post.str("blurb") ?: post.str("cooked").orEmpty()),
                    author = User(post.int("user_id")?.toString() ?: username, username, avatar),
                    community = Community(
                        id = topic.int("category_id")?.toString().orEmpty(),
                        name = "Search",
                        description = "",
                        category = "linux_do",
                        activeToday = 0,
                        onlineNow = 0,
                    ),
                    timeAgo = relativeTime(topic.str("created_at")),
                    likeCount = topic.int("like_count") ?: 0,
                    commentCount = ((topic.int("posts_count") ?: 1) - 1).coerceAtLeast(0),
                    tags = topic.arr("tags")
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        ?.takeIf { it.isNotEmpty() },
                )
            }
            val hasMore = root.obj("grouped_search_result").bool("more_results") ?: (threads.size >= 20)
            return SearchResult(threads, hasMore)
        }
    }
