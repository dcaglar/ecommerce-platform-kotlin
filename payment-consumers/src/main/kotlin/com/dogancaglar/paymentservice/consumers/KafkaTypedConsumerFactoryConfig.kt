// KafkaTypedConsumerFactoryConfig.kt
package com.dogancaglar.paymentservice.consumers

import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.paymentservice.config.kafka.EventEnvelopeKafkaSerializer
import com.dogancaglar.paymentservice.application.commands.PaymentOrderLedgerRecordingCommand
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.events.PaymentAuthorizedIntent
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.kafka.listener.adapter.RecordFilterStrategy
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory

@Configuration
class KafkaTypedConsumerFactoryConfig(
    private val dynamicProps: DynamicKafkaConsumersProperties,
    private val bootKafkaProps: KafkaProperties,
    private val meterRegistry: MeterRegistry
) {
    companion object {
        private val logger = LoggerFactory.getLogger(KafkaTypedConsumerFactoryConfig::class.java)
        private const val HDR_VALUE_BYTES = "springDeserializerExceptionValue"
    }

    init {
        logger.info("Loaded dynamicConsumers: {}", dynamicProps.dynamicConsumers.map { it.topic })
    }

    @Bean("custom-kafka-consumer-factory-for-micrometer")
    fun defaultKafkaConsumerFactory(): DefaultKafkaConsumerFactory<String, EventEnvelope<*>> {
        val configs = bootKafkaProps.buildConsumerProperties().toMutableMap()
        return DefaultKafkaConsumerFactory<String, EventEnvelope<*>>(configs).apply {
            addListener(MicrometerConsumerListener(meterRegistry))
        }
    }

    @Bean("dlqProducerFactory")
    fun dlqProducerFactory(): ProducerFactory<String, ByteArray> {
        val cfg = bootKafkaProps.buildProducerProperties().toMutableMap()
        cfg.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG)
        cfg[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = false
        cfg[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] =
            org.apache.kafka.common.serialization.StringSerializer::class.java
        cfg[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] =
            org.apache.kafka.common.serialization.ByteArraySerializer::class.java

        val factory = DefaultKafkaProducerFactory<String, ByteArray>(cfg)
        factory.addListener(MicrometerProducerListener<String, ByteArray>(meterRegistry))
        return factory
    }

    @Bean("dlqKafkaTemplate")
    fun dlqKafkaTemplate(
        @Qualifier("dlqProducerFactory") pf: ProducerFactory<String, ByteArray>
    ) = KafkaTemplate(pf)

    @Bean
    fun errorHandler(
        @Qualifier("dlqKafkaTemplate") dlqTemplate: KafkaTemplate<String, ByteArray>,
        kafkaExponentialBackOff: ExponentialBackOffWithMaxRetries
    ): DefaultErrorHandler {

        val recoverer = ConsumerRecordRecoverer { rec, ex ->
            val src = rec.topic()
            val target = if (src.endsWith(".DLQ")) src else Topics.dlqOf(src)
            val key: String? = rec.key()?.toString()

            // Prefer original bytes captured by ErrorHandlingDeserializer
            val raw: ByteArray? = rec.headers().lastHeader(HDR_VALUE_BYTES)?.value()


            val valueBytes: ByteArray = raw
                ?: (rec.value() as? EventEnvelope<*>)?.let { env ->
                    EventEnvelopeKafkaSerializer().serialize(rec.topic(), env) ?: ByteArray(0)
                } ?: ByteArray(0)
            // Copy headers and add error diagnostics
            val headers = org.apache.kafka.common.header.internals.RecordHeaders(rec.headers().toArray()).apply {
                add("x-error-class", (ex?.javaClass?.name ?: "n/a").toByteArray())
                add("x-error-message", ((ex?.message ?: "")
                    .take(8_000)).toByteArray()) // cap to avoid jumbo headers
                add("x-error-stacktrace", stackTraceString(ex, 16_000).toByteArray())
                add("x-recovered-at", Utc.nowInstant().toString().toByteArray())
                add("x-consumer-group", (rec.headers()
                    .lastHeader(org.springframework.kafka.support.KafkaHeaders.GROUP_ID)?.let { String(it.value()) }
                    ?: "unknown").toByteArray())
            }

            val pr = ProducerRecord<String, ByteArray>(
                target,
                rec.partition(),
                null,
                key,
                valueBytes,
                headers
            )

            dlqTemplate.send(pr)
        }

        return DefaultErrorHandler(recoverer, kafkaExponentialBackOff).apply {
            setCommitRecovered(true)
            setAckAfterHandle(false)

            // keep your exception policy
            addRetryableExceptions(
                org.apache.kafka.common.errors.RetriableException::class.java,
                org.springframework.dao.TransientDataAccessException::class.java,
                org.springframework.dao.CannotAcquireLockException::class.java,
                java.sql.SQLTransientException::class.java,
                org.apache.kafka.clients.consumer.CommitFailedException::class.java,
            )
            addNotRetryableExceptions(
                com.dogancaglar.paymentservice.adapter.outbound.persistence.MissingPaymentOrderException::class.java,
                java.lang.IllegalArgumentException::class.java,
                java.lang.NullPointerException::class.java,
                java.lang.ClassCastException::class.java,
                org.springframework.core.convert.ConversionException::class.java,
                org.springframework.kafka.support.serializer.DeserializationException::class.java,
                org.apache.kafka.common.errors.SerializationException::class.java,
                org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException::class.java,
                org.springframework.dao.DuplicateKeyException::class.java,
                org.springframework.dao.DataIntegrityViolationException::class.java,
                org.springframework.dao.NonTransientDataAccessException::class.java,
            )
        }
    }


    private fun stackTraceString(ex: Throwable?, max: Int): String =
        if (ex == null) "" else java.io.StringWriter().use { sw ->
            ex.printStackTrace(java.io.PrintWriter(sw))
            sw.toString().take(max)
        }

    @Bean
    fun kafkaExponentialBackOff(): ExponentialBackOffWithMaxRetries =
        ExponentialBackOffWithMaxRetries(5).apply {
            initialInterval = 2_000L
            multiplier = 2.0
            maxInterval = 30_000L
        }


    private fun <T : Event> createFactory(
        clientId: String,
        concurrency: Int,
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        consumerFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler,
        ackMode: ContainerProperties.AckMode = ContainerProperties.AckMode.RECORD,
        expectedEventType: String? = null,
        ackDiscarded: Boolean = true,
        batchMode: Boolean = false
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>> =
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<T>>().apply {
            this.consumerFactory = consumerFactory
            containerProperties.clientId = clientId
            consumerFactory.updateConfigs(
                mapOf(org.apache.kafka.clients.consumer.ConsumerConfig.CLIENT_ID_CONFIG to clientId)
            )
            containerProperties.pollTimeout = 1000           // block up to 1s waiting for data
            containerProperties.isMicrometerEnabled = true
            containerProperties.idleBetweenPolls = 250 // nap 250ms after an empty poll
            @Suppress("UNCHECKED_CAST")
            setRecordInterceptor(interceptor as RecordInterceptor<String, EventEnvelope<T>>)
            setCommonErrorHandler(errorHandler)
            containerProperties.ackMode = ackMode
            setConcurrency(concurrency)
            isBatchListener = batchMode

            // enforce semantic type at the container level
            expectedEventType?.let {
                setRecordFilterStrategy(eventTypeFilter(expectedEventType))
                setAckDiscarded(ackDiscarded)
            }
        }


    @Bean("${Topics.PAYMENT_INTENT_AUTHORIZED}-factory")
    fun paymentAuthorizedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentAuthorizedIntent>> {
        val cfg = cfgFor(PaymentEventMetadataCatalog.PaymentIntentAuthorizedMetadata.topic, "${Topics.PAYMENT_INTENT_AUTHORIZED}-factory")
        return createFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = PaymentEventMetadataCatalog.PaymentIntentAuthorizedMetadata.eventType,
            batchMode = false
        )
    }

    @Bean("${Topics.PAYMENT_ORDER_CREATED}-factory")
    fun paymentOrderCreatedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCreated>> {
        val cfg = cfgFor(PaymentEventMetadataCatalog.PaymentOrderCreatedMetadata.topic, "${Topics.PAYMENT_ORDER_CREATED}-factory")
        return createFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = PaymentEventMetadataCatalog.PaymentOrderCreatedMetadata.eventType,
            batchMode = false
        )
    }


    @Bean("${Topics.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE}-factory")
    fun paymentOrderPspCallRequestedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderCaptureCommand>> {
        val cfg = cfgFor(
            PaymentEventMetadataCatalog.PaymentOrderCaptureCommandMetadata.topic,
            "${Topics.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE}-factory"
        )
        return createFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = PaymentEventMetadataCatalog.PaymentOrderCaptureCommandMetadata.eventType,
            batchMode = false
        )
    }


    @Bean("${Topics.PAYMENT_ORDER_PSP_RESULT_UPDATED}-factory")
    fun pspResultUpdatedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderPspResultUpdated>> {
        val cfg = cfgFor(PaymentEventMetadataCatalog.PaymentOrderPspResultUpdatedMetadata.topic, "${Topics.PAYMENT_ORDER_PSP_RESULT_UPDATED}-factory")
        return createFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = PaymentEventMetadataCatalog.PaymentOrderPspResultUpdatedMetadata.eventType,
            batchMode = false
        )
    }



    @Bean("${Topics.LEDGER_RECORD_REQUEST_QUEUE}-factory")
    fun ledgerRecordingCommandFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderLedgerRecordingCommand>> {
        val cfg = cfgFor(
            Topics.LEDGER_RECORD_REQUEST_QUEUE,
            "${Topics.LEDGER_RECORD_REQUEST_QUEUE}-factory"
        )

        return createFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = PaymentEventMetadataCatalog.LedgerRecordingCommandMetadata.eventType,
            ackDiscarded = true,
            batchMode = false
        )
    }


    @Bean("${Topics.LEDGER_ENTRIES_RECORDED}-factory")
    fun ledgerEntriesRecordedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<LedgerEntriesRecorded>> {
        val cfg = cfgFor(
            PaymentEventMetadataCatalog.LedgerEntriesRecordedMetadata.topic,
            "${Topics.LEDGER_ENTRIES_RECORDED}-factory"
        )
        return createFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = PaymentEventMetadataCatalog.LedgerEntriesRecordedMetadata.eventType,
            ackDiscarded = true,
            batchMode = true
        )
    }

    @Bean("${Topics.PAYMENT_ORDER_FINALIZED}-factory")
    fun paymentOrderFinalizedFactory(
        interceptor: RecordInterceptor<String, EventEnvelope<*>>,
        @Qualifier("custom-kafka-consumer-factory-for-micrometer")
        customFactory: DefaultKafkaConsumerFactory<String, EventEnvelope<*>>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentOrderFinalized>> {

        val cfg = cfgFor(
            Topics.PAYMENT_ORDER_FINALIZED,
            "${Topics.PAYMENT_ORDER_FINALIZED}-factory"
        )

        return createFactory(
            clientId = cfg.id,
            concurrency = cfg.concurrency,
            interceptor = interceptor,
            consumerFactory = customFactory,
            errorHandler = errorHandler,
            ackMode = ContainerProperties.AckMode.MANUAL,
            expectedEventType = PaymentEventMetadataCatalog.PaymentOrderFinalizedMetadata.eventType,
            ackDiscarded = true,
            batchMode = false
        )
    }

    @Bean
    fun mdcRecordInterceptor(): RecordInterceptor<String, EventEnvelope<*>> = HeaderMdcInterceptor()

    @Bean
    fun messageHandlerMethodFactory(): DefaultMessageHandlerMethodFactory = DefaultMessageHandlerMethodFactory()


    private fun eventTypeFilter(expected: String): RecordFilterStrategy<String, EventEnvelope<*>> =
        RecordFilterStrategy { rec ->
            val serdeFailed = rec.headers().lastHeader(HDR_VALUE_BYTES) != null
            if (serdeFailed) {
                // let DefaultErrorHandler DLQ+commit it
                false
            } else {
                // only drop genuine wrong-type events
                rec.value()?.eventType != expected
            }
        }


    private fun cfgFor(topic: String, beanName: String): DynamicKafkaConsumersProperties.DynamicConsumer =
        dynamicProps.dynamicConsumers.firstOrNull { it.topic == topic }
            ?: error("Missing app.kafka.dynamic-consumers entry for topic '$topic' (required by bean '$beanName').")

}

