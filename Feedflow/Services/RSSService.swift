import Foundation
import Combine

@MainActor
class RSSService: ForumService {
    var name: String { "RSS Feeds" }
    var id: String { "rss" }
    var logo: String { FeedflowIcon.feed }

    // In-memory cache for thread details since RSS doesn't have a detail API
    // Mapping: ThreadID -> Thread
    private var threadCache: [String: Thread] = [:]


    public struct FeedInfo: Codable, Identifiable {
        public let id: String
        public let name: String
        public let url: String
        public let description: String

        public init(id: String, name: String, url: String, description: String) {
            self.id = id
            self.name = name
            self.url = url
            self.description = description
        }
    }

    @Published private(set) var feeds: [FeedInfo] = []

    private let defaultFeeds = [
        FeedInfo(id: "hacker_podcast", name: "Hacker Podcast", url: "https://hacker-podcast.agi.li/rss.xml", description: "Hacker News Recap"),
        FeedInfo(id: "ruanyifeng", name: "Ruanyifeng Blog", url: "https://www.ruanyifeng.com/blog/atom.xml", description: "Tech and Humanities"),
        FeedInfo(id: "oreilly", name: "O'Reilly Radar", url: "https://www.oreilly.com/radar/feed/", description: "Tech Trends")
    ]

    init() {
        loadFeeds()
    }

    private func loadFeeds() {
        if let data = UserDefaults.standard.data(forKey: "custom_rss_feeds"),
           let decoded = try? JSONDecoder().decode([FeedInfo].self, from: data) {
            self.feeds = decoded
        } else {
            self.feeds = defaultFeeds
        }
    }

    private func saveFeeds() {
        if let encoded = try? JSONEncoder().encode(feeds) {
            UserDefaults.standard.set(encoded, forKey: "custom_rss_feeds")
        }
    }

    func addFeed(name: String, url: String) {
        let id = UUID().uuidString
        let newFeed = FeedInfo(id: id, name: name, url: url, description: "")
        feeds.append(newFeed)
        saveFeeds()
    }

    func addFeeds(_ newFeeds: [FeedInfo]) {
        self.feeds.append(contentsOf: newFeeds)
        saveFeeds()
    }

    func removeFeed(id: String) {
        feeds.removeAll { $0.id == id }
        saveFeeds()
    }

    func removeFeeds(ids: Set<String>) {
        feeds.removeAll { ids.contains($0.id) }
        saveFeeds()
    }

    func fetchCategories() async throws -> [Community] {
        return feeds.map { feed in
            Community(
                id: feed.id,
                name: feed.name,
                description: feed.description,
                category: "RSS",
                activeToday: 0,
                onlineNow: 0
            )
        }
    }

    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [Thread] {
        guard let feed = feeds.first(where: { $0.id == categoryId }) else {
            throw NSError(domain: "RSSService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Feed not found"])
        }

        guard let url = URL(string: feed.url) else {
            throw NSError(domain: "RSSService", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
        }

        let (data, _) = try await URLSession.shared.data(from: url)

        let parser = RSSParser(data: data)
        let threads = await parser.parse()

        // Update communities and cache
        let community = communities.first(where: { $0.id == categoryId }) ??
                       Community(id: categoryId, name: "RSS", description: "", category: "RSS", activeToday: 0, onlineNow: 0)

        let mappedThreads = threads.map { thread -> Thread in
             // Clean content here because RSSParser returns raw XML/HTML content
             let cleanBody = self.cleanContent(thread.content)

             let mapped = Thread(
                id: thread.id,
                title: thread.title,
                content: cleanBody, // Use cleaned content
                author: thread.author,
                community: community,
                timeAgo: thread.timeAgo,
                likeCount: 0,
                commentCount: 0, // RSS usually has no comments API
                isLiked: false,
                tags: nil
            )
            // Cache for detail view
            self.threadCache[mapped.id] = mapped
            return mapped
        }

        return mappedThreads
    }

    func fetchThreadDetail(threadId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
        // Retrieve from cache
        if let thread = threadCache[threadId] {
            return (thread, [], 1)
        }

        // Fallback: If deep linked or cache miss, we can't easily fetch just one item from RSS.
        // We'd have to re-fetch the feed. For now, return error or empty.
        // Or we could try to find which feed it belongs to, but we don't know the feed ID from just thread ID easily
        // unless we encode it.
        // Let's assume cache hit for now as typical usage flow.

        let empty = Thread(
            id: threadId,
            title: "Content Unavailable",
            content: "Please refresh the feed list.",
            author: User(id: "system", username: "System", avatar: "exclamationmark.triangle", role: nil),
            community: Community(id: "error", name: "Error", description: "", category: "", activeToday: 0, onlineNow: 0),
            timeAgo: "",
            likeCount: 0,
            commentCount: 0
        )
        return (empty, [], 1)
    }

    func postComment(topicId: String, categoryId: String, content: String) async throws {
        throw NSError(domain: "RSSService", code: 403, userInfo: [NSLocalizedDescriptionKey: "Commenting is not supported for RSS feeds."])
    }

    func createThread(categoryId: String, title: String, content: String) async throws {
        throw NSError(domain: "RSSService", code: 403, userInfo: [NSLocalizedDescriptionKey: "Posting is not supported for RSS feeds."])
    }

    func getWebURL(for thread: Thread) -> String {
        return thread.id // ID is the link in our RSS parser
    }

