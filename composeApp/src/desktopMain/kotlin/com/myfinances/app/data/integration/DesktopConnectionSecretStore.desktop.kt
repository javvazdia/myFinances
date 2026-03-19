package com.myfinances.app.data.integration

import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.repository.ConnectionSecretStore
import com.myfinances.app.logging.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DesktopConnectionSecretStore : ConnectionSecretStore {
    private val mutex = Mutex()
    private val directory = File(System.getProperty("user.home"), ".myfinances").apply { mkdirs() }
    private val legacyFile = File(directory, "connection-secrets.properties")
    private val backend: DesktopSecretBackend = createDesktopSecretBackend(directory)
    private var didMigrateLegacySecrets = false

    override suspend fun saveSecret(
        providerId: ExternalProviderId,
        connectionId: String,
        secret: String,
    ) {
        mutex.withLock {
            migrateLegacySecretsIfNeeded()
            backend.save(buildConnectionSecretKey(providerId, connectionId), secret)
        }
    }

    override suspend fun readSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ): String? = mutex.withLock {
        migrateLegacySecretsIfNeeded()
        backend.read(buildConnectionSecretKey(providerId, connectionId))
    }

    override suspend fun deleteSecret(
        providerId: ExternalProviderId,
        connectionId: String,
    ) {
        mutex.withLock {
            migrateLegacySecretsIfNeeded()
            backend.delete(buildConnectionSecretKey(providerId, connectionId))
        }
    }

    private fun migrateLegacySecretsIfNeeded() {
        if (didMigrateLegacySecrets) return
        didMigrateLegacySecrets = true
        if (!legacyFile.exists()) return

        runCatching {
            val properties = Properties().apply {
                legacyFile.inputStream().use(::load)
            }
            properties.stringPropertyNames().forEach { secretKey ->
                properties.getProperty(secretKey)?.let { secret ->
                    backend.save(secretKey, secret)
                }
            }
            if (legacyFile.delete()) {
                AppLogger.debug(
                    tag = "Secrets",
                    message = "Migrated desktop connection secrets away from the legacy plaintext file.",
                )
            }
        }.onFailure { throwable ->
            AppLogger.error(
                tag = "Secrets",
                message = "Desktop secret migration failed: ${throwable.message}",
                throwable = throwable,
            )
            didMigrateLegacySecrets = false
        }
    }
}

private fun createDesktopSecretBackend(directory: File): DesktopSecretBackend {
    val encryptedFallback = EncryptedFileDesktopSecretBackend(
        file = File(directory, "connection-secrets.enc"),
    )
    val primary = when {
        DesktopOs.current.isWindows -> WindowsDpapiDesktopSecretBackend(File(directory, "secrets-windows"))
        DesktopOs.current.isMacOs -> MacOsKeychainSecretBackend()
        DesktopOs.current.isLinux && LinuxSecretToolSecretBackend.isAvailable() -> LinuxSecretToolSecretBackend()
        else -> null
    }

    if (primary == null) {
        AppLogger.debug(
            tag = "Secrets",
            message = "Using encrypted desktop fallback secret storage for ${DesktopOs.current.displayName}.",
        )
        return encryptedFallback
    }

    AppLogger.debug(
        tag = "Secrets",
        message = "Using native desktop secret storage for ${DesktopOs.current.displayName}.",
    )
    return MirroringDesktopSecretBackend(
        primary = primary,
        fallback = encryptedFallback,
    )
}

private interface DesktopSecretBackend {
    fun save(secretKey: String, secret: String)

    fun read(secretKey: String): String?

    fun delete(secretKey: String)
}

private class MirroringDesktopSecretBackend(
    private val primary: DesktopSecretBackend,
    private val fallback: DesktopSecretBackend,
) : DesktopSecretBackend {
    override fun save(secretKey: String, secret: String) {
        runCatching {
            primary.save(secretKey, secret)
            fallback.delete(secretKey)
        }.onFailure { throwable ->
            AppLogger.error(
                tag = "Secrets",
                message = "Native desktop secret save failed. Falling back to encrypted local storage.",
                throwable = throwable,
            )
            fallback.save(secretKey, secret)
        }
    }

    override fun read(secretKey: String): String? {
        val primarySecret = runCatching {
            primary.read(secretKey)
        }.onFailure { throwable ->
            AppLogger.error(
                tag = "Secrets",
                message = "Native desktop secret read failed. Trying encrypted local fallback.",
                throwable = throwable,
            )
        }.getOrNull()

        if (primarySecret != null) return primarySecret

        val fallbackSecret = fallback.read(secretKey) ?: return null
        runCatching {
            primary.save(secretKey, fallbackSecret)
            fallback.delete(secretKey)
        }.onFailure { throwable ->
            AppLogger.error(
                tag = "Secrets",
                message = "Desktop secret re-mirroring failed after reading from fallback storage.",
                throwable = throwable,
            )
        }
        return fallbackSecret
    }

    override fun delete(secretKey: String) {
        runCatching {
            primary.delete(secretKey)
        }.onFailure { throwable ->
            AppLogger.error(
                tag = "Secrets",
                message = "Native desktop secret delete failed.",
                throwable = throwable,
            )
        }
        fallback.delete(secretKey)
    }
}

