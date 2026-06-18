import SwiftUI

struct ThreadListView: View {
    @StateObject private var viewModel: ThreadListViewModel
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var navigationManager: NavigationManager
    let community: Community
    let service: ForumService
    @State private var isInitialLoad = true
    @State private var showNewThread = false
    @State private var showLoginSheet = false

    init(community: Community, service: ForumService) {
        self.community = community
        self.service = service
        _viewModel = StateObject(wrappedValue: ThreadListViewModel(service: service))
    }

    var body: some View {
        ZStack {
            Color.forumBackground.ignoresSafeArea()

            if viewModel.isLoading && viewModel.threads.isEmpty {
                ProgressView()
                    .tint(.forumAccent)
                    .scaleEffect(1.5)
            } else {
                ScrollViewReader { scrollProxy in
                    ScrollView {
                        GeometryReader { geometry in
                            Color.clear.preference(
                                key: ScrollOffsetPreferenceKey.self,
                                value: geometry.frame(in: .named("scroll")).minY
                            )
                        }
                        .frame(height: 0)

                        LazyVStack(spacing: 10) {
                            ForEach(viewModel.threads) { thread in
                                NavigationLink(destination: ThreadDetailView(thread: thread, service: service, contextThreads: viewModel.threads)) {
                                    ThreadRow(thread: thread)
                                        .onAppear {
                                            viewModel.prefetchThread(thread: thread)
                                            if thread == viewModel.threads.last {
                                                Task { await viewModel.loadMoreTopics(for: community) }
                                            }
                                        }
                                        .onDisappear {
                                            viewModel.cancelPrefetch(threadId: thread.id)
                                        }
                                }
                                .buttonStyle(PlainButtonStyle())
                                .if(service is ZhihuService) { view in
                                    view.contextMenu {
                                        Button(role: .destructive) {
                                            Task {
                                                // Remove from UI immediately
                                                await MainActor.run {
                                                    withAnimation {
                                                        viewModel.removeThread(thread)
                                                    }
                                                }
                                                // Also send downvote API call if possible
                                                if let zhihuService = service as? ZhihuService,
                                                   let feedItem = zhihuService.getFeedItem(for: thread.id) {
                                                    await zhihuService.downvoteItem(feedItem: feedItem)
                                                }
                                            }
                                        } label: {
                                            Label("不感兴趣", systemImage: "hand.thumbsdown")
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 10)
                        .padding(.bottom, 18)

                        if viewModel.isLoading && !viewModel.threads.isEmpty {
                            ProgressView()
                                .padding()
                        }
                    }
                    .refreshable {
                        await startManualRefresh().value
                    }
                    .coordinateSpace(name: "scroll")
                    .onPreferenceChange(ScrollOffsetPreferenceKey.self) { offset in
                        // User is at top if offset is close to 0 (within 50 points)
                        viewModel.updateScrollPosition(isAtTop: offset > -50)
                    }
                }
            }

            if let refreshMessage = viewModel.refreshMessage {
                VStack {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.orange)

                        Text(refreshMessage)
                            .font(.caption)
                            .foregroundColor(.forumTextPrimary)
                            .multilineTextAlignment(.leading)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color.forumCard)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(Color.forumSeparator.opacity(0.7), lineWidth: 1)
                    )
                    .padding(.horizontal)
                    .padding(.top, 10)

                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .animation(.easeInOut(duration: 0.2), value: refreshMessage)
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(backSwipeGesture)
        .toolbar {
            ToolbarItem(placement: .bottomBar) {
                 HStack(spacing: 12) {
                     ToolbarSymbolButton(name: FeedflowIcon.back) {
                        dismiss()
                    }

                    Text(community.name)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.forumTextPrimary)
                        .lineLimit(1)

                    Spacer()

                    if service.canCreateThread(in: community) {
                        ToolbarSymbolButton(name: FeedflowIcon.compose) {
                            showNewThread = true
                        }
                    }

                    ToolbarSymbolButton(name: FeedflowIcon.refresh) {
                        startManualRefresh()
                    }

                    ToolbarSymbolButton(name: FeedflowIcon.home) {
                        navigationManager.popToRoot()
                    }
                 }
            }
        }
        .toolbarBackground(Color.forumBackground, for: .bottomBar)
        .toolbarBackground(.visible, for: .bottomBar)
        .sheet(isPresented: $showNewThread) {
            NewThreadView(category: community, service: service)
        }
        .sheet(isPresented: $showLoginSheet, onDismiss: {
            startManualRefresh()
        }) {
            LoginView(initialSite: ForumSite.from(serviceId: service.id))
        }
        .onChange(of: viewModel.needsLogin) { needsLogin in
            if needsLogin {
                viewModel.clearLoginRequest()
                showLoginSheet = true
            }
        }
        .task {
            let isReturning = !isInitialLoad
            await viewModel.loadTopics(for: community, isReturning: isReturning)
            isInitialLoad = false
        }
    }

    @discardableResult
    private func startManualRefresh() -> Task<Void, Never> {
        Task {
            await viewModel.loadTopics(for: community, isReturning: false, forceRefresh: true)
        }
    }

    private var backSwipeGesture: some Gesture {
        DragGesture(minimumDistance: 12)
            .onEnded { value in
                let horizontal = value.translation.width
                let vertical = value.translation.height
                let predictedHorizontal = value.predictedEndTranslation.width
                let startedNearLeftEdge = value.startLocation.x <= 80
                let isHorizontalSwipe = abs(horizontal) > abs(vertical) * 1.2
                let hasEnoughDistance = horizontal > 45 || predictedHorizontal > 90

                if startedNearLeftEdge && isHorizontalSwipe && hasEnoughDistance {
                    dismiss()
                }
            }
    }
}

// Preference key for tracking scroll offset
struct ScrollOffsetPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct ThreadRow: View {
    let thread: Thread

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header

            Text(thread.title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.forumTextPrimary)
                .lineLimit(3)
                .multilineTextAlignment(.leading)

            if let excerpt {
                Text(excerpt)
                    .font(.subheadline)
                    .foregroundColor(.forumTextSecondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }

            footer
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.forumCard)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.forumSeparator.opacity(0.55), lineWidth: 1)
        )
    }

