import SwiftUI

struct ThreadDetailView: View {
    @StateObject private var viewModel: ThreadDetailViewModel
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var navigationManager: NavigationManager
    @ObservedObject var localizationManager = LocalizationManager.shared
    @State private var replyText: String = ""
    @State private var showAISummary: Bool = false
    @State private var replyErrorMessage: String?
    let service: ForumService

    init(thread: Thread, service: ForumService, contextThreads: [Thread] = []) {
        self.service = service
        _viewModel = StateObject(wrappedValue: ThreadDetailViewModel(thread: thread, service: service, contextThreads: contextThreads))
    }

    private var isRSSFeed: Bool {
        service is RSSService
    }

    private var canReply: Bool {
        service.supportsCommenting
    }

    private var supportsEdgeThreadNavigation: Bool {
        ["4d4y", "linux_do", "hackernews", "zhihu", "v2ex"].contains(service.id)
    }

    var body: some View {
        ZStack {
            Color.forumBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                            VStack(alignment: .leading, spacing: 12) {
                                // Header (hidden for RSS)
                                if !isRSSFeed {
                                    HStack {
                                        AvatarView(urlOrName: viewModel.thread.author.avatar, size: 40, fallbackText: viewModel.thread.author.username)

                                        VStack(alignment: .leading) {
                                            Text(viewModel.thread.author.username)
                                                .font(.headline)
                                                .foregroundColor(.forumTextPrimary)
                                            if let role = viewModel.thread.author.role {
                                                TagView(text: role)
                                            }
                                        }

                                        Spacer()
                                    }
                                }
                                Color.clear.frame(height: 0).id("thread_top")

                                // Content
                                Text(viewModel.thread.title)
                                    .font(.title2)
                                    .bold()
                                    .foregroundColor(.forumTextPrimary)

                                // Parsed Content (Text + Images)
                                ParsedContentView(text: viewModel.thread.content)

                                // Tags (hidden for RSS)
                                if !isRSSFeed, let tags = viewModel.thread.tags, !tags.isEmpty {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack {
                                            ForEach(tags, id: \.self) { tag in
                                                TagView(text: tag)
                                            }
                                        }
                                    }
                                }

                                Divider()
                                    .background(Color.forumTextSecondary.opacity(0.1))
                                    .padding(.vertical, 8)

                                if viewModel.isLoading && viewModel.comments.isEmpty {
                                    ProgressView()
                                        .tint(.forumAccent)
                                        .scaleEffect(1.5)
                                        .padding()
                                } else {
                                    LazyVStack(spacing: 0) {
                                        ForEach(viewModel.comments) { comment in
                                            CommentRow(comment: comment, canReply: canReply) {
                                                viewModel.selectCommentForReply(comment)
                                            }
                                            .onAppear {
                                                if comment.id == viewModel.comments.last?.id {
                                                    Task { await viewModel.loadMoreComments() }
                                                }
                                            }
                                            Divider()
                                                .background(Color.forumTextSecondary.opacity(0.1))
                                        }

                                        if viewModel.isLoading {
                                            ProgressView()
                                                .id("loading_indicator")
                                                .padding()
                                        }

                                        Color.clear
                                            .frame(height: 1)
                                            .id("bottom_anchor")
                                    }
                                }
                            }
                            .padding()
                        }
                        .onChange(of: viewModel.comments) { _ in
                            if viewModel.shouldScrollAfterReply {
                                Task {
                                    try? await Task.sleep(nanoseconds: 100_000_000)
                                    await MainActor.run {
                                        scrollToBottom(proxy: proxy)
                                        viewModel.shouldScrollAfterReply = false
                                    }
                                }
                            }
                        }
                        .onChange(of: viewModel.thread.id) { _ in
                            proxy.scrollTo("thread_top", anchor: .top)
                        }
                }

                    // Reply toolbar - only for services with native commenting support.
                    if canReply {
                        if let replyingTo = viewModel.replyingTo {
                            HStack {
                                Text("\(LocalizationManager.shared.localizedString("replying_to")) \(replyingTo.author.username)")
                                    .font(.caption)
                                    .foregroundColor(.forumTextSecondary)
                                Spacer()
                                Button(action: {
                                    viewModel.cancelReply()
                                }) {
                                    FeedflowSymbol(name: FeedflowIcon.close, size: 16, color: .forumTextSecondary)
                                }
                            }
                            .padding(.horizontal)
                            .padding(.top, 8)
                            .background(Color.forumBackground)
                        }

                        // Bottom Input
                        VStack(spacing: 0) {
                            Divider().background(Color.forumTextSecondary.opacity(0.1))
                            HStack {
                                Button(action: {}) {
                                    FeedflowSymbol(name: "photo.on.rectangle.angled", size: 18, color: .forumTextSecondary)
                                }

                                TextField("thread_reply".localized(), text: $replyText)
                                    .padding(10)
                                    .background(Color.forumCard)
                                    .cornerRadius(20)
                                    .foregroundColor(.forumTextPrimary)

                                Button(action: {
                                    guard !replyText.isEmpty else { return }
                                    let content = replyText
                                    let feedback = UINotificationFeedbackGenerator()
                                    feedback.prepare()

                                    Task {
                                        do {
                                            try await viewModel.sendReply(content: content)

                                            await MainActor.run {
                                                replyText = ""
                                                feedback.notificationOccurred(.success)
                                            }
                                        } catch {
                                            await MainActor.run {
                                                replyErrorMessage = error.localizedDescription
                                                feedback.notificationOccurred(.error)
                                            }
                                        }
                                    }
                                }) {
                                    FeedflowSymbol(name: FeedflowIcon.upload, size: 18, color: .forumAccent)
                                }
                            }
                            .padding()
                            .background(Color.forumBackground)
                        }
                    }

                    bottomActionToolbar
            }

            if supportsEdgeThreadNavigation && (viewModel.hasPreviousThread || viewModel.hasNextThread) {
                threadNavigationControls
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(backSwipeGesture)
        .sheet(isPresented: $showAISummary) {
            AISummaryView(threadId: viewModel.thread.id, serviceId: service.id, content: aiSummaryContent)
        }
        .alert("reply_failed".localized(), isPresented: Binding(
            get: { replyErrorMessage != nil },
            set: { if !$0 { replyErrorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {
                replyErrorMessage = nil
            }
        } message: {
            Text(replyErrorMessage ?? "")
        }
        .task {
            await viewModel.loadDetails()
        }
    }

    private var backSwipeGesture: some Gesture {
        DragGesture(minimumDistance: 20)
            .onEnded { value in
                let horizontal = value.translation.width
                let vertical = value.translation.height
                let startedAtLeftEdge = value.startLocation.x <= 36
                let isBackSwipe = horizontal > 70 && abs(horizontal) > abs(vertical) * 1.4

                if startedAtLeftEdge && isBackSwipe {
                    dismiss()
                }
            }
    }

    private var bottomActionToolbar: some View {
        HStack(spacing: 12) {
            Button(action: { dismiss() }) {
                FeedflowSymbol(
                    name: FeedflowIcon.back,
                    size: 18,
                    color: .forumAccent,
                    background: Color.forumAccent.opacity(0.12),
                    frameSize: 36,
                    shape: .circle
                )
            }
            .buttonStyle(.plain)

            Spacer()

            HStack(spacing: 12) {
                FeedflowSymbol(
                    name: viewModel.isLatest ? "checkmark.icloud.fill" : "externaldrive.fill",
                    size: 14,
                    color: viewModel.isLatest ? .green : .orange,
                    background: (viewModel.isLatest ? Color.green : Color.orange).opacity(0.14),
                    frameSize: 30,
                    shape: .circle
                )
                .help(viewModel.isLatest ? "Latest Content" : "Local Content")

                Button(action: {
                    Task { await viewModel.refreshDetails() }
                }) {
                    FeedflowSymbol(
                        name: FeedflowIcon.refresh,
                        size: 18,
                        color: viewModel.isLoading ? .forumTextSecondary : .forumAccent,
                        background: (viewModel.isLoading ? Color.forumTextSecondary : Color.forumAccent).opacity(0.12),
                        frameSize: 36,
                        shape: .circle
                    )
                }
                .buttonStyle(.plain)
                .disabled(viewModel.isLoading)

                Button(action: {
                    viewModel.toggleBookmark()
                    let feedback = UIImpactFeedbackGenerator(style: .medium)
                    feedback.impactOccurred()
                }) {
                    FeedflowSymbol(
                        name: viewModel.isBookmarked ? FeedflowIcon.bookmarkFill : FeedflowIcon.bookmark,
                        size: 18,
                        color: .forumAccent,
                        background: Color.forumAccent.opacity(0.12),
                        frameSize: 36,
                        shape: .circle
                    )
                }
                .buttonStyle(.plain)

                Button(action: {
                    showAISummary = true
                }) {
                    FeedflowSymbol(
                        name: FeedflowIcon.ai,
                        size: 18,
                        color: .forumAccent,
                        background: Color.forumAccent.opacity(0.12),
                        frameSize: 36,
                        shape: .circle
                    )
                }
                .buttonStyle(.plain)

                Button(action: {
                    navigationManager.popToRoot()
                }) {
                    FeedflowSymbol(
                        name: FeedflowIcon.home,
                        size: 18,
                        color: .forumAccent,
                        background: Color.forumAccent.opacity(0.12),
                        frameSize: 36,
                        shape: .circle
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            Color.forumBackground
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(Color.forumTextSecondary.opacity(0.10))
                        .frame(height: 1)
                }
        )
    }

    private var threadNavigationControls: some View {
        VStack(spacing: 8) {
            Button(action: goToPreviousThread) {
                FeedflowSymbol(
                    name: "chevron.up",
                    size: 18,
                    color: viewModel.hasPreviousThread ? .forumAccent : .forumTextSecondary.opacity(0.45),
                    background: .forumCard,
                    frameSize: 42,
                    shape: .circle
                )
            }
            .buttonStyle(.plain)
            .disabled(!viewModel.hasPreviousThread || viewModel.isLoading)

            Button(action: goToNextThread) {
                FeedflowSymbol(
                    name: "chevron.down",
                    size: 18,
                    color: viewModel.hasNextThread ? .forumAccent : .forumTextSecondary.opacity(0.45),
                    background: .forumCard,
                    frameSize: 42,
                    shape: .circle
                )
            }
            .buttonStyle(.plain)
            .disabled(!viewModel.hasNextThread || viewModel.isLoading)
        }
        .padding(.trailing, 14)
        .padding(.bottom, canReply ? 154 : 82)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
    }

    private func goToPreviousThread() {
        guard viewModel.hasPreviousThread, !viewModel.isLoading else { return }
        let feedback = UIImpactFeedbackGenerator(style: .light)
        feedback.impactOccurred()
        viewModel.goPrevious()
    }

    private func goToNextThread() {
        guard viewModel.hasNextThread, !viewModel.isLoading else { return }
        let feedback = UIImpactFeedbackGenerator(style: .light)
        feedback.impactOccurred()
        viewModel.goNext()
    }

    private func scrollToBottom(proxy: ScrollViewProxy) {
        withAnimation {
            proxy.scrollTo("bottom_anchor", anchor: .bottom)
        }
    }

    private var aiSummaryContent: String {
        // Collect full text content for all sites
        let commentsText = viewModel.comments.prefix(25).map { "\($0.author.username): \($0.content)" }.joined(separator: "\n")

        let targetLanguage = LocalizationManager.shared.currentLanguage == "zh" ? "Chinese (Simplified)" : "English"

        return """
        Context: The user is viewing a forum topic.
        Title: \(viewModel.thread.title)

        Original Thread Content:
        \(viewModel.thread.content)

        Comments/Replies (First 25):
        \(commentsText)

        Please provide a concise summary of the discussion based on the content above. The summary MUST be written in \(targetLanguage).
        """
    }
}

// MARK: - Linked Text (makes URLs tappable)

struct LinkedTextView: View {
    let text: String
    @State private var selectedURL: String? = nil

    var body: some View {
        let segments = parseSegments(from: text)

        // Check if it's a single link or single URL
        let linkSegments = segments.filter { if case .link = $0 { return true }; if case .url = $0 { return true }; return false }
        let plainSegments = segments.filter { if case .plain(let t) = $0 { return !t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }; return false }

        if linkSegments.count == 1 && plainSegments.isEmpty {
            // Single link — show as prominent card
            let (url, title): (String, String) = {
                switch linkSegments[0] {
                case .link(let u, let t): return (u, t)
                case .url(let u, let t): return (u, t)
                default: return ("", "")
                }
            }()

            Button(action: { selectedURL = url }) {
                HStack(spacing: 8) {
                    Image(systemName: FeedflowIcon.link)
                        .symbolRenderingMode(.hierarchical)
                        .font(.system(size: 14))
                    Text(title)
                        .font(.body)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                .foregroundColor(.forumAccent)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.forumAccent.opacity(0.08))
                .cornerRadius(10)
            }
            .buttonStyle(.plain)
            .fullScreenCover(item: Binding<URLItem?>(
                get: { selectedURL.map { URLItem(url: $0) } },
                set: { selectedURL = $0?.url }
            )) { item in
                InAppBrowserView(url: item.url)
            }
        } else {
            // Mixed content — inline text with tappable links
            VStack(alignment: .leading, spacing: 4) {
                let textContent = segments.reduce(Text("")) { result, segment in
                    switch segment {
                    case .plain(let content):
                        return result + Text(content)
                            .font(.body)
                            .foregroundColor(.forumTextPrimary.opacity(0.9))
                    case .link(let urlString, let displayText):
                        return result + Text(.init("[\(displayText)](\(urlString))"))
                            .font(.body)
                            .foregroundColor(.forumAccent)
                    case .url(let urlString, let displayText):
                        return result + Text(.init("[\(displayText)](\(urlString))"))
                            .font(.body)
                            .foregroundColor(.forumAccent)
                    }
                }
                textContent
                    .lineSpacing(6)
                    .tint(.forumAccent)
                    .environment(\.openURL, OpenURLAction { url in
                        selectedURL = url.absoluteString
                        return .handled
                    })
            }
            .fullScreenCover(item: Binding<URLItem?>(
                get: { selectedURL.map { URLItem(url: $0) } },
                set: { selectedURL = $0?.url }
            )) { item in
                InAppBrowserView(url: item.url)
            }
        }
    }

    struct URLItem: Identifiable {
        let id = UUID()
        let url: String
    }

    enum TextSegment {
        case plain(String)
        case link(String, String)   // (url, title) from [LINK:url|title]
        case url(String, String)    // (url, displayText) from raw URL detection
    }

    /// Parse [LINK:url|title] markers first, then detect raw URLs in remaining text
    private func parseSegments(from text: String) -> [TextSegment] {
        var segments: [TextSegment] = []

        // Step 1: Split on [LINK:url|title] markers
        let linkPattern = "\\[LINK:([^|\\]]+)\\|([^\\]]+)\\]"
        guard let linkRegex = try? NSRegularExpression(pattern: linkPattern, options: []) else {
            return detectRawURLs(in: text)
        }

        let nsText = text as NSString
        let fullRange = NSRange(location: 0, length: nsText.length)
        let matches = linkRegex.matches(in: text, range: fullRange)

        var lastEnd = 0

        for match in matches {
            // Plain text before this [LINK:]
            if match.range.location > lastEnd {
                let beforeRange = NSRange(location: lastEnd, length: match.range.location - lastEnd)
                let before = nsText.substring(with: beforeRange)
                // Detect raw URLs in the plain text portion
                segments.append(contentsOf: detectRawURLs(in: before))
            }

            // Extract url and title
            if let urlRange = Range(match.range(at: 1), in: text),
               let titleRange = Range(match.range(at: 2), in: text) {
                let url = String(text[urlRange])
                let title = String(text[titleRange])
                segments.append(.link(url, title))
            }

            lastEnd = match.range.location + match.range.length
        }

        // Remaining text after last [LINK:]
        if lastEnd < nsText.length {
            let remaining = nsText.substring(from: lastEnd)
            segments.append(contentsOf: detectRawURLs(in: remaining))
        }

        return segments.isEmpty ? [.plain(text)] : segments
    }

    /// Detect raw URLs (http/https) in plain text using NSDataDetector
    private func detectRawURLs(in text: String) -> [TextSegment] {
        let trimmed = text
        if trimmed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return trimmed.isEmpty ? [] : [.plain(trimmed)]
        }

        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else {
            return [.plain(trimmed)]
        }

        var segments: [TextSegment] = []
        let nsText = trimmed as NSString
        let range = NSRange(location: 0, length: nsText.length)
        let matches = detector.matches(in: trimmed, range: range)

        var lastEnd = 0

        for match in matches {
            if match.range.location > lastEnd {
                let plainRange = NSRange(location: lastEnd, length: match.range.location - lastEnd)
                segments.append(.plain(nsText.substring(with: plainRange)))
            }

            let urlString = nsText.substring(with: match.range)
            segments.append(.url(urlString, urlString))

            lastEnd = match.range.location + match.range.length
        }

        if lastEnd < nsText.length {
            segments.append(.plain(nsText.substring(from: lastEnd)))
        }

        return segments.isEmpty ? [.plain(trimmed)] : segments
    }
}

struct ParsedContentView: View {
    let text: String
    @State private var selectedImageURL: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(parseBlocks(from: text), id: \.self) { block in
                switch block {
                case .text(let content):
                    if !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        LinkedTextView(text: content)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                case .image(let url):
                    AsyncImage(url: URL(string: url)) { phase in
                        switch phase {
                        case .empty:
                            ProgressView()
                                .frame(height: 200)
                        case .success(let image):
                            image.resizable()
                                 .aspectRatio(contentMode: .fit)
                                 .cornerRadius(8)
                                 .onTapGesture(count: 2) {
                                     selectedImageURL = url
                                 }
                        case .failure:
                            EmptyView()
                        @unknown default:
                            EmptyView()
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .fullScreenCover(item: Binding<ImageItem?>(
            get: { selectedImageURL.map { ImageItem(url: $0) } },
            set: { selectedImageURL = $0?.url }
        )) { item in
            FullScreenImageView(imageURL: item.url, isPresented: Binding(
                get: { selectedImageURL != nil },
                set: { if !$0 { selectedImageURL = nil } }
            ))
        }
    }

    struct ImageItem: Identifiable {
        let id = UUID()
        let url: String
    }

    enum ContentBlock: Hashable {
        case text(String)
        case image(String)
    }

    private func parseBlocks(from text: String) -> [ContentBlock] {
        var blocks: [ContentBlock] = []
        var seenImages = Set<String>()
        let components = text.components(separatedBy: "[IMAGE:") // Primitive split

        for (index, component) in components.enumerated() {
            if index == 0 {
                // First part is always text (before the first image)
                let trimmed = component.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    blocks.append(.text(trimmed))
                }
            } else {
                // Subsequent parts start with "url]" followed by text
                // Example: "http://.../img.jpg]\nSome text..."
                if let range = component.range(of: "]") {
                    let url = String(component[..<range.lowerBound])
                    let remainingText = String(component[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
                    let imageKey = normalizedImageKey(url)

                    if !seenImages.contains(imageKey) {
                        seenImages.insert(imageKey)
                        blocks.append(.image(url))
                    }
                    if !remainingText.isEmpty {
                        blocks.append(.text(remainingText))
                    }
                } else {
                    // Fallback
                    let trimmed = ("[IMAGE:" + component).trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty {
                        blocks.append(.text(trimmed))
                    }
                }
            }
        }
        return blocks
    }

    private func normalizedImageKey(_ rawURL: String) -> String {
        var url = rawURL
            .replacingOccurrences(of: "&amp;", with: "&")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if url.hasPrefix("//") {
            url = "https:\(url)"
        }

        guard var components = URLComponents(string: url) else {
            return url.lowercased()
        }

        components.scheme = "https"
        components.query = nil
        components.fragment = nil

        if let host = components.host?.lowercased(), host.hasSuffix("zhimg.com") {
            components.host = "zhimg.com"
        }

        var path = components.path
        path = path.replacingOccurrences(of: "/80/", with: "/")
        path = path.replacingOccurrences(of: "/50/", with: "/")
        path = path.replacingOccurrences(
            of: "_(?:r|b|hd|720w|1080w|1440w|2160w|720w_1l|1080w_1l)\\.(jpg|jpeg|png|webp)$",
            with: ".$1",
            options: [.regularExpression, .caseInsensitive]
        )
        components.path = path

        return (components.string ?? url).lowercased()
    }
}

struct TagView: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.caption)
            .foregroundColor(.forumTextSecondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Color.forumCard)
            .cornerRadius(8)
    }
}

struct CommentRow: View {
    let comment: Comment
    let canReply: Bool
    let onReply: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            AvatarView(urlOrName: comment.author.avatar, size: 32, fallbackText: comment.author.username)

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(comment.author.username)
                        .font(.footnote)
                        .bold()
                        .foregroundColor(.forumTextPrimary)

                    if let role = comment.author.role {
                        TagView(text: role)
                    }

                    if canReply {
                        Button(action: onReply) {
                            Text("reply".localized())
                                .font(.caption)
                                .foregroundColor(.forumAccent) // Accent color for link style
                        }
                    }

                    Spacer()

                    Text(comment.timeAgo)
                        .font(.caption)
                        .foregroundColor(.forumTextSecondary)
                }

                // Use ParsedContentView for comments too
                ParsedContentView(text: comment.content)

            }
        }
        .padding(.vertical, 12)
    }
}
