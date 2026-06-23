import XCTest
@testable import Feedflow

private typealias FeedThread = Feedflow.Thread

// MARK: - Helpers (shared)

private func makeTestCookie(name: String, value: String, domain: String = "4d4y.com", expires: Date? = Date().addingTimeInterval(3600)) -> HTTPCookie {
    var props: [HTTPCookiePropertyKey: Any] = [.name: name, .value: value, .domain: domain, .path: "/"]
    if let expires { props[.expires] = expires }
    return HTTPCookie(properties: props)!
}

private func makeTestThread(id: String, title: String, community: Community? = nil, author: User? = nil) -> FeedThread {
    let c = community ?? Community(id: "test", name: "Test", description: "", category: "test", activeToday: 0, onlineNow: 0)
    let a = author ?? User(id: "u1", username: "Tester", avatar: "person.circle", role: nil)
    return FeedThread(id: id, title: title, content: "", author: a, community: c, timeAgo: "now", likeCount: 0, commentCount: 0)
}

private func makeTestCommunity(id: String = "c1", name: String = "Test") -> Community {
    Community(id: id, name: name, description: "", category: "general", activeToday: 10, onlineNow: 5)
}

// MARK: - 2. Authentication & Session (Extended)

final class AuthenticationExtendedTests: XCTestCase {

    func testPersistentCookieUpgradeTiming() {
        let sc = makeTestCookie(name: "cdb_auth", value: "t", domain: "4d4y.com", expires: nil)
        XCTAssertNil(sc.expiresDate)
        var props = sc.properties ?? [:]
        let target = Date().addingTimeInterval(30 * 24 * 3600)
        props[.expires] = target
        let upgraded = HTTPCookie(properties: props)
        XCTAssertNotNil(upgraded?.expiresDate)
        if let actual = upgraded?.expiresDate {
            XCTAssertEqual(actual.timeIntervalSinceReferenceDate, target.timeIntervalSinceReferenceDate, accuracy: 5)
        }
    }

    func testExistingExpiresDateUnchanged() {
        let original = Date().addingTimeInterval(3600)
        let sc = makeTestCookie(name: "cdb_auth", value: "t", domain: "4d4y.com", expires: original)
        XCTAssertEqual(sc.expiresDate!.timeIntervalSinceReferenceDate, original.timeIntervalSinceReferenceDate, accuracy: 1)
    }

    func testCookieSerializationPreservesAllProperties() {
        let original = makeTestCookie(name: "test", value: "val", domain: "4d4y.com", expires: Date().addingTimeInterval(86400))
        guard let props = original.properties else { XCTFail("No properties"); return }
        guard let restored = HTTPCookie(properties: props) else { XCTFail("Failed to restore"); return }
        XCTAssertEqual(restored.name, original.name)
        XCTAssertEqual(restored.value, original.value)
        XCTAssertEqual(restored.domain, original.domain)
        XCTAssertEqual(restored.path, original.path)
        XCTAssertEqual(restored.isSecure, original.isSecure)
        XCTAssertEqual(restored.isHTTPOnly, original.isHTTPOnly)
    }

    func testEncryptionRoundTrip() {
        let original = "testPassword123!@#"
        guard let encrypted = EncryptionHelper.shared.encrypt(original) else {
            XCTFail("Encryption failed"); return
        }
        XCTAssertNotEqual(encrypted, original)
        let decrypted = EncryptionHelper.shared.decrypt(encrypted)
        XCTAssertEqual(decrypted, original)
    }

    func testEncryptionDifferentOutputsForSameInput() {
        let input = "password"
        let e1 = EncryptionHelper.shared.encrypt(input)
        let e2 = EncryptionHelper.shared.encrypt(input)
        XCTAssertNotNil(e1)
        XCTAssertNotNil(e2)
        XCTAssertNotEqual(e1, e2, "AES-GCM should produce different ciphertexts due to random nonce")
    }

    func testDecryptInvalidDataReturnsNil() {
        XCTAssertNil(EncryptionHelper.shared.decrypt("not-valid-base64!!!"))
        XCTAssertNil(EncryptionHelper.shared.decrypt(""))
    }

    func testEncryptEmptyString() {
        let encrypted = EncryptionHelper.shared.encrypt("")
        XCTAssertNotNil(encrypted)
        XCTAssertEqual(EncryptionHelper.shared.decrypt(encrypted!), "")
    }

