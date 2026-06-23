import Foundation

struct User: Identifiable, Hashable, Codable {
    let id: String
    let username: String
    let avatar: String // Image name or URL
    let role: String? // e.g., "Product Designer"
}

struct Community: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let description: String
    let category: String
    let activeToday: Int
    let onlineNow: Int
}

struct Thread: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let content: String
    let author: User
    let community: Community
    let timeAgo: String
    let likeCount: Int
    let commentCount: Int
    var isLiked: Bool = false
    var tags: [String]? = nil
    var lastPostTime: String? = nil
    var lastPosterName: String? = nil
}

struct Comment: Identifiable, Hashable, Codable {
    let id: String
    let author: User
    let content: String
    let timeAgo: String
    let likeCount: Int
    let replies: [Comment]?
}

struct Category: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let description: String
}
