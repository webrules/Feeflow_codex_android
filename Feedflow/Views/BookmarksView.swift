import SwiftUI

struct BookmarksView: View {
    @StateObject private var viewModel = BookmarksViewModel()
    @Environment(\.dismiss) var dismiss
    @ObservedObject var localizationManager = LocalizationManager.shared

    var body: some View {
        NavigationStack {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                if viewModel.bookmarkedThreads.isEmpty && viewModel.urlBookmarks.isEmpty {
                    VStack {
                        FeedflowSymbol(
                            name: "bookmark.slash.fill",
                            size: 48,
                            color: .forumTextSecondary,
                            background: Color.forumTextSecondary.opacity(0.12),
                            frameSize: 76,
                            shape: .circle
                        )
                        Text("no_bookmarks".localized())
                            .font(.headline)
                            .foregroundColor(.forumTextSecondary)
                    }
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            // Thread bookmarks
                            if !viewModel.bookmarkedThreads.isEmpty {
                                SectionHeader(title: "thread_bookmarks".localized())

                                ForEach(viewModel.bookmarkedThreads, id: \.0.id) { thread, serviceId in
                                    NavigationLink(destination: ThreadDetailView(thread: thread, service: viewModel.getService(for: serviceId))) {
                                        BookmarkRow(thread: thread, serviceName: viewModel.getService(for: serviceId).name)
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                            }

                            // URL bookmarks
                            if !viewModel.urlBookmarks.isEmpty {
                                SectionHeader(title: "url_bookmarks".localized())

                                ForEach(viewModel.urlBookmarks, id: \.0) { url, title, date in
                                    URLBookmarkRow(url: url, title: title, date: date) {
                                        viewModel.removeURLBookmark(url: url)
                                    }
                                }
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("bookmarks".localized())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("close".localized()) { dismiss() }
                        .foregroundColor(.forumAccent)
                }
            }
            .onAppear {
                viewModel.loadBookmarks()
            }
        }
    }
}

struct SectionHeader: View {
    let title: String
    var body: some View {
        HStack {
            Text(title)
                .font(.caption)
                .foregroundColor(.forumTextSecondary)
                .textCase(.uppercase)
            Spacer()
        }
        .padding(.top, 8)
    }
}

struct BookmarkRow: View {
    let thread: Thread
    let serviceName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(serviceName)
                    .font(.caption2)
                    .bold()
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.forumAccent.opacity(0.1))
                    .foregroundColor(.forumAccent)
                    .cornerRadius(4)

                Spacer()

                Text(thread.timeAgo)
                    .font(.caption2)
                    .foregroundColor(.forumTextSecondary)
            }

            Text(thread.title)
                .font(.headline)
                .foregroundColor(.forumTextPrimary)
                .lineLimit(2)

            HStack {
                AvatarView(urlOrName: thread.author.avatar, size: 20, fallbackText: thread.author.username)
                Text(thread.author.username)
                    .font(.caption)
                    .foregroundColor(.forumTextSecondary)

                Spacer()

                HStack(spacing: 12) {
                    Label("\(thread.likeCount)", systemImage: "hand.thumbsup")
                    Label("\(thread.commentCount)", systemImage: FeedflowIcon.comments)
                }
                .font(.caption2)
                .foregroundColor(.forumTextSecondary)
            }
        }
        .padding()
        .background(Color.forumCard)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.gray.opacity(0.1), lineWidth: 1)
        )
    }
}

struct URLBookmarkRow: View {
    let url: String
    let title: String
    let date: Date
    let onDelete: () -> Void
    @State private var showBrowser = false

    var body: some View {
        Button(action: { showBrowser = true }) {
            HStack(spacing: 12) {
                FeedflowSymbol(
                    name: FeedflowIcon.web,
                    size: 18,
                    color: .forumAccent,
                    background: .forumAccentSoft,
                    frameSize: 38
                )

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.forumTextPrimary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    Text(url)
                        .font(.caption2)
                        .foregroundColor(.forumTextSecondary)
                        .lineLimit(1)
                }

                Spacer()

                Text(timeAgo(from: date))
                    .font(.caption2)
                    .foregroundColor(.forumTextSecondary)
            }
            .padding()
            .background(Color.forumCard)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.gray.opacity(0.1), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(role: .destructive, action: onDelete) {
                Label("delete".localized(), systemImage: FeedflowIcon.trash)
            }
        }
        .fullScreenCover(isPresented: $showBrowser) {
            InAppBrowserView(url: url, pageTitle: title)
        }
    }

    private func timeAgo(from date: Date) -> String {
        let diff = Date().timeIntervalSince(date)
        if diff < 60 { return "just now" }
        else if diff < 3600 { return "\(Int(diff / 60))m" }
        else if diff < 86400 { return "\(Int(diff / 3600))h" }
        else { return "\(Int(diff / 86400))d" }
    }
}