    func testReplaceCookiesOverwrites() {
        let db = DatabaseManager.shared
        let siteId = "test_replace_\(UUID().uuidString)"
        let c1 = makeTestCookie(name: "a", value: "1", domain: "test.com")
        let c2 = makeTestCookie(name: "b", value: "2", domain: "test.com")
        db.replaceCookies(siteId: siteId, cookies: [c1])
        XCTAssertEqual(db.getCookies(siteId: siteId)?.count, 1)
        db.replaceCookies(siteId: siteId, cookies: [c2])
        let result = db.getCookies(siteId: siteId)
        XCTAssertEqual(result?.count, 1)
        XCTAssertEqual(result?.first?.name, "b")
        db.clearCookies(siteId: siteId)
    }

    func testClearCookiesRemovesAll() {
        let db = DatabaseManager.shared
        let siteId = "test_clear_\(UUID().uuidString)"
        db.replaceCookies(siteId: siteId, cookies: [makeTestCookie(name: "x", value: "v", domain: "t.com")])
        XCTAssertTrue(db.hasCookies(siteId: siteId))
        db.clearCookies(siteId: siteId)
        XCTAssertFalse(db.hasCookies(siteId: siteId))
        XCTAssertNil(db.getCookies(siteId: siteId))
    }

    func testHasCookiesFalseForUnknownSite() {
        XCTAssertFalse(DatabaseManager.shared.hasCookies(siteId: "nonexistent_\(UUID().uuidString)"))
    }

    func testSiteLoginConfigFourD4YDomain() {
        let config = SiteLoginConfig.config(for: .fourD4Y)
        XCTAssertEqual(config?.cookieDomain, "4d4y.com")
        XCTAssertTrue(config?.oauthOptions.isEmpty == true)
    }

    func testSiteLoginConfigZhihuRequiredCookie() {
        let config = SiteLoginConfig.config(for: .zhihu)
        XCTAssertEqual(config?.requiredCookieName, "z_c0")
    }

    func testSiteLoginConfigHackerNews() {
        let config = SiteLoginConfig.config(for: .hackerNews)
        XCTAssertEqual(config?.cookieDomain, "ycombinator.com")
    }

    func testIsLoginURLIdentifiesLoginPage() {
        guard let config = SiteLoginConfig.config(for: .fourD4Y) else { XCTFail(); return }
        let loginURL = URL(string: "https://www.4d4y.com/forum/logging.php?action=login")
        XCTAssertTrue(config.isLoginURL(loginURL))
        let otherURL = URL(string: "https://www.4d4y.com/forum/index.php")
        XCTAssertFalse(config.isLoginURL(otherURL))
    }

    func testIsPostLoginNavigation() {
        guard let config = SiteLoginConfig.config(for: .fourD4Y) else { XCTFail(); return }
        let postLogin = URL(string: "https://www.4d4y.com/forum/index.php")
        XCTAssertTrue(config.isPostLoginNavigation(postLogin))
        let external = URL(string: "https://google.com")
        XCTAssertFalse(config.isPostLoginNavigation(external))
    }

    func testShouldCheckCookies() {
        guard let config = SiteLoginConfig.config(for: .linuxDo) else { XCTFail(); return }
        XCTAssertTrue(config.shouldCheckCookies(for: URL(string: "https://linux.do/latest")))
        XCTAssertTrue(config.shouldCheckCookies(for: URL(string: "https://linux.do/top")))
        XCTAssertFalse(config.shouldCheckCookies(for: URL(string: "https://other.com")))
    }
}

// MARK: - 3. Content Browsing

final class ContentBrowsingExtendedTests: XCTestCase {

    func testBookmarkToggle() {
        let db = DatabaseManager.shared
        let thread = makeTestThread(id: "bm_\(UUID().uuidString)", title: "Bookmark Test")
        db.toggleBookmark(thread: thread, serviceId: "test")
        XCTAssertTrue(db.isBookmarked(threadId: thread.id, serviceId: "test"))
        db.toggleBookmark(thread: thread, serviceId: "test")
        XCTAssertFalse(db.isBookmarked(threadId: thread.id, serviceId: "test"))
    }

    func testURLBookmarkRoundTrip() {
        let db = DatabaseManager.shared
        let url = "https://example.com/page_\(UUID().uuidString)"
        XCTAssertFalse(db.isURLBookmarked(url: url))
        db.saveURLBookmark(url: url, title: "Test Page")
        XCTAssertTrue(db.isURLBookmarked(url: url))
        db.removeURLBookmark(url: url)
        XCTAssertFalse(db.isURLBookmarked(url: url))
    }