private class EncryptedFileDesktopSecretBackend(
    private val file: File,
) : DesktopSecretBackend {
    private val preferences = Preferences.userRoot().node("com/myfinances/desktop-secret-store")

    override fun save(secretKey: String, secret: String) {
        val properties = loadProperties()
        properties.setProperty(secretKey, encrypt(secret))
        storeProperties(properties)
    }

    override fun read(secretKey: String): String? {
        val storedValue = loadProperties().getProperty(secretKey) ?: return null
        return decrypt(storedValue)
    }

    override fun delete(secretKey: String) {
        val properties = loadProperties()
        properties.remove(secretKey)
        storeProperties(properties)
    }

    private fun loadProperties(): Properties = Properties().apply {
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

    private fun storeProperties(properties: Properties) {
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            properties.store(stream, "myFinances encrypted desktop connection secrets")
        }
    }

    private fun encrypt(secret: String): String {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(loadOrCreateMasterKey(), "AES"),
            GCMParameterSpec(128, iv),
        )
        val encrypted = cipher.doFinal(secret.toByteArray(UTF_8))
        return "${base64Encode(iv)}:${base64Encode(encrypted)}"
    }

    private fun decrypt(payload: String): String? = runCatching {
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(loadOrCreateMasterKey(), "AES"),
            GCMParameterSpec(128, base64Decode(parts[0])),
        )
        cipher.doFinal(base64Decode(parts[1])).toString(UTF_8)
    }.getOrNull()

    private fun loadOrCreateMasterKey(): ByteArray {
        val existing = preferences.get("master-key-v1", null)
        if (existing != null) {
            return base64Decode(existing)
        }

        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        preferences.put("master-key-v1", base64Encode(key))
        return key
    }
}

private class WindowsDpapiDesktopSecretBackend(
    private val directory: File,
) : DesktopSecretBackend {
    init {
        directory.mkdirs()
    }

    override fun save(secretKey: String, secret: String) {
        val encryptedSecret = runPowerShell(
            script = """
                ${'$'}encoded = [Console]::In.ReadToEnd().Trim()
                ${'$'}value = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(${'$'}encoded))
                ${'$'}secure = ConvertTo-SecureString -String ${'$'}value -AsPlainText -Force
                ConvertFrom-SecureString -SecureString ${'$'}secure
            """.trimIndent(),
            stdin = base64Encode(secret.toByteArray(UTF_8)),
        ).trim()
        secretFile(secretKey).writeText(encryptedSecret, UTF_8)
    }

    override fun read(secretKey: String): String? {
        val file = secretFile(secretKey)
        if (!file.exists()) return null
        val encryptedSecret = file.readText(UTF_8).trim()
        if (encryptedSecret.isBlank()) return null
        return runPowerShell(
            script = """
                ${'$'}encrypted = [Console]::In.ReadToEnd().Trim()
                ${'$'}secure = ConvertTo-SecureString -String ${'$'}encrypted
                ${'$'}bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(${'$'}secure)
                try {
                    [Runtime.InteropServices.Marshal]::PtrToStringBSTR(${'$'}bstr)
                } finally {
                    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR(${'$'}bstr)
                }
            """.trimIndent(),
            stdin = encryptedSecret,
        ).trim().ifBlank { null }
    }

    override fun delete(secretKey: String) {
        secretFile(secretKey).delete()
    }

    private fun secretFile(secretKey: String): File =
        File(directory, "${base64UrlEncode(secretKey.toByteArray(UTF_8))}.secret")
}

private class MacOsKeychainSecretBackend : DesktopSecretBackend {
    override fun save(secretKey: String, secret: String) {
        runCommand(
            command = listOf(
                "security",
                "add-generic-password",
                "-U",
                "-a",
                secretKey,
                "-s",
                KEYCHAIN_SERVICE_NAME,
                "-w",
                secret,
            ),
        )
    }

