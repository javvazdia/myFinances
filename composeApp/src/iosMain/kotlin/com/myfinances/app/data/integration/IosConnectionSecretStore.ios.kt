package com.myfinances.app.data.integration

import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.logging.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSStringEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
class IosConnectionSecretStore : ConnectionSecretStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    ) {
        val secretKey = buildConnectionSecretKey(providerId, connectionId)
        val query = baseQuery(secretKey)
        val secretData = secret.encodeToByteArray().toNSData()
        val attributes = mutableMapOf<Any?, Any?>(
            kSecValueData to secretData,
        )

        val updateStatus = SecItemUpdate(
            query.toNSMutableDictionary(),
            attributes.toNSMutableDictionary(),
        )
        val finalStatus = if (updateStatus == errSecItemNotFound) {
            SecItemAdd(
                (query + attributes).toNSMutableDictionary(),
                null,
            )
        } else {
            updateStatus
        }

        if (finalStatus != errSecSuccess) {
            error("Keychain save failed with status $finalStatus")
        }

        defaults.removeObjectForKey(secretKey)
    }

    override suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String? {
        val secretKey = buildConnectionSecretKey(providerId, connectionId)
        val query = baseQuery(secretKey) + mapOf(
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )

        val keychainValue = memScoped {
            val result = alloc<CFTypeRefVar>()
            when (SecItemCopyMatching(query.toNSMutableDictionary(), result.ptr)) {
                errSecSuccess -> (result.value as? NSData)?.toUtf8String()
                errSecItemNotFound -> null
                else -> {
                    AppLogger.error(
                        tag = "Secrets",
                        message = "iOS Keychain read failed for $secretKey",
                    )
                    null
                }
            }
        }

        if (keychainValue != null) return keychainValue

        return defaults.stringForKey(secretKey)?.also { legacySecret ->
            runCatching {
                saveSecret(providerId, connectionId, legacySecret)
                AppLogger.debug(
                    tag = "Secrets",
                    message = "Migrated iOS connection secret into Keychain storage.",
                )
            }
        }
    }

    override suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ) {
        val secretKey = buildConnectionSecretKey(providerId, connectionId)
        val status = SecItemDelete(baseQuery(secretKey).toNSMutableDictionary())
        if (status != errSecSuccess && status != errSecItemNotFound) {
            AppLogger.error(
                tag = "Secrets",
                message = "iOS Keychain delete failed for $secretKey with status $status",
            )
        }
        defaults.removeObjectForKey(secretKey)
    }

    private fun baseQuery(secretKey: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to KEYCHAIN_SERVICE_NAME,
        kSecAttrAccount to secretKey,
    )

    private companion object {
        private const val KEYCHAIN_SERVICE_NAME = "com.myfinances.connection-secrets"
    }
}

private fun Map<Any?, Any?>.toNSMutableDictionary(): NSMutableDictionary =
    NSMutableDictionary().apply {
        forEach { (key, value) ->
            setObject(value, forKey = key)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(
        bytes = pinned.addressOf(0),
        length = size.toULong(),
    )
}

private fun NSData.toUtf8String(): String? =
    NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()
