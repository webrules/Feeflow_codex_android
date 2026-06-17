import Foundation

class RSSParser: NSObject, XMLParserDelegate {
    private var parser: XMLParser
    private var completion: (([Thread]) -> Void)?
    
    // Internal state
    private var threads: [Thread] = []
    private var currentElement = ""
    private var currentTitle: String = ""
    private var currentLink: String = ""
    private var currentContent: String = ""
    private var currentPubDate: String = ""
    private var currentAuthor: String = ""
    private var currentID: String = "" // For Atom <id> or RSS <guid>
    
    // Parent Tracker
    private var elementStack: [String] = []
    
    // Current Item Context
    private var inItem = false
    
    init(data: Data) {
        self.parser = XMLParser(data: data)
        super.init()
        self.parser.delegate = self
    }
    
    func parse() async -> [Thread] {
        return await withCheckedContinuation { continuation in
            self.completion = { items in
                continuation.resume(returning: items)
            }
            self.parser.parse()
        }
    }
    
    // MARK: - XMLParserDelegate
    
    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String : String] = [:]) {
        currentElement = elementName
        elementStack.append(elementName)
        
        if elementName == "item" || elementName == "entry" {
            inItem = true
            currentTitle = ""
            currentLink = ""
            currentContent = ""
            currentPubDate = ""
            currentAuthor = ""
            currentID = ""
        }
        
        // Atom link href
        if inItem && elementName == "link" {
            if let href = attributeDict["href"] {
                // Prefer alternate or empty rel
                let rel = attributeDict["rel"]
                if rel == "alternate" || rel == nil {
                    currentLink = href
                }
            }
        }
    }
    
    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard inItem else { return }
        let data = string.trimmingCharacters(in: .whitespacesAndNewlines)
        // Allow whitespace for content, but trim for others usually.
        // For simplicity, we append everything and trim later if needed, 
        // but `foundCharacters` can be called multiple times.
        
        switch currentElement {
        case "title":
            currentTitle += string
        case "link":
             // RSS <link>text</link>
             // Atom usually uses href attribute, but sometimes text
             if !string.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                 currentLink += string
             }
        case "description", "content", "content:encoded", "summary":
            currentContent += string
        case "pubDate", "updated", "published", "dc:date":
            currentPubDate += string
        case "author", "dc:creator", "name": 
            // Atom: <author><name>Bob</name></author> 
            // We need to be careful with nesting. 
            // If stack is entry -> author -> name, capture it.
            if elementStack.contains("author") {
                 currentAuthor += string
            } else if currentElement == "dc:creator" {
                 currentAuthor += string
            }
        case "guid", "id":
            currentID += string
        default:
            break
        }
    }
    
    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        elementStack.removeLast()
        
        if elementName == "item" || elementName == "entry" {
            inItem = false
            
            // Post-processing
            let title = currentTitle.trimmingCharacters(in: .whitespacesAndNewlines).decodingHTMLEntities()
            let content = currentContent.trimmingCharacters(in: .whitespacesAndNewlines).decodingHTMLEntities()
            let authorName = currentAuthor.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Author" : currentAuthor.trimmingCharacters(in: .whitespacesAndNewlines)
            let link = currentLink.trimmingCharacters(in: .whitespacesAndNewlines)
            
            // Format Date
            let timeAgo = formatDate(currentPubDate)
            
            // ID: use guid/id or fallback to link hash
            let id = currentID.isEmpty ? String(link.hash) : currentID.trimmingCharacters(in: .whitespacesAndNewlines)
            
            // Note: We need a dummy community to create a Thread, but the Service will override it or we pass it in?
            // The service calls this parser, so the service will assign the correct community to the returned threads.
            // For now, we create a partial thread, or just return struct data? 
            // Better to return `Thread` with placeholder community.
            
            let thread = Thread(
                id: link, // Use link as ID for easier navigation/webview
                title: title,
                content: content,
                author: User(id: authorName, username: authorName, avatar: "rss", role: "RSS"),
                community: Community(id: "rss", name: "RSS", description: "", category: "RSS", activeToday: 0, onlineNow: 0),
                timeAgo: timeAgo,
                likeCount: 0,
                commentCount: 0,
                isLiked: false,
                tags: nil
            )
            
            threads.append(thread)
        }
    }
    
    func parserDidEndDocument(_ parser: XMLParser) {
        completion?(threads)
    }
    
    func parser(_ parser: XMLParser, parseErrorOccurred parseError: Error) {
        print("RSS Parse Error: \(parseError)")
        completion?([])
    }
    
    private func formatDate(_ dateString: String) -> String {
        // Try common formats
        // RFC 822 (RSS): Sat, 07 Sep 2002 00:00:01 GMT
        // ISO 8601 (Atom): 2002-09-07T00:00:01Z
        
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        
        // RSS
        formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss Z"
        if let date = formatter.date(from: dateString) {
            return ForumServiceHelper.calculateTimeAgo(from: date)
        }
        
        // Atom / ISO
        let isoFormatter = ISO8601DateFormatter()
        if let date = isoFormatter.date(from: dateString.trimmingCharacters(in: .whitespacesAndNewlines)) {
             return ForumServiceHelper.calculateTimeAgo(from: date)
        }
        
        // Fallback
        return "Recent"
    }
}

// Helper struct since protocol extension methods aren't static
struct ForumServiceHelper {
    static func calculateTimeAgo(from date: Date) -> String {
        let diff = Date().timeIntervalSince(date)
        if diff < 60 {
            return "just now"
        } else if diff < 3600 {
            return "\(Int(diff / 60))m"
        } else if diff < 86400 {
            return "\(Int(diff / 3600))h"
        } else {
            return "\(Int(diff / 86400))d"
        }
    }
}
