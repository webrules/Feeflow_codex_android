import Foundation

// MARK: - Zhihu API Response Models

/// Represents a single feed item from Zhihu's recommendation API
struct ZhihuFeedItem: Codable {
    let id: String?
    let type: String?       // "feed", "feed_advert", etc.
    let verb: String?       // action type
    let target: ZhihuTarget?
    let brief: String?
    let attachedInfo: String?
    let actionText: String?
    let cursor: String?

    enum CodingKeys: String, CodingKey {
        case id, type, verb, target, brief
        case attachedInfo = "attached_info"
        case actionText = "action_text"
        case cursor
    }
}

/// Represents the target content within a feed item
struct ZhihuTarget: Codable {
    let id: Int?
    let type: String?          // "answer", "article", "zvideo", "question"
    let url: String?
    let title: String?
    let excerpt: String?
    let content: String?
    let author: ZhihuAuthor?
    let question: ZhihuQuestion?
    let voteupCount: Int?
    let commentCount: Int?
    let thanksCount: Int?
    let createdTime: Int?
    let updatedTime: Int?
    let thumbnail: String?
    let thumbnailExtraInfo: ZhihuThumbnailInfo?

    enum CodingKeys: String, CodingKey {
        case id, type, url, title, excerpt, content, author, question, thumbnail
        case voteupCount = "voteup_count"
        case commentCount = "comment_count"
        case thanksCount = "thanks_count"
        case createdTime = "created_time"
        case updatedTime = "updated_time"
        case thumbnailExtraInfo = "thumbnail_extra_info"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // id can be Int or String in the API response
        if let intId = try? container.decode(Int.self, forKey: .id) {
            self.id = intId
        } else if let strId = try? container.decode(String.self, forKey: .id) {
            self.id = Int(strId)
        } else {
            self.id = nil
        }
        self.type = try container.decodeIfPresent(String.self, forKey: .type)
        self.url = try container.decodeIfPresent(String.self, forKey: .url)
        self.title = try container.decodeIfPresent(String.self, forKey: .title)
        self.excerpt = try container.decodeIfPresent(String.self, forKey: .excerpt)
        self.content = try container.decodeIfPresent(String.self, forKey: .content)
        self.author = try container.decodeIfPresent(ZhihuAuthor.self, forKey: .author)
        self.question = try container.decodeIfPresent(ZhihuQuestion.self, forKey: .question)
        self.voteupCount = try container.decodeIfPresent(Int.self, forKey: .voteupCount)
        self.commentCount = try container.decodeIfPresent(Int.self, forKey: .commentCount)
        self.thanksCount = try container.decodeIfPresent(Int.self, forKey: .thanksCount)
        self.createdTime = try container.decodeIfPresent(Int.self, forKey: .createdTime)
        self.updatedTime = try container.decodeIfPresent(Int.self, forKey: .updatedTime)
        self.thumbnail = try container.decodeIfPresent(String.self, forKey: .thumbnail)
        self.thumbnailExtraInfo = try container.decodeIfPresent(ZhihuThumbnailInfo.self, forKey: .thumbnailExtraInfo)
    }

    /// Effective title: for answers, use question title; otherwise use target title
    var effectiveTitle: String {
        if type == "answer", let qTitle = question?.title, !qTitle.isEmpty {
            return qTitle
        }
        return title ?? "Untitled"
    }

    /// Description for content type
    var typeDescription: String {
        switch type {
        case "answer": return "回答"
        case "article": return "文章"
        case "zvideo": return "视频"
        case "question": return "问题"
        case "pin": return "想法"
        default: return type ?? "未知"
        }
    }

    /// Details line (vote count, comment count)
    var detailsText: String {
        let votes = voteupCount ?? 0
        let comments = commentCount ?? 0
        switch type {
        case "answer":
            return "回答 · \(votes) 赞同 · \(comments) 评论"
        case "article":
            return "文章 · \(votes) 赞 · \(comments) 评论"
        case "zvideo":
            return "视频 · \(votes) 赞 · \(comments) 评论"
        case "question":
            return "问题 · \(comments) 评论"
        default:
            return "\(typeDescription) · \(votes) 赞"
        }
    }

    /// Filter reason: returns nil if feed should be shown, or a reason string if it should be hidden
    /// Mirrors zhihu-plus-plus local recommendation logic
    func filterReason() -> String? {
        switch type {
        case "answer":
            let votes = voteupCount ?? 0
            let isFollowing = author?.isFollowing ?? false
            if votes < 10 && !isFollowing {
                return "规则：回答；赞数 < 10，未关注作者"
            }
        case "article":
            let votes = voteupCount ?? 0
            let followers = author?.followerCount ?? 0
            let isFollowing = author?.isFollowing ?? false
            if (followers < 50 || votes < 20) && !isFollowing {
                return "规则：文章；作者粉丝数 < 50 或 赞数 < 20，未关注作者"
            }
        case "zvideo":
            let votes = voteupCount ?? 0
            let followers = author?.followerCount ?? 0
            let isFollowing = author?.isFollowing ?? false
            if followers < 50 && votes < 20 && !isFollowing {
                return "规则：视频"
            }
        default:
            break
        }
        return nil
    }
}

struct ZhihuAuthor: Codable {
    let id: String?
    let name: String?
    let headline: String?
    let avatarUrl: String?
    let avatarUrlTemplate: String?
    let type: String?
    let url: String?
    let urlToken: String?
    let isOrg: Bool?
    let gender: Int?
    let followerCount: Int?
    let isFollowing: Bool?
    let isFollowed: Bool?

    enum CodingKeys: String, CodingKey {
        case id, name, headline, type, url, gender
        case avatarUrl = "avatar_url"
        case avatarUrlTemplate = "avatar_url_template"
        case urlToken = "url_token"
        case isOrg = "is_org"
        case followerCount = "follower_count"
        case isFollowing = "is_following"
        case isFollowed = "is_followed"
    }
}

