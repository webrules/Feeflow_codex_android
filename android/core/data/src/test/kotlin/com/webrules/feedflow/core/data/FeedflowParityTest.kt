package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.DatabaseSchemaMigration
import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.FeedflowDatabaseContract
import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.model.capability
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.network.FeedflowHttpClient
import com.webrules.feedflow.core.network.CookieMatcher
import com.webrules.feedflow.core.security.AesGcmSecretStore
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun cookie(name: String, value: String = "v", domain: String = "4d4y.com", path: String = "/", expires: Long? = System.currentTimeMillis() + 3_600_000) =
    FeedflowCookie(name, value, domain, path, expires)

private fun community(id: String = "test") = Community(id, "Test", "", "general", 10, 5)
private fun thread(id: String = "1", title: String = "T", author: User = User("u1", "Tester", "person.circle")) =
    FeedThread(id, title, "", author, community(), "now", 0, 0)

private class FixtureHttpClient(private val responses: Map<String, String>) : FeedflowHttpClient {
    val requested = mutableListOf<String>()
    override suspend fun get(url: String, cookies: List<FeedflowCookie>): String {
        requested += url
        return responses[url] ?: error("Missing fixture response for $url")
    }

    override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String =
        error("POST not used in this fixture")
}

class ForumSiteParityTest {
    @Test fun allCasesCount() = assertEquals(6, ForumSite.entries.size)
    @Test fun fromServiceIdMapsCorrectly() {
        assertEquals(ForumSite.FourD4Y, ForumSite.fromServiceId("4d4y"))
        assertEquals(ForumSite.LinuxDo, ForumSite.fromServiceId("linux_do"))
        assertEquals(ForumSite.HackerNews, ForumSite.fromServiceId("hackernews"))
        assertEquals(ForumSite.V2ex, ForumSite.fromServiceId("v2ex"))
        assertEquals(ForumSite.Rss, ForumSite.fromServiceId("rss"))
        assertEquals(ForumSite.Zhihu, ForumSite.fromServiceId("zhihu"))
        assertNull(ForumSite.fromServiceId("invalid"))
    }
    @Test fun makeServiceReturnsCorrectType() {
        assertTrue(ForumSite.FourD4Y.makeService() is FourD4YService)
        assertTrue(ForumSite.LinuxDo.makeService() is DiscourseService)
        assertTrue(ForumSite.HackerNews.makeService() is HackerNewsService)
        assertTrue(ForumSite.V2ex.makeService() is V2exService)
        assertTrue(ForumSite.Rss.makeService() is RssService)
        assertTrue(ForumSite.Zhihu.makeService() is ZhihuService)
    }
    @Test fun sourceCapabilitiesMatchIosServiceContract() {
        assertEquals(listOf(ForumSite.Rss, ForumSite.HackerNews, ForumSite.FourD4Y, ForumSite.V2ex, ForumSite.LinuxDo, ForumSite.Zhihu), ForumSite.entries)
        assertEquals("RSS Feeds", ForumSite.Rss.displayName)
        assertEquals("Linux.do", ForumSite.LinuxDo.displayName)
        assertFalse(ForumSite.Rss.capability.requiresLogin)
        assertFalse(ForumSite.HackerNews.capability.supportsCommenting)
        assertTrue(ForumSite.FourD4Y.capability.requiresLogin)
        assertTrue(ForumSite.FourD4Y.capability.supportsCommenting)
        assertTrue(ForumSite.FourD4Y.capability.supportsThreadCreation)
        assertTrue(ForumSite.V2ex.capability.supportsCommenting)
        assertFalse(ForumSite.V2ex.capability.supportsThreadCreation)
        assertTrue(ForumSite.LinuxDo.capability.supportsThreadCreation)
        assertTrue(ForumSite.Zhihu.capability.requiresLogin)
        assertFalse(ForumSite.Zhihu.capability.supportsCommenting)
    }
    @Test fun rssAlwaysEnabledAndOrderMatchesIos() {
        val manager = CommunitySettingsManager()
        assertTrue(manager.isEnabled(ForumSite.Rss))
        manager.toggle(ForumSite.Rss)
        assertTrue(manager.isEnabled(ForumSite.Rss))
        assertEquals(listOf(ForumSite.Rss, ForumSite.HackerNews, ForumSite.FourD4Y, ForumSite.V2ex, ForumSite.LinuxDo, ForumSite.Zhihu), ForumSite.entries)
    }
}

