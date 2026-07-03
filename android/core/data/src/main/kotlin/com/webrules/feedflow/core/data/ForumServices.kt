package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowHttpClient
import com.webrules.feedflow.core.network.UnimplementedFeedflowHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

sealed class FeedflowError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object AuthRequired : FeedflowError("Authentication required")
    data class UnsupportedFeature(val operation: String) : FeedflowError("$operation is not supported")
    data class Network(val url: String, val statusCode: Int? = null, val bodyPreview: String? = null) :
        FeedflowError("Network failure for $url")
    data class Parsing(val serviceId: String, val stage: String, val bodyPreview: String? = null) :
        FeedflowError("Failed to parse $stage for $serviceId")
    data class PermissionDenied(val operation: String) : FeedflowError("Permission denied for $operation")
    data class Upstream(val serviceId: String, val statusCode: Int, val messageText: String? = null) :
        FeedflowError("Upstream $serviceId returned $statusCode")
}

data class ThreadDetailResult(
    val thread: FeedThread,
    val comments: List<Comment>,
    val totalPages: Int?,
)

data class SearchResult(
    val threads: List<FeedThread>,
    val hasMore: Boolean,
)

interface ForumService {
    val name: String
    val id: String
    val logo: String
    val requiresLogin: Boolean get() = false
    val supportsCommenting: Boolean get() = false
    val supportsThreadCreation: Boolean get() = false
    val currentUsername: String? get() = null
    suspend fun restoreSession(): Boolean = true
    suspend fun fetchCategories(): List<Community>
    suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread>
    suspend fun refreshCategoryThreads(categoryId: String, communities: List<Community>): List<FeedThread> =
        fetchCategoryThreads(categoryId, communities, 1)
    suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult
    suspend fun postComment(topicId: String, categoryId: String, content: String): Unit =
        throw FeedflowError.UnsupportedFeature("postComment")
    suspend fun createThread(categoryId: String, title: String, content: String): Unit =
        throw FeedflowError.UnsupportedFeature("createThread")
    fun canDeleteThread(thread: FeedThread): Boolean = false
    suspend fun deleteThread(threadId: String, categoryId: String): Unit =
        throw FeedflowError.UnsupportedFeature("deleteThread")
    suspend fun markThreadRead(thread: FeedThread): Unit = Unit
    suspend fun markThreadNotInterested(thread: FeedThread): Unit =
        throw FeedflowError.UnsupportedFeature("markThreadNotInterested")
    suspend fun searchThreads(query: String, page: Int): SearchResult = SearchResult(emptyList(), false)
    fun getWebUrl(thread: FeedThread): String
    fun canCreateThread(community: Community): Boolean = supportsThreadCreation
}

fun ForumSite.makeService(
    store: FeedflowStore? = null,
    httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
): ForumService = when (this) {
    ForumSite.Rss -> RssService(
        httpClient = httpClient,
        initialFeeds = store?.getRssFeeds()?.takeIf { it.isNotEmpty() }?.map { feed ->
            RssFeedInfo(id = feed.url, name = feed.title, url = feed.url, description = "")
        } ?: RssService.defaultFeeds,
    )
    ForumSite.HackerNews -> HackerNewsService(httpClient)
    ForumSite.FourD4Y -> FourD4YService(store, httpClient)
    ForumSite.V2ex -> V2exService(store, httpClient)
    ForumSite.LinuxDo -> DiscourseService(store, httpClient)
    ForumSite.Zhihu -> ZhihuService(httpClient, store)
}

abstract class StaticForumService : ForumService {
    protected val defaultCommunity = Community("general", "General", "", "general", 0, 0)
    protected fun sampleThread(id: String = "1") = FeedThread(
        id = id,
        title = "$name sample topic",
        content = "Content unavailable.",
        author = User("u1", "Feedflow", "person.circle"),
        community = defaultCommunity,
        timeAgo = "now",
        likeCount = 0,
        commentCount = 0,
    )

    override suspend fun fetchCategories(): List<Community> = listOf(defaultCommunity)
    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> =
        listOf(sampleThread("$id-$categoryId-$page"))
    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult =
        ThreadDetailResult(sampleThread(threadId), emptyList(), null)
}

data class RssFeedInfo(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
)

class RssService(
    private val httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
    initialFeeds: List<RssFeedInfo> = defaultFeeds,
) : StaticForumService() {
    override val name = "RSS Feeds"
    override val id = "rss"
    override val logo = "feed"
    private val feeds = initialFeeds.toMutableList()
    private val threadCache = linkedMapOf<String, FeedThread>()

    override suspend fun fetchCategories(): List<Community> =
        feeds.map { Community(it.id, it.name, it.description, "RSS", 0, 0) }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val feed = feeds.firstOrNull { it.id == categoryId } ?: throw FeedflowError.Network(url = categoryId, statusCode = 404, bodyPreview = "Feed not found")
        val xml = httpClient.get(feed.url)
        val community = communities.firstOrNull { it.id == categoryId }
            ?: Community(categoryId, feed.name, feed.description, "RSS", 0, 0)
        return RssParser(xml.toByteArray()).parse().map { item ->
            FeedThread(
                id = item.link,
                title = item.title,
                content = RssContentCleaner.clean(item.content),
                author = User(item.author, item.author, "rss", "RSS"),
                community = community,
                timeAgo = item.timeAgo,
                likeCount = 0,
                commentCount = 0,
            ).also { threadCache[it.id] = it }
        }
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult =
        ThreadDetailResult(
            thread = threadCache[threadId] ?: FeedThread(
                id = threadId,
                title = "Content Unavailable",
                content = "Please refresh the feed list.",
                author = User("system", "System", "exclamationmark.triangle"),
                community = Community("error", "Error", "", "", 0, 0),
                timeAgo = "",
                likeCount = 0,
                commentCount = 0,
            ),
            comments = emptyList(),
            totalPages = 1,
        )

    override fun getWebUrl(thread: FeedThread): String = thread.id

    fun addFeed(name: String, url: String, feedId: String = url): RssFeedInfo =
        RssFeedInfo(feedId, name, url, "").also { feeds.add(it) }

    fun removeFeed(id: String) {
        feeds.removeAll { it.id == id }
    }

    fun fetchDailyUpdatesFromThreads(threads: List<FeedThread>): List<FeedThread> =
        threads.filter { it.timeAgo == "just now" || it.timeAgo.endsWith("m") || it.timeAgo.endsWith("h") }

    companion object {
        val defaultFeeds = listOf(
            RssFeedInfo("hacker_podcast", "Hacker Podcast", "https://hacker-podcast.agi.li/rss.xml", "Hacker News Recap"),
            RssFeedInfo("ruanyifeng", "Ruanyifeng Blog", "https://www.ruanyifeng.com/blog/atom.xml", "Tech and Humanities"),
            RssFeedInfo("oreilly", "O'Reilly Radar", "https://www.oreilly.com/radar/feed/", "Tech Trends"),
        )
    }
}