struct ZhihuQuestion: Codable {
    let id: Int?
    let title: String?
    let type: String?
    let url: String?
    let answerCount: Int?
    let followerCount: Int?
    let commentCount: Int?

    enum CodingKeys: String, CodingKey {
        case id, title, type, url
        case answerCount = "answer_count"
        case followerCount = "follower_count"
        case commentCount = "comment_count"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let intId = try? container.decode(Int.self, forKey: .id) {
            self.id = intId
        } else if let strId = try? container.decode(String.self, forKey: .id) {
            self.id = Int(strId)
        } else {
            self.id = nil
        }
        self.title = try container.decodeIfPresent(String.self, forKey: .title)
        self.type = try container.decodeIfPresent(String.self, forKey: .type)
        self.url = try container.decodeIfPresent(String.self, forKey: .url)
        self.answerCount = try container.decodeIfPresent(Int.self, forKey: .answerCount)
        self.followerCount = try container.decodeIfPresent(Int.self, forKey: .followerCount)
        self.commentCount = try container.decodeIfPresent(Int.self, forKey: .commentCount)
    }
}

struct ZhihuThumbnailInfo: Codable {
    let count: Int?
    let type: String?
}

/// Response from Zhihu's recommend API
struct ZhihuFeedResponse: Codable {
    let data: [ZhihuFeedItem]?
    let paging: ZhihuPaging?
    let freshText: String?

    enum CodingKeys: String, CodingKey {
        case data, paging
        case freshText = "fresh_text"
    }
}

// MARK: - Hot List Response Models

/// Hot list has a different structure from recommend feed
struct ZhihuHotListResponse: Codable {
    let data: [ZhihuHotListItem]?
    let paging: ZhihuPaging?
}

struct ZhihuHotListItem: Codable {
    let type: String?           // "hot_list_feed"
    let styleType: String?      // "1"
    let id: String?
    let cardId: String?
    let target: ZhihuHotListTarget?
    let detailText: String?     // "123万热度"
    let children: [ZhihuHotListChild]?

    enum CodingKeys: String, CodingKey {
        case type, id, target, children
        case styleType = "style_type"
        case cardId = "card_id"
        case detailText = "detail_text"
    }
}

struct ZhihuHotListTarget: Codable {
    let id: Int?
    let titleArea: ZhihuTextArea?
    let excerptArea: ZhihuTextArea?
    let imageArea: ZhihuImageArea?
    let url: String?
    let type: String?
    let created: Int?
    let metricsArea: ZhihuMetricsArea?

    enum CodingKeys: String, CodingKey {
        case id, url, type, created
        case titleArea = "title_area"
        case excerptArea = "excerpt_area"
        case imageArea = "image_area"
        case metricsArea = "metrics_area"
    }
}

struct ZhihuTextArea: Codable {
    let text: String?
}

struct ZhihuImageArea: Codable {
    let url: String?
}

struct ZhihuMetricsArea: Codable {
    let text: String?
    let followerCount: Int?
    let answerCount: Int?

    enum CodingKeys: String, CodingKey {
        case text
        case followerCount = "follower_count"
        case answerCount = "answer_count"
    }
}

struct ZhihuHotListChild: Codable {
    let type: String?           // "answer"
    let thumbnail: String?
    let excerpt: String?
    let id: Int?
    let author: ZhihuAuthor?
}

struct ZhihuPaging: Codable {
    let isEnd: Bool?
    let next: String?
    let previous: String?

    enum CodingKeys: String, CodingKey {
        case isEnd = "is_end"
        case next, previous
    }
}

/// Response from Zhihu's article detail API
struct ZhihuArticleDetail: Codable {
    let id: Int?
    let title: String?
    let content: String?
    let excerpt: String?
    let author: ZhihuAuthor?
    let voteupCount: Int?
    let commentCount: Int?
    let created: Int?
    let updated: Int?

    enum CodingKeys: String, CodingKey {
        case id, title, content, excerpt, author, created, updated
        case voteupCount = "voteup_count"
        case commentCount = "comment_count"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let intId = try? container.decode(Int.self, forKey: .id) {
            self.id = intId
        } else if let strId = try? container.decode(String.self, forKey: .id) {
            self.id = Int(strId)
        } else {
            self.id = nil
        }
        self.title = try container.decodeIfPresent(String.self, forKey: .title)
        self.content = try container.decodeIfPresent(String.self, forKey: .content)
        self.excerpt = try container.decodeIfPresent(String.self, forKey: .excerpt)
        self.author = try container.decodeIfPresent(ZhihuAuthor.self, forKey: .author)
        self.voteupCount = try container.decodeIfPresent(Int.self, forKey: .voteupCount)
        self.commentCount = try container.decodeIfPresent(Int.self, forKey: .commentCount)
        self.created = try container.decodeIfPresent(Int.self, forKey: .created)
        self.updated = try container.decodeIfPresent(Int.self, forKey: .updated)
    }
}

/// Response from Zhihu's answer detail API
struct ZhihuAnswerDetail: Codable {
    let id: Int?
    let content: String?
    let excerpt: String?
    let author: ZhihuAuthor?
    let question: ZhihuQuestion?
    let voteupCount: Int?
    let commentCount: Int?
    let thanksCount: Int?
    let createdTime: Int?
    let updatedTime: Int?

