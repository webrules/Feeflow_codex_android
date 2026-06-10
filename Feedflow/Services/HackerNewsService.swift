import Foundation

class HackerNewsService: ForumService {
    var name: String { "Hacker News" }
    var id: String { "hackernews" }
    var logo: String { "flame.fill" }

    func getWebURL(for thread: Thread) -> String {
        return "https://news.ycombinator.com/item?id=\(thread.id)"
    }

    func postComment(topicId: String, categoryId: String, content: String) async throws {
        throw NSError(
            domain: "HackerNewsService",
            code: 403,
            userInfo: [NSLocalizedDescriptionKey: "Hacker News' official Firebase API is read-only; native posting is not available."]
        )
    }

    func createThread(categoryId: String, title: String, content: String) async throws {
        throw NSError(
            domain: "HackerNewsService",
            code: 403,
            userInfo: [NSLocalizedDescriptionKey: "Hacker News' official Firebase API is read-only; native posting is not available."]
        )
    }

    private let baseURL = "https://hacker-news.firebaseio.com/v0"

    func fetchCategories() async throws -> [Community] {
        return [
            Community(id: "topstories", name: "Top", description: "Top stories", category: "General", activeToday: 0, onlineNow: 0),
            Community(id: "newstories", name: "New", description: "New stories", category: "General", activeToday: 0, onlineNow: 0),
            Community(id: "beststories", name: "Best", description: "Best stories", category: "General", activeToday: 0, onlineNow: 0),
            Community(id: "showstories", name: "Show", description: "Show HN", category: "General", activeToday: 0, onlineNow: 0),
            Community(id: "askstories", name: "Ask", description: "Ask HN", category: "General", activeToday: 0, onlineNow: 0),
            Community(id: "jobstories", name: "Jobs", description: "Jobs", category: "General", activeToday: 0, onlineNow: 0)
        ]
    }

    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [Thread] {
        if page > 1 { return [] }
        guard let url = URL(string: "\(baseURL)/\(categoryId).json") else {
            throw URLError(.badURL)
        }

        let (data, _) = try await URLSession.shared.data(from: url)
        let ids = try JSONDecoder().decode([Int].self, from: data)
        let topIds = Array(ids.prefix(20))

        return await withTaskGroup(of: Thread?.self) { group in
            for id in topIds {
                group.addTask {
                    return await self.fetchItemAsThread(id: id, categoryId: categoryId, communities: communities)
                }
            }

            var threads: [Thread] = []
            for await thread in group {
                if let t = thread {
                    threads.append(t)
                }
            }
            return threads.sorted { $0.timeAgo < $1.timeAgo } // Approximation
        }
    }