class HackerNewsService(
    private val httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
) : StaticForumService() {
    override val name = "Hacker News"
    override val id = "hackernews"
    override val logo = "flame.fill"
    private val baseUrl = "https://hacker-news.firebaseio.com/v0"

    override suspend fun fetchCategories(): List<Community> = hnCategories

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        if (page > 1) return emptyList()
        val ids = HackerNewsJson.parseIds(httpClient.get("$baseUrl/$categoryId.json")).take(20)
        val community = communities.firstOrNull { it.id == categoryId }
            ?: hnCategories.firstOrNull { it.id == categoryId }
            ?: Community(categoryId, categoryId, "", "General", 0, 0)
        return ids.mapNotNull { id ->
            HackerNewsJson.parseItem(httpClient.get("$baseUrl/item/$id.json"))?.toThread(community)
        }
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        if (page > 1) {
            return ThreadDetailResult(sampleThread(threadId).copy(title = "", content = ""), emptyList(), null)
        }
        val item = HackerNewsJson.parseItem(httpClient.get("$baseUrl/item/$threadId.json"))
            ?: throw FeedflowError.Parsing(id, "item", threadId)
        val thread = item.toThread(Community("hacker_news", "Hacker News", "", "General", 0, 0), detail = true)
        val comments = item.kids.orEmpty().take(20).mapNotNull { kidId ->
            HackerNewsJson.parseItem(httpClient.get("$baseUrl/item/$kidId.json"))?.toComment()
        }
        return ThreadDetailResult(thread, comments, null)
    }

    override suspend fun searchThreads(query: String, page: Int): SearchResult {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val zeroBasedPage = (page - 1).coerceAtLeast(0)
        val json = httpClient.get("https://hn.algolia.com/api/v1/search?tags=story&query=$encoded&page=$zeroBasedPage")
        return HackerNewsSearchJson.parse(json, Community("search", "Search", query, "General", 0, 0))
    }

    override fun getWebUrl(thread: FeedThread): String = "https://news.ycombinator.com/item?id=${thread.id}"

    private fun HnItem.toThread(community: Community, detail: Boolean = false): FeedThread {
        val body = if (detail) {
            buildString {
                append(text.orEmpty())
                if (!url.isNullOrBlank()) append("\n\nLink: ").append(url)
            }.let(HackerNewsContentCleaner::clean)
        } else {
            text ?: url.orEmpty()
        }
        return FeedThread(
            id = id.toString(),
            title = title ?: if (detail) "No title" else "No Title",
            content = body,
            author = User(by ?: "unknown", by ?: "unknown", "person.circle.fill"),
            community = community,
            timeAgo = HackerNewsContentCleaner.timeAgo(time),
            likeCount = score ?: 0,
            commentCount = descendants ?: 0,
        )
    }

    private fun HnItem.toComment(): Comment? {
        val author = by ?: return null
        val body = text?.takeIf { it.isNotBlank() } ?: return null
        if (deleted || dead) return null
        return Comment(
            id = id.toString(),
            author = User(author, author, "person.circle"),
            content = HackerNewsContentCleaner.clean(body),
            timeAgo = HackerNewsContentCleaner.timeAgo(time),
            likeCount = 0,
        )
    }

    companion object {
        val hnCategories = listOf(
            Community("topstories", "Top", "Top stories", "General", 0, 0),
            Community("newstories", "New", "New stories", "General", 0, 0),
            Community("beststories", "Best", "Best stories", "General", 0, 0),
            Community("showstories", "Show", "Show HN", "General", 0, 0),
            Community("askstories", "Ask", "Ask HN", "General", 0, 0),
            Community("jobstories", "Jobs", "Jobs", "General", 0, 0),
        )
    }
}

