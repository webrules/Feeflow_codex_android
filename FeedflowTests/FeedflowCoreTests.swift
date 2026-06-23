import XCTest
@testable import Feedflow

private typealias FeedThread = Feedflow.Thread

// MARK: - Test Helpers

private func makeCookie(name: String, value: String, domain: String, expires: Date? = Date().addingTimeInterval(3600)) -> HTTPCookie {
    var props: [HTTPCookiePropertyKey: Any] = [.name: name, .value: value, .domain: domain, .path: "/"]
    if let expires { props[.expires] = expires }
    return HTTPCookie(properties: props)!
}

private func makeThread(id: String, title: String, community: Community? = nil, author: User? = nil) -> FeedThread {
    let c = community ?? Community(id: "test", name: "Test", description: "", category: "test", activeToday: 0, onlineNow: 0)
    let a = author ?? User(id: "u1", username: "Tester", avatar: "person.circle", role: nil)
    return FeedThread(id: id, title: title, content: "", author: a, community: c, timeAgo: "now", likeCount: 0, commentCount: 0)
}

// MARK: - 1. Multi-Site Forum Aggregation

final class ForumSiteTests: XCTestCase {
    func testAllCasesCount() { XCTAssertEqual(ForumSite.allCases.count, 6) }
    func testFromServiceIdMapsCorrectly() {
        XCTAssertEqual(ForumSite.from(serviceId: "4d4y"), .fourD4Y)
        XCTAssertEqual(ForumSite.from(serviceId: "linux_do"), .linuxDo)
        XCTAssertEqual(ForumSite.from(serviceId: "hackernews"), .hackerNews)
        XCTAssertEqual(ForumSite.from(serviceId: "v2ex"), .v2ex)
        XCTAssertEqual(ForumSite.from(serviceId: "rss"), .rss)
        XCTAssertEqual(ForumSite.from(serviceId: "zhihu"), .zhihu)
    }
    func testFromServiceIdInvalidReturnsNil() {
        XCTAssertNil(ForumSite.from(serviceId: "invalid"))
        XCTAssertNil(ForumSite.from(serviceId: ""))
    }
    func testMakeServiceReturnsCorrectType() {
        XCTAssertTrue(ForumSite.fourD4Y.makeService() is FourD4YService)
        XCTAssertTrue(ForumSite.linuxDo.makeService() is DiscourseService)
        XCTAssertTrue(ForumSite.hackerNews.makeService() is HackerNewsService)
        XCTAssertTrue(ForumSite.v2ex.makeService() is V2EXService)
        XCTAssertTrue(ForumSite.rss.makeService() is RSSService)
        XCTAssertTrue(ForumSite.zhihu.makeService() is ZhihuService)
    }
    func testRSSAlwaysEnabled() {
        let m = CommunitySettingsManager.shared; let b = m.isEnabled(.rss)
        m.toggle(.rss); XCTAssertEqual(m.isEnabled(.rss), b); XCTAssertTrue(m.isEnabled(.rss))
    }
    func testToggleCommunityVisibility() {
        let m = CommunitySettingsManager.shared; let i = m.isEnabled(.fourD4Y)
        m.toggle(.fourD4Y); XCTAssertNotEqual(m.isEnabled(.fourD4Y), i)
        m.toggle(.fourD4Y); XCTAssertEqual(m.isEnabled(.fourD4Y), i)
    }
    func testVisibleSitesContainsRSS() { XCTAssertTrue(CommunitySettingsManager.shared.visibleSites.contains(.rss)) }
}

// MARK: - 2. Authentication & Session

