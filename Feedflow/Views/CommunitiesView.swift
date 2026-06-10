import SwiftUI

struct CommunitiesView: View {
    @StateObject private var viewModel: ForumViewModel
    @EnvironmentObject var navigationManager: NavigationManager
    let service: ForumService
    @State private var showFeedManager = false
    @State private var showDailySummary = false
    @State private var showLoginSheet = false

    init(service: ForumService) {
        self.service = service
        _viewModel = StateObject(wrappedValue: ForumViewModel(service: service))
    }

    var body: some View {
        ZStack {
            Color.forumBackground.ignoresSafeArea()

            if viewModel.isLoading && viewModel.communities.isEmpty {
                ProgressView()
                    .tint(.forumAccent)
                    .scaleEffect(1.5)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {

                        // All Categories
                        VStack(alignment: .leading, spacing: 16) {

                            ForEach(viewModel.communities) { community in
                                NavigationLink(value: community) {
                                    CommunityRow(community: community)
                                }
                            }
                        }
                    }
                    .padding(.top)
                }
            }
        }
        .navigationTitle(service.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.forumBackground, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 16) {
                    // Show "Manage Feeds" button only for RSS
                    if service.id == "rss" {
                        Button(action: {
                            showDailySummary = true
                        }) {
                            FeedflowSymbol(name: FeedflowIcon.summary, size: 18, color: .forumAccent)
                        }

                        Button(action: {
                            showFeedManager = true
                        }) {
                            FeedflowSymbol(name: FeedflowIcon.feedManager, size: 18, color: .forumAccent)
                        }
                    }

                    ToolbarSymbolButton(name: FeedflowIcon.refresh, activeColor: .forumTextPrimary) {
                        Task {
                            await viewModel.refresh()
                        }
                    }

                    ToolbarSymbolButton(name: FeedflowIcon.home, activeColor: .forumTextPrimary) {
                        navigationManager.popToRoot()
                    }
                }
            }
        }
        .sheet(isPresented: $showFeedManager, onDismiss: {
            // Refresh communities after managing feeds
            Task { await viewModel.refresh() }
        }) {
            RSSFeedManagerView()
        }
        .sheet(isPresented: $showDailySummary) {
            if let rssService = service as? RSSService {
                DailyRSSSummaryView(rssService: rssService)
            }
        }
        .navigationDestination(for: Community.self) { community in
            ThreadListView(community: community, service: service)
        }
        .sheet(isPresented: $showLoginSheet, onDismiss: {
            // After login sheet closes, retry loading
            Task { await viewModel.refresh() }
        }) {
            LoginView(initialSite: ForumSite.from(serviceId: service.id))
        }
        .onChange(of: viewModel.needsLogin) { needsLogin in
            if needsLogin {
                showLoginSheet = true
            }
        }
    }
}

struct CommunityRow: View {
    let community: Community

    var body: some View {
        HStack(spacing: 12) {
            FeedflowSymbol(
                name: "folder.fill",
                size: 17,
                color: .forumAccent,
                background: .forumAccentSoft,
                frameSize: 38
            )

            VStack(alignment: .leading, spacing: 6) {
                Text(community.name)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.forumTextPrimary)

                if !community.description.isEmpty {
                    Text(community.description)
                        .font(.system(size: 14))
                        .foregroundColor(.forumTextSecondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
            }

            Spacer()

            FeedflowSymbol(name: "chevron.right", size: 13, color: .forumTextSecondary.opacity(0.55))
        }
        .padding()
        .background(Color.forumBackground)
        .overlay(
            Rectangle()
                .frame(height: 1)
                .foregroundColor(Color.forumTextSecondary.opacity(0.1)),
            alignment: .bottom
        )
    }
}
