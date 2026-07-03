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
        store.saveCookies("4d4y", listOf(
            FeedflowCookie("auth", "token", "4d4y.com", expiresAtMillis = null),
            FeedflowCookie("sid", "SID123", "4d4y.com", expiresAtMillis = null),
        ))
        val httpClient = SourceFixtureHttpClient(
            "https://www.4d4y.com/forum/index.php?sid=SID123" to """<a href="forumdisplay.php?fid=7&sid=SID123"><span>技术交流</span></a><a href="logging.php?action=logout">退出</a>""",
            "https://www.4d4y.com/forum/forumdisplay.php?fid=7&sid=SID123" to """
                <tbody id="normalthread_99">
                  <a href="https://www.4d4y.com/forum/viewthread.php?tid=99"><span>GB18030 topic</span></a>
                  <a href="space.php?uid=123">alice</a>
                  <td class="nums"><strong>4</strong></td>
                  <td class="lastpost"><cite><a href="redirect.php?tid=99">2026-07-03 11:30</a></cite><em><a href="space.php?uid=456">bob</a></em></td>
                </tbody>
            """,
            "https://www.4d4y.com/forum/viewthread.php?tid=99&page=1&sid=SID123" to """
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
            "https://www.4d4y.com/forum/viewthread.php?tid=99&page=2&sid=SID123" to """
                <h2><a href="viewthread.php?tid=99">4D4Y detail</a></h2>
                <a href="forumdisplay.php?fid=7">技术交流</a>
                <li id="pid789">
                  <a href="space.php?uid=789">carol</a>/ 10m</div>
                  <div class="replycon">Second page reply</div>
                </li>
            """,
            "https://www.4d4y.com/forum/viewthread.php?tid=99&sid=SID123" to """
                <input type="hidden" name="formhash" value="abcd1234">
                <div id="authorposton456"></div>
            """,
            "https://www.4d4y.com/forum/post.php?action=edit&fid=7&tid=99&pid=456&page=1&sid=SID123" to """
                <input type="hidden" name="formhash" value="ijkl9012">
            """,
            "https://www.4d4y.com/forum/post.php?action=newthread&fid=7&sid=SID123" to """
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
        assertEquals("2026-07-03 11:30", threads.single().lastPostTime)
        assertEquals("bob", threads.single().lastPosterName)
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
        assertEquals("https://www.4d4y.com/forum/post.php?action=reply&fid=7&tid=99&extra=&replysubmit=yes&inajax=1&sid=SID123", httpClient.lastPostUrl)
        assertTrue(httpClient.lastPostBody.contains("formhash=abcd1234"))
        assertTrue(httpClient.lastPostBody.contains("posttime="))
        assertTrue(httpClient.lastPostBody.contains("wysiwyg=1"))
        assertTrue(httpClient.lastPostBody.contains("noticeauthor=&noticetrimstr=&noticeauthormsg="))
        assertTrue(httpClient.lastPostBody.contains("subject=&message=Reply+body&replysubmit=yes&inajax=1"))
        service.createThread("7", "Title body", "Thread body")
        assertEquals("https://www.4d4y.com/forum/post.php?action=newthread&fid=7&extra=&topicsubmit=yes&inajax=1&sid=SID123", httpClient.lastPostUrl)
        assertTrue(httpClient.lastPostBody.contains("formhash=efgh5678"))
        assertTrue(httpClient.lastPostBody.contains("posttime="))
        assertTrue(httpClient.lastPostBody.contains("wysiwyg=1&typeid=12&subject=Title+body&message=Thread+body&topicsubmit=yes&inajax=1"))
        service.deleteThread("99", "7")
        assertEquals("https://www.4d4y.com/forum/post.php?action=edit&fid=7&tid=99&pid=456&page=1&editsubmit=yes&inajax=1&sid=SID123", httpClient.lastPostUrl)
        assertEquals("formhash=ijkl9012&delete=1&editsubmit=yes&inajax=1", httpClient.lastPostBody)
        assertEquals("auth=token; sid=SID123", httpClient.lastCookieHeader)
    }

    @Test fun fourD4YLoginSessionShowsProtectedCategoriesAndPersistsSid() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("4d4y", listOf(FeedflowCookie("cdb_auth", "token", "4d4y.com", expiresAtMillis = null)))
        val httpClient = SourceFixtureHttpClient(
            "https://www.4d4y.com/forum/index.php" to """
                <html><body>
                    <a href="forumdisplay.php?fid=2&sid=abc123xyz"><span>Discovery</span></a>
                    <a href="forumdisplay.php?fid=7&sid=abc123xyz">Buy &amp; Sell</a>
                    <a href="logging.php?action=logout&amp;formhash=abc123">退出</a>
                </body></html>
            """,
            "https://www.4d4y.com/forum/index.php?sid=abc123xyz" to """
                <html><body>
                    <a href="forumdisplay.php?fid=2&sid=abc123xyz"><span>Discovery</span></a>
                    <a href="forumdisplay.php?fid=7&sid=abc123xyz">Buy &amp; Sell</a>
                    <a href="logging.php?action=logout&amp;formhash=abc123">退出</a>
                </body></html>
            """,
            "https://www.4d4y.com/forum/forumdisplay.php?fid=2&sid=abc123xyz" to """
                <tbody id="normalthread_88">
                  <a href="viewthread.php?tid=88">Protected topic</a>
                  <a href="space.php?uid=321">member</a>
                </tbody>
            """,
        )
        val service = FourD4YService(store = store, httpClient = httpClient)

        assertTrue(service.restoreSession())
        val categories = service.fetchCategories()
        assertEquals(listOf("Discovery", "Buy & Sell"), categories.map { it.name })
        assertEquals("abc123xyz", store.getSetting("4d4y_sid"))
        val threads = service.fetchCategoryThreads("2", categories, 1)
        assertEquals("Protected topic", threads.single().title)
    }

    @Test fun fourD4YAuthenticatedCategoriesIncludeMemberOnlyCategoryAndFallbackThreads() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("4d4y", listOf(FeedflowCookie("auth", "token", "4d4y.com", expiresAtMillis = null)))
        val httpClient = SourceFixtureHttpClient(
            "https://www.4d4y.com/forum/index.php" to """
                <html><body>
                    <a class="forumtitle" href="forum.php?mod=forumdisplay&amp;fid=35&amp;sid=SID123"><span><strong>Category</strong></span></a>
                    <a href="forumdisplay.php?fid=2&amp;sid=SID123"><span>Discovery</span></a>
                    <a href="logging.php?action=logout&amp;formhash=abc123">退出</a>
                </body></html>
            """,
            "https://www.4d4y.com/forum/index.php?sid=SID123" to """
                <html><body>
                    <a class="forumtitle" href="forum.php?mod=forumdisplay&amp;fid=35&amp;sid=SID123"><span><strong>Category</strong></span></a>
                    <a href="forumdisplay.php?fid=2&amp;sid=SID123"><span>Discovery</span></a>
                    <a href="logging.php?action=logout&amp;formhash=abc123">退出</a>
                </body></html>
            """,
            "https://www.4d4y.com/forum/forumdisplay.php?fid=35&sid=SID123" to """
                <table>
                  <tr>
                    <th>
                      <a href="viewthread.php?tid=901&amp;extra=page%3D1"><span>Member-only topic</span></a>
                      <a href="space.php?uid=654">member</a>
                    </th>
                    <td class="nums"><strong>12</strong><em>34</em></td>
                    <td class="lastpost"><cite>2026-07-03 10:20</cite><em><a href="space.php?uid=777">lastPoster</a></em></td>
                  </tr>
                </table>
            """,
        )
        val service = FourD4YService(store = store, httpClient = httpClient)

        assertTrue(service.restoreSession())
        val categories = service.fetchCategories()
        assertEquals(listOf("35", "2"), categories.map { it.id })
        assertEquals(listOf("Category", "Discovery"), categories.map { it.name })
        assertEquals("SID123", store.getSetting("4d4y_sid"))
        val threads = service.fetchCategoryThreads("35", categories, 1)

        assertTrue(httpClient.lastGetUrl.startsWith("https://www.4d4y.com/forum/forumdisplay.php?fid=35&sid=SID123&_t="))
        assertEquals("Member-only topic", threads.single().title)
        assertEquals("654", threads.single().author.id)
        assertEquals("member", threads.single().author.username)
        assertEquals(12, threads.single().commentCount)
        assertEquals("2026-07-03 10:20", threads.single().lastPostTime)
        assertEquals("lastPoster", threads.single().lastPosterName)
    }

    @Test fun fourD4YRestoreSessionStoresLoggedInUsernameForDeleteGating() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("4d4y", listOf(FeedflowCookie("auth", "token", "4d4y.com", expiresAtMillis = null)))
        val service = FourD4YService(
            store = store,
            httpClient = SourceFixtureHttpClient(
                "https://www.4d4y.com/forum/index.php" to """
                    <a href="forumdisplay.php?fid=2">Discovery</a>
                    <a href="logging.php?action=logout">退出</a>
                    <div>欢迎您回来，<strong>Webrules</strong></div>
                """,
            ),
        )

        assertTrue(service.restoreSession())
        assertEquals("Webrules", store.getSetting("detected_4d4y_username"))
        assertTrue(service.canDeleteThread(sampleThread("99").copy(author = com.webrules.feedflow.core.model.User("u", "Webrules", ""))))
    }

    @Test fun fourD4YServiceParsesWapThreadDetailLikeIos() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("4d4y", listOf(FeedflowCookie("auth", "token", "4d4y.com", expiresAtMillis = null)))
        val service = FourD4YService(
            store = store,
            httpClient = SourceFixtureHttpClient(
                "https://www.4d4y.com/forum/viewthread.php?tid=3454758&page=1" to """
                    <html>
                    <head>
                    <title>国家刺激经济的终极杀招是啥？ - Discovery -  4D4Y  </title>
                    <script type="text/javascript">var STYLEID = '1', gid = parseInt('34'), fid = parseInt('2'), tid = parseInt('3454758')</script>
                    </head>
                    <body>
                    <div class="w navbar">
                        <a href="index.php">4D4Y</a> &raquo; <a href="forumdisplay.php?fid=2">Discovery</a> &raquo; 国家刺激经济的终极杀招是啥？
                    </div>
                    <div class="w bordertop detail">
                        <h2><a href="" class="classify">国家刺激经济的终极杀招是啥？</h2>
                        <div class="sub"><a href="space.php?uid=504383" style="margin-left: 20px; font-weight: 800">iamez</a>
                            <em id="authorposton74407801">发表于 2026-6-25 19:01</em>
                        </div>
                        <div class="detailcon" id="pid74407801">
                            我认为是银行把利率降到0，然后你钱存银行不仅没利息还得交管理费<br />
                            就问这样你慌不慌？
                        </div>
                    </div>
                    <div class="w detailbtn"><a href="post.php?action=reply&amp;fid=2&amp;tid=3454758">发表回复</a></div>
                    <div class="w replylist">
                    <ul>
                    <li id="pid74407806">
                        <div class="replytop"><span><a href="post.php?action=reply&amp;fid=2&amp;tid=3454758&amp;reppost=74407806&amp;extra=&amp;page=1">发表回复</a></span>2#</span><a href="space.php?uid=737271" style="margin-left: 20px; font-weight: 800">跳跳猪</a>/ 2026-6-25 19:03 </div>
                        <div class="replycon">零利率好像日本试过了&nbsp; &nbsp;&nbsp; &nbsp; <font size="1"><a href="https://www.4d4y.com/forum/viewthread.php?tid=2950630" target="_blank">论坛助手</a></font></div>
                    </li>
                    <li id="pid74407832">
                        <div class="replytop"><span><a href="post.php?action=reply&amp;fid=2&amp;tid=3454758&amp;reppost=74407832&amp;extra=&amp;page=1">发表回复</a></span>5#</span><a href="space.php?uid=504383" style="margin-left: 20px; font-weight: 800">iamez</a>/ 2026-6-25 19:07 </div>
                        <div class="replycon"><div class="quote"><blockquote>0利率不就是日本？<br />
                        <font size="2"><font color="#999999">linlance2000 发表于 2026-6-25 19:04</font></font></blockquote></div><br />
                        哈，你以为零利率就是存贷都是零？ 我存是零，贷款依然要付利息的，服不服？</div>
                    </li>
                    </ul>
                    </div>
                    <div class="w seclist"><table><tbody><tr><td><strong class="fade">1/3</strong></td></tr></tbody></table></div>
                    </body>
                    </html>
                """.trimIndent(),
            ),
        )

        val detail = service.fetchThreadDetail("3454758", 1)
        assertEquals("国家刺激经济的终极杀招是啥？", detail.thread.title)
        assertEquals("504383", detail.thread.author.id)
        assertEquals("iamez", detail.thread.author.username)
        assertEquals("2026-6-25 19:01", detail.thread.timeAgo)
        assertTrue(detail.thread.content.contains("我认为是银行把利率降到0"))
        assertEquals(2, detail.thread.commentCount)
        assertEquals(3, detail.totalPages)
        assertEquals(2, detail.comments.size)
        assertEquals("74407806", detail.comments[0].id)
        assertEquals("737271", detail.comments[0].author.id)
        assertEquals("跳跳猪", detail.comments[0].author.username)
        assertEquals("2026-6-25 19:03", detail.comments[0].timeAgo)
        assertTrue(detail.comments[0].content.contains("零利率好像日本试过了"))
        assertEquals("74407832", detail.comments[1].id)
        assertEquals("iamez", detail.comments[1].author.username)
        assertEquals("2026-6-25 19:07", detail.comments[1].timeAgo)
        assertTrue(detail.comments[1].content.contains("0利率不就是日本？"))
        assertTrue(detail.comments[1].content.contains("哈，你以为零利率就是存贷都是零？"))
    }

    @Test fun fourD4YServiceParsesDesktopThreadDetailLikeIos() = runBlocking {
        val store = InMemoryFeedflowStore()
        store.saveCookies("4d4y", listOf(FeedflowCookie("auth", "token", "4d4y.com", expiresAtMillis = null)))
        val service = FourD4YService(
            store = store,
            httpClient = SourceFixtureHttpClient(
                "https://www.4d4y.com/forum/viewthread.php?tid=901&page=1" to """
                    <html>
                    <head>
                      <title>Desktop protected topic - Category - 4D4Y</title>
                      <script>var fid = parseInt('35'), tid = parseInt('901')</script>
                    </head>
                    <body>
                      <div class="pages"><a>1</a><a>2</a></div>
                      <table>
                        <tr>
                          <td class="postauthor">
                            <div class="postinfo"><a href="space.php?uid=111">alice</a></div>
                          </td>
                          <td class="t_msgfont" id="postmessage_501">
                            Original desktop<br />
                            post <img src="https://cdn.example.com/original.jpg" />
                          </td>
                        </tr>
                        <tr>
                          <td class="postauthor">
                            <div class="postinfo"><a href="space.php?uid=222">bob</a></div>
                          </td>
                          <td id="postmessage_502" class="t_msgfont">
                            Reply desktop<br />content
                          </td>
                        </tr>
                      </table>
                    </body>
                    </html>
                """.trimIndent(),
            ),
        )

        val detail = service.fetchThreadDetail("901", 1)

        assertEquals("Desktop protected topic", detail.thread.title)
        assertEquals("35", detail.thread.community.id)
        assertEquals("111", detail.thread.author.id)
        assertEquals("alice", detail.thread.author.username)
        assertTrue(detail.thread.content.contains("Original desktop"))
        assertTrue(detail.thread.content.contains("[IMAGE:https://cdn.example.com/original.jpg]"))
        assertEquals(1, detail.thread.commentCount)
        assertEquals(2, detail.totalPages)
        assertEquals("502", detail.comments.single().id)
        assertEquals("222", detail.comments.single().author.id)
        assertEquals("bob", detail.comments.single().author.username)
        assertTrue(detail.comments.single().content.contains("Reply desktop"))
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
    var lastGetUrl: String = ""
    var lastPostUrl: String = ""
    var lastPostBody: String = ""
    var lastCookieHeader: String = ""
    var lastContentType: String = ""

    override suspend fun get(url: String, cookies: List<FeedflowCookie>): String {
        lastGetUrl = url
        lastCookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        return fixtures.firstOrNull { (fixtureUrl, _) -> fixtureUrl == url }?.second
            ?: fixtures.firstOrNull { (fixtureUrl, _) -> fixtureUrl == url.withoutTimestampCacheBuster() }?.second
            ?: error("Missing fixture for $url")
    }

    override suspend fun post(url: String, body: String, cookies: List<FeedflowCookie>, contentType: String): String {
        lastPostUrl = url
        lastPostBody = body
        lastCookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        lastContentType = contentType
        return "ok"
    }
}

private fun String.withoutTimestampCacheBuster(): String =
    replace(Regex("""[&?]_t=\d+""")) { match ->
        if (match.value.startsWith("?")) "?" else ""
    }.trimEnd('?', '&')