    enum CodingKeys: String, CodingKey {
        case id, content, excerpt, author, question
        case voteupCount = "voteup_count"
        case commentCount = "comment_count"
        case thanksCount = "thanks_count"
        case createdTime = "created_time"
        case updatedTime = "updated_time"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let intId = try? container.decode(Int.self, forKey: .id) {
            self.id = intId
        } else if let strId = try? container.decode(String.self, forKey: .id) {
            self.id = Int(strId)
        } else {
            self.id = nil
        }
        self.content = try container.decodeIfPresent(String.self, forKey: .content)
        self.excerpt = try container.decodeIfPresent(String.self, forKey: .excerpt)
        self.author = try container.decodeIfPresent(ZhihuAuthor.self, forKey: .author)
        self.question = try container.decodeIfPresent(ZhihuQuestion.self, forKey: .question)
        self.voteupCount = try container.decodeIfPresent(Int.self, forKey: .voteupCount)
        self.commentCount = try container.decodeIfPresent(Int.self, forKey: .commentCount)
        self.thanksCount = try container.decodeIfPresent(Int.self, forKey: .thanksCount)
        self.createdTime = try container.decodeIfPresent(Int.self, forKey: .createdTime)
        self.updatedTime = try container.decodeIfPresent(Int.self, forKey: .updatedTime)
    }
}

/// Response from Zhihu's comment API
struct ZhihuCommentResponse: Codable {
    let data: [ZhihuComment]?
    let paging: ZhihuPaging?
}

struct ZhihuComment: Codable {
    let id: String?
    let type: String?
    let content: String?
    let createdTime: Int?
    let likeCount: Int?
    let dislikeCount: Int?
    let author: ZhihuCommentAuthor?
    let replyToAuthor: ZhihuCommentAuthor?
    let childCommentCount: Int?
    let childComments: [ZhihuComment]?

    enum CodingKeys: String, CodingKey {
        case id, type, content, author
        case createdTime = "created_time"
        case likeCount = "like_count"
        case dislikeCount = "dislike_count"
        case replyToAuthor = "reply_to_author"
        case childCommentCount = "child_comment_count"
        case childComments = "child_comments"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // id can be Int or String
        if let strId = try? container.decode(String.self, forKey: .id) {
            self.id = strId
        } else if let intId = try? container.decode(Int.self, forKey: .id) {
            self.id = String(intId)
        } else {
            self.id = nil
        }
        self.type = try container.decodeIfPresent(String.self, forKey: .type)
        self.content = try container.decodeIfPresent(String.self, forKey: .content)
        self.createdTime = try container.decodeIfPresent(Int.self, forKey: .createdTime)
        self.likeCount = try container.decodeIfPresent(Int.self, forKey: .likeCount)
        self.dislikeCount = try container.decodeIfPresent(Int.self, forKey: .dislikeCount)
        self.author = try container.decodeIfPresent(ZhihuCommentAuthor.self, forKey: .author)
        self.replyToAuthor = try container.decodeIfPresent(ZhihuCommentAuthor.self, forKey: .replyToAuthor)
        self.childCommentCount = try container.decodeIfPresent(Int.self, forKey: .childCommentCount)
        self.childComments = try container.decodeIfPresent([ZhihuComment].self, forKey: .childComments)
    }
}

struct ZhihuCommentAuthor: Codable {
    let id: String?
    let name: String?
    let avatarUrl: String?
    let avatarUrlTemplate: String?
    let headline: String?

    enum CodingKeys: String, CodingKey {
        case id, name, headline
        case avatarUrl = "avatar_url"
        case avatarUrlTemplate = "avatar_url_template"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let strId = try? container.decode(String.self, forKey: .id) {
            self.id = strId
        } else if let intId = try? container.decode(Int.self, forKey: .id) {
            self.id = String(intId)
        } else {
            self.id = nil
        }
        self.name = try container.decodeIfPresent(String.self, forKey: .name)
        self.avatarUrl = try container.decodeIfPresent(String.self, forKey: .avatarUrl)
        self.avatarUrlTemplate = try container.decodeIfPresent(String.self, forKey: .avatarUrlTemplate)
        self.headline = try container.decodeIfPresent(String.self, forKey: .headline)
    }
}

/// Response from /api/v4/me
struct ZhihuMeResponse: Codable {
    let id: String?
    let name: String?
    let headline: String?
    let avatarUrl: String?

    enum CodingKeys: String, CodingKey {
        case id, name, headline
        case avatarUrl = "avatar_url"
    }
}


// MARK: - Zhihu Service

class ZhihuService: ForumService {
    var name: String { "知乎" }
    var id: String { "zhihu" }
    var logo: String { "questionmark.bubble.fill" }
    var requiresLogin: Bool { true }

    // Zhihu API endpoints
    private let recommendURL = "https://api.zhihu.com/topstory/recommend"
    private let webRecommendURL = "https://www.zhihu.com/api/v3/feed/topstory/recommend"
    private let articleDetailBase = "https://www.zhihu.com/api/v4/articles"
    private let answerDetailBase = "https://www.zhihu.com/api/v4/answers"
    private let questionDetailBase = "https://www.zhihu.com/api/v4/questions"
    private let commentBase = "https://www.zhihu.com/api/v4"

    // Headers mimicking zhihu-plus-plus Android client
    private let zhihuHeaders: [String: String] = [
        "x-api-version": "3.1.8",
        "x-app-version": "10.61.0",
        "x-requested-with": "fetch",
        "Referer": "https://www.zhihu.com/",
    ]

    private let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    // Pagination state
    private var nextPageURL: String?
    private var currentFeedItems: [ZhihuFeedItem] = []

    // Cache question data from hot list/feed (title, excerpt) for detail fallback
    private var questionDataCache: [String: (title: String, excerpt: String)] = [:]

    // Downvoted item IDs (persisted via DatabaseManager)
    private var downvotedIds: Set<String> = []

    // Session restore flag
    private var sessionRestored = false

    init() {
        loadDownvotedIds()
    }

    // MARK: - Session Restore