class AuthenticationParityTest {
    @Test fun oauthOptionCountsAndRssNoConfig() {
        assertEquals(6, SiteLoginConfig.forSite(ForumSite.LinuxDo)?.oauthOptions?.size)
        assertEquals(2, SiteLoginConfig.forSite(ForumSite.V2ex)?.oauthOptions?.size)
        assertNull(SiteLoginConfig.forSite(ForumSite.Rss))
    }
    @Test fun authCookieDetection() {
        assertTrue(SiteLoginConfig.forSite(ForumSite.LinuxDo)!!.hasAuthenticatedSession(listOf(cookie("_t", domain = "linux.do"))))
        val four = SiteLoginConfig.forSite(ForumSite.FourD4Y)!!
        assertTrue(four.hasAuthenticatedSession(listOf(cookie("cdb_auth"))))
        assertTrue(four.hasAuthenticatedSession(listOf(cookie("cdb_login"))))
        assertFalse(four.hasAuthenticatedSession(listOf(cookie("cdb_sid"))))
        assertTrue(SiteLoginConfig.forSite(ForumSite.Zhihu)!!.hasAuthenticatedSession(listOf(cookie("z_c0", domain = "zhihu.com"))))
    }
    @Test fun siteCookiesFilterAndCookieHeader() {
        val filtered = SiteLoginConfig.forSite(ForumSite.FourD4Y)!!.siteCookies(listOf(cookie("cdb_auth", domain = "www.4d4y.com"), cookie("x", domain = "google.com")))
        assertEquals(1, filtered.size)
        assertEquals("cdb_auth", filtered.first().name)
        assertNotNull(CookieMatcher.matchingCookieHeader("https://www.4d4y.com/forum/index.php", listOf(cookie("cdb_auth", domain = ".4d4y.com"))))
        assertNull(CookieMatcher.matchingCookieHeader("https://www.4d4y.com/forum/index.php", listOf(cookie("cdb_auth", domain = ".4d4y.com", path = "/admin"))))
        assertNull(CookieMatcher.matchingCookieHeader("https://www.4d4y.com/forum/index.php", listOf(cookie("cdb_auth", domain = ".4d4y.com", expires = 1))))
    }
    @Test fun persistentCookieUpgradeAndSignature() {
        val upgraded = CookieMatcher.withThirtyDayExpiry(cookie("cdb_auth", expires = null), nowMillis = 0)
        assertEquals(30L * 24 * 60 * 60 * 1000, upgraded.expiresAtMillis)
        assertEquals(CookieMatcher.signature(listOf(cookie("a", "1"))), CookieMatcher.signature(listOf(cookie("a", "1"))))
        assertNotEquals(CookieMatcher.signature(listOf(cookie("a", "1"))), CookieMatcher.signature(listOf(cookie("a", "2"))))
    }
}

