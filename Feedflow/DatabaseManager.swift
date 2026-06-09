import Foundation
import SQLite3

class DatabaseManager {
    static let shared = DatabaseManager()
    private var db: OpaquePointer?
    
    /// Serial queue to ensure all database access is thread-safe.
    private let dbQueue = DispatchQueue(label: "com.feedflow.database", qos: .userInitiated)
    
    /// SQLITE_TRANSIENT tells SQLite to copy string data immediately,
    /// preventing dangling pointer issues with temporary NSString objects.
    private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
    
    private init() {
        openDatabase()
        createTables()
        createSettingsTable()
        createSummariesTable()
        createCacheTables()
        createBookmarksTable()
        createURLBookmarksTable()
        migrateSchemas()
    }
    
    private func openDatabase() {
        guard let fileURL = try? FileManager.default
            .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("Feedflow.sqlite") else {
            print("[DB] Error: Could not resolve documents directory")
            return
        }
        
        if sqlite3_open(fileURL.path, &db) != SQLITE_OK {
            print("[DB] Error opening database")
            return
        }
        
        // Enable WAL mode for better concurrent read/write performance
        sqlite3_exec(db, "PRAGMA journal_mode=WAL;", nil, nil, nil)
        // Set busy timeout to 5 seconds to handle contention gracefully
        sqlite3_busy_timeout(db, 5000)
    }
    
    private func createTables() {
        let createTableString = """
        CREATE TABLE IF NOT EXISTS communities(
            id TEXT,
            name TEXT,
            description TEXT,
            category TEXT,
            activeToday INTEGER,
            onlineNow INTEGER,
            serviceId TEXT,
            PRIMARY KEY (id, serviceId)
        );
        """
        
        var createTableStatement: OpaquePointer?
        if sqlite3_prepare_v2(db, createTableString, -1, &createTableStatement, nil) == SQLITE_OK {
            if sqlite3_step(createTableStatement) != SQLITE_DONE {
                print("[DB] Communities table could not be created.")
            }
        } else {
            print("[DB] CREATE TABLE statement could not be prepared.")
        }
        sqlite3_finalize(createTableStatement)
    }
    
