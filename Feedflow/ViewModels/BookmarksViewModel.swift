import Foundation
import SwiftUI
import Combine

@MainActor
class BookmarksViewModel: ObservableObject {
    @Published var bookmarkedThreads: [(Thread, String)] = []
    @Published var urlBookmarks: [(String, String, Date)] = []  // (url, title, date)

    func loadBookmarks() {
        bookmarkedThreads = DatabaseManager.shared.getBookmarkedThreads()
        urlBookmarks = DatabaseManager.shared.getURLBookmarks()
    }

    func removeURLBookmark(url: String) {
        DatabaseManager.shared.removeURLBookmark(url: url)
        urlBookmarks.removeAll { $0.0 == url }
    }

    func getService(for id: String) -> ForumService {
        switch id {
        case "4d4y": return FourD4YService()
        case "linux_do": return DiscourseService()
        case "hackernews": return HackerNewsService()
        case "v2ex": return V2EXService()
        case "rss": return RSSService()
        case "zhihu": return ZhihuService()
        default: return FourD4YService() // Fallback
        }
    }
}
