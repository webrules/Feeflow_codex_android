package com.webrules.feedflow.core.model

data class User(
    val id: String,
    val username: String,
    val avatar: String,
    val role: String? = null,
)

data class Community(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val activeToday: Int,
    val onlineNow: Int,
)

data class FeedThread(
    val id: String,
    val title: String,
    val content: String,
    val author: User,
    val community: Community,
    val timeAgo: String,
    val likeCount: Int,
    val commentCount: Int,
    var isLiked: Boolean = false,
    var tags: List<String>? = null,
    val lastPostTime: String? = null,
    val lastPosterName: String? = null,
)

data class Comment(
    val id: String,
    val author: User,
    val content: String,
    val timeAgo: String,
    val likeCount: Int,
    val replies: List<Comment>? = null,
)

enum class ForumSite(
    val serviceId: String,
    val displayName: String,
    val icon: String,
    val description: String,
    val requiresLogin: Boolean,
    val supportsCommenting: Boolean,
    val supportsThreadCreation: Boolean,
    val supportsSearch: Boolean,
) {
    Rss("rss", "RSS Feeds", "feed", "Custom RSS and Atom subscriptions", false, false, false, false),
    HackerNews("hackernews", "Hacker News", "flame.fill", "Top, new, best, show, ask, and jobs", false, false, false, false),
    FourD4Y("4d4y", "4D4Y", "4.circle.fill", "Discuz forum with replies and new threads", true, true, true, true),
    V2ex("v2ex", "V2EX", "point.3.connected.trianglepath.dotted", "V2EX topics and replies", false, true, false, true),
    LinuxDo("linux_do", "Linux.do", "terminal.fill", "Discourse community with authenticated actions", true, true, true, true),
    Zhihu("zhihu", "知乎", "questionmark.bubble.fill", "Zhihu recommendations and search", true, false, false, true);

    companion object {
        fun fromServiceId(serviceId: String): ForumSite? = entries.firstOrNull { it.serviceId == serviceId }
    }
}

data class SourceCapability(
    val serviceId: String,
    val requiresLogin: Boolean,
    val supportsCommenting: Boolean,
    val supportsThreadCreation: Boolean,
    val supportsSearch: Boolean,
)

val ForumSite.capability: SourceCapability
    get() = SourceCapability(
        serviceId = serviceId,
        requiresLogin = requiresLogin,
        supportsCommenting = supportsCommenting,
        supportsThreadCreation = supportsThreadCreation,
        supportsSearch = supportsSearch,
    )
