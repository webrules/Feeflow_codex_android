package com.webrules.feedflow.core.data

import com.webrules.feedflow.core.database.RssFeedSubscription

data class RssFeedManagerState(
    val feeds: List<RssFeedSubscription> = emptyList(),
    val editMode: Boolean = false,
    val selectedUrls: Set<String> = emptySet(),
    val importPreview: List<RssFeedSubscription> = emptyList(),
    val errorMessage: String? = null,
) {
    fun addManual(name: String, url: String, createdAt: Long): RssFeedManagerState {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return copy(errorMessage = "Feed URL is required")
        val title = name.trim().ifBlank { trimmedUrl }
        return copy(
            feeds = (feeds.filterNot { it.url == trimmedUrl } + RssFeedSubscription(trimmedUrl, title, false, createdAt)),
            errorMessage = null,
        )
    }

    fun toggleEdit(): RssFeedManagerState = copy(editMode = !editMode, selectedUrls = emptySet())

    fun toggleSelection(url: String): RssFeedManagerState =
        if (!editMode) this else copy(selectedUrls = if (selectedUrls.contains(url)) selectedUrls - url else selectedUrls + url)

    fun deleteSelected(): RssFeedManagerState =
        copy(
            feeds = feeds.filterNot { selectedUrls.contains(it.url) },
            selectedUrls = emptySet(),
            editMode = feeds.any { !selectedUrls.contains(it.url) },
        )

    fun previewOpml(data: ByteArray, createdAt: Long): RssFeedManagerState {
        val parsed = OpmlParser(data).parse().map { feed ->
            RssFeedSubscription(feed.url, feed.title, false, createdAt)
        }
        return if (parsed.isEmpty()) {
            copy(importPreview = emptyList(), errorMessage = "No feeds found")
        } else {
            copy(importPreview = parsed, errorMessage = null)
        }
    }

    fun importPreview(selectedUrls: Set<String> = importPreview.map { it.url }.toSet()): RssFeedManagerState {
        val selected = importPreview.filter { selectedUrls.contains(it.url) }
        return copy(
            feeds = (feeds + selected).distinctBy { it.url },
            importPreview = emptyList(),
            errorMessage = null,
        )
    }
}