class V2exService(
    private val store: FeedflowStore? = null,
    private val httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
) : StaticForumService() {
    override val name = "V2EX"
    override val id = "v2ex"
    override val logo = "point.3.connected.trianglepath.dotted"
    override val supportsCommenting = true

    override suspend fun fetchCategories(): List<Community> =
        V2exParser.tabs.map { tab -> Community(tab, tab.replaceFirstChar(Char::uppercase), "V2EX $tab topics", "V2EX", 0, 0) }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        if (page > 1) return emptyList()
        val html = httpClient.get("https://www.v2ex.com/?tab=$categoryId")
        val community = communities.firstOrNull { it.id == categoryId } ?: Community(categoryId, categoryId, "", "V2EX", 0, 0)
        return V2exParser.parseThreadList(html).map { topic ->
            FeedThread(
                id = topic.id,
                title = topic.title,
                content = "",
                author = User(topic.author, topic.author, "person.circle"),
                community = community,
                timeAgo = "",
                likeCount = 0,
                commentCount = topic.replies,
            )
        }
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        if (page > 1) {
            return ThreadDetailResult(
                thread = FeedThread(
                    id = threadId,
                    title = "",
                    content = "",
                    author = User("", "", ""),
                    community = Community("v2ex", "V2EX", "", "V2EX", 0, 0),
                    timeAgo = "",
                    likeCount = 0,
                    commentCount = 0,
                ),
                comments = emptyList(),
                totalPages = page,
            )
        }
        val html = httpClient.get("https://www.v2ex.com/t/$threadId")
        val title = V2exParser.parseTopicTitle(html).ifBlank { "V2EX" }
        val content = V2exParser.parseTopicContent(html)
        val author = V2exParser.parseTopicAuthor(html).ifBlank { "V2EX" }
        val replies = V2exParser.parseReplies(html).map { reply ->
            Comment(
                id = reply.id,
                author = User(reply.author, reply.author, "person.circle"),
                content = reply.content,
                timeAgo = reply.timeAgo,
                likeCount = 0,
            )
        }
        val thread = FeedThread(
            id = threadId,
            title = title,
            content = content,
            author = User(author, author, "person.circle"),
            community = Community("v2ex", "V2EX", "", "V2EX", 0, 0),
            timeAgo = "",
            likeCount = 0,
            commentCount = replies.size,
        )
        return ThreadDetailResult(thread, replies, 1)
    }

    override suspend fun postComment(topicId: String, categoryId: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val topicHtml = httpClient.get("https://www.v2ex.com/t/$topicId", cookies)
        val once = extractOnceToken(topicHtml) ?: throw FeedflowError.Parsing(id, "replyOnce", topicHtml.take(200))
        httpClient.post(
            url = "https://www.v2ex.com/t/$topicId",
            body = "content=${content.formEncode()}&once=${once.formEncode()}",
            cookies = cookies,
        )
    }

    override fun getWebUrl(thread: FeedThread): String = "https://www.v2ex.com/t/${thread.id}"

    private fun extractOnceToken(html: String): String? {
        val patterns = listOf(
            """name=["']once["'][^>]*value=["'](\d+)["']""",
            """value=["'](\d+)["'][^>]*name=["']once["']""",
            """var\s+once\s*=\s*["']?(\d+)["']?""",
            """["']once["']\s*:\s*["']?(\d+)["']?""",
            """once=(\d+)""",
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        }
    }
}

private fun String.formEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.gbkFormEncode(): String = FourD4YParser.encodeFormValue(this)

internal val FOURD4Y_HEADERS = mapOf(
    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
    "Cache-Control" to "no-cache",
    "Pragma" to "no-cache",
)

class DiscourseService(
    private val store: FeedflowStore? = null,
    private val httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
) : StaticForumService() {
    override val name = "Linux.do"
    override val id = "linux_do"
    override val logo = "terminal.fill"
    override val requiresLogin = true
    override val supportsCommenting = true
    override val supportsThreadCreation = true
    override fun canCreateThread(community: Community): Boolean = true

    override suspend fun fetchCategories(): List<Community> =
        DiscourseParser.parseCategories(httpClient.get("https://linux.do/categories.json", store?.getCookies(id).orEmpty()))

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val path = if (categoryId == "latest") "latest.json" else "c/$categoryId.json"
        val json = httpClient.get("https://linux.do/$path?page=$page", store?.getCookies(id).orEmpty())
        val community = communities.firstOrNull { it.id == categoryId } ?: Community(categoryId, categoryId, "", "linux_do", 0, 0)
        return DiscourseParser.parseTopicList(json, community)
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val json = httpClient.get("https://linux.do/t/$threadId.json?page=$page", store?.getCookies(id).orEmpty())
        val (thread, comments, totalPages) = DiscourseParser.parseThreadDetail(json, threadId)
        return ThreadDetailResult(thread, comments, totalPages)
    }

    override suspend fun createThread(categoryId: String, title: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        httpClient.post(
            url = "https://linux.do/posts.json",
            body = "title=${title.formEncode()}&raw=${content.formEncode()}&category=${categoryId.formEncode()}",
            cookies = cookies,
        )
    }

    override suspend fun postComment(topicId: String, categoryId: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        httpClient.post(
            url = "https://linux.do/posts.json",
            body = "topic_id=${topicId.formEncode()}&raw=${content.formEncode()}",
            cookies = cookies,
        )
    }

    override fun getWebUrl(thread: FeedThread): String = "https://linux.do/t/${thread.id}"
}

