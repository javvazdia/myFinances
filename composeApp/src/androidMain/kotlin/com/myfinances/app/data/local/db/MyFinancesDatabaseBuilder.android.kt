package com.myfinances.app.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun getMyFinancesDatabaseBuilder(
    context: Context,
): RoomDatabase.Builder<MyFinancesDatabase> {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath(MY_FINANCES_DB_NAME)
    databaseFile.parentFile?.mkdirs()

    return Room.databaseBuilder<MyFinancesDatabase>(
        context = appContext,
        name = databaseFile.absolutePath,
    ).addMigrations(MIGRATION_1_TO_2, MIGRATION_2_TO_3, MIGRATION_3_TO_4)
        .setDriver(BundledSQLiteDriver())
}

fun createMyFinancesDatabase(context: Context): MyFinancesDatabase =
    getMyFinancesDatabaseBuilder(context).build()
