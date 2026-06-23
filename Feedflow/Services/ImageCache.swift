import Foundation
import UIKit

/// Lightweight two-tier image cache: NSCache (memory) + file-based disk cache.
/// Used by AvatarView to avoid re-fetching avatars on every scroll.
final class ImageCache {
    static let shared = ImageCache()

    private let memoryCache = NSCache<NSURL, UIImage>()
    private let fileManager = FileManager.default
    private let diskCacheDir: URL
    private let queue = DispatchQueue(label: "com.feedflow.imagecache", qos: .utility)

    private init() {
        memoryCache.countLimit = 200
        memoryCache.totalCostLimit = 30 * 1024 * 1024 // 30 MB

        let caches = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first!
        diskCacheDir = caches.appendingPathComponent("FeedflowImageCache", isDirectory: true)

        if !fileManager.fileExists(atPath: diskCacheDir.path) {
            try? fileManager.createDirectory(at: diskCacheDir, withIntermediateDirectories: true)
        }

        // Purge disk cache entries older than 7 days on init (best-effort, async)
        queue.async { [self] in
            self.purgeExpired()
        }
    }

    // MARK: - Public API

    func image(for url: URL) -> UIImage? {
        // 1. Memory hit
        if let mem = memoryCache.object(forKey: url as NSURL) {
            return mem
        }
        // 2. Disk hit
        let diskURL = diskCacheURL(for: url)
        if fileManager.fileExists(atPath: diskURL.path),
           let data = try? Data(contentsOf: diskURL),
           let image = UIImage(data: data) {
            // Promote to memory
            memoryCache.setObject(image, forKey: url as NSURL, cost: data.count)
            return image
        }
        return nil
    }

    func store(image: UIImage, data: Data, for url: URL) {
        let nsURL = url as NSURL
        memoryCache.setObject(image, forKey: nsURL, cost: data.count)

        let diskURL = diskCacheURL(for: url)
        queue.async { [self] in
            try? data.write(to: diskURL, options: .atomic)
        }
    }

    // MARK: - Private

    private func diskCacheURL(for url: URL) -> URL {
        // Use SHA256 of the absolute string as the file name to avoid collisions
        // and sanitize any filesystem-unfriendly characters
        let hash = url.absoluteString.sha256Prefix(32)
        return diskCacheDir.appendingPathComponent(hash)
    }

    private func purgeExpired() {
        guard let contents = try? fileManager.contentsOfDirectory(
            at: diskCacheDir,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: .skipsHiddenFiles
        ) else { return }

        let cutoff = Date().addingTimeInterval(-7 * 24 * 60 * 60) // 7 days ago
        for fileURL in contents {
            guard let attrs = try? fileManager.attributesOfItem(atPath: fileURL.path),
                  let modDate = attrs[.modificationDate] as? Date,
                  modDate < cutoff else {
                continue
            }
            try? fileManager.removeItem(at: fileURL)
        }
    }
}

private extension String {
    /// Returns the first `count` hex characters of the SHA-256 hash of this string.
    func sha256Prefix(_ count: Int) -> String {
        guard let data = data(using: .utf8) else { return String(self.prefix(count)) }
        let hash = data.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) -> [UInt8] in
            var ctx = SHA256Context()
            ctx.update(ptr)
            return ctx.finalize()
        }
        return hash.prefix(count).map { String(format: "%02x", $0) }.joined()
    }
}

// MARK: - Minimal SHA-256 (no CryptoKit dependency for this simple use case)
// Uses CommonCrypto which is available on all Apple platforms.

import CommonCrypto

private struct SHA256Context {
    private var ctx = CC_SHA256_CTX()

    init() {
        CC_SHA256_Init(&ctx)
    }

    mutating func update(_ ptr: UnsafeRawBufferPointer) {
        CC_SHA256_Update(&ctx, ptr.baseAddress, CC_LONG(ptr.count))
    }

    func finalize() -> [UInt8] {
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        var mutableCtx = ctx
        CC_SHA256_Final(&digest, &mutableCtx)
        return digest
    }
}
