import SwiftUI
import Combine

// Define ForumSite enum — ordered: RSS, Hacker News, 4D4Y, V2EX, Linux.do, Zhihu
enum ForumSite: String, CaseIterable, Identifiable {
    case rss
    case hackerNews
    case fourD4Y
    case v2ex
    case linuxDo
    case zhihu

    var id: String { rawValue }

    static func from(serviceId: String) -> ForumSite? {
        switch serviceId {
        case "4d4y": return .fourD4Y
        case "linux_do": return .linuxDo
        case "hackernews": return .hackerNews
        case "v2ex": return .v2ex
        case "rss": return .rss
        case "zhihu": return .zhihu
        default: return nil
        }
    }

    func makeService() -> ForumService {
        switch self {
        case .fourD4Y: return FourD4YService()
        case .linuxDo: return DiscourseService()
        case .hackerNews: return HackerNewsService()
        case .v2ex: return V2EXService()
        case .rss: return RSSService()
        case .zhihu: return ZhihuService()
        }
    }
}

// MARK: - Community Visibility Settings

class CommunitySettingsManager: ObservableObject {
    static let shared = CommunitySettingsManager()

    private let key = "enabledCommunities"

    @Published var enabledSites: Set<String> {
        didSet {
            // Ensure RSS is always enabled
            if !enabledSites.contains(ForumSite.rss.rawValue) {
                enabledSites.insert(ForumSite.rss.rawValue)
            }
            let array = Array(enabledSites)
            UserDefaults.standard.set(array, forKey: key)
        }
    }

    private init() {
        if let saved = UserDefaults.standard.stringArray(forKey: key) {
            self.enabledSites = Set(saved)
            // Ensure RSS is always present
            if !self.enabledSites.contains(ForumSite.rss.rawValue) {
                self.enabledSites.insert(ForumSite.rss.rawValue)
            }
        } else {
            // Default: all communities enabled
            self.enabledSites = Set(ForumSite.allCases.map { $0.rawValue })
        }
    }

    func isEnabled(_ site: ForumSite) -> Bool {
        enabledSites.contains(site.rawValue)
    }

    func toggle(_ site: ForumSite) {
        // RSS cannot be toggled off
        guard site != .rss else { return }
        if enabledSites.contains(site.rawValue) {
            enabledSites.remove(site.rawValue)
        } else {
            enabledSites.insert(site.rawValue)
        }
    }

    var visibleSites: [ForumSite] {
        ForumSite.allCases.filter { isEnabled($0) }
    }
}

// MARK: - Community Configuration View

struct CommunityConfigView: View {
    @ObservedObject var settingsManager = CommunitySettingsManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                List {
                    ForEach(ForumSite.allCases) { site in
                        let service = site.makeService()
                        let isOn = settingsManager.isEnabled(site)
                        let isRSS = site == .rss

                        HStack(spacing: 14) {
                            SiteIcon(service: service, size: 36)

                            Text(service.name)
                                .font(.body)
                                .foregroundColor(.forumTextPrimary)

                            Spacer()

                            if isRSS {
                                // RSS is always on — show a locked checkmark
                                FeedflowSymbol(name: "checkmark.seal.fill", size: 18, color: .forumAccent)
                            } else {
                                Toggle("", isOn: Binding(
                                    get: { isOn },
                                    set: { _ in settingsManager.toggle(site) }
                                ))
                                .tint(.forumAccent)
                                .labelsHidden()
                            }
                        }
                        .padding(.vertical, 4)
                        .listRowBackground(Color.forumCard)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("communities".localized())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .bottomBar) {
                    Button("done".localized()) {
                        dismiss()
                    }
                    .foregroundColor(.forumAccent)
                }
            }
            .toolbarBackground(Color.forumBackground, for: .bottomBar)
            .toolbarBackground(.visible, for: .bottomBar)
        }
    }
}

// MARK: - Site List (Home Page)

struct SiteListView: View {
    // No callback needed, NavigationLink handles it
    @EnvironmentObject var themeManager: ThemeManager
    @ObservedObject var localizationManager = LocalizationManager.shared
    @ObservedObject var communitySettings = CommunitySettingsManager.shared
    @State private var showSettings: Bool = false
    @State private var showLogin: Bool = false
    @State private var showBookmarks: Bool = false
    @State private var showCommunityConfig: Bool = false
    @State private var showCrossSiteSummary: Bool = false

    var body: some View {
        ZStack {
            Color.forumBackground.ignoresSafeArea()

            VStack(spacing: 24) {
                Text("select_community".localized())
                    .font(.title2)
                    .bold()
                    .foregroundColor(.forumTextPrimary)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 16)], spacing: 16) {
                    ForEach(communitySettings.visibleSites) { site in
                        let service = site.makeService()

                        NavigationLink(value: site) {
                            VStack(spacing: 16) {
                                SiteIcon(service: service, size: 52)

                                Text(service.name)
                                    .font(.headline)
                                    .foregroundColor(.forumTextPrimary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 32)
                            .background(Color.forumCard)
                            .cornerRadius(16)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.gray.opacity(0.1), lineWidth: 1)
                            )
                        }
                    }
                }
                .padding()

                Spacer()
            }
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
            .sheet(isPresented: $showLogin) {
                LoginView()
            }
            .sheet(isPresented: $showBookmarks) {
                BookmarksView()
            }
            .sheet(isPresented: $showCommunityConfig) {
                CommunityConfigView()
            }
            .sheet(isPresented: $showCrossSiteSummary) {
                CrossSiteAISummaryView()
            }
        }
        .safeAreaInset(edge: .bottom) {
            homeToolbar
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 8)
                .background(Color.forumBackground)
        }
    }

    private var homeToolbar: some View {
        HStack(spacing: 12) {
            HStack(spacing: 14) {
                Button(action: { showLogin = true }) {
                    FeedflowSymbol(name: FeedflowIcon.login, size: 22, color: .forumTextPrimary)
                }

                Button(action: { showSettings = true }) {
                    FeedflowSymbol(name: FeedflowIcon.settings, size: 18, color: .forumTextPrimary)
                }

                Button(action: { showBookmarks = true }) {
                    FeedflowSymbol(name: FeedflowIcon.bookmarkFill, size: 18, color: .forumTextPrimary)
                }

                Button(action: { showCrossSiteSummary = true }) {
                    FeedflowSymbol(name: FeedflowIcon.ai, size: 18, color: .forumTextPrimary)
                }
            }

            Spacer(minLength: 16)

            HStack(spacing: 14) {
                Button(action: { showCommunityConfig = true }) {
                    FeedflowSymbol(name: FeedflowIcon.communities, size: 17, color: .forumTextPrimary)
                }

                Button(action: { themeManager.isDarkMode.toggle() }) {
                    FeedflowSymbol(name: FeedflowIcon.theme, size: 18, color: .forumTextPrimary)
                }

                Button(action: {
                    LocalizationManager.shared.currentLanguage =
                        LocalizationManager.shared.currentLanguage == "en" ? "zh" : "en"
                }) {
                    Text(LocalizationManager.shared.currentLanguage == "en" ? "EN" : "中")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.forumTextPrimary)
                        .frame(width: 28, height: 28)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.forumCard)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.forumSeparator.opacity(0.6), lineWidth: 1)
        )
    }
}
