package com.dogancaglar.paymentservice.infra.adapter.outbound.redis.client

import com.dogancaglar.paymentservice.domain.model.ledger.AccountCategory
import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.common.Currency

import com.dogancaglar.paymentservice.domain.model.ledger.AccountStatus
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class AccountProfileRedisCacheTest {

    private val redisTemplate: StringRedisTemplate = mockk()
    private val valueOps: ValueOperations<String, String> = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = mockk()
    
    init {
        every { redisTemplate.opsForValue() } returns valueOps
    }

    private val cache = AccountProfileRedisCache(redisTemplate, objectMapper)

    @Test
    fun `getProfile returns deserialized object when key exists`() {
        val type = AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT
        val masterId = "SELLER-1"
        val key = "account:profile:${type.name}:$masterId"
        val json = "{}"
        val profile = AccountProfile("CODE", type, masterId, Currency("EUR"), AccountCategory.LIABILITY, "NL", AccountStatus.ACTIVE)

        every { valueOps.get(any()) } returns json
        every { objectMapper.readValue(json, AccountProfile::class.java) } returns profile

        val result = cache.getProfile(type, masterId)

        assertEquals(profile, result)
    }

    @Test
    fun `getProfile returns null when key does not exist`() {
        val type = AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT
        val masterId = "SELLER-1"
        val key = "account:profile:${type.name}:$masterId"

        every { valueOps.get(any()) } returns null

        val result = cache.getProfile(type, masterId)

        assertNull(result)
    }

    @Test
    fun `getProfile returns null on deserialization exception`() {
        val type = AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT
        val masterId = "SELLER-1"
        val key = "account:profile:${type.name}:$masterId"
        val json = "invalid-json"

        every { valueOps.get(any()) } returns json
        every { objectMapper.readValue(json, AccountProfile::class.java) } throws RuntimeException("Jackson error")

        val result = cache.getProfile(type, masterId)

        assertNull(result)
    }

    @Test
    fun `saveProfile serializes and sets key with TTL`() {
        val type = AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT
        val masterId = "SELLER-1"
        val profile = AccountProfile("CODE", type, masterId, Currency("EUR"), AccountCategory.LIABILITY, "NL", AccountStatus.ACTIVE)
        val key = "account:profile:${type.name}:$masterId"
        val json = "{}"
        val ttl = Duration.ofHours(24)

        every { objectMapper.writeValueAsString(profile) } returns json

        cache.saveProfile(profile, ttl)

        verify(exactly = 1) { valueOps.set(any(), json, ttl) }
    }
}
