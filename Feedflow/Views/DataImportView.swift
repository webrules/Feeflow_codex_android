import SwiftUI
import UniformTypeIdentifiers

struct RSSFeedManagerView: View {
    @State private var rssService = RSSService()
    @State private var showFilePicker = false
    @State private var importedFeeds: [(title: String, url: String)] = []
    @State private var showImportSheet = false
    @State private var showAddFeed = false
    @State private var newFeedName = ""
    @State private var newFeedURL = ""
    @State private var editMode: EditMode = .inactive
    @State private var selectedFeedIds: Set<String> = []
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                VStack(spacing: 0) {
                    if rssService.feeds.isEmpty {
                        ContentUnavailableView(
                            "no_rss_feeds".localized(),
                            systemImage: FeedflowIcon.feed,
                            description: Text("add_feeds_description".localized())
                        )
                    } else {
                        List(selection: $selectedFeedIds) {
                            ForEach(rssService.feeds) { feed in
                                HStack(spacing: 12) {
                                    FeedflowSymbol(
                                        name: FeedflowIcon.feed,
                                        size: 16,
                                        color: .forumAccent,
                                        background: .forumAccentSoft,
                                        frameSize: 32
                                    )

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(feed.name)
                                            .font(.system(size: 16, weight: .medium))
                                            .foregroundColor(.forumTextPrimary)

                                        Text(feed.url)
                                            .font(.system(size: 12))
                                            .foregroundColor(.forumTextSecondary)
                                            .lineLimit(1)
                                    }
                                }
                                .listRowBackground(Color.forumCard)
                            }
                            .onDelete { indexSet in
                                let feedIds = indexSet.map { rssService.feeds[$0].id }
                                for id in feedIds {
                                    rssService.removeFeed(id: id)
                                }
                            }
                        }
                        .listStyle(.plain)
                        .environment(\.editMode, $editMode)
                    }

                    // Bottom action bar when in edit mode with selections
                    if editMode == .active && !selectedFeedIds.isEmpty {
                        HStack {
                            Button(role: .destructive) {
                                rssService.removeFeeds(ids: selectedFeedIds)
                                selectedFeedIds.removeAll()
                                if rssService.feeds.isEmpty {
                                    editMode = .inactive
                                }
                            } label: {
                                Label(LocalizationManager.shared.localizedString("delete_count", selectedFeedIds.count), systemImage: FeedflowIcon.trash)
                                    .font(.system(size: 16, weight: .semibold))
                            }
                            .tint(.red)

                            Spacer()
                        }
                        .padding()
                        .background(Color.forumCard)
                    }
                }
            }
            .navigationTitle("manage_feeds".localized())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("done".localized()) { dismiss() }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 12) {
                        if !rssService.feeds.isEmpty {
                            Button(editMode == .active ? "done".localized() : "edit".localized()) {
                                withAnimation {
                                    editMode = editMode == .active ? .inactive : .active
                                    selectedFeedIds.removeAll()
                                }
                            }
                        }

                        Menu {
                            Button {
                                showAddFeed = true
                            } label: {
                                Label("add_feed_manually".localized(), systemImage: FeedflowIcon.addCircle)
                            }

                            Button {
                                showFilePicker = true
                            } label: {
                                Label("import_from_opml".localized(), systemImage: FeedflowIcon.importFile)
                            }
                        } label: {
                            FeedflowSymbol(name: FeedflowIcon.addCircle, size: 20, color: .forumAccent)
                        }
                    }
                }
            }
            .fileImporter(
                isPresented: $showFilePicker,
                allowedContentTypes: [.xml, UTType(filenameExtension: "opml") ?? .xml],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    guard let url = urls.first else { return }
                    if url.startAccessingSecurityScopedResource() {
                        defer { url.stopAccessingSecurityScopedResource() }
                        do {
                            let data = try Data(contentsOf: url)
                            let parser = OPMLParser(data: data)
                            self.importedFeeds = parser.parse()
                            if !importedFeeds.isEmpty {
                                showImportSheet = true
                            }
                        } catch {
                            print("Error reading file: \(error)")
                        }
                    }
                case .failure(let error):
                    print("Error picking file: \(error)")
                }
            }
            .sheet(isPresented: $showImportSheet) {
                OPMLImportSheet(
                    importedFeeds: importedFeeds,
                    existingURLs: Set(rssService.feeds.map { $0.url }),
                    onImport: { selectedFeeds in
                        let newFeedInfos = selectedFeeds.map {
                            RSSService.FeedInfo(id: UUID().uuidString, name: $0.title, url: $0.url, description: "")
                        }
                        rssService.addFeeds(newFeedInfos)
                    }
                )
            }
            .alert("add_rss_feed".localized(), isPresented: $showAddFeed) {
                TextField("feed_name".localized(), text: $newFeedName)
                TextField("feed_url".localized(), text: $newFeedURL)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)

                Button("cancel".localized(), role: .cancel) {
                    newFeedName = ""
                    newFeedURL = ""
                }
                Button("add".localized()) {
                    let name = newFeedName.isEmpty ? newFeedURL : newFeedName
                    if !newFeedURL.isEmpty {
                        rssService.addFeed(name: name, url: newFeedURL)
                    }
                    newFeedName = ""
                    newFeedURL = ""
                }
            }
        }
    }
}

