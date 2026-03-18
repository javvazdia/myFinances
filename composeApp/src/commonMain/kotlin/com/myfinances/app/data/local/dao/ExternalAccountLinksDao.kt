package com.myfinances.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfinances.app.data.local.entity.ExternalAccountLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalAccountLinksDao {
    @Query("SELECT * FROM external_account_links WHERE connection_id = :connectionId ORDER BY account_display_name ASC")
    fun observeAccountLinks(connectionId: String): Flow<List<ExternalAccountLinkEntity>>

    @Upsert
    suspend fun upsertAccountLinks(links: List<ExternalAccountLinkEntity>)

    @Query("DELETE FROM external_account_links WHERE connection_id = :connectionId")
    suspend fun deleteAccountLinksForConnection(connectionId: String)
}