class StoreAndModelParityTest {
    @Test fun bookmarksUrlBookmarksCommunitiesAndSettings() {
        val store = InMemoryFeedflowStore()
        val t = thread("bm")
        store.toggleBookmark(t, "hn")
        assertTrue(store.isBookmarked("bm", "hn"))
        store.toggleBookmark(t, "hn")
        assertFalse(store.isBookmarked("bm", "hn"))
        store.saveUrlBookmark("https://example.com", "Example")
        assertTrue(store.isUrlBookmarked("https://example.com"))
        store.removeUrlBookmark("https://example.com")
        assertFalse(store.isUrlBookmarked("https://example.com"))
        store.saveCommunities(listOf(community("a"), community("b")), "test")
        assertEquals(2, store.getCommunities("test").size)
        store.saveSetting("k", "v")
        assertEquals("v", store.getSetting("k"))
        store.saveSetting("k", "v2")
        assertEquals("v2", store.getSetting("k"))
        store.removeSetting("k")
        assertNull(store.getSetting("k"))
    }
    @Test fun cookiesSummariesCachesAndFilteredPosts() {
        var now = 1_000L
        val store = InMemoryFeedflowStore(clockMillis = { now })
        store.replaceCookies("4d4y", listOf(cookie("cdb_auth")))
        assertTrue(store.hasCookies("4d4y"))
        assertEquals("cdb_auth", store.getCookies("4d4y")!!.first().name)
        store.clearCookies("4d4y")
        assertFalse(store.hasCookies("4d4y"))
        store.saveSummary("t", "hn", "summary")
        assertEquals("summary", store.getSummary("t", "hn"))
        assertEquals("summary", store.getSummaryIfFresh("t", "hn", 3600))
        now += 10_000
        assertNull(store.getSummaryIfFresh("t", "hn", 0))
        val topics = listOf(thread("c1"), thread("c2"))
        store.saveCachedTopics("key", topics)
        assertEquals(2, store.getCachedTopics("key")!!.size)
        store.saveCachedThread("c1", "hn", topics.first(), listOf(Comment("comment", User("u", "U", ""), "Nice", "1h", 0)))
        assertEquals(1, store.getCachedThread("c1", "hn")!!.second.size)
        assertNull(store.getCachedThread("c1", "rss"))
        store.addFilteredPost("p", "hn")
        assertTrue(store.isPostFiltered("p", "hn"))
        assertTrue(store.getFilteredPostIds("hn").contains("p"))
    }
    @Test fun encryptedSettingsAndEncryption() {
        val secret = AesGcmSecretStore()
        val first = secret.encrypt("password")
        val second = secret.encrypt("password")
        assertNotEquals("password", first)
        assertNotEquals(first, second)
        assertEquals("password", secret.decrypt(first))
        assertNull(secret.decrypt("not-valid-base64!!!"))
        val store = InMemoryFeedflowStore(secret)
        store.saveEncryptedSetting("api", "secret")
        assertNotEquals("secret", store.getSetting("api"))
        assertEquals("secret", store.getEncryptedSetting("api"))
        store.saveSetting("legacy", "plain")
        assertEquals("plain", store.getEncryptedSetting("legacy"))
        assertNotEquals("plain", store.getSetting("legacy"))
    }
    @Test fun modelsMatchIosIdentitySemantics() {
        assertEquals("1", User("1", "Alice", "p.c", "Admin").id)
        assertEquals(5, Community("c1", "T", "d", "cat", 5, 2).activeToday)
        assertNotEquals(thread("1", "A"), thread("1", "B"))
        assertNotEquals(thread("1", "A"), thread("2", "A"))
        val mutable = thread("l")
        assertFalse(mutable.isLiked)
        mutable.isLiked = true
        assertTrue(mutable.isLiked)
        mutable.tags = listOf("swift")
        assertEquals(1, mutable.tags!!.size)
    }
}

