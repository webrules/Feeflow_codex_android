import SwiftUI

struct ThreadListView: View {
    @StateObject private var viewModel: ThreadListViewModel
    @EnvironmentObject var navigationManager: NavigationManager
    let community: Community
    let service: ForumService
    @State private var isInitialLoad = true
    @State private var showNewThread = false

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
                        .padding(.bottom, service.canCreateThread(in: community) ? 84 : 18)

                        if viewModel.isLoading && !viewModel.threads.isEmpty {
                            ProgressView()
                                .padding()
                        }
                    }
                    .coordinateSpace(name: "scroll")
                    .onPreferenceChange(ScrollOffsetPreferenceKey.self) { offset in
                        // User is at top if offset is close to 0 (within 50 points)
                        viewModel.updateScrollPosition(isAtTop: offset > -50)
                    }
                }
            }
        }
        .navigationTitle(community.name)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                 HStack(spacing: 16) {
                     ToolbarSymbolButton(name: FeedflowIcon.refresh) {
                        Task { await viewModel.loadTopics(for: community, isReturning: false) }
                    }

                    ToolbarSymbolButton(name: FeedflowIcon.home) {
                        navigationManager.popToRoot()
                    }
                 }
            }
        }
        .overlay(
            Group {
                if service.canCreateThread(in: community) {
                    Button(action: {
                        showNewThread = true
                    }) {
                        Image(systemName: FeedflowIcon.compose)
                            .symbolRenderingMode(.hierarchical)
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 56, height: 56)
                            .background(Color.forumAccent)
                            .clipShape(Circle())
                            .shadow(radius: 4)
                    }
                    .padding()
                }
            }
            , alignment: .bottomTrailing
        )
        .sheet(isPresented: $showNewThread) {
            NewThreadView(category: community, service: service)
        }
        .task {
            let isReturning = !isInitialLoad
            await viewModel.loadTopics(for: community, isReturning: isReturning)
            isInitialLoad = false
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