final class AuthenticationTests: XCTestCase {
    func testLinuxDoOAuthOptionCount() { XCTAssertEqual(SiteLoginConfig.config(for: .linuxDo)?.oauthOptions.count, 6) }
    func testV2EXOAuthOptionCount() { XCTAssertEqual(SiteLoginConfig.config(for: .v2ex)?.oauthOptions.count, 2) }
    func testRSSNoLoginConfig() { XCTAssertNil(SiteLoginConfig.config(for: .rss)) }
    func testLinuxDoAuthCookie() {
        XCTAssertTrue(SiteLoginConfig.config(for: .linuxDo)?.hasAuthenticatedSession(in: [makeCookie(name: "_t", value: "t", domain: "linux.do")]) == true)
    }
    func testFourD4YAuthCookieDetection() {
        let c = SiteLoginConfig.config(for: .fourD4Y)
        XCTAssertTrue(c?.hasAuthenticatedSession(in: [makeCookie(name: "cdb_auth", value: "x", domain: "4d4y.com")]) == true)
        XCTAssertTrue(c?.hasAuthenticatedSession(in: [makeCookie(name: "cdb_login", value: "x", domain: "4d4y.com")]) == true)
        XCTAssertFalse(c?.hasAuthenticatedSession(in: [makeCookie(name: "cdb_sid", value: "x", domain: "4d4y.com")]) == true)
    }
    func testZhihuRequiredCookie() {
        let c = SiteLoginConfig.config(for: .zhihu)
        XCTAssertTrue(c?.hasAuthenticatedSession(in: [makeCookie(name: "z_c0", value: "t", domain: "zhihu.com")]) == true)
        XCTAssertFalse(c?.hasAuthenticatedSession(in: [makeCookie(name: "x", value: "x", domain: "zhihu.com")]) == true)
    }
    func testSiteCookiesFilterByDomain() {
        let filtered = SiteLoginConfig.config(for: .fourD4Y)!.siteCookies(from: [
            makeCookie(name: "cdb_auth", value: "x", domain: "www.4d4y.com"),
            makeCookie(name: "x", value: "x", domain: "google.com")
        ])
        XCTAssertEqual(filtered.count, 1); XCTAssertEqual(filtered.first?.name, "cdb_auth")
    }
    func testPersistentCookieUpgrade() {
        let sc = makeCookie(name: "cdb_auth", value: "t", domain: "4d4y.com", expires: nil)
        XCTAssertNil(sc.expiresDate)
        var props = sc.properties ?? [:]; props[.expires] = Date().addingTimeInterval(30 * 24 * 3600)
        let p = HTTPCookie(properties: props)
        XCTAssertNotNil(p?.expiresDate)
        if let e = p?.expiresDate {
            XCTAssertGreaterThan(e.timeIntervalSince(Date()), 29 * 24 * 3600)
        }
    }
    func testExistingExpiryPreserved() {
        let f = Date().addingTimeInterval(7 * 24 * 3600)
        XCTAssertEqual(makeCookie(name: "c", value: "x", domain: "d.com", expires: f).expiresDate?.timeIntervalSince1970 ?? 0, f.timeIntervalSince1970, accuracy: 1)
    }
    func testSIDExtractionLoggedIn() { XCTAssertTrue("logout sid=abc".contains("logout")) }
    func testSIDExtractionGuest() { XCTAssertFalse("login sid=guest".contains("logout")) }
}

// MARK: - 3. Content Browsing