class FourD4YService(
    private val store: FeedflowStore? = null,
    private val httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
) : StaticForumService() {
    override val name = "4D4Y"
    override val id = "4d4y"
    override val logo = "4.circle.fill"
    override val requiresLogin = true
    override val supportsCommenting = true
    override val supportsThreadCreation = true

    override suspend fun restoreSession(): Boolean {
        val cookies = store?.getCookies(id).orEmpty()
        if (!cookies.any { it.domain.contains("4d4y.com") && SiteLoginConfig.forSite(ForumSite.FourD4Y)!!.hasAuthenticatedSession(cookies) }) {
            return false
        }
        val html = runCatching { fetch("https://www.4d4y.com/forum/index.php", cookies) }.getOrNull() ?: return true
        val username = parseLoggedInUsername(html)
        username?.let { store?.saveSetting("detected_4d4y_username", it) }
        return username != null || validateSessionHtml(html)
    }

    override fun canCreateThread(community: Community): Boolean = true
    override fun getWebUrl(thread: FeedThread): String = "https://www.4d4y.com/forum/viewthread.php?tid=${thread.id}"

    override suspend fun fetchCategories(): List<Community> {
        val html = fetch("https://www.4d4y.com/forum/index.php", authCookies())
        return FourD4YParser.parseCategories(html)
    }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val html = fetch("https://www.4d4y.com/forum/forumdisplay.php?fid=$categoryId&page=$page", authCookies())
        val community = communities.firstOrNull { it.id == categoryId } ?: Community(categoryId, categoryId, "", "4D4Y", 0, 0)
        return FourD4YParser.parseThreadRows(html, community)
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val cookies = authCookies()
        val html = fetch(withSid("https://www.4d4y.com/forum/viewthread.php?tid=$threadId&page=$page", cookies), cookies)
        val parsed = parseThreadDetailHtml(html, threadId, page)
            ?: throw FeedflowError.Parsing(id, "threadDetail", html.take(200))
        return ThreadDetailResult(parsed.first, parsed.second, parsed.third)
    }

    private fun authCookies(): List<com.webrules.feedflow.core.network.FeedflowCookie> =
        store?.getCookies(id).orEmpty().filter { it.domain.contains("4d4y.com") }

    private suspend fun fetch(url: String, cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>): String =
        httpClient.get(url, cookies, FOURD4Y_HEADERS, "GB18030")

    private fun withSid(url: String, cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>): String {
        val sid = cookies.firstOrNull { it.name.equals("sid", ignoreCase = true) }?.value
            ?: store?.getSetting("4d4y_sid")
            ?: return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}sid=${sid.formEncode()}"
    }

    private fun validateSessionHtml(html: String): Boolean {
        val forumMatches = Regex("""href="forumdisplay\.php\?fid=(\d+)[^"]*"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            .findAll(html).toList()
        val lower = html.lowercase()
        val hasLogout = lower.contains("action=logout") || lower.contains("action%3dlogout") || html.contains("退出")
        val hasLogin = (lower.contains("action=login") || lower.contains("action%3dlogin")) && !hasLogout
        val hasDiscovery = forumMatches.any { it.groupValues[2].stripTags().trim() == "Discovery" }
        return forumMatches.isNotEmpty() && !hasLogin && (hasLogout || hasDiscovery)
    }

    override suspend fun postComment(topicId: String, categoryId: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val formHtml = fetch(withSid("https://www.4d4y.com/forum/viewthread.php?tid=$topicId", cookies), cookies)
        val formHash = extractFormHash(formHtml) ?: throw FeedflowError.Parsing(id, "replyFormhash", formHtml.take(200))
        httpClient.post(
            url = withSid("https://www.4d4y.com/forum/post.php?action=reply&fid=$categoryId&tid=$topicId&extra=&replysubmit=yes&inajax=1", cookies),
            body = listOf(
                "formhash=${formHash.gbkFormEncode()}",
                "posttime=${Instant.now().epochSecond}",
                "wysiwyg=1",
                "noticeauthor=",
                "noticetrimstr=",
                "noticeauthormsg=",
                "subject=",
                "message=${content.gbkFormEncode()}",
                "replysubmit=yes",
                "inajax=1",
            ).joinToString("&"),
            cookies = cookies,
        )
    }

    override suspend fun createThread(categoryId: String, title: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val formHtml = fetch(withSid("https://www.4d4y.com/forum/post.php?action=newthread&fid=$categoryId", cookies), cookies)
        val formHash = extractFormHash(formHtml) ?: throw FeedflowError.Parsing(id, "newThreadFormhash", formHtml.take(200))
        val typeField = extractFirstTypeId(formHtml)?.let { "&typeid=${it.gbkFormEncode()}" }.orEmpty()
        httpClient.post(
            url = withSid("https://www.4d4y.com/forum/post.php?action=newthread&fid=$categoryId&extra=&topicsubmit=yes&inajax=1", cookies),
            body = "formhash=${formHash.gbkFormEncode()}&posttime=${Instant.now().epochSecond}&wysiwyg=1$typeField&subject=${title.gbkFormEncode()}&message=${content.gbkFormEncode()}&topicsubmit=yes&inajax=1",
            cookies = cookies,
        )
    }

    override suspend fun deleteThread(threadId: String, categoryId: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val threadHtml = fetch(withSid("https://www.4d4y.com/forum/viewthread.php?tid=$threadId", cookies), cookies)
        val pid = parseFirstPostId(threadHtml) ?: throw FeedflowError.Parsing(id, "deletePid", threadHtml.take(200))
        val editHtml = fetch(withSid("https://www.4d4y.com/forum/post.php?action=edit&fid=$categoryId&tid=$threadId&pid=$pid&page=1", cookies), cookies)
        val formHash = extractFormHash(editHtml) ?: throw FeedflowError.Parsing(id, "deleteFormhash", editHtml.take(200))
        httpClient.post(
            url = withSid("https://www.4d4y.com/forum/post.php?action=edit&fid=$categoryId&tid=$threadId&pid=$pid&page=1&editsubmit=yes&inajax=1", cookies),
            body = "formhash=${formHash.gbkFormEncode()}&delete=1&editsubmit=yes&inajax=1",
            cookies = cookies,
        )
    }

    fun calculateTimeAgo(instant: Instant): String {
        val seconds = Duration.between(instant, Instant.now()).seconds
        return when {
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3_600}h"
            else -> "${seconds / 86_400}d"
        }
    }

    fun calculateTimeAgo(iso8601: String): String =
        try {
            calculateTimeAgo(Instant.parse(iso8601))
        } catch (_: DateTimeParseException) {
            "now"
        }

    fun avatarUrlForUid(uid: String): String {
        val numeric = uid.trim().toLongOrNull() ?: return "person.circle"
        val padded = numeric.toString().padStart(9, '0')
        val path = "${padded.substring(0, 3)}/${padded.substring(3, 5)}/${padded.substring(5, 7)}/${padded.substring(7, 9)}"
        return "https://img02.4d4y.com/forum/uc_server/data/avatar/${path}_avatar_middle.jpg"
    }

    fun extractFirstTypeId(html: String): String? =
        Regex("""<option\s+value=["'](\d+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it != "0" }

    fun extractFormHash(html: String): String? {
        val patterns = listOf(
            """formhash=([a-zA-Z0-9]+)""",
            """name=['"]formhash['"][^>]*value=['"]([a-zA-Z0-9]+)['"]""",
            """value=['"]([a-zA-Z0-9]+)['"][^>]*name=['"]formhash['"]""",
            """(?:var\s+)?formhash\s*[:=]\s*['"]([a-zA-Z0-9]+)['"]""",
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        }
    }

    fun parseFirstPostId(html: String): String? =
        Regex("""authorposton(\d+)""").find(html)?.groupValues?.get(1)

    override fun canDeleteThread(thread: FeedThread): Boolean =
        store?.getSetting("detected_4d4y_username")?.equals(thread.author.username, ignoreCase = true) == true

    fun parseThreadDetailHtml(html: String, threadId: String, page: Int): Triple<FeedThread, List<Comment>, Int?>? {
        val title = Regex("""<h2><a[^>]*>(.*?)</h2>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.stripTags()?.decodeHtmlEntities() ?: return null
        val authorMatch = Regex("""space\.php\?uid=(\d+)[^>]*>([^<]+)</a>.*?发表于\s*([^<]+)</em>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val communityMatch = Regex("""forumdisplay\.php\?fid=(\d+)[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).find(html)
        val rawContent = extractDetailContent(html)
        val comments = Regex("""<li[^>]*id=["']pid(\d+)["'][^>]*>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .mapNotNull {
                val block = it.groupValues[2]
                val author = Regex("""space\.php\?uid=(\d+)[^>]*>([^<]+)</a>/\s*([^<]+?)(?:</div>|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(block) ?: return@mapNotNull null
                Comment(
                    id = it.groupValues[1],
                    author = User(author.groupValues[1], author.groupValues[2].decodeHtmlEntities(), avatarUrlForUid(author.groupValues[1])),
                    content = cleanContent(extractReplyContent(block)),
                    timeAgo = author.groupValues[3].trim(),
                    likeCount = 0,
                )
            }.toList()
        val totalPages = Regex("""<strong[^>]*>\s*\d+/(\d+)\s*</strong>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.toIntOrNull()
        val community = Community(
            id = communityMatch?.groupValues?.get(1).orEmpty(),
            name = communityMatch?.groupValues?.get(2)?.decodeHtmlEntities().orEmpty(),
            description = "",
            category = "",
            activeToday = 0,
            onlineNow = 0,
        )
        val thread = FeedThread(
            id = threadId,
            title = title,
            content = if (page == 1) cleanContent(rawContent) else "",
            author = if (page == 1) {
                User(authorMatch?.groupValues?.get(1).orEmpty(), authorMatch?.groupValues?.get(2).orEmpty(), avatarUrlForUid(authorMatch?.groupValues?.get(1).orEmpty()))
            } else {
                User("0", "", "")
            },
            community = community,
            timeAgo = if (page == 1) authorMatch?.groupValues?.get(3)?.trim().orEmpty() else "",
            likeCount = 0,
            commentCount = if (page == 1) comments.size else 0,
        )
        return Triple(thread, comments, totalPages)
    }

    companion object {
        fun parseLoggedInUsername(html: String): String? =
            Regex("""欢迎您回来，\s*<strong>([^<]+)</strong>""").find(html)?.groupValues?.get(1)
                ?: Regex("""space\.php\?uid=\d+[^>]*font-weight:\s*800[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

        fun cleanContent(html: String): String {
            var processed = html
                .replace(Regex("""<div\s+class=["']t_attach["'][^>]*>.*?</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("""<ignore_js_op>.*?</ignore_js_op>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            processed = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).replace(processed) { match ->
                val src = match.groupValues[1]
                if (src.contains("smilies") || src.contains("images/default") || src.contains("images/common") || src.contains("common/back.gif")) {
                    ""
                } else {
                    "\n[IMAGE:$src]\n"
                }
            }
            processed = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(processed) { match ->
                val href = match.groupValues[1].decodeHtmlEntities()
                val label = match.groupValues[2].stripTags().decodeHtmlEntities().trim()
                if (label.isBlank() || label == href) href else "$label ($href)"
            }
            return processed
                .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .stripTags()
                .decodeHtmlEntities()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }

        private fun extractDetailContent(html: String): String {
            val startMatch = Regex("""<div[^>]*class=["'][^"']*detailcon[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE).find(html)
                ?: return ""
            val contentStart = startMatch.range.last + 1
            val end = Regex("""<div[^>]*class=["'][^"']*detailbtn|<li\s+id=["']?pid\d+|<div[^>]*class=["'][^"']*replylist|</body>""", RegexOption.IGNORE_CASE)
                .find(html, contentStart)
                ?.range
                ?.first
                ?: html.length
            return html.substring(contentStart, end)
        }

        private fun extractReplyContent(block: String): String {
            val startMatch = Regex("""<div[^>]*class=["'][^"']*replycon[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE).find(block)
                ?: return ""
            val contentStart = startMatch.range.last + 1
            val end = block.lastIndexOf("</div>").takeIf { it > contentStart } ?: block.length
            return block.substring(contentStart, end)
        }
    }
}

class ZhihuService(
    private val httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
    private val store: FeedflowStore? = null,
) : StaticForumService() {
    override val name = "知乎"
    override val id = "zhihu"
    override val logo = "questionmark.bubble.fill"
    override val requiresLogin = true

    private val recommendCommunity = Community("recommend", "知乎", "", "zhihu", 0, 0)
    private val hotCommunity = Community("hot", "知乎热榜", "", "zhihu", 0, 0)
    private val questionDataCache = mutableMapOf<String, Pair<String, String>>()
    private val downvotedSettingKey = "zhihu_downvoted_ids"

    private fun cookies() = store?.getCookies(id).orEmpty()

    private fun apiHeaders() = mapOf(
        "Referer" to "https://www.zhihu.com/",
        "x-requested-with" to "fetch",
        "Accept" to "application/json, text/plain, */*",
    )

    override suspend fun fetchCategories(): List<Community> = ZhihuParser.categories

    fun normalizedAvatarUrl(url: String?, template: String? = null): String =
        ZhihuParser.normalizedAvatarUrl(url, template)

    override val currentUsername: String?
        get() = null

    override suspend fun restoreSession(): Boolean {
        val cookies = cookies()
        if (cookies.isEmpty()) return false
        val me = runCatching {
            httpClient.get("https://www.zhihu.com/api/v4/me", cookies, apiHeaders())
        }.getOrNull() ?: return false
        return ZhihuJson.parse(me)?.obj()?.str("name") != null
    }

    override fun getWebUrl(thread: FeedThread): String {
        val parts = thread.id.split("_", limit = 2)
        if (parts.size != 2) return "https://www.zhihu.com"
        val (type, rawId) = parts
        val identifier = rawId
        return when (type) {
            "answer" -> "https://www.zhihu.com/question/0/answer/$identifier"
            "article" -> "https://zhuanlan.zhihu.com/p/$identifier"
            "question" -> "https://www.zhihu.com/question/$identifier"
            else -> "https://www.zhihu.com"
        }
    }

    override suspend fun markThreadRead(thread: FeedThread) {
        if (thread.community.id != "recommend") return
        val targetId = zhihuTargetId(thread.id) ?: return
        store?.addFilteredPost(targetId, id)
    }

    override suspend fun markThreadNotInterested(thread: FeedThread) {
        val targetId = zhihuTargetId(thread.id) ?: return
        val targetType = thread.id.substringBefore("_", missingDelimiterValue = "")
        if (targetType.isBlank()) return
        val existing = store?.getSetting(downvotedSettingKey)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toMutableSet()
            ?: linkedSetOf()
        existing += targetId
        store?.saveSetting(downvotedSettingKey, existing.joinToString(","))
        store?.addFilteredPost(targetId, id)
        store?.addFilteredPost(thread.id, id)
        runCatching {
            httpClient.post(
                url = "https://www.zhihu.com/api/v3/feed/topstory/uninterest",
                body = """{"target_type":"$targetType","target_id":"$targetId","reason":"not_interested"}""",
                cookies = cookies(),
                contentType = "application/json; charset=UTF-8",
            )
        }
    }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> =
        when (categoryId) {
            "hot" -> fetchHotList(page)
            else -> fetchRecommendFeed(page)
        }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val parts = threadId.split("_", limit = 2)
        val type = parts.getOrNull(0).orEmpty()
        val id = parts.getOrNull(1).orEmpty()
        if (parts.size != 2 || id.isBlank() || id.any { !it.isDigit() }) {
            throw FeedflowError.Parsing(id, "zhihuThreadId", threadId)
        }
        return when (type) {
            "answer" -> fetchAnswerDetail(id, page)
            "article" -> fetchArticleDetail(id, page)
            "question" -> fetchQuestionDetail(id, page)
            else -> throw FeedflowError.Parsing(id, "zhihuThreadType", threadId)
        }
    }

    private suspend fun fetchRecommendFeed(page: Int): List<FeedThread> {
        val url = if (page <= 1) {
            "https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=10"
        } else {
            "https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=10&after_id=$page"
        }
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull() ?: return emptyList()
        val data = ZhihuJson.parse(body)?.obj()?.arr("data") ?: return emptyList()
        val seen = linkedSetOf<String>()
        val threads = mutableListOf<FeedThread>()
        for (element in data) {
            val item = element.obj() ?: continue
            if (item.str("type") == "feed_advert") continue
            val target = item.obj("target") ?: continue
            val targetType = target.str("type") ?: continue
            if (targetType !in listOf("answer", "article", "question", "pin")) continue
            if (ZhihuParser.filterReason(target) != null) continue
            val targetId = target.longId("id") ?: continue
            val threadId = "${targetType}_$targetId"
            if (!seen.add(threadId)) continue
            if (isFilteredZhihuTarget(threadId, targetId.toString())) continue
            threads += FeedThread(
                id = threadId,
                title = ZhihuParser.effectiveTitle(target),
                content = target.str("excerpt").orEmpty().decodeHtmlEntities(),
                author = ZhihuParser.author(target.obj("author")),
                community = recommendCommunity,
                timeAgo = ZhihuParser.formatTimestamp(target.int("created_time")),
                likeCount = target.int("voteup_count") ?: 0,
                commentCount = target.int("comment_count") ?: 0,
                tags = listOf(ZhihuParser.typeDescription(targetType)),
            )
        }
        return threads
    }

    private fun isFilteredZhihuTarget(threadId: String, targetId: String): Boolean =
        store?.isPostFiltered(threadId, id) == true ||
            store?.isPostFiltered(targetId, id) == true ||
            store?.getSetting(downvotedSettingKey)
                ?.split(",")
                ?.any { it == targetId } == true

    private fun zhihuTargetId(threadId: String): String? =
        threadId.split("_", limit = 2).getOrNull(1)?.takeIf { it.isNotBlank() }

    private suspend fun fetchHotList(page: Int): List<FeedThread> {
        if (page > 1) return emptyList()
        val url = "https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=20&desktop=true"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull() ?: return emptyList()
        val data = ZhihuJson.parse(body)?.obj()?.arr("data") ?: return emptyList()
        val threads = mutableListOf<FeedThread>()
        for (element in data) {
            val item = element.obj() ?: continue
            val target = item.obj("target") ?: continue
            val cardId = item.str("card_id")
            val questionId = if (cardId != null && cardId.startsWith("Q_")) {
                cardId.removePrefix("Q_")
            } else {
                item.longId("id")?.toString() ?: continue
            }
            val title = target.obj("title_area").str("text") ?: "Untitled"
            val childAuthor = item.arr("children")?.firstOrNull()?.obj()?.obj("author")
            val excerpt = target.obj("excerpt_area").str("text")
                ?: item.arr("children")?.firstOrNull()?.obj()?.str("excerpt")
                ?: ""
            questionDataCache[questionId] = title to excerpt
            val metrics = target.obj("metrics_area")
            threads += FeedThread(
                id = "question_$questionId",
                title = title.decodeHtmlEntities(),
                content = excerpt.decodeHtmlEntities(),
                author = ZhihuParser.author(childAuthor, fallbackName = "热榜"),
                community = hotCommunity,
                timeAgo = item.str("detail_text").orEmpty(),
                likeCount = metrics.int("follower_count") ?: 0,
                commentCount = metrics.int("answer_count") ?: 0,
                tags = listOf("🔥 热榜"),
            )
        }
        return threads
    }

    private suspend fun fetchAnswerDetail(answerId: String, page: Int): ThreadDetailResult {
        val url = "https://www.zhihu.com/api/v4/answers/$answerId" +
            "?include=content,html_content,excerpt,thanks_count,voteup_count,comment_count,visited_count,author"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull()
            ?: return fetchGenericDetail("answer_$answerId")
        val answer = ZhihuJson.parse(body)?.obj() ?: return fetchGenericDetail("answer_$answerId")
        val content = answer.str("content") ?: answer.str("html_content") ?: answer.str("excerpt") ?: ""
        val thread = FeedThread(
            id = "answer_$answerId",
            title = answer.obj("question").str("title") ?: "回答",
            content = ZhihuParser.cleanHtml(content),
            author = ZhihuParser.author(answer.obj("author")),
            community = recommendCommunity,
            timeAgo = ZhihuParser.formatTimestamp(answer.int("created_time") ?: answer.int("updated_time")),
            likeCount = answer.int("voteup_count") ?: 0,
            commentCount = answer.int("comment_count") ?: 0,
        )
        return ThreadDetailResult(thread, fetchComments("answers", answerId, page), null)
    }

    private suspend fun fetchArticleDetail(articleId: String, page: Int): ThreadDetailResult {
        val url = "https://www.zhihu.com/api/v4/articles/$articleId" +
            "?include=content,html_content,excerpt,thanks_count,voteup_count,comment_count,visited_count,author"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull()
            ?: return fetchGenericDetail("article_$articleId")
        val article = ZhihuJson.parse(body)?.obj() ?: return fetchGenericDetail("article_$articleId")
        val content = article.str("content") ?: article.str("html_content") ?: article.str("excerpt") ?: ""
        val thread = FeedThread(
            id = "article_$articleId",
            title = article.str("title") ?: "文章",
            content = ZhihuParser.cleanHtml(content),
            author = ZhihuParser.author(article.obj("author")),
            community = recommendCommunity,
            timeAgo = ZhihuParser.formatTimestamp(article.int("created") ?: article.int("updated")),
            likeCount = article.int("voteup_count") ?: 0,
            commentCount = article.int("comment_count") ?: 0,
        )
        return ThreadDetailResult(thread, fetchComments("articles", articleId, page), null)
    }

    private suspend fun fetchQuestionDetail(questionId: String, page: Int): ThreadDetailResult {
        val url = "https://www.zhihu.com/api/v4/questions/$questionId" +
            "?include=detail,excerpt,answer_count,visit_count,comment_count,follower_count,topics"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull()
        val json = body?.let { ZhihuJson.parse(it)?.obj() }
        var title = "问题"
        var detail = ""
        var commentCount = 0
        var answerCount = 0
        var author = ZhihuParser.author(null)
        if (json != null && json.str("error") == null) {
            title = json.str("title") ?: title
            detail = json.str("detail") ?: json.str("excerpt") ?: ""
            commentCount = json.int("comment_count") ?: 0
            answerCount = json.int("answer_count") ?: 0
            author = ZhihuParser.author(json.obj("author"))
        } else {
            questionDataCache[questionId]?.let { (cachedTitle, cachedExcerpt) ->
                title = cachedTitle
                detail = cachedExcerpt
            }
        }
        val thread = FeedThread(
            id = "question_$questionId",
            title = title.decodeHtmlEntities(),
            content = ZhihuParser.cleanHtml(detail),
            author = author,
            community = hotCommunity,
            timeAgo = "",
            likeCount = 0,
            commentCount = commentCount,
            tags = if (answerCount > 0) listOf("回答数: $answerCount") else null,
        )
        return ThreadDetailResult(thread, fetchQuestionAnswers(questionId, page), null)
    }

    private suspend fun fetchGenericDetail(threadId: String): ThreadDetailResult {
        val webUrl = getWebUrl(FeedThread(threadId, "", "", ZhihuParser.author(null), recommendCommunity, "", 0, 0))
        val html = runCatching { httpClient.get(webUrl, cookies()) }.getOrNull().orEmpty()
        val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.substringBefore(" - 知乎")?.stripTags()?.decodeHtmlEntities()
            ?: "知乎"
        val content = Regex("""<meta\s+name=["']description["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.decodeHtmlEntities().orEmpty()
        val thread = FeedThread(
            id = threadId,
            title = title,
            content = content,
            author = ZhihuParser.author(null),
            community = recommendCommunity,
            timeAgo = "",
            likeCount = 0,
            commentCount = 0,
        )
        return ThreadDetailResult(thread, emptyList(), null)
    }

    private suspend fun fetchComments(type: String, id: String, page: Int): List<Comment> {
        val offset = (page - 1) * 20
        val url = "https://www.zhihu.com/api/v4/$type/$id/root_comments?limit=20&offset=$offset&order=normal&status=open"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull() ?: return emptyList()
        val data = ZhihuJson.parse(body)?.obj()?.arr("data") ?: return emptyList()
        return data.mapNotNull { element ->
            val comment = element.obj() ?: return@mapNotNull null
            val content = comment.str("content")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Comment(
                id = comment.str("id") ?: comment.longId("id")?.toString() ?: return@mapNotNull null,
                author = ZhihuParser.author(comment.obj("author").obj("member") ?: comment.obj("author"), fallbackName = "匿名"),
                content = ZhihuParser.cleanHtml(content),
                timeAgo = ZhihuParser.formatTimestamp(comment.int("created_time")),
                likeCount = comment.int("like_count") ?: 0,
                replies = comment.arr("child_comments")?.mapNotNull { childElement ->
                    val child = childElement.obj() ?: return@mapNotNull null
                    val childContent = child.str("content") ?: return@mapNotNull null
                    Comment(
                        id = child.str("id") ?: child.longId("id")?.toString() ?: return@mapNotNull null,
                        author = ZhihuParser.author(child.obj("author").obj("member") ?: child.obj("author"), fallbackName = "匿名"),
                        content = ZhihuParser.cleanHtml(childContent),
                        timeAgo = ZhihuParser.formatTimestamp(child.int("created_time")),
                        likeCount = child.int("like_count") ?: 0,
                    )
                }?.takeIf { it.isNotEmpty() },
            )
        }
    }

    private suspend fun fetchQuestionAnswers(questionId: String, page: Int): List<Comment> {
        val offset = (page - 1) * 10
        val url = "https://www.zhihu.com/api/v4/questions/$questionId/answers" +
            "?include=content,voteup_count,comment_count&limit=10&offset=$offset&sort_by=default"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull() ?: return emptyList()
        val data = ZhihuJson.parse(body)?.obj()?.arr("data") ?: return emptyList()
        return data.mapNotNull { element ->
            val answer = element.obj() ?: return@mapNotNull null
            val content = answer.str("content") ?: answer.str("excerpt") ?: ""
            val votes = answer.int("voteup_count") ?: 0
            Comment(
                id = answer.longId("id")?.toString() ?: answer.str("id") ?: return@mapNotNull null,
                author = ZhihuParser.author(answer.obj("author"), fallbackName = "匿名用户"),
                content = ZhihuParser.cleanHtml(content),
                timeAgo = "👍 $votes",
                likeCount = votes,
            )
        }
    }

    override suspend fun searchThreads(query: String, page: Int): SearchResult {
        val limit = 20
        val offset = (page - 1) * limit
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://www.zhihu.com/api/v4/search_v3?q=$encoded&t=content&correction=1&offset=$offset&limit=$limit"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull()
            ?: return SearchResult(emptyList(), false)
        val root = ZhihuJson.parse(body)?.obj() ?: return SearchResult(emptyList(), false)
        val data = root.arr("data") ?: return SearchResult(emptyList(), false)
        val searchCommunity = Community("search", "搜索", query, "zhihu", 0, 0)
        val threads = data.mapNotNull { element ->
            val item = element.obj() ?: return@mapNotNull null
            val obj = item.obj("object") ?: return@mapNotNull null
            val type = obj.str("type") ?: return@mapNotNull null
            if (type !in listOf("answer", "article", "question")) return@mapNotNull null
            val objId = obj.longId("id") ?: 0L
            val title = if (type == "answer") {
                obj.obj("question").str("title")?.takeIf { it.isNotEmpty() } ?: obj.str("title") ?: "无标题"
            } else {
                obj.str("title") ?: "无标题"
            }
            FeedThread(
                id = "${type}_$objId",
                title = ZhihuParser.cleanSearchText(title),
                content = ZhihuParser.cleanSearchText(obj.str("excerpt").orEmpty()),
                author = ZhihuParser.author(obj.obj("author")),
                community = searchCommunity,
                timeAgo = ZhihuParser.formatTimestamp(obj.int("created_time")),
                likeCount = obj.int("voteup_count") ?: 0,
                commentCount = obj.int("comment_count") ?: 0,
                tags = listOf(ZhihuParser.typeDescription(type)),
            )
        }
        val paging = root.obj("paging")
        val hasMore = paging.bool("is_end") == false || paging.str("next")?.isNotEmpty() == true
        return SearchResult(threads, hasMore)
    }
}

internal object ZhihuJson {
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(text: String): JsonElement? = runCatching { lenient.parseToJsonElement(text) }.getOrNull()
}

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

internal fun JsonObject?.arr(key: String): JsonArray? = this?.get(key) as? JsonArray

internal fun JsonObject?.str(key: String): String? {
    val prim = this?.get(key) as? JsonPrimitive ?: return null
    if (prim.isString) return prim.content
    return prim.contentOrNull
}

internal fun JsonObject?.int(key: String): Int? {
    val prim = this?.get(key) as? JsonPrimitive ?: return null
    return prim.intOrNull ?: prim.contentOrNull?.toDoubleOrNull()?.toInt()
}

internal fun JsonObject?.longId(key: String): Long? {
    val prim = this?.get(key) as? JsonPrimitive ?: return null
    return prim.contentOrNull?.toLongOrNull()
}

internal fun JsonObject?.bool(key: String): Boolean? {
    val prim = this?.get(key) as? JsonPrimitive ?: return null
    return prim.booleanOrNull ?: when (prim.contentOrNull) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
