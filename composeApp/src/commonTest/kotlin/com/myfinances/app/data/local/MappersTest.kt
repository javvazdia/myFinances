package com.myfinances.app.data.local

import com.myfinances.app.domain.model.Account
import com.myfinances.app.domain.model.AccountSourceType
import com.myfinances.app.domain.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {
    @Test
    fun accountMappingRoundTripsCoreFields() {
        val account = Account(
            id = "acc-main",
            name = "Main account",
            type = AccountType.CHECKING,
            currencyCode = "EUR",
            openingBalanceMinor = 125_000,
            sourceType = AccountSourceType.MANUAL,
            sourceProvider = null,
            externalAccountId = null,
            lastSyncedAtEpochMs = null,
            isArchived = false,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
        )

        val roundTrip = AccountEntityMapper.toDomain(AccountEntityMapper.toEntity(account))

        assertEquals(account, roundTrip)
    }
}