class ParsingAndAccessibilityParityTest {
    @Test fun htmlEntityDecoding() {
        assertEquals("。", "&#12290;".decodeHtmlEntities())
        assertEquals("中文", "&#20013;&#25991;".decodeHtmlEntities())
        assertEquals("中", "&#x4E2D;".decodeHtmlEntities())
        assertEquals("&", "&amp;".decodeHtmlEntities())
        assertEquals("。 & 中 — end", "&#12290; &amp; &#x4E2D; &mdash; end".decodeHtmlEntities())
        assertEquals("Hello World", "Hello World".decodeHtmlEntities())
        assertNotNull("&#99999999;".decodeHtmlEntities())
    }
    @Test fun imageZoomAndSpeechDefaults() {
        assertEquals(1f, ImageZoom.clampScale(0.2f, 1f, 5f))
        assertEquals(5f, ImageZoom.clampScale(12f, 1f, 5f))
        assertEquals(2.5f, ImageZoom.clampScale(2.5f, 1f, 5f))
        assertFalse(SpeechServiceState.isSpeaking)
        SpeechServiceState.stop()
        assertFalse(SpeechServiceState.isSpeaking)
    }
    @Test fun rssAndOpmlParsing() {
        val rss = """<?xml version="1.0"?><rss version="2.0"><channel><item><title>Test</title><link>https://ex.com</link><description>Content</description></item></channel></rss>"""
        assertEquals("Test", RssParser(rss.toByteArray()).parse().first().title)
        val atom = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Atom</title><link href="https://ex.com"/><content>C</content></entry></feed>"""
        assertEquals("Atom", RssParser(atom.toByteArray()).parse().first().title)
        assertTrue(RssParser("""<?xml version="1.0"?>""".toByteArray()).parse().isEmpty())
        val opml = """<?xml version="1.0"?><opml version="2.0"><body><outline text="F1" xmlUrl="https://ex.com/1.xml"/><outline text="F2" xmlUrl="https://ex.com/2.xml"/></body></opml>"""
        val feeds = OpmlParser(opml.toByteArray()).parse()
        assertEquals(2, feeds.size)
        assertEquals("F1", feeds.first().title)
    }

    @Test fun rssParserExtractsIosSupportedFieldsAndCleanerTokens() {
        val rss = """
            <?xml version="1.0"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <item>
                  <title>Tom &amp;amp; Jerry</title>
                  <link>https://example.com/post</link>
                  <guid>guid-1</guid>
                  <dc:creator>Alice</dc:creator>
                  <pubDate>Sat, 07 Sep 2002 00:00:01 GMT</pubDate>
                  <content:encoded><![CDATA[<p>Hello <a href="https://example.com">site</a></p><script>x()</script><img src="https://example.com/a.png" />]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val item = RssParser(rss.toByteArray()).parse().single()
        assertEquals("Tom & Jerry", item.title)
        assertEquals("https://example.com/post", item.link)
        assertEquals("Alice", item.author)
        assertEquals("guid-1", item.id)
        val cleaned = RssContentCleaner.clean(item.content)
        assertTrue(cleaned.contains("[LINK:https://example.com|site]"))
        assertTrue(cleaned.contains("[IMAGE:https://example.com/a.png]"))
        assertFalse(cleaned.contains("script"))
    }

    @Test fun rssParserFallsBackWhenAndroidDomReturnsNoItems() {
        val rss = """
            <?xml version="1.0"?>
            <rss version="2.0">
              <channel>
                <title>Invalid & channel title</title>
                <item>
                  <title><![CDATA[Fallback Item]]></title>
                  <link>https://example.com/fallback</link>
                  <description><![CDATA[<p>Fallback body</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val item = RssParser(rss.toByteArray()).parse().single()
        assertEquals("Fallback Item", item.title)
        assertEquals("https://example.com/fallback", item.link)
        assertTrue(item.content.contains("Fallback body"))
    }

    @Test fun opmlParserUsesTextTitleUrlFallback() {
        val opml = """<opml><body><outline xmlUrl="https://a.xml" text="A"/><outline xmlUrl="https://b.xml" title="B"/><outline xmlUrl="https://c.xml"/><outline text="skip"/></body></opml>"""
        val feeds = OpmlParser(opml.toByteArray()).parse()
        assertEquals(listOf("A", "B", "https://c.xml"), feeds.map { it.title })
        assertEquals(listOf("https://a.xml", "https://b.xml", "https://c.xml"), feeds.map { it.url })
    }
}

class ReadOnlySourceParityTest {
    @Test fun hackerNewsCategoriesAndJsonMappingMirrorIos() = kotlinx.coroutines.runBlocking {
        val client = FixtureHttpClient(
            mapOf(
                "https://hacker-news.firebaseio.com/v0/topstories.json" to "[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21]",
            ) + (1..20).associate { id ->
                "https://hacker-news.firebaseio.com/v0/item/$id.json" to """{"id":$id,"type":"story","by":"pg","time":1000,"title":"Story $id","url":"https://example.com/$id","score":42,"descendants":7}"""
            },
        )
        val service = HackerNewsService(client)
        val categories = service.fetchCategories()
        assertEquals(listOf("topstories", "newstories", "beststories", "showstories", "askstories", "jobstories"), categories.map { it.id })
        val threads = service.fetchCategoryThreads("topstories", categories, page = 1)
        assertEquals(20, threads.size)
        assertEquals("Story 1", threads.first().title)
        assertEquals(42, threads.first().likeCount)
        assertEquals(7, threads.first().commentCount)
        assertTrue(client.requested.none { it.endsWith("/item/21.json") })
        assertTrue(service.fetchCategoryThreads("topstories", categories, page = 2).isEmpty())
    }

    @Test fun hackerNewsDetailCleansContentAndSkipsDeletedComments() = kotlinx.coroutines.runBlocking {
        val client = FixtureHttpClient(
            mapOf(
                "https://hacker-news.firebaseio.com/v0/item/100.json" to """{"id":100,"type":"story","by":"alice","time":1000,"title":"Ask HN","text":"<p>Hello &amp; welcome","url":"https://example.com","score":5,"descendants":2,"kids":[101,102]}""",
                "https://hacker-news.firebaseio.com/v0/item/101.json" to """{"id":101,"type":"comment","by":"bob","time":1001,"text":"<i>Nice</i> <a href=\"https://news.ycombinator.com\">HN</a>"}""",
                "https://hacker-news.firebaseio.com/v0/item/102.json" to """{"id":102,"type":"comment","by":"dead","time":1002,"text":"gone","deleted":true}""",
            ),
        )
        val detail = HackerNewsService(client).fetchThreadDetail("100", page = 1)
        assertEquals("Ask HN", detail.thread.title)
        assertTrue(detail.thread.content.contains("Hello & welcome"))
        assertTrue(detail.thread.content.contains("Link: https://example.com"))
        assertEquals(1, detail.comments.size)
        assertEquals("_Nice_ https://news.ycombinator.com", detail.comments.single().content)
        assertTrue(HackerNewsService(client).fetchThreadDetail("100", page = 2).comments.isEmpty())
    }

    @Test fun rssServiceUsesDefaultFeedsAndCacheFallbackDetail() = kotlinx.coroutines.runBlocking {
        val feedUrl = RssService.defaultFeeds.first().url
        val client = FixtureHttpClient(
            mapOf(
                feedUrl to """<rss><channel><item><title>RSS Item</title><link>https://example.com/rss</link><description><![CDATA[<p>Body</p>]]></description></item></channel></rss>""",
            ),
        )
        val service = RssService(client)
        val categories = service.fetchCategories()
        assertEquals(listOf("hacker_podcast", "ruanyifeng", "oreilly"), categories.map { it.id })
        val threads = service.fetchCategoryThreads("hacker_podcast", categories, page = 99)
        assertEquals("https://example.com/rss", threads.single().id)
        assertEquals("Body", threads.single().content)
        assertEquals("https://example.com/rss", service.getWebUrl(threads.single()))
        val detail = service.fetchThreadDetail("https://example.com/rss", page = 1)
        assertEquals("RSS Item", detail.thread.title)
        assertEquals(1, detail.totalPages)
    }
}

