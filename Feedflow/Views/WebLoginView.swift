import SwiftUI
import UIKit
import WebKit

// MARK: - Login Configuration per Site

struct SiteLoginConfig {
    let site: ForumSite
    let loginURL: String
    let successURLPatterns: [String]  // URL patterns that indicate successful login
    let oauthOptions: [OAuthOption]
    let cookieDomain: String
    let authCookieNameFragments: [String]

    struct OAuthOption: Identifiable {
        let id = UUID()
        let name: String
        let icon: String      // SF Symbol name
        let loginPath: String  // Path appended to base URL
    }

    // Optional check: success only if this cookie is present
    let requiredCookieName: String?

    init(
        site: ForumSite,
        loginURL: String,
        successURLPatterns: [String],
        oauthOptions: [OAuthOption],
        cookieDomain: String,
        authCookieNameFragments: [String] = [],
        requiredCookieName: String? = nil
    ) {
        self.site = site
        self.loginURL = loginURL
        self.successURLPatterns = successURLPatterns
        self.oauthOptions = oauthOptions
        self.cookieDomain = cookieDomain
        self.authCookieNameFragments = authCookieNameFragments
        self.requiredCookieName = requiredCookieName
    }

    func siteCookies(from cookies: [HTTPCookie]) -> [HTTPCookie] {
        cookies.filter { $0.domain.contains(cookieDomain) }
    }

    func hasAuthenticatedSession(in cookies: [HTTPCookie]) -> Bool {
        let relevantCookies = siteCookies(from: cookies)

        if let requiredCookieName {
            return relevantCookies.contains { $0.name == requiredCookieName }
        }

        if authCookieNameFragments.isEmpty {
            return !relevantCookies.isEmpty
        }

        return relevantCookies.contains { cookie in
            let normalizedName = cookie.name.lowercased()
            return authCookieNameFragments.contains { normalizedName.contains($0.lowercased()) }
        }
    }

    func isSuccessURL(_ urlString: String) -> Bool {
        successURLPatterns.contains { pattern in
            urlString.contains(pattern)
        }
    }

    func shouldCheckCookies(for url: URL?) -> Bool {
        guard let host = url?.host?.lowercased() else {
            return false
        }

        let normalizedDomain = cookieDomain.lowercased()
        return host == normalizedDomain || host.hasSuffix(".\(normalizedDomain)")
    }
}

extension SiteLoginConfig {
    static func config(for site: ForumSite) -> SiteLoginConfig? {
        switch site {
        case .rss:
            return nil  // RSS doesn't need login

        case .fourD4Y:
            return SiteLoginConfig(
                site: .fourD4Y,
                loginURL: "https://www.4d4y.com/forum/logging.php?action=login",
                successURLPatterns: ["4d4y.com/forum/index.php", "4d4y.com/forum/forumdisplay", "4d4y.com/forum/viewthread"],
                oauthOptions: [],
                cookieDomain: "4d4y.com",
                authCookieNameFragments: ["auth", "login", "member"]
            )

        case .hackerNews:
            return SiteLoginConfig(
                site: .hackerNews,
                loginURL: "https://news.ycombinator.com/login",
                successURLPatterns: ["news.ycombinator.com/news", "news.ycombinator.com/newest"],
                oauthOptions: [],
                cookieDomain: "ycombinator.com",
                authCookieNameFragments: ["user"]
            )

        case .v2ex:
            return SiteLoginConfig(
                site: .v2ex,
                loginURL: "https://v2ex.com/signin",
                successURLPatterns: ["v2ex.com/?tab", "v2ex.com/#", "v2ex.com/member/"],
                oauthOptions: [
                    .init(name: "Google", icon: "g.circle.fill", loginPath: "https://v2ex.com/auth/google"),
                    .init(name: "Solana", icon: "wallet.pass.fill", loginPath: "https://v2ex.com/auth/solana"),
                ],
                cookieDomain: "v2ex.com",
                authCookieNameFragments: ["a2"]
            )

        case .linuxDo:
            return SiteLoginConfig(
                site: .linuxDo,
                loginURL: "https://linux.do/login",
                successURLPatterns: ["linux.do/latest", "linux.do/top", "linux.do/categories"],
                oauthOptions: [
                    .init(name: "Google", icon: "g.circle.fill", loginPath: "https://linux.do/auth/google_oauth2"),
                    .init(name: "GitHub", icon: "chevron.left.forwardslash.chevron.right", loginPath: "https://linux.do/auth/github"),
                    .init(name: "X", icon: "xmark", loginPath: "https://linux.do/auth/twitter"),
                    .init(name: "Discord", icon: "bubble.left.and.bubble.right.fill", loginPath: "https://linux.do/auth/discord"),
                    .init(name: "Apple", icon: "apple.logo", loginPath: "https://linux.do/auth/apple"),
                    .init(name: "Passkey", icon: "person.badge.key.fill", loginPath: "https://linux.do/session/passkey/challenge"),
                ],
                cookieDomain: "linux.do",
                authCookieNameFragments: ["_t", "remember_user_token"]
            )

        case .zhihu:
            return SiteLoginConfig(
                site: .zhihu,
                loginURL: "https://www.zhihu.com/signin",
                // Re-adding generic patterns but relying on requiredCookieName for safety
                successURLPatterns: ["zhihu.com/hot", "zhihu.com/follow", "zhihu.com/people", "zhihu.com/?tab", "zhihu.com/question", "www.zhihu.com", "zhihu.com"],
                oauthOptions: [],
                cookieDomain: "zhihu.com",
                requiredCookieName: "z_c0"  // Critical for Zhihu auth
            )
        }
    }
}

