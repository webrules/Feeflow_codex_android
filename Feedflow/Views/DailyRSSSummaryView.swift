import SwiftUI
import Combine

class DailyRSSSummaryViewModel: ObservableObject {
    private let rssService: RSSService
    private let geminiService = GeminiService()
    private let cacheKey = "daily_rss_summary"
    private let cacheMaxAge: TimeInterval = 7 * 24 * 60 * 60 // 7 days

    @Published var dailySummary: String = ""
    @Published var isLoading: Bool = false
    @Published var articlesFound: Int = 0
    @Published var errorMessage: String? = nil
    @Published var isCached: Bool = false

    init(rssService: RSSService) {
        self.rssService = rssService
    }

    @MainActor
    func generateSummary(forceRefresh: Bool = false) async {
        isLoading = true
        errorMessage = nil
        dailySummary = ""
        isCached = false

        // Check cache first (unless forcing refresh)
        if !forceRefresh,
           let cached = DatabaseManager.shared.getSummaryIfFresh(threadId: cacheKey, maxAgeSeconds: cacheMaxAge) {
            self.dailySummary = cached
            self.isCached = true
            self.isLoading = false
            return
        }

        // 1. Fetch updates
        let threads = await rssService.fetchDailyUpdates()
        articlesFound = threads.count

        if threads.isEmpty {
            dailySummary = "no_updates_24h".localized()
            isLoading = false
            return
        }

        // 2. Prepare content for Gemini
        let maxArticles = 30
        let limitedThreads = threads.prefix(maxArticles)

        var promptContent = "Here are the RSS feed updates from the last 24 hours:\n\n"

        for thread in limitedThreads {
            let cleanBody = thread.content.prefix(500).replacingOccurrences(of: "\n", with: " ")
            promptContent += "Source: \(thread.community.name)\n"
            promptContent += "Title: \(thread.title)\n"
            promptContent += "Link: \(thread.id)\n"
            promptContent += "Snippet: \(cleanBody)...\n\n"
            promptContent += "---\n\n"
        }

        promptContent += """

        Please provide a 'Daily Briefing' based on these updates.
        1. Summarize the key themes or topics discussed.
        2. Highlight the most interesting 3-5 articles with their Titles and a brief 1-sentence summary for each.
        3. Provide a list of all mentioned articles with their Links, grouped by Source.

        Format the output clearly with Markdown headers.
        """

        // 3. Call Gemini
        do {
            let result = try await geminiService.generateSummary(for: promptContent)
            self.dailySummary = result

            // Save to cache
            DatabaseManager.shared.saveSummary(threadId: cacheKey, summary: result)
        } catch {
            self.errorMessage = "Failed to generate summary: \(error.localizedDescription)"
        }

        isLoading = false
    }
}

struct DailyRSSSummaryView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: DailyRSSSummaryViewModel
    @ObservedObject var localizationManager = LocalizationManager.shared

    init(rssService: RSSService) {
        _viewModel = StateObject(wrappedValue: DailyRSSSummaryViewModel(rssService: rssService))
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                if viewModel.isLoading {
                    VStack(spacing: 20) {
                        ProgressView()
                            .scaleEffect(1.5)
                            .tint(.forumAccent)

                        Text("fetching_summary".localized())
                            .foregroundColor(.forumTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding()

                        if viewModel.articlesFound > 0 {
                            Text(LocalizationManager.shared.localizedString("articles_found", viewModel.articlesFound))
                                .font(.caption)
                                .foregroundColor(.forumTextSecondary)
                        }
                    }
                } else if let error = viewModel.errorMessage {
                    VStack(spacing: 16) {
                        FeedflowSymbol(
                            name: "exclamationmark.triangle.fill",
                            size: 34,
                            color: .red,
                            background: Color.red.opacity(0.12),
                            frameSize: 68,
                            shape: .circle
                        )
                        Text("error".localized())
                            .font(.headline)
                        Text(error)
                            .foregroundColor(.forumTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding()

                        Button("try_again".localized()) {
                            Task { await viewModel.generateSummary(forceRefresh: true) }
                        }
                        .buttonStyle(.bordered)
                    }
                } else if !viewModel.dailySummary.isEmpty {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            HStack {
                                Text("daily_briefing".localized())
                                    .font(.title)
                                    .bold()
                                    .foregroundColor(.forumAccent)
                                Spacer()
                                if viewModel.isCached {
                                    Text("cached".localized())
                                        .font(.caption)
                                        .foregroundColor(.forumTextSecondary)
                                        .padding(6)
                                        .background(Color.forumCard)
                                        .cornerRadius(6)
                                }
                                Text("last_24h".localized())
                                    .font(.caption)
                                    .foregroundColor(.forumTextSecondary)
                                    .padding(6)
                                    .background(Color.forumCard)
                                    .cornerRadius(6)
                            }

                            // Markdown Rendering (Simplified using ParsedContentView or just Text)
                            // Since standard Text doesn't support Markdown deeply in older SwiftUI,
                            // we'll use LocalizedStringKey or just Text for now.
                            // Feedflow seems to rely on basic Text or ParsedContentView for HTML.
                            // But Gemini returns Markdown.
                            // iOS 15+ Text supports basic Markdown.
                            Text(LocalizedStringKey(viewModel.dailySummary))
                                .font(.body)
                                .foregroundColor(.forumTextPrimary)
                                .lineSpacing(6)
                                .textSelection(.enabled)

                            Divider()
                                .padding(.vertical)

                            Button(action: {
                                Task { await viewModel.generateSummary(forceRefresh: true) }
                            }) {
                                Label("regenerate".localized(), systemImage: FeedflowIcon.refresh)
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)
                        }
                        .padding()
                    }
                    .refreshable {
                        await viewModel.generateSummary(forceRefresh: true)
                    }
                } else {
                    // Initial State? Should auto-start?
                    // Or "No updates"
                    VStack(spacing: 16) {
                        Text("no_summary".localized())
                            .foregroundColor(.forumTextSecondary)

                        Button("generate_daily_summary".localized()) {
                            Task { await viewModel.generateSummary() }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.forumAccent)
                    }
                }
            }
            .navigationTitle("daily_rss_summary".localized())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .bottomBar) {
                    Button(action: { dismiss() }) {
                        FeedflowSymbol(name: FeedflowIcon.close, size: 20, color: .forumTextSecondary)
                    }
                }
            }
            .toolbarBackground(Color.forumBackground, for: .bottomBar)
            .toolbarBackground(.visible, for: .bottomBar)
            .task {
                if viewModel.dailySummary.isEmpty && !viewModel.isLoading {
                    await viewModel.generateSummary()
                }
            }
        }
    }
}
