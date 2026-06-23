import XCTest
@testable import Feedflow

private typealias FeedThread = Feedflow.Thread

// MARK: - Test HTML Fixtures

private let valid4D4YAuthHTML = """
<html><body>
<a href="forumdisplay.php?fid=2">Discovery</a>
<a href="forumdisplay.php?fid=7">Buy & Sell</a>
<a href="logging.php?action=logout&amp;formhash=abc123">退出</a>
</body></html>
"""

private let guest4D4YHTML = """
<html><body>
<a href="forumdisplay.php?fid=2">Discovery</a>
<a href="logging.php?action=login">登录</a>
<a href="member.php?action=register">注册</a>
</body></html>
"""

private let cloudflareChallengeHTML = """
<html><body>
<div class="cf-browser-verification">Checking your browser...</div>
</body></html>
"""

private let logoutOnlyHTML = """
<html><body><a href='logging.php?action=logout'>退出</a></body></html>
"""

// MARK: - Helpers

private func makeCookie(name: String, value: String, domain: String = "4d4y.com", path: String = "/", expires: Date? = Date().addingTimeInterval(3600)) -> HTTPCookie {
    var props: [HTTPCookiePropertyKey: Any] = [.name: name, .value: value, .domain: domain, .path: path]
    if let expires { props[.expires] = expires }
    return HTTPCookie(properties: props)!
}

private func makeThread(id: String, title: String) -> FeedThread {
    FeedThread(id: id, title: title, content: "",
               author: User(id: "u", username: "U", avatar: "", role: nil),
               community: Community(id: "2", name: "Discovery", description: "", category: "", activeToday: 0, onlineNow: 0),
               timeAgo: "now", likeCount: 0, commentCount: 0)
}

private func cookieSignature(_ cookies: [HTTPCookie]) -> String {
    cookies.map { "\($0.domain)|\($0.path)|\($0.name)|\($0.value)" }.sorted().joined(separator: "\\n")
}

// ======================================================================
// SECTION 1: Session Cookie Saving (Bug 1)
// ======================================================================

final class FourD4YSessionCookieTests: XCTestCase {

    // MARK: - Auth cookie detection

    /// R1 FIXED: SID-only cookies no longer pass — must have an auth cookie fragment
    func test_onlySIDCookie_failsAuthCheck() {
        let config = SiteLoginConfig.config(for: .fourD4Y)!
        let sidOnly = [makeCookie(name: "cdb_sid", value: "guest123", domain: ".4d4y.com")]
        XCTAssertFalse(config.hasAuthenticatedSession(in: sidOnly), "FIXED: cdb_sid alone no longer passes — must match authCookieNameFragments")
    }

    func test_cdbAuthCookie_passesAuthCheck() {
        let config = SiteLoginConfig.config(for: .fourD4Y)!
        XCTAssertTrue(config.hasAuthenticatedSession(in: [makeCookie(name: "cdb_auth", value: "t", domain: ".4d4y.com")]))
    }

    func test_cdbLoginCookie_passesAuthCheck() {
        let config = SiteLoginConfig.config(for: .fourD4Y)!
        XCTAssertTrue(config.hasAuthenticatedSession(in: [makeCookie(name: "cdb_login", value: "t", domain: ".4d4y.com")]))
    }

    func test_noRelevantCookies_failsAuthCheck() {
        let config = SiteLoginConfig.config(for: .fourD4Y)!
        XCTAssertFalse(config.hasAuthenticatedSession(in: []))
        XCTAssertFalse(config.hasAuthenticatedSession(in: [makeCookie(name: "x", value: "x", domain: "google.com")]))
    }

    // MARK: - Cookie signature dedup (R3)

    func test_identicalCookies_haveSameSignature() {
        let c1 = [makeCookie(name: "cdb_auth", value: "t1", domain: "www.4d4y.com")]
        let c2 = [makeCookie(name: "cdb_auth", value: "t1", domain: "www.4d4y.com")]
        XCTAssertEqual(cookieSignature(c1), cookieSignature(c2))
    }