// MARK: - WebView Login (handles Captcha, OAuth, etc.)

struct WebLoginView: UIViewRepresentable {
    let config: SiteLoginConfig
    let onLoginSuccess: ([HTTPCookie]) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(config: config, onLoginSuccess: onLoginSuccess)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        // Use default data store so OAuth redirects through third-party domains work
        configuration.websiteDataStore = .default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = true

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .always

        // Use a Safari-like user agent to avoid Google's "disallowed_useragent" block
        // Google blocks OAuth from embedded WKWebViews with the default UA
        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

        if let url = URL(string: config.loginURL) {
            webView.load(URLRequest(url: url))
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        let config: SiteLoginConfig
        let onLoginSuccess: ([HTTPCookie]) -> Void
        private var didReportSuccess = false

        init(config: SiteLoginConfig, onLoginSuccess: @escaping ([HTTPCookie]) -> Void) {
            self.config = config
            self.onLoginSuccess = onLoginSuccess
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard let currentURL = webView.url?.absoluteString else { return }
            AppLogger.debug("[WebLogin] Finished navigation on \(config.site.makeService().id): \(currentURL)")

            let isSuccess = config.isSuccessURL(currentURL)

            if isSuccess {
                checkCookiesWithRetry(webView: webView, retries: 8)
            } else if config.shouldCheckCookies(for: webView.url) {
                checkCookiesWithRetry(webView: webView, retries: 3)
            }
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if let urlString = navigationAction.request.url?.absoluteString {
                AppLogger.debug("[WebLogin] Navigation requested on \(config.site.makeService().id): \(urlString), newWindow=\(navigationAction.targetFrame == nil)")
            }

            decisionHandler(.allow)
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            guard navigationAction.targetFrame == nil else {
                return nil
            }

            AppLogger.debug("[WebLogin] Creating popup web view for \(config.site.makeService().id)")
            configuration.websiteDataStore = .default()
            configuration.preferences.javaScriptCanOpenWindowsAutomatically = true

            let popupWebView = WKWebView(frame: .zero, configuration: configuration)
            popupWebView.navigationDelegate = self
            popupWebView.uiDelegate = self
            popupWebView.customUserAgent = webView.customUserAgent
            popupWebView.allowsBackForwardNavigationGestures = true
            popupWebView.scrollView.contentInsetAdjustmentBehavior = .always
            popupWebView.translatesAutoresizingMaskIntoConstraints = false

            let closeButton = UIButton(type: .system)
            closeButton.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal)
            closeButton.tintColor = .secondaryLabel
            closeButton.backgroundColor = .systemBackground.withAlphaComponent(0.85)
            closeButton.layer.cornerRadius = 18
            closeButton.translatesAutoresizingMaskIntoConstraints = false
            closeButton.addAction(UIAction { [weak popupWebView] _ in
                popupWebView?.removeFromSuperview()
            }, for: .touchUpInside)

            webView.addSubview(popupWebView)
            popupWebView.addSubview(closeButton)

            NSLayoutConstraint.activate([
                popupWebView.leadingAnchor.constraint(equalTo: webView.leadingAnchor),
                popupWebView.trailingAnchor.constraint(equalTo: webView.trailingAnchor),
                popupWebView.topAnchor.constraint(equalTo: webView.topAnchor),
                popupWebView.bottomAnchor.constraint(equalTo: webView.bottomAnchor),

                closeButton.topAnchor.constraint(equalTo: popupWebView.safeAreaLayoutGuide.topAnchor, constant: 10),
                closeButton.trailingAnchor.constraint(equalTo: popupWebView.safeAreaLayoutGuide.trailingAnchor, constant: -10),
                closeButton.widthAnchor.constraint(equalToConstant: 36),
                closeButton.heightAnchor.constraint(equalToConstant: 36)
            ])

            return popupWebView
        }

