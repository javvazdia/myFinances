package com.myfinances.app.data.local.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.myfinances.app.data.local.dao.AccountDao
import com.myfinances.app.data.local.dao.CategoryDao
import com.myfinances.app.data.local.dao.ExternalAccountLinksDao
import com.myfinances.app.data.local.dao.ExternalConnectionsDao
import com.myfinances.app.data.local.dao.ExternalSyncRunsDao
import com.myfinances.app.data.local.dao.TransactionDao
import com.myfinances.app.data.local.entity.AccountEntity
import com.myfinances.app.data.local.entity.CategoryEntity
import com.myfinances.app.data.local.entity.ExternalAccountLinkEntity
import com.myfinances.app.data.local.entity.ExternalConnectionEntity
import com.myfinances.app.data.local.entity.ExternalSyncRunEntity
import com.myfinances.app.data.local.entity.TransactionEntity

const val MY_FINANCES_DB_NAME = "my_finances.db"

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        ExternalConnectionEntity::class,
        ExternalAccountLinkEntity::class,
        ExternalSyncRunEntity::class,
        TransactionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(FinanceTypeConverters::class)
@ConstructedBy(MyFinancesDatabaseConstructor::class)
abstract class MyFinancesDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun externalConnectionsDao(): ExternalConnectionsDao

    abstract fun externalAccountLinksDao(): ExternalAccountLinksDao

    abstract fun externalSyncRunsDao(): ExternalSyncRunsDao

    abstract fun transactionDao(): TransactionDao
}

@Suppress("KotlinNoActualForExpect")
expect object MyFinancesDatabaseConstructor : RoomDatabaseConstructor<MyFinancesDatabase> {
    override fun initialize(): MyFinancesDatabase
}

val MIGRATION_1_TO_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS external_connections (
                id TEXT NOT NULL PRIMARY KEY,
                provider_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                status TEXT NOT NULL,
                external_user_id TEXT,
                last_successful_sync_epoch_ms INTEGER,
                last_sync_attempt_epoch_ms INTEGER,
                last_sync_status TEXT NOT NULL,
                last_error_message TEXT,
                created_at_epoch_ms INTEGER NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS external_account_links (
                connection_id TEXT NOT NULL,
                provider_account_id TEXT NOT NULL,
                local_account_id TEXT,
                account_display_name TEXT NOT NULL,
                account_type_label TEXT,
                currency_code TEXT,
                last_imported_at_epoch_ms INTEGER,
                PRIMARY KEY(connection_id, provider_account_id),
                FOREIGN KEY(connection_id) REFERENCES external_connections(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS external_sync_runs (
                id TEXT NOT NULL PRIMARY KEY,
                connection_id TEXT NOT NULL,
                provider_id TEXT NOT NULL,
                started_at_epoch_ms INTEGER NOT NULL,
                finished_at_epoch_ms INTEGER,
                status TEXT NOT NULL,
                imported_accounts INTEGER NOT NULL,
                imported_transactions INTEGER NOT NULL,
                imported_positions INTEGER NOT NULL,
                message TEXT,
                FOREIGN KEY(connection_id) REFERENCES external_connections(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_external_account_links_connection_id ON external_account_links(connection_id)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_external_account_links_local_account_id ON external_account_links(local_account_id)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_external_sync_runs_connection_id ON external_sync_runs(connection_id)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_external_sync_runs_provider_id ON external_sync_runs(provider_id)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_external_sync_runs_started_at_epoch_ms ON external_sync_runs(started_at_epoch_ms)",
        )
    }
}