    private var header: some View {
        HStack(spacing: 9) {
            if isRSS {
                FeedflowSymbol(
                    name: FeedflowIcon.feed,
                    size: 13,
                    color: .forumAccent,
                    background: .forumAccentSoft,
                    frameSize: 28,
                    shape: .circle
                )
            } else {
                AvatarView(urlOrName: thread.author.avatar, size: 28, fallbackText: thread.author.username)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(primaryMetadata)
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.forumTextPrimary)
                    .lineLimit(1)

                Text(secondaryMetadata)
                    .font(.caption2)
                    .foregroundColor(.forumTextSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            if let firstTag = displayTags.first {
                ThreadTagChip(text: firstTag, color: tagColor(firstTag))
            }
        }
    }

    private var footer: some View {
        HStack(spacing: 8) {
            if thread.likeCount > 0 {
                ThreadMetricPill(icon: "hand.thumbsup", text: "\(thread.likeCount)")
            }

            if !isRSS {
                ThreadMetricPill(icon: FeedflowIcon.comments, text: "\(thread.commentCount)")
            }

            if displayTags.count > 1 {
                ForEach(displayTags.dropFirst().prefix(2), id: \.self) { tag in
                    ThreadTagChip(text: tag, color: tagColor(tag))
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.forumTextSecondary.opacity(0.55))
        }
    }

    private var isRSS: Bool {
        thread.community.category == "RSS"
    }

    private var primaryMetadata: String {
        isRSS ? thread.community.name : "@\(thread.author.username)"
    }

    private var secondaryMetadata: String {
        isRSS ? thread.timeAgo : "\(thread.community.name) · \(thread.timeAgo)"
    }

    private var excerpt: String? {
        let trimmed = thread.content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return (thread.community.category == "zhihu" || isRSS) ? trimmed : nil
    }

    private var displayTags: [String] {
        guard thread.community.category == "zhihu" else { return [] }
        return thread.tags ?? []
    }

    private func tagColor(_ tag: String) -> Color {
        switch tag {
        case "回答": return .blue
        case "文章": return .green
        case "问题": return .orange
        case "视频": return .purple
        case "想法": return .pink
        default: return .forumTextSecondary
        }
    }
}

private struct ThreadMetricPill: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .symbolRenderingMode(.hierarchical)
            Text(text)
        }
        .font(.caption2)
        .fontWeight(.medium)
        .foregroundColor(.forumTextSecondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background(Color.forumInputBackground.opacity(0.7))
        .clipShape(Capsule())
    }
}

private struct ThreadTagChip: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(color.opacity(0.13))
            .clipShape(Capsule())
    }
}
