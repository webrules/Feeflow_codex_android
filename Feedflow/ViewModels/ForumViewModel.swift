import Foundation
import Combine

@MainActor
class ForumViewModel: ObservableObject {
    @Published var communities: [Community] = []
    @Published var isLoading: Bool = false
    @Published var selectedCategory: String = "All Categories"
    @Published var needsLogin: Bool = false

    private let service: ForumService

    init(service: ForumService) {
        self.service = service
        // Try to load cached data first — fast synchronous read from DB
        self.communities = DatabaseManager.shared.getCommunities(forService: service.id)

        // Deferred load via refresh() ensures no race with subsequent calls.
        // Using Task here would race if refresh() is called before this Task completes.
        Task {
            await loadData()
        }
    }

    func loadData() async {
        isLoading = true
        defer { isLoading = false }

        // Restore session first; if login is needed, signal the UI
        let sessionReady = await service.restoreSession()
        if !sessionReady && service.requiresLogin {
            needsLogin = true
            return
        }

        do {
            let fetchedCommunities = try await service.fetchCategories()
            let resolvedCommunities = resolveCommunitiesAfterFetch(fetchedCommunities)
            self.communities = resolvedCommunities
            // Save to DB — DatabaseManager is not Sendable, so this must stay on the
            // MainActor. The dbQueue inside DatabaseManager makes it thread-safe,
            // but the struct itself contains non-Sendable properties (e.g. dbQueue).
            DatabaseManager.shared.saveCommunities(resolvedCommunities, forService: service.id)
        } catch is CancellationError {
            // Ignore
        } catch let error as URLError where error.code == .cancelled {
            // Ignore
        } catch {
            AppLogger.debug("Failed to fetch data: \(error)")
        }
    }

    private func resolveCommunitiesAfterFetch(_ fetched: [Community]) -> [Community] {
        guard service.id == "4d4y", !communities.isEmpty, fetched.count < communities.count else {
            return fetched
        }

        let fetchedById = Dictionary(uniqueKeysWithValues: fetched.map { ($0.id, $0) })
        var resolved = communities.map { cached in
            fetchedById[cached.id] ?? cached
        }

        let cachedIds = Set(communities.map(\.id))
        let newCommunities = fetched.filter { !cachedIds.contains($0.id) }
        resolved.append(contentsOf: newCommunities)

        AppLogger.debug("[ForumViewModel] Preserved \(resolved.count - fetched.count) cached 4D4Y communities because fresh fetch returned fewer communities (\(fetched.count) < \(communities.count)).")
        return resolved
    }

    func refresh() async {
        needsLogin = false
        await loadData()
    }
}
