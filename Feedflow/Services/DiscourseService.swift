import Foundation
import Combine
import WebKit

class DiscourseService: ForumService {
    var name: String { "Linux.do" }
    var id: String { "linux_do" }
    var logo: String { "terminal.fill" }
    var requiresLogin: Bool { true }
    var supportsCommenting: Bool { true }
    var supportsThreadCreation: Bool { true }

    private let baseURL = "https://linux.do"
    private let threadPageSize = 20
    private var csrfToken: String?

    func getWebURL(for thread: Thread) -> String {
        return "\(baseURL)/t/\(thread.id)"
    }

    func restoreSession() async -> Bool {
        let savedCookies = linuxCookies(from: DatabaseManager.shared.getCookies(siteId: id) ?? [])

        if !savedCookies.isEmpty {
            syncCookies(savedCookies)
            if await validateSession() {
                return true
            }
            AppLogger.debug("[Discourse] Saved cookies did not validate; checking WKWebView cookies")
        }

        let webCookies = linuxCookies(from: await webKitCookies(for: "linux.do"))
        if !webCookies.isEmpty {
            AppLogger.debug("[Discourse] Importing \(webCookies.count) Linux.do cookies from WKWebView")
            DatabaseManager.shared.replaceCookies(siteId: id, cookies: webCookies)
            syncCookies(webCookies)

            if await validateSession() {
                return true
            }
        }

        return false
    }

    func postComment(topicId: String, categoryId: String, content: String) async throws {
        guard let numericTopicId = Int(topicId) else {
            throw NSError(
                domain: "DiscourseService",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "Invalid Linux.do topic id."]
            )
        }