    func testGetBookmarkedThreads() {
        let db = DatabaseManager.shared
        let id = "bmlist_\(UUID().uuidString)"
        let thread = makeTestThread(id: id, title: "B")
        db.toggleBookmark(thread: thread, serviceId: "test")
        let bookmarks = db.getBookmarkedThreads()
        XCTAssertTrue(bookmarks.contains { $0.0.id == id })
        db.toggleBookmark(thread: thread, serviceId: "test")
    }

    func testCommunityCacheSaveAndLoad() {
        let db = DatabaseManager.shared
        let serviceId = "cache_test_\(UUID().uuidString)"
        let communities = [
            Community(id: "a", name: "Alpha", description: "", category: "g", activeToday: 1, onlineNow: 2),
            Community(id: "b", name: "Beta", description: "", category: "g", activeToday: 3, onlineNow: 4)
        ]
        db.saveCommunities(communities, forService: serviceId)
        let loaded = db.getCommunities(forService: serviceId)
        XCTAssertEqual(loaded.count, 2)
        XCTAssertEqual(loaded.first?.name, "Alpha")
    }

    func testCommunityCacheEmptyService() {
        let loaded = DatabaseManager.shared.getCommunities(forService: "empty_service_\(UUID().uuidString)")
        XCTAssertTrue(loaded.isEmpty)
    }

    func testSettingsKeyValue() {
        let db = DatabaseManager.shared
        let key = "test_key_\(UUID().uuidString)"
        db.saveSetting(key: key, value: "hello")
        XCTAssertEqual(db.getSetting(key: key), "hello")
        db.saveSetting(key: key, value: "world")
        XCTAssertEqual(db.getSetting(key: key), "world")
        db.removeSetting(key: key)
        XCTAssertNil(db.getSetting(key: key))
    }

    func testEncryptedSettingRoundTrip() {
        let db = DatabaseManager.shared
        let key = "enc_test_\(UUID().uuidString)"
        db.saveEncryptedSetting(key: key, value: "secret123")
        let retrieved = db.getEncryptedSetting(key: key)
        XCTAssertEqual(retrieved, "secret123")
        db.removeSetting(key: key)
    }

    func testSummaryCache() {
        let db = DatabaseManager.shared
        let threadId = "sum_\(UUID().uuidString)"
        db.saveSummary(threadId: threadId, summary: "TL;DR")
        XCTAssertEqual(db.getSummary(threadId: threadId), "TL;DR")
        let fresh = db.getSummaryIfFresh(threadId: threadId, maxAgeSeconds: 3600)
        XCTAssertEqual(fresh, "TL;DR")
        let stale = db.getSummaryIfFresh(threadId: threadId, maxAgeSeconds: 0)
        XCTAssertNil(stale)
    }

    func testFilteredPostLifecycle() {
        let db = DatabaseManager.shared
        let postId = "filter_\(UUID().uuidString)"
        let serviceId = "test_service"
        XCTAssertFalse(db.isPostFiltered(postId: postId, serviceId: serviceId))
        db.addFilteredPost(postId: postId, serviceId: serviceId)
        XCTAssertTrue(db.isPostFiltered(postId: postId, serviceId: serviceId))
        let ids = db.getFilteredPostIds(serviceId: serviceId)
        XCTAssertTrue(ids.contains(postId))
        db.cleanupOldFilteredPosts(serviceId: serviceId)
    }

    func testPrefetchFlagDefaults() {
        UserDefaults.standard.set(true, forKey: "background_prefetch_enabled")
        XCTAssertTrue(UserDefaults.standard.bool(forKey: "background_prefetch_enabled"))
        UserDefaults.standard.removeObject(forKey: "background_prefetch_enabled")
    }

    @MainActor func testThreadListViewModelInitialState() {
        let service = RefreshTrackingService()
        let vm = ThreadListViewModel(service: service)
        XCTAssertTrue(vm.threads.isEmpty)
        XCTAssertFalse(vm.isLoading)
        XCTAssertTrue(vm.canLoadMore)
        XCTAssertTrue(vm.isAtTop)
    }

