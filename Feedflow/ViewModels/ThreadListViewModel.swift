import Foundation
import Combine
import SwiftUI
import Network

@MainActor
class ThreadListViewModel: ObservableObject {
    @Published var threads: [Thread] = []
    @Published var isLoading = false
    @Published var canLoadMore = true
    @Published var isAtTop = true // Track if user is at top of list
    @Published var refreshMessage: String?
    @Published var needsLogin: Bool = false

    private var currentPage = 1
    private let service: ForumService
    private var currentCommunity: Community?

    private var prefetchQueue: [Thread] = []
    private var isQueueProcessing = false
    private var pendingPrefetches: [String: Task<Void, Never>] = [:]
    private let maxPrefetchQueueSize = 5

    /// Debounce task for scroll-position updates — prevents firing on every frame.
    private var scrollDebounceTask: Task<Void, Never>?

    static let backgroundPrefetchEnabledKey = "background_prefetch_enabled"

    private var isBackgroundPrefetchEnabled: Bool {
        UserDefaults.standard.bool(forKey: Self.backgroundPrefetchEnabledKey)
    }

    private var serviceAllowsBackgroundPrefetch: Bool {
        ["hackernews", "rss", "4d4y", "v2ex", "linux_do", "zhihu"].contains(service.id)
    }

    var allowsConfiguredBackgroundPrefetch: Bool {
        isBackgroundPrefetchEnabled && serviceAllowsBackgroundPrefetch
    }

    init(service: ForumService) {
        self.service = service
    }

