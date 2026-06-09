import Foundation
import Combine

class DiscourseService: ForumService {
    var name: String { "Linux.do" }
    var id: String { "linux_do" }
    var logo: String { "https://linux.do/uploads/default/original/4X/c/c/d/ccd8c210609d498cbeb3d5201d4c259348447562.png" }
    
    private let baseURL = "https://linux.do"
    
    func getWebURL(for thread: Thread) -> String {
        return "\(baseURL)/t/\(thread.id)"
    }
    
    func postComment(topicId: String, categoryId: String, content: String) async throws {
        // Discourse implementation would use the /posts endpoint
    }
    
    func createThread(categoryId: String, title: String, content: String) async throws {
        // Discourse implementation would use the /posts endpoint with title/category
    }
    
    // MARK: - DTOs
    struct SiteResponse: Codable {
        let categoryList: CategoryList
        enum CodingKeys: String, CodingKey {
            case categoryList = "category_list"
        }
    }
    
    struct CategoryList: Codable {
        let categories: [DiscourseCategory]
    }
    
    struct DiscourseCategory: Codable {
        let id: Int
        let name: String
        let description: String?
        let slug: String
        let topicCount: Int?
        
        enum CodingKeys: String, CodingKey {
            case id, name, description, slug
            case topicCount = "topic_count"
        }
    }
    
    struct LatestResponse: Codable {
        let users: [DiscourseUser]?
        let threadList: DiscourseThreadList
        
        enum CodingKeys: String, CodingKey {
            case users
            case threadList = "topic_list"
        }
    }
    
    struct DiscourseThreadList: Codable {
        let threads: [DiscourseThread]
        enum CodingKeys: String, CodingKey {
            case threads = "topics"
        }
    }
    
    struct DiscourseThread: Codable {
        let id: Int
        let title: String
        let fancyTitle: String?
        let slug: String
        let postsCount: Int
        let replyCount: Int?
        let likeCount: Int?
        let views: Int
        let createdAt: String
        let bumpedAt: String?
        let categoryId: Int?
        let tags: [String]?
        let posters: [ThreadPoster]?
        
        enum CodingKeys: String, CodingKey {
            case id, title, slug, views, tags
            case fancyTitle = "fancy_title"
            case postsCount = "posts_count"
            case replyCount = "reply_count"
            case likeCount = "like_count"
            case createdAt = "created_at"
            case bumpedAt = "bumped_at"
            case categoryId = "category_id"
            case posters
        }
        
        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(Int.self, forKey: .id)
            title = try container.decode(String.self, forKey: .title)
            fancyTitle = try container.decodeIfPresent(String.self, forKey: .fancyTitle)
            slug = try container.decode(String.self, forKey: .slug)
            postsCount = try container.decode(Int.self, forKey: .postsCount)
            replyCount = try container.decodeIfPresent(Int.self, forKey: .replyCount)
            likeCount = try container.decodeIfPresent(Int.self, forKey: .likeCount)
            views = try container.decode(Int.self, forKey: .views)
            createdAt = try container.decode(String.self, forKey: .createdAt)
            bumpedAt = try container.decodeIfPresent(String.self, forKey: .bumpedAt)
            categoryId = try container.decodeIfPresent(Int.self, forKey: .categoryId)
            posters = try container.decodeIfPresent([ThreadPoster].self, forKey: .posters)
            