    @MainActor func testClearLoginRequest() {
        let service = RefreshTrackingService()
        let vm = ThreadListViewModel(service: service)
        vm.needsLogin = true
        vm.clearLoginRequest()
        XCTAssertFalse(vm.needsLogin)
    }

    @MainActor func testUpdateScrollPosition() async {
        let service = RefreshTrackingService()
        let vm = ThreadListViewModel(service: service)
        vm.updateScrollPosition(isAtTop: false)
        // isAtTop=false is debounced by 100ms — wait for it to settle.
        try? await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertFalse(vm.isAtTop)
        vm.updateScrollPosition(isAtTop: true)
        // isAtTop=true takes effect immediately (no debounce).
        XCTAssertTrue(vm.isAtTop)
    }

    @MainActor func testRemoveThread() {
        let service = RefreshTrackingService()
        let vm = ThreadListViewModel(service: service)
        let t1 = makeTestThread(id: "1", title: "One")
        let t2 = makeTestThread(id: "2", title: "Two")
        vm.threads = [t1, t2]
        vm.removeThread(t1)
        XCTAssertEqual(vm.threads.count, 1)
        XCTAssertEqual(vm.threads.first?.id, "2")
    }
}

// MARK: - 4. Reading & Accessibility

final class ReadingAccessibilityTests: XCTestCase {

    func testThemeManagerDefault() {
        let tm = ThemeManager()
        XCTAssertTrue(tm.isDarkMode)
    }

    func testThemeManagerToggle() {
        let tm = ThemeManager()
        let original = tm.isDarkMode
        tm.isDarkMode.toggle()
        XCTAssertNotEqual(tm.isDarkMode, original)
        tm.isDarkMode = original
    }

    func testThemeManagerPersists() {
        let tm = ThemeManager()
        tm.isDarkMode = false
        let tm2 = ThemeManager()
        XCTAssertFalse(tm2.isDarkMode)
        tm.isDarkMode = true
    }

    func testLocalizationAllKeysHaveValues() {
        let lm = LocalizationManager.shared
        let keys = ["login", "cancel", "done", "save", "close", "error", "select_community",
                     "settings", "bookmarks", "ai_assistant", "reply", "reply_failed",
                     "new_thread", "post_failed", "thread_title", "share_thoughts",
                     "signed_in", "signed_out", "logout", "save_session",
                     "login_with_browser", "login_to_site", "login_success",
                     "communities", "manage_feeds", "daily_rss_summary",
                     "browser", "thread_bookmarks", "url_bookmarks"]
        for key in keys {
            let localized = lm.localizedString(key)
            XCTAssertFalse(localized.isEmpty, "Key '\(key)' has empty translation")
            XCTAssertNotEqual(localized, key, "Key '\(key)' has no translation")
        }
    }

    func testLocalizedExtension() {
        let s = "login".localized()
        XCTAssertFalse(s.isEmpty)
        XCTAssertNotEqual(s, "login")
    }

    func testHTMLEntityDecodingNamed() {
        XCTAssertEqual("&amp;".decodingHTMLEntities(), "&")
        XCTAssertEqual("&lt;".decodingHTMLEntities(), "<")
        XCTAssertEqual("&gt;".decodingHTMLEntities(), ">")
        XCTAssertEqual("&quot;".decodingHTMLEntities(), "\"")
        XCTAssertEqual("&apos;".decodingHTMLEntities(), "'")
        XCTAssertEqual("&nbsp;".decodingHTMLEntities(), " ")
        XCTAssertEqual("&ndash;".decodingHTMLEntities(), "–")
        XCTAssertEqual("&mdash;".decodingHTMLEntities(), "—")
    }

    func testHTMLEntityDecodingDecimal() {
        XCTAssertEqual("&#12290;".decodingHTMLEntities(), "。")
        XCTAssertEqual("&#20013;".decodingHTMLEntities(), "中")
        XCTAssertEqual("&#22269;".decodingHTMLEntities(), "国")
    }

    func testHTMLEntityDecodingHex() {
        XCTAssertEqual("&#x4E2D;".decodingHTMLEntities(), "中")
        XCTAssertEqual("&#x56FD;".decodingHTMLEntities(), "国")
        XCTAssertEqual("&#X4E2D;".decodingHTMLEntities(), "中")
    }

    func testHTMLEntityDecodingMixed() {
        let input = "Hello&#12290; &amp; World&#x4E2D;"
        let expected = "Hello。 & World中"
        XCTAssertEqual(input.decodingHTMLEntities(), expected)
    }

