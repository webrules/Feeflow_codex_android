import Foundation
import Combine
import SwiftUI

@MainActor
class ThreadDetailViewModel: ObservableObject {
    @Published var thread: Thread
    @Published var comments: [Comment] = []
    @Published var isLoading = false
    @Published var canLoadMore = true
    @Published var isLatest = false
    @Published var isBookmarked = false
    @Published var shouldScrollAfterReply = false
    @Published var replyingTo: Comment? = nil
    private var currentPage = 1

    private let service: ForumService
    private let contextThreads: [Thread]

    init(thread: Thread, service: ForumService, contextThreads: [Thread] = []) {
        self.thread = thread
        self.service = service
        self.contextThreads = contextThreads
        self.isBookmarked = DatabaseManager.shared.isBookmarked(threadId: thread.id, serviceId: service.id)
        markCurrentZhihuRecommendationAsRead()
    }

    func goPrevious() {
        guard let index = contextThreads.firstIndex(where: { $0.id == thread.id }), index > 0 else { return }
        let prev = contextThreads[index - 1]
        switchToThread(prev)
    }

    func goNext() {
        guard let index = contextThreads.firstIndex(where: { $0.id == thread.id }), index < contextThreads.count - 1 else { return }
        let next = contextThreads[index + 1]
        switchToThread(next)
    }

    var hasPreviousThread: Bool {
        guard let index = contextThreads.firstIndex(where: { $0.id == thread.id }) else { return false }
        return index > 0
    }

    var hasNextThread: Bool {
        guard let index = contextThreads.firstIndex(where: { $0.id == thread.id }) else { return false }
        return index < contextThreads.count - 1
    }

    private func switchToThread(_ newThread: Thread) {
        self.thread = newThread
        self.comments = []
        self.currentPage = 1
        self.isBookmarked = DatabaseManager.shared.isBookmarked(threadId: newThread.id, serviceId: service.id)
        markCurrentZhihuRecommendationAsRead()

        Task {
            await loadDetails()
        }
    }

    private func markCurrentZhihuRecommendationAsRead() {
        guard thread.community.id == "recommend",
              let zhihuService = service as? ZhihuService else { return }

        zhihuService.markPostAsRead(threadId: thread.id)
    }

    func loadDetails() async {
        await loadDetails(useCache: true)
    }

    func refreshDetails() async {
        await loadDetails(useCache: false)
    }

    private func loadDetails(useCache: Bool) async {
        isLoading = true
        defer { isLoading = false }
        currentPage = 1
        isLatest = false

        // Load from cache first (instant display)
        if useCache,
           thread.community.id != "search",
           let cached = DatabaseManager.shared.getCachedThread(threadId: thread.id, serviceId: service.id) {
            if isInvalidCachedDetail(cached.0) {
                AppLogger.debug("[ThreadDetailViewModel] Ignored invalid cached detail for \(service.id)/\(thread.id)")
                comments = []
            } else {
                self.thread = cached.0
                self.comments = cached.1
                self.canLoadMore = !cached.1.isEmpty
            }
        } else if comments.isEmpty {
            comments = []
        }

        // Fetch fresh data in background
        do {
            let (fetchedThread, fetchedComments, totalPages) = try await service.fetchThreadDetail(threadId: thread.id, page: 1)

            // Merge fetched content
            // Preserve original content/title if the API didn't return any (e.g., search results)
            let effectiveContent: String = {
                let fetched = fetchedThread.content.trimmingCharacters(in: .whitespacesAndNewlines)
                if !fetched.isEmpty { return fetched }
                let current = self.thread.content.trimmingCharacters(in: .whitespacesAndNewlines)
                if !current.isEmpty { return current }
                return fetched
            }()
            let effectiveTitle: String = {
                let fetched = fetchedThread.title.trimmingCharacters(in: .whitespacesAndNewlines)
                if isPlaceholderTitle(fetched) == false { return fetched }
                let current = self.thread.title.trimmingCharacters(in: .whitespacesAndNewlines)
                if !current.isEmpty { return current }
                return fetched
            }()

            let updatedThread = Thread(
                id: fetchedThread.id,
                title: effectiveTitle,
                content: effectiveContent,
                author: resolvedAuthor(fetched: fetchedThread.author, current: self.thread.author),
                community: self.thread.community,
                timeAgo: fetchedThread.timeAgo,
                likeCount: fetchedThread.likeCount,
                commentCount: fetchedThread.commentCount,
                isLiked: self.thread.isLiked,
                tags: fetchedThread.tags,
                lastPostTime: self.thread.lastPostTime,
                lastPosterName: self.thread.lastPosterName
            )

            // Save to cache
            DatabaseManager.shared.saveCachedThread(threadId: thread.id, serviceId: service.id, thread: updatedThread, comments: fetchedComments)

            // Update UI with fresh data
            self.thread = updatedThread
            self.comments = fetchedComments
            self.isLatest = true

            // Check max pages logic
            if let max = totalPages {
                self.canLoadMore = currentPage < max
            } else {
                 self.canLoadMore = !fetchedComments.isEmpty
            }
        } catch is CancellationError {
            // Ignore
        } catch let error as URLError where error.code == .cancelled {
            // Ignore
        } catch {
            AppLogger.debug("Error loading details: \(error)")
        }
    }

