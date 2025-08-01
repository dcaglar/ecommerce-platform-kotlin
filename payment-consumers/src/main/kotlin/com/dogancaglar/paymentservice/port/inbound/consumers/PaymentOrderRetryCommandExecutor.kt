package    com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.TOPICS
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.port.inbound.consumers.base.BaseBatchKafkaConsumer
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.PspResultCachePort
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.converter.ConversionException
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLTransientException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderRetryCommandExecutor(
    private val processPspResultUseCase: ProcessPspResultUseCase,
    private val meterRegistry: MeterRegistry,
    private val pspResultCache: PspResultCachePort,
    private val pspClient: PaymentGatewayPort,
    private val retryMetrics: RetryMetrics,
    @Qualifier("paymentOrderRetryExecutorPoolConfig") private val pspRetryExecutor: ThreadPoolTaskExecutor,
    @Qualifier("externalPspExecutorPoolConfig") private val externalPspExecutorPoolConig: ThreadPoolTaskExecutor
) : BaseBatchKafkaConsumer<PaymentOrderRetryRequested>() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(envelope: EventEnvelope<PaymentOrderRetryRequested>): Boolean {
        // In the future: filter by retry count, status, etc. For now, accept all.
        return true
    }

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val totalStart = System.currentTimeMillis()
        val envelope = record.value()
        val event = envelope.data
        val order = PaymentOrderDomainEventMapper.fromEvent(event)
        val cacheStart = System.currentTimeMillis()
        try {
            val key = order.paymentOrderId
            val cachedResult = pspResultCache.get(key)
            val cacheEnd = System.currentTimeMillis()
            logger.info("TIMING: PSP cache lookup took ${cacheEnd - cacheStart} ms for $key")

            val pspStart = System.currentTimeMillis()
            val status = if (cachedResult != null) {
                logger.info("♻️ Cache hit for $key → $cachedResult")
                PaymentOrderStatus.valueOf(cachedResult)
            } else {
                val result = safePspCall(order)
                pspResultCache.put(key, result.name)
                logger.info("✅ PSP returned $result for $key")
                result
            }
            val pspEnd = System.currentTimeMillis()
            logger.info("TIMING: PSP call (including cache, if miss) took ${pspEnd - pspStart} ms for $key")

            val dbStart = System.currentTimeMillis()
            processPspResultUseCase.processPspResult(event = event, pspStatus = status)
            val dbEnd = System.currentTimeMillis()
            logger.info("TIMING: processPspResult (DB/write) took ${dbEnd - dbStart} ms for $key")

            val totalEnd = System.currentTimeMillis()
            logger.info("TIMING: Total handler time: ${totalEnd - totalStart} ms for $key")

        } catch (e: Exception) {
            when (e) {
                is RetriableException,
                is TransientDataAccessException,
                is CannotAcquireLockException,
                is SQLTransientException -> {
                    logger.warn("Retryable exception occurred, will be retried or sent to DLQ", e)
                    throw e
                }

                is IllegalArgumentException,
                is NullPointerException,
                is ClassCastException,
                is ConversionException,
                is DeserializationException,
                is SerializationException,
                is MethodArgumentNotValidException,
                is DuplicateKeyException,
                is DataIntegrityViolationException,
                is KafkaException,
                is org.springframework.kafka.KafkaException,
                is NonTransientDataAccessException -> {
                    logger.error("Non-retryable exception occurred", e)
                    throw e
                }

                else -> {
                    logger.error("unexpected error occurred while processing record ${record.value().eventId}", e)
                }
            }

        }
    }


    @KafkaListener(
        topics = [TOPICS.PAYMENT_ORDER_RETRY],
        containerFactory = "${TOPICS.PAYMENT_ORDER_RETRY}-factory",
        groupId = "${CONSUMER_GROUPS.PAYMENT_ORDER_RETRY}",
        concurrency = "1"
    )
    fun handleBatchListener(
        records: List<ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>>,
        acknowledgment: Acknowledgment
    ) {
        super.handleBatch(records, acknowledgment)
    }

    override fun consume(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>,
    ) {
        handle(record)
    }


    override fun getExecutor(): ThreadPoolTaskExecutor? {
        return pspRetryExecutor
    }


    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val future = externalPspExecutorPoolConig.submit<PaymentOrderStatus> { pspClient.chargeRetry(order) }
        return try {
            future.get(1, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("PSP retry call timed out for paymentOrderId=${order.paymentOrderId}, scheduling retry")
            return PaymentOrderStatus.TIMEOUT
        } finally {
            retryMetrics.recordRetryAttempt(order.retryCount, order.retryReason)
        }
    }

}


@Component
class RetryMetrics(meterRegistry: MeterRegistry) {
    // If you want to tag by reason, you can keep a map or register with a static set of reasons
    private val retrySummary = DistributionSummary.builder("paymentorder.retry.attempts")
        .baseUnit("attempts")
        .description("Number of retry attempts before PaymentOrder succeeded")
        .register(meterRegistry)

    fun recordRetryAttempt(retryCount: Int, reason: String?) {
        // You can add .tag("reason", reason ?: "unknown") if you really want, but beware of unbounded label cardinality!
        retrySummary.record(retryCount.toDouble())
    }
}