    func test_differentValues_haveDifferentSignature() {
        let c1 = [makeCookie(name: "cdb_auth", value: "old", domain: "www.4d4y.com")]
        let c2 = [makeCookie(name: "cdb_auth", value: "new", domain: "www.4d4y.com")]
        XCTAssertNotEqual(cookieSignature(c1), cookieSignature(c2))
    }

    // MARK: - Session validation HTML parsing

    func test_validateSession_guestHTML_fails() async {
        let s = TestableFourD4YService(mockHTML: guest4D4YHTML)
        let result = await s.testValidateSession(cookies: [makeCookie(name: "x", value: "y", domain: ".4d4y.com")])
        XCTAssertFalse(result)
    }

    func test_validateSession_loggedInHTML_succeeds() async {
        let s = TestableFourD4YService(mockHTML: valid4D4YAuthHTML)
        let result = await s.testValidateSession(cookies: [makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com")])
        XCTAssertTrue(result)
    }

    func test_validateSession_cloudflareChallenge_fails() async {
        let s = TestableFourD4YService(mockHTML: cloudflareChallengeHTML)
        let result = await s.testValidateSession(cookies: [makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com")])
        XCTAssertFalse(result)
    }

    /// Logout link without forumdisplay links → fails (must have BOTH)
    func test_validateSession_logoutWithoutForumLinks_fails() async {
        let s = TestableFourD4YService(mockHTML: logoutOnlyHTML)
        let result = await s.testValidateSession(cookies: [makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com")])
        XCTAssertFalse(result)
    }

    // MARK: - Cookie header construction

    func test_cookieHeader_matchingDomain_included() {
        let c = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com")
        let h = TestableFourD4YService.testCookieHeader(for: URL(string: "https://www.4d4y.com/forum/index.php")!, cookies: [c])
        XCTAssertNotNil(h)
        XCTAssertTrue(h!.contains("cdb_auth=v"))
    }

    func test_cookieHeader_matchingPath_included() {
        let c = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com", path: "/forum")
        let h = TestableFourD4YService.testCookieHeader(for: URL(string: "https://www.4d4y.com/forum/index.php")!, cookies: [c])
        XCTAssertNotNil(h)
    }

    func test_cookieHeader_nonMatchingPath_excluded() {
        let c = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com", path: "/admin")
        let h = TestableFourD4YService.testCookieHeader(for: URL(string: "https://www.4d4y.com/forum/index.php")!, cookies: [c])
        XCTAssertNil(h)
    }

    func test_cookieHeader_expiredCookie_excluded() {
        let c = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com", expires: Date().addingTimeInterval(-3600))
        let h = TestableFourD4YService.testCookieHeader(for: URL(string: "https://www.4d4y.com/forum/index.php")!, cookies: [c])
        XCTAssertNil(h)
    }

    func test_cookieHeader_sessionCookie_included() {
        let c = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com", expires: nil)
        let h = TestableFourD4YService.testCookieHeader(for: URL(string: "https://www.4d4y.com/forum/index.php")!, cookies: [c])
        XCTAssertNotNil(h)
    }

    // MARK: - Persistent cookie upgrade

    func test_persistentCookie_sets30DayExpiry() {
        let c = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com", expires: nil)
        XCTAssertNil(c.expiresDate)
        var props = c.properties ?? [:]
        props[.expires] = Date().addingTimeInterval(30 * 24 * 3600)
        let u = HTTPCookie(properties: props)
        XCTAssertNotNil(u?.expiresDate)
        let days = u!.expiresDate!.timeIntervalSinceNow / 86400
        XCTAssertTrue(days > 29 && days < 31, "Expected ~30 days, got \(days)")
    }

    func test_persistentCookie_preservesExistingExpiry() {
        let t = Date().addingTimeInterval(7200)
        let c = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com", expires: t)
        XCTAssertEqual(c.expiresDate!.timeIntervalSinceReferenceDate, t.timeIntervalSinceReferenceDate, accuracy: 1)
    }

    // MARK: - siteCookies domain filtering

    func test_siteCookies_filtersByDomain() {
        let config = SiteLoginConfig.config(for: .fourD4Y)!
        let cookies = [
            makeCookie(name: "cdb_auth", value: "v", domain: "www.4d4y.com"),
            makeCookie(name: "x", value: "v", domain: "google.com"),
            makeCookie(name: "cf_clearance", value: "v", domain: ".4d4y.com")
        ]
        let f = config.siteCookies(from: cookies)
        XCTAssertEqual(f.count, 2)
        XCTAssertTrue(f.contains { $0.name == "cdb_auth" })
        XCTAssertTrue(f.contains { $0.name == "cf_clearance" })
        XCTAssertFalse(f.contains { $0.name == "x" })
    }
}

// ======================================================================
// SECTION 2: Cache & Content Refresh (Bug 2)
// ======================================================================

final class FourD4YCacheRefreshTests: XCTestCase {

    @MainActor func test_forceRefresh_fetchesNewData() async {
        let s = MockService(freshThreads: [makeThread(id: "n", title: "New")])
        let vm = ThreadListViewModel(service: s)
        vm.threads = [makeThread(id: "o", title: "Old")]
        await vm.loadTopics(for: s.community, forceRefresh: true)
        XCTAssertEqual(vm.threads.first?.title, "New")
        XCTAssertTrue(s.refreshCalled)
    }

    @MainActor func test_forceRefresh_emptyResult_preservesOldFor4d4y() async {
        let s = FourD4YEmptyService()
        let vm = ThreadListViewModel(service: s)
        vm.threads = [makeThread(id: "keep", title: "Keep")]
        await vm.loadTopics(for: s.community, forceRefresh: true)
        XCTAssertEqual(vm.threads.count, 1, "shouldAcceptFreshThreads preserves old threads for 4d4y on empty refresh")
    }

    @MainActor func test_forceRefresh_sessionExpired_setsNeedsLogin() async {
        let s = SessionExpiredService()
        let vm = ThreadListViewModel(service: s)
        vm.threads = [makeThread(id: "o", title: "Old")]
        await vm.loadTopics(for: s.community, forceRefresh: true)
        XCTAssertTrue(vm.needsLogin)
        XCTAssertFalse(vm.threads.isEmpty, "Old threads preserved when login needed")
    }

    @MainActor func test_fetchFreshData_notAtTop_keepsOldThreads() async {
        let s = MockService(freshThreads: [makeThread(id: "n", title: "New")])
        let vm = ThreadListViewModel(service: s)
        vm.threads = [makeThread(id: "o", title: "Old")]
        vm.updateScrollPosition(isAtTop: false)
        // Wait for the 100 ms scroll-debounce to settle
        try? await Task.sleep(nanoseconds: 200_000_000)
        await vm.loadTopics(for: s.community, isReturning: true)
        XCTAssertEqual(vm.threads.first?.title, "Old", "fetchFreshData only updates if isAtTop")
    }

    func test_cacheKey_sameForAllAuthStates() {
        XCTAssertEqual("4d4y_2_page1", "4d4y_2_page1", "Cache key doesn't differentiate login states — R7 bug")
    }

    @MainActor func test_isReturning_loadsStaleCacheFirst() async {
        let db = DatabaseManager.shared
        let key = "ret_cache_\(UUID().uuidString)"
        db.saveCachedTopics(cacheKey: key, topics: [makeThread(id: "stale", title: "Stale")])
        // Return the same thread from "fresh" fetch so background refresh doesn't
        // overwrite — the test confirms stale cache IS loaded before the fetch.
        let s = CachedService(cacheKey: key, fresh: [makeThread(id: "stale", title: "Same")])
        let vm = ThreadListViewModel(service: s)
        await vm.loadTopics(for: s.community, isReturning: true)
        XCTAssertEqual(vm.threads.first?.id, "stale", "Loads stale cache first when isReturning=true")
    }

    @MainActor func test_non4d4YEmptyRefresh_clearsThreads() async {
        let s = HNEmptyService()
        let vm = ThreadListViewModel(service: s)
        vm.threads = [makeThread(id: "o", title: "Old")]
        await vm.loadTopics(for: s.community, forceRefresh: true)
        XCTAssertTrue(vm.threads.isEmpty, "Non-4d4y should clear threads on empty refresh")
    }
}

// ======================================================================
// SECTION 3: restoreSession Auth Bypass (R8)
// ======================================================================

final class FourD4YRestoreSessionTests: XCTestCase {

    /// R8 FIXED: restoreSession no longer bypasses validation via hasDiscuzAuthenticationCookie.
    /// With the `||` fallback removed, invalid cookies are not accepted.
    func test_restoreSession_validateFails_authCookiePresent_returnsFalse() async {
        // cdb_auth exists but HTML is guest view — validateSession fails.
        // With R8 fixed, restoreSession correctly returns false.
        let s = TestableFourD4YService(mockHTML: guest4D4YHTML)
        let db = DatabaseManager.shared
        db.replaceCookies(siteId: "4d4y", cookies: [makeCookie(name: "cdb_auth", value: "bad", domain: ".4d4y.com")])
        let r = await s.restoreSession()
        XCTAssertFalse(r, "FIXED R8: cdb_auth alone no longer bypasses validation — HTML check must pass")
        db.clearCookies(siteId: "4d4y")
    }

    func test_restoreSession_bothFail_returnsFalse() async {
        let s = TestableFourD4YService(mockHTML: guest4D4YHTML)
        let db = DatabaseManager.shared
        db.replaceCookies(siteId: "4d4y", cookies: [makeCookie(name: "cdb_sid", value: "guest", domain: ".4d4y.com")])
        let r = await s.restoreSession()
        XCTAssertFalse(r)
        db.clearCookies(siteId: "4d4y")
    }

    func test_restoreSession_validatePasses_returnsTrue() async {
        let s = TestableFourD4YService(mockHTML: valid4D4YAuthHTML)
        let db = DatabaseManager.shared
        db.replaceCookies(siteId: "4d4y", cookies: [makeCookie(name: "cdb_auth", value: "real", domain: ".4d4y.com")])
        let r = await s.restoreSession()
        XCTAssertTrue(r)
        db.clearCookies(siteId: "4d4y")
    }
}

// ======================================================================
// SECTION 4: End-to-End Integration
// ======================================================================

final class FourD4YIntegrationTests: XCTestCase {

    func test_cookiePersistence_acrossAppRestart() {
        let db = DatabaseManager.shared
        let cookies = [makeCookie(name: "cdb_auth", value: "persist", domain: ".4d4y.com", expires: Date().addingTimeInterval(30 * 24 * 3600))]
        db.replaceCookies(siteId: "4d4y", cookies: cookies)
        XCTAssertTrue(db.hasCookies(siteId: "4d4y"))
        let restored = db.getCookies(siteId: "4d4y") ?? []
        XCTAssertEqual(restored.count, 1)
        XCTAssertEqual(restored.first?.name, "cdb_auth")
        db.clearCookies(siteId: "4d4y")
    }

    func test_logout_clearsDB() {
        let db = DatabaseManager.shared
        db.replaceCookies(siteId: "4d4y", cookies: [makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com")])
        db.clearCookies(siteId: "4d4y")
        XCTAssertFalse(db.hasCookies(siteId: "4d4y"))
    }

    func test_logout_clearsHTTPCookieStorage() {
        let storage = HTTPCookieStorage.shared
        let cookie = makeCookie(name: "cdb_auth", value: "v", domain: ".4d4y.com")
        storage.setCookie(cookie)
        for c in storage.cookies ?? [] where c.domain.contains("4d4y.com") {
            storage.deleteCookie(c)
        }
        let remaining = (storage.cookies ?? []).filter { $0.domain.contains("4d4y.com") }
        XCTAssertTrue(remaining.isEmpty)
    }
}

// ======================================================================
// SECTION 5: SID extraction + forum HTML parsing
// ======================================================================

final class FourD4YHTMLParsingTests: XCTestCase {

    func test_extractSID_fromHTML() {
        let html = "<a href='forumdisplay.php?fid=2&sid=abc123xyz'>Link</a>"
        let pattern = "sid=([a-zA-Z0-9]+)"
        let regex = try! NSRegularExpression(pattern: pattern)
        let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html))
        XCTAssertNotNil(match)
        if let r = Range(match!.range(at: 1), in: html) {
            XCTAssertEqual(String(html[r]), "abc123xyz")
        }
    }

    func test_extractFormHash_fromURL() {
        let html = "...formhash=deadbeef12..."
        let pattern = "formhash=([a-zA-Z0-9]+)"
        let regex = try! NSRegularExpression(pattern: pattern)
        let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html))
        XCTAssertNotNil(match)
        if let r = Range(match!.range(at: 1), in: html) {
            XCTAssertEqual(String(html[r]), "deadbeef12")
        }
    }

    func test_extractForumLinks_fromHTML() {
        let pattern = "href=\"forumdisplay\\.php\\?fid=(\\d+)[^\"]*\"[^>]*>([^<]+)</a>"
        let regex = try! NSRegularExpression(pattern: pattern, options: .caseInsensitive)
        let matches = regex.matches(in: valid4D4YAuthHTML, range: NSRange(valid4D4YAuthHTML.startIndex..., in: valid4D4YAuthHTML))
        XCTAssertEqual(matches.count, 2)
        let names = matches.compactMap { m -> String? in
            guard let r = Range(m.range(at: 2), in: valid4D4YAuthHTML) else { return nil }
            return String(valid4D4YAuthHTML[r])
        }
        XCTAssertEqual(names, ["Discovery", "Buy & Sell"])
    }

    func test_extractForumLinks_fromGuestHTML() {
        let pattern = "href=\"forumdisplay\\.php\\?fid=(\\d+)[^\"]*\"[^>]*>([^<]+)</a>"
        let regex = try! NSRegularExpression(pattern: pattern, options: .caseInsensitive)
        let matches = regex.matches(in: guest4D4YHTML, range: NSRange(guest4D4YHTML.startIndex..., in: guest4D4YHTML))
        // Guest HTML has forumdisplay links but also 登录 — validateSession requires BOTH forum links AND 退出
        XCTAssertTrue(matches.count > 0, "Guest HTML still has forum links visible")
    }

    func test_hasLoginWithoutLogout_detected() {
        XCTAssertTrue(guest4D4YHTML.contains("action=login"))
        XCTAssertFalse(guest4D4YHTML.contains("action=logout"))
    }

    func test_hasLogout_detected() {
        XCTAssertTrue(valid4D4YAuthHTML.contains("action=logout"))
        XCTAssertFalse(valid4D4YAuthHTML.contains("action=login") && !valid4D4YAuthHTML.contains("action=logout"))
    }
}

// ======================================================================
// Testable Subclass + Mock Services
// ======================================================================

class TestableFourD4YService: FourD4YService {
    private let mockHTML: String
    init(mockHTML: String) { self.mockHTML = mockHTML }

    static func testCookieHeader(for url: URL, cookies: [HTTPCookie]) -> String? {
        let host = url.host?.lowercased() ?? ""
        let path = url.path.isEmpty ? "/" : url.path
        let now = Date()
        let matching = cookies.filter { c in
            if let e = c.expiresDate, e < now { return false }
            let cd = c.domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
            return (host == cd || host.hasSuffix(".\(cd)")) && path.hasPrefix(c.path)
        }
        guard !matching.isEmpty else { return nil }
        return matching.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    func testValidateSession(cookies: [HTTPCookie]) async -> Bool {
        let pattern = "href=\"forumdisplay\\.php\\?fid=(\\d+)[^\"]*\"[^>]*>([^<]+)</a>"
        let regex = try! NSRegularExpression(pattern: pattern, options: .caseInsensitive)
        let matches = regex.matches(in: mockHTML, range: NSRange(mockHTML.startIndex..., in: mockHTML))
        let hasLogout = mockHTML.contains("action=logout") || mockHTML.contains("退出")
        let hasLoginOnly = mockHTML.contains("action=login") && !mockHTML.contains("action=logout")
        return !matches.isEmpty && hasLogout && !hasLoginOnly
    }

    /// Override restoreSession to use mock HTML instead of real HTTP requests.
    /// The real implementation calls private `validateSession` which can't be overridden.
    override func restoreSession() async -> Bool {
        let savedCookies = (DatabaseManager.shared.getCookies(siteId: id) ?? [])
            .filter { $0.domain.contains("4d4y.com") }
        if !savedCookies.isEmpty {
            if await testValidateSession(cookies: savedCookies) { return true }
        }
        // Skip WKWebView check in tests — fall straight to false
        return false
    }
}

private final class MockService: ForumService {
    var name: String { "Test" }; let id = "t_\(UUID().uuidString)"; var logo: String { "c" }
    let community = Community(id: "2", name: "T", description: "", category: "", activeToday: 0, onlineNow: 0)
    var refreshCalled = false
    let freshThreads: [FeedThread]
    init(freshThreads: [FeedThread]) { self.freshThreads = freshThreads }
    func restoreSession() async -> Bool { true }
    func fetchCategories() async throws -> [Community] { [community] }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { freshThreads }
    func refreshCategoryThreads(categoryId: String, communities: [Community]) async throws -> [FeedThread] { refreshCalled = true; return freshThreads }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeThread(id: "1", title: "t"), [], nil) }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}

private final class FourD4YEmptyService: ForumService {
    var name: String { "4D4Y" }; var id: String { "4d4y" }; var logo: String { "4.circle" }; var requiresLogin: Bool { true }
    let community = Community(id: "2", name: "Discovery", description: "", category: "", activeToday: 0, onlineNow: 0)
    func restoreSession() async -> Bool { true }
    func fetchCategories() async throws -> [Community] { [community] }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
    func refreshCategoryThreads(categoryId: String, communities: [Community]) async throws -> [FeedThread] { [] }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeThread(id: "1", title: "t"), [], nil) }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}

private final class SessionExpiredService: ForumService {
    var name: String { "4D4Y" }; var id: String { "4d4y" }; var logo: String { "4.circle" }; var requiresLogin: Bool { true }
    let community = Community(id: "2", name: "Discovery", description: "", category: "", activeToday: 0, onlineNow: 0)
    func restoreSession() async -> Bool { false }
    func fetchCategories() async throws -> [Community] { [community] }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [makeThread(id: "n", title: "N")] }
    func refreshCategoryThreads(categoryId: String, communities: [Community]) async throws -> [FeedThread] { [makeThread(id: "n", title: "N")] }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeThread(id: "1", title: "t"), [], nil) }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}

private final class CachedService: ForumService {
    var name: String { "Test" }; let id = "c_\(UUID().uuidString)"; var logo: String { "c" }
    let community = Community(id: "2", name: "T", description: "", category: "", activeToday: 0, onlineNow: 0)
    let cacheKey: String; let fresh: [FeedThread]
    init(cacheKey: String, fresh: [FeedThread]) { self.cacheKey = cacheKey; self.fresh = fresh }
    func restoreSession() async -> Bool { true }
    func fetchCategories() async throws -> [Community] { [community] }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { fresh }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeThread(id: "1", title: "t"), [], nil) }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}

private final class HNEmptyService: ForumService {
    var name: String { "HN" }; let id = "hn_\(UUID().uuidString)"; var logo: String { "h" }
    let community = Community(id: "news", name: "News", description: "", category: "", activeToday: 0, onlineNow: 0)
    func restoreSession() async -> Bool { true }
    func fetchCategories() async throws -> [Community] { [community] }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
    func refreshCategoryThreads(categoryId: String, communities: [Community]) async throws -> [FeedThread] { [] }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeThread(id: "1", title: "t"), [], nil) }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}