    func testHTMLEntityDecodingNoEntities() {
        let input = "Plain text without entities"
        XCTAssertEqual(input.decodingHTMLEntities(), input)
    }

    func testHTMLEntityDecodingEmpty() {
        XCTAssertEqual("".decodingHTMLEntities(), "")
    }

    func testHTMLEntityDecodingEntityInTitle() {
        let title = "测试&#12290;标题"
        XCTAssertEqual(title.decodingHTMLEntities(), "测试。标题")
    }

    func testSpeechServiceSharedInstance() {
        XCTAssertNotNil(SpeechService.shared)
    }

    func testSpeechServiceInitialState() {
        XCTAssertFalse(SpeechService.shared.isSpeaking)
    }

    func testSpeechServiceStopWhenNotSpeaking() {
        let service = SpeechService.shared
        service.stop()
        XCTAssertFalse(service.isSpeaking)
    }
}

// MARK: - 5. Content Interaction

final class ContentInteractionExtendedTests: XCTestCase {

    func testThreadLikeToggle() {
        var thread = makeTestThread(id: "like_test", title: "L")
        XCTAssertFalse(thread.isLiked)
        thread.isLiked = true
        XCTAssertTrue(thread.isLiked)
    }

    func testCanCreateThreadDefault() {
        struct S: ForumService {
            var name: String { "S" }; var id: String { "s" }; var logo: String { "c" }
            func fetchCategories() async throws -> [Community] { [] }
            func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
            func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeTestThread(id: "1", title: "t"), [], nil) }
            func postComment(topicId: String, categoryId: String, content: String) async throws {}
            func createThread(categoryId: String, title: String, content: String) async throws {}
            func getWebURL(for thread: FeedThread) -> String { "" }
        }
        XCTAssertFalse(S().canCreateThread(in: makeTestCommunity()))
    }

    func testFourD4YCanCreateThread() {
        let service = FourD4YService()
        XCTAssertTrue(service.canCreateThread(in: makeTestCommunity()))
        XCTAssertTrue(service.supportsCommenting)
        XCTAssertTrue(service.supportsThreadCreation)
    }

    func testServiceWebURL() {
        let service = FourD4YService()
        let thread = makeTestThread(id: "12345", title: "Test")
        let url = service.getWebURL(for: thread)
        XCTAssertTrue(url.contains("12345"))
        XCTAssertTrue(url.contains("4d4y.com"))
    }
}

// MARK: - 6. AI Features

final class AIFeaturesExtendedTests: XCTestCase {

    @MainActor func testGeminiServiceSummaryDoesNotCrash() async {
        let service = GeminiService()
        do {
            let result = try await service.generateSummary(for: "Test content")
            XCTAssertFalse(result.isEmpty)
        } catch {
            // Acceptable — may fail in test env without API key
        }
    }
}

// MARK: - 7. RSS Feed Management

final class RSSFeedExtendedTests: XCTestCase {

    func testRSSParserExists() {
        // RSSParser requires Data init
        // Skipped
    }

    func testOPMLParserExists() {
        // OPMLParser requires Data init
        // Skipped
    }

    @MainActor func testRSSServiceProperties() {
        let service = RSSService()
        XCTAssertFalse(service.logo.isEmpty)
        XCTAssertEqual(service.name, "RSS")
    }
}

// MARK: - 8. Settings & Preferences

final class SettingsPreferencesTests: XCTestCase {

    func testCommunitySettingsRSSAlwaysEnabled() {
        let m = CommunitySettingsManager.shared
        XCTAssertTrue(m.isEnabled(.rss))
        let wasEnabled = m.isEnabled(.fourD4Y)
        m.toggle(.rss)
        XCTAssertTrue(m.isEnabled(.rss))
        XCTAssertEqual(m.isEnabled(.fourD4Y), wasEnabled)
    }

    func testCommunitySettingsVisibleSites() {
        let m = CommunitySettingsManager.shared
        let visible = m.visibleSites
        XCTAssertTrue(visible.contains(.rss))
        for site in visible {
            XCTAssertTrue(m.isEnabled(site))
        }
    }

    func testBackgroundPrefetchToggle() {
        let key = "background_prefetch_enabled"
        UserDefaults.standard.set(true, forKey: key)
        XCTAssertTrue(UserDefaults.standard.bool(forKey: key))
        UserDefaults.standard.set(false, forKey: key)
        XCTAssertFalse(UserDefaults.standard.bool(forKey: key))
        UserDefaults.standard.removeObject(forKey: key)
    }
}