    func saveCommunities(_ communities: [Community], forService serviceId: String) {
        dbQueue.sync {
            sqlite3_exec(db, "BEGIN TRANSACTION", nil, nil, nil)
            
            let insertStatementString = "INSERT OR REPLACE INTO communities (id, name, description, category, activeToday, onlineNow, serviceId) VALUES (?, ?, ?, ?, ?, ?, ?);"
            var insertStatement: OpaquePointer?
            
            if sqlite3_prepare_v2(db, insertStatementString, -1, &insertStatement, nil) == SQLITE_OK {
                for community in communities {
                    sqlite3_bind_text(insertStatement, 1, (community.id as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(insertStatement, 2, (community.name as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(insertStatement, 3, (community.description as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(insertStatement, 4, (community.category as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_int(insertStatement, 5, Int32(community.activeToday))
                    sqlite3_bind_int(insertStatement, 6, Int32(community.onlineNow))
                    sqlite3_bind_text(insertStatement, 7, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    
                    if sqlite3_step(insertStatement) != SQLITE_DONE {
                        print("[DB] Could not insert row.")
                    }
                    sqlite3_reset(insertStatement)
                }
            } else {
                print("[DB] INSERT statement could not be prepared.")
            }
            sqlite3_finalize(insertStatement)
            sqlite3_exec(db, "COMMIT TRANSACTION", nil, nil, nil)
        }
    }
    
    func getCommunities(forService serviceId: String) -> [Community] {
        dbQueue.sync {
            let queryStatementString = "SELECT id, name, description, category, activeToday, onlineNow FROM communities WHERE serviceId = ?;"
            var queryStatement: OpaquePointer?
            var communities: [Community] = []
            
            if sqlite3_prepare_v2(db, queryStatementString, -1, &queryStatement, nil) == SQLITE_OK {
                sqlite3_bind_text(queryStatement, 1, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                
                while sqlite3_step(queryStatement) == SQLITE_ROW {
                    guard let idPtr = sqlite3_column_text(queryStatement, 0),
                          let namePtr = sqlite3_column_text(queryStatement, 1),
                          let descPtr = sqlite3_column_text(queryStatement, 2),
                          let catPtr = sqlite3_column_text(queryStatement, 3) else { continue }
                    
                    let id = String(cString: idPtr)
                    let name = String(cString: namePtr)
                    let description = String(cString: descPtr)
                    let category = String(cString: catPtr)
                    let activeToday = Int(sqlite3_column_int(queryStatement, 4))
                    let onlineNow = Int(sqlite3_column_int(queryStatement, 5))
                    
                    communities.append(Community(
                        id: id,
                        name: name,
                        description: description,
                        category: category,
                        activeToday: activeToday,
                        onlineNow: onlineNow
                    ))
                }
            } else {
                print("[DB] SELECT statement could not be prepared")
            }
            sqlite3_finalize(queryStatement)
            return communities
        }
    }
    
    // MARK: - Settings
    
    private func createSettingsTable() {
        let createTableString = """
        CREATE TABLE IF NOT EXISTS settings(
            key TEXT PRIMARY KEY,
            value TEXT
        );
        """
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, createTableString, -1, &statement, nil) == SQLITE_OK {
            if sqlite3_step(statement) == SQLITE_DONE {
                // print("Settings table created.")
            }
        }
        sqlite3_finalize(statement)
    }
    
    func saveSetting(key: String, value: String) {
        dbQueue.sync {
            let sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?);"
            var statement: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (key as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (value as NSString).utf8String, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) != SQLITE_DONE {
                    print("[DB] Error saving setting: \(key)")
                }
            } else {
                print("[DB] Error preparing save statement for key: \(key)")
            }
            sqlite3_finalize(statement)
        }
    }
    
    func getSetting(key: String) -> String? {
        dbQueue.sync {
            let sql = "SELECT value FROM settings WHERE key = ?;"
            var statement: OpaquePointer?
            var result: String?
            
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (key as NSString).utf8String, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) == SQLITE_ROW {
                    if let val = sqlite3_column_text(statement, 0) {
                        result = String(cString: val)
                    }
                }
            }
            sqlite3_finalize(statement)
            return result
        }
    }
    
    // MARK: - Cookie Storage (encrypted)
    
    /// Save cookies with merge — merges new cookies into existing ones (for incremental updates)
    func saveCookies(siteId: String, cookies: [HTTPCookie]) {
        print("[DB] saveCookies (merge) called for \(siteId) with \(cookies.count) new cookies")
        
        var currentCookies = getCookies(siteId: siteId) ?? []
        print("[DB] Existing cookies for \(siteId): \(currentCookies.count)")
        
        for newCookie in cookies {
            if let index = currentCookies.firstIndex(where: { 
                $0.name == newCookie.name && $0.domain == newCookie.domain && $0.path == newCookie.path 
            }) {
                currentCookies[index] = newCookie
            } else {
                currentCookies.append(newCookie)
            }
        }
        
        print("[DB] Merged cookie count for \(siteId): \(currentCookies.count)")
        persistCookies(siteId: siteId, cookies: currentCookies)
    }
    
    /// Replace cookies entirely — overwrites all existing cookies (for fresh login)
    func replaceCookies(siteId: String, cookies: [HTTPCookie]) {
        print("[DB] replaceCookies called for \(siteId) with \(cookies.count) cookies (clean overwrite)")
        persistCookies(siteId: siteId, cookies: cookies)
    }
    
    /// Internal: serialize and persist cookies to DB
    private func persistCookies(siteId: String, cookies: [HTTPCookie]) {
        let cookieDicts = cookies.map { cookie -> [String: Any] in
            var dict: [String: Any] = [
                "name": cookie.name,
                "value": cookie.value,
                "domain": cookie.domain,
                "path": cookie.path,
                "secure": cookie.isSecure,
                "httpOnly": cookie.isHTTPOnly
            ]
            if let expires = cookie.expiresDate {
                dict["expires"] = expires.timeIntervalSince1970
            }
            return dict
        }
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: cookieDicts, options: []) else {
            print("[DB] ERROR: Failed to serialize cookies to JSON for \(siteId)")
            return
        }
        guard let jsonString = String(data: jsonData, encoding: .utf8) else {
            print("[DB] ERROR: Failed to convert cookie JSON data to string for \(siteId)")
            return
        }
        guard let encrypted = EncryptionHelper.shared.encrypt(jsonString) else {
            print("[DB] ERROR: Failed to encrypt cookies for \(siteId)")
            return
        }
        
        print("[DB] Saving \(cookies.count) cookies for \(siteId) (\(encrypted.count) chars)")
        saveSetting(key: "login_\(siteId)_cookies", value: encrypted)
        
        // Verify the save worked
        if let verify = getSetting(key: "login_\(siteId)_cookies") {
            print("[DB] Verified: cookie data saved for \(siteId) (\(verify.count) chars)")
        } else {
            print("[DB] CRITICAL: Cookie save verification FAILED for \(siteId)!")
        }
    }
    
    func getCookies(siteId: String) -> [HTTPCookie]? {
        guard let encrypted = getSetting(key: "login_\(siteId)_cookies") else {
            print("[DB] getCookies: No stored data for \(siteId)")
            return nil
        }
        guard let jsonString = EncryptionHelper.shared.decrypt(encrypted) else {
            print("[DB] getCookies: Decryption FAILED for \(siteId)")
            return nil
        }
        guard let jsonData = jsonString.data(using: .utf8) else {
            print("[DB] getCookies: String to data conversion failed for \(siteId)")
            return nil
        }
        guard let array = try? JSONSerialization.jsonObject(with: jsonData, options: []) as? [[String: Any]] else {
            print("[DB] getCookies: JSON parse failed for \(siteId)")
            return nil
        }
        
        var cookies: [HTTPCookie] = []
        for dict in array {
            var properties: [HTTPCookiePropertyKey: Any] = [:]
            if let name = dict["name"] as? String { properties[.name] = name }
            if let value = dict["value"] as? String { properties[.value] = value }
            if let domain = dict["domain"] as? String { properties[.domain] = domain }
            if let path = dict["path"] as? String { properties[.path] = path }
            if let secure = dict["secure"] as? Bool, secure { properties[.secure] = "TRUE" }
            if let expires = dict["expires"] as? TimeInterval {
                properties[.expires] = Date(timeIntervalSince1970: expires)
            }
            if let cookie = HTTPCookie(properties: properties) {
                // Only skip cookies that have explicitly expired
                if let expires = cookie.expiresDate, expires < Date() {
                    print("[DB] Skipping expired cookie: \(cookie.name) (expired \(expires))")
                    continue
                }
                cookies.append(cookie)
            } else {
                print("[DB] Failed to create HTTPCookie from dict: \(dict["name"] ?? "unknown")")
            }
        }
        print("[DB] getCookies for \(siteId): \(array.count) stored, \(cookies.count) valid")
        return cookies
    }
    
    func hasCookies(siteId: String) -> Bool {
        return getSetting(key: "login_\(siteId)_cookies") != nil
    }
    
    // MARK: - AI Summaries
    
    private func createSummariesTable() {
        let sql = """
        CREATE TABLE IF NOT EXISTS ai_summaries(
            thread_id TEXT,
            service_id TEXT DEFAULT '',
            summary TEXT,
            created_at INTEGER,
            PRIMARY KEY (thread_id, service_id)
        );
        """
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }
    
    func saveSummary(threadId: String, serviceId: String = "", summary: String) {
        dbQueue.sync {
            let sql = "INSERT OR REPLACE INTO ai_summaries (thread_id, service_id, summary, created_at) VALUES (?, ?, ?, ?);"
            var statement: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (threadId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 3, (summary as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_int64(statement, 4, Int64(Date().timeIntervalSince1970))
                if sqlite3_step(statement) != SQLITE_DONE {
                    print("[DB] Error saving summary for thread: \(threadId)")
                }
            }
            sqlite3_finalize(statement)
        }
    }
    
    func getSummary(threadId: String, serviceId: String = "") -> String? {
        dbQueue.sync {
            let sql = "SELECT summary FROM ai_summaries WHERE thread_id = ? AND service_id = ?;"
            var statement: OpaquePointer?
            var result: String?
            
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (threadId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) == SQLITE_ROW {
                    if let val = sqlite3_column_text(statement, 0) {
                        result = String(cString: val)
                    }
                }
            }
            sqlite3_finalize(statement)
            return result
        }
    }
    
    /// Returns cached summary only if it was created within `maxAgeSeconds` ago.
    func getSummaryIfFresh(threadId: String, serviceId: String = "", maxAgeSeconds: TimeInterval) -> String? {
        dbQueue.sync {
            let sql = "SELECT summary, created_at FROM ai_summaries WHERE thread_id = ? AND service_id = ?;"
            var statement: OpaquePointer?
            var result: String?
            
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (threadId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) == SQLITE_ROW {
                    if let val = sqlite3_column_text(statement, 0) {
                        let createdAt = TimeInterval(sqlite3_column_int64(statement, 1))
                        let age = Date().timeIntervalSince1970 - createdAt
                        if age < maxAgeSeconds {
                            result = String(cString: val)
                        }
                    }
                }
            }
            sqlite3_finalize(statement)
            return result
        }
    }
    
    // MARK: - Content Caching
    
    private func createCacheTables() {
        // Table for cached topics (thread lists per community)
        let topicsTable = """
        CREATE TABLE IF NOT EXISTS cached_topics(
            cache_key TEXT PRIMARY KEY,
            data TEXT,
            timestamp INTEGER
        );
        """
        
        // Table for cached thread details — keyed by (thread_id, service_id) to avoid cross-service collisions
        let threadsTable = """
        CREATE TABLE IF NOT EXISTS cached_threads(
            thread_id TEXT,
            service_id TEXT DEFAULT '',
            data TEXT,
            timestamp INTEGER,
            PRIMARY KEY (thread_id, service_id)
        );
        """
        
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, topicsTable, -1, &statement, nil) == SQLITE_OK {
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
        
        if sqlite3_prepare_v2(db, threadsTable, -1, &statement, nil) == SQLITE_OK {
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }
    
    func saveCachedTopics(cacheKey: String, topics: [Thread]) {
        dbQueue.sync {
            guard let jsonData = try? JSONEncoder().encode(topics),
                  let jsonString = String(data: jsonData, encoding: .utf8) else { return }
            
            let sql = "INSERT OR REPLACE INTO cached_topics (cache_key, data, timestamp) VALUES (?, ?, ?);"
            var statement: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (cacheKey as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (jsonString as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_int64(statement, 3, Int64(Date().timeIntervalSince1970))
                sqlite3_step(statement)
            }
            sqlite3_finalize(statement)
        }
    }
    
    func getCachedTopics(cacheKey: String) -> [Thread]? {
        dbQueue.sync {
            let sql = "SELECT data FROM cached_topics WHERE cache_key = ?;"
            var statement: OpaquePointer?
            var result: [Thread]?
            
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (cacheKey as NSString).utf8String, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) == SQLITE_ROW {
                    if let jsonStringPtr = sqlite3_column_text(statement, 0) {
                        let jsonString = String(cString: jsonStringPtr)
                        if let jsonData = jsonString.data(using: .utf8) {
                            do {
                                result = try JSONDecoder().decode([Thread].self, from: jsonData)
                            } catch {
                                print("[DB] Failed to decode cached topics for \(cacheKey): \(error)")
                            }
                        }
                    }
                }
            } else {
                print("[DB] SELECT statement could not be prepared for cached_topics")
            }
            sqlite3_finalize(statement)
            return result
        }
    }
    
    func saveCachedThread(threadId: String, serviceId: String = "", thread: Thread, comments: [Comment]) {
        dbQueue.sync {
            let cacheData = ThreadDetailCache(thread: thread, comments: comments)
            guard let jsonData = try? JSONEncoder().encode(cacheData),
                  let jsonString = String(data: jsonData, encoding: .utf8) else { return }
            
            let sql = "INSERT OR REPLACE INTO cached_threads (thread_id, service_id, data, timestamp) VALUES (?, ?, ?, ?);"
            var statement: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (threadId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 3, (jsonString as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_int64(statement, 4, Int64(Date().timeIntervalSince1970))
                sqlite3_step(statement)
            }
            sqlite3_finalize(statement)
        }
    }
    
    func getCachedThread(threadId: String, serviceId: String = "") -> (Thread, [Comment])? {
        dbQueue.sync {
            let sql = "SELECT data FROM cached_threads WHERE thread_id = ? AND service_id = ?;"
            var statement: OpaquePointer?
            var result: (Thread, [Comment])?
            
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (threadId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) == SQLITE_ROW,
                   let jsonString = sqlite3_column_text(statement, 0),
                   let jsonData = String(cString: jsonString).data(using: .utf8),
                   let cache = try? JSONDecoder().decode(ThreadDetailCache.self, from: jsonData) {
                    result = (cache.thread, cache.comments)
                }
            }
            sqlite3_finalize(statement)
            return result
        }
    }
    
    private func createBookmarksTable() {
        let sql = """
        CREATE TABLE IF NOT EXISTS bookmarks(
            thread_id TEXT,
            service_id TEXT,
            data TEXT,
            timestamp INTEGER,
            PRIMARY KEY (thread_id, service_id)
        );
        """
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }
    
    func toggleBookmark(thread: Thread, serviceId: String) {
        dbQueue.sync {
            if _isBookmarked(threadId: thread.id, serviceId: serviceId) {
                let sql = "DELETE FROM bookmarks WHERE thread_id = ? AND service_id = ?;"
                var statement: OpaquePointer?
                if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                    sqlite3_bind_text(statement, 1, (thread.id as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_step(statement)
                }
                sqlite3_finalize(statement)
            } else {
                guard let jsonData = try? JSONEncoder().encode(thread),
                      let jsonString = String(data: jsonData, encoding: .utf8) else { return }
                
                let sql = "INSERT INTO bookmarks (thread_id, service_id, data, timestamp) VALUES (?, ?, ?, ?);"
                var statement: OpaquePointer?
                if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                    sqlite3_bind_text(statement, 1, (thread.id as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(statement, 3, (jsonString as NSString).utf8String, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_int64(statement, 4, Int64(Date().timeIntervalSince1970))
                    sqlite3_step(statement)
                }
                sqlite3_finalize(statement)
            }
        }
    }
    
    func isBookmarked(threadId: String, serviceId: String) -> Bool {
        dbQueue.sync {
            return _isBookmarked(threadId: threadId, serviceId: serviceId)
        }
    }
    
    /// Internal (must be called within dbQueue)
    private func _isBookmarked(threadId: String, serviceId: String) -> Bool {
        let sql = "SELECT 1 FROM bookmarks WHERE thread_id = ? AND service_id = ?;"
        var statement: OpaquePointer?
        var exists = false
        if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_bind_text(statement, 1, (threadId as NSString).utf8String, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 2, (serviceId as NSString).utf8String, -1, SQLITE_TRANSIENT)
            if sqlite3_step(statement) == SQLITE_ROW {
                exists = true
            }
        }
        sqlite3_finalize(statement)
        return exists
    }
    
    func getBookmarkedThreads() -> [(Thread, String)] {
        dbQueue.sync {
            let sql = "SELECT data, service_id FROM bookmarks ORDER BY timestamp DESC;"
            var statement: OpaquePointer?
            var results: [(Thread, String)] = []
            
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                while sqlite3_step(statement) == SQLITE_ROW {
                    if let jsonString = sqlite3_column_text(statement, 0),
                       let serviceIdPtr = sqlite3_column_text(statement, 1) {
                        let serviceId = String(cString: serviceIdPtr)
                        if let jsonData = String(cString: jsonString).data(using: .utf8),
                           let thread = try? JSONDecoder().decode(Thread.self, from: jsonData) {
                            results.append((thread, serviceId))
                        }
                    }
                }
            }
            sqlite3_finalize(statement)
            return results
        }
    }
    
    // MARK: - URL Bookmarks
    
    private func createURLBookmarksTable() {
        let sql = """
        CREATE TABLE IF NOT EXISTS url_bookmarks(
            url TEXT PRIMARY KEY,
            title TEXT,
            timestamp INTEGER
        );
        """
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }
    
    func saveURLBookmark(url: String, title: String) {
        dbQueue.sync {
            let sql = "INSERT OR REPLACE INTO url_bookmarks (url, title, timestamp) VALUES (?, ?, ?);"
            var statement: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (url as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(statement, 2, (title as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_bind_int64(statement, 3, Int64(Date().timeIntervalSince1970))
                sqlite3_step(statement)
            }
            sqlite3_finalize(statement)
        }
    }
    
    func removeURLBookmark(url: String) {
        dbQueue.sync {
            let sql = "DELETE FROM url_bookmarks WHERE url = ?;"
            var statement: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (url as NSString).utf8String, -1, SQLITE_TRANSIENT)
                sqlite3_step(statement)
            }
            sqlite3_finalize(statement)
        }
    }
    
    func isURLBookmarked(url: String) -> Bool {
        dbQueue.sync {
            let sql = "SELECT 1 FROM url_bookmarks WHERE url = ?;"
            var statement: OpaquePointer?
            var exists = false
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                sqlite3_bind_text(statement, 1, (url as NSString).utf8String, -1, SQLITE_TRANSIENT)
                if sqlite3_step(statement) == SQLITE_ROW { exists = true }
            }
            sqlite3_finalize(statement)
            return exists
        }
    }
    
    func getURLBookmarks() -> [(String, String, Date)] {
        dbQueue.sync {
            let sql = "SELECT url, title, timestamp FROM url_bookmarks ORDER BY timestamp DESC;"
            var statement: OpaquePointer?
            var results: [(String, String, Date)] = []
            if sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK {
                while sqlite3_step(statement) == SQLITE_ROW {
                    if let urlPtr = sqlite3_column_text(statement, 0),
                       let titlePtr = sqlite3_column_text(statement, 1) {
                        let url = String(cString: urlPtr)
                        let title = String(cString: titlePtr)
                        let timestamp = Date(timeIntervalSince1970: TimeInterval(sqlite3_column_int64(statement, 2)))
                        results.append((url, title, timestamp))
                    }
                }
            }
            sqlite3_finalize(statement)
            return results
        }
    }
    
    // MARK: - Schema Migration
    
    /// Migrates old single-key tables to composite keys for existing installs.
    private func migrateSchemas() {
        // Migrate cached_threads: add service_id column if missing
        migrateAddColumn(table: "cached_threads", column: "service_id", type: "TEXT DEFAULT ''")
        // Migrate ai_summaries: add service_id column if missing
        migrateAddColumn(table: "ai_summaries", column: "service_id", type: "TEXT DEFAULT ''")
    }
    
    private func migrateAddColumn(table: String, column: String, type: String) {
        // Check if column already exists
        let pragmaSQL = "PRAGMA table_info(\(table));"
        var statement: OpaquePointer?
        var hasColumn = false
        
        if sqlite3_prepare_v2(db, pragmaSQL, -1, &statement, nil) == SQLITE_OK {
            while sqlite3_step(statement) == SQLITE_ROW {
                if let namePtr = sqlite3_column_text(statement, 1) {
                    let name = String(cString: namePtr)
                    if name == column {
                        hasColumn = true
                        break
                    }
                }
            }
        }
        sqlite3_finalize(statement)
        
        if !hasColumn {
            let alterSQL = "ALTER TABLE \(table) ADD COLUMN \(column) \(type);"
            sqlite3_exec(db, alterSQL, nil, nil, nil)
            print("[DB] Migrated \(table): added column \(column)")
        }
    }
}

// Helper struct for caching thread details
struct ThreadDetailCache: Codable {
    let thread: Thread
    let comments: [Comment]
}
