package com.webrules.feedflow.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.FeedflowDatabaseContract
import com.webrules.feedflow.core.database.FeedflowPersistenceCodecs
import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.database.DatabaseSchemaMigration
import com.webrules.feedflow.core.database.RssFeedSubscription
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.core.security.SecretStore

class AndroidSqliteFeedflowStore(
    context: Context,
    private val secretStore: SecretStore = KeystoreSecretStore(context),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : FeedflowStore {
    private val helper = FeedflowSqliteOpenHelper(context.applicationContext)

    override fun saveSetting(key: String, value: String) {
        helper.writableDatabase.insertWithOnConflict(
            FeedflowDatabaseContract.settingsTable,
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun getSetting(key: String): String? = helper.readableDatabase.querySingle(
        table = FeedflowDatabaseContract.settingsTable,
        columns = arrayOf("value"),
        selection = "key = ?",
        selectionArgs = arrayOf(key),
    )

    override fun removeSetting(key: String) {
        helper.writableDatabase.delete(FeedflowDatabaseContract.settingsTable, "key = ?", arrayOf(key))
    }

    override fun saveEncryptedSetting(key: String, value: String) {
        saveSetting(key, secretStore.encrypt(value))
    }

    override fun getEncryptedSetting(key: String): String? {
        val stored = getSetting(key) ?: return null
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
        saveEncryptedSetting(
            FeedflowDatabaseContract.cookieSettingKey(siteId),
            FeedflowPersistenceCodecs.encodeCookies(cookies),
        )
    }

    override fun getCookies(siteId: String): List<FeedflowCookie>? =
        getEncryptedSetting(FeedflowDatabaseContract.cookieSettingKey(siteId))
            ?.let { FeedflowPersistenceCodecs.decodeCookies(it, clockMillis()) }

    override fun clearCookies(siteId: String) {
        removeSetting(FeedflowDatabaseContract.cookieSettingKey(siteId))
    }

    override fun hasCookies(siteId: String): Boolean = getCookies(siteId)?.isNotEmpty() == true

    override fun toggleBookmark(thread: FeedThread, serviceId: String) {
        if (isBookmarked(thread.id, serviceId)) {
            helper.writableDatabase.delete("bookmarks", "thread_id = ? AND service_id = ?", arrayOf(thread.id, serviceId))
        } else {
            helper.writableDatabase.insertWithOnConflict(
                "bookmarks",
                null,
                ContentValues().apply {
                    put("thread_id", thread.id)
                    put("service_id", serviceId)
                    put("data", FeedflowPersistenceCodecs.encodeThread(thread))
                    put("timestamp", clockMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    override fun isBookmarked(threadId: String, serviceId: String): Boolean =
        helper.readableDatabase.querySingle(
            table = "bookmarks",
            columns = arrayOf("thread_id"),
            selection = "thread_id = ? AND service_id = ?",
            selectionArgs = arrayOf(threadId, serviceId),
        ) != null

    override fun getBookmarkedThreads(): List<Pair<FeedThread, String>> =
        helper.readableDatabase.queryRows(
            sql = "SELECT data, service_id FROM bookmarks ORDER BY timestamp DESC",
            selectionArgs = emptyArray(),
        ) { row ->
            FeedflowPersistenceCodecs.decodeThread(row[0]) to row[1]
        }

    override fun saveUrlBookmark(url: String, title: String) {
        helper.writableDatabase.insertWithOnConflict(
            "url_bookmarks",
            null,
            ContentValues().apply {
                put("url", url)
                put("title", title)
                put("timestamp", clockMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun removeUrlBookmark(url: String) {
        helper.writableDatabase.delete("url_bookmarks", "url = ?", arrayOf(url))
    }

    override fun isUrlBookmarked(url: String): Boolean =
        helper.readableDatabase.querySingle("url_bookmarks", arrayOf("url"), "url = ?", arrayOf(url)) != null

    override fun getUrlBookmarks(): List<Pair<String, String>> =
        helper.readableDatabase.queryRows(
            sql = "SELECT url, title FROM url_bookmarks ORDER BY timestamp DESC",
            selectionArgs = emptyArray(),
        ) { row -> row[0] to row[1] }

    override fun saveSummary(threadId: String, serviceId: String, summary: String) {
        helper.writableDatabase.insertWithOnConflict(
            "ai_summaries",
            null,
            ContentValues().apply {
                put("thread_id", threadId)
                put("service_id", serviceId)
                put("summary", summary)
                put("created_at", clockMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun getSummary(threadId: String, serviceId: String): String? =
        summaryRow(threadId, serviceId)?.first

    override fun getSummaryIfFresh(threadId: String, serviceId: String, maxAgeSeconds: Long): String? {
        val (summary, createdAt) = summaryRow(threadId, serviceId) ?: return null
        return summary.takeIf { (clockMillis() - createdAt) / 1000 < maxAgeSeconds }
    }

    override fun saveCommunities(communities: List<Community>, serviceId: String) {
        helper.writableDatabase.inTransaction {
            delete("communities", "serviceId = ?", arrayOf(serviceId))
            communities.forEach { community ->
                insertWithOnConflict(
                    "communities",
                    null,
                    ContentValues().apply {
                        put("id", community.id)
                        put("name", community.name)
                        put("description", community.description)
                        put("category", community.category)
                        put("activeToday", community.activeToday)
                        put("onlineNow", community.onlineNow)
                        put("serviceId", serviceId)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    override fun getCommunities(serviceId: String): List<Community> =
        helper.readableDatabase.queryRows(
            sql = "SELECT id, name, description, category, activeToday, onlineNow FROM communities WHERE serviceId = ? ORDER BY name COLLATE NOCASE",
            selectionArgs = arrayOf(serviceId),
        ) { row ->
            Community(
                id = row[0],
                name = row[1],
                description = row[2],
                category = row[3],
                activeToday = row[4].toIntOrNull() ?: 0,
                onlineNow = row[5].toIntOrNull() ?: 0,
            )
        }

    override fun saveCachedTopics(cacheKey: String, topics: List<FeedThread>) {
        helper.writableDatabase.insertWithOnConflict(
            "cached_topics",
            null,
            ContentValues().apply {
                put("cache_key", cacheKey)
                put("data", FeedflowPersistenceCodecs.encodeThreads(topics))
                put("timestamp", clockMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun getCachedTopics(cacheKey: String): List<FeedThread>? =
        helper.readableDatabase.querySingle("cached_topics", arrayOf("data"), "cache_key = ?", arrayOf(cacheKey))
            ?.let(FeedflowPersistenceCodecs::decodeThreads)

    override fun clearCachedTopicsForService(serviceId: String) {
        helper.writableDatabase.delete("cached_topics", "cache_key LIKE ?", arrayOf("${serviceId}_%"))
    }

    override fun saveCachedThread(threadId: String, serviceId: String, thread: FeedThread, comments: List<Comment>) {
        helper.writableDatabase.insertWithOnConflict(
            "cached_threads",
            null,
            ContentValues().apply {
                put("thread_id", threadId)
                put("service_id", serviceId)
                put("data", FeedflowPersistenceCodecs.encodeCachedThread(thread, comments))
                put("timestamp", clockMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private data class CookieIdentity(val name: String, val domain: String, val path: String)

    override fun getCachedThread(threadId: String, serviceId: String): Pair<FeedThread, List<Comment>>? =
        helper.readableDatabase.querySingle(
            table = "cached_threads",
            columns = arrayOf("data"),
            selection = "thread_id = ? AND service_id = ?",
            selectionArgs = arrayOf(threadId, serviceId),
        )?.let {
            val cached = FeedflowPersistenceCodecs.decodeCachedThread(it)
            cached.thread to cached.comments
        }

    override fun addFilteredPost(postId: String, serviceId: String) {
        helper.writableDatabase.insertWithOnConflict(
            "filtered_posts",
            null,
            ContentValues().apply {
                put("postId", postId)
                put("serviceId", serviceId)
                put("filteredAt", clockMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun isPostFiltered(postId: String, serviceId: String): Boolean =
        helper.readableDatabase.querySingle(
            "filtered_posts",
            arrayOf("postId"),
            "postId = ? AND serviceId = ?",
            arrayOf(postId, serviceId),
        ) != null

    override fun getFilteredPostIds(serviceId: String): Set<String> =
        helper.readableDatabase.queryRows(
            sql = "SELECT postId FROM filtered_posts WHERE serviceId = ?",
            selectionArgs = arrayOf(serviceId),
        ) { row -> row[0] }.toSet()

    override fun replaceRssFeeds(feeds: List<RssFeedSubscription>) {
        helper.writableDatabase.inTransaction {
            delete("rss_feeds", null, null)
            feeds.forEach { feed ->
                insertWithOnConflict(
                    "rss_feeds",
                    null,
                    ContentValues().apply {
                        put("url", feed.url)
                        put("title", feed.title)
                        put("isDefault", if (feed.isDefault) 1 else 0)
                        put("created_at", feed.createdAt)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    override fun getRssFeeds(): List<RssFeedSubscription> =
        helper.readableDatabase.queryRows(
            sql = "SELECT url, title, isDefault, created_at FROM rss_feeds ORDER BY isDefault DESC, title COLLATE NOCASE",
            selectionArgs = emptyArray(),
        ) { row ->
            RssFeedSubscription(
                url = row[0],
                title = row[1],
                isDefault = row[2] == "1",
                createdAt = row[3].toLongOrNull() ?: 0L,
            )
        }

    private fun summaryRow(threadId: String, serviceId: String): Pair<String, Long>? =
        helper.readableDatabase.queryRows(
            sql = "SELECT summary, created_at FROM ai_summaries WHERE thread_id = ? AND service_id = ?",
            selectionArgs = arrayOf(threadId, serviceId),
        ) { row -> row[0] to (row[1].toLongOrNull() ?: 0L) }.firstOrNull()
}

private class FeedflowSqliteOpenHelper(context: Context) : SQLiteOpenHelper(
    context,
    FeedflowDatabaseContract.databaseName,
    null,
    FeedflowDatabaseContract.databaseVersion,
) {
    override fun onCreate(db: SQLiteDatabase) {
        FeedflowDatabaseContract.schemaStatements.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DatabaseSchemaMigration.statementsForUpgrade(oldVersion, newVersion).forEach(db::execSQL)
        FeedflowDatabaseContract.schemaStatements.forEach(db::execSQL)
    }
}

private fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

private fun SQLiteDatabase.querySingle(
    table: String,
    columns: Array<String>,
    selection: String,
    selectionArgs: Array<String>,
): String? {
    val cursor = query(table, columns, selection, selectionArgs, null, null, null, "1")
    return try {
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } finally {
        cursor.close()
    }
}

private fun <T> SQLiteDatabase.queryRows(
    sql: String,
    selectionArgs: Array<String>,
    map: (List<String>) -> T,
): List<T> {
    val cursor = rawQuery(sql, selectionArgs)
    return try {
        buildList {
            while (cursor.moveToNext()) {
                add(map((0 until cursor.columnCount).map { index -> cursor.getString(index).orEmpty() }))
            }
        }
    } finally {
        cursor.close()
    }
}