    func fetchDailyUpdates() async -> [Thread] {
        return await withTaskGroup(of: [Thread].self) { group in
            for feed in feeds {
                group.addTask {
                    guard let url = URL(string: feed.url) else { return [] }
                    do {
                        let (data, _) = try await URLSession.shared.data(from: url)
                        let parser = RSSParser(data: data)
                        let threads = await parser.parse()

                        // Filter items from last 24 hours
                        // Logic: timeAgo uses "m" (minutes) or "h" (hours) for < 24h.
                        // "d" (days) implies >= 24h.
                        let recentThreads = threads.filter { thread in
                            let t = thread.timeAgo
                            return t == "just now" || t.hasSuffix("m") || t.hasSuffix("h")
                        }

                        let community = Community(id: feed.id, name: feed.name, description: "", category: "RSS", activeToday: 0, onlineNow: 0)

                        return recentThreads.map { thread in
                            Thread(
                                id: thread.id,
                                title: thread.title,
                                content: self.cleanContent(thread.content),
                                author: thread.author,
                                community: community,
                                timeAgo: thread.timeAgo,
                                likeCount: thread.likeCount,
                                commentCount: thread.commentCount,
                                isLiked: thread.isLiked,
                                tags: thread.tags
                            )
                        }
                    } catch {
                        AppLogger.debug("Error fetching daily updates for \(feed.name): \(error)")
                        return []
                    }
                }
            }

            var allThreads: [Thread] = []
            for await threads in group {
                allThreads.append(contentsOf: threads)
            }

            // Sort by time? "formatted timeAgo" is hard to sort.
            // But usually feeds are sorted.
            // We can just return them.
            return allThreads
        }
    }

    private nonisolated func cleanContent(_ html: String) -> String {
        var processed = html

        // 0. Remove scripts and styles
        do {
            let scriptRegex = try NSRegularExpression(pattern: "<script[^>]*>[\\s\\S]*?</script>", options: .caseInsensitive)
            processed = scriptRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "")

            let styleRegex = try NSRegularExpression(pattern: "<style[^>]*>[\\s\\S]*?</style>", options: .caseInsensitive)
            processed = styleRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "")
        } catch {
            AppLogger.debug("Regex error (scripts): \(error)")
        }

        // 1. Handle Images
        // Replace <img src="..."> with [IMAGE:url] marker
        do {
            let imgRegex = try NSRegularExpression(pattern: "<img[^>]+src=\"([^\">]+)\"[^>]*>", options: .caseInsensitive)
            let matches = imgRegex.matches(in: processed, range: NSRange(processed.startIndex..., in: processed))

            for match in matches.reversed() {
                if let srcRange = Range(match.range(at: 1), in: processed),
                   let fullMatchRange = Range(match.range, in: processed) {
                    let src = String(processed[srcRange])
                    processed.replaceSubrange(fullMatchRange, with: "\n[IMAGE:\(src)]\n")
                }
            }
        } catch {
            AppLogger.debug("Regex error (images): \(error)")
        }

        // 2. Handle line breaks and paragraphs
        processed = processed.replacingOccurrences(of: "<br />", with: "\n")
        processed = processed.replacingOccurrences(of: "<br>", with: "\n")
        processed = processed.replacingOccurrences(of: "</p>", with: "\n\n")
        processed = processed.replacingOccurrences(of: "<p>", with: "")
        processed = processed.replacingOccurrences(of: "</div>", with: "\n")

        // 2.5. Extract links (<a href="...">) before stripping tags
        // Preserve as [LINK:url|title] so LinkedTextView can render titled links
        do {
            let linkRegex = try NSRegularExpression(
                pattern: "<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                options: [.caseInsensitive, .dotMatchesLineSeparators]
            )
            let matches = linkRegex.matches(in: processed, range: NSRange(processed.startIndex..., in: processed))

            for match in matches.reversed() {
                if let hrefRange = Range(match.range(at: 1), in: processed),
                   let textRange = Range(match.range(at: 2), in: processed),
                   let fullRange = Range(match.range, in: processed) {
                    let href = String(processed[hrefRange])
                    let linkText = String(processed[textRange])
                        .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                        .trimmingCharacters(in: .whitespacesAndNewlines)

                    // Skip anchors and javascript links
                    if href.hasPrefix("#") || href.hasPrefix("javascript:") { continue }

                    let title = linkText.isEmpty ? href : linkText
                    processed.replaceSubrange(fullRange, with: "[LINK:\(href)|\(title)]")
                }
            }
        } catch {
            AppLogger.debug("Regex error (links): \(error)")
        }

        // 3. Strip remaining HTML tags
        processed = processed.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression, range: nil)

        // 4. Decode HTML Entities
        processed = processed
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&apos;", with: "'")

        // Generic Numeric Entity Decoder
        if let regex = try? NSRegularExpression(pattern: "&#(\\d+);", options: []) {
            let matches = regex.matches(in: processed, range: NSRange(processed.startIndex..., in: processed))
            for match in matches.reversed() {
                if let r = Range(match.range(at: 1), in: processed),
                   let val = Int(String(processed[r])),
                   let scalar = UnicodeScalar(val),
                   let fullR = Range(match.range, in: processed) {
                    processed.replaceSubrange(fullR, with: String(scalar))
                }
            }
        }

        // 5. Cleanup whitespace
         if let newlineRegex = try? NSRegularExpression(pattern: "(\\s*\\n\\s*){3,}", options: []) {
            processed = newlineRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "\n\n")
        }

        return processed.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
