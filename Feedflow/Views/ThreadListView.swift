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
                ThreadListLoadingView(serviceName: service.name, communityName: community.name)
            } else {
                VStack(spacing: 0) {
                    if service is ZhihuService {
                        SearchBar(
                            query: $viewModel.searchQuery,
                            isSearching: viewModel.isSearching,
                            placeholder: "搜索知乎内容",
                            onSubmit: { viewModel.performSearch() },
                            onClear: { viewModel.clearSearch() }
                        )
                        .padding(.horizontal, 13)
                        .padding(.top, 8)
                        .padding(.bottom, 4)
                    }

                    if let error = viewModel.searchError {
                        HStack(spacing: 6) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.orange)
                            Text(error)
                                .font(.system(size: 13))
                                .foregroundColor(.forumTextSecondary)
                            Spacer()
                        }
                        .padding(.horizontal, 13)
                        .padding(.vertical, 6)
                        .background(Color.forumCard.opacity(0.6))
                        .transition(.opacity)
                    }

                    if viewModel.isSearching && viewModel.threads.isEmpty {
                        VStack(spacing: 12) {
                            ProgressView()
                            Text("搜索中...")
                                .font(.system(size: 14))
                                .foregroundColor(.forumTextSecondary)
                        }
                        .frame(maxWidth: .infinity, minHeight: 120)
                    }

                    ScrollViewReader { scrollProxy in
                        ScrollView {
                            GeometryReader { geometry in
                            Color.clear.preference(
                                key: ScrollOffsetPreferenceKey.self,
                                value: geometry.frame(in: .named("scroll")).minY
                            )
                        }
                        .frame(height: 0)

                        LazyVStack(spacing: 8) {
                            let searchCommunity = Community(id: "search", name: "\"\(viewModel.searchQuery)\" 搜索结果", description: "", category: "zhihu", activeToday: 0, onlineNow: 0)
                            ThreadListStatusHeader(
                                service: service,
                                community: viewModel.isSearchActive ? searchCommunity : community,
                                visibleCount: viewModel.threads.count,
                                isRefreshing: viewModel.isSearchActive ? viewModel.isSearching : viewModel.isLoading
                            )
                            .padding(.bottom, 2)

                            if viewModel.threads.isEmpty {
                                ThreadListEmptyView(serviceName: service.name, communityName: community.name) {
                                    _ = startManualRefresh()
                                }
                            } else {
                                ForEach(viewModel.threads) { thread in
                                    NavigationLink(destination: ThreadDetailView(thread: thread, service: service, contextThreads: viewModel.threads)) {
                                        ThreadRow(thread: thread, service: service)
                                            .onAppear {
                                                viewModel.prefetchThread(thread: thread)
                                                if thread == viewModel.threads.last {
                                                    if viewModel.isSearchActive {
                                                        viewModel.loadMoreSearchResults()
                                                    } else {
                                                        Task { await viewModel.loadMoreTopics(for: community) }
                                                    }
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
                                                    await MainActor.run {
                                                        withAnimation {
                                                            viewModel.removeThread(thread)
                                                        }
                                                    }
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

private struct ThreadListStatusHeader: View {
    let service: ForumService
    let community: Community
    let visibleCount: Int
    let isRefreshing: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 10) {
                SiteIcon(service: service, size: 34)

                VStack(alignment: .leading, spacing: 2) {
                    Text(community.name)
                        .font(.system(size: 19, weight: .bold))
                        .foregroundColor(.forumTextPrimary)
                        .lineLimit(1)

                    Text(subtitle)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.forumTextSecondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                if isRefreshing {
                    ProgressView()
                        .tint(.forumAccent)
                        .controlSize(.small)
                } else {
                    Image(systemName: "line.3.horizontal.decrease.circle.fill")
                        .symbolRenderingMode(.hierarchical)
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(Color.forumTextSecondary.opacity(0.75))
                }
            }

            HStack(spacing: 8) {
                ThreadStateChip(text: "\(visibleCount) visible", color: .forumAccent)

                if service.id == "zhihu" && community.id == "recommend" {
                    ThreadStateChip(text: "read hidden", color: .green)
                    ThreadStateChip(text: "fetches to 10", color: .orange)
                } else if service.requiresLogin {
                    ThreadStateChip(text: "session checked", color: .green)
                } else {
                    ThreadStateChip(text: "public source", color: .purple)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.forumCard.opacity(0.82))
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.forumSeparator.opacity(0.65), lineWidth: 1)
        )
    }

    private var subtitle: String {
        if service.id == "zhihu" && community.id == "recommend" {
            return "\(service.name) · recommendations"
        }

        return "\(service.name) · \(community.category)"
    }
}

private struct ThreadListLoadingView: View {
    let serviceName: String
    let communityName: String

    var body: some View {
        VStack(spacing: 14) {
            ProgressView()
                .tint(.forumAccent)
                .scaleEffect(1.18)

            VStack(spacing: 4) {
                Text("Loading \(communityName)")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.forumTextPrimary)
                    .lineLimit(1)

                Text("Checking \(serviceName) and preparing threads")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.forumTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(24)
        .frame(maxWidth: 280)
        .background(Color.forumCard)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.forumSeparator.opacity(0.65), lineWidth: 1)
        )
    }
}

private struct ThreadListEmptyView: View {
    let serviceName: String
    let communityName: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            FeedflowSymbol(
                name: "tray",
                size: 24,
                color: .forumTextSecondary,
                background: .forumInputBackground.opacity(0.75),
                frameSize: 54,
                shape: .circle
            )

            VStack(spacing: 4) {
                Text("No visible threads")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.forumTextPrimary)

                Text("\(serviceName) returned no visible posts for \(communityName).")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.forumTextSecondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }

            Button(action: retry) {
                Label("Refresh", systemImage: FeedflowIcon.refresh)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.forumAccent)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.forumAccentSoft)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
        .padding(22)
        .background(Color.forumCard)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.forumSeparator.opacity(0.65), lineWidth: 1)
        )
    }
}