class HeaderMdcInterceptor : RecordInterceptor<String, EventEnvelope<*>> {

    private val prevCtx = ThreadLocal<Map<String, String>?>()

    private fun putFrom(record: ConsumerRecord<String, EventEnvelope<*>>) {
        fun h(k: String) = record.headers().lastHeader(k)?.value()?.let { String(it) }
        val env = record.value()

        MDC.put(GenericLogFields.TRACE_ID, h("traceId") ?: env?.traceId)
        MDC.put(GenericLogFields.EVENT_ID, h("eventId") ?: env?.eventId?.toString())
        MDC.put(GenericLogFields.PARENT_EVENT_ID, h("parentEventId") ?: env?.parentEventId?.toString())
        MDC.put(GenericLogFields.AGGREGATE_ID, env?.aggregateId ?: record.key())
        MDC.put(GenericLogFields.EVENT_TYPE, h("eventType") ?: env?.eventType)
    }


    override fun intercept(
        record: ConsumerRecord<String, EventEnvelope<*>>,
        consumer: Consumer<String, EventEnvelope<*>>
    ): ConsumerRecord<String, EventEnvelope<*>>? {
        prevCtx.set(MDC.getCopyOfContextMap())
        putFrom(record)
        return record // return null to skip; we’re not skipping here
    }


    override fun afterRecord(
        record: ConsumerRecord<String, EventEnvelope<*>>,
        consumer: Consumer<String, EventEnvelope<*>>
    ) {
        val prev = prevCtx.get()
        if (prev != null) MDC.setContextMap(prev) else MDC.clear()
        prevCtx.remove()
    }
}