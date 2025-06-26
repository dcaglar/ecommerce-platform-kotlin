package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import java.math.BigDecimal
import java.time.LocalDateTime

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class PaymentOrderMapperIntegrationTest @Autowired constructor(
    val paymentOrderMapper: PaymentOrderMapper
) {
    @Test
    fun `should insert and find payment order`() {
        val entity = PaymentOrderEntity(
            paymentOrderId = 1L,
            publicPaymentOrderId = "public-1",
            paymentId = 100L,
            publicPaymentId = "public-payment-100",
            sellerId = "seller-1",
            amountValue = BigDecimal.TEN,
            amountCurrency = "EUR",
            status = PaymentOrderStatus.INITIATED,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        paymentOrderMapper.insert(entity)
        val found = paymentOrderMapper.findByPaymentId(100L)
        assertThat(found).isNotEmpty
        assertThat(found.first().publicPaymentOrderId).isEqualTo("public-1")
    }
}

