package com.webrules.feedflow.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String?
}

class AesGcmSecretStore(
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
) : SecretStore {
    private val random = SecureRandom()

    override fun encrypt(plainText: String): String {
        val nonce = ByteArray(12)
        random.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + encrypted)
    }

    override fun decrypt(cipherText: String): String? = runCatching {
        val payload = Base64.getDecoder().decode(cipherText)
        require(payload.size > 12)
        val nonce = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()
}