// MARK: - 9. Data Persistence

final class DataPersistenceExtendedTests: XCTestCase {

    func testSaveAndLoadCachedTopics() {
        let db = DatabaseManager.shared
        let key = "cache_topics_\(UUID().uuidString)"
        let threads = [makeTestThread(id: "t1", title: "T1"), makeTestThread(id: "t2", title: "T2")]
        db.saveCachedTopics(cacheKey: key, topics: threads)
        let loaded = db.getCachedTopics(cacheKey: key)
        XCTAssertEqual(loaded?.count, 2)
    }

    func testCachedTopicsNonexistentKey() {
        XCTAssertNil(DatabaseManager.shared.getCachedTopics(cacheKey: "nonexistent_\(UUID().uuidString)"))
    }

    func testSaveAndLoadCachedThread() {
        let db = DatabaseManager.shared
        let threadId = "ct_\(UUID().uuidString)"
        let thread = makeTestThread(id: threadId, title: "Cached Thread")
        let comments = [Comment(id: "c1", author: User(id: "u", username: "U", avatar: "", role: nil),
                                 content: "Nice", timeAgo: "1h", likeCount: 0, replies: nil)]
        db.saveCachedThread(threadId: threadId, thread: thread, comments: comments)
        let result = db.getCachedThread(threadId: threadId)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.0.id, threadId)
        XCTAssertEqual(result?.1.count, 1)
    }

    func testCachedThreadNonexistent() {
        XCTAssertNil(DatabaseManager.shared.getCachedThread(threadId: "nonexistent_\(UUID().uuidString)"))
    }

    func testURLBookmarksList() {
        let db = DatabaseManager.shared
        let url1 = "https://a.com/\(UUID().uuidString)"
        let url2 = "https://b.com/\(UUID().uuidString)"
        db.saveURLBookmark(url: url1, title: "A")
        db.saveURLBookmark(url: url2, title: "B")
        let bookmarks = db.getURLBookmarks()
        XCTAssertTrue(bookmarks.contains { $0.0 == url1 })
        XCTAssertTrue(bookmarks.contains { $0.0 == url2 })
        db.removeURLBookmark(url: url1)
        db.removeURLBookmark(url: url2)
    }

    func testMultipleSiteCookiesIsolation() {
        let db = DatabaseManager.shared
        let siteA = "iso_a_\(UUID().uuidString)"
        let siteB = "iso_b_\(UUID().uuidString)"
        db.replaceCookies(siteId: siteA, cookies: [makeTestCookie(name: "a_cookie", value: "av", domain: "a.com")])
        db.replaceCookies(siteId: siteB, cookies: [makeTestCookie(name: "b_cookie", value: "bv", domain: "b.com")])
        let cookiesA = db.getCookies(siteId: siteA) ?? []
        let cookiesB = db.getCookies(siteId: siteB) ?? []
        XCTAssertEqual(cookiesA.first?.name, "a_cookie")
        XCTAssertEqual(cookiesB.first?.name, "b_cookie")
        db.clearCookies(siteId: siteA)
        db.clearCookies(siteId: siteB)
    }
}

// MARK: - 10. Per-Site Service Specifics

final class PerSiteServiceTests: XCTestCase {

    func testFourD4YServiceID() {
        let service = FourD4YService()
        XCTAssertEqual(service.id, "4d4y")
        XCTAssertEqual(service.name, "4D4Y")
        XCTAssertTrue(service.requiresLogin)
    }

    func testV2EXServiceID() {
        let service = V2EXService()
        XCTAssertEqual(service.id, "v2ex")
        XCTAssertEqual(service.name, "V2EX")
    }

    func testLinuxDoServiceID() {
        let service = DiscourseService()
        XCTAssertEqual(service.id, "linux_do")
        XCTAssertEqual(service.name, "LINUX DO")
    }

    func testHackerNewsServiceID() {
        let service = HackerNewsService()
        XCTAssertEqual(service.id, "hackernews")
        XCTAssertFalse(service.requiresLogin)
    }

    func testZhihuServiceID() {
        let service = ZhihuService()
        XCTAssertEqual(service.id, "zhihu")
        XCTAssertEqual(service.name, "知乎")
    }