class RemainingSourceParserParityTest {
    @Test fun fourD4YParserHandlesEncodingSessionCategoriesThreadsAndCleanup() {
        val chinese = "发现 好文"
        assertEquals(chinese, FourD4YParser.decodeForumBytes(chinese.toByteArray(Charset.forName("GB18030"))))
        assertTrue(FourD4YParser.encodeFormValue("中文").contains("%"))
        val loggedIn = """<a href="forumdisplay.php?fid=2">Discovery</a><a href="logging.php?action=logout&amp;formhash=abc123">退出</a>"""
        val guest = """<a href="forumdisplay.php?fid=2">Discovery</a><a href="logging.php?action=login">登录</a>"""
        assertTrue(FourD4YParser.isLoggedInIndex(loggedIn))
        assertFalse(FourD4YParser.isLoggedInIndex(guest))
        assertFalse(FourD4YParser.isLoggedInIndex("cloudflare checking your browser sid=guest"))
        assertEquals("abc123", FourD4YParser.extractFormHash(loggedIn))
        assertEquals("deadbeef", FourD4YParser.extractFormHash("""<input value="deadbeef" name="formhash">"""))
        assertEquals("sid123", FourD4YParser.extractSid("""<a href="forumdisplay.php?fid=2&sid=sid123">x</a>"""))
        val categories = FourD4YParser.parseCategories("""<a href="forumdisplay.php?fid=2">Discovery</a><a href="forumdisplay.php?fid=7">交易</a>""")
        assertEquals(listOf("2", "7"), categories.map { it.id })
        val rows = FourD4YParser.parseThreadRows(
            """<tbody id="normalthread_42"><a href="viewthread.php?tid=42">标题&amp;A</a><td class="author"><a href="space.php?action=viewpro&amp;uid=12345">joe</a></td><td class="nums"><strong>9</strong></td></tbody>""",
            categories.first(),
        )
        assertEquals("标题&A", rows.single().title)
        assertEquals("12345", rows.single().author.id)
        assertEquals("joe", rows.single().author.username)
        assertTrue(rows.single().author.avatar.endsWith("/000/01/23/45_avatar_middle.jpg"))
        assertEquals(9, rows.single().commentCount)
        val searchRows = FourD4YParser.parseSearchThreads(
            """
                <ul>
                  <li><a href="viewthread.php?tid=51">搜索结果一</a><p class="author"><a href="space.php?action=viewpro&amp;uid=504383">iamez</a> / 今天</p></li>
                  <li><a href="viewthread.php?tid=52">搜索结果二</a><p class="by"><a href="space-uid-737271.html">跳跳猪</a> / 今天</p></li>
                </ul>
            """,
        )
        assertEquals(listOf("iamez", "跳跳猪"), searchRows.map { it.author.username })
        assertEquals(listOf("504383", "737271"), searchRows.map { it.author.id })
        assertTrue(searchRows.all { it.author.avatar.startsWith("https://img02.4d4y.com/") })
        val cleaned = FourD4YParser.cleanContent("""<blockquote>quote</blockquote><a href="https://x">X</a><img src="https://img/a.jpg">""")
        assertTrue(cleaned.contains("[QUOTE]quote[/QUOTE]"))
        assertTrue(cleaned.contains("[LINK:https://x|X]"))
        assertTrue(cleaned.contains("[IMAGE:https://img/a.jpg]"))
    }

