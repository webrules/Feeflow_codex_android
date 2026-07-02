package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.RssFeedSubscription
import com.webrules.feedflow.core.model.Comment
import com.webrules.feedflow.core.model.Community
import com.webrules.feedflow.core.model.FeedThread
import com.webrules.feedflow.core.model.ForumSite

data class LoadableContent<T>(
    val value: T,
    val isLoading: Boolean = false,
    val warning: String? = null,
    val loadedFromCache: Boolean = false,
) {
    companion object {
        fun <T> loaded(value: T, warning: String? = null, loadedFromCache: Boolean = false) =
            LoadableContent(value = value, warning = warning, loadedFromCache = loadedFromCache)
    }
}

data class ThreadDetailContent(
    val thread: FeedThread,
    val comments: List<Comment>,
)

data class FeedflowHomeState(
    val enabledSites: Set<ForumSite> = ForumSite.entries.toSet(),
    val signedInSites: Set<ForumSite> = emptySet(),
    val geminiApiKey: String = "",
    val backgroundPrefetch: Boolean = true,
    val rssFeeds: List<RssFeedSubscription> = listOf(
        RssFeedSubscription("https://hacker-podcast.agi.li/rss.xml", "Hacker Podcast", true, 0),
        RssFeedSubscription("https://www.ruanyifeng.com/blog/atom.xml", "Ruanyifeng Blog", true, 0),
        RssFeedSubscription("https://www.oreilly.com/radar/feed/", "O'Reilly Radar", true, 0),
    ),
) {
    val visibleSites: List<ForumSite>
        get() = ForumSite.entries.filter { it == ForumSite.Rss || enabledSites.contains(it) }

    fun toggleSite(site: ForumSite): FeedflowHomeState {
        if (site == ForumSite.Rss) return this
        return copy(enabledSites = if (enabledSites.contains(site)) enabledSites - site else enabledSites + site)
    }
}

class FeedflowAppStateController(
    private val repository: FeedflowRepository,
    initialHomeState: FeedflowHomeState = FeedflowHomeState(),
) {
    var homeState: FeedflowHomeState = initialHomeState
        private set

    private val communityCache = mutableMapOf<ForumSite, LoadableContent<List<Community>>>()
    private val threadCache = mutableMapOf<String, LoadableContent<List<FeedThread>>>()
    private val detailCache = mutableMapOf<String, LoadableContent<ThreadDetailContent>>()

    fun communities(site: ForumSite): LoadableContent<List<Community>> =
        communityCache[site] ?: LoadableContent.loaded(repository.defaultCommunities(site))

    fun threads(site: ForumSite, community: Community): LoadableContent<List<FeedThread>> =
        threadCache[threadKey(site, community)] ?: LoadableContent.loaded(emptyList())

    fun webUrl(site: ForumSite, thread: FeedThread): String = repository.webUrl(site, thread)

    fun canDeleteThread(site: ForumSite, thread: FeedThread): Boolean = repository.canDeleteThread(site, thread)

    fun detail(site: ForumSite, thread: FeedThread): LoadableContent<ThreadDetailContent> =
        detailCache[detailKey(site, thread)] ?: LoadableContent.loaded(ThreadDetailContent(thread, emptyList()))

    suspend fun refreshCommunities(site: ForumSite): LoadableContent<List<Community>> {
        val result = repository.loadCommunities(site)
        return contentFromResult(
            result = result,
            fallback = repository.defaultCommunities(site),
        ).also { communityCache[site] = it }
    }

    suspend fun refreshThreads(site: ForumSite, community: Community, page: Int = 1): LoadableContent<List<FeedThread>> {
        val result = repository.loadThreads(site, community, page)
        return contentFromResult(
            result = result,
            fallback = emptyList(),
        ).also { threadCache[threadKey(site, community)] = it }
    }

    suspend fun refreshDetail(site: ForumSite, thread: FeedThread, page: Int = 1): LoadableContent<ThreadDetailContent> {
        val result = repository.loadThreadDetail(site, thread, page)
        return contentFromResult(
            result = result.map { (freshThread, comments) -> ThreadDetailContent(freshThread, comments) },
            fallback = ThreadDetailContent(thread, emptyList()),
        ).also { detailCache[detailKey(site, thread)] = it }
    }

    suspend fun search(site: ForumSite, query: String, page: Int = 1): LoadableContent<SearchResult> {
        val result = repository.searchThreads(site, query, page)
        return contentFromResult(
            result = result,
            fallback = SearchResult(emptyList(), false),
        )
    }

    suspend fun createThread(site: ForumSite, community: Community, title: String, content: String): FeedflowError? =
        repository.createThread(site, community, title.trim(), content.trim())

    suspend fun postComment(site: ForumSite, thread: FeedThread, content: String): FeedflowError? =
        repository.postComment(site, thread, content.trim())

    suspend fun deleteThread(site: ForumSite, thread: FeedThread): FeedflowError? =
        repository.deleteThread(site, thread)

    suspend fun summarizeThread(
        apiKey: String,
        site: ForumSite,
        thread: FeedThread,
        comments: List<Comment>,
        language: String,
        forceRefresh: Boolean = false,
    ): AiSummaryUiState =
        repository.summarizeThread(apiKey, site, thread, comments, language, forceRefresh)

    suspend fun summarizeDailyRss(apiKey: String, threads: List<FeedThread>, language: String, forceRefresh: Boolean = false): AiSummaryUiState =
        repository.summarizeDailyRss(apiKey, threads, language, forceRefresh)

    suspend fun summarizeCrossSite(apiKey: String, site: ForumSite, threads: List<FeedThread>, language: String): AiSummaryUiState =
        repository.summarizeCrossSite(apiKey, site, threads, language)

    fun setSignedIn(site: ForumSite, signedIn: Boolean) {
        homeState = homeState.copy(signedInSites = if (signedIn) homeState.signedInSites + site else homeState.signedInSites - site)
    }

    fun saveSettings(geminiApiKey: String, backgroundPrefetch: Boolean) {
        homeState = homeState.copy(geminiApiKey = geminiApiKey, backgroundPrefetch = backgroundPrefetch)
    }

    fun updateRssFeeds(feeds: List<RssFeedSubscription>) {
        homeState = homeState.copy(rssFeeds = feeds)
    }

    fun toggleSite(site: ForumSite) {
        homeState = homeState.toggleSite(site)
    }

    private fun <T, R> CacheFirstResult<T>.map(transform: (T) -> R): CacheFirstResult<R> =
        CacheFirstResult(cached = cached?.let(transform), fresh = fresh?.let(transform), warning = warning)

    private fun <T> contentFromResult(result: CacheFirstResult<T>, fallback: T): LoadableContent<T> {
        val best = result.best ?: fallback
        return LoadableContent.loaded(
            value = best,
            warning = result.warning?.message,
            loadedFromCache = result.fresh == null && result.cached != null,
        )
    }

    private fun threadKey(site: ForumSite, community: Community): String = "${site.serviceId}:${community.id}"

    private fun detailKey(site: ForumSite, thread: FeedThread): String = "${site.serviceId}:${thread.id}"
}
