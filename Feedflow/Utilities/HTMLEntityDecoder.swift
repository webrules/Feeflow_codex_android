import Foundation

extension String {
    /// Decodes common HTML entities including numeric (decimal & hex) and named entities.
    func decodingHTMLEntities() -> String {
        var result = self
        // Named entities
        let named: [(String, String)] = [
            ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"),
            ("&quot;", "\""), ("&apos;", "'"), ("&#39;", "'"),
            ("&nbsp;", " "), ("&ensp;", "\u{2002}"), ("&emsp;", "\u{2003}"),
            ("&ndash;", "–"), ("&mdash;", "—"),
            ("&lsquo;", "\u{2018}"), ("&rsquo;", "\u{2019}"),
            ("&ldquo;", "\u{201C}"), ("&rdquo;", "\u{201D}"),
        ]
        for (entity, replacement) in named {
            result = result.replacingOccurrences(of: entity, with: replacement)
        }

        // Decimal numeric entities: &#12290; → 。
        if let regex = try? NSRegularExpression(pattern: "&#(\\d+);", options: []) {
            let nsRange = NSRange(result.startIndex..., in: result)
            let matches = regex.matches(in: result, options: [], range: nsRange)
            for match in matches.reversed() {
                guard let numRange = Range(match.range(at: 1), in: result),
                      let fullRange = Range(match.range, in: result),
                      let codePoint = UInt32(result[numRange]),
                      let scalar = UnicodeScalar(codePoint) else { continue }
                result.replaceSubrange(fullRange, with: String(scalar))
            }
        }

        // Hex numeric entities: &#x4E2D; → 中
        if let regex = try? NSRegularExpression(pattern: "&#[xX]([0-9a-fA-F]+);", options: []) {
            let nsRange = NSRange(result.startIndex..., in: result)
            let matches = regex.matches(in: result, options: [], range: nsRange)
            for match in matches.reversed() {
                guard let hexRange = Range(match.range(at: 1), in: result),
                      let fullRange = Range(match.range, in: result),
                      let codePoint = UInt32(result[hexRange], radix: 16),
                      let scalar = UnicodeScalar(codePoint) else { continue }
                result.replaceSubrange(fullRange, with: String(scalar))
            }
        }

        return result
    }
}