    override fun read(secretKey: String): String? {
        val result = runCommandCatching(
            command = listOf(
                "security",
                "find-generic-password",
                "-a",
                secretKey,
                "-s",
                KEYCHAIN_SERVICE_NAME,
                "-w",
            ),
        )
        if (result.exitCode != 0) {
            return if (result.stderr.contains("could not be found", ignoreCase = true)) {
                null
            } else {
                error(result.stderr.ifBlank { "macOS Keychain lookup failed." })
            }
        }

        return result.stdout.trim().ifBlank { null }
    }

    override fun delete(secretKey: String) {
        val result = runCommandCatching(
            command = listOf(
                "security",
                "delete-generic-password",
                "-a",
                secretKey,
                "-s",
                KEYCHAIN_SERVICE_NAME,
            ),
        )
        if (result.exitCode != 0 && !result.stderr.contains("could not be found", ignoreCase = true)) {
            error(result.stderr.ifBlank { "macOS Keychain delete failed." })
        }
    }

    private companion object {
        private const val KEYCHAIN_SERVICE_NAME = "com.myfinances.connection-secrets"
    }
}

private class LinuxSecretToolSecretBackend : DesktopSecretBackend {
    override fun save(secretKey: String, secret: String) {
        runCommand(
            command = listOf(
                "secret-tool",
                "store",
                "--label=myFinances connection secret",
                "service",
                SECRET_SERVICE_NAME,
                "account",
                secretKey,
            ),
            stdin = secret,
        )
    }

    override fun read(secretKey: String): String? {
        val result = runCommandCatching(
            command = listOf(
                "secret-tool",
                "lookup",
                "service",
                SECRET_SERVICE_NAME,
                "account",
                secretKey,
            ),
        )
        return if (result.exitCode == 0) {
            result.stdout.trim().ifBlank { null }
        } else {
            null
        }
    }

    override fun delete(secretKey: String) {
        runCommandCatching(
            command = listOf(
                "secret-tool",
                "clear",
                "service",
                SECRET_SERVICE_NAME,
                "account",
                secretKey,
            ),
        )
    }

    companion object {
        private const val SECRET_SERVICE_NAME = "com.myfinances.connection-secrets"

        fun isAvailable(): Boolean =
            runCommandCatching(listOf("secret-tool", "--help")).exitCode == 0
    }
}

private data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

private fun runCommand(
    command: List<String>,
    stdin: String? = null,
): String {
    val result = runCommandCatching(command, stdin)
    if (result.exitCode != 0) {
        error(result.stderr.ifBlank { "Command failed: ${command.joinToString(" ")}" })
    }
    return result.stdout
}

private fun runCommandCatching(
    command: List<String>,
    stdin: String? = null,
): CommandResult = runCatching {
    val process = ProcessBuilder(command)
        .redirectErrorStream(false)
        .start()

    if (stdin != null) {
        process.outputStream.bufferedWriter(UTF_8).use { writer ->
            writer.write(stdin)
        }
    } else {
        process.outputStream.close()
    }

    val stdout = process.inputStream.bufferedReader(UTF_8).readText()
    val stderr = process.errorStream.bufferedReader(UTF_8).readText()
    val exitCode = process.waitFor()
    CommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
}.getOrElse { throwable ->
    CommandResult(
        exitCode = -1,
        stdout = "",
        stderr = throwable.message ?: "Process failed",
    )
}

private fun runPowerShell(
    script: String,
    stdin: String? = null,
): String = runCommand(
    command = buildList {
        add("powershell.exe")
        add("-NoProfile")
        add("-NonInteractive")
        add("-Command")
        add(script)
    },
    stdin = stdin,
)

private object DesktopOs {
    private val rawName = System.getProperty("os.name").orEmpty().lowercase()

    val current = DesktopOsInfo(
        displayName = when {
            rawName.contains("win") -> "Windows"
            rawName.contains("mac") -> "macOS"
            rawName.contains("linux") -> "Linux"
            else -> rawName.ifBlank { "desktop" }
        },
        isWindows = rawName.contains("win"),
        isMacOs = rawName.contains("mac"),
        isLinux = rawName.contains("linux"),
    )
}

private data class DesktopOsInfo(
    val displayName: String,
    val isWindows: Boolean,
    val isMacOs: Boolean,
    val isLinux: Boolean,
)

private fun base64Encode(value: ByteArray): String =
    Base64.getEncoder().encodeToString(value)

private fun base64Decode(value: String): ByteArray =
    Base64.getDecoder().decode(value)

private fun base64UrlEncode(value: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value)