    private func fetchItemAsThread(id: Int, categoryId: String, communities: [Community]) async -> Thread? {
        guard let url = URL(string: "\(baseURL)/item/\(id).json") else { return nil }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let item = try JSONDecoder().decode(HNItem.self, from: data)

            let author = User(id: item.by ?? "unknown", username: item.by ?? "unknown", avatar: "person.circle.fill", role: nil)
            let community = communities.first(where: { $0.id == categoryId }) ?? Community(id: categoryId, name: categoryId, description: "", category: "", activeToday: 0, onlineNow: 0)

            return Thread(
                id: String(item.id),
                title: item.title ?? "No Title",
                content: item.text ?? (item.url ?? ""),
                author: author,
                community: community,
                timeAgo: calculateTimeAgo(from: Date(timeIntervalSince1970: TimeInterval(item.time))),
                likeCount: item.score ?? 0,
                commentCount: item.descendants ?? 0,
                isLiked: false
            )
        } catch {
            return nil
        }
    }

    func fetchThreadDetail(threadId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
        if page > 1 {
             let dummy = Thread(id: threadId, title: "", content: "", author: User(id: "", username: "", avatar: "", role: nil), community: Community(id: "", name: "", description: "", category: "", activeToday: 0, onlineNow: 0), timeAgo: "", likeCount: 0, commentCount: 0, isLiked: false, tags: nil)
             return (dummy, [], nil)
        }
        let (thread, comments) = try await fetchThreadDetail(threadId: threadId)
        return (thread, comments, nil)
    }

    func fetchThreadDetail(threadId: String) async throws -> (Thread, [Comment]) {
        guard let url = URL(string: "\(baseURL)/item/\(threadId).json") else {
             throw URLError(.badURL)
        }

        let (data, _) = try await URLSession.shared.data(from: url)
        let item = try JSONDecoder().decode(HNItem.self, from: data)

        let author = User(id: item.by ?? "unknown", username: item.by ?? "unknown", avatar: "person.circle.fill", role: nil)

        var content = item.text ?? ""
        if let link = item.url {
            content += "\n\nLink: \(link)"
        }

        content = cleanHNContent(content)

        let thread = Thread(
            id: String(item.id),
            title: item.title ?? "No title",
            content: content,
            author: author,
            community: Community(id: "hacker_news", name: "Hacker News", description: "", category: "General", activeToday: 0, onlineNow: 0),
            timeAgo: calculateTimeAgo(from: Date(timeIntervalSince1970: TimeInterval(item.time))),
            likeCount: item.score ?? 0,
            commentCount: item.descendants ?? 0,
            isLiked: false
        )

        var comments: [Comment] = []
        if let kids = item.kids {
            let prefixKids = Array(kids.prefix(20))

            comments = await withTaskGroup(of: Comment?.self) { group in
                for kidId in prefixKids {
                    group.addTask {
                        return await self.fetchItemAsComment(id: kidId)
                    }
                }

                var results: [Comment] = []
                for await c in group {
                    if let c = c { results.append(c) }
                }
                return results.sorted { $0.timeAgo > $1.timeAgo }
            }
        }

        return (thread, comments)
    }

    private func fetchItemAsComment(id: Int) async -> Comment? {
        guard let url = URL(string: "\(baseURL)/item/\(id).json") else { return nil }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let item = try JSONDecoder().decode(HNItem.self, from: data)

            guard let text = item.text, !text.isEmpty, let by = item.by else { return nil }

            return Comment(
                id: String(item.id),
                author: User(id: by, username: by, avatar: "person.circle", role: nil),
                content: cleanHNContent(text),
                timeAgo: calculateTimeAgo(from: Date(timeIntervalSince1970: TimeInterval(item.time))),
                likeCount: 0,
                replies: nil
            )
        } catch {
            return nil
        }
    }

    private func cleanHNContent(_ html: String) -> String {
        var processed = html

        processed = processed.replacingOccurrences(of: "<p>", with: "\n\n")
                             .replacingOccurrences(of: "<pre><code>", with: "\n```\n")
                             .replacingOccurrences(of: "</code></pre>", with: "\n```\n")
                             .replacingOccurrences(of: "<i>", with: "_")
                             .replacingOccurrences(of: "</i>", with: "_")

        processed = processed.replacingOccurrences(of: "<a href=\"([^\"]+)\"[^>]*>([^<]+)</a>", with: "$1", options: .regularExpression, range: nil)
        processed = processed.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression, range: nil)

        let entities = [
            "&quot;": "\"",
            "&apos;": "'",
            "&amp;": "&",
            "&lt;": "<",
            "&gt;": ">",
            "&nbsp;": " ",
            "&#x27;": "'",
            "&#x2F;": "/",
            "&#39;": "'",
            "&#34;": "\"",
            "&hellip;": "..."
        ]

        for (entity, value) in entities {
            processed = processed.replacingOccurrences(of: entity, with: value)
        }

        if let regex = try? NSRegularExpression(pattern: "&#(\\d+);", options: []) {
            let nsString = processed as NSString
            let matches = regex.matches(in: processed, options: [], range: NSRange(location: 0, length: nsString.length))

            for match in matches.reversed() {
                let range = match.range
                let codeRange = match.range(at: 1)

                let codeString = nsString.substring(with: codeRange)
                if let code = Int(codeString), let scalar = UnicodeScalar(code) {
                    let character = String(scalar)
                    processed = (processed as NSString).replacingCharacters(in: range, with: character)
                }
            }
        }

        if let newlineRegex = try? NSRegularExpression(pattern: "(\\s*\\n\\s*){3,}", options: []) {
            processed = newlineRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "\n\n")
        }

        if let blankLineRegex = try? NSRegularExpression(pattern: "\\n\\s+\\n", options: []) {
            processed = blankLineRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "\n\n")
        }

        return processed.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    struct HNItem: Codable {
        let id: Int
        let type: String?
        let by: String?
        let time: Int
        let text: String?
        let url: String?
        let title: String?
        let score: Int?
        let descendants: Int?
        let kids: [Int]?
    }
}
