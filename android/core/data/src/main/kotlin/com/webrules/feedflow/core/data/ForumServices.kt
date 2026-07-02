package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowHttpClient
import com.webrules.feedflow.core.network.UnimplementedFeedflowHttpClient
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
    ForumSite.Zhihu -> ZhihuService(httpClient)
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
        DiscourseParser.parseCategories(httpClient.get("https://linux.do/categories.json"))

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val path = if (categoryId == "latest") "latest.json" else "c/$categoryId.json"
        val json = httpClient.get("https://linux.do/$path?page=$page")
        val community = communities.firstOrNull { it.id == categoryId } ?: Community(categoryId, categoryId, "", "linux_do", 0, 0)
        return parseDiscourseTopicList(json, community)
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val json = httpClient.get("https://linux.do/t/$threadId.json")
        val title = Regex(""""title"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)?.decodeHtmlEntities() ?: "Linux.do"
        val communityName = Regex(""""category_id"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1).orEmpty()
        val community = Community(communityName.ifBlank { "latest" }, communityName.ifBlank { "Latest" }, "", "linux_do", 0, 0)
        val posts = Regex("""\{[^{}]*"id"\s*:\s*(\d+)[^{}]*"username"\s*:\s*"([^"]+)"[^{}]*"cooked"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .findAll(json)
            .map {
                Comment(
                    id = it.groupValues[1],
                    author = User(it.groupValues[2], it.groupValues[2], "person.circle"),
                    content = it.groupValues[3].jsonUnescape().stripTags().decodeHtmlEntities(),
                    timeAgo = "",
                    likeCount = 0,
                )
            }.toList()
        val firstPost = posts.firstOrNull()
        val thread = FeedThread(
            id = threadId,
            title = title,
            content = firstPost?.content.orEmpty(),
            author = firstPost?.author ?: User("linux_do", "Linux.do", "terminal.fill"),
            community = community,
            timeAgo = "",
            likeCount = 0,
            commentCount = (posts.size - 1).coerceAtLeast(0),
        )
        return ThreadDetailResult(thread, posts.drop(1), totalPages = null)
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

    private fun parseDiscourseTopicList(json: String, community: Community): List<FeedThread> =
        Regex("""\{[^{}]*"id"\s*:\s*(\d+)[^{}]*"title"\s*:\s*"([^"]+)"[^{}]*}""")
            .findAll(json)
            .map {
                val block = it.value
                val id = Regex(""""id"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1).orEmpty()
                val title = Regex(""""title"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1).orEmpty()
                val posts = Regex(""""posts_count"\s*:\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                FeedThread(
                    id = id,
                    title = title,
                    content = "",
                    author = User("linux_do", "Linux.do", "person.circle"),
                    community = community,
                    timeAgo = "",
                    likeCount = 0,
                    commentCount = (posts - 1).coerceAtLeast(0),
                )
            }.toList()
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
        val html = runCatching { httpClient.get("https://www.4d4y.com/forum/index.php", cookies) }.getOrNull() ?: return true
        parseLoggedInUsername(html)?.let { username -> store?.saveSetting("detected_4d4y_username", username) }
        return parseLoggedInUsername(html) != null
    }

    override fun canCreateThread(community: Community): Boolean = true
    override fun getWebUrl(thread: FeedThread): String = "https://www.4d4y.com/forum/viewthread.php?tid=${thread.id}"

    override suspend fun fetchCategories(): List<Community> {
        val html = httpClient.get("https://www.4d4y.com/forum/index.php")
        return FourD4YParser.parseCategories(html)
    }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        val html = httpClient.get("https://www.4d4y.com/forum/forumdisplay.php?fid=$categoryId&page=$page")
        val community = communities.firstOrNull { it.id == categoryId } ?: Community(categoryId, categoryId, "", "4D4Y", 0, 0)
        return FourD4YParser.parseThreadRows(html, community)
    }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val html = httpClient.get("https://www.4d4y.com/forum/viewthread.php?tid=$threadId&page=$page")
        val parsed = parseThreadDetailHtml(html, threadId, page)
            ?: throw FeedflowError.Parsing(id, "threadDetail", html.take(200))
        return ThreadDetailResult(parsed.first, parsed.second, parsed.third)
    }

    override suspend fun postComment(topicId: String, categoryId: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val formHtml = httpClient.get("https://www.4d4y.com/forum/viewthread.php?tid=$topicId", cookies)
        val formHash = extractFormHash(formHtml) ?: throw FeedflowError.Parsing(id, "replyFormhash", formHtml.take(200))
        httpClient.post(
            url = "https://www.4d4y.com/forum/post.php?action=reply&fid=$categoryId&tid=$topicId&extra=&replysubmit=yes&inajax=1",
            body = listOf(
                "formhash=${formHash.formEncode()}",
                "subject=",
                "message=${content.formEncode()}",
                "replysubmit=yes",
            ).joinToString("&"),
            cookies = cookies,
        )
    }

    override suspend fun createThread(categoryId: String, title: String, content: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val formHtml = httpClient.get("https://www.4d4y.com/forum/post.php?action=newthread&fid=$categoryId", cookies)
        val formHash = extractFormHash(formHtml) ?: throw FeedflowError.Parsing(id, "newThreadFormhash", formHtml.take(200))
        val typeField = extractFirstTypeId(formHtml)?.let { "&typeid=${it.formEncode()}" }.orEmpty()
        httpClient.post(
            url = "https://www.4d4y.com/forum/post.php?action=newthread&fid=$categoryId&extra=&topicsubmit=yes&inajax=1",
            body = "formhash=${formHash.formEncode()}$typeField&subject=${title.formEncode()}&message=${content.formEncode()}&topicsubmit=yes",
            cookies = cookies,
        )
    }

    override suspend fun deleteThread(threadId: String, categoryId: String) {
        val cookies = store?.getCookies(id).orEmpty()
        if (cookies.isEmpty()) throw FeedflowError.AuthRequired
        val threadHtml = httpClient.get("https://www.4d4y.com/forum/viewthread.php?tid=$threadId", cookies)
        val pid = parseFirstPostId(threadHtml) ?: throw FeedflowError.Parsing(id, "deletePid", threadHtml.take(200))
        val editHtml = httpClient.get("https://www.4d4y.com/forum/post.php?action=edit&fid=$categoryId&tid=$threadId&pid=$pid&page=1", cookies)
        val formHash = extractFormHash(editHtml) ?: throw FeedflowError.Parsing(id, "deleteFormhash", editHtml.take(200))
        httpClient.post(
            url = "https://www.4d4y.com/forum/post.php?action=edit&fid=$categoryId&tid=$threadId&pid=$pid&page=1&editsubmit=yes&inajax=1",
            body = "formhash=${formHash.formEncode()}&delete=1&editsubmit=yes&inajax=1",
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
        val content = Regex("""<div class="detailcon"[^>]*>(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.stripTags()?.decodeHtmlEntities().orEmpty()
        val comments = Regex("""<li id="pid(\d+)">.*?space\.php\?uid=(\d+)[^>]*>([^<]+)</a>/\s*([^<]+)</div>\s*<div class="replycon">(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map {
                Comment(
                    id = it.groupValues[1],
                    author = User(it.groupValues[2], it.groupValues[3].decodeHtmlEntities(), avatarUrlForUid(it.groupValues[2])),
                    content = it.groupValues[5].stripTags().decodeHtmlEntities(),
                    timeAgo = it.groupValues[4].trim(),
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
            content = content,
            author = User(authorMatch?.groupValues?.get(1).orEmpty(), authorMatch?.groupValues?.get(2).orEmpty(), avatarUrlForUid(authorMatch?.groupValues?.get(1).orEmpty())),
            community = community,
            timeAgo = authorMatch?.groupValues?.get(3)?.trim().orEmpty(),
            likeCount = 0,
            commentCount = comments.size,
        )
        return Triple(thread, comments, totalPages)
    }

    companion object {
        fun parseLoggedInUsername(html: String): String? =
            Regex("""欢迎您回来，\s*<strong>([^<]+)</strong>""").find(html)?.groupValues?.get(1)
                ?: Regex("""space\.php\?uid=\d+[^>]*font-weight:\s*800[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
    }
}

class ZhihuService(
    private val httpClient: FeedflowHttpClient = UnimplementedFeedflowHttpClient(),
) : StaticForumService() {
    override val name = "知乎"
    override val id = "zhihu"
    override val logo = "questionmark.bubble.fill"
    override val requiresLogin = true
    override suspend fun fetchCategories(): List<Community> = ZhihuParser.categories
    override fun getWebUrl(thread: FeedThread): String =
        if (thread.id.startsWith("question/") || thread.id.startsWith("p/")) {
            "https://www.zhihu.com/${thread.id}"
        } else {
            "https://www.zhihu.com/question/${thread.id}"
        }

    override suspend fun fetchThreadDetail(threadId: String, page: Int): ThreadDetailResult {
        val url = if (threadId.startsWith("http")) {
            threadId
        } else if (threadId.startsWith("question/") || threadId.startsWith("p/")) {
            "https://www.zhihu.com/$threadId"
        } else {
            "https://www.zhihu.com/question/$threadId"
        }
        val html = httpClient.get(url)
        val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.substringBefore(" - 知乎")?.stripTags()?.decodeHtmlEntities()
            ?: Regex(""""title"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)?.jsonUnescape()?.decodeHtmlEntities()
            ?: "知乎"
        val content = listOf(
            Regex("""<meta\s+name=["']description["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1),
            Regex(""""excerpt"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)?.jsonUnescape(),
            Regex("""<script[^>]+id=["']js-initialData["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1)?.take(900)?.jsonUnescape(),
        ).firstOrNull { !it.isNullOrBlank() }?.stripTags()?.decodeHtmlEntities().orEmpty()
        val thread = FeedThread(
            id = threadId,
            title = title,
            content = content,
            author = User("zhihu", "知乎", "questionmark.bubble.fill"),
            community = ZhihuParser.categories.first(),
            timeAgo = "",
            likeCount = 0,
            commentCount = 0,
        )
        return ThreadDetailResult(thread, emptyList(), totalPages = null)
    }

    override suspend fun fetchCategoryThreads(categoryId: String, communities: List<Community>, page: Int): List<FeedThread> {
        if (page > 1) return emptyList()
        val html = httpClient.get(if (categoryId == "hot") "https://www.zhihu.com/hot" else "https://www.zhihu.com")
        val community = communities.firstOrNull { it.id == categoryId } ?: ZhihuParser.categories.first()
        return ZhihuParser.extractSearchUrls(html).take(20).mapIndexed { index, url ->
            urlToThread(url, community, index)
        }
    }

    override suspend fun searchThreads(query: String, page: Int): SearchResult {
        if (page > 1) return SearchResult(emptyList(), false)
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val html = httpClient.get("https://www.zhihu.com/search?type=content&q=$encoded")
        val community = Community("search", "Search", query, "Zhihu", 0, 0)
        return SearchResult(
            threads = ZhihuParser.extractSearchUrls(html).take(20).mapIndexed { index, url -> urlToThread(url, community, index) },
            hasMore = false,
        )
    }

    private fun urlToThread(url: String, community: Community, index: Int): FeedThread {
        val id = url.removePrefix("https://www.zhihu.com/")
        val title = when {
            "/answer/" in url -> "Zhihu Answer ${index + 1}"
            "/p/" in url -> "Zhihu Article ${index + 1}"
            else -> "Zhihu Question ${index + 1}"
        }
        return FeedThread(
            id = id,
            title = title,
            content = url,
            author = User("zhihu", "知乎", "questionmark.bubble.fill"),
            community = community,
            timeAgo = "",
            likeCount = 0,
            commentCount = 0,
        )
    }

    fun normalizedAvatarUrl(url: String?, template: String? = null): String {
        val candidate = url?.takeIf { it.isNotBlank() } ?: template.orEmpty()
        if (candidate.isBlank()) return ""
        return candidate
            .replace("{size}", "80")
            .replace("_80.", "_80.")
            .replace("&amp;", "&")
            .let { if (it.startsWith("//")) "https:$it" else it }
    }
}
