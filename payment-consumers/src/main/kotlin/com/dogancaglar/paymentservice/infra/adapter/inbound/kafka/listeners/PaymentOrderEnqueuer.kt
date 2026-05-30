package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.paymentservice.domain.exception.MissingPaymentOrderException
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentOrderEnqueuer(
    @param:Qualifier("syncPaymentEventPublisher")
    private val publisher: EventPublisherPort,

    private val dedupe: EventDeduplicationPort,
    private val modification: PaymentOrderModificationPort) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_CREATED],
        containerFactory = "${Topics.PAYMENT_ORDER_CREATED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_ENQUEUER
    )
    fun onCreated(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCaptureReceived>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val envelope = record.value()
        val eventData = envelope.data
        val eventId = envelope.eventId

        EventLogContext.with(envelope) {
            if (dedupe.exists(eventId)) {
                logger.warn(
                    "⚠️ Event is processed already  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} , skipping")
                return@with
            }
            logger.info(
                "🎬 Started processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                        "with $PAYMENT_ID ${eventData.publicPaymentId}")
            // 1. DEDUPE CHECK,if it's captured alreadyv,it already exist in dedup


            // 2. UPDATE DB → mark as CAPTURE_REQUESTED
            val updated = try {
                modification.updateReturningIdempotentInitialCaptureRequest(eventData.paymentOrderId.toLong())
            } catch (ex: MissingPaymentOrderException) {
                logger.warn(
                    "‼️ Issue with processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} issue : ${ex.message}")
                return@with
            }

            // 3. Build new event
            val work = try {
                PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureCommand(updated, attempt = 0)
            } catch (ex: IllegalArgumentException) {
                // this comes from require(...) in from()
                logger.warn(
                    "‼️ Issue with processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} issue : ${ex.message}")
                return@with
            } catch (ex: Exception) {
                logger.warn(
                    "🚨 with processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} issue : ${ex.message}")
                return@with
            }



            // 4. KAFKA TX: publish + commit offset + mark dedupe
            publisher.publishSync(
                aggregateId = envelope.aggregateId,
                data = work,
                traceId = envelope.traceId,
                parentEventId = envelope.eventId
            )

            // 5. Mark as processed ONLY HERE — AFTER EVERYTHING SUCCEEDS
            dedupe.markProcessed(eventId, 3600)

            logger.info(
                "✅ Completed processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                        "with $PAYMENT_ID ${eventData.publicPaymentId}")
        }
    }
}