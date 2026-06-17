import SwiftUI
import WebKit

struct LoginView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var navigationManager: NavigationManager
    @ObservedObject var localizationManager = LocalizationManager.shared

    // Sites that support login (excludes RSS)
    private let loginSites: [ForumSite] = [.hackerNews, .fourD4Y, .v2ex, .linuxDo, .zhihu]

    @State private var selectedSite: ForumSite
    @State private var showWebLogin = false
    @State private var oauthOverrideURL: String? = nil  // When set, overrides the default login URL
    @State private var loginStatus: [String: Bool] = [:]
    @State private var isCompletingLogin = false

    init(initialSite: ForumSite? = nil) {
        _selectedSite = State(initialValue: initialSite ?? .fourD4Y)
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        siteSelector
                        loginOptionsForSelectedSite
                    }
                    .padding()
                }
            }
            .navigationTitle("login".localized())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .bottomBar) {
                    Button("cancel".localized()) { dismiss() }
                        .foregroundColor(.forumAccent)
                }
            }
            .toolbarBackground(Color.forumBackground, for: .bottomBar)
            .toolbarBackground(.visible, for: .bottomBar)
            .sheet(isPresented: $showWebLogin, onDismiss: {
                oauthOverrideURL = nil  // Reset override when sheet closes
                loadLoginStatuses()
            }) {
                if let baseConfig = SiteLoginConfig.config(for: selectedSite) {
                    let effectiveConfig: SiteLoginConfig = {
                        if let overrideURL = oauthOverrideURL {
                            return SiteLoginConfig(
                                site: baseConfig.site,
                                loginURL: overrideURL,
                                successURLPatterns: baseConfig.successURLPatterns,
                                oauthOptions: baseConfig.oauthOptions,
                                cookieDomain: baseConfig.cookieDomain,
                                authCookieNameFragments: baseConfig.authCookieNameFragments,
                                requiredCookieName: baseConfig.requiredCookieName
                            )
                        }
                        return baseConfig
                    }()
                    WebLoginSheetView(config: effectiveConfig) { cookies in
                        handleLoginSuccess(site: selectedSite, cookies: cookies)
                    }
                }
            }
            .onAppear {
                loadLoginStatuses()
            }
        }
    }

    // MARK: - Site Selector

    private var siteSelector: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("select_site".localized())
                .font(.caption)
                .foregroundColor(.forumTextSecondary)
                .textCase(.uppercase)

            HStack(spacing: 8) {
                ForEach(loginSites) { site in
                    Button(action: {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedSite = site
                        }
                    }) {
                        let isLoggedIn = loginStatus[site.makeService().id] == true

                        VStack(spacing: 6) {
                            ZStack(alignment: .topTrailing) {
                                SiteIcon(service: site.makeService(), size: 44)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(selectedSite == site ? Color.forumAccent : Color.clear, lineWidth: 2)
                                    )

                                FeedflowSymbol(
                                    name: isLoggedIn ? "checkmark.circle.fill" : "circle",
                                    size: 12,
                                    color: isLoggedIn ? .green : .forumTextSecondary.opacity(0.55),
                                    background: .forumCard,
                                    frameSize: 18,
                                    shape: .circle
                                )
                                .offset(x: 4, y: -4)
                            }

                            Text(siteShortName(site))
                                .font(.system(size: 11, weight: .medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .foregroundColor(
                            selectedSite == site
                            ? .forumAccent
                            : .forumTextSecondary
                        )
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
            .background(Color.forumCard)
            .cornerRadius(16)
        }
    }

    // MARK: - Login Options

    private var loginOptionsForSelectedSite: some View {
        VStack(spacing: 16) {
            if let config = SiteLoginConfig.config(for: selectedSite) {
                let service = selectedSite.makeService()
                let isLoggedIn = loginStatus[service.id] == true

                if isLoggedIn {
                    signedInCard(config: config)
                } else {
                    combinedLoginCard(config: config)
                }

                if !config.oauthOptions.isEmpty {
                    oauthSection(config: config)
                }

                loginNote
            }
        }
    }

    private func signedInCard(config: SiteLoginConfig) -> some View {
        let service = config.site.makeService()

        return HStack(spacing: 12) {
            FeedflowSymbol(
                name: "checkmark.seal.fill",
                size: 18,
                color: .green,
                background: Color.green.opacity(0.12),
                frameSize: 40,
                shape: .circle
            )

            VStack(alignment: .leading, spacing: 2) {
                Text("signed_in".localized())
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.forumTextPrimary)
                Text(service.name)
                    .font(.system(size: 12))
                    .foregroundColor(.forumTextSecondary)
            }

            Spacer()

            Button(action: { showWebLogin = true }) {
                FeedflowSymbol(name: "arrow.up.forward.app.fill", size: 18, color: .forumAccent)
            }
            .buttonStyle(.plain)

            Button(action: {
                logout(site: selectedSite)
            }) {
                HStack(spacing: 6) {
                    FeedflowSymbol(name: "rectangle.portrait.and.arrow.right", size: 14, color: .red)
                    Text("logout".localized())
                        .font(.system(size: 14, weight: .semibold))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.red.opacity(0.10))
                .cornerRadius(10)
            }
            .buttonStyle(.plain)
        }
        .padding()
        .background(Color.forumCard)
        .cornerRadius(12)
    }

    private func combinedLoginCard(config: SiteLoginConfig) -> some View {
        let service = config.site.makeService()

        return Button(action: {
            oauthOverrideURL = nil
            showWebLogin = true
        }) {
            HStack(spacing: 12) {
                FeedflowSymbol(
                    name: "person.crop.circle.badge.exclamationmark",
                    size: 18,
                    color: .forumTextSecondary,
                    background: Color.forumTextSecondary.opacity(0.12),
                    frameSize: 40,
                    shape: .circle
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text("signed_out".localized())
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.forumTextPrimary)
                    Text(LocalizationManager.shared.localizedString("login_to_site") + " " + service.name)
                        .font(.system(size: 12))
                        .foregroundColor(.forumTextSecondary)
                }

                Spacer()

                FeedflowSymbol(name: "arrow.up.forward.app.fill", size: 18, color: .forumAccent)
            }
            .padding()
            .background(Color.forumCard)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.forumAccent.opacity(0.3), lineWidth: 1)
            )
        }
        .foregroundColor(.forumTextPrimary)
        .buttonStyle(.plain)
    }



    private func oauthSection(config: SiteLoginConfig) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack { Divider() }
                Text("login_or".localized())
                    .font(.caption)
                    .foregroundColor(.forumTextSecondary)
                VStack { Divider() }
            }

            Text("login_with_provider".localized())
                .font(.caption)
                .foregroundColor(.forumTextSecondary)
                .textCase(.uppercase)

            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 10) {
                ForEach(config.oauthOptions) { option in
                    Button(action: {
                        oauthOverrideURL = option.loginPath
                        showWebLogin = true
                    }) {
                        HStack(spacing: 8) {
                            FeedflowSymbol(name: option.icon, size: 16, color: .forumAccent)
                            Text(option.name)
                                .font(.system(size: 14, weight: .medium))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.forumCard)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.gray.opacity(0.2), lineWidth: 1)
                        )
                    }
                    .foregroundColor(.forumTextPrimary)
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var loginNote: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("note".localized())
                .font(.caption)
                .foregroundColor(.forumTextSecondary)
                .textCase(.uppercase)

            Text("login_web_note".localized())
                .font(.caption)
                .foregroundColor(.forumTextSecondary)
                .padding()
                .background(Color.forumCard)
                .cornerRadius(10)
        }
    }

    // MARK: - Actions

    private func handleLoginSuccess(site: ForumSite, cookies: [HTTPCookie]) {
        let siteId = site.makeService().id  // Use service ID, not enum rawValue

        // Filter to only save cookies for the relevant domain
        // WKWebView's getAllCookies returns cookies from ALL domains (Google, Cloudflare, etc.)
        let domainFilter = siteDomain(site)
        let relevantCookies = cookies.filter { cookie in
            cookie.domain.contains(domainFilter)
        }

        print("[Login] Total cookies from WKWebView: \(cookies.count)")
        print("[Login] Relevant cookies for \(siteId) (domain: \(domainFilter)): \(relevantCookies.count)")
        for cookie in relevantCookies {
            print("[Login]   - \(cookie.name) = \(cookie.value.prefix(20))... (domain: \(cookie.domain), path: \(cookie.path), expires: \(cookie.expiresDate?.description ?? "session"))")
        }

        guard !relevantCookies.isEmpty else {
            print("[Login] No relevant cookies found for \(siteId); leaving existing session unchanged.")
            loginStatus[siteId] = false
            return
        }

        guard isAuthenticatedCookieSet(site: site, cookies: relevantCookies) else {
            print("[Login] Cookie set for \(siteId) is missing an authentication cookie; leaving existing session unchanged.")
            loginStatus[siteId] = DatabaseManager.shared.hasCookies(siteId: siteId)
            return
        }

        // Critical check for Zhihu authentication token
        if site == .zhihu && !relevantCookies.contains(where: { $0.name == "z_c0" }) {
            print("[Login] CRITICAL WARNING: Zhihu login success but 'z_c0' cookie is missing! API requests will likely fail.")
        }

        // Make session-only cookies persistent (30 days) so they survive app restarts.
        // Discuz's cdb_auth is set as session cookie but should persist with "cookietime".
        let persistentCookies = relevantCookies.map { cookie -> HTTPCookie in
            if cookie.expiresDate != nil { return cookie }
            var props = cookie.properties ?? [:]
            props[.expires] = Date().addingTimeInterval(30 * 24 * 3600)
            return HTTPCookie(properties: props) ?? cookie
        }

        DatabaseManager.shared.replaceCookies(siteId: siteId, cookies: persistentCookies)

        // Verify cookies were persisted
        let verified = DatabaseManager.shared.getCookies(siteId: siteId) ?? []
        print("[Login] Verification: \(verified.count) cookies readable from DB for \(siteId)")

        replaceRuntimeCookies(domainFilter: domainFilter, cookies: persistentCookies)

        // Verify the session is actually usable by calling restoreSession.
        // Don't set loginStatus to false first — just let the async verification update it.
        Task {
            let sessionValid = await site.makeService().restoreSession()
            await MainActor.run {
                loginStatus[siteId] = sessionValid
                if !sessionValid {
                    print("[Login] WARNING: Session verification FAILED after login for \(siteId)")
                    isCompletingLogin = false
                    return
                }

                guard !isCompletingLogin else { return }
                isCompletingLogin = true

                // Auto-close login flows and navigate to selected site's categories.
                showWebLogin = false
                dismiss()

                Task {
                    try? await Task.sleep(nanoseconds: 250_000_000)
                    await MainActor.run {
                        navigationManager.popToRoot()
                        navigationManager.path.append(site)
                    }
                }
            }
        }
    }

    private func siteDomain(_ site: ForumSite) -> String {
        switch site {
        case .fourD4Y: return "4d4y.com"
        case .hackerNews: return "ycombinator.com"
        case .v2ex: return "v2ex.com"
        case .linuxDo: return "linux.do"
        case .zhihu: return "zhihu.com"
        case .rss: return ""
        }
    }

    private func isAuthenticatedCookieSet(site: ForumSite, cookies: [HTTPCookie]) -> Bool {
        switch site {
        case .fourD4Y:
            return cookies.contains { cookie in
                let name = cookie.name.lowercased()
                return name.contains("auth") || name.contains("login") || name.contains("member")
            }
        case .zhihu:
            return cookies.contains { $0.name == "z_c0" }
        case .linuxDo:
            return cookies.contains { $0.name.lowercased().contains("_t") }
        case .hackerNews:
            return cookies.contains { $0.name.lowercased().contains("user") }
        case .v2ex:
            return cookies.contains { $0.name.lowercased().contains("a2") }
        case .rss:
            return true
        }
    }

    private func logout(site: ForumSite) {
        let siteId = site.makeService().id
        let domainFilter = siteDomain(site)

        DatabaseManager.shared.clearCookies(siteId: siteId)
        DatabaseManager.shared.removeSetting(key: "login_\(siteId)_username")
        DatabaseManager.shared.removeSetting(key: "login_\(siteId)_password")

        let systemCookies = HTTPCookieStorage.shared.cookies ?? []
        for cookie in systemCookies where cookie.domain.contains(domainFilter) {
            HTTPCookieStorage.shared.deleteCookie(cookie)
        }

        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
            let matchingCookies = cookies.filter { $0.domain.contains(domainFilter) }
            for cookie in matchingCookies {
                WKWebsiteDataStore.default().httpCookieStore.delete(cookie)
            }

            DispatchQueue.main.async {
                loginStatus[siteId] = false
            }
        }

        loginStatus[siteId] = false
    }

    private func replaceRuntimeCookies(domainFilter: String, cookies: [HTTPCookie]) {
        let systemCookies = HTTPCookieStorage.shared.cookies ?? []
        for cookie in systemCookies where cookie.domain.contains(domainFilter) {
            HTTPCookieStorage.shared.deleteCookie(cookie)
        }

        for cookie in cookies {
            HTTPCookieStorage.shared.setCookie(cookie)
        }
    }

    private func loadLoginStatuses() {
        for site in loginSites {
            let siteId = site.makeService().id
            loginStatus[siteId] = false
            Task {
                let verified = await site.makeService().restoreSession()
                await MainActor.run {
                    loginStatus[siteId] = verified
                }
            }
        }
    }

    private func siteShortName(_ site: ForumSite) -> String {
        switch site {
        case .hackerNews: return "HN"
        case .fourD4Y: return "4D4Y"
        case .v2ex: return "V2EX"
        case .linuxDo: return "Linux.do"
        case .zhihu: return "知乎"
        case .rss: return "RSS"
        }
    }
}
