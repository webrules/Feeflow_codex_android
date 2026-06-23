import SwiftUI

struct AvatarView: View {
    let urlOrName: String
    let size: CGFloat
    var fallbackText: String = ""
    @State private var loadedImage: UIImage?
    @State private var didFail = false

    var body: some View {
        avatarContent
            .frame(width: size, height: size)
            .clipShape(Circle())
            .overlay(Circle().stroke(Color.forumSeparator.opacity(0.45), lineWidth: 0.5))
            .task(id: normalizedURL?.absoluteString ?? urlOrName) {
                await loadAvatar()
            }
    }

    @ViewBuilder
    private var avatarContent: some View {
        if let loadedImage {
            Image(uiImage: loadedImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else if normalizedURL != nil, !didFail {
            fallbackAvatar
                .overlay {
                    ProgressView()
                        .scaleEffect(0.55)
                        .tint(.forumTextSecondary)
                }
        } else if shouldRenderSystemSymbol, UIImage(systemName: urlOrName) != nil {
            Image(systemName: urlOrName)
                .resizable()
                .symbolRenderingMode(.hierarchical)
                .aspectRatio(contentMode: .fit)
                .padding(size * 0.16)
                .foregroundStyle(Color.forumTextSecondary)
                .background(Color.forumCard)
        } else {
            fallbackAvatar
        }
    }

    private var normalizedURL: URL? {
        let trimmed = urlOrName
            .replacingOccurrences(of: "&amp;", with: "&")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmed.isEmpty else { return nil }

        if trimmed.hasPrefix("//") {
            return URL(string: "https:\(trimmed)")
        }

        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            return URL(string: trimmed)
        }

        return nil
    }

    private var shouldRenderSystemSymbol: Bool {
        guard !urlOrName.isEmpty else { return false }
        let genericPersonSymbols = ["person.circle", "person.circle.fill", "person.crop.circle", "person.crop.circle.fill"]
        return !genericPersonSymbols.contains(urlOrName)
    }

    private func loadAvatar() async {
        guard let url = normalizedURL else {
            loadedImage = nil
            didFail = true
            return
        }

        loadedImage = nil
        didFail = false

        // 1. Check cache first (memory + disk)
        if let cached = ImageCache.shared.image(for: url) {
            loadedImage = cached
            didFail = false
            return
        }

        // 2. Fetch from network, trying candidate URLs
        for candidateURL in avatarCandidateURLs(from: url) {
            var request = URLRequest(url: candidateURL)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
            request.setValue("image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8", forHTTPHeaderField: "Accept")
            if let host = candidateURL.host {
                request.setValue("https://\(host)/forum/", forHTTPHeaderField: "Referer")
            }

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                guard let httpResponse = response as? HTTPURLResponse,
                      (200..<300).contains(httpResponse.statusCode),
                      let image = UIImage(data: data) else {
                    continue
                }

                loadedImage = image
                didFail = false
                // 3. Store in cache (memory + disk) using the original normalized URL as key
                ImageCache.shared.store(image: image, data: data, for: url)
                return
            } catch {
                continue
            }
        }

        didFail = true
    }

    private func avatarCandidateURLs(from url: URL) -> [URL] {
        let absolute = url.absoluteString
        guard absolute.contains("4d4y.com") else {
            return [url]
        }

        var candidates = [url]
        let alternates = [
            absolute.replacingOccurrences(of: "https://www.4d4y.com/uc_server/", with: "https://www.4d4y.com/forum/uc_server/"),
            absolute.replacingOccurrences(of: "https://www.4d4y.com/forum/uc_server/", with: "https://www.4d4y.com/uc_server/"),
            absolute.replacingOccurrences(of: "https://www.4d4y.com/forum/uc_server/data/avatar/", with: "https://img02.4d4y.com/forum/uc_server/data/avatar/"),
            absolute.replacingOccurrences(of: "https://img02.4d4y.com/forum/uc_server/data/avatar/", with: "https://www.4d4y.com/forum/uc_server/data/avatar/"),
            absolute.replacingOccurrences(of: "https://img02.4d4y.com/forum/uc_server/data/avatar/", with: "https://img01.4d4y.com/forum/uc_server/data/avatar/")
        ]

        for alternate in alternates {
            if alternate != absolute, let alternateURL = URL(string: alternate), !candidates.contains(alternateURL) {
                candidates.append(alternateURL)
            }
        }

        return candidates
    }

    private var fallbackAvatar: some View {
        ZStack {
            Circle()
                .fill(fallbackColor.opacity(0.18))
            Text(initials)
                .font(.system(size: max(11, size * 0.38), weight: .semibold))
                .foregroundStyle(fallbackColor)
        }
    }

    private var initials: String {
        let source = fallbackText.trimmingCharacters(in: .whitespacesAndNewlines)
        let text = source.isEmpty ? "?" : source
        let parts = text.split { $0.isWhitespace || $0 == "_" || $0 == "-" }
        let chars = parts.prefix(2).compactMap { $0.first }
        let value = chars.isEmpty ? String(text.prefix(1)) : String(chars)
        return value.uppercased()
    }

    private var fallbackColor: Color {
        let palette: [Color] = [.blue, .green, .orange, .pink, .purple, .teal, .indigo]
        let seed = fallbackText.unicodeScalars.reduce(0) { $0 + Int($1.value) }
        return palette[seed % palette.count]
    }
}
