package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.model.ForumSite
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration

sealed interface ContentBlock {
    data class Text(val segments: List<LinkSegment>) : ContentBlock
    data class Quote(val segments: List<LinkSegment>) : ContentBlock
    data class Image(val url: String, val normalizedKey: String) : ContentBlock
}

sealed interface LinkSegment {
    data class Plain(val text: String) : LinkSegment
    data class Link(val url: String, val title: String) : LinkSegment
}

object AvatarRenderingPolicy {
    private val genericAvatarNames = setOf(
        "",
        "person.circle",
        "person.crop.circle",
        "person",
        "avatar",
        "rss",
        "feed",
        "exclamationmark.triangle",
    )

    fun isRemoteAvatar(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    fun shouldUseFallback(value: String): Boolean =
        value.isBlank() || genericAvatarNames.contains(value.lowercase()) || !isRemoteAvatar(value)

    fun fallbackInitial(username: String): String =
        username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

object ThreadRowRenderingPolicy {
    fun hidesAvatar(site: ForumSite, communityId: String? = null): Boolean =
        site == ForumSite.Rss ||
            site == ForumSite.HackerNews ||
            (site == ForumSite.Zhihu && communityId == "hot")
    fun hidesBadgeRow(site: ForumSite): Boolean = site == ForumSite.Zhihu || site == ForumSite.HackerNews
    fun supportsNotInterested(site: ForumSite, communityId: String): Boolean =
        site == ForumSite.Zhihu && communityId == "recommend"
    fun normalizedLastPostTime(value: String?): String? =
        value?.replace(Regex("""^\d{4}[-/]\d{1,2}[-/]\d{1,2}\s*"""), "")?.ifBlank { null }
}

object UrlBookmarkRelativeTime {
    fun format(age: Duration): String {
        val seconds = age.seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3_600}h"
            else -> "${seconds / 86_400}d"
        }
    }
}

object FeedflowContentRenderer {
    fun parseBlocks(content: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val seenImageKeys = linkedSetOf<String>()
        val imageRegex = Regex("""\[IMAGE:([^\]]+)]""")
        var cursor = 0
        imageRegex.findAll(content).forEach { match ->
            addTextAndQuoteBlocks(content.substring(cursor, match.range.first), blocks)
            val url = match.groupValues[1].trim()
            val key = normalizeImageKey(url)
            if (url.isNotBlank() && seenImageKeys.add(key)) {
                blocks += ContentBlock.Image(normalizeDisplayUrl(url), key)
            }
            cursor = match.range.last + 1
        }
        addTextAndQuoteBlocks(content.substring(cursor), blocks)
        return blocks.filterNot { block ->
            when (block) {
                is ContentBlock.Text -> block.segments.onlyBlankPlainText()
                is ContentBlock.Quote -> block.segments.onlyBlankPlainText()
                is ContentBlock.Image -> false
            }
        }
    }

    fun parseLinkedText(text: String): List<LinkSegment> {
        val segments = mutableListOf<LinkSegment>()
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf("[LINK:", cursor)
            if (start == -1) {
                addRawUrlSegments(text.substring(cursor), segments)
                break
            }
            addRawUrlSegments(text.substring(cursor, start), segments)
            val pipe = text.indexOf('|', start + 6)
            if (pipe == -1) {
                addRawUrlSegments(text.substring(start), segments)
                break
            }
            val close = findMarkerClose(text, pipe + 1)
            if (close == -1) {
                addRawUrlSegments(text.substring(start), segments)
                break
            }
            segments += LinkSegment.Link(
                url = text.substring(start + 6, pipe),
                title = text.substring(pipe + 1, close),
            )
            cursor = close + 1
        }
        return mergePlain(segments)
    }

    fun singleLinkOrNull(text: String): LinkSegment.Link? {
        val segments = parseLinkedText(text).filterNot { it is LinkSegment.Plain && it.text.isBlank() }
        return segments.singleOrNull() as? LinkSegment.Link
    }

    fun normalizeImageKey(url: String): String =
        normalizeDisplayUrl(url)
            .substringBefore("#")
            .substringBefore("?")
            .replace(Regex("""https://pic\d+\.zhimg\.com/"""), "https://pic.zhimg.com/")
            .replace(Regex("""_(?:b|r|hd|720w|1080w)\."""), ".")

    fun normalizeDisplayUrl(url: String): String {
        val decoded = url.replace("&amp;", "&")
        val protocolFixed = if (decoded.startsWith("//")) "https:$decoded" else decoded
        return runCatching { URLDecoder.decode(protocolFixed, StandardCharsets.UTF_8.name()) }.getOrDefault(protocolFixed)
    }

    private fun addTextAndQuoteBlocks(text: String, blocks: MutableList<ContentBlock>) {
        if (text.isEmpty()) return
        val quoteRegex = Regex("""\[QUOTE]([\s\S]*?)\[/QUOTE]""")
        var cursor = 0
        quoteRegex.findAll(text).forEach { match ->
            addPlainBlock(text.substring(cursor, match.range.first), blocks)
            blocks += ContentBlock.Quote(parseLinkedText(match.groupValues[1].trim()))
            cursor = match.range.last + 1
        }
        addPlainBlock(text.substring(cursor), blocks)
    }

    private fun addPlainBlock(text: String, blocks: MutableList<ContentBlock>) {
        if (text.isNotBlank()) blocks += ContentBlock.Text(parseLinkedText(text.trim()))
    }

    private fun addRawUrlSegments(text: String, segments: MutableList<LinkSegment>) {
        val urlRegex = Regex("""https?://[^\s<>()]+""")
        var cursor = 0
        urlRegex.findAll(text).forEach { match ->
            val plain = text.substring(cursor, match.range.first)
            if (plain.isNotEmpty()) segments += LinkSegment.Plain(plain)
            segments += LinkSegment.Link(match.value, match.value)
            cursor = match.range.last + 1
        }
        text.substring(cursor).takeIf { it.isNotEmpty() }?.let { segments += LinkSegment.Plain(it) }
    }

    private fun findMarkerClose(text: String, titleStart: Int): Int {
        var depth = 0
        for (index in titleStart until text.length) {
            when (text[index]) {
                '[' -> depth += 1
                ']' -> if (depth > 0) depth -= 1 else return index
            }
        }
        return -1
    }

    private fun mergePlain(segments: List<LinkSegment>): List<LinkSegment> =
        segments.fold(mutableListOf()) { merged, segment ->
            val last = merged.lastOrNull()
            if (last is LinkSegment.Plain && segment is LinkSegment.Plain) {
                merged[merged.lastIndex] = LinkSegment.Plain(last.text + segment.text)
            } else {
                merged += segment
            }
            merged
        }

    private fun List<LinkSegment>.onlyBlankPlainText(): Boolean =
        all { it is LinkSegment.Plain && it.text.isBlank() }
}