        func webViewDidClose(_ webView: WKWebView) {
            AppLogger.debug("[WebLogin] Popup web view closed for \(config.site.makeService().id)")
            checkCookiesWithRetry(webView: webView, retries: 3)
            webView.removeFromSuperview()
        }

        func checkCookiesWithRetry(webView: WKWebView, retries: Int) {
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
                guard !self.didReportSuccess else { return }

                guard self.config.hasAuthenticatedSession(in: cookies) else {
                    AppLogger.debug("[WebLogin] Authenticated cookie is missing. Retries left: \(retries)")
                    if retries > 0 {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                            self.checkCookiesWithRetry(webView: webView, retries: retries - 1)
                        }
                    }
                    return
                }

                let siteCookies = self.config.siteCookies(from: cookies)
                AppLogger.debug("[WebLogin] Authenticated session detected for \(self.config.site.makeService().id): \(siteCookies.count) cookies")
                self.didReportSuccess = true

                // Fire the success callback so cookies get persisted to the app database.
                // Without this, cookies remain only in WKWebView and are never saved,
                // causing the community list to appear empty after login.
                DispatchQueue.main.async {
                    self.onLoginSuccess(siteCookies)
                }
            }
        }
    }
}

// MARK: - Web Login Sheet View

struct WebLoginSheetView: View {
    let config: SiteLoginConfig
    let onSuccess: ([HTTPCookie]) -> Void
    @Environment(\.dismiss) var dismiss
    @State private var isLoggedIn = false

    var body: some View {
        NavigationView {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                if isLoggedIn {
                    VStack(spacing: 16) {
                        FeedflowSymbol(
                            name: "checkmark.circle.fill",
                            size: 46,
                            color: .green,
                            background: Color.green.opacity(0.12),
                            frameSize: 82,
                            shape: .circle
                        )
                        Text("login_success".localized())
                            .font(.headline)
                            .foregroundColor(.forumTextPrimary)
                    }
                } else {
                    WebLoginView(config: config) { cookies in
                        isLoggedIn = true
                        onSuccess(cookies)
                    }
                }
            }
            .navigationTitle(config.site.makeService().name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .bottomBar) {
                    HStack {
                        Button((isLoggedIn ? "done" : "cancel").localized()) { dismiss() }
                            .foregroundColor(.forumAccent)

                        Spacer()

                        Button("save_session".localized()) {
                            saveCurrentSession()
                        }
                        .foregroundColor(.forumAccent)
                        .disabled(isLoggedIn)
                    }
                }
            }
            .toolbarBackground(Color.forumBackground, for: .bottomBar)
            .toolbarBackground(.visible, for: .bottomBar)
        }
    }

    private func saveCurrentSession() {
        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
            let siteCookies = config.siteCookies(from: cookies)
            AppLogger.debug("[WebLogin] Manual save for \(config.site.makeService().id): \(siteCookies.count) site cookies")
            guard !siteCookies.isEmpty else { return }

            DispatchQueue.main.async {
                isLoggedIn = true
                onSuccess(siteCookies)
            }
        }
    }
}
