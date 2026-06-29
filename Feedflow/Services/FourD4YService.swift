import Foundation
import CoreFoundation
import WebKit

class FourD4YService: ForumService {
    var name: String { "4D4Y" }
    var id: String { "4d4y" }
    var logo: String { "4.circle.fill" }
    var supportsCommenting: Bool { true }
    var supportsThreadCreation: Bool { true }

    private let baseURL = "https://www.4d4y.com/forum"
    private let browserUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    private var currentSID: String?
    private var currentFormHash: String?

    private struct ParsedPostAuthor {
        let userId: String
        let username: String
        let avatar: String

        init(username: String, avatar: String, userId: String = "0") {
            self.userId = userId
            self.username = username
            self.avatar = avatar
        }
    }

    private struct ParsedThreadDetailPost {
        let id: String
        let author: ParsedPostAuthor
        let rawContent: String
        let timeAgo: String
    }

    // GBK Encoding
    private var gbkEncoding: String.Encoding {
        let encoding = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue))
        return String.Encoding(rawValue: encoding)
    }

    func getWebURL(for thread: Thread) -> String {
        return "\(baseURL)/viewthread.php?tid=\(thread.id)"
    }

    func login(username: String, password: String) async throws -> [HTTPCookie] {
        // Login page to get initial cookies and possible hidden fields (e.g., formhash)
        let loginPageURL = URL(string: "\(baseURL)/logging.php?action=login")!
        var initialRequest = URLRequest(url: loginPageURL)
        initialRequest.httpMethod = "GET"
        initialRequest.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")

        // Perform GET to obtain any required cookies (e.g., cf_clearance)
        let (_, _) = try await URLSession.shared.data(for: initialRequest)
        // Grab cookies set by the GET request
        let cookieStorage = HTTPCookieStorage.shared
        guard let loginPageCookies = cookieStorage.cookies(for: loginPageURL) else { return [] }

        // Build POST body – typical Discuz login fields
        var components = URLComponents()
        components.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password),
            URLQueryItem(name: "loginsubmit", value: "yes"),
            URLQueryItem(name: "inajax", value: "1"),
            URLQueryItem(name: "cookietime", value: "2592000") // 30 days persistent cookie
        ]
        let postBody = components.percentEncodedQuery?.data(using: .utf8) ?? Data()

        let loginURL = URL(string: "\(baseURL)/logging.php?action=login&loginsubmit=yes&inajax=1")!
        var request = URLRequest(url: loginURL)
        request.httpMethod = "POST"
        request.httpBody = postBody
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
        // Include cookies from the GET request
        let cookieHeader = HTTPCookie.requestHeaderFields(with: loginPageCookies)
        request.allHTTPHeaderFields?.merge(cookieHeader) { (_, new) in new }

        // Perform POST login
        let (_, response) = try await URLSession.shared.data(for: request)
        // Capture cookies after login from headers as well
        guard let httpResponse = response as? HTTPURLResponse,
              let headerFields = httpResponse.allHeaderFields as? [String: String],
              let url = httpResponse.url else { return [] }

        let newCookies = HTTPCookie.cookies(withResponseHeaderFields: headerFields, for: url)
        return newCookies
    }

    var requiresLogin: Bool { true }

    func restoreSession() async -> Bool {
        let savedCookies = fourD4YCookies(from: DatabaseManager.shared.getCookies(siteId: id) ?? [])

        if !savedCookies.isEmpty {
            if await validateSession(cookies: savedCookies) {
                syncCookies(savedCookies)
                return true
            }
            AppLogger.debug("[4D4Y] Saved cookies did not pass the HTML check; preserving them while checking WKWebView")
        }

        let webCookies = fourD4YCookies(from: await webKitCookies(for: "4d4y.com"))
        if !webCookies.isEmpty {
            AppLogger.debug("[4D4Y] Checking \(webCookies.count) 4d4y cookies from WKWebView")

            if await validateSession(cookies: webCookies) {
                DatabaseManager.shared.replaceCookies(siteId: id, cookies: webCookies)
                syncCookies(webCookies)
                return true
            }

            AppLogger.debug("[4D4Y] WKWebView cookies were not authenticated; keeping stored cookies unchanged")
        }

        // No cookies — attempt auto-login with saved credentials
        if (try? await performAutoLogin()) == true {
            return true
        }

        // No cookies and no saved credentials — login needed
        return false
    }

    private func fourD4YCookies(from cookies: [HTTPCookie]) -> [HTTPCookie] {
        cookies.filter { $0.domain.contains("4d4y.com") }
    }

    private func hasDiscuzAuthenticationCookie(_ cookies: [HTTPCookie]) -> Bool {
        let now = Date()
        return cookies.contains { cookie in
            guard !cookie.value.isEmpty,
                  cookie.expiresDate.map({ $0 > now }) ?? true else {
                return false
            }

            let name = cookie.name.lowercased()
            return name.contains("auth") || name.contains("member")
        }
    }

    private func cookieHeader(for url: URL, cookies: [HTTPCookie]) -> String? {
        let now = Date()
        let host = url.host?.lowercased() ?? ""
        let path = url.path.isEmpty ? "/" : url.path
        let allCookies = cookies
        let matchingCookies = cookies
            .filter { cookie in
                if let expires = cookie.expiresDate, expires < now { return false }
                let cookieDomain = cookie.domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
                let hostMatches = host == cookieDomain || host.hasSuffix(".\(cookieDomain)")
                let pathMatches = path.hasPrefix(cookie.path)
                return hostMatches && pathMatches
            }
        // DEBUG: log matching counts without exposing cookie names or values.
        let matchedNames = Set(matchingCookies.map { $0.name })
        for cookie in allCookies {
            if !matchedNames.contains(cookie.name) {
                let cookieDomain = cookie.domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
                let hostMatches = host == cookieDomain || host.hasSuffix(".\(cookieDomain)")
                let pathMatches = path.hasPrefix(cookie.path)
                let expired = cookie.expiresDate.map { $0 < now } ?? false
                AppLogger.debug("[4D4Y DEBUG] Cookie rejected: domain=\(cookie.domain) path=\(cookie.path) host=\(host) hostMatch=\(hostMatches) pathMatch=\(pathMatches) expired=\(expired)")
            }
        }
        AppLogger.debug("[4D4Y DEBUG] Cookie header for \(host)\(path): sending \(matchedNames.count)/\(allCookies.count) cookies")

        var bestCookieByName: [String: HTTPCookie] = [:]
        for cookie in matchingCookies {
            if let existing = bestCookieByName[cookie.name] {
                let existingDomainLength = existing.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).count
                let candidateDomainLength = cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).count
                let candidateIsBetter =
                    cookie.path.count > existing.path.count ||
                    (cookie.path.count == existing.path.count && candidateDomainLength > existingDomainLength)

                if candidateIsBetter {
                    bestCookieByName[cookie.name] = cookie
                }
            } else {
                bestCookieByName[cookie.name] = cookie
            }
        }

        let dedupedCookies = Array(bestCookieByName.values)
            .sorted {
                if $0.path.count != $1.path.count {
                    return $0.path.count > $1.path.count
                }
                let firstDomainLength = $0.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).count
                let secondDomainLength = $1.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).count
                if firstDomainLength != secondDomainLength {
                    return firstDomainLength > secondDomainLength
                }
                return $0.name < $1.name
            }

        guard !dedupedCookies.isEmpty else { return nil }
        return dedupedCookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    private func syncCookies(_ cookies: [HTTPCookie]) {
        let relevant = fourD4YCookies(from: cookies)
        clearSystemCookies(forDomain: "4d4y.com")
        for cookie in relevant {
            HTTPCookieStorage.shared.setCookie(cookie)
        }
    }

    private func clearSystemCookies(forDomain domain: String) {
        let systemCookies = HTTPCookieStorage.shared.cookies ?? []
        for cookie in systemCookies where cookie.domain.contains(domain) {
            HTTPCookieStorage.shared.deleteCookie(cookie)
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

    private func syncCookiesToSystem() {
        let saved = DatabaseManager.shared.getCookies(siteId: id) ?? []
        // Only sync cookies that actually belong to 4d4y domain
        let relevant = saved.filter { $0.domain.contains("4d4y.com") }
        if relevant.isEmpty {
            AppLogger.debug("[4D4Y] WARNING: No 4d4y cookies found in DB for site '\(id)'. User may not be logged in.")
        } else {
            let names = relevant.map { "\($0.name)(\($0.domain))" }.joined(separator: ", ")
            AppLogger.debug("[4D4Y] Syncing \(relevant.count) cookies to system: \(names)")
        }
        syncCookies(relevant)
    }

    private func validateSession(cookies: [HTTPCookie]) async -> Bool {
        guard let url = URL(string: "\(baseURL)/index.php") else {
            return false
        }

        AppLogger.debug("[4D4Y] Session validation started with \(cookies.count) cookies")
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false
        if let cookieHeader = cookieHeader(for: url, cookies: cookies) {
            request.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        } else {
            AppLogger.debug("[4D4Y] Session validation has no matching cookies for \(url)")
        }

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                AppLogger.debug("[4D4Y] Session validation HTTP \(httpResponse.statusCode)")
            }
            let html = String(data: data, encoding: gbkEncoding) ?? String(decoding: data, as: UTF8.self)
            let pattern = "href=\"forumdisplay\\.php\\?fid=(\\d+)[^\"]*\"[^>]*>([^<]+)</a>"
            let regex = try NSRegularExpression(pattern: pattern, options: .caseInsensitive)
            let matches = regex.matches(in: html, options: [], range: NSRange(html.startIndex..., in: html))
            let forumNames = matches.compactMap { match -> String? in
                guard let r = Range(match.range(at: 2), in: html) else { return nil }
                return String(html[r]).decodingHTMLEntities().trimmingCharacters(in: .whitespacesAndNewlines)
            }

            let lowercasedHTML = html.lowercased()
            let hasLogoutLink = lowercasedHTML.contains("action=logout") || lowercasedHTML.contains("action%3dlogout") || html.contains("退出")
            let hasLoginLink = (lowercasedHTML.contains("action=login") || lowercasedHTML.contains("action%3dlogin")) && !hasLogoutLink
            let hasProtectedDiscovery = forumNames.contains { $0 == "Discovery" }
            let isLoggedIn = !matches.isEmpty && !hasLoginLink && (hasLogoutLink || hasProtectedDiscovery)

            if isLoggedIn {
                AppLogger.debug("[4D4Y DEBUG] Session validation succeeded: forums=\(matches.count), logout=\(hasLogoutLink), discovery=\(hasProtectedDiscovery) → \(forumNames.joined(separator: ", "))")
                // Extract SID/formHash from the validation response so the service
                // instance is fully initialized for subsequent requests (e.g. posting).
                extractSID(from: html)
            } else {
                AppLogger.debug("[4D4Y] Session validation failed: forums=\(matches.count), logout=\(hasLogoutLink), login=\(hasLoginLink), discovery=\(hasProtectedDiscovery), length=\(html.count)")
            }

            return isLoggedIn
        } catch {
            AppLogger.debug("[4D4Y] Session validation error: \(error)")
            return false
        }
    }


    private func loadSavedCookies() -> [HTTPCookie] {
        return DatabaseManager.shared.getCookies(siteId: id) ?? []
    }

    private func fetchContent(url: URL) async throws -> String {
        // Load saved cookies directly from DB
        let savedCookies = DatabaseManager.shared.getCookies(siteId: id) ?? []
        let relevant = fourD4YCookies(from: savedCookies)

        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        request.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.setValue("no-cache", forHTTPHeaderField: "Pragma")
        request.httpShouldHandleCookies = false

        // Manually set cookies in the request header to guarantee they're sent
        // (HTTPCookieStorage automatic handling can silently drop cookies)
        if let cookieHeader = cookieHeader(for: url, cookies: relevant) {
            request.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
            AppLogger.debug("[4D4Y] Sending \(relevant.count) cookies manually")
        }

        let (data, _) = try await URLSession.shared.data(for: request)


        // Try GBK decode first
        var html = ""
        if let decoded = String(data: data, encoding: gbkEncoding) {
            html = decoded
        } else {
            html = String(decoding: data, as: UTF8.self)
        }

        // Only extract SID/FormHash from logged-in pages to avoid poisoning
        // currentSID with guest/challenge SIDs.
        let isLoggedInPage = html.contains("action=logout") || html.contains("退出")
        if isLoggedInPage {
            extractSID(from: html)
        }

        // DEBUG: Log login state indicators from the response HTML
        if url.absoluteString.contains("index.php") || url.absoluteString.contains("forumdisplay.php") {
            let loginIndicators = ["logout", "welcome", "member", "guest", "logginfo", "会员", "游客", "退出", "登录", "注册"]
            for keyword in loginIndicators {
                if let range = html.range(of: keyword, options: .caseInsensitive) {
                    let start = html.index(range.lowerBound, offsetBy: -50, limitedBy: html.startIndex) ?? html.startIndex
                    let end = html.index(range.upperBound, offsetBy: 50, limitedBy: html.endIndex) ?? html.endIndex
                    let snippet = String(html[start..<end]).replacingOccurrences(of: "\n", with: " ")
                    AppLogger.debug("[4D4Y DEBUG] \(url.lastPathComponent): found '\(keyword)' → \(snippet)")
                }
            }
            // Log visible forum links
            if let fRegex = try? NSRegularExpression(pattern: "forumdisplay\\.php\\?fid=(\\d+)[^\">]*\">([^<]+)</a>", options: .caseInsensitive) {
                let matches = fRegex.matches(in: html, range: NSRange(html.startIndex..., in: html))
                let names = matches.compactMap { m -> String? in
                    guard let r = Range(m.range(at: 2), in: html) else { return nil }
                    return String(html[r])
                }
                AppLogger.debug("[4D4Y DEBUG] \(url.lastPathComponent): visible forums (\(matches.count)): \(names.joined(separator: ", "))")
            }
        }

        return html
    }

    private func extractSID(from html: String) {
        // 1. Extract SID
        if let regex = try? NSRegularExpression(pattern: "sid=([a-zA-Z0-9]+)", options: []) {
            let range = NSRange(html.startIndex..., in: html)
            if let match = regex.firstMatch(in: html, options: [], range: range) {
                if let r = Range(match.range(at: 1), in: html) {
                    self.currentSID = String(html[r])
                    AppLogger.debug("[4D4Y] Extracted SID: \(self.currentSID!)")

                    // SID is carried via URL query parameter (not cookies), so we
                    // only store it in-memory.  Writing it to HTTPCookieStorage.shared
                    // would race with auth-cookie writes from handleLoginSuccess /
                    // syncCookies and could overwrite the real login session.
                }
            }
        }

        // 2. Extract FormHash
        if let formHash = extractFormHash(from: html) {
            self.currentFormHash = formHash
            AppLogger.debug("[4D4Y] Extracted FormHash: \(formHash)")
        }
    }

    private func extractFormHash(from html: String) -> String? {
        let patterns = [
            // URL/query style: ...formhash=abcdef12...
            "formhash=([a-zA-Z0-9]+)",
            // Hidden input styles with flexible attribute order and quotes
            "name=['\"]formhash['\"][^>]*value=['\"]([a-zA-Z0-9]+)['\"]",
            "value=['\"]([a-zA-Z0-9]+)['\"][^>]*name=['\"]formhash['\"]",
            // JS assignments found on some Discuz templates
            "(?:var\\s+)?formhash\\s*[:=]\\s*['\"]([a-zA-Z0-9]+)['\"]"
        ]

        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else {
                continue
            }
            let range = NSRange(html.startIndex..., in: html)
            if let match = regex.firstMatch(in: html, options: [], range: range),
               let hashRange = Range(match.range(at: 1), in: html) {
                let candidate = String(html[hashRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !candidate.isEmpty {
                    return candidate
                }
            }
        }

        return nil
    }

    func fetchCategories() async throws -> [Community] {
        return try await fetchCategoriesInternal(retryCount: 0)
    }

    private func fetchCategoriesInternal(retryCount: Int) async throws -> [Community] {
        let url = URL(string: "\(baseURL)/index.php")!
        AppLogger.debug("[4D4Y] Fetching index: \(url)")
        let html = try await fetchContent(url: url)
        AppLogger.debug("[4D4Y] Index fetched. Length: \(html.count)")

        extractSID(from: html)

        var communities: [Community] = []

        // Broad pattern to capture fid and name.
        // Handles: <a href="forumdisplay.php?fid=7" style="">Name</a>
        // Key fix: Allow any attributes after href value before closing >
        let pattern = "href=\"forumdisplay\\.php\\?fid=(\\d+)[^\"]*\"[^>]*>([^<]+)</a>"
        // AppLogger.debug("[4D4Y] Using Regex: \(pattern)")

        let regex = try NSRegularExpression(pattern: pattern, options: .caseInsensitive)
        let range = NSRange(html.startIndex..., in: html)
        let matches = regex.matches(in: html, options: [], range: range)

        AppLogger.debug("[4D4Y] Found \(matches.count) forum matches")

        for match in matches {
            if let fidRange = Range(match.range(at: 1), in: html),
               let nameRange = Range(match.range(at: 2), in: html) {

                let fid = String(html[fidRange])
                let name = String(html[nameRange])

                if !communities.contains(where: { $0.id == fid }) {
                    communities.append(Community(
                        id: fid,
                        name: name,
                        description: "",
                        category: "Forum",
                        activeToday: 0,
                        onlineNow: 0
                    ))
                }
            }
        }

        // If results are empty, try auto-login if possible
        if communities.isEmpty && retryCount == 0 {
            AppLogger.debug("[4D4Y] No categories found. Attempting auto-login and retry...")
            if try await performAutoLogin() {
                return try await fetchCategoriesInternal(retryCount: 1)
            }
        }

        return communities
    }

    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [Thread] {
        return try await fetchCategoryThreadsInternal(categoryId: categoryId, communities: communities, page: page, retryCount: 0)
    }

    func searchThreads(query: String, page: Int) async throws -> ([Thread], Bool) {
        if currentSID == nil {
            _ = try await fetchCategories()
        }

        let sidParameter = currentSID.map { "&sid=\($0)" } ?? ""
        let encodedQuery = gbkEncode(query)
        guard let url = URL(string: "\(baseURL)/search.php?searchsubmit=yes&srchtxt=\(encodedQuery)&searchfield=all&page=\(page)\(sidParameter)") else {
            throw URLError(.badURL)
        }

        let html = try await fetchContent(url: url)
        let pattern = "href=\\\"viewthread\\.php\\?tid=(\\d+)[^\\\"]*\\\"[^>]*>(.*?)</a>"
        let regex = try NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators])
        let matches = regex.matches(in: html, range: NSRange(html.startIndex..., in: html))
        let community = Community(id: "search", name: "Search", description: "", category: "4d4y", activeToday: 0, onlineNow: 0)
        var seenIDs = Set<String>()

        let threads = matches.compactMap { match -> Thread? in
            guard let idRange = Range(match.range(at: 1), in: html),
                  let titleRange = Range(match.range(at: 2), in: html) else {
                return nil
            }

            let id = String(html[idRange])
            let title = cleanContent(String(html[titleRange]))
            guard !title.isEmpty, seenIDs.insert(id).inserted else { return nil }

            return Thread(
                id: id,
                title: title,
                content: "",
                author: User(id: "", username: "Unknown", avatar: "person.circle", role: nil),
                community: community,
                timeAgo: "",
                likeCount: 0,
                commentCount: 0,
                isLiked: false,
                tags: nil
            )
        }

        AppLogger.debug("[4D4Y] Search returned \(threads.count) topics for \(query)")
        return (threads, threads.count >= 20)
    }

    private func fetchCategoryThreadsInternal(categoryId: String, communities: [Community], page: Int, retryCount: Int) async throws -> [Thread] {
        // Ensure we have a SID. If not, try to fetch index first.
        if currentSID == nil {
             _ = try await fetchCategories()
        }

        // Always carry SID when available — Discuz 7.2 requires it even with auth cookies
        let sidParam = currentSID.map { "&sid=\($0)" } ?? ""
        // Add page param
        let pageParam = page > 1 ? "&page=\(page)" : ""
        let cacheBuster = "&_t=\(Int(Date().timeIntervalSince1970))"

        let url = URL(string: "\(baseURL)/forumdisplay.php?fid=\(categoryId)\(sidParam)\(pageParam)\(cacheBuster)")!
        let html = try await fetchContent(url: url)

        var threads: [Thread] = []
        let community = communities.first(where: { $0.id == categoryId }) ?? Community(id: categoryId, name: "Unknown", description: "", category: "", activeToday: 0, onlineNow: 0)

        // Extract thread rows - each row has id="normalthread_*" or id="thread_*"
        // Pattern: Find each thread row, then extract tid, title, author, and reply count from within that row
        let threadRowPattern = "<tbody[^>]*id=\"(?:normalthread_|thread_)(\\d+)\"[^>]*>(.*?)</tbody>"
        let threadRowRegex = try NSRegularExpression(pattern: threadRowPattern, options: [.caseInsensitive, .dotMatchesLineSeparators])
        let threadMatches = threadRowRegex.matches(in: html, options: [], range: NSRange(html.startIndex..., in: html))

        AppLogger.debug("[4D4Y] Fetching threads from: \(url)")
        AppLogger.debug("[4D4Y] Found \(threadMatches.count) thread rows")

        for (index, threadMatch) in threadMatches.enumerated() {
            guard let tidRange = Range(threadMatch.range(at: 1), in: html),
                  let rowContentRange = Range(threadMatch.range(at: 2), in: html) else {
                continue
            }

            let tid = String(html[tidRange])
            let rowContent = String(html[rowContentRange])

            // Extract title from viewthread.php link within this row
            var title = "Unknown Title"
            if let titleRegex = try? NSRegularExpression(pattern: "href=\"viewthread\\.php\\?tid=\\d+[^\"]*\"[^>]*>([^<]+)</a>", options: .caseInsensitive),
               let titleMatch = titleRegex.firstMatch(in: rowContent, options: [], range: NSRange(rowContent.startIndex..., in: rowContent)),
               let titleTextRange = Range(titleMatch.range(at: 1), in: rowContent) {
                title = String(rowContent[titleTextRange]).decodingHTMLEntities()
            }

            // Extract author + UID from <td class="author"><a href="space.php?uid=123">authorname</a> within this row
            var authorName = "Unknown"
            var authorUID: String? = nil
            if let authorRegex = try? NSRegularExpression(pattern: "<td\\s+class=\"author\"[^>]*>.*?<a[^>]+href=[\"\']space\\.php\\?uid=(\\d+)[^\"\']*[\"\'][^>]*>([^<]+)</a>", options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let authorMatch = authorRegex.firstMatch(in: rowContent, options: [], range: NSRange(rowContent.startIndex..., in: rowContent)),
               let uidRange = Range(authorMatch.range(at: 1), in: rowContent),
               let authorTextRange = Range(authorMatch.range(at: 2), in: rowContent) {
                authorUID = String(rowContent[uidRange])
                authorName = String(rowContent[authorTextRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            // Build avatar from UID (same approach as thread detail WAP parsing)
            let resolvedAuthorAvatar: String
            if let uid = authorUID {
                resolvedAuthorAvatar = avatarURL(forUID: uid)
            } else {
                let authorAvatar = extractAvatarURL(from: rowContent)
                resolvedAuthorAvatar = isGenericAvatar(authorAvatar)
                    ? extractAvatarURLFromAuthorUid(in: rowContent)
                    : authorAvatar
            }

            // Extract reply count from <td class="nums"><strong>count</strong> within this row
            var replyCount = 0
            if let numsRegex = try? NSRegularExpression(pattern: "<td\\s+class=\"nums\"[^>]*>.*?<strong>(\\d+)</strong>", options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let numsMatch = numsRegex.firstMatch(in: rowContent, options: [], range: NSRange(rowContent.startIndex..., in: rowContent)),
               let countTextRange = Range(numsMatch.range(at: 1), in: rowContent),
               let count = Int(String(rowContent[countTextRange])) {
                replyCount = count
            }


            // Extract last post time and poster from <td class="lastpost">
            var lastPostTime: String? = nil
            var lastPosterName: String? = nil
            if let lastpostRegex = try? NSRegularExpression(pattern: "<td\\s+class=\"lastpost\"[^>]*>.*?<cite>.*?<a[^>]*>([^<]+)</a>.*?</cite>.*?<em>[^<]*<a[^>]*>([^<]+)</a>", options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let lpMatch = lastpostRegex.firstMatch(in: rowContent, options: [], range: NSRange(rowContent.startIndex..., in: rowContent)) {
                if let timeRange = Range(lpMatch.range(at: 1), in: rowContent) {
                    lastPostTime = String(rowContent[timeRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                }
                if let posterRange = Range(lpMatch.range(at: 2), in: rowContent) {
                    lastPosterName = String(rowContent[posterRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }

            // Debug first few
            if index < 3 {
                AppLogger.debug("[4D4Y] Thread \(index): tid=\(tid), title=\(title), author=\(authorName), replies=\(replyCount)")
            }

            // Add thread
            if !threads.contains(where: { $0.id == tid }) {
                threads.append(Thread(
                    id: tid,
                    title: title,
                    content: "",
                    author: User(id: authorName, username: authorName, avatar: resolvedAuthorAvatar, role: nil),
                    community: community,
                    timeAgo: "",
                    likeCount: 0,
                    commentCount: replyCount,
                    isLiked: false,
                    tags: nil,
                    lastPostTime: lastPostTime,
                    lastPosterName: lastPosterName
                ))
            }
        }

        if threads.isEmpty {
            threads = extractThreadLinksFallback(from: html, community: community)
            AppLogger.debug("[4D4Y] Fallback extracted \(threads.count) thread links")
        }

        AppLogger.debug("[4D4Y] Returning \(threads.count) unique threads")

        // If results are empty and it's the first page, try auto-login if possible
        if threads.isEmpty && page == 1 && retryCount == 0 {
            AppLogger.debug("[4D4Y] No threads found. Attempting auto-login and retry...")
            if try await performAutoLogin() {
                // Clear SID to force refresh after login
                self.currentSID = nil
                return try await fetchCategoryThreadsInternal(categoryId: categoryId, communities: communities, page: page, retryCount: 1)
            }
        }

        return threads
    }

    private func extractThreadLinksFallback(from html: String, community: Community) -> [Thread] {
        let pattern = "<a[^>]+href=[\"']viewthread\\.php\\?tid=(\\d+)[^\"']*[\"'][^>]*>(.*?)</a>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else {
            return []
        }

        let matches = regex.matches(in: html, options: [], range: NSRange(html.startIndex..., in: html))
        var seenIDs = Set<String>()
        var threads: [Thread] = []

        for match in matches {
            guard let tidRange = Range(match.range(at: 1), in: html),
                  let titleRange = Range(match.range(at: 2), in: html) else {
                continue
            }

            let tid = String(html[tidRange])
            let title = cleanThreadListTitle(String(html[titleRange]))
            guard !title.isEmpty, seenIDs.insert(tid).inserted else {
                continue
            }

            let context = surroundingHTMLBlock(in: html, around: match.range)
            let authorName = extractThreadListAuthor(from: context) ?? "Unknown"
            // Try to extract UID from author link for reliable avatar (same approach as detail page)
            let resolvedAuthorAvatar: String
            if let uid = extractUIDFromContext(context) {
                resolvedAuthorAvatar = avatarURL(forUID: uid)
            } else {
                let authorAvatar = extractAvatarURL(from: context)
                resolvedAuthorAvatar = isGenericAvatar(authorAvatar)
                    ? extractAvatarURLFromAuthorUid(in: context)
                    : authorAvatar
            }

            threads.append(Thread(
                id: tid,
                title: title,
                content: "",
                author: User(id: authorName, username: authorName, avatar: resolvedAuthorAvatar, role: nil),
                community: community,
                timeAgo: extractThreadListCreatedTime(from: context) ?? "",
                likeCount: 0,
                commentCount: extractThreadListReplyCount(from: context),
                isLiked: false,
                tags: nil,
                lastPostTime: extractThreadListLastPostTime(from: context),
                lastPosterName: extractThreadListLastPoster(from: context)
            ))
        }

        return threads
    }

    private func cleanThreadListTitle(_ html: String) -> String {
        cleanContent(html)
            .replacingOccurrences(of: "\\[LINK:[^|\\]]+\\|((?:\\[[^\\]]*\\]|[^\\]])*)\\]", with: "$1", options: .regularExpression)
            .replacingOccurrences(of: "\\[IMAGE:[^\\]]+\\]", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func surroundingHTMLBlock(in html: String, around range: NSRange) -> String {
        guard let swiftRange = Range(range, in: html) else { return "" }
        let lowerBound = html[..<swiftRange.lowerBound].range(of: "<tr", options: [.backwards, .caseInsensitive])?.lowerBound
            ?? html[..<swiftRange.lowerBound].range(of: "<li", options: [.backwards, .caseInsensitive])?.lowerBound
            ?? html[..<swiftRange.lowerBound].range(of: "<tbody", options: [.backwards, .caseInsensitive])?.lowerBound
            ?? html.index(swiftRange.lowerBound, offsetBy: -800, limitedBy: html.startIndex)
            ?? html.startIndex

        let upperBound = html[swiftRange.upperBound...].range(of: "</tr>", options: [.caseInsensitive])?.upperBound
            ?? html[swiftRange.upperBound...].range(of: "</li>", options: [.caseInsensitive])?.upperBound
            ?? html[swiftRange.upperBound...].range(of: "</tbody>", options: [.caseInsensitive])?.upperBound
            ?? html.index(swiftRange.upperBound, offsetBy: 1200, limitedBy: html.endIndex)
            ?? html.endIndex

        return String(html[lowerBound..<upperBound])
    }

    private func extractThreadListAuthor(from html: String) -> String? {
        let patterns = [
            "<p>\\s*<a[^>]+href=[\"']space\\.php\\?uid=\\d+[^\"']*[\"'][^>]*>([^<]+)</a>\\s*/\\s*[^<]+</p>",
            "space\\.php\\?uid=\\d+[^>]*>([^<]+)</a>",
            "class=[\"'][^\"']*author[^\"']*[\"'][^>]*>\\s*<a[^>]*>([^<]+)</a>",
            "class=[\"'][^\"']*by[^\"']*[\"'][^>]*>\\s*<a[^>]*>([^<]+)</a>"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html) {
                let author = cleanContent(String(html[range])).trimmingCharacters(in: .whitespacesAndNewlines)
                if !author.isEmpty {
                    return author
                }
            }
        }

        return nil
    }

    /// Extract author UID from space.php?uid=XXXXX link in thread list HTML
    func extractUIDFromContext(_ html: String) -> String? {
        let patterns = [
            "space\\.php\\?uid=(\\d+)",
            "uid=(\\d+)",
        ]
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html) {
                let uid = String(html[range]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !uid.isEmpty {
                    return uid
                }
            }
        }
        return nil
    }

    private func extractThreadListCreatedTime(from html: String) -> String? {
        let patterns = [
            "<p>\\s*<a[^>]+href=[\"']space\\.php\\?uid=\\d+[^\"']*[\"'][^>]*>[^<]+</a>\\s*/\\s*([^<]+)</p>",
            "((?:昨天|前天|\\d+\\s*(?:分钟前|小时前|天前)))"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html) {
                let value = cleanContent(String(html[range])).trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty {
                    return value
                }
            }
        }

        return nil
    }

    private func extractThreadListReplyCount(from html: String) -> Int {
        let patterns = [
            "<a[^>]+class=[\"'][^\"']*num[^\"']*[\"'][^>]*>\\s*(\\d+)\\s*</a>",
            "(?:回复|回覆|回帖|repl(?:y|ies))[^0-9]{0,12}(\\d+)",
            "<strong>(\\d+)</strong>",
            "class=[\"'][^\"']*(?:reply|num|count)[^\"']*[\"'][^>]*>\\s*(\\d+)"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html),
               let count = Int(String(html[range])) {
                return count
            }
        }

        return 0
    }

    private func extractThreadListLastPoster(from html: String) -> String? {
        let patterns = [
            "<p>\\s*<a[^>]+href=[\"']space\\.php\\?username=[^\"']+[\"'][^>]*>([^<]+)</a>\\s*/\\s*<a[^>]+href=[\"']redirect\\.php\\?tid=\\d+[^\"']*goto=lastpost[^\"']*[\"'][^>]*>[^<]+</a>\\s*</p>",
            "lastpost[^>]*>.*?<a[^>]*>([^<]+)</a>"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html) {
                let value = cleanContent(String(html[range])).trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty {
                    return value
                }
            }
        }

        return nil
    }

    private func extractThreadListLastPostTime(from html: String) -> String? {
        let patterns = [
            "<a[^>]+href=[\"']redirect\\.php\\?tid=\\d+[^\"']*goto=lastpost[^\"']*[\"'][^>]*>([^<]+)</a>",
            "(\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2})",
            "(\\d{1,2}:\\d{2})",
            "((?:昨天|前天|\\d+\\s*(?:分钟前|小时前|天前)))"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html) {
                let value = cleanContent(String(html[range])).trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty {
                    return value
                }
            }
        }

        return nil
    }

    private func performAutoLogin() async throws -> Bool {
        AppLogger.debug("[4D4Y] Attempting auto-login...")
        guard let encryptedUsername = DatabaseManager.shared.getSetting(key: "login_\(id)_username"),
              let encryptedPassword = DatabaseManager.shared.getSetting(key: "login_\(id)_password"),
              let username = EncryptionHelper.shared.decrypt(encryptedUsername),
              let password = EncryptionHelper.shared.decrypt(encryptedPassword) else {
            AppLogger.debug("[4D4Y] No saved credentials found for auto-login.")
            return false
        }

        do {
            clearSystemCookies(forDomain: "4d4y.com")
            let cookies = try await login(username: username, password: password)
            let systemCookies = HTTPCookieStorage.shared.cookies ?? []
            let sessionCookies = uniqueCookies(fourD4YCookies(from: cookies + systemCookies))

            if !sessionCookies.isEmpty,
               await validateSession(cookies: sessionCookies) {
                DatabaseManager.shared.replaceCookies(siteId: id, cookies: sessionCookies)
                syncCookies(sessionCookies)
                AppLogger.debug("[4D4Y] Auto-login successful.")
                return true
            }

            AppLogger.debug("[4D4Y] Auto-login did not produce a validated auth cookie set.")
        } catch {
            AppLogger.debug("[4D4Y] Auto-login failed: \(error)")
        }
        return false
    }

    private func uniqueCookies(_ cookies: [HTTPCookie]) -> [HTTPCookie] {
        var ordered: [String: HTTPCookie] = [:]
        for cookie in cookies {
            let key = "\(cookie.name)|\(cookie.domain)|\(cookie.path)"
            ordered[key] = cookie
        }
        return Array(ordered.values)
    }

    func fetchThreadDetail(threadId: String, page: Int) async throws -> (Thread, [Comment], Int?) {
        return try await fetchThreadDetailInternal(threadId: threadId, page: page, retryCount: 0)
    }

    private func fetchThreadDetailInternal(threadId: String, page: Int, retryCount: Int) async throws -> (Thread, [Comment], Int?) {
         // Always carry SID when available — Discuz 7.2 requires it even with auth cookies
        let sidParam = currentSID.map { "&sid=\($0)" } ?? ""
         // Need to handle both standard page param and 'extra' param if relevant, but typically &page=2 works for viewthread.php
         let pageParam = page > 1 ? "&page=\(page)&extra=page%3D1" : ""

         let url = URL(string: "\(baseURL)/viewthread.php?tid=\(threadId)\(sidParam)\(pageParam)")!
         AppLogger.debug("[4D4Y] Fetching thread detail: \(url)")
         let html = try await fetchContent(url: url)

         if let parsed = parseThreadDetailHTML(html, threadId: threadId, page: page) {
             return parsed
         } else {
             // Fallback if regex failed completely or page is empty

             if page == 1 && retryCount == 0 {
                 AppLogger.debug("[4D4Y] No content found in thread detail. Attempting auto-login and retry...")
                 if try await performAutoLogin() {
                     self.currentSID = nil
                     return try await fetchThreadDetailInternal(threadId: threadId, page: page, retryCount: 1)
                 }
             }

             throw NSError(
                domain: "4D4Y",
                code: 422,
                userInfo: [NSLocalizedDescriptionKey: "4D4Y returned a page without parseable post content. The existing cached detail was left unchanged."]
             )
         }
    }

    func parseThreadDetailHTML(_ html: String, threadId: String, page: Int) -> (Thread, [Comment], Int?)? {
       let totalPages = extractThreadDetailTotalPages(from: html)
       let currentFid = extractThreadDetailFid(from: html, threadId: threadId)
       AppLogger.debug("[4D4Y] Extracted Topic FID: \(currentFid)")

       let title = extractThreadDetailTitle(from: html)
       let posts = extractThreadDetailPosts(from: html)
       guard !posts.isEmpty else {
           return nil
       }

       let commentPosts: ArraySlice<ParsedThreadDetailPost>
       let thread: Thread

       if page == 1 {
           let mainPost = posts[0]
           commentPosts = posts.dropFirst()
           thread = Thread(
               id: threadId,
               title: title,
               content: cleanContent(mainPost.rawContent),
               author: user(from: mainPost.author),
               community: Community(id: currentFid, name: "", description: "", category: "", activeToday: 0, onlineNow: 0),
               timeAgo: mainPost.timeAgo,
               likeCount: 0,
               commentCount: commentPosts.count,
               isLiked: false,
               tags: nil
           )
       } else {
           commentPosts = posts[posts.startIndex..<posts.endIndex]
           thread = Thread(
               id: threadId,
               title: title,
               content: "",
               author: User(id: "0", username: "", avatar: "", role: nil),
               community: Community(id: currentFid, name: "", description: "", category: "", activeToday: 0, onlineNow: 0),
               timeAgo: "",
               likeCount: 0,
               commentCount: 0,
               isLiked: false,
               tags: nil
           )
       }

       let comments = commentPosts.map { post in
           Comment(
               id: post.id,
               author: user(from: post.author),
               content: cleanContent(post.rawContent),
               timeAgo: post.timeAgo,
               likeCount: 0,
               replies: nil
           )
       }

       return (thread, comments, totalPages)
    }

    private func user(from author: ParsedPostAuthor) -> User {
       User(
           id: author.userId.isEmpty ? "0" : author.userId,
           username: author.username.isEmpty ? "User" : author.username,
           avatar: author.avatar,
           role: nil
       )
    }

    private func extractThreadDetailPosts(from html: String) -> [ParsedThreadDetailPost] {
       let desktopPosts = extractDesktopThreadDetailPosts(from: html)
       if !desktopPosts.isEmpty {
           return desktopPosts
       }

       return extractWAPThreadDetailPosts(from: html)
    }

    private func extractDesktopThreadDetailPosts(from html: String) -> [ParsedThreadDetailPost] {
       let postAuthors = extractPostAuthors(from: html)
       let matches = postContentMatches(in: html)

       return matches.enumerated().compactMap { index, match in
           guard let pidRange = Range(match.range(at: 1), in: html),
                 let contentRange = Range(match.range(at: 2), in: html) else {
               return nil
           }

           let author = index < postAuthors.count
               ? postAuthors[index]
               : ParsedPostAuthor(username: "User", avatar: "person.circle")

           return ParsedThreadDetailPost(
               id: String(html[pidRange]),
               author: author,
               rawContent: String(html[contentRange]),
               timeAgo: ""
           )
       }
    }

    private func extractWAPThreadDetailPosts(from html: String) -> [ParsedThreadDetailPost] {
       var posts: [ParsedThreadDetailPost] = []
       if let mainPost = extractWAPMainThreadPost(from: html) {
           posts.append(mainPost)
       }
       posts.append(contentsOf: extractWAPReplyPosts(from: html))
       return posts
    }

    private func extractWAPMainThreadPost(from html: String) -> ParsedThreadDetailPost? {
       let patterns = [
           "<div[^>]*class=[\"'][^\"']*detailcon[^\"']*[\"'][^>]*id=[\"']pid(\\d+)[\"'][^>]*>(.*?)</div>\\s*</div>\\s*<div[^>]*class=[\"'][^\"']*detailbtn",
           "<div[^>]*id=[\"']pid(\\d+)[\"'][^>]*class=[\"'][^\"']*detailcon[^\"']*[\"'][^>]*>(.*?)</div>\\s*</div>\\s*<div[^>]*class=[\"'][^\"']*detailbtn",
           "<div[^>]*class=[\"'][^\"']*detailcon[^\"']*[\"'][^>]*id=[\"']pid(\\d+)[\"'][^>]*>(.*?)</div>",
           "<div[^>]*id=[\"']pid(\\d+)[\"'][^>]*class=[\"'][^\"']*detailcon[^\"']*[\"'][^>]*>(.*?)</div>"
       ]

       for pattern in patterns {
           guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
                 let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
                 let pidRange = Range(match.range(at: 1), in: html),
                 let contentRange = Range(match.range(at: 2), in: html),
                 let matchRange = Range(match.range, in: html) else {
               continue
           }

           let pid = String(html[pidRange])
           let prefix = String(html[..<matchRange.lowerBound].suffix(2500))
           return ParsedThreadDetailPost(
               id: pid,
               author: extractWAPAuthor(from: prefix),
               rawContent: String(html[contentRange]),
               timeAgo: extractWAPMainPostTime(from: prefix, pid: pid)
           )
       }

       return nil
    }

    private func extractWAPReplyPosts(from html: String) -> [ParsedThreadDetailPost] {
       let replyList = firstCapture(
           in: html,
           pattern: "<div[^>]*class=[\"'][^\"']*replylist[^\"']*[\"'][^>]*>\\s*<ul>(.*?)</ul>\\s*</div>"
       ) ?? html

       guard let liRegex = try? NSRegularExpression(
           pattern: "<li[^>]*id=[\"']pid(\\d+)[\"'][^>]*>(.*?)</li>",
           options: [.caseInsensitive, .dotMatchesLineSeparators]
       ) else {
           return []
       }

       return liRegex.matches(in: replyList, options: [], range: NSRange(replyList.startIndex..., in: replyList)).compactMap { match in
           guard let pidRange = Range(match.range(at: 1), in: replyList),
                 let blockRange = Range(match.range(at: 2), in: replyList) else {
               return nil
           }

           let pid = String(replyList[pidRange])
           let block = String(replyList[blockRange])
           let top = firstCapture(
               in: block,
               pattern: "<div[^>]*class=[\"'][^\"']*replytop[^\"']*[\"'][^>]*>(.*?)</div>"
           ) ?? block

           guard let rawContent = firstCapture(
               in: block,
               pattern: "<div[^>]*class=[\"'][^\"']*replycon[^\"']*[\"'][^>]*>(.*)</div>\\s*$"
           ) else {
               return nil
           }

           return ParsedThreadDetailPost(
               id: pid,
               author: extractWAPAuthor(from: top),
               rawContent: rawContent,
               timeAgo: extractWAPReplyPostTime(from: top)
           )
       }
    }

    private func extractWAPAuthor(from html: String) -> ParsedPostAuthor {
       guard let regex = try? NSRegularExpression(
           pattern: "<a[^>]+href=[\"']space\\.php\\?uid=(\\d+)[^\"']*[\"'][^>]*>([^<]+)</a>",
           options: [.caseInsensitive, .dotMatchesLineSeparators]
       ) else {
           return ParsedPostAuthor(username: "User", avatar: "person.circle")
       }

       let matches = regex.matches(in: html, options: [], range: NSRange(html.startIndex..., in: html))
       guard let match = matches.last,
             let uidRange = Range(match.range(at: 1), in: html),
             let usernameRange = Range(match.range(at: 2), in: html) else {
           return ParsedPostAuthor(username: "User", avatar: "person.circle")
       }

       let uid = String(html[uidRange])
       let username = cleanContent(String(html[usernameRange])).trimmingCharacters(in: .whitespacesAndNewlines)
       return ParsedPostAuthor(username: username, avatar: avatarURL(forUID: uid), userId: uid)
    }

    private func extractWAPMainPostTime(from html: String, pid: String) -> String {
       let escapedPid = NSRegularExpression.escapedPattern(for: pid)
       let patterns = [
           "<em[^>]*id=[\"']authorposton\(escapedPid)[\"'][^>]*>(.*?)</em>",
           "<em[^>]*>(.*?)</em>"
       ]

       for pattern in patterns {
           if let raw = firstCapture(in: html, pattern: pattern) {
               let time = cleanDiscuzPostTime(raw)
               if !time.isEmpty {
                   return time
               }
           }
       }

       return ""
    }

    private func extractWAPReplyPostTime(from html: String) -> String {
       let patterns = [
           "space\\.php\\?uid=\\d+[^>]*>[^<]+</a>\\s*/\\s*([^<]+)",
           "(\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2})",
           "(\\d{1,2}:\\d{2})"
       ]

       for pattern in patterns {
           if let raw = firstCapture(in: html, pattern: pattern) {
               let time = cleanDiscuzPostTime(raw)
               if !time.isEmpty {
                   return time
               }
           }
       }

       return ""
    }

    private func cleanDiscuzPostTime(_ html: String) -> String {
       cleanContent(html)
           .replacingOccurrences(of: "发表于", with: "")
           .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func extractThreadDetailTitle(from html: String) -> String {
       let patterns = [
           "<div[^>]*class=[\"'][^\"']*\\bdetail\\b[^\"']*[\"'][^>]*>.*?<h2[^>]*>\\s*(?:<a[^>]*>)?\\s*(.*?)\\s*</h2>",
           "<h1[^>]*>\\s*(?:<a[^>]*>)?\\s*(.*?)\\s*</h1>",
           "<title>\\s*(.*?)\\s*</title>",
           "<h2[^>]*>\\s*(?:<a[^>]*>)?\\s*(.*?)\\s*</h2>"
       ]

       for pattern in patterns {
           if let rawTitle = firstCapture(in: html, pattern: pattern) {
               let title = cleanThreadDetailTitle(rawTitle)
               if !title.isEmpty {
                   return title
               }
           }
       }

       return "Unknown Topic"
    }

    private func cleanThreadDetailTitle(_ html: String) -> String {
       var title = cleanContent(html)
           .replacingOccurrences(of: "\\[LINK:[^|\\]]+\\|((?:\\[[^\\]]*\\]|[^\\]])*)\\]", with: "$1", options: .regularExpression)
           .replacingOccurrences(of: "\\[IMAGE:[^\\]]+\\]", with: "", options: .regularExpression)
           .trimmingCharacters(in: .whitespacesAndNewlines)
       if let suffixRange = title.range(of: " - ") {
           title = String(title[..<suffixRange.lowerBound])
       }
       return title
    }

    private func extractThreadDetailFid(from html: String, threadId: String) -> String {
       let decodedHTML = html.replacingOccurrences(of: "&amp;", with: "&")
       let escapedThreadId = NSRegularExpression.escapedPattern(for: threadId)
       let patterns = [
           "\\bfid\\s*=\\s*parseInt\\(['\"](\\d+)['\"]\\)",
           "post\\.php\\?action=reply[^\"']*[?&]fid=(\\d+)[^\"']*[?&]tid=\(escapedThreadId)",
           "class=[\"'][^\"']*current[^\"']*[\"'][^>]*>\\s*<a[^>]+href=[\"']forumdisplay\\.php\\?fid=(\\d+)"
       ]

       for pattern in patterns {
           if let fid = firstCapture(in: decodedHTML, pattern: pattern) {
               return fid
           }
       }

       if let navbar = firstCapture(
           in: decodedHTML,
           pattern: "<div[^>]*class=[\"'][^\"']*navbar[^\"']*[\"'][^>]*>(.*?)</div>"
       ), let fid = allCaptures(in: navbar, pattern: "forumdisplay\\.php\\?fid=(\\d+)").last {
           return fid
       }

       return allCaptures(in: decodedHTML, pattern: "forumdisplay\\.php\\?fid=(\\d+)").last ?? "0"
    }

    private func extractThreadDetailTotalPages(from html: String) -> Int {
       var candidates: [Int] = []

       if let pagesContent = firstCapture(in: html, pattern: "<div[^>]*class=[\"']pages[\"'][^>]*>(.*?)</div>") {
           candidates.append(contentsOf: allCaptures(in: pagesContent, pattern: "(?:>|\\s)(\\d+)(?:<|\\s)").compactMap(Int.init))
       }

       if let seclist = firstCapture(in: html, pattern: "<div[^>]*class=[\"'][^\"']*seclist[^\"']*[\"'][^>]*>(.*?)</div>"),
          let total = firstCapture(in: seclist, pattern: "\\d+\\s*/\\s*(\\d+)").flatMap(Int.init) {
           candidates.append(total)
       }

       return candidates.max() ?? 1
    }

    private func firstCapture(
       in text: String,
       pattern: String,
       group: Int = 1,
       options: NSRegularExpression.Options = [.caseInsensitive, .dotMatchesLineSeparators]
    ) -> String? {
       guard let regex = try? NSRegularExpression(pattern: pattern, options: options),
             let match = regex.firstMatch(in: text, options: [], range: NSRange(text.startIndex..., in: text)),
             match.numberOfRanges > group,
             let range = Range(match.range(at: group), in: text) else {
           return nil
       }

       return String(text[range])
    }

    private func allCaptures(
       in text: String,
       pattern: String,
       group: Int = 1,
       options: NSRegularExpression.Options = [.caseInsensitive, .dotMatchesLineSeparators]
    ) -> [String] {
       guard let regex = try? NSRegularExpression(pattern: pattern, options: options) else {
           return []
       }

       return regex.matches(in: text, options: [], range: NSRange(text.startIndex..., in: text)).compactMap { match in
           guard match.numberOfRanges > group,
                 let range = Range(match.range(at: group), in: text) else {
               return nil
           }
           return String(text[range])
       }
    }

    private func postContentMatches(in html: String) -> [NSTextCheckingResult] {
        let patterns = [
            "class=\"t_msgfont\"[^>]*id=\"postmessage_(\\d+)\"[^>]*>(.*?)</td>",
            "id=\"postmessage_(\\d+)\"[^>]*class=\"t_msgfont\"[^>]*>(.*?)</td>",
            "<td[^>]*id=\"postmessage_(\\d+)\"[^>]*>(.*?)</td>",
            "<div[^>]*id=\"postmessage_(\\d+)\"[^>]*>(.*?)</div>"
        ]

        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else {
                continue
            }
            let matches = regex.matches(in: html, options: [], range: NSRange(html.startIndex..., in: html))
            if !matches.isEmpty {
                return matches
            }
        }

        return []
    }

    private func extractPostAuthors(from html: String) -> [ParsedPostAuthor] {
        let blockPattern = "<td[^>]*class=\"postauthor\"[^>]*>(.*?)</td>"
        let blockRegex = try? NSRegularExpression(pattern: blockPattern, options: [.caseInsensitive, .dotMatchesLineSeparators])
        let blockMatches = blockRegex?.matches(in: html, options: [], range: NSRange(html.startIndex..., in: html)) ?? []

        var authors: [ParsedPostAuthor] = []

        for match in blockMatches {
            guard let blockRange = Range(match.range(at: 1), in: html) else { continue }
            let block = String(html[blockRange])
            let username = extractPostUsername(from: block)
            let parsedAvatar = extractAvatarURL(from: block)
            let avatar = isGenericAvatar(parsedAvatar)
                ? extractAvatarURLFromAuthorUid(in: block)
                : parsedAvatar

            if !username.isEmpty {
                authors.append(ParsedPostAuthor(username: username, avatar: avatar))
            }
        }

        if !authors.isEmpty {
            return authors
        }

        return extractPostAuthorNames(from: html).map {
            ParsedPostAuthor(username: $0, avatar: "person.circle")
        }
    }

    private func extractPostAuthorNames(from html: String) -> [String] {
        let authorPattern = "class=\"postauthor\"[^>]*>.*?class=\"postinfo\"[^>]*>.*?<a[^>]*>([^<]+)</a>"
        guard let authorRegex = try? NSRegularExpression(pattern: authorPattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else {
            return []
        }

        return authorRegex.matches(in: html, options: [], range: NSRange(html.startIndex..., in: html)).compactMap { match in
            guard let usernameRange = Range(match.range(at: 1), in: html) else { return nil }
            return String(html[usernameRange]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    private func extractPostUsername(from block: String) -> String {
        let patterns = [
            "class=\"postinfo\"[^>]*>.*?<a[^>]*>([^<]+)</a>",
            "<a[^>]+href=\"space\\.php\\?uid=\\d+[^\"]*\"[^>]*>([^<]+)</a>",
            "<a[^>]+href=\"member\\.php[^\"]*\"[^>]*>([^<]+)</a>"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]),
               let match = regex.firstMatch(in: block, options: [], range: NSRange(block.startIndex..., in: block)),
               let range = Range(match.range(at: 1), in: block) {
                return String(block[range]).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }

        return ""
    }

    private func extractAvatarURLFromAuthorUid(in html: String) -> String {
        let decodedHTML = html.replacingOccurrences(of: "&amp;", with: "&")
        let patterns = [
            "(?:space|member)\\.php\\?[^\"'>]*[?&]uid=(\\d+)",
            "space\\.php\\?uid=(\\d+)",
            "space-uid-(\\d+)\\.html",
            "uid-(\\d+)",
            "uid=(\\d+)"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = regex.firstMatch(in: decodedHTML, options: [], range: NSRange(decodedHTML.startIndex..., in: decodedHTML)),
               let range = Range(match.range(at: 1), in: decodedHTML) {
                return avatarURL(forUID: String(decodedHTML[range]))
            }
        }

        return "person.circle"
    }

    func avatarURL(forUID uid: String) -> String {
        let trimmedUID = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let numericUID = Int(trimmedUID) else {
            return "person.circle"
        }

        let paddedUID = String(format: "%09d", numericUID)
        let characters = Array(paddedUID)
        let firstGroup = String(characters[0..<3])
        let secondGroup = String(characters[3..<5])
        let thirdGroup = String(characters[5..<7])
        let fourthGroup = String(characters[7..<9])

        return "https://img02.4d4y.com/forum/uc_server/data/avatar/\(firstGroup)/\(secondGroup)/\(thirdGroup)/\(fourthGroup)_avatar_middle.jpg"
    }

    private func isGenericAvatar(_ avatar: String) -> Bool {
        let normalized = avatar.trimmingCharacters(in: .whitespacesAndNewlines)
        return normalized.isEmpty ||
            normalized == "person.circle" ||
            normalized == "person.circle.fill" ||
            normalized == "person.crop.circle" ||
            normalized == "person.crop.circle.fill"
    }

    private func extractAvatarURL(from html: String) -> String {
        let patterns = [
            "<img[^>]+class=[\"'][^\"']*avatar[^\"']*[\"'][^>]+(?:src|data-src)=[\"']([^\"']+)[\"']",
            "<img[^>]+(?:src|data-src)=[\"']([^\"']+)[\"'][^>]+class=[\"'][^\"']*avatar[^\"']*[\"']",
            "<img[^>]+(?:src|data-src|file)=[\"']([^\"']*(?:avatar|uc_server|face|head)[^\"']*)[\"']",
            "<img[^>]+srcset=[\"']([^\"']*(?:avatar|uc_server|face|head)[^\"']*)[\"']",
            "background(?:-image)?\\s*:\\s*url\\([\"']?([^\"')]+(?:avatar|uc_server|face|head)[^\"')]+)[\"']?\\)"
        ]

        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = regex.firstMatch(in: html, options: [], range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range(at: 1), in: html) {
                return normalizeAvatarURL(firstAvatarURL(from: String(html[range])))
            }
        }

        return "person.circle"
    }

    private func firstAvatarURL(from value: String) -> String {
        value
            .components(separatedBy: ",")
            .first?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: .whitespaces)
            .first ?? value
    }

    private func normalizeAvatarURL(_ rawURL: String) -> String {
        let url = rawURL
            .replacingOccurrences(of: "&amp;", with: "&")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if url.isEmpty {
            return "person.circle"
        }

        if url.hasPrefix("//") {
            return "https:\(url)"
        }

        if url.hasPrefix("http") {
            return url
        }

        if url.hasPrefix("/") {
            return "https://www.4d4y.com\(url)"
        }

        if url.hasPrefix("uc_server/") {
            return "https://www.4d4y.com/\(url)"
        }

        if url.hasPrefix("data/avatar/") {
            return "https://img02.4d4y.com/forum/uc_server/\(url)"
        }

        return "\(baseURL)/\(url)"
    }

    func postComment(topicId: String, categoryId: String, content: String) async throws {
        do {
            try await postCommentInternal(topicId: topicId, categoryId: categoryId, content: content)
        } catch {
            let errorString = error.localizedDescription
            if errorString.contains("未登录") || errorString.contains("登录") || errorString.contains("login") || errorString.contains("无权访问") {
                AppLogger.debug("[4D4Y] Auth error detected during reply. Attempting auto-login...")
                if try await performAutoLogin() {
                    // Reset session identifiers to force fresh ones during retry
                    self.currentSID = nil
                    self.currentFormHash = nil

                    AppLogger.debug("[4D4Y] Retrying reply after auto-login...")
                    try await postCommentInternal(topicId: topicId, categoryId: categoryId, content: content)
                    return
                }
            }
            throw error
        }
    }

    private func postCommentInternal(topicId: String, categoryId: String, content: String) async throws {
        // Ensure system storage has our saved cookies
        syncCookiesToSystem()

        // 1. Ensure we have a formhash
        if currentFormHash == nil {
            try await ensureFormHashForReply(topicId: topicId, categoryId: categoryId)
        }

        guard let formhash = currentFormHash else {
            throw NSError(domain: "4D4Y", code: 401, userInfo: [NSLocalizedDescriptionKey: "No formhash found. Are you logged in?"])
        }

        // 2. Prepare POST URL - Include SID and inajax=1
        let sidParam = currentSID.map { "&sid=\($0)" } ?? ""
        let url = URL(string: "\(baseURL)/post.php?action=reply&fid=\(categoryId)&tid=\(topicId)&extra=&replysubmit=yes&inajax=1\(sidParam)")!

        // 3. Construct Body
        let postData: [String: String] = [
            "formhash": formhash,
            "posttime": "\(Int(Date().timeIntervalSince1970))",
            "wysiwyg": "1",
            "noticeauthor": "",
            "noticetrimstr": "",
            "noticeauthormsg": "",
            "subject": "",
            "message": content,
            "replysubmit": "yes",
            "inajax": "1"
        ]

        // Manual body building with GBK encoding
        var parts: [String] = []
        for (key, value) in postData {
            let encodedKey = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
            let encodedValue = gbkEncode(value)
            parts.append("\(encodedKey)=\(encodedValue)")
        }
        let bodyString = parts.joined(separator: "&")
        guard let bodyData = bodyString.data(using: .utf8) else {
            throw NSError(domain: "4D4Y", code: 400, userInfo: [NSLocalizedDescriptionKey: "Failed to encode post body."])
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = bodyData

        // Comprehensive headers to mimic a browser AJAX request
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/xml, */*", forHTTPHeaderField: "Accept")
        request.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        request.setValue("\(baseURL)/viewthread.php?tid=\(topicId)", forHTTPHeaderField: "Referer")
        request.setValue("https://www.4d4y.com", forHTTPHeaderField: "Origin")

        // Note: URLSession.shared.data(for:) automatically handles HTTPCookieStorage.shared
        // but we can manually inject if needed. Given we just called syncCookiesToSystem(),
        // the storage should be current.

        AppLogger.debug("[4D4Y] Sending AJAX reply (tid=\(topicId))...")
        let (data, response) = try await URLSession.shared.data(for: request)

        if let httpResponse = response as? HTTPURLResponse {
            AppLogger.debug("[4D4Y] Reply response: \(httpResponse.statusCode)")

            let responseString = String(data: data, encoding: gbkEncoding) ?? String(decoding: data, as: UTF8.self)

            if responseString.contains("succeed") || responseString.contains("成功") || responseString.contains("发布") {
                AppLogger.debug("[4D4Y] Reply successful.")
            } else {
                AppLogger.debug("[4D4Y] Reply response content redacted. Length: \(responseString.count)")
                var errorMessage = "Unknown error"
                if let regex = try? NSRegularExpression(pattern: "<!\\[CDATA\\[(.*?)\\]\\]>", options: [.dotMatchesLineSeparators]),
                   let match = regex.firstMatch(in: responseString, options: [], range: NSRange(responseString.startIndex..., in: responseString)),
                   let range = Range(match.range(at: 1), in: responseString) {
                    errorMessage = String(responseString[range])
                } else if responseString.contains("ajaxerror") {
                    errorMessage = "Not logged in or access denied (AJAX error)."
                }

                AppLogger.debug("[4D4Y] Reply FAILED: \(errorMessage)")
                throw NSError(domain: "4D4Y", code: 403, userInfo: [NSLocalizedDescriptionKey: errorMessage])
            }
        }
    }

    private func ensureFormHashForReply(topicId: String, categoryId: String) async throws {
        if currentFormHash != nil {
            return
        }

        let sidParam = currentSID.map { "&sid=\($0)" } ?? ""
        let threadURL = URL(string: "\(baseURL)/viewthread.php?tid=\(topicId)\(sidParam)")!
        _ = try await fetchContent(url: threadURL)

        if currentFormHash == nil {
            let indexURL = URL(string: "\(baseURL)/index.php")!
            _ = try await fetchContent(url: indexURL)
        }

        if currentFormHash == nil {
            let replyFormURL = URL(string: "\(baseURL)/post.php?action=reply&fid=\(categoryId)&tid=\(topicId)\(sidParam)")!
            _ = try await fetchContent(url: replyFormURL)
        }
    }

    func createThread(categoryId: String, title: String, content: String) async throws {
        // Ensure system storage has our saved cookies
        syncCookiesToSystem()

        // 1. Ensure we have a formhash
        if currentFormHash == nil {
            _ = try await fetchCategories() // Usually index has a formhash too
        }

        guard let formhash = currentFormHash else {
            throw NSError(domain: "4D4Y", code: 401, userInfo: [NSLocalizedDescriptionKey: "No formhash found. Are you logged in?"])
        }

        // 2. Prepare POST URL
        let sidParam = currentSID.map { "&sid=\($0)" } ?? ""
        let url = URL(string: "\(baseURL)/post.php?action=newthread&fid=\(categoryId)&extra=&topicsubmit=yes&inajax=1\(sidParam)")!

        // 3. Construct Body
        let postData: [String: String] = [
            "formhash": formhash,
            "posttime": "\(Int(Date().timeIntervalSince1970))",
            "wysiwyg": "1",
            "subject": title,
            "message": content,
            "topicsubmit": "yes",
            "inajax": "1"
        ]

        var parts: [String] = []
        for (key, value) in postData {
            let encodedKey = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
            let encodedValue = gbkEncode(value)
            parts.append("\(encodedKey)=\(encodedValue)")
        }
        let bodyString = parts.joined(separator: "&")
        guard let bodyData = bodyString.data(using: .utf8) else {
            throw NSError(domain: "4D4Y", code: 400, userInfo: [NSLocalizedDescriptionKey: "Failed to encode post body."])
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = bodyData
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/xml, */*", forHTTPHeaderField: "Accept")
        request.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")

        AppLogger.debug("[4D4Y] Creating new thread (fid=\(categoryId))...")
        let (data, response) = try await URLSession.shared.data(for: request)

        if let httpResponse = response as? HTTPURLResponse {
            let responseString = String(data: data, encoding: gbkEncoding) ?? String(decoding: data, as: UTF8.self)

            if responseString.contains("succeed") || responseString.contains("成功") {
                AppLogger.debug("[4D4Y] Thread creation successful.")
            } else {
                AppLogger.debug("[4D4Y] Thread creation failed. Response length: \(responseString.count)")
                throw NSError(domain: "4D4Y", code: 403, userInfo: [NSLocalizedDescriptionKey: "Failed to create thread."])
            }
        }
    }

    private func gbkEncode(_ string: String) -> String {
        guard let data = string.data(using: gbkEncoding) else {
            return string.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        }

        return data.map { byte in
            // Keep alphanumeric and safe chars as is, percent encode others
            if (48...57).contains(byte) || (65...90).contains(byte) || (97...122).contains(byte) ||
               byte == 45 || byte == 95 || byte == 46 || byte == 42 {
                return String(UnicodeScalar(byte))
            } else {
                return String(format: "%%%02X", byte)
            }
        }.joined()
    }

    private func cleanContent(_ html: String) -> String {
        var processed = html

        // 0. Remove attachment info (class="t_attach")
        // Pattern: <div class="t_attach">...</div> or <ignore_js_op>...</ignore_js_op>
        do {
            let attachRegex = try NSRegularExpression(pattern: "<div\\s+class=\"t_attach\"[^>]*>.*?</div>", options: [.caseInsensitive, .dotMatchesLineSeparators])
            processed = attachRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "")

            // Also remove ignore_js_op tags often used for attachments
            let ignoreRegex = try NSRegularExpression(pattern: "<ignore_js_op>.*?</ignore_js_op>", options: [.caseInsensitive, .dotMatchesLineSeparators])
            processed = ignoreRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "")
        } catch {
            AppLogger.debug("Regex error (attachments): \(error)")
        }

        // 1. Handle Images
        // Replace <img src="..."> with [IMAGE:url] marker
        // Pattern: <img[^>]+src="([^">]+)"[^>]*>
        // Note: Discuz sometimes uses 'file="url"' or 'onload' attributes, but standard is src.
        // We will try to capture the src.
        do {
            let imgRegex = try NSRegularExpression(pattern: "<img[^>]+src=\"([^\">]+)\"[^>]*>", options: .caseInsensitive)
            let matches = imgRegex.matches(in: processed, range: NSRange(processed.startIndex..., in: processed))

            for match in matches.reversed() {
                if let srcRange = Range(match.range(at: 1), in: processed),
                   let fullMatchRange = Range(match.range, in: processed) {
                    let src = String(processed[srcRange])

                    // Exclude smilies/emojis and UI images from being turned into big block images
                    if src.contains("smilies") || src.contains("images/default") || src.contains("images/common") || src.contains("common/back.gif") {
                         // Attempt to replace with a generic emoji if possible, or just remove to avoid giant block
                         // Ideally we map these, but for now removing avoids the UI bug.
                         processed.replaceSubrange(fullMatchRange, with: "")
                    } else {
                        let fullSrc = src.starts(with: "http") ? src : "\(baseURL)/\(src)"
                        processed.replaceSubrange(fullMatchRange, with: "\n[IMAGE:\(fullSrc)]\n")
                    }
                }
            }
        } catch {
            AppLogger.debug("Regex error (images): \(error)")
        }

        // 2. Handle line breaks
        processed = processed.replacingOccurrences(of: "<br />", with: "\n")
        processed = processed.replacingOccurrences(of: "<br>", with: "\n")
        processed = processed.replacingOccurrences(of: "</p>", with: "\n\n")

        // 2.1. Handle blockquotes: wrap in [QUOTE] markers, strip inner <a> links
        do {
            let bqRegex = try NSRegularExpression(
                pattern: "<blockquote[^>]*>(.*?)</blockquote>",
                options: [.caseInsensitive, .dotMatchesLineSeparators]
            )
            let bqMatches = bqRegex.matches(in: processed, range: NSRange(processed.startIndex..., in: processed))
            for match in bqMatches.reversed() {
                guard let innerRange = Range(match.range(at: 1), in: processed),
                      let fullRange = Range(match.range, in: processed) else { continue }
                var inner = String(processed[innerRange])
                // Strip <a> tags inside blockquote: keep text, discard link
                inner = inner.replacingOccurrences(
                    of: "<a[^>]+>",
                    with: "",
                    options: .regularExpression
                )
                inner = inner.replacingOccurrences(
                    of: "</a>",
                    with: "",
                    options: .caseInsensitive
                )
                processed.replaceSubrange(fullRange, with: "\n[QUOTE]\(inner)[/QUOTE]\n")
            }
        } catch {
            AppLogger.debug("Regex error (blockquotes): \(error)")
        }

        // 2.5. Extract links (<a href="...">) before stripping tags
        // Preserve as [LINK:url|title] so LinkedTextView can render titled links
        do {
            let linkRegex = try NSRegularExpression(
                pattern: "<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                options: [.caseInsensitive, .dotMatchesLineSeparators]
            )
            let linkMatches = linkRegex.matches(in: processed, range: NSRange(processed.startIndex..., in: processed))

            for match in linkMatches.reversed() {
                if let hrefRange = Range(match.range(at: 1), in: processed),
                   let textRange = Range(match.range(at: 2), in: processed),
                   let fullRange = Range(match.range, in: processed) {
                    let href = String(processed[hrefRange])
                    let linkText = String(processed[textRange])
                        .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                        .trimmingCharacters(in: .whitespacesAndNewlines)

                    if href.hasPrefix("#") || href.hasPrefix("javascript:") || href.contains("images/common") { continue }

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
        // Discuz/GBK often leaves entities like &#8203; (zero width space) or &amp;
        processed = processed
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&#128515;", with: "😃") // Example emoji
            // Basic numeric entity decoder

        // specific entity fix for common ones
        processed = processed.replacingOccurrences(of: "&#8203;", with: "") // Zero width space

        // Generic Numeric Entity Decoder (Simple approach)
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

        // 5. Collapse excessive whitespace/newlines
        // Replace 3+ consecutive newlines (or 2+ blank lines) with exactly 2 newlines (one blank line)
        // Also handle cases where spaces are between newlines
        if let newlineRegex = try? NSRegularExpression(pattern: "(\\s*\\n\\s*){3,}", options: []) {
            processed = newlineRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "\n\n")
        }

        // Ensure 2 newlines don't have extra spaces between them
        if let blankLineRegex = try? NSRegularExpression(pattern: "\\n\\s+\\n", options: []) {
            processed = blankLineRegex.stringByReplacingMatches(in: processed, range: NSRange(processed.startIndex..., in: processed), withTemplate: "\n\n")
        }

        return processed.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