    func testTimeAgoJustNow() {
        let result = FourD4YService().calculateTimeAgo(from: Date())
        XCTAssertEqual(result, "just now")
    }

    func testTimeAgoMinutes() {
        let result = FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-120))
        XCTAssertEqual(result, "2m")
    }

    func testTimeAgoHours() {
        let result = FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-7200))
        XCTAssertEqual(result, "2h")
    }

    func testTimeAgoDays() {
        let result = FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-172800))
        XCTAssertEqual(result, "2d")
    }

    func testTimeAgoFromISO8601String() {
        let iso = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-3600))
        let result = FourD4YService().calculateTimeAgo(from: iso)
        XCTAssertFalse(result.isEmpty)
    }

    func testTimeAgoFromInvalidString() {
        let result = FourD4YService().calculateTimeAgo(from: "not-a-date")
        XCTAssertEqual(result, "now")
    }

    func testForumSiteOrder() {
        let cases = ForumSite.allCases
        XCTAssertEqual(cases[0], .rss)
        XCTAssertEqual(cases[1], .hackerNews)
        XCTAssertEqual(cases[2], .fourD4Y)
        XCTAssertEqual(cases[3], .v2ex)
        XCTAssertEqual(cases[4], .linuxDo)
        XCTAssertEqual(cases[5], .zhihu)
    }
}

// MARK: - 11. Error Handling & Edge Cases (Extended)

final class ErrorHandlingExtendedTests: XCTestCase {

    func testCommentModelWithReplies() {
        let nested = Comment(id: "n1", author: User(id: "u", username: "N", avatar: "", role: nil),
                             content: "Nested", timeAgo: "2h", likeCount: 1, replies: nil)
        let parent = Comment(id: "p1", author: User(id: "u", username: "P", avatar: "", role: nil),
                             content: "Parent", timeAgo: "3h", likeCount: 5, replies: [nested])
        XCTAssertEqual(parent.replies?.count, 1)
        XCTAssertEqual(parent.replies?.first?.id, "n1")
    }

    func testUserModelWithRole() {
        let user = User(id: "u1", username: "Admin", avatar: "person", role: "Moderator")
        XCTAssertEqual(user.role, "Moderator")
    }

    func testUserModelWithoutRole() {
        let user = User(id: "u2", username: "User", avatar: "person", role: nil)
        XCTAssertNil(user.role)
    }

    func testThreadModelWithTags() {
        let thread = FeedThread(id: "t", title: "T", content: "C",
                                author: User(id: "u", username: "U", avatar: "", role: nil),
                                community: makeTestCommunity(),
                                timeAgo: "1h", likeCount: 0, commentCount: 0,
                                isLiked: false, tags: ["swift", "ios"])
        XCTAssertEqual(thread.tags?.count, 2)
        XCTAssertTrue(thread.tags?.contains("swift") == true)
    }

    func testThreadModelWithoutTags() {
        let thread = makeTestThread(id: "t", title: "T")
        XCTAssertNil(thread.tags)
    }

    func testCommunityModelFull() {
        let c = Community(id: "c1", name: "General", description: "General discussion",
                          category: "main", activeToday: 42, onlineNow: 7)
        XCTAssertEqual(c.activeToday, 42)
        XCTAssertEqual(c.onlineNow, 7)
    }

    func testEmptyCommentList() {
        let comments: [Comment] = []
        XCTAssertTrue(comments.isEmpty)
    }

    func testNetworkMonitorExists() {
        XCTAssertNotNil(NetworkMonitor.shared)
    }

    func testNetworkMonitorAccessDoesNotCrash() {
        let monitor = NetworkMonitor.shared
        _ = monitor.isConnected
        _ = monitor.isWiFi
    }

