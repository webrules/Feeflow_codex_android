package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowHttpClient
import com.webrules.feedflow.core.network.HttpStatusException
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
        store = store,
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
    private val store: FeedflowStore? = null,
    initialFeeds: List<RssFeedInfo> = defaultFeeds,
) : StaticForumService() {
    override val name = "RSS Feeds"
    override val id = "rss"
    override val logo = "feed"
    private val feeds = initialFeeds.toMutableList()
    private val threadCache = linkedMapOf<String, FeedThread>()
    private val currentFeeds: List<RssFeedInfo>
        get() = store?.getRssFeeds()?.takeIf { it.isNotEmpty() }?.map { feed ->
            RssFeedInfo(id = feed.url, name = feed.title, url = feed.url, description = "")
        } ?: feeds

    override suspend fun fetchCategories(): List<Community> =
        currentFeeds.map { Community(it.id, it.name, it.description, "RSS", 0, 0) }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val feed = currentFeeds.firstOrNull { it.id == categoryId } ?: throw FeedflowError.Network(url = categoryId, statusCode = 404, bodyPreview = "Feed not found")
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

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val cachedThread = threadCache[threadId]
        val thread = cachedThread?.withFullArticleContent() ?: FeedThread(
                id = threadId,
                title = "Content Unavailable",
                content = "Please refresh the feed list.",
                author = User("system", "System", "exclamationmark.triangle"),
                community = Community("error", "Error", "", "", 0, 0),
                timeAgo = "",
                likeCount = 0,
                commentCount = 0,
            )
        if (cachedThread != null) threadCache[threadId] = thread
        return ThreadDetailResult(
            thread = thread,
            comments = emptyList(),
            totalPages = 1,
        )
    }

    private suspend fun FeedThread.withFullArticleContent(): FeedThread {
        if (!id.startsWith("http://", ignoreCase = true) && !id.startsWith("https://", ignoreCase = true)) return this
        val article = runCatching {
            RssArticleExtractor.extract(
                html = httpClient.get(id),
                pageUrl = id,
            )
        }.getOrDefault("")
        return if (RssArticleExtractor.isMeaningfullyMoreComplete(content, article)) {
            copy(content = article)
        } else {
            this
        }
    }

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
                author = User(topic.author, topic.author, topic.avatar.ifBlank { "person.circle" }),
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
        val authorAvatar = V2exParser.parseTopicAvatar(html, author)
        val replies = V2exParser.parseReplies(html).map { reply ->
            Comment(
                id = reply.id,
                author = User(reply.author, reply.author, reply.avatar.ifBlank { "person.circle" }),
                content = reply.content,
                timeAgo = reply.timeAgo,
                likeCount = 0,
            )
        }
        val thread = FeedThread(
            id = threadId,
            title = title,
            content = content,
            author = User(author, author, authorAvatar.ifBlank { "person.circle" }),
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

private const val FOURD4Y_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

internal val FOURD4Y_HEADERS = mapOf(
    "User-Agent" to FOURD4Y_MOBILE_UA,
    "Cache-Control" to "no-cache",
    "Pragma" to "no-cache",
)
private const val FOURD4Y_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"

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

    private var csrfToken: String? = null

    override suspend fun restoreSession(): Boolean {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) return false
        val body = runCatching {
            httpClient.get(
                "https://linux.do/session/current.json",
                cookies,
                mapOf("Accept" to "application/json"),
            )
        }.getOrNull() ?: return false
        return ZhihuJson.parse(body)?.obj().obj("current_user") != null
    }

    override suspend fun fetchCategories(): List<Community> =
        DiscourseParser.parseCategories(httpClient.get("https://linux.do/categories.json", store?.getCookies(id).orEmpty()))

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val pageIndex = (page - 1).coerceAtLeast(0)
        val path = if (categoryId == "latest") "latest.json" else "c/$categoryId/l/latest.json"
        val json = httpClient.get("https://linux.do/$path?page=$pageIndex", store?.getCookies(id).orEmpty())
        val community = communities.firstOrNull { it.id == categoryId } ?: Community(categoryId, categoryId, "", "linux_do", 0, 0)
        return DiscourseParser.parseTopicList(json, community)
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val json = httpClient.get("https://linux.do/t/$threadId.json?page=$page", store?.getCookies(id).orEmpty())
        val (thread, comments, totalPages) = DiscourseParser.parseThreadDetail(json, threadId)
        return ThreadDetailResult(thread, comments, totalPages)
    }

    override suspend fun createThread(categoryId: String, title: String, content: String) {
        if (!restoreSession()) throw FeedflowError.AuthRequired
        val token = fetchCsrfToken()
        val numericCategory = numericCategoryId(categoryId)
            ?: throw FeedflowError.UnsupportedFeature("createThread: invalid category id $categoryId")
        val body = JsonObject(
            mapOf(
                "title" to JsonPrimitive(title),
                "raw" to JsonPrimitive(content),
                "category" to JsonPrimitive(numericCategory),
            ),
        ).toString()
        postDiscourseJson(body, token)
    }

    override suspend fun postComment(topicId: String, categoryId: String, content: String) {
        if (!restoreSession()) throw FeedflowError.AuthRequired
        val token = fetchCsrfToken()
        val numericTopicId = topicId.toIntOrNull()
            ?: throw FeedflowError.UnsupportedFeature("postComment: invalid topic id $topicId")
        val body = JsonObject(
            mapOf(
                "topic_id" to JsonPrimitive(numericTopicId),
                "raw" to JsonPrimitive(content),
            ),
        ).toString()
        postDiscourseJson(body, token)
    }

    private suspend fun postDiscourseJson(body: String, csrfToken: String) {
        httpClient.post(
            url = "https://linux.do/posts.json",
            body = body,
            cookies = store?.getCookies(id).orEmpty(),
            contentType = "application/json; charset=UTF-8",
            headers = mapOf(
                "Accept" to "application/json",
                "Origin" to "https://linux.do",
                "X-CSRF-Token" to csrfToken,
            ),
            forcedCharset = null,
        )
    }

    private suspend fun fetchCsrfToken(): String {
        csrfToken?.let { return it }
        val cookies = store?.getCookies(id).orEmpty()
        val body = try {
            httpClient.get(
                "https://linux.do/session/csrf.json",
                cookies,
                mapOf("Accept" to "application/json"),
            )
        } catch (e: HttpStatusException) {
            if (e.statusCode == 403) throw FeedflowError.AuthRequired
            throw e
        }
        val token = ZhihuJson.parse(body)?.obj()?.str("csrf")
            ?: throw FeedflowError.Parsing("linux_do", "csrf token")
        csrfToken = token
        return token
    }

    private fun numericCategoryId(categoryId: String): Int? {
        categoryId.toIntOrNull()?.let { return it }
        return categoryId.split("/").lastOrNull()?.toIntOrNull()
    }

    override suspend fun searchThreads(query: String, page: Int): SearchResult {
        if (!restoreSession()) throw FeedflowError.AuthRequired
        val encoded = query.formEncode()
        val json = httpClient.get(
            "https://linux.do/search/query?term=$encoded&page=$page",
            store?.getCookies(id).orEmpty(),
            mapOf("Accept" to "application/json"),
        )
        return DiscourseParser.parseSearch(json)
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
    private var currentFormHash: String? = null
    override val currentUsername: String?
        get() = store?.getSetting("detected_4d4y_username")?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun restoreSession(): Boolean {
        val cookies = store?.getCookies(id).orEmpty()
        val siteCookies = cookies.filter { it.domain.contains("4d4y.com") }
        if (!siteCookies.any { SiteLoginConfig.forSite(ForumSite.FourD4Y)!!.hasAuthenticatedSession(siteCookies) }) {
            return false
        }
        val html = runCatching { fetch(withSid("https://www.4d4y.com/forum/index.php", cookies), cookies) }.getOrNull() ?: return false
        rememberAuthenticatedPageArtifacts(html)
        return validateSessionHtml(html)
    }

    override fun canCreateThread(community: Community): Boolean = true
    override fun getWebUrl(thread: FeedThread): String = "https://www.4d4y.com/forum/viewthread.php?tid=${thread.id}"

    override suspend fun fetchCategories(): List<Community> {
        val cookies = authCookies()
        val html = fetch(withSid("https://www.4d4y.com/forum/index.php", cookies), cookies)
        rememberAuthenticatedPageArtifacts(html)
        return FourD4YParser.parseCategories(html).withProtectedDiscoveryIfAvailable(cookies)
    }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val cookies = authCookies()
        if (store?.getSetting("4d4y_sid").isNullOrBlank() && cookies.none { it.name.equals("sid", ignoreCase = true) }) {
            fetchCategories()
        }
        val html = fetch(categoryUrl(categoryId, page, cookies), cookies)
        rememberAuthenticatedPageArtifacts(html)
        val community = communities.firstOrNull { it.id == categoryId } ?: Community(categoryId, categoryId, "", "4D4Y", 0, 0)
        val threads = FourD4YParser.parseThreadRows(html, community)
        if (threads.isNotEmpty() || page != 1) return threads
        rememberAuthenticatedPageArtifacts(fetch(withSid("https://www.4d4y.com/forum/index.php", cookies), cookies))
        return FourD4YParser.parseThreadRows(fetch(categoryUrl(categoryId, page, cookies), cookies), community)
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val cookies = authCookies()
        val html = fetch(threadDetailUrl(threadId, page, cookies), cookies)
        if (html.contains("无权访问该版块") || html.contains("无权进行当前操作")) {
            throw FeedflowError.AuthRequired
        }
        rememberLoggedInUsername(html)
        val parsed = parseThreadDetailHtml(html, threadId, page)
            ?: throw FeedflowError.Parsing(id, "threadDetail", html.take(200))
        return ThreadDetailResult(parsed.first, parsed.second, parsed.third)
    }

    override suspend fun searchThreads(query: String, page: Int): SearchResult {
        val cookies = authCookies()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        if (store?.getSetting("4d4y_sid").isNullOrBlank() && cookies.none { it.name.equals("sid", ignoreCase = true) }) {
            fetchCategories()
        }
        val url = withSid(
            "https://www.4d4y.com/forum/search.php?searchsubmit=yes" +
                "&srchtxt=${query.gbkFormEncode()}&searchfield=all&page=$page",
            cookies,
        )
        val threads = FourD4YParser.parseSearchThreads(fetch(url, cookies))
        return SearchResult(threads, threads.size >= 20)
    }

    private fun authCookies(): List<com.webrules.feedflow.core.network.FeedflowCookie> =
        store?.getCookies(id).orEmpty().filter { it.domain.contains("4d4y.com") }

    private suspend fun fetch(url: String, cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>): String =
        httpClient.get(url, cookies, FOURD4Y_HEADERS, "GB18030")

    private fun withSid(url: String, cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>): String {
        val sid = cookies.firstOrNull { it.name.equals("sid", ignoreCase = true) || it.name.equals("cdb_sid", ignoreCase = true) }?.value
            ?: store?.getSetting("4d4y_sid")
            ?: return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}sid=${sid.formEncode()}"
    }

    private fun categoryUrl(
        categoryId: String,
        page: Int,
        cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>,
    ): String {
        val base = withSid("https://www.4d4y.com/forum/forumdisplay.php?fid=$categoryId", cookies)
        val pageParam = if (page > 1) "&page=$page" else ""
        return "$base$pageParam&_t=${Instant.now().epochSecond}"
    }

    private fun threadDetailUrl(
        threadId: String,
        page: Int,
        cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>,
    ): String {
        val base = withSid("https://www.4d4y.com/forum/viewthread.php?tid=$threadId", cookies)
        return if (page > 1) "$base&page=$page&extra=page%3D1" else base
    }

    private fun extractAndSaveSessionArtifacts(html: String) {
        val lower = html.lowercase()
        val hasLogout = lower.contains("action=logout") || lower.contains("action%3dlogout") || html.contains("退出")
        val hasLogin = (lower.contains("action=login") || lower.contains("action%3dlogin")) && !hasLogout
        val isChallenge = lower.contains("cloudflare") || lower.contains("checking your browser")
        if (hasLogin || isChallenge) return
        if (hasLogout || html.contains("sid=")) {
            FourD4YParser.extractSid(html)?.let { store?.saveSetting("4d4y_sid", it) }
        }
        extractFormHash(html)?.let { currentFormHash = it }
        rememberLoggedInUsername(html)
    }

    private fun validateSessionHtml(html: String): Boolean {
        val forums = FourD4YParser.parseCategories(html)
        val lower = html.lowercase()
        val hasLogout = lower.contains("action=logout") || lower.contains("action%3dlogout") || html.contains("退出")
        val hasLogin = (lower.contains("action=login") || lower.contains("action%3dlogin")) && !hasLogout
        val isChallenge = lower.contains("cloudflare") || lower.contains("checking your browser")
        val hasDiscovery = forums.any { it.name == "Discovery" }
        return forums.isNotEmpty() && !hasLogin && !isChallenge && (hasLogout || hasDiscovery)
    }

    private suspend fun List<Community>.withProtectedDiscoveryIfAvailable(
        cookies: List<com.webrules.feedflow.core.network.FeedflowCookie>,
    ): List<Community> {
        if (any { it.id == "2" || it.name.equals("Discovery", ignoreCase = true) }) return this
        val hasAuthCookies = cookies.any { it.domain.contains("4d4y.com") && (it.name.contains("auth", ignoreCase = true) || it.name.contains("member", ignoreCase = true) || it.name.contains("login", ignoreCase = true)) }
        if (!hasAuthCookies && !SiteLoginConfig.forSite(ForumSite.FourD4Y)!!.hasAuthenticatedSession(cookies)) return this
        val html = runCatching {
            fetch(withSid("https://www.4d4y.com/forum/forumdisplay.php?fid=2", cookies), cookies)
        }.getOrNull() ?: return this
        val lower = html.lowercase()
        val hasLogin = lower.contains("action=login") || lower.contains("logging.php?action=login") || html.contains("未登录")
        val isChallenge = lower.contains("cloudflare") || lower.contains("checking your browser")
        if (hasLogin || isChallenge) return this
        rememberAuthenticatedPageArtifacts(html)
        return this + Community("2", "Discovery", "", "4D4Y", 0, 0)
    }

    private fun rememberAuthenticatedPageArtifacts(html: String) {
        if (!validateSessionHtml(html)) {
            extractAndSaveSessionArtifacts(html)
            return
        }
        extractAndSaveSessionArtifacts(html)
    }

    private fun rememberLoggedInUsername(html: String) {
        val lower = html.lowercase()
        if (!lower.contains("action=logout") && !lower.contains("action%3dlogout") && !html.contains("退出")) return
        parseLoggedInUsername(html)?.let { store?.saveSetting("detected_4d4y_username", it) }
    }

    override suspend fun postComment(topicId: String, categoryId: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        if (currentFormHash == null) {
            val formHtml = fetch(withSid("https://www.4d4y.com/forum/post.php?action=reply&fid=$categoryId&tid=$topicId", cookies), cookies)
            currentFormHash = extractFormHash(formHtml)
        }
        val formHash = currentFormHash ?: throw FeedflowError.Parsing(id, "replyFormhash", "no formhash available")
        val response = httpClient.post(
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
            contentType = FOURD4Y_FORM_CONTENT_TYPE,
            headers = fourD4YMutationHeaders("https://www.4d4y.com/forum/viewthread.php?tid=$topicId"),
            forcedCharset = "GB18030",
        )
        ensureMutationSucceeded("postComment", response)
    }

    override suspend fun createThread(categoryId: String, title: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val formHtml = fetch(withSid("https://www.4d4y.com/forum/post.php?action=newthread&fid=$categoryId", cookies), cookies)
        currentFormHash = extractFormHash(formHtml)
        val formHash = currentFormHash ?: throw FeedflowError.Parsing(id, "newThreadFormhash", formHtml.take(200))
        val typeField = extractFirstTypeId(formHtml)?.let { "&typeid=${it.gbkFormEncode()}" }.orEmpty()
        val response = httpClient.post(
            url = withSid("https://www.4d4y.com/forum/post.php?action=newthread&fid=$categoryId&extra=&topicsubmit=yes&inajax=1", cookies),
            body = "formhash=${formHash.gbkFormEncode()}&posttime=${Instant.now().epochSecond}&wysiwyg=1$typeField&subject=${title.gbkFormEncode()}&message=${content.gbkFormEncode()}&topicsubmit=yes&inajax=1",
            cookies = cookies,
            contentType = FOURD4Y_FORM_CONTENT_TYPE,
            headers = fourD4YMutationHeaders("https://www.4d4y.com/forum/post.php?action=newthread&fid=$categoryId"),
            forcedCharset = "GB18030",
        )
        ensureMutationSucceeded("createThread", response)
    }

    override suspend fun deleteThread(threadId: String, categoryId: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val threadHtml = fetch(withSid("https://www.4d4y.com/forum/viewthread.php?tid=$threadId", cookies), cookies)
        val pid = parseFirstPostId(threadHtml) ?: throw FeedflowError.Parsing(id, "deletePid", threadHtml.take(200))
        val editHtml = fetch(withSid("https://www.4d4y.com/forum/post.php?action=edit&fid=$categoryId&tid=$threadId&pid=$pid&page=1", cookies), cookies)
        currentFormHash = extractFormHash(editHtml)
        val formHash = currentFormHash ?: throw FeedflowError.Parsing(id, "deleteFormhash", editHtml.take(200))
        val response = httpClient.post(
            url = withSid("https://www.4d4y.com/forum/post.php?action=edit&fid=$categoryId&tid=$threadId&pid=$pid&page=1&editsubmit=yes&inajax=1", cookies),
            body = "formhash=${formHash.gbkFormEncode()}&delete=1&editsubmit=yes&inajax=1",
            cookies = cookies,
            contentType = FOURD4Y_FORM_CONTENT_TYPE,
            headers = fourD4YMutationHeaders("https://www.4d4y.com/forum/viewthread.php?tid=$threadId"),
            forcedCharset = "GB18030",
        )
        ensureMutationSucceeded("deleteThread", response)
    }

    private fun fourD4YMutationHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "text/xml, */*",
        "X-Requested-With" to "XMLHttpRequest",
        "Referer" to referer,
        "Origin" to "https://www.4d4y.com",
        "User-Agent" to FOURD4Y_MOBILE_UA,
    )

    private fun ensureMutationSucceeded(operation: String, response: String) {
        if (
            response.contains("succeed", ignoreCase = true) ||
            response.contains("成功") ||
            (operation == "postComment" && response.contains("发布"))
        ) {
            return
        }
        val message = Regex("""<!\[CDATA\[(.*?)(?:\]\]>|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(response)
            ?.groupValues
            ?.get(1)
            ?.stripTags()
            ?.decodeHtmlEntities()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (
            response.contains("ajaxerror", ignoreCase = true) ||
            response.contains("登录") ||
            response.contains("login", ignoreCase = true) ||
            response.contains("无权访问")
        ) {
            throw FeedflowError.AuthRequired
        }
        throw FeedflowError.Parsing(id, operation, message ?: response.take(200))
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

    fun extractFirstTypeId(html: String): String? {
        val select = Regex(
            """<select[^>]*name=["']typeid["'][^>]*>(.*?)</select>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.get(1) ?: return null
        return Regex("""<option[^>]*value=["'](\d+)["']""", RegexOption.IGNORE_CASE)
            .findAll(select)
            .map { it.groupValues[1] }
            .firstOrNull { it != "0" }
    }

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
        listOf("""pid=(\d+)""", """reppost=(\d+)""", """repquote=(\d+)""", """authorposton(\d+)""", """post_(\d+)""")
            .firstNotNullOfOrNull { Regex(it, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) }

    override fun canDeleteThread(thread: FeedThread): Boolean =
        currentUsername?.equals(thread.author.username, ignoreCase = true) == true

    fun parseThreadDetailHtml(html: String, threadId: String, page: Int): Triple<FeedThread, List<Comment>, Int?>? {
        val title = extractThreadDetailTitle(html)
        val desktopPosts = extractDesktopThreadDetailPosts(html)
        if (desktopPosts.isNotEmpty()) {
            val community = Community(
                id = extractThreadDetailFid(html, threadId),
                name = "",
                description = "",
                category = "",
                activeToday = 0,
                onlineNow = 0,
            )
            val commentPosts = if (page == 1) desktopPosts.drop(1) else desktopPosts
            val mainPost = desktopPosts.first()
            val thread = FeedThread(
                id = threadId,
                title = title,
                content = if (page == 1) cleanContent(mainPost.rawContent) else "",
                author = if (page == 1) mainPost.author else User("0", "", ""),
                community = community,
                timeAgo = if (page == 1) mainPost.timeAgo else "",
                likeCount = 0,
                commentCount = if (page == 1) commentPosts.size else 0,
            )
            val comments = commentPosts.map { post ->
                Comment(
                    id = post.id,
                    author = post.author,
                    content = cleanContent(post.rawContent),
                    timeAgo = post.timeAgo,
                    likeCount = 0,
                )
            }
            return Triple(thread, comments, extractThreadDetailTotalPages(html))
        }
        val wapPosts = listOfNotNull(extractWapMainPost(html)) + extractWapReplyPosts(html)
        if (wapPosts.isEmpty()) return null
        val commentPosts = if (page == 1) wapPosts.drop(1) else wapPosts
        val mainPost = wapPosts.first()
        val community = Community(
            id = extractThreadDetailFid(html, threadId),
            name = "",
            description = "",
            category = "",
            activeToday = 0,
            onlineNow = 0,
        )
        val thread = FeedThread(
            id = threadId,
            title = title,
            content = if (page == 1) cleanContent(mainPost.rawContent) else "",
            author = if (page == 1) mainPost.author else User("0", "", ""),
            community = community,
            timeAgo = if (page == 1) mainPost.timeAgo else "",
            likeCount = 0,
            commentCount = if (page == 1) commentPosts.size else 0,
        )
        val comments = commentPosts.map { post ->
            Comment(
                id = post.id,
                author = post.author,
                content = cleanContent(post.rawContent),
                timeAgo = post.timeAgo,
                likeCount = 0,
            )
        }
        return Triple(thread, comments, extractThreadDetailTotalPages(html))
    }

    private data class FourD4YDetailPost(
        val id: String,
        val author: User,
        val rawContent: String,
        val timeAgo: String,
    )

    private fun extractWapMainPost(html: String): FourD4YDetailPost? {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val opening = Regex(
            """<div(?=[^>]*class=["'][^"']*detailcon)(?=[^>]*id=["']pid(\d+)["'])[^>]*>""",
            options,
        ).find(html)
        if (opening != null) {
            val contentStart = opening.range.last + 1
            val boundary = Regex(
                """<div[^>]*class=["'][^"']*(?:detailbtn|replylist)[^"']*["']|<li[^>]*id=["']pid\d+["']|</body>""",
                options,
            ).find(html, contentStart)?.range?.first ?: html.length
            var rawContent = html.substring(contentStart, boundary).trimEnd()
            if (rawContent.endsWith("</div>", ignoreCase = true)) {
                rawContent = rawContent.dropLast("</div>".length)
            }
            val pid = opening.groupValues[1]
            val prefix = html.substring(0, opening.range.first).takeLast(2_500)
            return FourD4YDetailPost(
                id = pid,
                author = extractWapAuthor(prefix),
                rawContent = rawContent,
                timeAgo = extractWapMainPostTime(prefix, pid),
            )
        }
        val patterns = listOf(
            """<div[^>]*class=["'][^"']*detailcon[^"']*["'][^>]*id=["']pid(\d+)["'][^>]*>(.*?)</div>\s*</div>\s*<div[^>]*class=["'][^"']*detailbtn""",
            """<div[^>]*id=["']pid(\d+)["'][^>]*class=["'][^"']*detailcon[^"']*["'][^>]*>(.*?)</div>\s*</div>\s*<div[^>]*class=["'][^"']*detailbtn""",
            """<div[^>]*class=["'][^"']*detailcon[^"']*["'][^>]*id=["']pid(\d+)["'][^>]*>(.*?)</div>""",
            """<div[^>]*id=["']pid(\d+)["'][^>]*class=["'][^"']*detailcon[^"']*["'][^>]*>(.*?)</div>""",
        )
        patterns.forEach { pattern ->
            val match = Regex(pattern, options).find(html) ?: return@forEach
            val pid = match.groupValues[1]
            val prefix = html.substring(0, match.range.first).takeLast(2_500)
            return FourD4YDetailPost(
                id = pid,
                author = extractWapAuthor(prefix),
                rawContent = match.groupValues[2],
                timeAgo = extractWapMainPostTime(prefix, pid),
            )
        }
        return null
    }

    private fun extractWapReplyPosts(html: String): List<FourD4YDetailPost> {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val replyList = Regex(
            """<div[^>]*class=["'][^"']*replylist[^"']*["'][^>]*>\s*<ul>(.*?)</ul>\s*</div>""",
            options,
        ).find(html)?.groupValues?.get(1) ?: html
        return Regex("""<li[^>]*id=["']pid(\d+)["'][^>]*>(.*?)</li>""", options)
            .findAll(replyList)
            .mapNotNull { match ->
                val block = match.groupValues[2]
                val top = Regex(
                    """<div[^>]*class=["'][^"']*replytop[^"']*["'][^>]*>(.*?)</div>""",
                    options,
                ).find(block)?.groupValues?.get(1) ?: block
                val content = Regex(
                    """<div[^>]*class=["'][^"']*replycon[^"']*["'][^>]*>(.*)</div>\s*$""",
                    options,
                ).find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val timeAgo = extractWapReplyPostTime(top)
                val position = Regex("""<span[^>]*>\s*(\d+)#?\s*</span>""", RegexOption.IGNORE_CASE)
                    .find(top)?.groupValues?.get(1)
                val displayTime = if (position != null && timeAgo.isNotBlank()) "# · " else timeAgo
                FourD4YDetailPost(
                    id = match.groupValues[1],
                    author = extractWapAuthor(top),
                    rawContent = content,
                    timeAgo = displayTime,
                )
            }
            .toList()
    }

    private fun extractWapAuthor(html: String): User {
        val match = Regex(
            """<a[^>]+href=["']space\.php\?uid=(\d+)[^"']*["'][^>]*>([^<]+)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html).lastOrNull()
        val uid = match?.groupValues?.get(1).orEmpty()
        val username = match?.groupValues?.get(2)?.stripTags()?.decodeHtmlEntities()?.trim().orEmpty()
        return User(
            id = uid.ifBlank { "0" },
            username = username.ifBlank { "User" },
            avatar = if (uid.isBlank()) "person.circle" else avatarUrlForUid(uid),
        )
    }

    private fun extractWapMainPostTime(html: String, pid: String): String =
        listOf(
            """<em[^>]*id=["']authorposton${Regex.escape(pid)}["'][^>]*>(.*?)</em>""",
            """<em[^>]*>(.*?)</em>""",
        ).firstNotNullOfOrNull { pattern ->
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.let(::cleanDiscuzPostTime)
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun extractWapReplyPostTime(html: String): String =
        listOf(
            """space\.php\?uid=\d+[^>]*>[^<]+</a>\s*/\s*([^<]+)""",
            """(\d{4}-\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2})""",
            """(\d{1,2}:\d{2})""",
        ).firstNotNullOfOrNull { pattern ->
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.let(::cleanDiscuzPostTime)
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun cleanDiscuzPostTime(html: String): String =
        cleanContent(html).replace("发表于", "").trim()

    private fun extractDesktopThreadDetailPosts(html: String): List<FourD4YDetailPost> {
        val authors = extractDesktopPostAuthors(html)
        val postPositions = extractDesktopPostPositions(html)
        val postTimes = extractDesktopPostTimes(html)
        return postContentMatches(html).mapIndexedNotNull { index, match ->
            val pid = match.groupValues.getOrNull(1).orEmpty()
            val content = match.groupValues.getOrNull(2).orEmpty()
            if (pid.isBlank() || content.isBlank()) return@mapIndexedNotNull null
            val position = postPositions.getOrNull(index)
            val time = postTimes.getOrNull(index).orEmpty()
            val displayTime = buildString {
                if (position != null) append("#")
                if (time.isNotBlank()) {
                    if (position != null) append(" · ")
                    append(time)
                }
            }
            FourD4YDetailPost(
                id = pid,
                author = authors.getOrElse(index) { User("0", "User", "person.circle") },
                rawContent = content,
                timeAgo = displayTime,
            )
        }
    }

    private fun extractDesktopPostPositions(html: String): List<Int> =
        Regex("""<em>(\d+)</em>\s*<sup>#</sup>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()

    private fun extractDesktopPostTimes(html: String): List<String> =
        Regex("""<em[^>]*id=["']authorposton\d+["'][^>]*>\s*(?:发表于\s*)?([^<]+)</em>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map { it.groupValues[1].trim() }
            .toList()

    private fun postContentMatches(html: String): List<MatchResult> {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val patterns = listOf(
            """class=["']t_msgfont["'][^>]*id=["']postmessage_(\d+)["'][^>]*>(.*?)</td>""",
            """id=["']postmessage_(\d+)["'][^>]*class=["']t_msgfont["'][^>]*>(.*?)</td>""",
            """<td[^>]*id=["']postmessage_(\d+)["'][^>]*>(.*?)</td>""",
            """<div[^>]*id=["']postmessage_(\d+)["'][^>]*>(.*?)</div>""",
        )
        return patterns
            .flatMap { pattern -> Regex(pattern, options).findAll(html).toList() }
            .sortedBy { it.range.first }
            .distinctBy { it.groupValues[1] }
    }

    private fun extractDesktopPostAuthors(html: String): List<User> {
        val authorBlocks = Regex("""<td[^>]*class=["']postauthor["'][^>]*>(.*?)</td>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map { it.groupValues[1] }
            .toList()
        val blockUsers = authorBlocks.mapNotNull(::extractDesktopPostAuthor)
        if (blockUsers.isNotEmpty()) return blockUsers
        return Regex("""class=["']postauthor["'][^>]*>.*?class=["']postinfo["'][^>]*>.*?<a[^>]*>([^<]+)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map {
                val username = it.groupValues[1].decodeHtmlEntities().trim()
                User(username, username.ifBlank { "User" }, "person.circle")
            }
            .toList()
    }

    private fun extractDesktopPostAuthor(block: String): User? {
        val username = listOf(
            """class=["']postinfo["'][^>]*>.*?<a[^>]*>([^<]+)</a>""",
            """<a[^>]+href=["']space\.php\?uid=\d+[^"']*["'][^>]*>([^<]+)</a>""",
            """<a[^>]+href=["']member\.php[^"']*["'][^>]*>([^<]+)</a>""",
        ).firstNotNullOfOrNull { pattern ->
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(block)
                ?.groupValues
                ?.get(1)
                ?.stripTags()
                ?.decodeHtmlEntities()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } ?: return null
        val uid = Regex("""(?:space|member)\.php\?[^"'>]*(?:[?&])?uid=(\d+)""", RegexOption.IGNORE_CASE)
            .find(block.decodeHtmlEntities())
            ?.groupValues
            ?.get(1)
            .orEmpty()
        val explicitAvatar = extractAvatarUrl(block)
        return User(
            id = uid.ifBlank { username },
            username = username,
            avatar = explicitAvatar ?: if (uid.isNotBlank()) avatarUrlForUid(uid) else "person.circle",
        )
    }

    private fun extractAvatarUrl(html: String): String? {
        val patterns = listOf(
            """<img[^>]+class=["'][^"']*avatar[^"']*["'][^>]+(?:src|data-src)=["']([^"']+)["']""",
            """<img[^>]+(?:src|data-src)=["']([^"']+)["'][^>]+class=["'][^"']*avatar[^"']*["']""",
            """<img[^>]+?(?:src|data-src|file)=["']([^"']*(?:avatar|uc_server|face|head)[^"']*)["']""",
            """<img[^>]+srcset=["']([^"']*(?:avatar|uc_server|face|head)[^"']*)["']""",
            """background(?:-image)?\s*:\s*url\(["']?([^"')]+(?:avatar|uc_server|face|head)[^"')]+)["']?\)""",
        )
        val raw = patterns.firstNotNullOfOrNull { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        } ?: return null
        val first = raw.substringBefore(',').trim().split(Regex("""\s+""")).first().decodeHtmlEntities()
        return when {
            first.isBlank() -> null
            first.startsWith("//") -> "https:$first"
            first.startsWith("http://", ignoreCase = true) -> "https://${first.substringAfter("://")}"
            first.startsWith("http") -> first
            first.startsWith("/") -> "https://www.4d4y.com$first"
            first.startsWith("uc_server/") -> "https://www.4d4y.com/$first"
            first.startsWith("data/avatar/") -> "https://img02.4d4y.com/forum/uc_server/$first"
            else -> "https://www.4d4y.com/forum/$first"
        }
    }

    private fun extractThreadDetailTitle(html: String): String {
        val title = listOf(
            """<div[^>]*class=["'][^"']*\bdetail\b[^"']*["'][^>]*>.*?<h2[^>]*>\s*(?:<a[^>]*>)?\s*(.*?)\s*</h2>""",
            """<h1[^>]*>\s*(?:<a[^>]*>)?\s*(.*?)\s*</h1>""",
            """<title>\s*(.*?)\s*</title>""",
            """<h2[^>]*>\s*(?:<a[^>]*>)?\s*(.*?)\s*</h2>""",
        ).firstNotNullOfOrNull { pattern ->
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.stripTags()
                ?.decodeHtmlEntities()
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                ?.substringBefore(" - ")
                ?.takeIf { it.isNotBlank() }
        }
        return title ?: "Unknown Topic"
    }

    private fun extractThreadDetailFid(html: String, threadId: String): String {
        val decoded = html.decodeHtmlEntities()
        val escapedThreadId = Regex.escape(threadId)
        val direct = listOf(
            """\bfid\s*=\s*parseInt\(['"](\d+)['"]\)""",
            """post\.php\?action=reply[^"']*[?&]fid=(\d+)[^"']*[?&]tid=$escapedThreadId""",
            """class=["'][^"']*current[^"']*["'][^>]*>\s*<a[^>]+href=["']forumdisplay\.php\?fid=(\d+)""",
        ).firstNotNullOfOrNull { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).find(decoded)?.groupValues?.get(1)
        }
        if (direct != null) return direct
        val navbar = Regex(
            """<div[^>]*class=["'][^"']*navbar[^"']*["'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(decoded)?.groupValues?.get(1)
        val forumPattern = Regex("""forumdisplay\.php\?fid=(\d+)""", RegexOption.IGNORE_CASE)
        return navbar
            ?.let { forumPattern.findAll(it).lastOrNull()?.groupValues?.get(1) }
            ?: forumPattern.findAll(decoded).lastOrNull()?.groupValues?.get(1)
            ?: "0"
    }

    private fun extractThreadDetailTotalPages(html: String): Int {
        val candidates = mutableListOf<Int>()
        Regex("""<div[^>]*class=["']pages["'][^>]*>(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let { pages ->
                candidates += Regex("""(?:>|\s)(\d+)(?:<|\s)""").findAll(pages).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            }
        Regex("""<div[^>]*class=["'][^"']*seclist[^"']*["'][^>]*>.*?\d+\s*/\s*(\d+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { candidates += it }
        return candidates.maxOrNull() ?: 1
    }

    companion object {
        fun parseLoggedInUsername(html: String): String? =
            Regex("""欢迎您回来，\s*<strong>([^<]+)</strong>""").find(html)?.groupValues?.get(1)
                ?: Regex("""space\.php\?uid=\d+[^>]*font-weight:\s*800[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""space\.php\?username=([^"'&]{1,40})["'][^>]*>(?:个人空间|我的)""", RegexOption.IGNORE_CASE)
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }

        fun cleanContent(html: String): String {
            var processed = html
                .replace(Regex("""<div\s+class=["']t_attach["'][^>]*>.*?</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("""<ignore_js_op>.*?</ignore_js_op>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            processed = Regex("""<font[^>]+size\s*=\s*["']?(\d+)["']?[^>]*>.*?</font>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .replace(processed) { match ->
                    val size = match.groupValues[1].toIntOrNull() ?: 99
                    if (size <= 4) "" else match.value
                }
            processed = Regex("""<div\s+class=["']quote["'][^>]*>\s*<blockquote>\s*(.*?)\s*</blockquote>\s*</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .replace(processed) { match ->
                    val inner = match.groupValues[1]
                    val cleaned = Regex("""<a[^>]+href=["'][^"']*redirect\.php\?goto=findpost[^"']*["'][^>]*>.*?</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .replace(inner, "")
                    val fontCleaned = Regex("""<font[^>]+size\s*=\s*["']?(\d+)["']?[^>]*>.*?</font>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .replace(cleaned) { fm ->
                            val sz = fm.groupValues[1].toIntOrNull() ?: 99
                            if (sz <= 4) "" else fm.value
                        }
                    "\n[QUOTE]${fontCleaned.trim()}[/QUOTE]\n"
                }
            processed = Regex("""<a[^>]+href=["'][^"']*redirect\.php\?goto=findpost[^"']*["'][^>]*>.*?</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .replace(processed, "")
            processed = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).replace(processed) { match ->
                val src = match.groupValues[1]
                if (src.contains("smilies") || src.contains("images/default") || src.contains("images/common") || src.contains("common/back.gif")) {
                    ""
                } else {
                    val resolved = if (src.startsWith("http")) src else "https://www.4d4y.com/forum/$src"
                    "\n[IMAGE:$resolved]\n"
                }
            }
            processed = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(processed) { match ->
                val href = match.groupValues[1].decodeHtmlEntities()
                val label = match.groupValues[2].stripTags().decodeHtmlEntities().trim()
                if (label.isBlank() || label == href || href.startsWith("javascript:", ignoreCase = true)) label else "$label ($href)"
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
            val questionId = ZhihuParser.hotQuestionId(item, target) ?: continue
            val title = ZhihuParser.hotTitle(item, target)
            val excerpt = ZhihuParser.hotExcerpt(item, target)
            val author = ZhihuParser.hotAuthor(item, target)
            questionDataCache[questionId] = title to excerpt
            val metrics = target.obj("metrics_area")
            threads += FeedThread(
                id = "question_$questionId",
                title = title,
                content = excerpt,
                author = ZhihuParser.author(author, fallbackName = "热榜"),
                community = hotCommunity,
                timeAgo = item.str("detail_text") ?: metrics.str("text").orEmpty(),
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
            "?include=detail,excerpt,answer_count,visit_count,comment_count,follower_count,topics,author"
        val body = runCatching { httpClient.get(url, cookies(), apiHeaders()) }.getOrNull()
        val json = body?.let { ZhihuJson.parse(it)?.obj() }
        var title = "问题"
        var detail = ""
        var commentCount = 0
        var answerCount = 0
        var author = ZhihuParser.author(null)
        if (json != null && json["error"] == null) {
            title = json.str("title") ?: title
            detail = json.str("detail") ?: json.str("excerpt") ?: ""
            commentCount = json.int("comment_count") ?: 0
            answerCount = json.int("answer_count") ?: 0
            author = ZhihuParser.author(json.obj("author"))
        }
        questionDataCache[questionId]?.let { (cachedTitle, cachedExcerpt) ->
            if (title == "问题" || title.isBlank()) title = cachedTitle
            if (detail.isBlank()) detail = cachedExcerpt
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
            "?include=content,voteup_count,comment_count,author&limit=10&offset=$offset&sort_by=default"
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