    @Test fun v2exParserHandlesTabsOnceTopicListRepliesAndUrls() {
        assertEquals("tech", V2exParser.tabs.first())
        assertEquals("planet", V2exParser.tabs.last())
        assertEquals("12345", V2exParser.extractOnce("""<input name="once" value="12345">"""))
        assertEquals("678", V2exParser.extractOnce("""var once = "678";"""))
        val html = """
            <div class="cell item"><a href="/t/100#reply1" class="topic-link">Hello &amp; V2EX</a><a href="/member/alice">alice</a><a class="count_livid">12</a></div>
            <div class="cell item"><a href="/t/101" class="topic-link">Second</a><a href="/member/bob">bob</a></div>
        """.trimIndent()
        val topics = V2exParser.parseThreadList(html)
        assertEquals(listOf("100", "101"), topics.map { it.id })
        assertEquals("Hello & V2EX", topics.first().title)
        assertEquals(12, topics.first().replies)
        val replies = V2exParser.parseReplies("""<div id="r_99" class="cell"><a class="dark">alice</a><span class="ago">1h</span><div class="reply_content">Hi<br><img src="//img.test/a.png"></div></div>""")
        assertEquals("99", replies.single().id)
        assertTrue(replies.single().content.contains("[IMAGE:https://img.test/a.png]"))
        assertEquals("https://v2ex.com/path", V2exParser.normalizeUrl("/path"))
    }

    @Test fun zhihuParserHandlesCategoriesAvatarFilteringSearchAndHtml() {
        assertEquals(listOf("recommend", "hot"), ZhihuParser.categories.map { it.id })
        assertEquals("https://pic.zhimg.com/a_80.jpg", ZhihuParser.normalizedAvatarUrl("//pic.zhimg.com/a_{size}.jpg"))
        assertEquals("answer_123", ZhihuParser.threadId("answer", "123"))
        assertFalse(ZhihuParser.shouldKeepRecommendation("feed_advert", voteCount = 999))
        assertFalse(ZhihuParser.shouldKeepRecommendation("answer", voteCount = 9))
        assertTrue(ZhihuParser.shouldKeepRecommendation("answer", voteCount = 10))
        assertTrue(ZhihuParser.shouldKeepRecommendation("article", voteCount = 1, followerCount = 60))
        val cleaned = ZhihuParser.cleanHtml("""<noscript>skip</noscript><a href="https://zhihu.com/p/1">文章</a><img src="//pic.zhimg.com/a.jpg"><img src="//pic.zhimg.com/a.jpg?x=1">""")
        assertTrue(cleaned.contains("[LINK:https://zhihu.com/p/1|文章]"))
        assertEquals(1, Regex("\\[IMAGE:").findAll(cleaned).count())
        val urls = ZhihuParser.extractSearchUrls("""<a href="https://www.zhihu.com/question/1/answer/2">A</a><a href="https://www.zhihu.com/p/3">P</a>""")
        assertEquals(listOf("https://www.zhihu.com/question/1/answer/2", "https://www.zhihu.com/p/3"), urls)
    }