struct ThreadRow: View {
    let thread: Thread
    let service: ForumService

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            badgeRow

            Text(thread.title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.forumTextPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)

            if ["4d4y", "v2ex", "linux_do"].contains(service.id), let time = thread.lastPostTime, !time.isEmpty {
                HStack(spacing: 4) {
                    if let poster = thread.lastPosterName, !poster.isEmpty {
                        Text("↳ \(poster) · \(time.replacingOccurrences(of: #"^\d{4}[-/.]"#, with: "", options: .regularExpression))")
                            .font(.system(size: 11, weight: .regular))
                            .foregroundColor(.forumTextSecondary)
                            .lineLimit(1)
                    } else {
                        Text("↳ \(time.replacingOccurrences(of: #"^\d{4}[-/.]"#, with: "", options: .regularExpression))")
                            .font(.system(size: 11, weight: .regular))
                            .foregroundColor(.forumTextSecondary)
                            .lineLimit(1)
                    }
                }
            }

            if let excerpt {
                Text(excerpt)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(.forumTextSecondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }

            footer
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 13)
        .padding(.vertical, 12)
        .background(Color.forumCard)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.forumSeparator.opacity(0.62), lineWidth: 1)
        )
    }

    private var badgeRow: some View {
        let compact = ["4d4y", "v2ex", "linux_do"].contains(service.id)
        return HStack(spacing: 6) {
            if compact {
                AvatarView(urlOrName: thread.author.avatar, size: 18, fallbackText: thread.author.username)
                Text("\(thread.author.username) · \(thread.timeAgo)")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.forumTextSecondary)
                    .lineLimit(1)
                if thread.commentCount > 0 {
                    ThreadMetricPill(icon: FeedflowIcon.comments, text: "\(thread.commentCount)")
                }
            } else {
                ThreadSourceBadge(text: sourceName, color: sourceColor, filled: true)
                ThreadSourceBadge(text: categoryName, color: .forumTextSecondary, filled: false)

                if let firstTag = displayTags.first {
                    ThreadSourceBadge(text: firstTag, color: tagColor(firstTag), filled: false)
                }
            }

            Spacer(minLength: 8)

            if isLikelyFresh {
                Circle()
                    .fill(Color.green)
                    .frame(width: 7, height: 7)
                    .accessibilityLabel("Fresh thread")
            }
        }
    }

    private var footer: some View {
        HStack(spacing: 8) {
            let showInlineMeta = ["4d4y", "v2ex", "linux_do"].contains(service.id)
            if !isRSS && !showInlineMeta {
                AvatarView(urlOrName: thread.author.avatar, size: 20, fallbackText: thread.author.username)
            }

            Text(metadata)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.forumTextSecondary)
                .lineLimit(1)

            Spacer(minLength: 8)

            if thread.likeCount > 0 {
                ThreadMetricPill(icon: "hand.thumbsup", text: "\(thread.likeCount)")
            }

            if !isRSS && !showInlineMeta {
                ThreadMetricPill(icon: FeedflowIcon.comments, text: "\(thread.commentCount)")
            }
        }
    }

    private var sourceName: String {
        switch service.id {
        case "zhihu": return "知乎"
        case "linux_do": return "linux.do"
        case "4d4y": return "4D4Y"
        case "v2ex": return "V2EX"
        case "hackernews": return "HN"
        case "rss": return "RSS"
        default: return service.name
        }
    }

    private var categoryName: String {
        if service.id == "zhihu" && thread.community.id == "recommend" {
            return "Recommend"
        }

        return thread.community.name.isEmpty ? thread.community.category : thread.community.name
    }

    private var metadata: String {
        if isRSS {
            return "\(thread.community.name) · \(thread.timeAgo)"
        }

        let showInlineMeta = ["4d4y", "v2ex", "linux_do"].contains(service.id)
        if showInlineMeta {
            return ""
        }

        return "@\(thread.author.username) · \(thread.timeAgo)"
    }

    private var isRSS: Bool {
        service.id == "rss" || thread.community.category == "RSS"
    }

    private var isLikelyFresh: Bool {
        let lower = thread.timeAgo.lowercased()
        return lower.contains("m") || lower.contains("minute") || lower.contains("分钟") || lower.contains("刚刚")
    }

    private var excerpt: String? {
        let trimmed = thread.content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return (service.id == "zhihu" || isRSS || service.id == "v2ex") ? trimmed : nil
    }

    private var displayTags: [String] {
        guard service.id == "zhihu" else { return [] }
        return thread.tags ?? []
    }

    private var sourceColor: Color {
        switch service.id {
        case "zhihu": return .blue
        case "linux_do": return .green
        case "4d4y": return .orange
        case "v2ex": return .purple
        case "hackernews": return .orange
        case "rss": return .teal
        default: return .forumAccent
        }
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

private struct ThreadSourceBadge: View {
    let text: String
    let color: Color
    let filled: Bool

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .lineLimit(1)
            .minimumScaleFactor(0.78)
            .foregroundColor(filled ? .white : color)
            .padding(.horizontal, 8)
            .frame(height: 22)
            .background(filled ? color : Color.forumInputBackground.opacity(0.72))
            .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
    }
}

private struct ThreadStateChip: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .lineLimit(1)
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .frame(height: 22)
            .background(color.opacity(0.13))
            .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
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
        .padding(.horizontal, 7)
        .frame(height: 22)
        .background(Color.forumInputBackground.opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
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
