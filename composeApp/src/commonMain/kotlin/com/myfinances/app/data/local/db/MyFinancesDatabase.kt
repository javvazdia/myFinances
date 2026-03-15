package com.myfinances.app.data.local.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.myfinances.app.data.local.dao.AccountDao
import com.myfinances.app.data.local.dao.CategoryDao
import com.myfinances.app.data.local.dao.TransactionDao
import com.myfinances.app.data.local.entity.AccountEntity
import com.myfinances.app.data.local.entity.CategoryEntity
import com.myfinances.app.data.local.entity.TransactionEntity

const val MY_FINANCES_DB_NAME = "my_finances.db"

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(FinanceTypeConverters::class)
@ConstructedBy(MyFinancesDatabaseConstructor::class)
abstract class MyFinancesDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao
}

@Suppress("KotlinNoActualForExpect")
expect object MyFinancesDatabaseConstructor : RoomDatabaseConstructor<MyFinancesDatabase> {
    override fun initialize(): MyFinancesDatabase
}

