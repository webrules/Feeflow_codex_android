import Foundation
// NOTE: You must add the Google Generative AI SDK for Swift package to your project.
// URL: https://github.com/google/generative-ai-swift
#if canImport(GoogleGenerativeAI)
import GoogleGenerativeAI

class GeminiService {
    private var apiKey: String? {
        DatabaseManager.shared.getEncryptedSetting(key: "gemini_api_key")
    }
    
    private var model: GenerativeModel?
    
    init() {
        if let key = apiKey, !key.isEmpty {
            self.model = GenerativeModel(name: "gemini-pro-latest", apiKey: key)
        }
    }
    
    func generateSummary(for content: String) async throws -> String {
        guard let model = model else {
             return "Please set your Gemini API Key in Settings (top left icon on home page)."
        }

        let currentLanguage = LocalizationManager.shared.currentLanguage
        let outputLanguageInstruction = currentLanguage == "zh"
            ? "Respond in Simplified Chinese."
            : "Respond in English."
        
        let prompt = """
        You are a helpful assistant. Please summarize the following forum discussion clearly and concisely.
        Identify the main topic, key arguments or points made, and the general sentiment if applicable.
        \(outputLanguageInstruction)
        
        Content:
        \(content.prefix(10000)) // Limit length to avoid token limits if necessary
        """
        
        let response = try await model.generateContent(prompt)
        return response.text ?? "Unable to generate summary."
    }
}
#else
class GeminiService {
    func generateSummary(for content: String) async throws -> String {
        // Fallback or Mock implementation when the package is not yet added
        AppLogger.debug("⚠️ GoogleGenerativeAI package is missing. Please add https://github.com/google/generative-ai-swift to your Xcode project.")
        try? await Task.sleep(nanoseconds: 1 * 1_000_000_000)
        return """
        [Dependency Missing]
        
        To enable real AI summaries:
        1. Open Xcode
        2. Go to File > Add Package Dependencies...
        3. Enter: https://github.com/google/generative-ai-swift
        4. Add to 'Feedflow' target.
        """
    }
}
#endif
