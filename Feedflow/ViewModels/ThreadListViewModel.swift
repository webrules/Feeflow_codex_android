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
    
    private var currentPage = 1
    private let service: ForumService
    private var currentCommunity: Community?
    
    private var prefetchQueue: [Thread] = []
    private var isQueueProcessing = false
    private var pendingPrefetches: [String: Task<Void, Never>] = [:]
    
    init(service: ForumService) {
        self.service = service
    }
    
    func loadTopics(for community: Community, isReturning: Bool = false) async {
        currentCommunity = community
        
        // Create cache key
        let cacheKey = "\(service.id)_\(community.id)_page1"
        
        // Attempt to load from cache
        let cachedThreads = DatabaseManager.shared.getCachedTopics(cacheKey: cacheKey)
        
        // If returning from detail or cache exists, load cache first
        if isReturning || cachedThreads != nil {
            if let threads = cachedThreads, !threads.isEmpty {
                self.threads = threads
                self.canLoadMore = true
                self.currentPage = 1
            }
            
            // Fetch fresh data in background
            await fetchFreshData(for: community, cacheKey: cacheKey)
        } else {
            // First time loading without cache - show loading state
            isLoading = true
            defer { isLoading = false }
            currentPage = 1
            self.threads = []
            
            do {
                let newThreads = try await service.fetchCategoryThreads(categoryId: community.id, communities: [community], page: 1)
                print("[ThreadListViewModel] Fetched \(newThreads.count) threads for \(community.id)")
                
                // Save to cache
                DatabaseManager.shared.saveCachedTopics(cacheKey: cacheKey, topics: newThreads)
                
                // Update UI
                self.threads = newThreads
                self.canLoadMore = !newThreads.isEmpty
            } catch is CancellationError {
                // Ignore cancellation
            } catch let error as URLError where error.code == .cancelled {
                // Ignore cancellation
            } catch {
                print("Error loading topics: \(error)")
            }
        }
    }
    
    private func fetchFreshData(for community: Community, cacheKey: String) async {
        do {
            let newThreads = try await service.fetchCategoryThreads(categoryId: community.id, communities: [community], page: 1)
            
            // Always save to cache
            DatabaseManager.shared.saveCachedTopics(cacheKey: cacheKey, topics: newThreads)
            
            // Only update UI if user is still at top and data has changed
            if isAtTop && hasChanges(old: self.threads, new: newThreads) {
                self.threads = newThreads
                self.canLoadMore = !newThreads.isEmpty
                
            }
        } catch is CancellationError {
            // Ignore
        } catch let error as URLError where error.code == .cancelled {
            // Ignore
        } catch {
            print("Error fetching fresh data: \(error)")
        }
    }
    
    private func hasChanges(old: [Thread], new: [Thread]) -> Bool {
        // Quick check: different counts
        if old.count != new.count { return true }
        
        // Check if any thread IDs or titles changed
        for (index, oldThread) in old.enumerated() {
            guard index < new.count else { return true }
            let newThread = new[index]
            if oldThread.id != newThread.id || oldThread.title != newThread.title {
                return true
            }
        }
        return false
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
            print("Error loading more topics: \(error)")
        }
    }
    
    func updateScrollPosition(isAtTop: Bool) {
        self.isAtTop = isAtTop
    }
    
    /// Remove a thread from the displayed list (e.g., after downvoting)
    func removeThread(_ thread: Thread) {
        threads.removeAll { $0.id == thread.id }
    }
    
    func prefetchThread(thread: Thread) {
        // Allow prefetch for all sites.
        // guard service.id == "4d4y" else { return }

        
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
                if !prefetchQueue.contains(where: { $0.id == thread.id }) {
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
            print("[Prefetch] Cancelled pending download for thread: \(threadId) (scrolled past)")
        }
    }
    
    private func processPrefetchQueue() {
        guard !isQueueProcessing, !prefetchQueue.isEmpty else { return }
        isQueueProcessing = true
        
        Task {
            while !prefetchQueue.isEmpty {
                // Double check WiFi at start of each item
                guard NetworkMonitor.shared.isWiFi else {
                    print("[Prefetch] Paused: Not on WiFi.")
                    prefetchQueue.removeAll()
                    isQueueProcessing = false
                    return
                }
                
                let thread = prefetchQueue.removeFirst()
                
                do {
                    print("[Prefetch] Starting individual download for thread: \(thread.id)")
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
                        tags: fetchedThread.tags
                    )
                    
                    DatabaseManager.shared.saveCachedThread(threadId: thread.id, serviceId: service.id, thread: updatedThread, comments: fetchedComments)
                    
                    // 3. Rate control: Wait before next download
                    try? await Task.sleep(nanoseconds: 1_000_000_000) // 1 second delay
                } catch {
                    print("[Prefetch] Error prefetching \(thread.id): \(error)")
                }
            }
            isQueueProcessing = false
        }
    }
}