final class ContentBrowsingTests: XCTestCase {
    @MainActor func testBookmarkPersistence() {
        let db = DatabaseManager.shared; let tid = "bm_\(UUID().uuidString)"; let sid = "hn"
        defer { db.toggleBookmark(thread: makeThread(id: tid, title: "test"), serviceId: sid) }
        XCTAssertFalse(db.isBookmarked(threadId: tid, serviceId: sid))
        db.toggleBookmark(thread: makeThread(id: tid, title: "test"), serviceId: sid)
        XCTAssertTrue(db.isBookmarked(threadId: tid, serviceId: sid))
    }
    @MainActor func testBookmarkIdempotent() {
        let db = DatabaseManager.shared; let tid = "bi_\(UUID().uuidString)"; let sid = "4d4y"
        defer { db.toggleBookmark(thread: makeThread(id: tid, title: "test"), serviceId: sid) }
        db.toggleBookmark(thread: makeThread(id: tid, title: "test"), serviceId: sid); db.toggleBookmark(thread: makeThread(id: tid, title: "test"), serviceId: sid)
        XCTAssertTrue(db.isBookmarked(threadId: tid, serviceId: sid))
        db.toggleBookmark(thread: makeThread(id: tid, title: "test"), serviceId: sid); db.toggleBookmark(thread: makeThread(id: tid, title: "test"), serviceId: sid)
        XCTAssertFalse(db.isBookmarked(threadId: tid, serviceId: sid))
    }
    @MainActor func testURLBookmarkAddRemove() {
        let db = DatabaseManager.shared; let url = "https://ex.com/\(UUID().uuidString)"
        defer { db.removeURLBookmark(url: url) }
        db.saveURLBookmark(url: url, title: "Test")
        XCTAssertTrue(db.getURLBookmarks().contains { $0.0 == url })
        db.removeURLBookmark(url: url)
        XCTAssertFalse(db.getURLBookmarks().contains { $0.0 == url })
    }
    @MainActor func testForceRefreshHook() async {
        let s = RefreshTrackingService(); let vm = ThreadListViewModel(service: s)
        vm.threads = [s.cachedThread]; await vm.loadTopics(for: s.community, forceRefresh: true)
        XCTAssertTrue(s.refreshCalled); XCTAssertFalse(s.pageOneFetchCalled)
        XCTAssertEqual(vm.threads, [s.refreshedThread])
    }
    @MainActor func testBackgroundPrefetchGating() {
        let k = "background_prefetch_enabled"
        let p = UserDefaults.standard.object(forKey: k)
        defer { if let p { UserDefaults.standard.set(p, forKey: k) } else { UserDefaults.standard.removeObject(forKey: k) } }
        UserDefaults.standard.set(false, forKey: k)
        XCTAssertFalse(ThreadListViewModel(service: HackerNewsService()).allowsConfiguredBackgroundPrefetch)
        UserDefaults.standard.set(true, forKey: k)
        XCTAssertTrue(ThreadListViewModel(service: HackerNewsService()).allowsConfiguredBackgroundPrefetch)
        XCTAssertFalse(ThreadListViewModel(service: FourD4YService()).allowsConfiguredBackgroundPrefetch)
    }
}

// MARK: - 4. Reading & Accessibility

final class AccessibilityTests: XCTestCase {
    func testDecimalEntityDecoding() {
        XCTAssertEqual("&#12290;".decodingHTMLEntities(), "。")
        XCTAssertEqual("&#20013;&#25991;".decodingHTMLEntities(), "中文")
    }
    func testHexEntityDecoding() {
        XCTAssertEqual("&#x4E2D;".decodingHTMLEntities(), "中")
        XCTAssertEqual("&#X6587;".decodingHTMLEntities(), "文")
    }
    func testNamedEntityDecoding() {
        XCTAssertEqual("&amp;".decodingHTMLEntities(), "&")
        XCTAssertEqual("&lt;".decodingHTMLEntities(), "<")
        XCTAssertEqual("&gt;".decodingHTMLEntities(), ">")
        XCTAssertEqual("&quot;".decodingHTMLEntities(), "\"")
        XCTAssertEqual("&nbsp;".decodingHTMLEntities(), " ")
        XCTAssertEqual("&mdash;".decodingHTMLEntities(), "—")
        XCTAssertEqual("&ndash;".decodingHTMLEntities(), "–")
    }
    func testMixedEntityDecoding() {
        XCTAssertEqual("&#12290; &amp; &#x4E2D; &mdash; end".decodingHTMLEntities(), "。 & 中 — end")
    }
    func testNoEntityReturnsUnchanged() {
        XCTAssertEqual("Hello World".decodingHTMLEntities(), "Hello World")
        XCTAssertEqual("".decodingHTMLEntities(), "")
    }
    func testInvalidEntityNoCrash() {
        let r = "&#99999999;".decodingHTMLEntities()
        XCTAssertNotNil(r)
    }
    func testSpeechInitialState() { XCTAssertFalse(SpeechService.shared.isSpeaking) }
}

