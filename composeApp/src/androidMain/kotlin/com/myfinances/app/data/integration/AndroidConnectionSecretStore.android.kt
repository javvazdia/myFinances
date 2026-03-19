package com.myfinances.app.data.integration

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.logging.AppLogger
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidConnectionSecretStore(
    context: Context,
) : ConnectionSecretStore {
    private val preferences = context.getSharedPreferences("connection_secrets", Context.MODE_PRIVATE)

    override suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    ) {
        preferences.edit()
            .putString(buildConnectionSecretKey(providerId, connectionId), encrypt(secret))
            .apply()
    }

    override suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String? {
        val preferenceKey = buildConnectionSecretKey(providerId, connectionId)
        val storedValue = preferences.getString(preferenceKey, null) ?: return null
        return if (storedValue.startsWith(ENCRYPTED_PREFIX)) {
            decrypt(storedValue.removePrefix(ENCRYPTED_PREFIX))
        } else {
            storedValue.also { plaintextSecret ->
                runCatching {
                    preferences.edit()
                        .putString(preferenceKey, encrypt(plaintextSecret))
                        .apply()
                }.onSuccess {
                    AppLogger.debug(
                        tag = "Secrets",
                        message = "Migrated Android connection secret into keystore-backed storage.",
                    )
                }
            }
        }
    }

    override suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ) {
        preferences.edit()
            .remove(buildConnectionSecretKey(providerId, connectionId))
            .apply()
    }

    private fun encrypt(secret: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(secret.toByteArray(UTF_8))
        return ENCRYPTED_PREFIX + encode(iv) + ":" + encode(encryptedBytes)
    }

    private fun decrypt(payload: String): String? = runCatching {
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            loadOrCreateSecretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, decode(parts[0])),
        )
        cipher.doFinal(decode(parts[1])).toString(UTF_8)
    }.onFailure { throwable ->
        AppLogger.error(
            tag = "Secrets",
            message = "Android secret decryption failed: ${throwable.message}",
            throwable = throwable,
        )
    }.getOrNull()

    private fun loadOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "com.myfinances.connection-secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "enc_v1:"
        private const val TAG_LENGTH_BITS = 128
    }
}
