import SwiftUI
import WebKit
import Combine

struct InAppBrowserView: View {
    let url: String
    let pageTitle: String?
    @Environment(\.dismiss) var dismiss
    @ObservedObject var localizationManager = LocalizationManager.shared
    @StateObject private var webViewModel = WebViewModel()
    @State private var isBookmarked = false

    init(url: String, pageTitle: String? = nil) {
        self.url = url
        self.pageTitle = pageTitle
    }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // WebView
                BrowserWebView(viewModel: webViewModel, urlString: url)

                // Bottom toolbar
                bottomToolbar
            }
            .background(Color.forumBackground)
            .navigationTitle(webViewModel.title.isEmpty ? (pageTitle ?? "browser".localized()) : webViewModel.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("done".localized()) { dismiss() }
                        .foregroundColor(.forumAccent)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 16) {
                        // Bookmark
                        Button(action: toggleBookmark) {
                            FeedflowSymbol(
                                name: isBookmarked ? FeedflowIcon.bookmarkFill : FeedflowIcon.bookmark,
                                size: 18,
                                color: isBookmarked ? .forumAccent : .forumTextPrimary
                            )
                        }
                        // Share
                        ShareLink(item: URL(string: webViewModel.currentURL ?? url) ?? URL(string: url)!) {
                            FeedflowSymbol(name: FeedflowIcon.share, size: 18, color: .forumTextPrimary)
                        }
                    }
                }
            }
            .onAppear {
                isBookmarked = DatabaseManager.shared.isURLBookmarked(url: url)
            }
        }
    }

    private var bottomToolbar: some View {
        HStack {
            // Back
            ToolbarSymbolButton(name: FeedflowIcon.back, isActive: webViewModel.canGoBack) {
                webViewModel.goBack()
            }

            Spacer()

            // Forward
            ToolbarSymbolButton(name: FeedflowIcon.forward, isActive: webViewModel.canGoForward) {
                webViewModel.goForward()
            }

            Spacer()

            // Reload / Stop
            Button(action: {
                if webViewModel.isLoading {
                    webViewModel.stopLoading()
                } else {
                    webViewModel.reload()
                }
            }) {
                FeedflowSymbol(
                    name: webViewModel.isLoading ? "xmark" : FeedflowIcon.refresh,
                    size: 17,
                    color: .forumAccent,
                    background: Color.forumAccent.opacity(0.12),
                    frameSize: 34,
                    shape: .circle
                )
            }

            Spacer()

            // Open in Safari
            Button(action: {
                if let currentURL = webViewModel.currentURL, let url = URL(string: currentURL) {
                    UIApplication.shared.open(url)
                }
            }) {
                FeedflowSymbol(
                    name: FeedflowIcon.browser,
                    size: 17,
                    color: .forumAccent,
                    background: Color.forumAccent.opacity(0.12),
                    frameSize: 34,
                    shape: .circle
                )
            }
        }
        .padding(.horizontal, 30)
        .padding(.vertical, 10)
        .background(Color.forumCard)
        .overlay(
            Divider(), alignment: .top
        )
    }

    private func toggleBookmark() {
        let currentURL = webViewModel.currentURL ?? url
        let title = webViewModel.title.isEmpty ? (pageTitle ?? currentURL) : webViewModel.title

        if isBookmarked {
            DatabaseManager.shared.removeURLBookmark(url: currentURL)
        } else {
            DatabaseManager.shared.saveURLBookmark(url: currentURL, title: title)
        }
        isBookmarked.toggle()
    }
}

// MARK: - WebView ViewModel

class WebViewModel: ObservableObject {
    @Published var title: String = ""
    @Published var currentURL: String?
    @Published var canGoBack: Bool = false
    @Published var canGoForward: Bool = false
    @Published var isLoading: Bool = false

    weak var webView: WKWebView?

    func goBack() { webView?.goBack() }
    func goForward() { webView?.goForward() }
    func reload() { webView?.reload() }
    func stopLoading() { webView?.stopLoading() }
}

// MARK: - WKWebView Wrapper

struct BrowserWebView: UIViewRepresentable {
    @ObservedObject var viewModel: WebViewModel
    let urlString: String

    func makeCoordinator() -> Coordinator {
        Coordinator(viewModel: viewModel)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .always

        // Safari-like user agent (avoids blocks from Google OAuth etc.)
        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

        viewModel.webView = webView

        if let url = URL(string: urlString) {
            webView.load(URLRequest(url: url))
        }

        // Observe properties
        context.coordinator.observe(webView)

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    class Coordinator: NSObject, WKNavigationDelegate {
        let viewModel: WebViewModel
        private var observations: [NSKeyValueObservation] = []

        init(viewModel: WebViewModel) {
            self.viewModel = viewModel
        }

        func observe(_ webView: WKWebView) {
            observations = [
                webView.observe(\.title) { [weak self] wv, _ in
                    DispatchQueue.main.async { self?.viewModel.title = wv.title ?? "" }
                },
                webView.observe(\.url) { [weak self] wv, _ in
                    DispatchQueue.main.async { self?.viewModel.currentURL = wv.url?.absoluteString }
                },
                webView.observe(\.canGoBack) { [weak self] wv, _ in
                    DispatchQueue.main.async { self?.viewModel.canGoBack = wv.canGoBack }
                },
                webView.observe(\.canGoForward) { [weak self] wv, _ in
                    DispatchQueue.main.async { self?.viewModel.canGoForward = wv.canGoForward }
                },
                webView.observe(\.isLoading) { [weak self] wv, _ in
                    DispatchQueue.main.async { self?.viewModel.isLoading = wv.isLoading }
                },
            ]
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            decisionHandler(.allow)
        }
    }
}