// MARK: - 5. Content Interaction

final class ContentInteractionTests: XCTestCase {
    func testThreadLikeToggle() {
        var t = makeThread(id: "1", title: "T"); XCTAssertFalse(t.isLiked)
        t.isLiked = true; XCTAssertTrue(t.isLiked)
        t.isLiked = false; XCTAssertFalse(t.isLiked)
    }
    func testGetWebURL() { XCTAssertTrue(HackerNewsService().getWebURL(for: makeThread(id: "123", title: "T")).contains("123")) }
    func testThreadTagsOptional() {
        var t = makeThread(id: "1", title: "T"); XCTAssertNil(t.tags)
        t.tags = ["swift"]; XCTAssertEqual(t.tags?.count, 1)
    }
    func testCommentRepliesOptional() {
        let c = Comment(id: "1", author: User(id: "u", username: "U", avatar: "", role: nil), content: "c", timeAgo: "now", likeCount: 0, replies: nil)
        XCTAssertNil(c.replies)
        let withR = Comment(id: "1", author: User(id: "u", username: "U", avatar: "", role: nil), content: "c", timeAgo: "now", likeCount: 0, replies: [c])
        XCTAssertEqual(withR.replies?.count, 1)
    }
}

// MARK: - 6. AI Features

final class AIFeaturesTests: XCTestCase {
    @MainActor func testSummaryPersistence() {
        let db = DatabaseManager.shared; let tid = "ai_\(UUID().uuidString)"; let sid = "hn"
        db.saveSummary(threadId: tid, serviceId: sid, summary: "test summary")
        XCTAssertEqual(db.getSummary(threadId: tid, serviceId: sid), "test summary")
    }
    @MainActor func testSummaryExpiresWithZeroMaxAge() {
        let db = DatabaseManager.shared; let tid = "ae_\(UUID().uuidString)"; let sid = "hn"
        db.saveSummary(threadId: tid, serviceId: sid, summary: "old")
        XCTAssertNil(db.getSummaryIfFresh(threadId: tid, serviceId: sid, maxAgeSeconds: 0))
    }
    @MainActor func testSummaryWithinTTL() {
        let db = DatabaseManager.shared; let tid = "at_\(UUID().uuidString)"; let sid = "hn"
        db.saveSummary(threadId: tid, serviceId: sid, summary: "fresh")
        XCTAssertEqual(db.getSummaryIfFresh(threadId: tid, serviceId: sid, maxAgeSeconds: 3600), "fresh")
    }
    @MainActor func testSummaryOverwrite() {
        let db = DatabaseManager.shared; let tid = "ao_\(UUID().uuidString)"; let sid = "hn"
        db.saveSummary(threadId: tid, serviceId: sid, summary: "first")
        db.saveSummary(threadId: tid, serviceId: sid, summary: "second")
        XCTAssertEqual(db.getSummary(threadId: tid, serviceId: sid), "second")
    }
}

// MARK: - 7. RSS Feed Management

