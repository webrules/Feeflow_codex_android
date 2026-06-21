import SwiftUI
import Combine

struct CrossSiteAISummaryView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = CrossSiteAISummaryViewModel()

    var body: some View {
        NavigationView {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(viewModel.sections) { section in
                            sectionCard(section)
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("AI Cross-Site Top 10")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .bottomBar) {
                    HStack {
                        Button("Close") {
                            dismiss()
                        }
                        .foregroundColor(.forumAccent)

                        Spacer()

                        Button(action: {
                            Task { await viewModel.loadAll() }
                        }) {
                            FeedflowSymbol(name: FeedflowIcon.refresh, size: 18, color: .forumAccent)
                        }
                        .disabled(viewModel.isRefreshing)
                    }
                }
            }
            .toolbarBackground(Color.forumBackground, for: .bottomBar)
            .toolbarBackground(.visible, for: .bottomBar)
            .task {
                await viewModel.loadAll()
            }
        }
    }

    @ViewBuilder
    private func sectionCard(_ section: CrossSiteSection) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(section.siteName)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.forumTextPrimary)

                Spacer()

                Text("Top 10")
                    .font(.caption)
                    .foregroundColor(.forumTextSecondary)
            }

            if section.isLoading {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Generating summary and loading links...")
                        .font(.subheadline)
                        .foregroundColor(.forumTextSecondary)
                }
            } else if let error = section.error {
                Text(error)
                    .font(.subheadline)
                    .foregroundColor(.red)
            } else {
                Text(section.summary)
                    .font(.subheadline)
                    .foregroundColor(.forumTextPrimary)
                    .lineSpacing(4)

                Divider()
                    .background(Color.forumSeparator.opacity(0.5))

                VStack(alignment: .leading, spacing: 8) {
                    ForEach(section.posts) { post in
                        NavigationLink(destination: ThreadDetailView(thread: post.thread, service: post.makeService(), contextThreads: section.contextThreads)) {
                            Text(post.title)
                                .font(.footnote)
                                .foregroundColor(.forumAccent)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
        .padding()
        .background(Color.forumCard)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.forumSeparator.opacity(0.5), lineWidth: 1)
        )
    }
}

@MainActor
final class CrossSiteAISummaryViewModel: ObservableObject {
    @Published var sections: [CrossSiteSection] = [
        CrossSiteSection(siteName: "Hacker News"),
        CrossSiteSection(siteName: "V2EX"),
        CrossSiteSection(siteName: "Linux.do"),
        CrossSiteSection(siteName: "4D4Y")
    ]
    @Published var isRefreshing = false

    private let geminiService = GeminiService()
    private let localizationManager = LocalizationManager.shared

    func loadAll() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }

        await withTaskGroup(of: (Int, CrossSiteSection).self) { group in
            for index in sections.indices {
                let current = sections[index]
                sections[index].isLoading = true
                sections[index].error = nil

                group.addTask {
                    let updated = await self.buildSection(for: current.siteName)
                    return (index, updated)
                }
            }

            for await (index, updated) in group {
                sections[index] = updated
            }
        }
    }

    private func buildSection(for siteName: String) async -> CrossSiteSection {
        var section = CrossSiteSection(siteName: siteName)
        section.isLoading = true

        do {
            let service: ForumService
            let preferredCategoryId: String?

            switch siteName {
            case "Hacker News":
                service = HackerNewsService()
                preferredCategoryId = "topstories"
            case "V2EX":
                service = V2EXService()
                preferredCategoryId = "hot"
            case "Linux.do":
                service = DiscourseService()
                preferredCategoryId = "latest"
            case "4D4Y":
                service = FourD4YService()
                preferredCategoryId = nil
            default:
                throw NSError(domain: "CrossSiteAISummary", code: 0, userInfo: [NSLocalizedDescriptionKey: "Unknown site"])
            }

            _ = await service.restoreSession()
            let communities = try await service.fetchCategories()

            guard !communities.isEmpty else {
                throw NSError(domain: "CrossSiteAISummary", code: 1, userInfo: [NSLocalizedDescriptionKey: "No categories found"])
            }

            let selectedCommunity: Community
            if let preferredCategoryId,
               let match = communities.first(where: { $0.id == preferredCategoryId }) {
                selectedCommunity = match
            } else {
                selectedCommunity = communities[0]
            }

            let threads = try await service.fetchCategoryThreads(categoryId: selectedCommunity.id, communities: communities, page: 1)
            let topThreads = Array(threads.prefix(10))
            let posts = topThreads.map { thread -> CrossSitePostLink in
                let trimmedTitle = thread.title.trimmingCharacters(in: .whitespacesAndNewlines)
                return CrossSitePostLink(
                    title: trimmedTitle.isEmpty ? "Untitled thread" : trimmedTitle,
                    thread: thread,
                    serviceId: service.id
                )
            }

            if posts.isEmpty {
                throw NSError(domain: "CrossSiteAISummary", code: 2, userInfo: [NSLocalizedDescriptionKey: "No posts found"])
            }

            let titleList = topThreads.enumerated().map { "\($0.offset + 1). \($0.element.title)" }.joined(separator: "\n")
            let prompt: String
            if localizationManager.currentLanguage == "zh" {
                prompt = """
                请用 3 到 4 句话总结以下来自 \(siteName) 的 10 条热门帖子。
                重点概括主要话题、趋势和值得注意的讨论点。

                帖子：
                \(titleList)
                """
            } else {
                prompt = """
                Summarize the following top 10 posts from \(siteName) in 3-4 concise sentences.
                Focus on dominant themes and notable trends.

                Posts:
                \(titleList)
                """
            }

            let summary = try await geminiService.generateSummary(for: prompt)

            section.summary = summary
            section.posts = posts
            section.isLoading = false
            return section
        } catch {
            section.isLoading = false
            section.error = error.localizedDescription
            section.summary = ""
            section.posts = []
            return section
        }
    }
}

struct CrossSiteSection: Identifiable {
    let id = UUID()
    let siteName: String
    var summary: String = ""
    var posts: [CrossSitePostLink] = []
    var isLoading = false
    var error: String?

    var contextThreads: [Thread] {
        posts.map(\.thread)
    }
}

struct CrossSitePostLink: Identifiable {
    let id = UUID()
    let title: String
    let thread: Thread
    let serviceId: String

    func makeService() -> ForumService {
        switch serviceId {
        case "hackernews":
            return HackerNewsService()
        case "v2ex":
            return V2EXService()
        case "linux_do":
            return DiscourseService()
        case "4d4y":
            return FourD4YService()
        case "zhihu":
            return ZhihuService()
        case "rss":
            return RSSService()
        default:
            return FourD4YService()
        }
    }
}