    func restoreSession() async -> Bool {
        guard !sessionRestored else { return true }
        sessionRestored = true

        let cookies = getAuthCookies()
        if !cookies.isEmpty {
            // Sync to system cookie storage
            for cookie in cookies {
                HTTPCookieStorage.shared.setCookie(cookie)
            }
            print("[Zhihu] Restored \(cookies.count) cookies to session")
            return true
        }

        // No cookies — user needs to log in
        return false
    }

    // MARK: - Cookie Management

    private func getAuthCookies() -> [HTTPCookie] {
        let saved = DatabaseManager.shared.getCookies(siteId: id) ?? []
        return saved.filter { $0.domain.contains("zhihu.com") }
    }

    private func buildRequest(url: URL, method: String = "GET") -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")

        // Add Zhihu-specific headers
        for (key, value) in zhihuHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }

        // Manually inject cookies (same pattern as 4D4Y)
        let cookies = getAuthCookies()
        if !cookies.isEmpty {
            let cookieHeader = cookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
            request.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
            request.httpShouldHandleCookies = false
            print("[Zhihu] Sending \(cookies.count) cookies: \(cookies.map { $0.name }.joined(separator: ", "))")
        } else {
            print("[Zhihu] WARNING: No auth cookies found. User may not be logged in.")
        }

        return request
    }

    // MARK: - Downvote Management

    private func loadDownvotedIds() {
        if let saved = DatabaseManager.shared.getSetting(key: "zhihu_downvoted_ids") {
            downvotedIds = Set(saved.components(separatedBy: ",").filter { !$0.isEmpty })
        }
        print("[Zhihu] Loaded \(downvotedIds.count) downvoted IDs")
    }

    private func saveDownvotedIds() {
        let value = downvotedIds.joined(separator: ",")
        DatabaseManager.shared.saveSetting(key: "zhihu_downvoted_ids", value: value)
    }

    /// Downvote a feed item — marks it locally and sends uninterest request to Zhihu
    func downvoteItem(feedItem: ZhihuFeedItem) async {
        guard let targetId = feedItem.target?.id else { return }
        let idStr = String(targetId)

        // Add to local blacklist
        downvotedIds.insert(idStr)
        saveDownvotedIds()
        print("[Zhihu] Downvoted item: \(idStr)")

        // Send uninterest request to Zhihu API
        // This tells Zhihu not to recommend similar content
        await sendUninterestToServer(feedItem: feedItem)
    }

    /// Undo a downvote
    func undoDownvote(targetId: String) {
        downvotedIds.remove(targetId)
        saveDownvotedIds()
        print("[Zhihu] Undid downvote for: \(targetId)")
    }

    /// Check if an item is downvoted
    func isDownvoted(targetId: Int?) -> Bool {
        guard let id = targetId else { return false }
        return downvotedIds.contains(String(id))
    }

    /// Send uninterest signal to Zhihu's server
    private func sendUninterestToServer(feedItem: ZhihuFeedItem) async {
        guard let targetType = feedItem.target?.type,
              let targetId = feedItem.target?.id else { return }

        let urlStr = "https://www.zhihu.com/api/v3/feed/topstory/uninterest"
        guard let url = URL(string: urlStr) else { return }

        var request = buildRequest(url: url, method: "POST")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Build the uninterest payload
        let payload: [String: Any] = [
            "target_type": targetType,
            "target_id": String(targetId),
            "reason": "not_interested"
        ]

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
            let (_, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                print("[Zhihu] Uninterest response: \(httpResponse.statusCode)")
            }
        } catch {
            print("[Zhihu] Failed to send uninterest: \(error)")
        }
    }

    // MARK: - ForumService Protocol Implementation

    func getWebURL(for thread: Thread) -> String {
        // Thread ID format: "answer_123" or "article_456"
        let parts = thread.id.components(separatedBy: "_")
        guard parts.count == 2 else { return "https://www.zhihu.com" }

        let type = parts[0]
        let id = parts[1]

        switch type {
        case "answer":
            return "https://www.zhihu.com/question/0/answer/\(id)"
        case "article":
            return "https://zhuanlan.zhihu.com/p/\(id)"
        case "question":
            return "https://www.zhihu.com/question/\(id)"
        default:
            return "https://www.zhihu.com"
        }
    }

    func fetchCategories() async throws -> [Community] {
        // Zhihu doesn't have traditional "categories" like forums
        // Instead, we present the recommendation feed as a single "community"
        // and optionally add Hot Topics as another
        return [
            Community(
                id: "recommend",
                name: "推荐",
                description: "个性化推荐内容",
                category: "zhihu",
                activeToday: 0,
                onlineNow: 0
            ),
            Community(
                id: "hot",
                name: "热榜",
                description: "知乎热门话题",
                category: "zhihu",
                activeToday: 0,
                onlineNow: 0
            )
        ]
    }

    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [Thread] {
        switch categoryId {
        case "recommend":
            return try await fetchRecommendFeed(page: page)
        case "hot":
            return try await fetchHotList(page: page)
        default:
            return []
        }
    }

    func fetchThreadDetail(threadId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
        let parts = threadId.components(separatedBy: "_")
        guard parts.count == 2 else {
            throw NSError(domain: "ZhihuService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid thread ID format"])
        }

        let type = parts[0]
        let id = parts[1]

        switch type {
        case "answer":
            return try await fetchAnswerDetail(answerId: id, page: page)
        case "article":
            return try await fetchArticleDetail(articleId: id, page: page)
        case "question":
            return try await fetchQuestionDetail(questionId: id, page: page)
        default:
            throw NSError(domain: "ZhihuService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Unknown content type: \(type)"])
        }
    }

    func postComment(topicId: String, categoryId: String, content: String) async throws {
        // TODO: Implement comment posting
        throw NSError(domain: "ZhihuService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Comment posting not yet supported"])
    }

    func createThread(categoryId: String, title: String, content: String) async throws {
        // Not applicable for Zhihu in this context
        throw NSError(domain: "ZhihuService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Thread creation not supported"])
    }

    // MARK: - Recommendation Feed

    private func fetchRecommendFeed(page: Int) async throws -> [Thread] {
        let urlStr: String
        if page == 1 {
            // First page: use base recommend URL
            urlStr = "\(webRecommendURL)?desktop=true&limit=10"
            nextPageURL = nil
        } else if let nextURL = nextPageURL {
            // Subsequent pages: use pagination URL
            urlStr = nextURL
        } else {
            // Fallback
            urlStr = "\(webRecommendURL)?desktop=true&limit=10&after_id=\(page)"
        }

        guard let url = URL(string: urlStr) else {
            throw NSError(domain: "ZhihuService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }

        print("[Zhihu] Fetching recommend feed page \(page): \(urlStr)")
        let request = buildRequest(url: url)

        let (data, response) = try await URLSession.shared.data(for: request)

        if let httpResponse = response as? HTTPURLResponse {
            print("[Zhihu] Recommend response status: \(httpResponse.statusCode)")
            if httpResponse.statusCode == 401 {
                print("[Zhihu] Unauthorized - user needs to login")
                throw NSError(domain: "ZhihuService", code: 401, userInfo: [NSLocalizedDescriptionKey: "请先登录知乎 (Please login to Zhihu first)"])
            }
        }

        let decoder = JSONDecoder()

        // Debug: log raw response snippet
        if let jsonStr = String(data: data, encoding: .utf8) {
            let preview = String(jsonStr.prefix(500))
            print("[Zhihu] Recommend raw response (first 500 chars): \(preview)")
        }

        let feedResponse: ZhihuFeedResponse
        do {
            feedResponse = try decoder.decode(ZhihuFeedResponse.self, from: data)
        } catch {
            print("[Zhihu] Failed to decode recommend response: \(error)")
            return []
        }

        // Save next page URL for pagination
        nextPageURL = feedResponse.paging?.next

        // Store raw feed items for downvote capability
        currentFeedItems = feedResponse.data ?? []

        // Convert to Thread objects, applying filters
        let threads = convertFeedToThreads(feedItems: feedResponse.data ?? [])
        print("[Zhihu] Fetched \(feedResponse.data?.count ?? 0) items, \(threads.count) after filtering")

        return threads
    }

    // MARK: - Hot List

    private func fetchHotList(page: Int) async throws -> [Thread] {
        let urlStr = "https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=10&desktop=true"
        guard let url = URL(string: urlStr) else { return [] }

        let request = buildRequest(url: url)
        let (data, response) = try await URLSession.shared.data(for: request)

        if let httpResponse = response as? HTTPURLResponse {
            print("[Zhihu] Hot list response status: \(httpResponse.statusCode)")
        }

        // Debug: log raw response snippet
        if let jsonStr = String(data: data, encoding: .utf8) {
            let preview = String(jsonStr.prefix(500))
            print("[Zhihu] Hot list raw response (first 500 chars): \(preview)")
        }

        let decoder = JSONDecoder()

        // Hot list has a different JSON structure from recommend feed
        let hotListResponse: ZhihuHotListResponse
        do {
            hotListResponse = try decoder.decode(ZhihuHotListResponse.self, from: data)
        } catch {
            print("[Zhihu] Failed to decode hot list response: \(error)")
            return []
        }

        let items = hotListResponse.data ?? []
        print("[Zhihu] Hot list decoded \(items.count) items")

        return items.enumerated().compactMap { (index, item) -> Thread? in
            guard let target = item.target else {
                print("[Zhihu] Hot item #\(index): no target, skipped")
                return nil
            }

            // Extract question ID from cardId (format: "Q_12345678")
            let questionId: String
            if let cardId = item.cardId, cardId.hasPrefix("Q_") {
                questionId = String(cardId.dropFirst(2))
            } else {
                questionId = item.id ?? UUID().uuidString
            }

            let threadId = "question_\(questionId)"
            let title = target.titleArea?.text ?? "Untitled"
            print("[Zhihu] Hot item #\(index): id=\(threadId), title=\(String(title.prefix(30)))")

            // Use excerpt_area.text or first child's excerpt
            let excerpt = target.excerptArea?.text ?? item.children?.first?.excerpt ?? ""
            let childAuthor = item.children?.first?.author

            // Cache question data for detail view fallback
            questionDataCache[questionId] = (title: title, excerpt: excerpt)

            // Heat text like "123万热度"
            let heatText = item.detailText ?? ""
            return Thread(
                id: threadId,
                title: title,
                content: excerpt,
                author: User(
                    id: childAuthor?.id ?? "",
                    username: childAuthor?.name ?? "热榜",
                    avatar: avatarURL(from: childAuthor),
                    role: childAuthor?.headline
                ),
                community: Community(id: "hot", name: "知乎热榜", description: "", category: "zhihu", activeToday: 0, onlineNow: 0),
                timeAgo: heatText,
                likeCount: target.metricsArea?.followerCount ?? 0,
                commentCount: target.metricsArea?.answerCount ?? 0,
                tags: ["🔥 热榜"]
            )
        }
    }

    // MARK: - Content Detail

    private func fetchAnswerDetail(answerId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
        let urlStr = "\(answerDetailBase)/\(answerId)?include=content,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count"
        guard let url = URL(string: urlStr) else {
            throw NSError(domain: "ZhihuService", code: -1)
        }

        print("[Zhihu] Fetching answer detail: \(answerId)")
        let request = buildRequest(url: url)
        let (data, _) = try await URLSession.shared.data(for: request)

        let decoder = JSONDecoder()
        let answer = try decoder.decode(ZhihuAnswerDetail.self, from: data)

        // Create Thread from answer detail
        let thread = Thread(
            id: "answer_\(answerId)",
            title: answer.question?.title ?? "回答",
            content: cleanHTML(answer.content ?? answer.excerpt ?? ""),
            author: User(
                id: answer.author?.id ?? "",
                username: answer.author?.name ?? "匿名用户",
                avatar: avatarURL(from: answer.author),
                role: answer.author?.headline
            ),
            community: Community(id: "recommend", name: "知乎", description: "", category: "zhihu", activeToday: 0, onlineNow: 0),
            timeAgo: formatTimestamp(answer.createdTime),
            likeCount: answer.voteupCount ?? 0,
            commentCount: answer.commentCount ?? 0,
            tags: [answer.question?.title].compactMap { $0 }
        )

        // Fetch comments
        let comments = try await fetchComments(type: "answers", id: answerId, page: page)

        return (thread, comments, nil)
    }

    private func fetchArticleDetail(articleId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
        let urlStr = "\(articleDetailBase)/\(articleId)?include=content,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count"
        guard let url = URL(string: urlStr) else {
            throw NSError(domain: "ZhihuService", code: -1)
        }

        print("[Zhihu] Fetching article detail: \(articleId)")
        let request = buildRequest(url: url)
        let (data, _) = try await URLSession.shared.data(for: request)

        let decoder = JSONDecoder()
        let article = try decoder.decode(ZhihuArticleDetail.self, from: data)

        let thread = Thread(
            id: "article_\(articleId)",
            title: article.title ?? "文章",
            content: cleanHTML(article.content ?? article.excerpt ?? ""),
            author: User(
                id: article.author?.id ?? "",
                username: article.author?.name ?? "匿名用户",
                avatar: avatarURL(from: article.author),
                role: article.author?.headline
            ),
            community: Community(id: "recommend", name: "知乎", description: "", category: "zhihu", activeToday: 0, onlineNow: 0),
            timeAgo: formatTimestamp(article.created),
            likeCount: article.voteupCount ?? 0,
            commentCount: article.commentCount ?? 0
        )

        let comments = try await fetchComments(type: "articles", id: articleId, page: page)

        return (thread, comments, nil)
    }

    private func fetchQuestionDetail(questionId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
        print("[Zhihu] Fetching question detail: \(questionId)")

        var title = "问题"
        var detail = ""
        var answerCount = 0
        var commentCount = 0
        var authorName = ""
        var authorAvatar = ""
        var authorId = ""
        var authorHeadline: String? = nil
        var apiSuccess = false

        // Try v4 API first
        let urlStr = "\(questionDetailBase)/\(questionId)?include=detail,excerpt,answer_count,visit_count,comment_count,follower_count,topics"
        if let url = URL(string: urlStr) {
            let request = buildRequest(url: url)
            if let (data, response) = try? await URLSession.shared.data(for: request) {
                let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
                print("[Zhihu] Question detail v4 response status: \(statusCode)")

                if statusCode == 200,
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   json["error"] == nil {
                    title = json["title"] as? String ?? title
                    detail = json["detail"] as? String ?? json["excerpt"] as? String ?? ""
                    answerCount = json["answer_count"] as? Int ?? 0
                    commentCount = json["comment_count"] as? Int ?? 0
                    let authorInfo = json["author"] as? [String: Any]
                    authorName = authorInfo?["name"] as? String ?? ""
                    authorAvatar = avatarURL(from: authorInfo)
                    authorId = authorInfo?["id"] as? String ?? ""
                    authorHeadline = authorInfo?["headline"] as? String
                    apiSuccess = true
                    print("[Zhihu] Question v4 API success: title='\(title)'")
                } else {
                    print("[Zhihu] Question v4 API failed (status \(statusCode)), trying cache fallback")
                }
            }
        }

        // Fallback: use cached data from hot list
        if !apiSuccess, let cached = questionDataCache[questionId] {
            title = cached.title
            detail = cached.excerpt
            print("[Zhihu] Using cached question data: title='\(title)'")
        }

        let thread = Thread(
            id: "question_\(questionId)",
            title: title,
            content: cleanHTML(detail),
            author: User(
                id: authorId,
                username: authorName,
                avatar: authorAvatar,
                role: authorHeadline
            ),
            community: Community(id: "hot", name: "知乎热榜", description: "", category: "zhihu", activeToday: 0, onlineNow: 0),
            timeAgo: "",
            likeCount: 0,
            commentCount: commentCount,
            tags: answerCount > 0 ? ["回答数: \(answerCount)"] : nil
        )

        // Fetch top answers as "comments"
        let answers = try await fetchQuestionAnswers(questionId: questionId, page: page)

        return (thread, answers, nil)
    }

    // MARK: - Comments

    private func fetchComments(type: String, id: String, page: Int) async throws -> [Comment] {
        let offset = (page - 1) * 20
        let urlStr = "\(commentBase)/\(type)/\(id)/root_comments?limit=20&offset=\(offset)&order=normal&status=open"
        guard let url = URL(string: urlStr) else { return [] }

        let request = buildRequest(url: url)
        let (data, _) = try await URLSession.shared.data(for: request)

        let decoder = JSONDecoder()
        let commentResponse = try decoder.decode(ZhihuCommentResponse.self, from: data)

        return (commentResponse.data ?? []).compactMap { zhComment -> Comment? in
            guard let content = zhComment.content, !content.isEmpty else { return nil }

            return Comment(
                id: zhComment.id ?? UUID().uuidString,
                author: User(
                    id: zhComment.author?.id ?? "",
                    username: zhComment.author?.name ?? "匿名",
                    avatar: avatarURL(from: zhComment.author),
                    role: nil
                ),
                content: cleanHTML(content),
                timeAgo: formatTimestamp(zhComment.createdTime),
                likeCount: zhComment.likeCount ?? 0,
                replies: zhComment.childComments?.compactMap { child in
                    guard let childContent = child.content else { return nil }
                    return Comment(
                        id: child.id ?? UUID().uuidString,
                        author: User(
                            id: child.author?.id ?? "",
                            username: child.author?.name ?? "匿名",
                            avatar: avatarURL(from: child.author),
                            role: nil
                        ),
                        content: cleanHTML(childContent),
                        timeAgo: formatTimestamp(child.createdTime),
                        likeCount: child.likeCount ?? 0,
                        replies: nil
                    )
                }
            )
        }
    }

    private func fetchQuestionAnswers(questionId: String, page: Int) async throws -> [Comment] {
        let offset = (page - 1) * 10
        let urlStr = "\(questionDetailBase)/\(questionId)/answers?include=content,voteup_count,comment_count&limit=10&offset=\(offset)&sort_by=default"
        guard let url = URL(string: urlStr) else { return [] }

        let request = buildRequest(url: url)
        let (data, _) = try await URLSession.shared.data(for: request)

        // Parse answers as comments for display
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let answers = json["data"] as? [[String: Any]] else { return [] }

        return answers.compactMap { answer -> Comment? in
            let content = answer["content"] as? String ?? answer["excerpt"] as? String ?? ""
            let authorInfo = answer["author"] as? [String: Any]
            let voteupCount = answer["voteup_count"] as? Int ?? 0
            let answerId = answer["id"]
            let idStr: String
            if let intId = answerId as? Int {
                idStr = String(intId)
            } else if let strId = answerId as? String {
                idStr = strId
            } else {
                idStr = UUID().uuidString
            }

            return Comment(
                id: idStr,
                author: User(
                    id: authorInfo?["id"] as? String ?? "",
                    username: authorInfo?["name"] as? String ?? "匿名用户",
                    avatar: avatarURL(from: authorInfo),
                    role: authorInfo?["headline"] as? String
                ),
                content: cleanHTML(content),
                timeAgo: "👍 \(voteupCount)",
                likeCount: voteupCount,
                replies: nil
            )
        }
    }

    // MARK: - Feed Conversion & Filtering

    private func convertFeedToThreads(feedItems: [ZhihuFeedItem]) -> [Thread] {
        return feedItems.compactMap { item -> Thread? in
            // Skip advertisements
            guard item.type != "feed_advert" else {
                print("[Zhihu] Filtered out ad")
                return nil
            }

            // Skip items without a target
            guard let target = item.target else { return nil }

            // Skip unsupported types (e.g., videos)
            guard let targetType = target.type,
                  ["answer", "article", "question", "pin"].contains(targetType) else {
                return nil
            }

            // Skip downvoted items
            if isDownvoted(targetId: target.id) {
                print("[Zhihu] Excluded downvoted item: \(target.id ?? 0)")
                return nil
            }

            // Apply quality filter (from zhihu-plus-plus logic)
            if let reason = target.filterReason() {
                print("[Zhihu] Filtered: \(reason)")
                return nil
            }

            // Build thread ID in format "type_id"
            let threadId = "\(targetType)_\(target.id ?? 0)"

            // Build content preview
            let contentPreview = target.excerpt ?? ""

            // Build details text
            var details = target.detailsText
            if let actionText = item.actionText {
                details += " · \(actionText)"
            }

            return Thread(
                id: threadId,
                title: target.effectiveTitle,
                content: contentPreview,
                author: User(
                    id: target.author?.id ?? "",
                    username: target.author?.name ?? "匿名用户",
                    avatar: avatarURL(from: target.author),
                    role: target.author?.headline
                ),
                community: Community(id: "recommend", name: "知乎", description: "", category: "zhihu", activeToday: 0, onlineNow: 0),
                timeAgo: formatTimestamp(target.createdTime),
                likeCount: target.voteupCount ?? 0,
                commentCount: target.commentCount ?? 0,
                tags: [target.typeDescription]
            )
        }
    }

    /// Get the raw ZhihuFeedItem for a thread (used by downvote UI)
    func getFeedItem(for threadId: String) -> ZhihuFeedItem? {
        let parts = threadId.components(separatedBy: "_")
        guard parts.count == 2, let targetId = Int(parts[1]) else { return nil }
        return currentFeedItems.first { $0.target?.id == targetId }
    }

    // MARK: - Login Verification

    /// Check if user is logged in by calling /api/v4/me
    func verifyLogin() async -> ZhihuMeResponse? {
        guard let url = URL(string: "https://www.zhihu.com/api/v4/me") else { return nil }

        let request = buildRequest(url: url)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                let decoder = JSONDecoder()
                let me = try decoder.decode(ZhihuMeResponse.self, from: data)
                print("[Zhihu] Logged in as: \(me.name ?? "unknown")")
                return me
            }
        } catch {
            print("[Zhihu] Login verification failed: \(error)")
        }
        return nil
    }

    // MARK: - Helpers

    private func avatarURL(from author: ZhihuAuthor?) -> String {
        normalizedAvatarURL(author?.avatarUrl, template: author?.avatarUrlTemplate)
    }

    private func avatarURL(from author: ZhihuCommentAuthor?) -> String {
        normalizedAvatarURL(author?.avatarUrl, template: author?.avatarUrlTemplate)
    }

    private func avatarURL(from authorInfo: [String: Any]?) -> String {
        normalizedAvatarURL(
            authorInfo?["avatar_url"] as? String,
            template: authorInfo?["avatar_url_template"] as? String
        )
    }

    private func normalizedAvatarURL(_ url: String?, template: String? = nil) -> String {
        let raw = (url?.isEmpty == false ? url : template) ?? ""
        var avatar = raw
            .replacingOccurrences(of: "&amp;", with: "&")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if avatar.contains("{size}") {
            avatar = avatar.replacingOccurrences(of: "{size}", with: "80")
        }

        if avatar.hasPrefix("//") {
            return "https:\(avatar)"
        }

        return avatar
    }

    private func formatTimestamp(_ timestamp: Int?) -> String {
        guard let ts = timestamp, ts > 0 else { return "" }
        let date = Date(timeIntervalSince1970: TimeInterval(ts))
        return calculateTimeAgo(from: date)
    }

    /// Strip HTML tags and clean content for display
    private func cleanHTML(_ html: String) -> String {
        // First, preserve links as [LINK:url|title] markers
        var cleaned = html

        // Zhihu commonly includes fallback <noscript><img ...></noscript> copies
        // beside the real image. Drop those fallbacks before converting images.
        cleaned = cleaned.replacingOccurrences(
            of: "<noscript[^>]*>.*?</noscript>",
            with: "",
            options: [.regularExpression, .caseInsensitive]
        )

        // Extract <a> tags with href
        if let linkRegex = try? NSRegularExpression(pattern: "<a[^>]*href=[\"']([^\"']*)[\"'][^>]*>(.*?)</a>", options: [.caseInsensitive, .dotMatchesLineSeparators]) {
            let range = NSRange(cleaned.startIndex..., in: cleaned)
            let matches = linkRegex.matches(in: cleaned, options: [], range: range)

            // Process from end to start to avoid index shifting
            for match in matches.reversed() {
                if let urlRange = Range(match.range(at: 1), in: cleaned),
                   let textRange = Range(match.range(at: 2), in: cleaned),
                   let fullRange = Range(match.range, in: cleaned) {
                    let linkURL = String(cleaned[urlRange])
                    var linkText = String(cleaned[textRange])
                    // Strip any nested tags from link text
                    linkText = linkText.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)

                    if linkText.isEmpty || linkText == linkURL {
                        cleaned.replaceSubrange(fullRange, with: "[LINK:\(linkURL)|\(linkURL)]")
                    } else {
                        cleaned.replaceSubrange(fullRange, with: "[LINK:\(linkURL)|\(linkText)]")
                    }
                }
            }
        }

        // Extract <img> tags and preserve as [IMAGE:url] markers
        if let imgRegex = try? NSRegularExpression(pattern: "<img[^>]*(?:src|data-original|data-actualsrc)=[\"']([^\"']+)[\"'][^>]*/?>" , options: [.caseInsensitive]) {
            let range = NSRange(cleaned.startIndex..., in: cleaned)
            let matches = imgRegex.matches(in: cleaned, options: [], range: range)
            var seenImageKeys = Set<String>()

            for match in matches.reversed() {
                if let urlRange = Range(match.range(at: 1), in: cleaned),
                   let fullRange = Range(match.range, in: cleaned) {
                    let imgURL = String(cleaned[urlRange])
                    let imageKey = normalizedImageKey(imgURL)
                    // Skip tiny images (tracking pixels, emojis)
                    if !imgURL.contains("equation") && !imgURL.contains("data:"), !seenImageKeys.contains(imageKey) {
                        seenImageKeys.insert(imageKey)
                        cleaned.replaceSubrange(fullRange, with: "\n[IMAGE:\(imgURL)]\n")
                    } else {
                        cleaned.replaceSubrange(fullRange, with: "")
                    }
                }
            }
        }

        // Convert <figure> and </figure> to nothing (images already extracted)
        cleaned = cleaned.replacingOccurrences(of: "</?figure[^>]*>", with: "", options: .regularExpression)

        // Convert <br> and <p> to newlines
        cleaned = cleaned.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "</p>", with: "\n", options: .caseInsensitive)
        cleaned = cleaned.replacingOccurrences(of: "<p[^>]*>", with: "", options: .regularExpression)

        // Strip remaining HTML tags
        cleaned = cleaned.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)

        // Decode HTML entities
        cleaned = cleaned.replacingOccurrences(of: "&amp;", with: "&")
        cleaned = cleaned.replacingOccurrences(of: "&lt;", with: "<")
        cleaned = cleaned.replacingOccurrences(of: "&gt;", with: ">")
        cleaned = cleaned.replacingOccurrences(of: "&quot;", with: "\"")
        cleaned = cleaned.replacingOccurrences(of: "&#39;", with: "'")
        cleaned = cleaned.replacingOccurrences(of: "&nbsp;", with: " ")

        // Collapse multiple newlines
        cleaned = cleaned.replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression)

        return cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func normalizedImageKey(_ rawURL: String) -> String {
        var url = rawURL
            .replacingOccurrences(of: "&amp;", with: "&")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if url.hasPrefix("//") {
            url = "https:\(url)"
        }

        guard var components = URLComponents(string: url) else {
            return url.lowercased()
        }

        components.scheme = "https"
        components.query = nil
        components.fragment = nil

        if let host = components.host?.lowercased(), host.hasSuffix("zhimg.com") {
            components.host = "zhimg.com"
        }

        var path = components.path
        path = path.replacingOccurrences(of: "/80/", with: "/")
        path = path.replacingOccurrences(of: "/50/", with: "/")
        path = path.replacingOccurrences(of: "/v2-", with: "/v2-")
        path = path.replacingOccurrences(
            of: "_(?:r|b|hd|720w|1080w|1440w|2160w|720w_1l|1080w_1l)\\.(jpg|jpeg|png|webp)$",
            with: ".$1",
            options: [.regularExpression, .caseInsensitive]
        )
        components.path = path

        return (components.string ?? url).lowercased()
    }
}