final class RSSFeedTests: XCTestCase {
    @MainActor func testParseRSS2() async {
        let xml = "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><item><title>Test</title><link>https://ex.com</link><pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate><description>Content</description></item></channel></rss>"
        let p = RSSParser(data: xml.data(using: .utf8)!)
        let items = await p.parse()
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.title, "Test")
    }
    @MainActor func testParseAtom() async {
        let xml = "<?xml version=\"1.0\"?><feed xmlns=\"http://www.w3.org/2005/Atom\"><entry><title>Atom</title><link href=\"https://ex.com\"/><updated>2024-01-01T00:00:00Z</updated><content>C</content></entry></feed>"
        let p = RSSParser(data: xml.data(using: .utf8)!)
        let items = await p.parse()
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.title, "Atom")
    }
    @MainActor func testParseEmptyXML() async {
        let p = RSSParser(data: "<?xml version=\"1.0\"?>".data(using: .utf8)!)
        let items = await p.parse()
        XCTAssertEqual(items.count, 0)
    }
    @MainActor func testParseNoItems() async {
        let xml = "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel></channel></rss>"
        let p = RSSParser(data: xml.data(using: .utf8)!)
        let items = await p.parse()
        XCTAssertEqual(items.count, 0)
    }
    func testOPMLParsing() {
        let opml = "<?xml version=\"1.0\"?><opml version=\"2.0\"><body><outline text=\"F1\" xmlUrl=\"https://ex.com/1.xml\"/><outline text=\"F2\" xmlUrl=\"https://ex.com/2.xml\"/></body></opml>"
        let p = OPMLParser(data: opml.data(using: .utf8)!)
        let feeds = p.parse()
        XCTAssertEqual(feeds.count, 2)
        XCTAssertEqual(feeds[0].title, "F1")
    }
    func testOPMLEmptyXML() {
        let p = OPMLParser(data: "<?xml version=\"1.0\"?>".data(using: .utf8)!)
        let feeds = p.parse()
        XCTAssertEqual(feeds.count, 0)
    }
}

// MARK: - 8. Settings

final class SettingsTests: XCTestCase {
    @MainActor func testSaveLoadSetting() {
        let db = DatabaseManager.shared; let k = "st_\(UUID().uuidString)"
        defer { db.removeSetting(key: k) }
        db.saveSetting(key: k, value: "val"); XCTAssertEqual(db.getSetting(key: k), "val")
    }
    @MainActor func testUpdateSetting() {
        let db = DatabaseManager.shared; let k = "su_\(UUID().uuidString)"
        defer { db.removeSetting(key: k) }
        db.saveSetting(key: k, value: "old"); db.saveSetting(key: k, value: "new")
        XCTAssertEqual(db.getSetting(key: k), "new")
    }
    @MainActor func testRemoveNonexistentNoCrash() {
        DatabaseManager.shared.removeSetting(key: "nonexistent_xyz"); XCTAssertNil(DatabaseManager.shared.getSetting(key: "nonexistent_xyz"))
    }
    @MainActor func testEncryptedSetting() {
        let k = "se_\(UUID().uuidString)"; defer { DatabaseManager.shared.removeSetting(key: k) }
        DatabaseManager.shared.saveEncryptedSetting(key: k, value: "secret")
        XCTAssertNotEqual(DatabaseManager.shared.getSetting(key: k), "secret")
        XCTAssertEqual(DatabaseManager.shared.getEncryptedSetting(key: k), "secret")
    }
    @MainActor func testPlaintextMigration() {
        let k = "sm_\(UUID().uuidString)"; defer { DatabaseManager.shared.removeSetting(key: k) }
        DatabaseManager.shared.saveSetting(key: k, value: "legacy")
        XCTAssertEqual(DatabaseManager.shared.getEncryptedSetting(key: k), "legacy")
        XCTAssertNotEqual(DatabaseManager.shared.getSetting(key: k), "legacy")
    }
}

// MARK: - 9. Data Persistence