    @Test fun discourseParserHandlesCategoriesAvatarAndCookedHtml() {
        val categories = DiscourseParser.parseCategories("""{"category_list":{"categories":[{"id":5,"name":"General","description":"Talk","slug":"general","topic_count":42}]}}""")
        assertEquals("latest", categories.first().id)
        assertEquals("5", categories[1].id)
        assertEquals("General", categories[1].name)
        assertEquals(42, categories[1].activeToday)
        assertEquals("https://linux.do/user_avatar/linux.do/alice/64/1.png", DiscourseParser.resolveAvatar("/user_avatar/linux.do/alice/{size}/1.png"))
        val cleaned = DiscourseParser.cleanCooked("""<p>Hello &amp; world<img src="/uploads/a.png"></p><img class="emoji" alt="🙂" src="/images/emoji.png">""")
        assertTrue(cleaned.contains("Hello & world"))
        assertTrue(cleaned.contains("[IMAGE:https://linux.do/uploads/a.png]"))
        assertTrue(cleaned.contains("🙂"))
    }
}

class PerSiteServiceParityTest {
    @Test fun servicePropertiesAndDefaults() {
        assertEquals("4D4Y", FourD4YService().name)
        assertTrue(FourD4YService().requiresLogin)
        assertTrue(FourD4YService().supportsCommenting)
        assertTrue(FourD4YService().supportsThreadCreation)
        assertEquals("V2EX", V2exService().name)
        assertEquals("Linux.do", DiscourseService().name)
        assertFalse(HackerNewsService().requiresLogin)
        assertEquals("知乎", ZhihuService().name)
        assertEquals("RSS Feeds", RssService().name)
        assertEquals("feed", RssService().logo)
        assertEquals("flame.fill", HackerNewsService().logo)
        assertEquals("4.circle.fill", FourD4YService().logo)
        assertEquals("point.3.connected.trianglepath.dotted", V2exService().logo)
        assertEquals("terminal.fill", DiscourseService().logo)
        assertEquals("questionmark.bubble.fill", ZhihuService().logo)
    }
    @Test fun timeAgoAndWebUrl() {
        val service = FourD4YService()
        assertEquals("just now", service.calculateTimeAgo(java.time.Instant.now()))
        assertEquals("2m", service.calculateTimeAgo(java.time.Instant.now().minusSeconds(120)))
        assertEquals("2h", service.calculateTimeAgo(java.time.Instant.now().minusSeconds(7200)))
        assertEquals("2d", service.calculateTimeAgo(java.time.Instant.now().minusSeconds(172800)))
        assertEquals("now", service.calculateTimeAgo("not-a-date"))
        assertTrue(HackerNewsService().getWebUrl(thread("123")).contains("123"))
        assertTrue(service.getWebUrl(thread("12345")).contains("4d4y.com"))
    }
    @Test fun fourD4YAvatarAndPostingParsing() {
        val service = FourD4YService()
        assertTrue(service.avatarUrlForUid("12345").contains("uc_server/data/avatar"))
        assertTrue(service.avatarUrlForUid("1").contains("000/00/00/01_avatar_middle.jpg"))
        assertTrue(service.avatarUrlForUid("999999999").contains("999/99/99/99_avatar_middle.jpg"))
        assertEquals("person.circle", service.avatarUrlForUid("not-a-number"))
        val typeHtml = """<select name="typeid"><option value="0">选择分类</option><option value="123">[大杂烩]</option></select>"""
        assertEquals("123", service.extractFirstTypeId(typeHtml))
        assertNull(service.extractFirstTypeId("""<select name="sort"><option value="999">Wrong field</option></select>"""))
        assertNull(service.extractFirstTypeId("<form></form>"))
        assertEquals("74407801", service.parseFirstPostId("""<em id="authorposton74407801">发表于</em>"""))
        assertEquals("74407802", service.parseFirstPostId("""<a href="post.php?reppost=74407802">reply</a>"""))
        assertEquals("Webrules", FourD4YService.parseLoggedInUsername("<div>欢迎您回来，<strong>Webrules</strong> <a href='logout'>退出</a></div>"))
        val store = InMemoryFeedflowStore()
        store.saveSetting("detected_4d4y_username", "Webrules")
        assertEquals("Webrules", FourD4YService(store).currentUsername)
        assertTrue(FourD4YService(store).canDeleteThread(thread(author = User("u", "Webrules", ""))))
        assertFalse(FourD4YService(store).canDeleteThread(thread(author = User("u", "someoneElse", ""))))
    }
    @Test fun sessionValidationMirror() {
        val valid = """<a href="forumdisplay.php?fid=2">Discovery</a><a href="logging.php?action=logout&amp;formhash=abc123">退出</a>"""
        val guest = """<a href="forumdisplay.php?fid=2">Discovery</a><a href="logging.php?action=login">登录</a>"""
        assertTrue(valid.contains("action=logout"))
        assertFalse(guest.contains("action=logout"))
        assertEquals("abc123xyz", Regex("sid=([a-zA-Z0-9]+)").find("<a href='forumdisplay.php?fid=2&sid=abc123xyz'>Link</a>")!!.groupValues[1])
        assertEquals("deadbeef12", Regex("formhash=([a-zA-Z0-9]+)").find("...formhash=deadbeef12...")!!.groupValues[1])
    }
}

