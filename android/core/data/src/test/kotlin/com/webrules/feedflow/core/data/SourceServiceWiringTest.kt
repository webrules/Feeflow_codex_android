package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.network.FeedflowHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceServiceWiringTest {
    @Test fun v2exServiceUsesTabsThreadParserAndReplyParser() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("v2ex", listOf(FeedflowCookie("a2", "token", "v2ex.com", expiresAtMillis = null)))
        val httpClient = SourceFixtureHttpClient(
            "https://www.v2ex.com/?tab=tech" to """
                <div class="cell item">
                  <a href="/t/123#reply1" class="topic-link">Android parity</a>
                  <a href="/member/alice">alice</a>
                  <a class="count_livid">8</a>
                </div>
            """,
            "https://www.v2ex.com/t/123" to """
                <h1>Android parity detail</h1>
                <a href="/member/alice">alice</a>
                <input type="hidden" name="once" value="7788">
                <div class="topic_content">Original<br>topic</div>
                <div id="r_1"><a class="dark">bob</a><span class="ago">1 min ago</span><div class="reply_content">Hello<br>world</div></div>
            """,
        )
        val service = V2exService(
            store = store,
            httpClient = httpClient,
        )
        assertTrue(service.fetchCategories().any { it.id == "tech" })
        val threads = service.fetchCategoryThreads("tech", listOf(Community("tech", "Tech", "", "V2EX", 0, 0)), 1)
        assertEquals("Android parity", threads.single().title)
        assertEquals(8, threads.single().commentCount)
        val detail = service.fetchThreadDetail("123", 1)
        assertEquals("Android parity detail", detail.thread.title)
        assertTrue(detail.thread.content.contains("Original"))
        assertEquals("bob", detail.comments.single().author.username)
        assertTrue(detail.comments.single().content.contains("Hello"))
        service.postComment("123", "tech", "hello world")
        assertEquals("https://www.v2ex.com/t/123", httpClient.lastPostUrl)
        assertEquals("content=hello+world&once=7788", httpClient.lastPostBody)
        assertEquals("a2=token", httpClient.lastCookieHeader)
    }

    @Test fun discourseServiceUsesJsonCategoriesAndTopics() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("linux_do", listOf(FeedflowCookie("_t", "token", "linux.do", expiresAtMillis = null)))
        val httpClient = SourceFixtureHttpClient(
            "https://linux.do/categories.json" to """{"category_list":{"categories":[{"id":5,"name":"General","description":"Talk","slug":"general","topic_count":42}]}}""",
            "https://linux.do/c/5.json?page=1" to """{"topic_list":{"topics":[{"id":9,"title":"Linux topic","posts_count":3}]}}""",
            "https://linux.do/t/9.json" to """
                {"title":"Linux topic","category_id":5,"post_stream":{"posts":[
                  {"id":91,"username":"alice","cooked":"<p>Original &amp; post</p>"},
                  {"id":92,"username":"bob","cooked":"<p>Reply<br>body</p>"}
                ]}}
            """.trimIndent(),
        )
        val service = DiscourseService(
            store = store,
            httpClient = httpClient,
        )
        val categories = service.fetchCategories()
        assertEquals("latest", categories.first().id)
        assertEquals("General", categories[1].name)
        val threads = service.fetchCategoryThreads("5", categories, 1)
        assertEquals("Linux topic", threads.single().title)
        assertEquals(2, threads.single().commentCount)
        val detail = service.fetchThreadDetail("9", 1)
        assertEquals("Original & post", detail.thread.content)
        assertEquals("bob", detail.comments.single().author.username)
        assertTrue(detail.comments.single().content.contains("Reply"))
        service.createThread("5", "New title", "New body")
        assertEquals("https://linux.do/posts.json", httpClient.lastPostUrl)
        assertEquals("title=New+title&raw=New+body&category=5", httpClient.lastPostBody)
        service.postComment("9", "5", "Reply body")
        assertEquals("topic_id=9&raw=Reply+body", httpClient.lastPostBody)
        assertEquals("_t=token", httpClient.lastCookieHeader)
    }

    @Test fun fourD4YServiceUsesDiscuzCategoriesAndThreadRows() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("4d4y", listOf(FeedflowCookie("auth", "token", "4d4y.com", expiresAtMillis = null)))
        val httpClient = SourceFixtureHttpClient(
            "https://www.4d4y.com/forum/index.php" to """<a href="forumdisplay.php?fid=7">技术交流</a>""",
            "https://www.4d4y.com/forum/forumdisplay.php?fid=7&page=1" to """
                <tbody id="normalthread_99">
                  <a href="viewthread.php?tid=99">GB18030 topic</a>
                  <a href="space.php?uid=123">alice</a>
                  <td class="nums"><strong>4</strong></td>
                </tbody>
            """,
            "https://www.4d4y.com/forum/viewthread.php?tid=99&page=1" to """
                <h2><a href="viewthread.php?tid=99">4D4Y detail</a></h2>
                <a href="forumdisplay.php?fid=7">技术交流</a>
                <a href="space.php?uid=123">alice</a><em>发表于 1h</em>
                <input type="hidden" name="formhash" value="abcd1234">
                <div class="detailcon">Original post</div>
                <li id="pid456">
                  <a href="space.php?uid=456">bob</a>/ 30m</div>
                  <div class="replycon">Reply content</div>
                </li>
            """,
            "https://www.4d4y.com/forum/viewthread.php?tid=99" to """
                <input type="hidden" name="formhash" value="abcd1234">
                <div id="authorposton456"></div>
            """,
            "https://www.4d4y.com/forum/post.php?action=edit&fid=7&tid=99&pid=456&page=1" to """
                <input type="hidden" name="formhash" value="ijkl9012">
            """,
            "https://www.4d4y.com/forum/post.php?action=newthread&fid=7" to """
                <input type="hidden" name="formhash" value="efgh5678">
                <select><option value="0">None</option><option value="12">Type</option></select>
            """,
        )
        val service = FourD4YService(
            store = store,
            httpClient = httpClient,
        )
        val categories = service.fetchCategories()
        assertEquals("技术交流", categories.single().name)
        val threads = service.fetchCategoryThreads("7", categories, 1)
        assertEquals("GB18030 topic", threads.single().title)
        assertEquals(4, threads.single().commentCount)
        val detail = service.fetchThreadDetail("99", 1)
        assertEquals("4D4Y detail", detail.thread.title)
        assertTrue(detail.thread.content.contains("Original"))
        assertEquals("bob", detail.comments.single().author.username)
        service.postComment("99", "7", "Reply body")
        assertEquals("https://www.4d4y.com/forum/post.php?action=reply&fid=7&tid=99&extra=&replysubmit=yes&inajax=1", httpClient.lastPostUrl)
        assertEquals("formhash=abcd1234&subject=&message=Reply+body&replysubmit=yes", httpClient.lastPostBody)
        service.createThread("7", "Title body", "Thread body")
        assertEquals("https://www.4d4y.com/forum/post.php?action=newthread&fid=7&extra=&topicsubmit=yes&inajax=1", httpClient.lastPostUrl)
        assertEquals("formhash=efgh5678&typeid=12&subject=Title+body&message=Thread+body&topicsubmit=yes", httpClient.lastPostBody)
        service.deleteThread("99", "7")
        assertEquals("https://www.4d4y.com/forum/post.php?action=edit&fid=7&tid=99&pid=456&page=1&editsubmit=yes&inajax=1", httpClient.lastPostUrl)
        assertEquals("formhash=ijkl9012&delete=1&editsubmit=yes&inajax=1", httpClient.lastPostBody)
        assertEquals("auth=token", httpClient.lastCookieHeader)
    }

    @Test fun fourD4YRestoreSessionStoresLoggedInUsernameForDeleteGating() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("4d4y", listOf(FeedflowCookie("auth", "token", "4d4y.com", expiresAtMillis = null)))
        val service = FourD4YService(
            store = store,
            httpClient = SourceFixtureHttpClient(
                "https://www.4d4y.com/forum/index.php" to "<div>欢迎您回来，<strong>Webrules</strong></div>",
            ),
        )

        assertTrue(service.restoreSession())
        assertEquals("Webrules", store.getSetting("detected_4d4y_username"))
        assertTrue(service.canDeleteThread(sampleThread("99").copy(author = com.webrules.feedflow.core.model.User("u", "Webrules", ""))))
    }

    @Test fun zhihuServiceExposesRecommendationAndHotCategories() = runBlocking {
        val categories = ZhihuService().fetchCategories()
        assertEquals(listOf("recommend", "hot"), categories.map { it.id })
        assertEquals("https://www.zhihu.com/question/answer_1", ZhihuService().getWebUrl(sampleThread("answer_1")))
    }

    @Test fun zhihuServiceUsesSearchUrlsForHotAndSearchResults() = runBlocking {
        val service = ZhihuService(
            SourceFixtureHttpClient(
                "https://www.zhihu.com/hot" to """<a href="https://www.zhihu.com/question/123/answer/456">Answer</a>""",
                "https://www.zhihu.com/question/123/answer/456" to """<html><head><title>Android parity - 知乎</title><meta name="description" content="Zhihu answer body"></head></html>""",
                "https://www.zhihu.com/search?type=content&q=android+parity" to """<a href="https://www.zhihu.com/p/789">Article</a>""",
            ),
        )
        val categories = service.fetchCategories()
        val hot = service.fetchCategoryThreads("hot", categories, 1)
        assertEquals("question/123/answer/456", hot.single().id)
        assertEquals("https://www.zhihu.com/question/123/answer/456", service.getWebUrl(hot.single()))

        val search = service.searchThreads("android parity", 1)
        assertEquals("p/789", search.threads.single().id)
        assertEquals("https://www.zhihu.com/p/789", service.getWebUrl(search.threads.single()))
        val detail = service.fetchThreadDetail("question/123/answer/456", 1)
        assertEquals("Android parity", detail.thread.title)
        assertEquals("Zhihu answer body", detail.thread.content)
    }

    private fun sampleThread(id: String) = com.webrules.feedflow.core.model.FeedThread(
        id = id,
        title = "Title",
        content = "",
        author = com.webrules.feedflow.core.model.User("u", "user", ""),
        community = Community("recommend", "Recommendations", "", "Zhihu", 0, 0),
        timeAgo = "now",
        likeCount = 0,
        commentCount = 0,
    )
}

private class SourceFixtureHttpClient(private vararg val fixtures: Pair<String, String>) : FeedflowHttpClient {
    var lastPostUrl: String = ""
    var lastPostBody: String = ""
    var lastCookieHeader: String = ""

    override suspend fun get(url: String, cookies: List<FeedflowCookie>): String =
        fixtures.firstOrNull { (fixtureUrl, _) -> fixtureUrl == url }?.second ?: error("Missing fixture for $url")

    override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String {
        lastPostUrl = url
        lastPostBody = body
        lastCookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        return "ok"
    }
}
