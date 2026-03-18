package com.myfinances.app.data.local.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

fun getMyFinancesDatabaseBuilder(): RoomDatabase.Builder<MyFinancesDatabase> {
    val parentDirectory = File(System.getProperty("user.home"), ".myfinances")
    parentDirectory.mkdirs()

    val databaseFile = File(parentDirectory, MY_FINANCES_DB_NAME)

    return Room.databaseBuilder<MyFinancesDatabase>(
        name = databaseFile.absolutePath,
    ).addMigrations(MIGRATION_1_TO_2)
        .setDriver(BundledSQLiteDriver())
}

fun createMyFinancesDatabase(): MyFinancesDatabase =
    getMyFinancesDatabaseBuilder().build()
