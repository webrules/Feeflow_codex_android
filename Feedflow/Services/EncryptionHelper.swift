import Foundation
import CryptoKit
import Security

/// Encryption utility for storing credentials locally.
/// Uses AES-GCM with a random per-install key stored in the iOS Keychain.
class EncryptionHelper {
    static let shared = EncryptionHelper()
    
    private let encryptionKey: SymmetricKey
    private static let keychainAccount = "com.feedflow.encryption-key"
    
    private init() {
        self.encryptionKey = EncryptionHelper.getOrCreateKey()
    }
    
    /// Retrieves an existing key from Keychain, or generates and stores a new random one.
    private static func getOrCreateKey() -> SymmetricKey {
        if let existingKeyData = loadFromKeychain() {
            return SymmetricKey(data: existingKeyData)
        }
        
        // Generate a new random 256-bit key
        let newKey = SymmetricKey(size: .bits256)
        let keyData = newKey.withUnsafeBytes { Data($0) }
        saveToKeychain(keyData)
        return newKey
    }
    
    private static func loadFromKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrService as String: "Feedflow",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        if status == errSecSuccess, let data = result as? Data {
            return data
        }
        return nil
    }
    
    private static func saveToKeychain(_ data: Data) {
        // Delete any existing entry first
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrService as String: "Feedflow"
        ]
        SecItemDelete(deleteQuery as CFDictionary)
        
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrService as String: "Feedflow",
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        
        let status = SecItemAdd(addQuery as CFDictionary, nil)
        if status != errSecSuccess {
            print("[EncryptionHelper] Warning: Failed to save key to Keychain (status: \(status))")
        }
    }
    
    /// Encrypts a string and returns a Base64-encoded ciphertext.
    func encrypt(_ plaintext: String) -> String? {
        guard let data = plaintext.data(using: .utf8) else { return nil }
        
        do {
            let sealedBox = try AES.GCM.seal(data, using: encryptionKey)
            return sealedBox.combined?.base64EncodedString()
        } catch {
            print("[EncryptionHelper] Encryption error: \(error)")
            return nil
        }
    }
    
    /// Decrypts a Base64-encoded ciphertext and returns the original string.
    func decrypt(_ ciphertext: String) -> String? {
        guard let data = Data(base64Encoded: ciphertext) else { return nil }
        
        do {
            let sealedBox = try AES.GCM.SealedBox(combined: data)
            let decryptedData = try AES.GCM.open(sealedBox, using: encryptionKey)
            return String(data: decryptedData, encoding: .utf8)
        } catch {
            print("[EncryptionHelper] Decryption error: \(error)")
            return nil
        }
    }
}