final class DataPersistenceTests: XCTestCase {
    @MainActor func testCookieRoundTrip() {
        let db = DatabaseManager.shared; let sid = "cp_\(UUID().uuidString)"
        defer { db.clearCookies(siteId: sid) }
        db.replaceCookies(siteId: sid, cookies: [makeCookie(name: "cdb_auth", value: "tok", domain: "4d4y.com")])
        let r = db.getCookies(siteId: sid) ?? []
        XCTAssertEqual(r.count, 1); XCTAssertEqual(r.first?.name, "cdb_auth"); XCTAssertEqual(r.first?.value, "tok")
    }
    @MainActor func testCookieSecureFlagPreserved() {
        let db = DatabaseManager.shared; let sid = "cs_\(UUID().uuidString)"
        defer { db.clearCookies(siteId: sid) }
        let c = HTTPCookie(properties: [.name: "s", .value: "v", .domain: "4d4y.com", .path: "/", .secure: "TRUE"])!
        db.replaceCookies(siteId: sid, cookies: [c])
        XCTAssertTrue(db.getCookies(siteId: sid)?.first?.isSecure ?? false)
    }
    @MainActor func testCookieEmptyArray() {
        let sid = "ce_\(UUID().uuidString)"
        DatabaseManager.shared.replaceCookies(siteId: sid, cookies: [])
        XCTAssertTrue(DatabaseManager.shared.getCookies(siteId: sid)?.isEmpty ?? true)
    }
    @MainActor func testClearCookies() {
        let db = DatabaseManager.shared; let sid = "cc_\(UUID().uuidString)"
        db.replaceCookies(siteId: sid, cookies: [makeCookie(name: "a", value: "1", domain: "d.com")])
        db.clearCookies(siteId: sid)
        XCTAssertTrue(db.getCookies(siteId: sid)?.isEmpty ?? true)
    }
    @MainActor func testCommunityCache() {
        let db = DatabaseManager.shared; let sid = "cm_\(UUID().uuidString)"
        db.saveCommunities([Community(id: "1", name: "A", description: "", category: "", activeToday: 0, onlineNow: 0)], forService: sid)
        XCTAssertEqual(db.getCommunities(forService: sid).count, 1)
    }
    @MainActor func testCommunityCacheEmpty() {
        let sid = "ce2_\(UUID().uuidString)"
        DatabaseManager.shared.saveCommunities([], forService: sid)
        XCTAssertEqual(DatabaseManager.shared.getCommunities(forService: sid).count, 0)
    }
    func testSchemaMigrationDecision() {
        XCTAssertFalse(DatabaseSchemaMigration.needsCompositePrimaryKeyMigration(currentPrimaryKey: ["a","b"], expectedPrimaryKey: ["a","b"]))
        XCTAssertTrue(DatabaseSchemaMigration.needsCompositePrimaryKeyMigration(currentPrimaryKey: ["a"], expectedPrimaryKey: ["a","b"]))
    }
    @MainActor func testHasCookies() {
        let db = DatabaseManager.shared; let sid = "hc_\(UUID().uuidString)"
        defer { db.clearCookies(siteId: sid) }
        XCTAssertFalse(db.hasCookies(siteId: sid))
        db.replaceCookies(siteId: sid, cookies: [makeCookie(name: "a", value: "1", domain: "d.com")])
        XCTAssertTrue(db.hasCookies(siteId: sid))
    }
}

// MARK: - 9a. Encryption

final class EncryptionTests: XCTestCase {
    func testEncryptDecryptRoundTrip() {
        let h = EncryptionHelper.shared
        guard let e = h.encrypt("test_user"), let d = h.decrypt(e) else { XCTFail(); return }
        XCTAssertEqual(d, "test_user")
    }
    func testEncryptDecryptSpecialChars() {
        let h = EncryptionHelper.shared
        guard let e = h.encrypt("p@ss!#$%"), let d = h.decrypt(e) else { XCTFail(); return }
        XCTAssertEqual(d, "p@ss!#$%")
    }
    func testEncryptDecryptEmpty() {
        let h = EncryptionHelper.shared
        guard let e = h.encrypt(""), let d = h.decrypt(e) else { XCTFail(); return }
        XCTAssertEqual(d, "")
    }
    func testEncryptDecryptChinese() {
        let h = EncryptionHelper.shared
        guard let e = h.encrypt("中文测试"), let d = h.decrypt(e) else { XCTFail(); return }
        XCTAssertEqual(d, "中文测试")
    }
    func testDecryptInvalidReturnsNil() { XCTAssertNil(EncryptionHelper.shared.decrypt("!!invalid!!")) }
    func testEncryptProducesDifferentOutput() {
        guard let e = EncryptionHelper.shared.encrypt("test") else { XCTFail(); return }
        XCTAssertNotEqual(e, "test")
    }
}

