package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.network.FeedflowHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val secondPage = service.fetchThreadDetail("123", 2)
        assertEquals(2, secondPage.totalPages)
        assertEquals("", secondPage.thread.title)
        assertTrue(secondPage.comments.isEmpty())
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
            "https://linux.do/t/9.json?page=1" to """
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
                <div class="detailcon">Original<br>post<div class="t_attach">download.bin</div><ignore_js_op>ignored attachment</ignore_js_op><img src="https://cdn.example.com/a.jpg"><img src="/images/common/back.gif"><a href="https://example.com">Example</a></div>
                <li id="pid456">
                  <a href="space.php?uid=456">bob</a>/ 30m</div>
                  <div class="replycon">Reply<br>content<img src="https://cdn.example.com/r.png"></div>
                </li>
            """,
            "https://www.4d4y.com/forum/viewthread.php?tid=99&page=2" to """
                <h2><a href="viewthread.php?tid=99">4D4Y detail</a></h2>
                <a href="forumdisplay.php?fid=7">技术交流</a>
                <li id="pid789">
                  <a href="space.php?uid=789">carol</a>/ 10m</div>
                  <div class="replycon">Second page reply</div>
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
        assertTrue(detail.thread.content.contains("[IMAGE:https://cdn.example.com/a.jpg]"))
        assertTrue(detail.thread.content.contains("Example (https://example.com)"))
        assertTrue(!detail.thread.content.contains("download.bin"))
        assertTrue(!detail.thread.content.contains("back.gif"))
        assertEquals("bob", detail.comments.single().author.username)
        assertTrue(detail.comments.single().content.contains("[IMAGE:https://cdn.example.com/r.png]"))
        val secondPage = service.fetchThreadDetail("99", 2)
        assertEquals("", secondPage.thread.content)
        assertEquals("", secondPage.thread.author.username)
        assertEquals(0, secondPage.thread.commentCount)
        assertEquals("carol", secondPage.comments.single().author.username)
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
        assertEquals("https://www.zhihu.com/question/0/answer/1", ZhihuService().getWebUrl(sampleThread("answer_1")))
    }

    @Test fun zhihuServiceParsesHotSearchAndDetailJson() = runBlocking {
        val service = ZhihuService(
            SourceFixtureHttpClient(
                "https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=20&desktop=true" to
                    """{"data":[{"card_id":"Q_123","detail_text":"100万热度","target":{"title_area":{"text":"Hot question"},"excerpt_area":{"text":"Hot excerpt"},"metrics_area":{"follower_count":10,"answer_count":5}},"children":[{"author":{"name":"作者"}}]}]}""",
                "https://www.zhihu.com/api/v4/search_v3?q=android+parity&t=content&correction=1&offset=0&limit=20" to
                    """{"data":[{"object":{"type":"article","id":789,"title":"Search article","excerpt":"body","author":{"name":"A"}}}],"paging":{"is_end":true}}""",
                "https://www.zhihu.com/api/v4/answers/456?include=content,html_content,excerpt,thanks_count,voteup_count,comment_count,visited_count,author" to
                    """{"content":"Answer body","voteup_count":3,"comment_count":1,"question":{"title":"Android parity"},"author":{"name":"Writer"}}""",
                "https://www.zhihu.com/api/v4/answers/456/root_comments?limit=20&offset=0&order=normal&status=open" to
                    """{"data":[{"id":"c1","content":"Nice","created_time":0,"like_count":2,"author":{"member":{"name":"Reader"}}}]}""",
            ),
        )
        val categories = service.fetchCategories()
        val hot = service.fetchCategoryThreads("hot", categories, 1)
        assertEquals("question_123", hot.single().id)
        assertEquals("Hot question", hot.single().title)

        val search = service.searchThreads("android parity", 1)
        assertEquals("article_789", search.threads.single().id)
        assertEquals("Search article", search.threads.single().title)
        assertEquals("https://zhuanlan.zhihu.com/p/789", service.getWebUrl(search.threads.single()))

        val detail = service.fetchThreadDetail("answer_456", 1)
        assertEquals("Android parity", detail.thread.title)
        assertEquals("Answer body", detail.thread.content)
        assertEquals("Nice", detail.comments.single().content)
        assertEquals("Reader", detail.comments.single().author.username)
        assertFailsWith<FeedflowError.Parsing> { service.fetchThreadDetail("invalid", 1) }
        assertFailsWith<FeedflowError.Parsing> { service.fetchThreadDetail("video_456", 1) }
        Unit
    }

    @Test fun zhihuServiceMarksRecommendationsReadAndSendsNotInterestedPayload() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("zhihu", listOf(FeedflowCookie("z_c0", "token", "zhihu.com", expiresAtMillis = null)))
        val httpClient = SourceFixtureHttpClient(
            "https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=10" to
                """{"data":[
                    {"type":"feed","target":{"type":"answer","id":456,"excerpt":"Read body","voteup_count":20,"comment_count":1,"question":{"title":"Read item"},"author":{"name":"Reader"}}},
                    {"type":"feed","target":{"type":"article","id":789,"title":"Visible item","excerpt":"Visible body","voteup_count":30,"comment_count":2,"author":{"name":"Writer","follower_count":100}}}
                ]}""",
        )
        val service = ZhihuService(httpClient, store)
        val categories = service.fetchCategories()
        val firstLoad = service.fetchCategoryThreads("recommend", categories, 1)
        assertEquals(listOf("answer_456", "article_789"), firstLoad.map { it.id })

        service.markThreadRead(firstLoad.first())
        assertTrue(store.isPostFiltered("456", "zhihu"))
        assertEquals(listOf("article_789"), service.fetchCategoryThreads("recommend", categories, 1).map { it.id })

        service.markThreadNotInterested(firstLoad.last())
        assertTrue(store.isPostFiltered("789", "zhihu"))
        assertTrue(store.isPostFiltered("article_789", "zhihu"))
        assertEquals("https://www.zhihu.com/api/v3/feed/topstory/uninterest", httpClient.lastPostUrl)
        assertEquals("""{"target_type":"article","target_id":"789","reason":"not_interested"}""", httpClient.lastPostBody)
        assertEquals("application/json; charset=UTF-8", httpClient.lastContentType)
        assertEquals("z_c0=token", httpClient.lastCookieHeader)
        assertEquals("789", store.getSetting("zhihu_downvoted_ids"))
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
    var lastContentType: String = ""

    override suspend fun get(url: String, cookies: List<FeedflowCookie>): String =
        fixtures.firstOrNull { (fixtureUrl, _) -> fixtureUrl == url }?.second ?: error("Missing fixture for $url")

    override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String {
        lastPostUrl = url
        lastPostBody = body
        lastCookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        lastContentType = contentType
        return "ok"
    }
}
