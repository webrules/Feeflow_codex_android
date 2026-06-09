import Foundation
import Combine

@MainActor
class ForumViewModel: ObservableObject {
    @Published var communities: [Community] = []
    @Published var isLoading: Bool = false
    @Published var selectedCategory: String = "All Categories"
    
    private let service: ForumService
    
    init(service: ForumService) {
        self.service = service
        // Try to load cached data first
        self.communities = DatabaseManager.shared.getCommunities(forService: service.id)
        
        Task { await loadData() }
    }
    
    func loadData() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let fetchedCommunities = try await service.fetchCategories()
            self.communities = fetchedCommunities
            // Save to DB (now thread-safe via dbQueue)
            let serviceId = self.service.id
            Task.detached {
                DatabaseManager.shared.saveCommunities(fetchedCommunities, forService: serviceId)
            }
        } catch is CancellationError {
            // Ignore
        } catch let error as URLError where error.code == .cancelled {
            // Ignore
        } catch {
            print("Failed to fetch data: \(error)")
        }
    }
    
    func refresh() async {
        await loadData()
    }
}
