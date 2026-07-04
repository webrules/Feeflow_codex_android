package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.FeedflowCacheKeys
import com.webrules.feedflow.core.database.FeedflowStore
import com.webrules.feedflow.core.database.InMemoryFeedflowStore
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.model.User
import com.webrules.feedflow.core.network.FeedflowHttpClient
import com.webrules.feedflow.core.network.HttpStatusException
import com.webrules.feedflow.core.network.UrlConnectionFeedflowHttpClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class CacheFirstResult<T>(
    val cached: T?,
    val fresh: T?,
    val warning: FeedflowError? = null,
) {
    val best: T?
        get() = fresh ?: cached
}

class FeedflowRepository(
    private val store: FeedflowStore = InMemoryFeedflowStore(),
    private val httpClient: FeedflowHttpClient = UrlConnectionFeedflowHttpClient(),
    serviceFactory: ((ForumSite) -> ForumService)? = null,
    private val summaryClient: GeminiSummaryClient = GeminiRestSummaryClient(httpClient),
    private val prefetchDelay: suspend (Long) -> Unit = { delay(it) },
) {
    private val summaryCoordinator = AiSummaryCoordinator(store)
    private var zhihuService: ForumService? = null
    private val serviceFactory: (ForumSite) -> ForumService = serviceFactory ?: { site ->
        if (site == ForumSite.Zhihu) {
            synchronized(this) {
                zhihuService ?: site.makeService(store, httpClient).also { zhihuService = it }
            }
        } else {
            site.makeService(store, httpClient)
        }
    }

    fun sites(): List<ForumSite> = ForumSite.entries

    fun webUrl(site: ForumSite, thread: FeedThread): String =
        serviceFactory(site).getWebUrl(thread)

    fun canDeleteThread(site: ForumSite, thread: FeedThread): Boolean =
        serviceFactory(site).canDeleteThread(thread)

    suspend fun loadCommunities(site: ForumSite): CacheFirstResult<List<Community>> {
        val cached = store.getCommunities(site.serviceId).takeIf { it.isNotEmpty() }
        return runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).fetchCategories()
            }.also { store.saveCommunities(it, site.serviceId) }
        }.fold(
            onSuccess = { CacheFirstResult(cached = cached, fresh = it) },
            onFailure = { CacheFirstResult(cached = cached, fresh = null, warning = it.toFeedflowError(site.serviceId, "fetchCategories")) },
        )
    }

    suspend fun loadThreads(site: ForumSite, community: Community, page: Int = 1): CacheFirstResult<List<FeedThread>> {
        val cacheKey = FeedflowCacheKeys.topicList(site.serviceId, community.id, page)
        val cached = store.getCachedTopics(cacheKey)
        return runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).fetchCategoryThreads(community.id, store.getCommunities(site.serviceId).ifEmpty { listOf(community) }, page)
            }.also { if (page == 1 || it.isNotEmpty()) store.saveCachedTopics(cacheKey, it) }
        }.fold(
            onSuccess = { fresh ->
                if (fresh.isEmpty() && shouldPreserveOldThreadsOnEmptyRefresh(site, community)) {
                    CacheFirstResult(cached = cached, fresh = cached)
                } else {
                    CacheFirstResult(cached = cached, fresh = fresh)
                }
            },
            onFailure = { CacheFirstResult(cached = cached, fresh = null, warning = it.toFeedflowError(site.serviceId, "fetchCategoryThreads")) },
        )
    }

    suspend fun loadThreadDetail(site: ForumSite, thread: FeedThread, page: Int = 1): CacheFirstResult<Pair<FeedThread, List<Comment>>> {
        val cached = store.getCachedThread(thread.id, site.serviceId)?.takeUnless {
            site == ForumSite.FourD4Y && it.first.content.contains("Could not parse content.", ignoreCase = true)
        }
        return runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).fetchThreadDetail(thread.id, page)
            }.let { detail ->
                store.saveCachedThread(thread.id, site.serviceId, detail.thread, detail.comments)
                detail.thread to detail.comments
            }
        }.fold(
            onSuccess = { CacheFirstResult(cached = cached, fresh = it) },
            onFailure = { CacheFirstResult(cached = cached, fresh = null, warning = it.toFeedflowError(site.serviceId, "fetchThreadDetail")) },
        )
    }

    suspend fun loadMoreComments(site: ForumSite, thread: FeedThread, page: Int): List<Comment> =
        runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).fetchThreadDetail(thread.id, page).comments
            }
        }.getOrDefault(emptyList())

    fun supportsCommentPagination(site: ForumSite): Boolean = site == ForumSite.FourD4Y

    fun isThreadDetailCached(site: ForumSite, thread: FeedThread): Boolean =
        store.getCachedThread(thread.id, site.serviceId) != null

    suspend fun prefetchThreadDetails(
        site: ForumSite,
        threads: List<FeedThread>,
        enabled: Boolean,
        isWifi: Boolean,
        maxQueueSize: Int = BackgroundPrefetchPolicy.maxQueueSize,
    ) {
        val queue = threads
            .distinctBy { it.id }
            .filter { thread ->
                PrefetchGate.shouldPrefetch(
                    PrefetchDecisionInput(
                        enabled = enabled,
                        isWifi = isWifi,
                        site = site,
                        detailCached = isThreadDetailCached(site, thread),
                        queueSize = 0,
                        maxQueueSize = maxQueueSize,
                    ),
                )
            }
            .take(maxQueueSize)
        if (queue.isEmpty()) return

        prefetchDelay(BackgroundPrefetchPolicy.debounceMillis)
        var queued = 0
        for ((index, thread) in queue.withIndex()) {
            val decision = PrefetchDecisionInput(
                enabled = enabled,
                isWifi = isWifi,
                site = site,
                detailCached = isThreadDetailCached(site, thread),
                queueSize = queued,
                maxQueueSize = maxQueueSize,
            )
            if (!PrefetchGate.shouldPrefetch(decision)) continue
            loadThreadDetail(site, thread)
            queued++
            if (queued >= maxQueueSize) break
            if (index < queue.lastIndex) {
                prefetchDelay(BackgroundPrefetchPolicy.interItemDelayMillis)
            }
        }
    }

    suspend fun searchThreads(site: ForumSite, query: String, page: Int = 1): CacheFirstResult<SearchResult> =
        runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).searchThreads(query, page)
            }
        }.fold(
            onSuccess = { CacheFirstResult(cached = null, fresh = it) },
            onFailure = { CacheFirstResult(cached = null, fresh = null, warning = it.toFeedflowError(site.serviceId, "searchThreads")) },
        )

    suspend fun createThread(site: ForumSite, community: Community, title: String, content: String): FeedflowError? =
        runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).createThread(community.id, title, content)
            }
        }.fold(
            onSuccess = { null },
            onFailure = { it.toFeedflowError(site.serviceId, "createThread") },
        )

    suspend fun postComment(site: ForumSite, thread: FeedThread, content: String): FeedflowError? =
        runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).postComment(thread.id, thread.community.id, content)
            }
        }.fold(
            onSuccess = { null },
            onFailure = { it.toFeedflowError(site.serviceId, "postComment") },
        )

    suspend fun deleteThread(site: ForumSite, thread: FeedThread): FeedflowError? =
        runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).deleteThread(thread.id, thread.community.id)
            }
        }.fold(
            onSuccess = {
                store.addFilteredPost(thread.id, site.serviceId)
                null
            },
            onFailure = { it.toFeedflowError(site.serviceId, "deleteThread") },
        )

    suspend fun markThreadRead(site: ForumSite, thread: FeedThread): FeedflowError? =
        runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).markThreadRead(thread)
            }
        }.fold(
            onSuccess = { null },
            onFailure = { it.toFeedflowError(site.serviceId, "markThreadRead") },
        )

    suspend fun markThreadNotInterested(site: ForumSite, thread: FeedThread): FeedflowError? =
        runCatching {
            withContext(Dispatchers.IO) {
                serviceFactory(site).markThreadNotInterested(thread)
            }
        }.fold(
            onSuccess = { null },
            onFailure = { it.toFeedflowError(site.serviceId, "markThreadNotInterested") },
        )

    suspend fun summarizeThread(
        apiKey: String,
        site: ForumSite,
        thread: FeedThread,
        comments: List<Comment>,
        language: String,
        forceRefresh: Boolean = false,
    ): AiSummaryUiState =
        if (apiKey.isBlank()) {
            AiSummaryUiState(errorMessage = "check_api_key")
        } else {
            summaryCoordinator.summarizeAsync(
                thread = thread,
                serviceId = site.serviceId,
                language = language,
                comments = comments,
                forceRefresh = forceRefresh,
            ) { prompt ->
                withContext(Dispatchers.IO) {
                    summaryClient.generateSummary(apiKey, prompt)
                }
            }
        }

    suspend fun summarizeDailyRss(apiKey: String, threads: List<FeedThread>, language: String, forceRefresh: Boolean = false): AiSummaryUiState {
        val cacheKey = "daily_rss_summary"
        if (!forceRefresh) {
            store.getSummaryIfFresh(cacheKey, "", 7 * 24 * 60 * 60)?.let {
                return AiSummaryUiState(summary = it, isCached = true)
            }
        }
        if (threads.isEmpty()) return AiSummaryUiState(summary = if (language == "zh") "过去 24 小时没有更新。" else "No updates found in the last 24 hours.")
        if (apiKey.isBlank()) return AiSummaryUiState(errorMessage = "check_api_key")
        return runCatching {
            withContext(Dispatchers.IO) {
                summaryClient.generateSummary(apiKey, buildDailyRssPrompt(threads, language))
            }.also { summary ->
                store.saveSummary(cacheKey, "", summary)
            }
        }.fold(
            onSuccess = { AiSummaryUiState(summary = it) },
            onFailure = { AiSummaryUiState(errorMessage = it.message ?: "failed_summary") },
        )
    }

    suspend fun summarizeCrossSite(apiKey: String, site: ForumSite, threads: List<FeedThread>, language: String): AiSummaryUiState {
        if (threads.isEmpty()) return AiSummaryUiState(errorMessage = "No posts found")
        if (apiKey.isBlank()) return AiSummaryUiState(errorMessage = "check_api_key")
        return runCatching {
            withContext(Dispatchers.IO) {
                summaryClient.generateSummary(apiKey, buildCrossSitePrompt(site, threads, language))
            }
        }.fold(
            onSuccess = { AiSummaryUiState(summary = it) },
            onFailure = { AiSummaryUiState(errorMessage = it.message ?: "failed_summary") },
        )
    }

    private fun shouldPreserveOldThreadsOnEmptyRefresh(site: ForumSite, community: Community): Boolean =
        site == ForumSite.FourD4Y || (site == ForumSite.Zhihu && community.id == "recommend")

    private fun Throwable.toFeedflowError(serviceId: String, stage: String): FeedflowError =
        when (this) {
            is FeedflowError -> this
            is HttpStatusException -> FeedflowError.Network(url, statusCode, bodyPreview)
            is IOException -> FeedflowError.Network(stage, bodyPreview = message)
            else -> FeedflowError.Parsing(serviceId, stage, message)
        }

    private fun buildDailyRssPrompt(threads: List<FeedThread>, language: String): String =
        buildString {
            appendLine("Here are the RSS feed updates from the last 24 hours:")
            appendLine()
            threads.take(30).forEach { thread ->
                appendLine("Source: ${thread.community.name}")
                appendLine("Title: ${thread.title}")
                appendLine("Link: ${thread.id}")
                appendLine("Snippet: ${thread.content.take(500).replace('\n', ' ')}...")
                appendLine()
                appendLine("---")
                appendLine()
            }
            appendLine("Please provide a 'Daily Briefing' based on these updates.")
            appendLine("1. Summarize the key themes or topics discussed.")
            appendLine("2. Highlight the most interesting 3-5 articles with their Titles and a brief 1-sentence summary for each.")
            appendLine("3. Provide a list of all mentioned articles with their Links, grouped by Source.")
            appendLine("Format the output clearly with Markdown headers.")
            appendLine(if (language == "zh") "Respond in Simplified Chinese." else "Respond in English.")
        }

    private fun buildCrossSitePrompt(site: ForumSite, threads: List<FeedThread>, language: String): String =
        buildString {
            appendLine("Summarize the top 10 posts from ${site.displayName}.")
            appendLine(if (language == "zh") "Respond in Simplified Chinese." else "Respond in English.")
            appendLine("For each major trend, include why it matters and reference the thread titles.")
            threads.take(10).forEachIndexed { index, thread ->
                appendLine("${index + 1}. ${thread.title} (${thread.commentCount} comments, ${thread.likeCount} likes)")
                if (thread.content.isNotBlank()) appendLine(thread.content.take(300))
            }
        }

    fun defaultCommunities(site: ForumSite): List<Community> = when (site) {
        ForumSite.Rss -> listOf(
            Community("feeds", "All Feeds", "Latest items from subscribed RSS/Atom feeds", "rss", 0, 0),
        )
        ForumSite.HackerNews -> listOf(
            Community("topstories", "Top", "Top stories", "General", 0, 0),
            Community("newstories", "New", "New stories", "General", 0, 0),
            Community("beststories", "Best", "Best stories", "General", 0, 0),
            Community("showstories", "Show", "Show HN", "General", 0, 0),
            Community("askstories", "Ask", "Ask HN", "General", 0, 0),
            Community("jobstories", "Jobs", "Jobs", "General", 0, 0),
        )
        ForumSite.FourD4Y -> emptyList()
        ForumSite.V2ex -> V2exParser.tabs.map { tab -> Community(tab, tab.replaceFirstChar(Char::uppercase), "V2EX $tab topics", "V2EX", 0, 0) }
        ForumSite.LinuxDo -> listOf(Community("latest", "Latest", "LINUX DO latest conversations", "linux_do", 0, 0))
        ForumSite.Zhihu -> ZhihuParser.categories
    }
}