    private func showRefreshMessage(_ message: String) {
        refreshMessage = message
        Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if refreshMessage == message {
                refreshMessage = nil
            }
        }
    }

    func clearLoginRequest() {
        needsLogin = false
    }

    func loadTopics(for community: Community, isReturning: Bool = false, forceRefresh: Bool = false) async {
        currentCommunity = community

        // Restore session (cookies/credentials) proactively before fetching
        let sessionReady = await service.restoreSession()
        if !sessionReady && service.requiresLogin {
            needsLogin = true
            showRefreshMessage("Login required. Redirecting to Login.")
            return
        }

        // Create cache key
        let cacheKey = "\(service.id)_\(community.id)_page1"

        // Attempt to load from cache
        let cachedThreads = DatabaseManager.shared.getCachedTopics(cacheKey: cacheKey)

        if forceRefresh {
            isLoading = true
            defer { isLoading = false }

            do {
                let previousThreads = self.threads
                let newThreads = try await service.refreshCategoryThreads(categoryId: community.id, communities: [community])

                guard shouldAcceptFreshThreads(newThreads) else {
                    if service.id == "4d4y" {
                        needsLogin = true
                    }
                    // An explicit refresh that returns empty is a strong signal
                    // that the session has expired and the server is silently
                    // rejecting the request behind login-gated content.
                    if service.id == "4d4y" || service.requiresLogin {
                        showRefreshMessage("Session may have expired. Login to refresh content.")
                    } else {
                        showRefreshMessage("Refresh returned no data. Kept existing posts.")
                    }
                    // Small delay to ensure UI updates propagate to refreshable modifier
                    try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
                    return
                }

                let mergedThreads = threadsPreservingRicherMetadata(newThreads, existing: previousThreads)
                DatabaseManager.shared.saveCachedTopics(cacheKey: cacheKey, topics: mergedThreads)
                mergeThreadsWithDiff(mergedThreads)
                self.canLoadMore = !newThreads.isEmpty
                self.currentPage = 1
                if !mergedThreads.isEmpty && !hasChanges(old: previousThreads, new: mergedThreads) {
                    showRefreshMessage("No new posts yet.")
                }
                // Small delay to ensure UI updates propagate to refreshable modifier
                try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
            } catch is CancellationError {
                // Ignore cancellation
            } catch let error as URLError where error.code == .cancelled {
                // Ignore cancellation
            } catch let error as URLError where error.code == .userAuthenticationRequired {
                AppLogger.debug("Error force refreshing topics: \(error)")
                needsLogin = true
                showRefreshMessage("Login required. Redirecting to Login.")
            } catch {
                AppLogger.debug("Error force refreshing topics: \(error)")
                showRefreshMessage("Refresh failed. Check network or login and try again.")
            }
        // If returning from detail or cache exists, load cache first
        } else if isReturning || cachedThreads != nil {
            if let threads = cachedThreads, !threads.isEmpty {
                self.threads = threads
                self.canLoadMore = true
                self.currentPage = 1
            }

            // Zhihu recommend should only refresh when user explicitly requests it.
            if !shouldSkipBackgroundRefresh(for: community) {
                await fetchFreshData(for: community, cacheKey: cacheKey)
            }
        } else {
            // First time loading without cache - show loading state
            isLoading = true
            defer { isLoading = false }
            currentPage = 1
            self.threads = []

            do {
                let newThreads = try await service.fetchCategoryThreads(categoryId: community.id, communities: [community], page: 1)
                AppLogger.debug("[ThreadListViewModel] Fetched \(newThreads.count) threads for \(community.id)")

                guard shouldAcceptFreshThreads(newThreads) else { return }

                let mergedThreads = threadsPreservingRicherMetadata(newThreads, existing: threads)
                DatabaseManager.shared.saveCachedTopics(cacheKey: cacheKey, topics: mergedThreads)
                self.threads = mergedThreads
                self.canLoadMore = !newThreads.isEmpty
            } catch is CancellationError {
                // Ignore cancellation
            } catch let error as URLError where error.code == .cancelled {
                // Ignore cancellation
            } catch let error as URLError where error.code == .userAuthenticationRequired {
                AppLogger.debug("Error loading topics: \(error)")
                needsLogin = true
            } catch {
                AppLogger.debug("Error loading topics: \(error)")
            }
        }
    }

    private func fetchFreshData(for community: Community, cacheKey: String) async {
        do {
            let newThreads = try await service.fetchCategoryThreads(categoryId: community.id, communities: [community], page: 1)

            guard shouldAcceptFreshThreads(newThreads) else { return }

            let mergedThreads = threadsPreservingRicherMetadata(newThreads, existing: threads)
            DatabaseManager.shared.saveCachedTopics(cacheKey: cacheKey, topics: mergedThreads)

            // Only update UI if user is still at top and data has changed
            if isAtTop && hasChanges(old: self.threads, new: mergedThreads) {
                mergeThreadsWithDiff(mergedThreads)
                self.canLoadMore = !mergedThreads.isEmpty

            }
        } catch is CancellationError {
            // Ignore
        } catch let error as URLError where error.code == .cancelled {
            // Ignore
        } catch let error as URLError where error.code == .userAuthenticationRequired {
            AppLogger.debug("Error fetching fresh data: \(error)")
            needsLogin = true
        } catch {
            AppLogger.debug("Error fetching fresh data: \(error)")
        }
    }

    private func hasChanges(old: [Thread], new: [Thread]) -> Bool {
        // Quick check: different counts
        if old.count != new.count { return true }

        // Check if any visible thread metadata changed. This matters for sites
        // where the same thread IDs can gain richer metadata after login.
        for (index, oldThread) in old.enumerated() {
            guard index < new.count else { return true }
            let newThread = new[index]
            if oldThread.id != newThread.id ||
                oldThread.title != newThread.title ||
                oldThread.author.username != newThread.author.username ||
                oldThread.author.avatar != newThread.author.avatar ||
                oldThread.timeAgo != newThread.timeAgo ||
                oldThread.commentCount != newThread.commentCount ||
                oldThread.lastPostTime != newThread.lastPostTime ||
                oldThread.lastPosterName != newThread.lastPosterName {
                return true
            }
        }
        return false
    }

    private func shouldSkipBackgroundRefresh(for community: Community) -> Bool {
        service.id == "zhihu" && community.id == "recommend"
    }

    private func threadsPreservingRicherMetadata(_ incoming: [Thread], existing: [Thread]) -> [Thread] {
        guard !existing.isEmpty else { return incoming }

        let existingByID = Dictionary(uniqueKeysWithValues: existing.map { ($0.id, $0) })
        return incoming.map { newThread in
            guard let oldThread = existingByID[newThread.id] else {
                return newThread
            }

            let author = shouldKeepExistingAvatar(newThread.author.avatar, oldThread.author.avatar)
                ? oldThread.author
                : newThread.author

            return Thread(
                id: newThread.id,
                title: newThread.title,
                content: newThread.content,
                author: author,
                community: newThread.community,
                timeAgo: newThread.timeAgo.isEmpty ? oldThread.timeAgo : newThread.timeAgo,
                likeCount: newThread.likeCount,
                commentCount: newThread.commentCount,
                isLiked: newThread.isLiked,
                tags: newThread.tags ?? oldThread.tags,
                lastPostTime: newThread.lastPostTime ?? oldThread.lastPostTime,
                lastPosterName: newThread.lastPosterName ?? oldThread.lastPosterName
            )
        }
    }

    private func shouldKeepExistingAvatar(_ newAvatar: String, _ oldAvatar: String) -> Bool {
        isPlaceholderAvatar(newAvatar) && !isPlaceholderAvatar(oldAvatar)
    }

    private func isPlaceholderAvatar(_ avatar: String) -> Bool {
        let normalized = avatar.trimmingCharacters(in: .whitespacesAndNewlines)
        return normalized.isEmpty ||
            normalized == "person.circle" ||
            normalized == "person.circle.fill" ||
            normalized == "person.crop.circle" ||
            normalized == "person.crop.circle.fill"
    }

    private func shouldAcceptFreshThreads(_ newThreads: [Thread]) -> Bool {
        // For 4d4y and Zhihu recommend: if refresh returns empty results but we have existing posts,
        // keep the existing posts instead of showing an empty list (likely due to filtering or network issues)
        let shouldPreserveOnEmpty = (service.id == "4d4y") || (service.id == "zhihu" && currentCommunity?.id == "recommend")
        
        guard shouldPreserveOnEmpty, newThreads.isEmpty, !threads.isEmpty else {
            return true
        }

        AppLogger.debug("[ThreadListViewModel] Ignored empty refresh to preserve \(threads.count) visible threads.")
        return false
    }

    /// Merge freshly-fetched threads into the displayed list using per-item
    /// mutations so SwiftUI animates only changed rows via `Identifiable` diffing.
    private func mergeThreadsWithDiff(_ newThreads: [Thread]) {
        let oldIDs = threads.map(\.id)
        let newIDs = newThreads.map(\.id)

        // Nothing changed — skip the animation pass entirely.
        guard oldIDs != newIDs else {
            // Still assign in case thread properties (title, counts) changed.
            threads = newThreads
            return
        }

        withAnimation(.easeInOut(duration: 0.2)) {
            threads = newThreads
        }
    }

    func loadMoreTopics(for community: Community) async {
        guard !isLoading, canLoadMore else { return }
        isLoading = true
        defer { isLoading = false }
        let nextPage = currentPage + 1

        do {
            let newThreads = try await service.fetchCategoryThreads(categoryId: community.id, communities: [community], page: nextPage)
            if newThreads.isEmpty {
                canLoadMore = false
            } else {
                self.threads.append(contentsOf: newThreads)
                currentPage = nextPage

            }
        } catch is CancellationError {
            // Ignore
        } catch let error as URLError where error.code == .cancelled {
            // Ignore
        } catch {
            AppLogger.debug("Error loading more topics: \(error)")
        }
    }

    func updateScrollPosition(isAtTop: Bool) {
        // Immediately set to true when reaching the top — needed for timely
        // background-refresh gating and pull-to-refresh UX.
        if isAtTop {
            scrollDebounceTask?.cancel()
            if !self.isAtTop { self.isAtTop = true }
            return
        }

        // Debounce leaving the top: don't flip `isAtTop` to false on every
        // frame during a fast scroll. Wait 100 ms for the scroll to settle.
        guard self.isAtTop else { return }
        scrollDebounceTask?.cancel()
        scrollDebounceTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 100_000_000) // 100 ms
            guard !Task.isCancelled, let self else { return }
            self.isAtTop = false
        }
    }

    /// Remove a thread from the displayed list (e.g., after downvoting)
    func removeThread(_ thread: Thread) {
        threads.removeAll { $0.id == thread.id }
    }

    func prefetchThread(thread: Thread) {
        guard allowsConfiguredBackgroundPrefetch else { return }
        guard NetworkMonitor.shared.isWiFi else { return }
        guard prefetchQueue.count < maxPrefetchQueueSize else { return }

        // Skip if already in local DB
        if DatabaseManager.shared.getCachedThread(threadId: thread.id, serviceId: service.id) != nil {
            return
        }

        // Cancel existing pending task for this thread if any (shouldn't really happen but for safety)
        pendingPrefetches[thread.id]?.cancel()

        // Create a new debounce task
        let task = Task {
            try? await Task.sleep(nanoseconds: 400_000_000) // 400ms debounce

            if !Task.isCancelled {
                // Add to queue if not already present
                if prefetchQueue.count < maxPrefetchQueueSize,
                   !prefetchQueue.contains(where: { $0.id == thread.id }) {
                    prefetchQueue.append(thread)
                    processPrefetchQueue()
                }
                pendingPrefetches.removeValue(forKey: thread.id)
            }
        }

        pendingPrefetches[thread.id] = task
    }

    func cancelPrefetch(threadId: String) {
        if let task = pendingPrefetches[threadId] {
            task.cancel()
            pendingPrefetches.removeValue(forKey: threadId)
            AppLogger.debug("[Prefetch] Cancelled pending download for thread: \(threadId) (scrolled past)")
        }
    }

    private func processPrefetchQueue() {
        guard !isQueueProcessing, !prefetchQueue.isEmpty else { return }
        isQueueProcessing = true

        Task {
            while !prefetchQueue.isEmpty {
                // Double check WiFi at start of each item
                guard NetworkMonitor.shared.isWiFi else {
                    AppLogger.debug("[Prefetch] Paused: Not on WiFi.")
                    prefetchQueue.removeAll()
                    isQueueProcessing = false
                    return
                }

                let thread = prefetchQueue.removeFirst()

                do {
                    AppLogger.debug("[Prefetch] Starting individual download for thread: \(thread.id)")
                    let (fetchedThread, fetchedComments, _) = try await service.fetchThreadDetail(threadId: thread.id, page: 1)

                    let updatedThread = Thread(
                        id: fetchedThread.id,
                        title: fetchedThread.title,
                        content: fetchedThread.content,
                        author: fetchedThread.author,
                        community: thread.community,
                        timeAgo: thread.timeAgo,
                        likeCount: thread.likeCount,
                        commentCount: thread.commentCount,
                        isLiked: thread.isLiked,
                        tags: fetchedThread.tags,
                        lastPostTime: thread.lastPostTime,
                        lastPosterName: thread.lastPosterName
                    )

                    DatabaseManager.shared.saveCachedThread(threadId: thread.id, serviceId: service.id, thread: updatedThread, comments: fetchedComments)

                    // 3. Rate control: Wait before next download
                    try? await Task.sleep(nanoseconds: 1_000_000_000) // 1 second delay
                } catch {
                    AppLogger.debug("[Prefetch] Error prefetching \(thread.id): \(error)")
                }
            }
            isQueueProcessing = false
        }
    }
}
