import XCTest
@testable import Feedflow

private typealias FeedThread = Feedflow.Thread

final class FeedflowCoreTests: XCTestCase {
    @MainActor
    func testEncryptedSettingStoresCiphertextAndReadsPlaintext() {
        let key = "test_encrypted_setting_\(UUID().uuidString)"
        defer { DatabaseManager.shared.removeSetting(key: key) }

        DatabaseManager.shared.saveEncryptedSetting(key: key, value: "super-secret")

        XCTAssertNotEqual(DatabaseManager.shared.getSetting(key: key), "super-secret")
        XCTAssertEqual(DatabaseManager.shared.getEncryptedSetting(key: key), "super-secret")
    }

    @MainActor
    func testPlaintextEncryptedSettingIsMigratedOnRead() {
        let key = "test_plaintext_migration_\(UUID().uuidString)"
        defer { DatabaseManager.shared.removeSetting(key: key) }

        DatabaseManager.shared.saveSetting(key: key, value: "legacy-secret")

        XCTAssertEqual(DatabaseManager.shared.getEncryptedSetting(key: key), "legacy-secret")
        XCTAssertNotEqual(DatabaseManager.shared.getSetting(key: key), "legacy-secret")
    }

    func testCompositePrimaryKeyMigrationDecision() {
        XCTAssertFalse(DatabaseSchemaMigration.needsCompositePrimaryKeyMigration(
            currentPrimaryKey: ["thread_id", "service_id"],
            expectedPrimaryKey: ["thread_id", "service_id"]
        ))

        XCTAssertTrue(DatabaseSchemaMigration.needsCompositePrimaryKeyMigration(
            currentPrimaryKey: ["thread_id"],
            expectedPrimaryKey: ["thread_id", "service_id"]
        ))
    }

    func testLinuxDoLoginConfigRecognizesAuthenticatedCookie() {
        let config = SiteLoginConfig.config(for: .linuxDo)
        let cookie = makeCookie(name: "_t", value: "token", domain: "linux.do")

        XCTAssertNotNil(config)
        XCTAssertTrue(config?.hasAuthenticatedSession(in: [cookie]) == true)
    }

    func testLinuxDoLoginConfigChecksCookiesAcrossSitePages() {
        let config = SiteLoginConfig.config(for: .linuxDo)

        XCTAssertTrue(config?.shouldCheckCookies(for: URL(string: "https://linux.do/login")) == true)
        XCTAssertTrue(config?.shouldCheckCookies(for: URL(string: "https://linux.do/auth/github/callback")) == true)
        XCTAssertFalse(config?.shouldCheckCookies(for: URL(string: "https://github.com/login/oauth")) == true)
    }

    @MainActor
    func testBackgroundPrefetchGateIsOptInAndPublicSourceOnly() {
        let key = ThreadListViewModel.backgroundPrefetchEnabledKey
        let previous = UserDefaults.standard.object(forKey: key)
        defer {
            if let previous {
                UserDefaults.standard.set(previous, forKey: key)
            } else {
                UserDefaults.standard.removeObject(forKey: key)
            }
        }

        UserDefaults.standard.set(false, forKey: key)
        XCTAssertFalse(ThreadListViewModel(service: HackerNewsService()).allowsConfiguredBackgroundPrefetch)

        UserDefaults.standard.set(true, forKey: key)
        XCTAssertTrue(ThreadListViewModel(service: HackerNewsService()).allowsConfiguredBackgroundPrefetch)
        XCTAssertTrue(ThreadListViewModel(service: RSSService()).allowsConfiguredBackgroundPrefetch)
        XCTAssertFalse(ThreadListViewModel(service: FourD4YService()).allowsConfiguredBackgroundPrefetch)
        XCTAssertFalse(ThreadListViewModel(service: DiscourseService()).allowsConfiguredBackgroundPrefetch)
        XCTAssertFalse(ThreadListViewModel(service: V2EXService()).allowsConfiguredBackgroundPrefetch)
        XCTAssertFalse(ThreadListViewModel(service: ZhihuService()).allowsConfiguredBackgroundPrefetch)
    }

    @MainActor
    func testForceRefreshUsesServiceRefreshHook() async {
        let service = RefreshTrackingService()
        let viewModel = ThreadListViewModel(service: service)
        viewModel.threads = [service.cachedThread]

        await viewModel.loadTopics(for: service.community, forceRefresh: true)

        XCTAssertTrue(service.refreshCalled)
        XCTAssertFalse(service.pageOneFetchCalled)
        XCTAssertEqual(viewModel.threads, [service.refreshedThread])
    }
}

private func makeCookie(name: String, value: String, domain: String) -> HTTPCookie {
    let cookie = HTTPCookie(properties: [
        .name: name,
        .value: value,
        .domain: domain,
        .path: "/",
        .expires: Date().addingTimeInterval(3600)
    ])

    return try! XCTUnwrap(cookie)
}

private final class RefreshTrackingService: ForumService {
    let name = "Refresh Test"
    let id = "refresh_test_\(UUID().uuidString)"
    let logo = "arrow.clockwise"
    var refreshCalled = false
    var pageOneFetchCalled = false

    let community = Community(
        id: "timeline",
        name: "Timeline",
        description: "",
        category: "test",
        activeToday: 0,
        onlineNow: 0
    )

    lazy var cachedThread = makeThread(id: "cached", title: "Cached")
    lazy var refreshedThread = makeThread(id: "fresh", title: "Fresh")

    func fetchCategories() async throws -> [Community] {
        [community]
    }

    func fetchCategoryThreads(categoryId: String, communities: [Community], page: Int) async throws -> [FeedThread] {
        if page == 1 {
            pageOneFetchCalled = true
        }
        return [cachedThread]
    }

    func refreshCategoryThreads(categoryId: String, communities: [Community]) async throws -> [FeedThread] {
        refreshCalled = true
        return [refreshedThread]
    }

    func fetchThreadDetail(threadId: String, page: Int) async throws -> (FeedThread, [Comment], Int?) {
        (refreshedThread, [], nil)
    }

    func postComment(topicId: String, categoryId: String, content: String) async throws {}

    func createThread(categoryId: String, title: String, content: String) async throws {}

    func getWebURL(for thread: FeedThread) -> String {
        ""
    }

    func canCreateThread(in community: Community) -> Bool {
        false
    }

    private func makeThread(id: String, title: String) -> FeedThread {
        FeedThread(
            id: id,
            title: title,
            content: "",
            author: User(id: "tester", username: "Tester", avatar: "", role: nil),
            community: community,
            timeAgo: "now",
            likeCount: 0,
            commentCount: 0
        )
    }
}
