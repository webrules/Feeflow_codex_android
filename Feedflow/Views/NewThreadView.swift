import SwiftUI

struct NewThreadView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: NewThreadViewModel
    @ObservedObject var localizationManager = LocalizationManager.shared
    @State private var postErrorMessage: String?

    init(category: Community, service: ForumService) {
        _viewModel = StateObject(wrappedValue: NewThreadViewModel(category: category, service: service))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                VStack(alignment: .leading) {
                    if viewModel.isPosting {
                        LinearProgressView()
                    }

                    // Inputs
                    VStack(alignment: .leading, spacing: 0) {
                        TextField("thread_title".localized(), text: $viewModel.title)
                            .font(.title2)
                            .bold()
                            .foregroundColor(.forumTextPrimary)
                            .padding(.vertical)
                            .submitLabel(.next)

                        ZStack(alignment: .topLeading) {
                            if viewModel.content.isEmpty {
                                Text("share_thoughts".localized())
                                    .foregroundColor(.forumTextSecondary)
                                    .padding(.top, 8)
                            }
                            TextEditor(text: $viewModel.content)
                                .scrollContentBackground(.hidden)
                                .background(Color.clear)
                                .foregroundColor(.forumTextPrimary)
                                .font(.body)
                        }
                    }
                    .padding()

                    Spacer()

                    // Attachments (Placeholder)
                    VStack(alignment: .leading) {
                        HStack {
                            Text("attachments_header".localized())
                                .font(.caption)
                                .bold()
                                .foregroundColor(.forumTextSecondary)
                            Spacer()
                            Button(action: {}) {
                                Label("add_images".localized(), systemImage: "photo.badge.plus")
                                    .font(.caption)
                                    .foregroundColor(.forumAccent)
                            }
                        }
                        .padding(.horizontal)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(0..<3) { index in
                                    ZStack(alignment: .topTrailing) {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.forumCard)
                                            .frame(width: 120, height: 120)
                                            .overlay(
                                                FeedflowSymbol(name: "photo.on.rectangle.angled", size: 24, color: .forumTextSecondary)
                                            )

                                        Button(action: {}) {
                                            FeedflowSymbol(name: FeedflowIcon.close, size: 18, color: .forumTextSecondary)
                                                .background(Circle().fill(Color.forumCard))
                                        }
                                        .offset(x: 5, y: -5)
                                    }
                                }
                            }
                            .padding(.horizontal)
                            .padding(.bottom)
                        }
                    }

                    // Toolbar at bottom
                    HStack(spacing: 24) {
                        Button(action: {}) { FeedflowSymbol(name: "bold", size: 16, color: .forumTextSecondary) }
                        Button(action: {}) { FeedflowSymbol(name: "italic", size: 16, color: .forumTextSecondary) }
                        Button(action: {}) { FeedflowSymbol(name: FeedflowIcon.link, size: 16, color: .forumTextSecondary) }
                        Button(action: {}) { FeedflowSymbol(name: "list.bullet", size: 16, color: .forumTextSecondary) }

                        Spacer()

                        Text(LocalizationManager.shared.localizedString("word_count", viewModel.content.count))
                            .font(.caption)
                            .foregroundColor(.forumTextSecondary)
                    }
                    .foregroundColor(.forumTextSecondary)
                    .padding()
                    .background(Color.forumCard)
                }
            }
            .navigationTitle("new_thread".localized())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .bottomBar) {
                    HStack {
                        Button("cancel".localized()) { dismiss() }
                            .foregroundColor(.forumAccent)

                        Spacer()

                        Button(action: {
                            Task {
                                do {
                                    try await viewModel.postThread()
                                    dismiss()
                                } catch {
                                    postErrorMessage = error.localizedDescription
                                }
                            }
                        }) {
                            Text("thread_button".localized())
                                .font(.headline)
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(viewModel.title.isEmpty || viewModel.content.isEmpty ? Color.gray : Color.forumAccent)
                                .cornerRadius(20)
                        }
                        .disabled(viewModel.title.isEmpty || viewModel.content.isEmpty || viewModel.isPosting)
                    }
                }
            }
            .toolbarBackground(Color.forumBackground, for: .navigationBar)
            .toolbarBackground(Color.forumBackground, for: .bottomBar)
            .toolbarBackground(.visible, for: .bottomBar)
            .alert("post_failed".localized(), isPresented: Binding(
                get: { postErrorMessage != nil },
                set: { if !$0 { postErrorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {
                    postErrorMessage = nil
                }
            } message: {
                Text(postErrorMessage ?? "")
            }
        }
    }
}

struct LinearProgressView: View {
    var body: some View {
        ProgressView()
            .progressViewStyle(LinearProgressViewStyle(tint: .forumAccent))
            .frame(height: 2)
    }
}
