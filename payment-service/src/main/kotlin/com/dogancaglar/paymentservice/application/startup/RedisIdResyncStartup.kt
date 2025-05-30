package com.dogancaglar.paymentservice.application.startup

import com.dogancaglar.paymentservice.config.id.IdNamespaces
import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.PaymentOutboundPort
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RedisIdResyncStartup(
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentOrderOutboundPort: PaymentOrderOutboundPort,
    private val paymentOutboundPort: PaymentOutboundPort
) {

    private val logger = LoggerFactory.getLogger(RedisIdResyncStartup::class.java)

    @PostConstruct
    fun syncRedisIdWithDatabase() {
        try {
            val maxPaymentOrderIdInDb = paymentOrderOutboundPort.getMaxPaymentOrderId()
            val floor = maxPaymentOrderIdInDb + 100 // Give some breathing space

            val maxPaymentIdInDb = paymentOutboundPort.getMaxPaymentId()
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