class ZhihuAndLocalizationParityTest {
    @Test fun zhihuAvatarNormalization() {
        val service = ZhihuService()
        assertFalse(service.normalizedAvatarUrl("https://pic.zhimg.com/v2-abc_{size}.jpg").contains("{size}"))
        assertTrue(service.normalizedAvatarUrl("https://pic.zhimg.com/v2-abc_{size}.jpg").contains("_80.jpg"))
        assertEquals("https://pic.zhimg.com/avatar.jpg", service.normalizedAvatarUrl("//pic.zhimg.com/avatar.jpg"))
        assertTrue(service.normalizedAvatarUrl("", "https://pic.zhimg.com/v2-x_{size}.jpg").contains("_80.jpg"))
        assertEquals("", service.normalizedAvatarUrl("", null))
        assertEquals("https://pic.zhimg.com/a.jpg?x=1&y=2", service.normalizedAvatarUrl("https://pic.zhimg.com/a.jpg?x=1&amp;y=2"))
        assertEquals("匿名用户", "".ifEmpty { "匿名用户" })
    }
    @Test fun localizationKeysAndLanguageToggle() {
        val manager = LocalizationManager()
        val keys = listOf("login", "cancel", "done", "save", "close", "error", "select_community", "settings", "bookmarks", "ai_assistant", "reply", "reply_failed", "new_thread", "post_failed", "thread_title", "share_thoughts", "signed_in", "signed_out", "logout", "save_session", "login_with_browser", "login_to_site", "login_success", "communities", "manage_feeds", "daily_rss_summary", "browser", "thread_bookmarks", "url_bookmarks")
        keys.forEach {
            assertTrue(manager.localizedString(it).isNotEmpty())
            assertNotEquals(it, manager.localizedString(it))
        }
        val current = manager.currentLanguage
        manager.currentLanguage = if (current == "en") "zh" else "en"
        assertNotEquals(current, manager.currentLanguage)
    }
    @Test fun schemaMigrationDecision() {
        assertFalse(DatabaseSchemaMigration.needsCompositePrimaryKeyMigration(listOf("a", "b"), listOf("a", "b")))
        assertTrue(DatabaseSchemaMigration.needsCompositePrimaryKeyMigration(listOf("a"), listOf("a", "b")))
    }
    @Test fun databaseSchemaAndCacheKeysMirrorIosContracts() {
        assertEquals("Feedflow.sqlite", FeedflowDatabaseContract.databaseName)
        assertEquals("login_4d4y_cookies", FeedflowDatabaseContract.cookieSettingKey("4d4y"))
        assertTrue(FeedflowDatabaseContract.schemaStatements.any { it.contains("PRIMARY KEY (id, serviceId)") })
        assertTrue(FeedflowDatabaseContract.schemaStatements.any { it.contains("PRIMARY KEY (thread_id, service_id)") })
        assertTrue(FeedflowDatabaseContract.schemaStatements.any { it.contains("PRIMARY KEY (postId, serviceId)") })
        assertTrue(FeedflowDatabaseContract.schemaStatements.any { it.contains("CREATE TABLE IF NOT EXISTS rss_feeds") })
        assertEquals("4d4y_2_page1", FeedflowCacheKeys.topicList("4d4y", "2"))
        assertEquals("4d4y_2_page3", FeedflowCacheKeys.topicList("4d4y", "2", page = 3))
        assertEquals("thread1" to "4d4y", FeedflowCacheKeys.threadDetail("4d4y", "thread1"))
        assertEquals("thread1_4d4y_zh" to "4d4y", FeedflowCacheKeys.summary("thread1", "4d4y", "zh"))
    }
}