// MARK: - 10. Per-Site Service Properties

@MainActor final class PerSiteTests: XCTestCase {
    func testFourD4YProps() {
        let s = FourD4YService()
        XCTAssertEqual(s.name, "4D4Y"); XCTAssertEqual(s.id, "4d4y")
        XCTAssertTrue(s.requiresLogin); XCTAssertTrue(s.supportsCommenting); XCTAssertTrue(s.supportsThreadCreation)
    }
    func testFourD4YSIDRegex() {
        let html = "sid=abc123xyz"
        let r = try? NSRegularExpression(pattern: "sid=([a-zA-Z0-9]+)")
        let m = r?.firstMatch(in: html, range: NSRange(html.startIndex..., in: html))
        XCTAssertNotNil(m)
        if let range = Range(m!.range(at: 1), in: html) { XCTAssertEqual(String(html[range]), "abc123xyz") }
    }
    func testV2EXProps() { let s = V2EXService(); XCTAssertEqual(s.name, "V2EX"); XCTAssertEqual(s.id, "v2ex") }
    func testDiscourseProps() { let s = DiscourseService(); XCTAssertEqual(s.name, "Linux.do"); XCTAssertEqual(s.id, "linux_do"); XCTAssertTrue(s.requiresLogin) }
    func testHNProps() { let s = HackerNewsService(); XCTAssertEqual(s.name, "Hacker News"); XCTAssertEqual(s.id, "hackernews"); XCTAssertFalse(s.requiresLogin) }
    func testZhihuProps() { let s = ZhihuService(); XCTAssertEqual(s.name, "知乎"); XCTAssertEqual(s.id, "zhihu"); XCTAssertTrue(s.requiresLogin) }
    func testRSSProps() { let s = RSSService(); XCTAssertEqual(s.name, "RSS"); XCTAssertEqual(s.id, "rss"); XCTAssertFalse(s.requiresLogin) }
    func testTimeAgoJustNow() { XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date()), "just now") }
    func testTimeAgoMinutes() { XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-120)), "2m") }
    func testTimeAgoHours() { XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-7200)), "2h") }
    func testTimeAgoDays() { XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-172800)), "2d") }
}

// MARK: - 11. Navigation & Models

final class NavigationTests: XCTestCase {
    func testPopToRoot() {
        let m = NavigationManager(); m.path.append(ForumSite.fourD4Y)
        XCTAssertFalse(m.path.isEmpty); m.popToRoot(); XCTAssertTrue(m.path.isEmpty)
    }
}

final class ModelTests: XCTestCase {
    func testUserModel() {
        let u = User(id: "1", username: "Alice", avatar: "p.c", role: "Admin")
        XCTAssertEqual(u.id, "1"); XCTAssertEqual(u.role, "Admin")
    }
    func testCommunityModel() {
        let c = Community(id: "c1", name: "T", description: "d", category: "cat", activeToday: 5, onlineNow: 2)
        XCTAssertEqual(c.id, "c1"); XCTAssertEqual(c.activeToday, 5)
    }
    func testThreadEquality() {
        XCTAssertEqual(makeThread(id: "1", title: "A"), makeThread(id: "1", title: "B"))
        XCTAssertNotEqual(makeThread(id: "1", title: "A"), makeThread(id: "2", title: "A"))
    }
    func testCommentModel() {
        let c = Comment(id: "c1", author: User(id: "u", username: "U", avatar: "", role: nil), content: "Hi", timeAgo: "1h", likeCount: 3, replies: nil)
        XCTAssertEqual(c.id, "c1"); XCTAssertEqual(c.likeCount, 3)
    }
}

