package com.dogancaglar.paymentservice.application

import com.dogancaglar.paymentservice.application.constants.IdNamespaces
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.DependsOn
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Component
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort

@Component
@DependsOn("liquibase") // ensure migrations done
class IdResyncStartup(
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val paymentRepository: PaymentRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            val maxOrder = paymentOrderRepository.getMaxPaymentOrderId().value.toLong()
            val minOrder = maxOrder + 100
            val maxPayment = paymentRepository.getMaxPaymentId().value.toLong()
            val minPayment = maxPayment + 100

            log.info("🔁 Resyncing Redis ID generator {}: setting minimum to {} (max in DB: {})",
                IdNamespaces.PAYMENT_ORDER, minOrder, maxOrder)
            log.info("🔁 Resyncing Redis ID generator {}: setting minimum to {} (max in DB: {})",
                IdNamespaces.PAYMENT, minPayment, maxPayment)

            idGeneratorPort.setMinValue(IdNamespaces.PAYMENT_ORDER, minOrder)
            idGeneratorPort.setMinValue(IdNamespaces.PAYMENT, minPayment)
        } catch (e: BadSqlGrammarException) {
            // Likely schema not present on the chosen DataSource
            log.error("❌ Skipping ID resync: schema missing (did Liquibase run on webDataSource?)", e)
            throw IllegalStateException("Liquibase did not initialize schema before ID resync", e)
        } catch (e: Exception) {
            log.error("❌ Failed to resync Redis ID generator from DB: {}", e.message, e)
            throw IllegalStateException("Redis ID generator resync failed", e)
        }
    }
}