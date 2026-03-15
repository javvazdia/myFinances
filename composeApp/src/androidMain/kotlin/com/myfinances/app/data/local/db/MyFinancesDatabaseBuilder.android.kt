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
    ).setDriver(BundledSQLiteDriver())
}

fun createMyFinancesDatabase(context: Context): MyFinancesDatabase =
    getMyFinancesDatabaseBuilder(context).build()