    func loadMoreComments() async {
        guard !isLoading, canLoadMore else { return }
        isLoading = true
        defer { isLoading = false }
        let nextPage = currentPage + 1
        AppLogger.debug("Loading more comments page: \(nextPage)")

        do {
            let (_, newComments, totalPages) = try await service.fetchThreadDetail(threadId: thread.id, page: nextPage)

            if newComments.isEmpty {
                canLoadMore = false
            } else {
                self.comments.append(contentsOf: newComments)
                currentPage = nextPage

                // Update canLoadMore based on max pages again if available
                if let max = totalPages {
                    self.canLoadMore = currentPage < max
                }
            }
        } catch is CancellationError {
            // Ignore
        } catch let error as URLError where error.code == .cancelled {
            // Ignore
        } catch {
            AppLogger.debug("Error loading more comments: \(error)")
        }
    }

    private func resolvedAuthor(fetched: User, current: User) -> User {
        let fetchedAvatar = fetched.avatar.trimmingCharacters(in: .whitespacesAndNewlines)
        let currentAvatar = current.avatar.trimmingCharacters(in: .whitespacesAndNewlines)
        let genericAvatars = Set(["", "person.circle", "person.circle.fill", "person.crop.circle", "person.crop.circle.fill"])
        let genericUsernames = Set(["", "Unknown", "User", "匿名用户"])

        guard genericAvatars.contains(fetchedAvatar), !genericAvatars.contains(currentAvatar) else {
            return fetched
        }

        return User(
            id: fetched.id.isEmpty ? current.id : fetched.id,
            username: genericUsernames.contains(fetched.username) ? current.username : fetched.username,
            avatar: current.avatar,
            role: fetched.role ?? current.role
        )
    }

    private func isPlaceholderTitle(_ title: String) -> Bool {
        ["", "无标题", "回答", "文章", "问题", "Unknown Topic", "Unknown Title"]
            .contains(title)
    }

    private func isInvalidCachedDetail(_ cachedThread: Thread) -> Bool {
        service.id == "4d4y" && cachedThread.content == "Could not parse content."
    }

    func sendReply(content: String) async throws {
        var finalContent = content

        if let replyingTo = replyingTo {
            // format quote
            // Truncate if too long? For now, full content per requirement.
            // Discuz style simple quote
            let quote = "[quote][b]\(replyingTo.author.username) \(LocalizationManager.shared.localizedString("said")):[/b]\n\(replyingTo.content)[/quote]\n\n"
            finalContent = quote + content
        }

        try await service.postComment(topicId: thread.id, categoryId: thread.community.id, content: finalContent)
        // Refresh to see the new comment - jump to the last page
        await refreshAfterReply()

        await MainActor.run {
            self.replyingTo = nil
        }
    }

    func selectCommentForReply(_ comment: Comment) {
        replyingTo = comment
    }

    func cancelReply() {
        replyingTo = nil
    }

    private func refreshAfterReply() async {
        isLoading = true
        defer { isLoading = false }

        do {
            // First, get the first page again to see if total pages updated
            let (_, _, totalPages) = try await service.fetchThreadDetail(threadId: thread.id, page: 1)

            if let max = totalPages, max > 1 {
                // If there are multiple pages, fetch the last one
                let (_, lastPageComments, _) = try await service.fetchThreadDetail(threadId: thread.id, page: max)
                self.comments = lastPageComments
                self.currentPage = max
                self.canLoadMore = false
            } else {
                // Just reload page 1
                let (_, freshComments, _) = try await service.fetchThreadDetail(threadId: thread.id, page: 1)
                self.comments = freshComments
                self.currentPage = 1
                self.canLoadMore = false
            }
            self.shouldScrollAfterReply = true
        } catch {
            AppLogger.debug("Error refreshing after reply: \(error)")
        }
    }

    func toggleBookmark() {
        DatabaseManager.shared.toggleBookmark(thread: thread, serviceId: service.id)
        isBookmarked = DatabaseManager.shared.isBookmarked(threadId: thread.id, serviceId: service.id)
    }
}
