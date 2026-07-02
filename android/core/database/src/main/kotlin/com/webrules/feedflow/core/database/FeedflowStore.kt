package com.webrules.feedflow.core.database

import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.security.AesGcmSecretStore
import com.webrules.feedflow.core.security.SecretStore
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class UrlBookmark(val url: String, val title: String, val timestamp: Long)

data class RssFeedSubscription(
    val url: String,
    val title: String,
    val isDefault: Boolean,
    val createdAt: Long,
)

data class CachedThreadDetail(val thread: FeedThread, val comments: List<Comment>)

interface FeedflowStore {
    fun saveSetting(key: String, value: String)
    fun getSetting(key: String): String?
    fun removeSetting(key: String)
    fun saveEncryptedSetting(key: String, value: String)
    fun getEncryptedSetting(key: String): String?
    fun saveCookies(siteId: String, cookies: List<FeedflowCookie>)
    fun replaceCookies(siteId: String, cookies: List<FeedflowCookie>)
    fun getCookies(siteId: String): List<FeedflowCookie>?
    fun clearCookies(siteId: String)
    fun hasCookies(siteId: String): Boolean
    fun toggleBookmark(thread: FeedThread, serviceId: String)
    fun isBookmarked(threadId: String, serviceId: String): Boolean
    fun getBookmarkedThreads(): List<Pair<FeedThread, String>>
    fun saveUrlBookmark(url: String, title: String)
    fun removeUrlBookmark(url: String)
    fun isUrlBookmarked(url: String): Boolean
    fun getUrlBookmarks(): List<Pair<String, String>>
    fun saveSummary(threadId: String, serviceId: String = "", summary: String)
    fun getSummary(threadId: String, serviceId: String = ""): String?
    fun getSummaryIfFresh(threadId: String, serviceId: String = "", maxAgeSeconds: Long): String?
    fun saveCommunities(communities: List<Community>, serviceId: String)
    fun getCommunities(serviceId: String): List<Community>
    fun saveCachedTopics(cacheKey: String, topics: List<FeedThread>)
    fun getCachedTopics(cacheKey: String): List<FeedThread>?
    fun clearCachedTopicsForService(serviceId: String)
    fun saveCachedThread(threadId: String, serviceId: String = "", thread: FeedThread, comments: List<Comment>)
    fun getCachedThread(threadId: String, serviceId: String = ""): Pair<FeedThread, List<Comment>>?
    fun addFilteredPost(postId: String, serviceId: String)
    fun isPostFiltered(postId: String, serviceId: String): Boolean
    fun getFilteredPostIds(serviceId: String): Set<String>
    fun replaceRssFeeds(feeds: List<RssFeedSubscription>)
    fun getRssFeeds(): List<RssFeedSubscription>
}