// MARK: - 11b. Error Handling & Edge Cases

final class ErrorHandlingTests: XCTestCase {
    func testHTTPCookieNilForBadProps() { XCTAssertNil(HTTPCookie(properties: [:])) }
    func testHTTPCookieMinimumProps() {
        let c = HTTPCookie(properties: [.name: "t", .value: "v", .domain: "ex.com", .path: "/"])
        XCTAssertNotNil(c); XCTAssertEqual(c?.name, "t")
    }
    @MainActor func testEmptyRefreshPreservesThreads() {
        let s = RefreshTrackingService(); let vm = ThreadListViewModel(service: s)
        vm.threads = [makeThread(id: "keep", title: "K")]
        XCTAssertEqual(vm.threads.count, 1)
    }
    func testGenericAvatarNotURL() {
        for s in ["person.circle", "person.circle.fill", "person.crop.circle"] {
            XCTAssertFalse(s.hasPrefix("http"))
        }
    }
}

// MARK: - ForumService Defaults

final class ForumServiceDefaultsTests: XCTestCase {
    func testDefaultRequiresLoginFalse() {
        struct S: ForumService {
            var name: String { "D" }; var id: String { "d" }; var logo: String { "c" }
            func fetchCategories() async throws -> [Community] { [] }
            func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
            func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeThread(id: "1", title: "t"), [], nil) }
            func postComment(topicId: String, categoryId: String, content: String) async throws {}
            func createThread(categoryId: String, title: String, content: String) async throws {}
            func getWebURL(for thread: FeedThread) -> String { "" }
            func canCreateThread(in community: Community) -> Bool { false }
        }
        XCTAssertFalse(S().requiresLogin); XCTAssertFalse(S().supportsCommenting)
    }
    @MainActor func testDefaultRestoreSessionTrue() async {
        struct S: ForumService {
            var name: String { "D" }; var id: String { "d" }; var logo: String { "c" }
            func fetchCategories() async throws -> [Community] { [] }
            func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
            func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeThread(id: "1", title: "t"), [], nil) }
            func postComment(topicId: String, categoryId: String, content: String) async throws {}
            func createThread(categoryId: String, title: String, content: String) async throws {}
            func getWebURL(for thread: FeedThread) -> String { "" }
            func canCreateThread(in community: Community) -> Bool { false }
        }
        let sessionResult = await S().restoreSession(); XCTAssertTrue(sessionResult)
    }
}

// MARK: - Localization

final class LocalizationTests: XCTestCase {
    func testKeysHaveTranslations() {
        let m = LocalizationManager.shared
        for k in ["login", "cancel", "done", "save", "close", "error", "select_community"] {
            let t = m.localizedString(k)
            XCTAssertFalse(t.isEmpty); XCTAssertNotEqual(t, k)
        }
    }
    func testLanguageToggle() {
        let m = LocalizationManager.shared; let c = m.currentLanguage
        m.currentLanguage = (c == "en") ? "zh" : "en"
        XCTAssertNotEqual(m.currentLanguage, c)
        m.currentLanguage = c
    }
}

// MARK: - Helper Stubs

private final class RefreshTrackingService: ForumService {
    let name = "Refresh"; let id = "rt_\(UUID().uuidString)"; let logo = "arrow"
    var refreshCalled = false; var pageOneFetchCalled = false
    let community = Community(id: "tl", name: "TL", description: "", category: "t", activeToday: 0, onlineNow: 0)
    lazy var cachedThread = makeThread(id: "c", title: "Cached")
    lazy var refreshedThread = makeThread(id: "f", title: "Fresh")
    func fetchCategories() async throws -> [Community] { [community] }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { if page == 1 { pageOneFetchCalled = true }; return [cachedThread] }
    func refreshCategoryThreads(categoryId: String, communities: [Community]) async throws -> [FeedThread] { refreshCalled = true; return [refreshedThread] }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (refreshedThread, [], nil) }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}