        try await createPost(payload: [
            "topic_id": numericTopicId,
            "raw": content
        ])
    }

    func createThread(categoryId: String, title: String, content: String) async throws {
        guard let numericCategoryId = numericCategoryId(from: categoryId) else {
            throw NSError(
                domain: "DiscourseService",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "Choose a Linux.do category before creating a topic."]
            )
        }

        try await createPost(payload: [
            "title": title,
            "raw": content,
            "category": numericCategoryId
        ])
    }

    func canCreateThread(in community: Community) -> Bool {
        numericCategoryId(from: community.id) != nil
    }

    private func createPost(payload: [String: Any]) async throws {
        guard await restoreSession() else {
            throw URLError(.userAuthenticationRequired)
        }

        let token = try await fetchCSRFToken()
        guard let url = URL(string: "\(baseURL)/posts.json") else {
            throw URLError(.badURL)
        }

        var request = authorizedRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(token, forHTTPHeaderField: "X-CSRF-Token")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { return }

        if !(200..<300).contains(httpResponse.statusCode) {
            let message = parseDiscourseError(data: data) ?? "Linux.do rejected the post (HTTP \(httpResponse.statusCode))."
            throw NSError(domain: "DiscourseService", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: message])
        }
    }

    private func fetchCSRFToken() async throws -> String {
        if let csrfToken {
            return csrfToken
        }

        guard let url = URL(string: "\(baseURL)/session/csrf.json") else {
            throw URLError(.badURL)
        }

        var request = authorizedRequest(url: url)
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await URLSession.shared.data(for: request)
        if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 403 {
            throw URLError(.userAuthenticationRequired)
        }

        let decoded = try JSONDecoder().decode(CSRFResponse.self, from: data)
        csrfToken = decoded.csrf
        return decoded.csrf
    }

    private func authorizedRequest(url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.setValue(baseURL, forHTTPHeaderField: "Origin")
        request.setValue(baseURL, forHTTPHeaderField: "Referer")

        let cookies = DatabaseManager.shared.getCookies(siteId: id) ?? []
        let relevant = cookies.filter { $0.domain.contains("linux.do") }
        if !relevant.isEmpty {
            request.setValue(relevant.map { "\($0.name)=\($0.value)" }.joined(separator: "; "), forHTTPHeaderField: "Cookie")
            request.httpShouldHandleCookies = false
        }

        return request
    }

    private func linuxCookies(from cookies: [HTTPCookie]) -> [HTTPCookie] {
        cookies.filter { $0.domain.contains("linux.do") }
    }

    private func syncCookies(_ cookies: [HTTPCookie]) {
        for cookie in cookies {
            HTTPCookieStorage.shared.setCookie(cookie)
        }
    }

    @MainActor
    private func webKitCookies(for domain: String) async -> [HTTPCookie] {
        await withCheckedContinuation { continuation in
            WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
                continuation.resume(returning: cookies.filter { $0.domain.contains(domain) })
            }
        }
    }

    private func numericCategoryId(from categoryId: String) -> Int? {
        if let id = Int(categoryId) {
            return id
        }

        return categoryId
            .split(separator: "/")
            .last
            .flatMap { Int($0) }
    }

    private func parseDiscourseError(data: Data) -> String? {
        guard
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return String(data: data, encoding: .utf8) }

        if let errors = object["errors"] as? [String], !errors.isEmpty {
            return errors.joined(separator: "\n")
        }

        if let error = object["error"] as? String {
            return error
        }

        return nil
    }

    // MARK: - DTOs
    struct CSRFResponse: Codable {
        let csrf: String
    }

    private func validateSession() async -> Bool {
        guard let url = URL(string: "\(baseURL)/session/current.json") else {
            return false
        }

        do {
            var request = authorizedRequest(url: url)
            request.setValue("application/json", forHTTPHeaderField: "Accept")
            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse,
                  (200..<300).contains(httpResponse.statusCode)
            else {
                AppLogger.debug("[Discourse] Session validation failed: session/current.json was not accessible")
                return false
            }

            guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                AppLogger.debug("[Discourse] Session validation failed: invalid current session payload")
                return false
            }

            let currentUser = object["current_user"]
            let isReady = currentUser != nil && !(currentUser is NSNull)
            AppLogger.debug("[Discourse] Session validation \(isReady ? "succeeded" : "failed"): current_user=\(isReady)")
            return isReady
        } catch {
            AppLogger.debug("[Discourse] Session validation error: \(error)")
            return false
        }
    }

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
            title = try container.decode(String.self, forKey: .title).decodingHTMLEntities()
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
        let stream: [Int]?
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
                let (data, response) = try await URLSession.shared.data(for: authorizedRequest(url: url))
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
                AppLogger.debug("[Discourse] Failed to fetch categories: \(error)")
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

        let (data, response) = try await URLSession.shared.data(for: authorizedRequest(url: url))

        if let httpResponse = response as? HTTPURLResponse {
            AppLogger.debug("[Discourse] Fetching \(urlStr) - Status: \(httpResponse.statusCode)")
            // If category page requires auth, fall back to latest
            if httpResponse.statusCode == 403 && categoryId != "latest" {
                AppLogger.debug("[Discourse] Category requires auth, falling back to /latest.json")
                guard let fallbackURL = URL(string: "\(baseURL)/latest.json?page=\(page - 1)") else {
                    throw URLError(.badURL)
                }
                let (fallbackData, _) = try await URLSession.shared.data(for: authorizedRequest(url: fallbackURL))
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
        let communityMapByNumericId = Dictionary(
            uniqueKeysWithValues: communities.compactMap { community -> (String, Community)? in
                guard let id = numericCategoryId(from: community.id) else { return nil }
                return (String(id), community)
            }
        )

        var threads: [Thread] = []

        for threadItem in response.threadList.threads {
            let opId = threadItem.posters?.first(where: { $0.description.contains("Original Poster") })?.userId ?? threadItem.posters?.first?.userId ?? 0

            let user = userMap[opId]
            let categoryId = String(threadItem.categoryId ?? 0)
            let community = communityMap[categoryId] ?? communityMapByNumericId[categoryId] ?? Community(id: categoryId, name: "Unknown", description: "", category: "Unknown", activeToday: 0, onlineNow: 0)

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
        guard await restoreSession() else {
            throw URLError(.userAuthenticationRequired)
        }

        guard let url = URL(string: "\(baseURL)/t/\(threadId).json?page=\(page)") else {
            throw URLError(.badURL)
        }

        let (data, response) = try await URLSession.shared.data(for: authorizedRequest(url: url))

        if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 403 {
            throw URLError(.userAuthenticationRequired)
        }

        let detailResponse = try JSONDecoder().decode(ThreadDetailResponse.self, from: data)
        let initialItems = detailResponse.threadStream.posts
        guard let firstItem = initialItems.first else {
            throw URLError(.cannotParseResponse)
        }

        let streamIds = detailResponse.threadStream.stream ?? initialItems.map(\.id)
        let requestedIds = postIdsForPage(streamIds: streamIds, page: page)
        var itemsById = Dictionary(uniqueKeysWithValues: initialItems.map { ($0.id, $0) })

        let missingIds = requestedIds.filter { itemsById[$0] == nil }
        AppLogger.debug("[Discourse] Topic \(threadId) page \(page): initial=\(initialItems.count), stream=\(streamIds.count), requested=\(requestedIds.count), missing=\(missingIds.count)")
        let fetchedItems = try await fetchThreadPosts(postIds: missingIds)
        for item in fetchedItems {
            itemsById[item.id] = item
        }

        let orderedItems = requestedIds.compactMap { itemsById[$0] }
        let opItem = page == 1 ? (orderedItems.first ?? firstItem) : firstItem
        let commentItems: [DiscourseThreadItem]
        if page == 1 {
            commentItems = orderedItems.filter { $0.id != opItem.id }
        } else {
            commentItems = orderedItems
        }

        let mainThread = makeThread(from: opItem, response: detailResponse, commentCount: max(streamIds.count - 1, initialItems.count - 1))
        let comments = commentItems.map(makeComment)
        let totalPages = max(1, Int(ceil(Double(max(streamIds.count - 1, 0)) / Double(threadPageSize))))

        return (mainThread, comments, totalPages)
    }

    private func postIdsForPage(streamIds: [Int], page: Int) -> [Int] {
        guard !streamIds.isEmpty else { return [] }

        if page <= 1 {
            return Array(streamIds.prefix(threadPageSize + 1))
        }

        let replyIds = Array(streamIds.dropFirst())
        let startIndex = max(0, (page - 1) * threadPageSize)
        return Array(replyIds.dropFirst(startIndex).prefix(threadPageSize))
    }

    private func fetchThreadPosts(postIds: [Int]) async throws -> [DiscourseThreadItem] {
        var posts: [DiscourseThreadItem] = []

        for postId in postIds {
            do {
                posts.append(try await fetchThreadPost(postId: postId))
            } catch let error as URLError where error.code == .userAuthenticationRequired {
                throw URLError(.userAuthenticationRequired)
            } catch {
                AppLogger.debug("[Discourse] Failed to fetch post \(postId): \(error)")
            }
        }

        return posts
    }

    private func fetchThreadPost(postId: Int) async throws -> DiscourseThreadItem {
        guard let url = URL(string: "\(baseURL)/posts/\(postId).json") else {
            throw URLError(.badURL)
        }

        let (data, response) = try await URLSession.shared.data(for: authorizedRequest(url: url))

        if let httpResponse = response as? HTTPURLResponse {
            if httpResponse.statusCode == 403 {
                throw URLError(.userAuthenticationRequired)
            }
            guard (200..<300).contains(httpResponse.statusCode) else {
                throw URLError(.badServerResponse)
            }
        }

        return try JSONDecoder().decode(DiscourseThreadItem.self, from: data)
    }

    private func makeThread(from item: DiscourseThreadItem, response: ThreadDetailResponse, commentCount: Int) -> Thread {
        Thread(
            id: String(response.id),
            title: response.title,
            content: cleanContent(item.cooked),
            author: makeUser(from: item),
            community: Community(id: "0", name: "", description: "", category: "", activeToday: 0, onlineNow: 0),
            timeAgo: calculateTimeAgo(from: item.createdAt),
            likeCount: Int(item.score ?? 0),
            commentCount: commentCount,
            isLiked: false,
            tags: response.tags
        )
    }

    private func makeComment(from item: DiscourseThreadItem) -> Comment {
        Comment(
            id: String(item.id),
            author: makeUser(from: item),
            content: cleanContent(item.cooked),
            timeAgo: calculateTimeAgo(from: item.createdAt),
            likeCount: Int(item.score ?? 0),
            replies: nil
        )
    }

    private func makeUser(from item: DiscourseThreadItem) -> User {
        let avatarURL = item.avatarTemplate.replacingOccurrences(of: "{size}", with: "64")
        let fullAvatarURL = avatarURL.starts(with: "http") ? avatarURL : "\(baseURL)\(avatarURL)"
        let role = item.primaryGroupName ?? (item.admin == true ? "Admin" : (item.moderator == true ? "Moderator" : nil))

        return User(
            id: String(item.userId ?? 0),
            username: item.username,
            avatar: fullAvatarURL,
            role: role
        )
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
            AppLogger.debug("Regex error in cleanContent: \(error)")
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
            AppLogger.debug("Regex error in cleanContent (images): \(error)")
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