            // tags can be [String] or [{"name": "...", ...}]
            if let stringTags = try? container.decode([String].self, forKey: .tags) {
                tags = stringTags
            } else if let dictTags = try? container.decode([[String: AnyCodable]].self, forKey: .tags) {
                tags = dictTags.compactMap { $0["name"]?.stringValue }
            } else {
                tags = nil
            }
        }
    }
    
    // Helper for decoding mixed-type JSON values
    struct AnyCodable: Codable {
        let value: Any
        
        var stringValue: String? {
            value as? String
        }
        
        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            if let str = try? container.decode(String.self) { value = str }
            else if let int = try? container.decode(Int.self) { value = int }
            else if let bool = try? container.decode(Bool.self) { value = bool }
            else if let dbl = try? container.decode(Double.self) { value = dbl }
            else { value = "" }
        }
        
        func encode(to encoder: Encoder) throws {
            var container = encoder.singleValueContainer()
            if let str = value as? String { try container.encode(str) }
            else if let int = value as? Int { try container.encode(int) }
            else if let bool = value as? Bool { try container.encode(bool) }
            else if let dbl = value as? Double { try container.encode(dbl) }
        }
    }
    
    struct ThreadPoster: Codable {
        let userId: Int
        let description: String
        enum CodingKeys: String, CodingKey {
            case userId = "user_id"
            case description
        }
    }
    
    struct DiscourseUser: Codable {
        let id: Int
        let username: String
        let avatarTemplate: String
        enum CodingKeys: String, CodingKey {
            case id, username
            case avatarTemplate = "avatar_template"
        }
    }
    
    struct ThreadDetailResponse: Codable {
        let id: Int
        let title: String
        let threadStream: ThreadStream
        let tags: [String]?
        
        enum CodingKeys: String, CodingKey {
            case id, title, tags
            case threadStream = "post_stream"
        }
        
        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(Int.self, forKey: .id)
            title = try container.decode(String.self, forKey: .title)
            threadStream = try container.decode(ThreadStream.self, forKey: .threadStream)
            
            // tags can be [String] or [{"name": "...", ...}]
            if let stringTags = try? container.decode([String].self, forKey: .tags) {
                tags = stringTags
            } else if let dictTags = try? container.decode([[String: AnyCodable]].self, forKey: .tags) {
                tags = dictTags.compactMap { $0["name"]?.stringValue }
            } else {
                tags = nil
            }
        }
    }
    
    struct ThreadStream: Codable {
        let posts: [DiscourseThreadItem]
    }
    
    struct DiscourseThreadItem: Codable {
        let id: Int
        let userId: Int?
        let username: String
        let avatarTemplate: String
        let cooked: String
        let createdAt: String
        let postNumber: Int
        let replyCount: Int?
        let score: Double?
        let primaryGroupName: String?
        let admin: Bool?
        let moderator: Bool?
        
        enum CodingKeys: String, CodingKey {
            case id, username, cooked, score, admin, moderator
            case userId = "user_id"
            case avatarTemplate = "avatar_template"
            case createdAt = "created_at"
            case postNumber = "post_number"
            case replyCount = "reply_count"
            case primaryGroupName = "primary_group_name"
        }
    }
    
    // MARK: - Fetching
    
    func fetchCategories() async throws -> [Community] {
        // Use "latest" as the primary feed since linux.do requires auth for category pages
        var communities = [
            Community(
                id: "latest",
                name: "最新",
                description: "Latest topics",
                category: "General",
                activeToday: 0,
                onlineNow: 0
            )
        ]
        
        // Try to fetch categories for display, but these may require auth
        if let url = URL(string: "\(baseURL)/categories.json") {
            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                    let siteResponse = try JSONDecoder().decode(SiteResponse.self, from: data)
                    let cats = siteResponse.categoryList.categories.map { cat in
                        Community(
                            id: "\(cat.slug)/\(cat.id)",
                            name: cat.name,
                            description: cat.description ?? "",
                            category: "General",
                            activeToday: cat.topicCount ?? 0,
                            onlineNow: 0
                        )
                    }
                    communities.append(contentsOf: cats)
                }
            } catch {
                print("[Discourse] Failed to fetch categories: \(error)")
            }
        }
        
        return communities
    }
    
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [Thread] {
        let urlStr: String
        if categoryId == "latest" {
            urlStr = "\(baseURL)/latest.json?page=\(page - 1)"
        } else {
            // categoryId is "slug/id" format
            urlStr = "\(baseURL)/c/\(categoryId)/l/latest.json?page=\(page - 1)"
        }
        
        guard let url = URL(string: urlStr) else {
            throw URLError(.badURL)
        }
        
        let (data, response) = try await URLSession.shared.data(from: url)
        
        if let httpResponse = response as? HTTPURLResponse {
            print("[Discourse] Fetching \(urlStr) - Status: \(httpResponse.statusCode)")
            // If category page requires auth, fall back to latest
            if httpResponse.statusCode == 403 && categoryId != "latest" {
                print("[Discourse] Category requires auth, falling back to /latest.json")
                guard let fallbackURL = URL(string: "\(baseURL)/latest.json?page=\(page - 1)") else {
                    throw URLError(.badURL)
                }
                let (fallbackData, _) = try await URLSession.shared.data(from: fallbackURL)
                return try parseThreadList(data: fallbackData, communities: communities)
            }
        }
        
        return try parseThreadList(data: data, communities: communities)
    }
    
    private func parseThreadList(data: Data, communities: [Community]) throws -> [Thread] {
        let response = try JSONDecoder().decode(LatestResponse.self, from: data)
        
        let users = response.users ?? []
        let userMap = Dictionary(uniqueKeysWithValues: users.map { ($0.id, $0) })
        let communityMap = Dictionary(uniqueKeysWithValues: communities.map { ($0.id, $0) })
        
        var threads: [Thread] = []
        
        for threadItem in response.threadList.threads {
            let opId = threadItem.posters?.first(where: { $0.description.contains("Original Poster") })?.userId ?? threadItem.posters?.first?.userId ?? 0
            
            let user = userMap[opId]
            let categoryId = String(threadItem.categoryId ?? 0)
            let community = communityMap[categoryId] ?? Community(id: categoryId, name: "Unknown", description: "", category: "Unknown", activeToday: 0, onlineNow: 0)
            
            let avatarURL = (user?.avatarTemplate ?? "/user_avatar/linux.do/system/{size}/1.png")
                .replacingOccurrences(of: "{size}", with: "64")
            
            let fullAvatarURL = avatarURL.starts(with: "http") ? avatarURL : "\(baseURL)\(avatarURL)"
            
            let threadAuthor = User(
                id: String(user?.id ?? 0),
                username: user?.username ?? "Unknown",
                avatar: fullAvatarURL,
                role: nil
            )
            
            let thread = Thread(
                id: String(threadItem.id),
                title: threadItem.title,
                content: threadItem.fancyTitle ?? "",
                author: threadAuthor,
                community: community,
                timeAgo: calculateTimeAgo(from: threadItem.createdAt),
                likeCount: threadItem.likeCount ?? 0,
                commentCount: (threadItem.postsCount) - 1,
                isLiked: false,
                tags: threadItem.tags
            )
            
            threads.append(thread)
        }
        
        return threads
    }
    
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
         guard let url = URL(string: "\(baseURL)/t/\(threadId).json?page=\(page)") else {
             throw URLError(.badURL)
         }
         
         let (data, response) = try await URLSession.shared.data(from: url)
         
         if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 403 {
             throw URLError(.userAuthenticationRequired)
         }
         
         let detailResponse = try JSONDecoder().decode(ThreadDetailResponse.self, from: data)
         
         let threadItems = detailResponse.threadStream.posts
         guard let firstItem = threadItems.first else {
             throw URLError(.cannotParseResponse)
         }
         
         let opAvatarURL = firstItem.avatarTemplate.replacingOccurrences(of: "{size}", with: "64")
         let opFullAvatarURL = opAvatarURL.starts(with: "http") ? opAvatarURL : "\(baseURL)\(opAvatarURL)"
         
         let opRole = firstItem.primaryGroupName ?? (firstItem.admin == true ? "Admin" : (firstItem.moderator == true ? "Moderator" : nil))
         let opUser = User(
            id: String(firstItem.userId ?? 0),
            username: firstItem.username,
            avatar: opFullAvatarURL,
            role: opRole
         )
         
         let mainThread = Thread(
            id: String(detailResponse.id),
            title: detailResponse.title,
            content: cleanContent(firstItem.cooked),
            author: opUser,
            community: Community(id: "0", name: "", description: "", category: "", activeToday: 0, onlineNow: 0),
            timeAgo: calculateTimeAgo(from: firstItem.createdAt),
            likeCount: Int(firstItem.score ?? 0),
            commentCount: threadItems.count - 1,
            isLiked: false,
            tags: detailResponse.tags
         )
         
         var comments: [Comment] = []
         
         for p in threadItems.dropFirst() {
             let avatarURL = p.avatarTemplate.replacingOccurrences(of: "{size}", with: "64")
             let fullAvatarURL = avatarURL.starts(with: "http") ? avatarURL : "\(baseURL)\(avatarURL)"
             
            let role = p.primaryGroupName ?? (p.admin == true ? "Admin" : (p.moderator == true ? "Moderator" : nil))
            let user = User(id: String(p.userId ?? 0), username: p.username, avatar: fullAvatarURL, role: role)
             
             let comment = Comment(
                id: String(p.id),
                author: user,
                content: cleanContent(p.cooked),
                timeAgo: calculateTimeAgo(from: p.createdAt),
                likeCount: Int(p.score ?? 0),
                replies: nil
             )
             comments.append(comment)
         }
         
         return (mainThread, comments, nil)
    }
    
    private func cleanContent(_ html: String) -> String {
        var processed = html
        
        // 0. Pre-process emojis and avatars (Discourse specific)
        do {
            let avatarRegex = try NSRegularExpression(pattern: "<img[^>]*class=\"[^\"]*avatar[^\"]*\"[^>]*>", options: .caseInsensitive)
            processed = avatarRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "")
            
            let iconRegex = try NSRegularExpression(pattern: "<img[^>]*class=\"[^\"]*site-icon[^\"]*\"[^>]*>", options: .caseInsensitive)
            processed = iconRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "")
            
            let metaRegex = try NSRegularExpression(pattern: "<span class=\"(informations|filename|meta)\"[^>]*>.*?</span>", options: [.caseInsensitive, .dotMatchesLineSeparators])
            processed = metaRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "")
            
            let emojiRegex = try NSRegularExpression(pattern: "<img[^>]*class=\"[^\"]*emoji[^\"]*\"[^>]*alt=\"([^\"]+)\"[^>]*>", options: .caseInsensitive)
            let range = NSRange(processed.startIndex..., in: processed)
            let matches = emojiRegex.matches(in: processed, range: range)
            
            for match in matches.reversed() {
                if let altRange = Range(match.range(at: 1), in: processed),
                   let fullMatchRange = Range(match.range, in: processed) {
                    let altText = String(processed[altRange])
                    processed.replaceSubrange(fullMatchRange, with: altText)
                }
            }
        } catch {
            print("Regex error in cleanContent: \(error)")
        }

        // 1. Extract images
        do {
            let regex = try NSRegularExpression(pattern: "<img[^>]+src=\"([^\">]+)\"[^>]*>", options: .caseInsensitive)
            let range = NSRange(processed.startIndex..., in: processed)
            let matches = regex.matches(in: processed, range: range)
            
            for match in matches.reversed() {
                if let srcRange = Range(match.range(at: 1), in: processed) {
                    let src = String(processed[srcRange])
                    let fullSrc = src.starts(with: "http") ? src : "\(baseURL)\(src)"
                    
                    if let fullMatchRange = Range(match.range, in: processed) {
                        processed.replaceSubrange(fullMatchRange, with: "\n[IMAGE:\(fullSrc)]\n")
                    }
                }
            }
        } catch {
            print("Regex error in cleanContent (images): \(error)")
        }
        
        // 2. Formatting
        processed = processed.replacingOccurrences(of: "<br>", with: "\n")
                             .replacingOccurrences(of: "</p>", with: "\n\n")
        
        // 3. Strip tags
        processed = processed.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression, range: nil)
        
        // 4. Entities
        processed = processed
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&apos;", with: "'")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&#8217;", with: "'")
            .replacingOccurrences(of: "&#8220;", with: "\"")
            .replacingOccurrences(of: "&#8221;", with: "\"")
            .replacingOccurrences(of: "&#8211;", with: "-")
            .replacingOccurrences(of: "&#8212;", with: "--")
            .replacingOccurrences(of: "&hellip;", with: "...")
            
        // 5. Spacing
        if let newlineRegex = try? NSRegularExpression(pattern: "(\\s*\\n\\s*){3,}", options: []) {
            processed = newlineRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "\n\n")
        }
        
        if let blankLineRegex = try? NSRegularExpression(pattern: "\\n\\s+\\n", options: []) {
            processed = blankLineRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "\n\n")
        }

        return processed.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
