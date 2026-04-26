package com.myfinances.app.integrations.cajaingenieros.sync

import com.myfinances.app.domain.model.integration.ExternalConnection
import com.myfinances.app.domain.model.integration.ExternalConnectionStatus
import com.myfinances.app.domain.model.integration.ExternalProviderId
import com.myfinances.app.domain.model.integration.ExternalSyncRun
import com.myfinances.app.domain.model.integration.ExternalSyncStatus
import com.myfinances.app.domain.repository.ExternalConnectionsRepository
import com.myfinances.app.integrations.statements.StatementImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val CAJA_INGENIEROS_START_URL = "https://www.cajaingenieros.es/"

class DesktopCajaIngenierosBrowserSyncService(
    private val externalConnectionsRepository: ExternalConnectionsRepository,
    private val statementImportService: StatementImportService,
) : CajaIngenierosBrowserSyncService {
    override val isSupported: Boolean = statementImportService.isSupported

    override suspend fun runAssistedStatementSync(connectionId: String): ExternalSyncRun? {
        check(isSupported) {
            "Browser-assisted Caja Ingenieros sync is not available on this platform yet."
        }

        val connection = loadConnection(connectionId)
        val startedAt = Clock.System.now().toEpochMilliseconds()
        markConnectionRunning(connection, startedAt)

        return runCatching {
            val downloadedStatementPath = withContext(Dispatchers.IO) { openBrowserAndWaitForPdf(startedAt) }
            val importResult = statementImportService.importCajaIngenierosPdfFromFile(
                downloadedStatementPath.toAbsolutePath().toString(),
            )
            val finishedAt = Clock.System.now().toEpochMilliseconds()
            val syncRun = ExternalSyncRun(
                id = buildSyncRunId(connectionId, finishedAt),
                connectionId = connectionId,
                providerId = ExternalProviderId.CAJA_INGENIEROS,
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = finishedAt,
                status = ExternalSyncStatus.SUCCESS,
                importedAccounts = 1,
                importedTransactions = importResult.importedTransactions,
                importedPositions = 0,
                message = buildSuccessMessage(importResult),
            )
            externalConnectionsRepository.recordSyncRun(syncRun)
            externalConnectionsRepository.upsertConnection(
                connection.copy(
                    status = ExternalConnectionStatus.CONNECTED,
                    lastSuccessfulSyncEpochMs = finishedAt,
                    lastSyncAttemptEpochMs = finishedAt,
                    lastSyncStatus = ExternalSyncStatus.SUCCESS,
                    lastErrorMessage = null,
                    updatedAtEpochMs = finishedAt,
                ),
            )
            syncRun
        }.getOrElse { throwable ->
            if (throwable is BrowserSyncCanceledException) {
                restoreConnectionAfterCancellation(connection, startedAt)
                return null
            }

            val finishedAt = Clock.System.now().toEpochMilliseconds()
            val syncRun = ExternalSyncRun(
                id = buildSyncRunId(connectionId, finishedAt),
                connectionId = connectionId,
                providerId = ExternalProviderId.CAJA_INGENIEROS,
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = finishedAt,
                status = ExternalSyncStatus.FAILED,
                importedAccounts = 0,
                importedTransactions = 0,
                importedPositions = 0,
                message = throwable.message ?: "Caja Ingenieros browser-assisted sync failed.",
            )
            externalConnectionsRepository.recordSyncRun(syncRun)
            externalConnectionsRepository.upsertConnection(
                connection.copy(
                    status = ExternalConnectionStatus.NEEDS_ATTENTION,
                    lastSyncAttemptEpochMs = finishedAt,
                    lastSyncStatus = ExternalSyncStatus.FAILED,
                    lastErrorMessage = syncRun.message,
                    updatedAtEpochMs = finishedAt,
                ),
            )
            throw throwable
        }
    }

    private suspend fun loadConnection(connectionId: String): ExternalConnection =
        externalConnectionsRepository
            .observeConnections()
            .first()
            .firstOrNull { connection -> connection.id == connectionId }
            ?: error("Caja Ingenieros connection not found.")

    private suspend fun markConnectionRunning(
        connection: ExternalConnection,
        startedAt: Long,
    ) {
        externalConnectionsRepository.upsertConnection(
            connection.copy(
                status = ExternalConnectionStatus.SYNCING,
                lastSyncAttemptEpochMs = startedAt,
                lastSyncStatus = ExternalSyncStatus.RUNNING,
                lastErrorMessage = null,
                updatedAtEpochMs = startedAt,
            ),
        )
    }

    private suspend fun restoreConnectionAfterCancellation(
        connection: ExternalConnection,
        updatedAt: Long,
    ) {
        externalConnectionsRepository.upsertConnection(
            connection.copy(
                status = if (connection.lastSuccessfulSyncEpochMs != null) {
                    ExternalConnectionStatus.CONNECTED
                } else {
                    connection.status
                },
                lastSyncStatus = connection.lastSyncStatus,
                lastErrorMessage = connection.lastErrorMessage,
                updatedAtEpochMs = updatedAt,
            ),
        )
    }
}

