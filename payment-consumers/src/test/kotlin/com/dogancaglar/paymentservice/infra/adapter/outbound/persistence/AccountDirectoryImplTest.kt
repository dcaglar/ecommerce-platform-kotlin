package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.ledger.AccountCategory
import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.common.Currency

import com.dogancaglar.paymentservice.domain.model.ledger.AccountStatus
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.AccountDirectoryMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.redis.client.AccountProfileRedisCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AccountDirectoryImplTest {

    private val mapper: AccountDirectoryMapper = mockk()
    private val redisCache: AccountProfileRedisCache = mockk(relaxed = true)
    private val directory = AccountDirectoryImpl(mapper, redisCache)

    @Test
    fun `getAccountProfile returns cached profile if found`() {
        val type = AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT
        val masterId = "SELLER-1"
        val cachedProfile = AccountProfile("CODE", type, masterId, Currency("EUR"), AccountCategory.LIABILITY, "NL", AccountStatus.ACTIVE)

        every { redisCache.getProfile(type, masterId) } returns cachedProfile

        val result = directory.getAccountProfile(type, masterId)

        assertEquals(cachedProfile, result)
        verify(exactly = 0) { mapper.findByEntityAndType(any(), any()) }
    }

    @Test
    fun `getAccountProfile fetches from DB and caches if not found in cache`() {
        val type = AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT
        val masterId = "SELLER-1"
        val dbProfile = AccountProfile("CODE", type, masterId, Currency("EUR"), AccountCategory.LIABILITY, "NL", AccountStatus.ACTIVE)

        every { redisCache.getProfile(type, masterId) } returns null
        every { mapper.findByEntityAndType(type.name, masterId) } returns dbProfile

        val result = directory.getAccountProfile(type, masterId)

        assertEquals(dbProfile, result)
        verify(exactly = 1) { mapper.findByEntityAndType(type.name, masterId) }
        verify(exactly = 1) { redisCache.saveProfile(dbProfile) }
    }

    @Test
    fun `getAccountProfile throws exception if not found in DB`() {
        val type = AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT
        val masterId = "SELLER-1"

        every { redisCache.getProfile(type, masterId) } returns null
        every { mapper.findByEntityAndType(type.name, masterId) } returns null

        assertThrows<IllegalArgumentException> {
            directory.getAccountProfile(type, masterId)
        }
    }
}
