import SwiftUI

struct LoginView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var localizationManager = LocalizationManager.shared
    
    // Sites that support login (excludes RSS)
    private let loginSites: [ForumSite] = [.hackerNews, .fourD4Y, .v2ex, .linuxDo, .zhihu]
    
    @State private var selectedSite: ForumSite = .fourD4Y
    @State private var showWebLogin = false
    @State private var oauthOverrideURL: String? = nil  // When set, overrides the default login URL
    @State private var loginStatus: [String: Bool] = [:]
    
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
                ToolbarItem(placement: .cancellationAction) {
                    Button("cancel".localized()) { dismiss() }
                        .foregroundColor(.forumAccent)
                }
            }
            .sheet(isPresented: $showWebLogin, onDismiss: {
                oauthOverrideURL = nil  // Reset override when sheet closes
            }) {
                if let baseConfig = SiteLoginConfig.config(for: selectedSite) {
                    let effectiveConfig: SiteLoginConfig = {
                        if let overrideURL = oauthOverrideURL {
                            return SiteLoginConfig(
                                site: baseConfig.site,
                                loginURL: overrideURL,
                                successURLPatterns: baseConfig.successURLPatterns,
                                oauthOptions: baseConfig.oauthOptions
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
                        VStack(spacing: 6) {
                            siteLogoView(site: site)
                                .frame(width: 44, height: 44)
                                .background(
                                    selectedSite == site
                                    ? Color.forumAccent.opacity(0.15)
                                    : Color.forumCard
                                )
                                .cornerRadius(12)
                            
                            Text(siteShortName(site))
                                .font(.system(size: 11, weight: .medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                            
                            if loginStatus[site.makeService().id] == true {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(.green)
                            }
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
    
    @ViewBuilder
    private func siteLogoView(site: ForumSite) -> some View {
        let logo = site.makeService().logo
        if logo.hasPrefix("http") {
            // URL-based logo (e.g., Linux.do)
            AsyncImage(url: URL(string: logo)) { phase in
                switch phase {
                case .success(let image):
                    image.resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 24, height: 24)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                case .failure:
                    Image(systemName: "globe")
                        .font(.system(size: 22))
                default:
                    ProgressView()
                        .frame(width: 24, height: 24)
                }
            }
        } else {
            // SF Symbol logo
            Image(systemName: logo)
                .font(.system(size: 22))
        }
    }
    
    // MARK: - Login Options
    
    private var loginOptionsForSelectedSite: some View {
        VStack(spacing: 16) {
            if let config = SiteLoginConfig.config(for: selectedSite) {
                webLoginButton(config: config)
                
                if !config.oauthOptions.isEmpty {
                    oauthSection(config: config)
                }
                
                loginNote
            }
        }
    }
    
    private func webLoginButton(config: SiteLoginConfig) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("login_with_browser".localized())
                .font(.caption)
                .foregroundColor(.forumTextSecondary)
                .textCase(.uppercase)
            
            Button(action: {
                oauthOverrideURL = nil
                showWebLogin = true
            }) {
                HStack(spacing: 12) {
                    Image(systemName: "globe")
                        .font(.system(size: 20))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(LocalizationManager.shared.localizedString("login_to_site") + " " + config.site.makeService().name)
                            .font(.system(size: 16, weight: .semibold))
                        Text("login_captcha_support".localized())
                            .font(.system(size: 12))
                            .foregroundColor(.forumTextSecondary)
                    }
                    
                    Spacer()
                    
                    Image(systemName: "arrow.up.right.square")
                        .foregroundColor(.forumAccent)
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
                            Image(systemName: option.icon)
                                .font(.system(size: 16))
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
        
        // Critical check for Zhihu authentication token
        if site == .zhihu && !relevantCookies.contains(where: { $0.name == "z_c0" }) {
            print("[Login] CRITICAL WARNING: Zhihu login success but 'z_c0' cookie is missing! API requests will likely fail.")
        }
        
        DatabaseManager.shared.replaceCookies(siteId: siteId, cookies: relevantCookies)
        
        // Verify cookies were persisted
        let verified = DatabaseManager.shared.getCookies(siteId: siteId) ?? []
        print("[Login] Verification: \(verified.count) cookies readable from DB for \(siteId)")
        
        for cookie in relevantCookies {
            HTTPCookieStorage.shared.setCookie(cookie)
        }
        
        loginStatus[siteId] = true
        
        // Auto-dismiss the login view after successful login
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            dismiss()
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
    
    private func loadLoginStatuses() {
        for site in loginSites {
            let siteId = site.makeService().id
            loginStatus[siteId] = DatabaseManager.shared.hasCookies(siteId: siteId)
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
