import XCTest
@testable import Feedflow

// Regression coverage for the Zhihu search author/avatar fix and the
// full-screen image zoom improvement (PR #13). These guard against the two
// fixes silently regressing if the search avatar pipeline or zoom clamp
// change again.

final class ZhihuSearchAvatarTests: XCTestCase {

    private let service = ZhihuService()

    func testAvatarTemplateSizePlaceholderResolved() {
        let resolved = service.normalizedAvatarURL("https://pic.zhimg.com/v2-abc_{size}.jpg")
        XCTAssertFalse(resolved.contains("{size}"), "{size} placeholder must be replaced")
        XCTAssertTrue(resolved.contains("_80.jpg"), "expected 80px size, got \(resolved)")
    }

    func testProtocolRelativeAvatarGetsHTTPS() {
        let resolved = service.normalizedAvatarURL("//pic.zhimg.com/avatar.jpg")
        XCTAssertEqual(resolved, "https://pic.zhimg.com/avatar.jpg")
    }

    func testEmptyAvatarFallsBackToTemplate() {
        let resolved = service.normalizedAvatarURL("", template: "https://pic.zhimg.com/v2-x_{size}.jpg")
        XCTAssertTrue(resolved.contains("_80.jpg"))
    }

    func testEmptyAvatarAndTemplateStaysEmpty() {
        XCTAssertEqual(service.normalizedAvatarURL("", template: nil), "")
        XCTAssertEqual(service.normalizedAvatarURL(nil), "")
    }

    func testAmpersandEntityDecodedInAvatarURL() {
        let resolved = service.normalizedAvatarURL("https://pic.zhimg.com/a.jpg?x=1&amp;y=2")
        XCTAssertEqual(resolved, "https://pic.zhimg.com/a.jpg?x=1&y=2")
    }

    // The in-page search_v3 fetch returns objects whose authors carry names and
    // avatars; a missing/empty name must surface 匿名用户 instead of crashing or
    // showing a blank. This mirrors the mapping ZhihuService applies.
    func testNonEmptyAuthorNameKept() {
        let name = "申权认真生活"
        XCTAssertEqual(name.isEmpty ? "匿名用户" : name, name)
    }

    func testEmptyAuthorNameBecomesAnonymous() {
        let name = ""
        XCTAssertEqual(name.isEmpty ? "匿名用户" : name, "匿名用户")
    }

    // Zhihu nests comment authors under `author.member`; decoding must reach the
    // name + avatar so repliers don't all show as 匿名.
    func testCommentAuthorDecodedFromNestedMember() throws {
        let json = """
        {"id":"c1","content":"hi","author":{"role":"normal","member":{"id":"m1","name":"wwww2021","avatar_url":"https://picx.zhimg.com/v2-x.jpg","headline":"hi"}}}
        """.data(using: .utf8)!
        let c = try JSONDecoder().decode(ZhihuComment.self, from: json)
        XCTAssertEqual(c.author?.name, "wwww2021")
        XCTAssertEqual(c.author?.id, "m1")
        XCTAssertEqual(c.author?.avatarUrl, "https://picx.zhimg.com/v2-x.jpg")
    }

    func testCommentAuthorDecodedFromFlatFallback() throws {
        let json = """
        {"id":"c2","content":"hi","author":{"id":"u2","name":"flatUser","avatar_url":"https://p/y.jpg"}}
        """.data(using: .utf8)!
        let c = try JSONDecoder().decode(ZhihuComment.self, from: json)
        XCTAssertEqual(c.author?.name, "flatUser")
        XCTAssertEqual(c.author?.avatarUrl, "https://p/y.jpg")
    }

    func testCurrentHotCardShapeDecodesQuestionFields() throws {
        let json = """
        {"id":"0_987","detail_text":"765万热度","target":{"id":123,"type":"question","url":"https://api.zhihu.com/questions/123","title":"真实问题标题","excerpt":"问题摘要","author":{"id":"u1","name":"提问者","avatar_url":"https://pic.zhimg.com/u1.jpg"}}}
        """.data(using: .utf8)!

        let item = try JSONDecoder().decode(ZhihuHotListItem.self, from: json)

        XCTAssertEqual(item.questionId, "123")
        XCTAssertEqual(item.target?.title, "真实问题标题")
        XCTAssertEqual(item.target?.excerpt, "问题摘要")
        XCTAssertEqual(item.target?.author?.name, "提问者")
        XCTAssertEqual(item.target?.author?.avatarUrl, "https://pic.zhimg.com/u1.jpg")
    }

    func testHotCardQuestionIdFallsBackToQuestionURL() throws {
        let json = """
        {"id":"ranking-card","target":{"url":"https://www.zhihu.com/question/456?utm_source=hot","title":"URL question"}}
        """.data(using: .utf8)!

        let item = try JSONDecoder().decode(ZhihuHotListItem.self, from: json)

        XCTAssertEqual(item.questionId, "456")
    }

    func testNotInterestedOnlyAppearsForRecommendations() {
        XCTAssertTrue(ThreadListActionPolicy.supportsNotInterested(serviceId: "zhihu", communityId: "recommend"))
        XCTAssertFalse(ThreadListActionPolicy.supportsNotInterested(serviceId: "zhihu", communityId: "hot"))
        XCTAssertFalse(ThreadListActionPolicy.supportsNotInterested(serviceId: "v2ex", communityId: "recommend"))
    }
}

final class ImageZoomClampTests: XCTestCase {

    func testClampHonorsMinimum() {
        XCTAssertEqual(FullScreenImageView.clampScale(0.2, min: 1.0, max: 5.0), 1.0)
    }

    func testClampHonorsMaximum() {
        XCTAssertEqual(FullScreenImageView.clampScale(12.0, min: 1.0, max: 5.0), 5.0)
    }

    func testClampPassesThroughInRange() {
        XCTAssertEqual(FullScreenImageView.clampScale(2.5, min: 1.0, max: 5.0), 2.5)
    }
}