// MARK: - OPML Import Sheet (preview & select feeds from OPML)
struct OPMLImportSheet: View {
    let importedFeeds: [(title: String, url: String)]
    let existingURLs: Set<String>
    let onImport: ([(title: String, url: String)]) -> Void

    @State private var selectedURLs: Set<String> = []
    @Environment(\.dismiss) var dismiss

    // Feeds that are new (not already in the feed list)
    private var newFeeds: [(title: String, url: String)] {
        importedFeeds.filter { !existingURLs.contains($0.url) }
    }

    private var duplicateFeeds: [(title: String, url: String)] {
        importedFeeds.filter { existingURLs.contains($0.url) }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.forumBackground.ignoresSafeArea()

                List {
                    if !newFeeds.isEmpty {
                        Section {
                            ForEach(newFeeds, id: \.url) { feed in
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(feed.title)
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundColor(.forumTextPrimary)
                                        Text(feed.url)
                                            .font(.system(size: 11))
                                            .foregroundColor(.forumTextSecondary)
                                            .lineLimit(1)
                                    }

                                    Spacer()

                                    if selectedURLs.contains(feed.url) {
                                        FeedflowSymbol(name: "checkmark.circle.fill", size: 18, color: .forumAccent)
                                    } else {
                                        FeedflowSymbol(name: "circle", size: 18, color: .forumTextSecondary)
                                    }
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    if selectedURLs.contains(feed.url) {
                                        selectedURLs.remove(feed.url)
                                    } else {
                                        selectedURLs.insert(feed.url)
                                    }
                                }
                                .listRowBackground(Color.forumCard)
                            }
                        } header: {
                            HStack {
                                Text(LocalizationManager.shared.localizedString("new_feeds_count", newFeeds.count))
                                Spacer()
                                Button(selectedURLs.count == newFeeds.count ? "deselect_all".localized() : "select_all".localized()) {
                                    if selectedURLs.count == newFeeds.count {
                                        selectedURLs.removeAll()
                                    } else {
                                        selectedURLs = Set(newFeeds.map { $0.url })
                                    }
                                }
                                .font(.caption)
                            }
                        }
                    }

                    if !duplicateFeeds.isEmpty {
                        Section {
                            ForEach(duplicateFeeds, id: \.url) { feed in
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(feed.title)
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundColor(.forumTextSecondary)
                                        Text(feed.url)
                                            .font(.system(size: 11))
                                            .foregroundColor(.forumTextSecondary.opacity(0.6))
                                            .lineLimit(1)
                                    }

                                    Spacer()

                                    Text("already_added".localized())
                                        .font(.caption)
                                        .foregroundColor(.forumTextSecondary)
                                }
                                .listRowBackground(Color.forumCard.opacity(0.5))
                            }
                        } header: {
                            Text(LocalizationManager.shared.localizedString("already_subscribed_count", duplicateFeeds.count))
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
            .navigationTitle("import_from_opml".localized())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("cancel".localized()) { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(LocalizationManager.shared.localizedString("import_count", selectedURLs.count)) {
                        let feedsToImport = newFeeds.filter { selectedURLs.contains($0.url) }
                        onImport(feedsToImport)
                        dismiss()
                    }
                    .disabled(selectedURLs.isEmpty)
                }
            }
            .onAppear {
                // Pre-select all new feeds
                selectedURLs = Set(newFeeds.map { $0.url })
            }
        }
    }
}