class InMemoryFeedflowStore(
    private val secretStore: SecretStore = AesGcmSecretStore(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : FeedflowStore {
    private val settings = linkedMapOf<String, String>()
    private val cookies = linkedMapOf<String, List<FeedflowCookie>>()
    private val bookmarks = linkedMapOf<Pair<String, String>, FeedThread>()
    private val urlBookmarks = linkedMapOf<String, String>()
    private val summaries = linkedMapOf<Pair<String, String>, Pair<String, Long>>()
    private val communities = linkedMapOf<String, List<Community>>()
    private val cachedTopics = linkedMapOf<String, List<FeedThread>>()
    private val cachedThreads = linkedMapOf<Pair<String, String>, Pair<FeedThread, List<Comment>>>()
    private val filteredPosts = linkedMapOf<String, MutableSet<String>>()
    private val rssFeeds = linkedMapOf<String, RssFeedSubscription>()

    override fun saveSetting(key: String, value: String) {
        settings[key] = value
    }

    override fun getSetting(key: String): String? = settings[key]

    override fun removeSetting(key: String) {
        settings.remove(key)
    }

    override fun saveEncryptedSetting(key: String, value: String) {
        settings[key] = secretStore.encrypt(value)
    }

    override fun getEncryptedSetting(key: String): String? {
        val stored = settings[key] ?: return null
        return secretStore.decrypt(stored) ?: stored.also { saveEncryptedSetting(key, it) }
    }

    override fun saveCookies(siteId: String, cookies: List<FeedflowCookie>) {
        val merged = getCookies(siteId).orEmpty()
            .associateBy { CookieIdentity(it.name, it.domain, it.path) }
            .toMutableMap()
        cookies.forEach { merged[CookieIdentity(it.name, it.domain, it.path)] = it }
        replaceCookies(siteId, merged.values.toList())
    }

    override fun replaceCookies(siteId: String, cookies: List<FeedflowCookie>) {
        this.cookies[siteId] = cookies
    }

    override fun getCookies(siteId: String): List<FeedflowCookie>? =
        cookies[siteId]?.filter { cookie ->
            val expiresAt = cookie.expiresAtMillis
            expiresAt == null || expiresAt >= clockMillis()
        }

    override fun clearCookies(siteId: String) {
        cookies.remove(siteId)
    }

    override fun hasCookies(siteId: String): Boolean = cookies[siteId]?.isNotEmpty() == true

    override fun toggleBookmark(thread: FeedThread, serviceId: String) {
        val key = thread.id to serviceId
        if (bookmarks.containsKey(key)) bookmarks.remove(key) else bookmarks[key] = thread
    }

    override fun isBookmarked(threadId: String, serviceId: String): Boolean = bookmarks.containsKey(threadId to serviceId)

    override fun getBookmarkedThreads(): List<Pair<FeedThread, String>> = bookmarks.map { (key, thread) -> thread to key.second }

    override fun saveUrlBookmark(url: String, title: String) {
        urlBookmarks[url] = title
    }

    override fun removeUrlBookmark(url: String) {
        urlBookmarks.remove(url)
    }

    override fun isUrlBookmarked(url: String): Boolean = urlBookmarks.containsKey(url)

    override fun getUrlBookmarks(): List<Pair<String, String>> = urlBookmarks.map { it.key to it.value }

    override fun saveSummary(threadId: String, serviceId: String, summary: String) {
        summaries[threadId to serviceId] = summary to clockMillis()
    }

    override fun getSummary(threadId: String, serviceId: String): String? = summaries[threadId to serviceId]?.first

    override fun getSummaryIfFresh(threadId: String, serviceId: String, maxAgeSeconds: Long): String? {
        val (summary, createdAt) = summaries[threadId to serviceId] ?: return null
        val ageSeconds = (clockMillis() - createdAt) / 1000
        return summary.takeIf { ageSeconds < maxAgeSeconds }
    }

    override fun saveCommunities(communities: List<Community>, serviceId: String) {
        this.communities[serviceId] = communities
    }

    override fun getCommunities(serviceId: String): List<Community> = communities[serviceId].orEmpty()

    override fun saveCachedTopics(cacheKey: String, topics: List<FeedThread>) {
        cachedTopics[cacheKey] = topics
    }

    override fun getCachedTopics(cacheKey: String): List<FeedThread>? = cachedTopics[cacheKey]

    override fun clearCachedTopicsForService(serviceId: String) {
        cachedTopics.keys.filter { it.startsWith("${serviceId}_") }.forEach(cachedTopics::remove)
    }

    override fun saveCachedThread(threadId: String, serviceId: String, thread: FeedThread, comments: List<Comment>) {
        cachedThreads[threadId to serviceId] = thread to comments
    }

    override fun getCachedThread(threadId: String, serviceId: String): Pair<FeedThread, List<Comment>>? =
        cachedThreads[threadId to serviceId]

    override fun addFilteredPost(postId: String, serviceId: String) {
        filteredPosts.getOrPut(serviceId) { linkedSetOf() }.add(postId)
    }

    override fun isPostFiltered(postId: String, serviceId: String): Boolean = filteredPosts[serviceId]?.contains(postId) == true

    override fun getFilteredPostIds(serviceId: String): Set<String> = filteredPosts[serviceId].orEmpty()

    override fun replaceRssFeeds(feeds: List<RssFeedSubscription>) {
        rssFeeds.clear()
        feeds.forEach { rssFeeds[it.url] = it }
    }

    override fun getRssFeeds(): List<RssFeedSubscription> = rssFeeds.values.toList()
}

private data class CookieIdentity(val name: String, val domain: String, val path: String)

object DatabaseSchemaMigration {
    fun needsCompositePrimaryKeyMigration(currentPrimaryKey: List<String>, expectedPrimaryKey: List<String>): Boolean =
        currentPrimaryKey != expectedPrimaryKey
}

object FeedflowDatabaseContract {
    const val databaseName = "Feedflow.sqlite"
    const val settingsTable = "settings"
    const val cookieSettingPrefix = "login_"
    const val cookieSettingSuffix = "_cookies"
    const val geminiApiKey = "gemini_api_key"

    val schemaStatements = listOf(
        """
        CREATE TABLE IF NOT EXISTS communities(
            id TEXT,
            name TEXT,
            description TEXT,
            category TEXT,
            activeToday INTEGER,
            onlineNow INTEGER,
            serviceId TEXT,
            PRIMARY KEY (id, serviceId)
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS settings(
            key TEXT PRIMARY KEY,
            value TEXT
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS filtered_posts(
            postId TEXT PRIMARY KEY,
            serviceId TEXT,
            filteredAt INTEGER
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS ai_summaries(
            thread_id TEXT,
            service_id TEXT DEFAULT '',
            summary TEXT,
            created_at INTEGER,
            PRIMARY KEY (thread_id, service_id)
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS cached_topics(
            cache_key TEXT PRIMARY KEY,
            data TEXT,
            timestamp INTEGER
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS cached_threads(
            thread_id TEXT,
            service_id TEXT DEFAULT '',
            data TEXT,
            timestamp INTEGER,
            PRIMARY KEY (thread_id, service_id)
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS bookmarks(
            thread_id TEXT,
            service_id TEXT,
            data TEXT,
            timestamp INTEGER,
            PRIMARY KEY (thread_id, service_id)
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS url_bookmarks(
            url TEXT PRIMARY KEY,
            title TEXT,
            timestamp INTEGER
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS rss_feeds(
            url TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            isDefault INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        );
        """.trimIndent(),
    )

    fun cookieSettingKey(siteId: String): String = "$cookieSettingPrefix${siteId}$cookieSettingSuffix"
}

object FeedflowCacheKeys {
    fun topicList(serviceId: String, communityId: String, page: Int = 1): String =
        "${serviceId}_${communityId}_page$page"

    fun threadDetail(serviceId: String, threadId: String): Pair<String, String> =
        threadId to serviceId

    fun summary(threadId: String, serviceId: String, language: String): Pair<String, String> =
        "${threadId}_${serviceId}_${language}" to serviceId
}

object FeedflowPersistenceCodecs {
    private const val fieldSeparator = "\u001F"
    private const val recordSeparator = "\u001E"

    fun encodeCommunity(community: Community): String = encodeFields(
        community.id,
        community.name,
        community.description,
        community.category,
        community.activeToday.toString(),
        community.onlineNow.toString(),
    )

    fun decodeCommunity(value: String): Community {
        val fields = decodeFields(value)
        return Community(
            id = fields.getOrElse(0) { "" },
            name = fields.getOrElse(1) { "" },
            description = fields.getOrElse(2) { "" },
            category = fields.getOrElse(3) { "" },
            activeToday = fields.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
            onlineNow = fields.getOrElse(5) { "0" }.toIntOrNull() ?: 0,
        )
    }

    fun encodeThread(thread: FeedThread): String = encodeFields(
        thread.id,
        thread.title,
        thread.content,
        encodeUser(thread.author),
        encodeCommunity(thread.community),
        thread.timeAgo,
        thread.likeCount.toString(),
        thread.commentCount.toString(),
        thread.isLiked.toString(),
        thread.tags.orEmpty().joinToString(recordSeparator, transform = ::encodeComponent),
        thread.lastPostTime.orEmpty(),
        thread.lastPosterName.orEmpty(),
    )

    fun decodeThread(value: String): FeedThread {
        val fields = decodeFields(value)
        return FeedThread(
            id = fields.getOrElse(0) { "" },
            title = fields.getOrElse(1) { "" },
            content = fields.getOrElse(2) { "" },
            author = decodeUser(fields.getOrElse(3) { "" }),
            community = decodeCommunity(fields.getOrElse(4) { "" }),
            timeAgo = fields.getOrElse(5) { "" },
            likeCount = fields.getOrElse(6) { "0" }.toIntOrNull() ?: 0,
            commentCount = fields.getOrElse(7) { "0" }.toIntOrNull() ?: 0,
            isLiked = fields.getOrElse(8) { "false" }.toBooleanStrictOrNull() ?: false,
            tags = fields.getOrElse(9) { "" }.takeIf { it.isNotBlank() }?.split(recordSeparator)?.map(::decodeComponent),
            lastPostTime = fields.getOrElse(10) { "" }.ifBlank { null },
            lastPosterName = fields.getOrElse(11) { "" }.ifBlank { null },
        )
    }

    fun encodeComment(comment: Comment): String = encodeFields(
        comment.id,
        encodeUser(comment.author),
        comment.content,
        comment.timeAgo,
        comment.likeCount.toString(),
        encodeComments(comment.replies.orEmpty()),
    )

    fun decodeComment(value: String): Comment {
        val fields = decodeFields(value)
        return Comment(
            id = fields.getOrElse(0) { "" },
            author = decodeUser(fields.getOrElse(1) { "" }),
            content = fields.getOrElse(2) { "" },
            timeAgo = fields.getOrElse(3) { "" },
            likeCount = fields.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
            replies = decodeComments(fields.getOrElse(5) { "" }).takeIf { it.isNotEmpty() },
        )
    }

    fun encodeThreads(threads: List<FeedThread>): String =
        threads.joinToString(recordSeparator) { encodeComponent(encodeThread(it)) }

    fun decodeThreads(value: String): List<FeedThread> =
        value.takeIf { it.isNotBlank() }?.split(recordSeparator)?.map { decodeThread(decodeComponent(it)) }.orEmpty()

    fun encodeCachedThread(thread: FeedThread, comments: List<Comment>): String =
        encodeFields(encodeThread(thread), encodeComments(comments))

    fun decodeCachedThread(value: String): CachedThreadDetail {
        val fields = decodeFields(value)
        return CachedThreadDetail(
            thread = decodeThread(fields.getOrElse(0) { "" }),
            comments = decodeComments(fields.getOrElse(1) { "" }),
        )
    }

    fun encodeComments(comments: List<Comment>): String =
        comments.joinToString(recordSeparator) { encodeComponent(encodeComment(it)) }

    fun decodeComments(value: String): List<Comment> =
        value.takeIf { it.isNotBlank() }?.split(recordSeparator)?.map { decodeComment(decodeComponent(it)) }.orEmpty()

    fun encodeCookies(cookies: List<FeedflowCookie>): String =
        cookies.joinToString(prefix = "[", postfix = "]") { cookie ->
            buildString {
                append("{\"name\":\"").append(jsonEscape(cookie.name)).append('"')
                append(",\"value\":\"").append(jsonEscape(cookie.value)).append('"')
                append(",\"domain\":\"").append(jsonEscape(cookie.domain)).append('"')
                append(",\"path\":\"").append(jsonEscape(cookie.path)).append('"')
                append(",\"secure\":").append(cookie.secure)
                append(",\"httpOnly\":").append(cookie.httpOnly)
                cookie.expiresAtMillis?.let { append(",\"expires\":").append(String.format(Locale.US, "%.1f", it / 1000.0)) }
                append('}')
            }
        }

    fun decodeCookies(value: String, nowMillis: Long = System.currentTimeMillis()): List<FeedflowCookie> =
        if (value.trim().startsWith("[")) {
            Regex("""\{[^{}]*}""").findAll(value).mapNotNull { match ->
                val json = match.value
                val expiresAt = jsonNumber(json, "expires")?.times(1000)?.toLong()
                if (expiresAt != null && expiresAt < nowMillis) {
                    null
                } else {
                    FeedflowCookie(
                        name = jsonString(json, "name").orEmpty(),
                        value = jsonString(json, "value").orEmpty(),
                        domain = jsonString(json, "domain").orEmpty(),
                        path = jsonString(json, "path").orEmpty().ifBlank { "/" },
                        expiresAtMillis = expiresAt,
                        secure = jsonBool(json, "secure"),
                        httpOnly = jsonBool(json, "httpOnly"),
                    )
                }
            }.toList()
        } else {
            decodeLegacyCookies(value).filter { cookie ->
                val expiresAt = cookie.expiresAtMillis
                expiresAt == null || expiresAt >= nowMillis
            }
        }

    private fun decodeLegacyCookies(value: String): List<FeedflowCookie> =
        value.takeIf { it.isNotBlank() }?.split(recordSeparator)?.map {
            val fields = decodeFields(it)
            FeedflowCookie(
                name = fields.getOrElse(0) { "" },
                value = fields.getOrElse(1) { "" },
                domain = fields.getOrElse(2) { "" },
                path = fields.getOrElse(3) { "/" }.ifBlank { "/" },
                expiresAtMillis = fields.getOrElse(4) { "" }.toLongOrNull(),
                secure = fields.getOrElse(5) { "false" }.toBooleanStrictOrNull() ?: false,
                httpOnly = fields.getOrElse(6) { "false" }.toBooleanStrictOrNull() ?: false,
            )
        }.orEmpty()

    private fun encodeUser(user: User): String = encodeFields(user.id, user.username, user.avatar, user.role.orEmpty())

    private fun decodeUser(value: String): User {
        val fields = decodeFields(value)
        return User(
            id = fields.getOrElse(0) { "" },
            username = fields.getOrElse(1) { "" },
            avatar = fields.getOrElse(2) { "" },
            role = fields.getOrElse(3) { "" }.ifBlank { null },
        )
    }

    private fun encodeFields(vararg fields: String): String =
        fields.joinToString(fieldSeparator, transform = ::encodeComponent)

    private fun decodeFields(value: String): List<String> =
        value.split(fieldSeparator).map(::decodeComponent)

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decodeComponent(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun jsonEscape(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private fun jsonUnescape(value: String): String =
        value.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

    private fun jsonString(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json)?.groupValues?.get(1)?.let(::jsonUnescape)

    private fun jsonBool(json: String, key: String): Boolean =
        Regex(""""$key"\s*:\s*true""").containsMatchIn(json)

    private fun jsonNumber(json: String, key: String): Double? =
        Regex(""""$key"\s*:\s*([0-9]+(?:\.[0-9]+)?)""").find(json)?.groupValues?.get(1)?.toDoubleOrNull()
}
