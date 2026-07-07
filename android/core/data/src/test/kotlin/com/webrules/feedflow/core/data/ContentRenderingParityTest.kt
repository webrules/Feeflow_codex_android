package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.model.ForumSite
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentRenderingParityTest {
    @Test fun avatarPolicyDistinguishesRemoteUrlsFromFallbackNames() {
        assertTrue(AvatarRenderingPolicy.isRemoteAvatar("https://example.com/a.png"))
        assertFalse(AvatarRenderingPolicy.isRemoteAvatar("person.circle"))
        assertTrue(AvatarRenderingPolicy.shouldUseFallback(""))
        assertTrue(AvatarRenderingPolicy.shouldUseFallback("rss"))
        assertEquals("J", AvatarRenderingPolicy.fallbackInitial("joey"))
        assertEquals("?", AvatarRenderingPolicy.fallbackInitial(""))
    }

    @Test fun threadRowPolicyMatchesSourceSpecificVisibilityRules() {
        assertTrue(ThreadRowRenderingPolicy.hidesAvatar(ForumSite.Rss))
        assertTrue(ThreadRowRenderingPolicy.hidesAvatar(ForumSite.HackerNews))
        assertFalse(ThreadRowRenderingPolicy.hidesAvatar(ForumSite.V2ex))
        assertTrue(ThreadRowRenderingPolicy.hidesAvatar(ForumSite.Zhihu, "hot"))
        assertFalse(ThreadRowRenderingPolicy.hidesAvatar(ForumSite.Zhihu, "recommend"))
        assertTrue(ThreadRowRenderingPolicy.hidesBadgeRow(ForumSite.Zhihu))
        assertTrue(ThreadRowRenderingPolicy.hidesBadgeRow(ForumSite.HackerNews))
        assertFalse(ThreadRowRenderingPolicy.hidesBadgeRow(ForumSite.LinuxDo))
        assertTrue(ThreadRowRenderingPolicy.supportsNotInterested(ForumSite.Zhihu, "recommend"))
        assertFalse(ThreadRowRenderingPolicy.supportsNotInterested(ForumSite.Zhihu, "hot"))
        assertFalse(ThreadRowRenderingPolicy.supportsNotInterested(ForumSite.V2ex, "recommend"))
        assertEquals("12:34", ThreadRowRenderingPolicy.normalizedLastPostTime("2026-07-02 12:34"))
        assertNull(ThreadRowRenderingPolicy.normalizedLastPostTime(""))
    }

    @Test fun linkedTextParsesMarkersRawUrlsAndSingleLinkCards() {
        val marker = FeedflowContentRenderer.parseLinkedText("See [LINK:https://example.com|Example [docs]] now")
        assertIs<LinkSegment.Link>(marker[1])
        assertEquals("Example [docs]", (marker[1] as LinkSegment.Link).title)

        val raw = FeedflowContentRenderer.parseLinkedText("Visit https://example.com/feed now")
        assertEquals("https://example.com/feed", (raw[1] as LinkSegment.Link).url)

        val single = FeedflowContentRenderer.singleLinkOrNull("[LINK:https://example.com|Example]")
        assertEquals("Example", single?.title)
    }

    @Test fun hackerNewsAndV2exDetailsUseBodyColorForLinks() {
        assertFalse(ThreadDetailRenderingPolicy.usesAccentLinkColor(ForumSite.Rss))
        assertFalse(ThreadDetailRenderingPolicy.usesAccentLinkColor(ForumSite.HackerNews))
        assertFalse(ThreadDetailRenderingPolicy.usesAccentLinkColor(ForumSite.V2ex))
        assertFalse(ThreadDetailRenderingPolicy.usesAccentLinkColor(ForumSite.FourD4Y))
        assertFalse(ThreadDetailRenderingPolicy.usesAccentLinkColor(ForumSite.Zhihu))
        assertTrue(ThreadDetailRenderingPolicy.usesAccentLinkColor(ForumSite.LinuxDo))
    }

    @Test fun parsedContentSplitsQuotesImagesAndDedupesZhihuVariants() {
        val blocks = FeedflowContentRenderer.parseBlocks(
            """
            Intro [LINK:https://example.com|link]
            [QUOTE]quoted https://quote.example[/QUOTE]
            [IMAGE:https://pic1.zhimg.com/abc_b.jpg?source=1]
            [IMAGE:https://pic2.zhimg.com/abc_r.jpg#frag]
            [IMAGE://cdn.example.com/a.png]
            """.trimIndent(),
        )
        assertIs<ContentBlock.Text>(blocks[0])
        assertIs<ContentBlock.Quote>(blocks[1])
        val images = blocks.filterIsInstance<ContentBlock.Image>()
        assertEquals(2, images.size)
        assertEquals("https://cdn.example.com/a.png", images[1].url)
    }

    @Test fun parsedContentPromotesMarkdownHeadingsToHeadingBlocks() {
        val blocks = FeedflowContentRenderer.parseBlocks(
            """
            Intro paragraph.

            ## Article Section

            Body paragraph.
            • First bullet
            • Second bullet
            [QUOTE]quoted paragraph[/QUOTE]
            """.trimIndent(),
        )

        assertIs<ContentBlock.Text>(blocks[0])
        val heading = assertIs<ContentBlock.Heading>(blocks[1])
        assertEquals(2, heading.level)
        assertEquals("Article Section", (heading.segments.single() as LinkSegment.Plain).text)
        assertIs<ContentBlock.Text>(blocks[2])
        assertIs<ContentBlock.Quote>(blocks[3])
    }

    @Test fun urlBookmarkRelativeTimeMatchesIosThresholds() {
        assertEquals("just now", UrlBookmarkRelativeTime.format(Duration.ofSeconds(59)))
        assertEquals("1m", UrlBookmarkRelativeTime.format(Duration.ofSeconds(60)))
        assertEquals("1h", UrlBookmarkRelativeTime.format(Duration.ofSeconds(3_600)))
        assertEquals("1d", UrlBookmarkRelativeTime.format(Duration.ofSeconds(86_400)))
    }
}
