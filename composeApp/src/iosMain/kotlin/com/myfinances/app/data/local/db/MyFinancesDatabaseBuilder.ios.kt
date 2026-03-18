package com.myfinances.app.data.local.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

fun getMyFinancesDatabaseBuilder(): RoomDatabase.Builder<MyFinancesDatabase> {
    val documentDirectory = NSFileManager.defaultManager
        .URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        ?.path ?: error("Could not resolve iOS documents directory")

    val databasePath = "$documentDirectory/$MY_FINANCES_DB_NAME"

    return Room.databaseBuilder<MyFinancesDatabase>(
        name = databasePath,
    ).addMigrations(MIGRATION_1_TO_2)
        .setDriver(BundledSQLiteDriver())
}

fun createMyFinancesDatabase(): MyFinancesDatabase =
    getMyFinancesDatabaseBuilder().build()