private suspend fun openBrowserAndWaitForPdf(startedAtEpochMs: Long): Path {
    val downloadsDir = defaultDownloadsDirectory()
    require(Files.isDirectory(downloadsDir)) {
        "Could not find the default Downloads folder at ${downloadsDir.toAbsolutePath()}."
    }

    openDefaultBrowser(URI(CAJA_INGENIEROS_START_URL))
    return awaitDownloadedPdf(downloadsDir, startedAtEpochMs)
}

private fun openDefaultBrowser(uri: URI) {
    if (!Desktop.isDesktopSupported()) {
        error("Desktop browser launch is not supported on this machine.")
    }
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        error("Opening the default browser is not supported on this machine.")
    }
    try {
        desktop.browse(uri)
    } catch (exception: IOException) {
        throw IllegalStateException("Could not open the default browser for Caja Ingenieros sync.", exception)
    }
}

private suspend fun awaitDownloadedPdf(
    downloadsDir: Path,
    startedAtEpochMs: Long,
): Path {
    val deadline = Clock.System.now() + 5.minutes
    var newestCandidate: Path? = null
    var newestCandidateTime = FileTime.fromMillis(0L)

    while (Clock.System.now() < deadline) {
        Files.list(downloadsDir).use { files ->
            files
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.fileName.toString().endsWith(".pdf", ignoreCase = true) }
                .forEach { path ->
                    val modifiedTime = Files.getLastModifiedTime(path)
                    if (modifiedTime.toMillis() >= startedAtEpochMs && modifiedTime > newestCandidateTime) {
                        newestCandidate = path
                        newestCandidateTime = modifiedTime
                    }
                }
        }

        if (newestCandidate != null) {
            return newestCandidate
        }

        delay(1.seconds)
    }

    throw BrowserSyncCanceledException(
        "No Caja Ingenieros PDF download was detected in ${downloadsDir.toAbsolutePath()} within 5 minutes. Start the browser sync again when you are ready to log in and download a statement.",
    )
}

private fun defaultDownloadsDirectory(): Path =
    Paths.get(System.getProperty("user.home"), "Downloads")

private fun buildSyncRunId(connectionId: String, timestampMs: Long): String =
    "sync-caja-browser-$connectionId-$timestampMs-${Random.nextInt(1000, 9999)}"

private fun buildSuccessMessage(result: com.myfinances.app.integrations.statements.StatementImportResult): String =
    "Imported ${result.importedTransactions} new Caja Ingenieros transactions from ${result.sourceFileName}. Skipped ${result.skippedTransactions} already-known rows."

private class BrowserSyncCanceledException(
    message: String,
) : IllegalStateException(message)
