package com.dogancaglar.com.dogancaglar.payment.application.startup

import com.dogancaglar.payment.domain.port.PaymentRepository
import com.dogancaglar.payment.domain.port.id.IdGeneratorPort
import com.dogancaglar.payment.domain.port.id.IdNamespaces
import com.dogancaglar.port.PaymentOrderRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RedisIdResyncStartup(
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val paymentRepository: PaymentRepository
) {

    private val logger = LoggerFactory.getLogger(RedisIdResyncStartup::class.java)

    @PostConstruct
    fun syncRedisIdWithDatabase() {
        try {
            val maxPaymentOrderIdInDb = paymentOrderRepository.getMaxPaymentOrderId().value.toLong()
            val floor = maxPaymentOrderIdInDb + 100 // Give some breathing space

            val maxPaymentIdInDb = paymentRepository.getMaxPaymentId().value.toLong()
            val floorIdInDb = maxPaymentIdInDb.plus(100) // Give some breathing space
            logger.info(
                "🔁 Resyncing Redis ID generator ${
                    IdNamespaces.PAYMENT_ORDER
                }: setting minimum to $floor (max in DB: $maxPaymentOrderIdInDb)"
            )


            logger.info(
                "🔁 Resyncing Redis ID generator ${
                    IdNamespaces.PAYMENT
                }: setting minimum to $floor (max in DB: $maxPaymentOrderIdInDb)"
            )
            idGeneratorPort.setMinValue(IdNamespaces.PAYMENT_ORDER, floor)

            idGeneratorPort.setMinValue(IdNamespaces.PAYMENT, floorIdInDb)
        } catch (e: Exception) {
            logger.error("❌ Failed to resync Redis ID generator from DB: ${e.message}", e)
            throw IllegalStateException("Redis ID generator resync failed", e)
        }
    }
}