import SwiftUI
import Combine

struct SiteSearchRoute: Hashable {
    let site: ForumSite
    let query: String
}

@MainActor
final class SiteSearchViewModel: ObservableObject {
    @Published private(set) var threads: [Thread] = []
    @Published private(set) var isLoading = false
    @Published private(set) var canLoadMore = false
    @Published private(set) var errorMessage: String?

    let route: SiteSearchRoute
    let service: ForumService
    private var page = 1

    init(route: SiteSearchRoute) {
        self.route = route
        self.service = route.site.makeService()
    }

    var serviceName: String { service.name }

    func loadResults() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let sessionReady = await service.restoreSession()
        guard sessionReady || !service.requiresLogin else {
            errorMessage = "Login is required to search \(serviceName)."
            return
        }

        do {
            let (results, hasMore) = try await service.searchThreads(query: route.query, page: 1)
            threads = results
            page = 1
            canLoadMore = hasMore
            if results.isEmpty {
                errorMessage = "No results found."
            }
        } catch is CancellationError {
            // The view disappeared before the request completed.
        } catch {
            errorMessage = "Search failed. Please try again."
            AppLogger.debug("[SiteSearch] \(serviceName) search failed: \(error)")
        }
    }

    func loadMore() async {
        guard canLoadMore, !isLoading else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            let nextPage = page + 1
            let (results, hasMore) = try await service.searchThreads(query: route.query, page: nextPage)
            let existingIDs = Set(threads.map(\.id))
            threads.append(contentsOf: results.filter { !existingIDs.contains($0.id) })
            page = nextPage
            canLoadMore = hasMore
        } catch is CancellationError {
            // The view disappeared before the request completed.
        } catch {
            AppLogger.debug("[SiteSearch] \(serviceName) pagination failed: \(error)")
        }
    }
}

struct SiteSearchResultsView: View {
    @StateObject private var viewModel: SiteSearchViewModel
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var navigationManager: NavigationManager

    init(route: SiteSearchRoute) {
        _viewModel = StateObject(wrappedValue: SiteSearchViewModel(route: route))
    }

    var body: some View {
        ZStack {
            Color.forumBackground.ignoresSafeArea()

            if viewModel.isLoading && viewModel.threads.isEmpty {
                ProgressView()
                    .tint(.forumAccent)
                    .scaleEffect(1.2)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        searchHeader

                        if viewModel.threads.isEmpty {
                            searchState
                        } else {
                            ForEach(viewModel.threads) { thread in
                                NavigationLink(destination: ThreadDetailView(
                                    thread: thread,
                                    service: viewModel.service,
                                    contextThreads: viewModel.threads
                                )) {
                                    ThreadRow(thread: thread, service: viewModel.service)
                                        .onAppear {
                                            if thread.id == viewModel.threads.last?.id {
                                                Task { await viewModel.loadMore() }
                                            }
                                        }
                                }
                                .buttonStyle(.plain)
                            }

                            if viewModel.isLoading {
                                ProgressView()
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 18)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 12)
                    .padding(.bottom, 18)
                }
                .refreshable {
                    await viewModel.loadResults()
                }
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .bottomBar) {
                HStack(spacing: 12) {
                    ToolbarSymbolButton(name: FeedflowIcon.back) {
                        dismiss()
                    }

                    Text(viewModel.serviceName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.forumTextPrimary)
                        .lineLimit(1)

                    Spacer()

                    ToolbarSymbolButton(name: FeedflowIcon.refresh, isActive: !viewModel.isLoading) {
                        Task { await viewModel.loadResults() }
                    }

                    ToolbarSymbolButton(name: FeedflowIcon.home) {
                        navigationManager.popToRoot()
                    }
                }
            }
        }
        .toolbarBackground(Color.forumBackground, for: .bottomBar)
        .toolbarBackground(.visible, for: .bottomBar)
        .simultaneousGesture(
            DragGesture(minimumDistance: 24)
                .onEnded { value in
                    let horizontalSwipe = value.translation.width > 80
                    let isPrimarilyHorizontal = abs(value.translation.width) > abs(value.translation.height) * 1.5
                    if horizontalSwipe && isPrimarilyHorizontal {
                        navigationManager.popToRoot()
                    }
                }
        )
        .task {
            await viewModel.loadResults()
        }
    }

    private var searchHeader: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Search results")
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(.forumTextPrimary)

            Text("\"\(viewModel.route.query)\"")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.forumTextSecondary)
                .lineLimit(2)
        }
        .padding(.vertical, 6)
    }

    @ViewBuilder
    private var searchState: some View {
        VStack(spacing: 10) {
            FeedflowSymbol(
                name: "magnifyingglass",
                size: 22,
                color: .forumTextSecondary,
                background: .forumInputBackground,
                frameSize: 48,
                shape: .circle
            )

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.forumTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 56)
    }
}