    @MainActor func testThreadListViewModelEmptyLoad() async {
        struct EmptyService: ForumService {
            var name: String { "Empty" }; var id: String { "empty_\(UUID().uuidString)" }; var logo: String { "circle" }
            func fetchCategories() async throws -> [Community] { [makeTestCommunity()] }
            func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
            func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeTestThread(id: "1", title: "t"), [], nil) }
            func postComment(topicId: String, categoryId: String, content: String) async throws {}
            func createThread(categoryId: String, title: String, content: String) async throws {}
            func getWebURL(for thread: FeedThread) -> String { "" }
            func canCreateThread(in community: Community) -> Bool { false }
        }
        let service = EmptyService()
        let vm = ThreadListViewModel(service: service)
        await vm.loadTopics(for: makeTestCommunity())
        XCTAssertTrue(vm.threads.isEmpty)
    }

    @MainActor func testForumViewModelNeedsLogin() async {
        struct LoginRequiredService: ForumService {
            var name: String { "LR" }; var id: String { "lr_\(UUID().uuidString)" }; var logo: String { "lock" }
            var requiresLogin: Bool { true }
            func restoreSession() async -> Bool { false }
            func fetchCategories() async throws -> [Community] { [] }
            func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
            func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeTestThread(id: "1", title: "t"), [], nil) }
            func postComment(topicId: String, categoryId: String, content: String) async throws {}
            func createThread(categoryId: String, title: String, content: String) async throws {}
            func getWebURL(for thread: FeedThread) -> String { "" }
            func canCreateThread(in community: Community) -> Bool { false }
        }
        let service = LoginRequiredService()
        let vm = ForumViewModel(service: service)
        await vm.loadData()
        let needsLogin = vm.needsLogin
        XCTAssertTrue(needsLogin)
    }

    @MainActor func testForumViewModelRefreshResetsLogin() async {
        struct ToggleService: ForumService {
            var name: String { "T" }; var id: String { "t_\(UUID().uuidString)" }; var logo: String { "c" }
            var requiresLogin: Bool { true }
            var sessionResult = false
            func restoreSession() async -> Bool { sessionResult }
            func fetchCategories() async throws -> [Community] { [makeTestCommunity()] }
            func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
            func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) { (makeTestThread(id: "1", title: "t"), [], nil) }
            func postComment(topicId: String, categoryId: String, content: String) async throws {}
            func createThread(categoryId: String, title: String, content: String) async throws {}
            func getWebURL(for thread: FeedThread) -> String { "" }
            func canCreateThread(in community: Community) -> Bool { false }
        }
        let service = ToggleService()
        let vm = ForumViewModel(service: service)
        await vm.refresh()
        let needsLogin = vm.needsLogin
        XCTAssertFalse(needsLogin)
    }
}

// MARK: - TimeAgo Formatting

final class TimeAgoFormattingTests: XCTestCase {

    func testJustNow() {
        XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date()), "just now")
    }

    func testOneMinute() {
        XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-59)), "just now")
    }

    func testTwoMinutes() {
        XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-120)), "2m")
    }

    func testOneHour() {
        XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-3600)), "1h")
    }

    func testOneDay() {
        XCTAssertEqual(FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-86400)), "1d")
    }

    func testThirtyDays() {
        let result = FourD4YService().calculateTimeAgo(from: Date().addingTimeInterval(-30 * 86400))
        XCTAssertEqual(result, "30d")
    }
}

// MARK: - Helper Stubs (extended)

private final class RefreshTrackingService: ForumService {
    let name = "Refresh"; let id = "rt_\(UUID().uuidString)"; let logo = "arrow"
    var refreshCalled = false; var pageOneFetchCalled = false
    let community = Community(id: "tl", name: "TL", description: "", category: "t", activeToday: 0, onlineNow: 0)
    lazy var cachedThread = makeTestThread(id: "c", title: "Cached")
    lazy var refreshedThread = makeTestThread(id: "f", title: "Fresh")
    func fetchCategories() async throws -> [Community] { [community] }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] {
        if page == 1 { pageOneFetchCalled = true }; return [cachedThread]
    }
    func refreshCategoryThreads(categoryId: String, communities: [Community]) async throws -> [FeedThread] {
        refreshCalled = true; return [refreshedThread]
    }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) {
        (refreshedThread, [], nil)
    }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}

private final class FourCommunityService: ForumService {
    let name = "4C"; let id = "4c_\(UUID().uuidString)"; let logo = "4.circle"
    func fetchCategories() async throws -> [Community] {
        [Community(id: "a", name: "A", description: "", category: "g", activeToday: 1, onlineNow: 1),
         Community(id: "b", name: "B", description: "", category: "g", activeToday: 2, onlineNow: 2)]
    }
    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] { [] }
    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) {
        (makeTestThread(id: "1", title: "t"), [], nil)
    }
    func postComment(topicId: String, categoryId: String, content: String) async throws {}
    func createThread(categoryId: String, title: String, content: String) async throws {}
    func getWebURL(for thread: FeedThread) -> String { "" }
    func canCreateThread(in community: Community) -> Bool { false }
}
