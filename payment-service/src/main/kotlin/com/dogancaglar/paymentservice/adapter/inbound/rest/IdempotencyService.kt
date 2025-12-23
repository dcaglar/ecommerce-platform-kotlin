// payment-service/src/main/kotlin/.../idempotency/IdempotencyService.kt
package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.idempotency.CanonicalJsonHasher
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyStorePort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
@Service
class IdempotencyService(
    private val store: IdempotencyStorePort,
    private val canonicalJsonHasher: CanonicalJsonHasher,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 2 seconds max wait (Stripe uses roughly this window)
    private val maxWaitMs = 2000L
    private val pollIntervalMs = 50L

    fun run(
        key: String,
        requestBody: CreatePaymentIntentRequestDTO,
        block: () -> CreatePaymentIntentResponseDTO
    ): IdempotencyResult<CreatePaymentIntentResponseDTO> {

        val hash = canonicalJsonHasher.hashBody(requestBody)

        // 1️⃣ Try to insert PENDING row
        val isFirst = store.tryInsertPending(key, hash)

        if (isFirst) {
            logger.info("Idempotency FIRST request for key={}", key)

            try {
                val response = block()
                val json = objectMapper.writeValueAsString(response)
                val internalPaymentIntentId = PublicIdFactory.toInternalId(response.paymentIntentId!!)
                store.updateResponsePayload(key, json,internalPaymentIntentId)

                return IdempotencyResult(
                    response = response,
                    status = IdempotencyExecutionStatus.CREATED
                )
            } catch (e: Exception) {
                // Cleanup so retries can re-execute safely
                store.deletePending(key)
                throw e
            }
        }

        // 2️⃣ RETRY path
        logger.info("Idempotency RETRY request for key={}", key)

        val record = store.findByKey(key)
            ?: error("Inconsistent state: idempotency key exists but row missing")

        // 2a — ensure payload is identical
        if (record.requestHash != hash) {
            throw IdempotencyConflictException(
                "Idempotency-Key reused with different request body"
            )
        }

        // 2b — if still pending, dont block thread return 202 with retryafter in header
        if (record.responsePayload == null) {
            val pending = CreatePaymentIntentResponseDTO(
                paymentIntentId = null,
                buyerId = requestBody.buyerId,
                orderId = requestBody.orderId,
                totalAmount = requestBody.totalAmount,
                status = "PENDING",
                createdAt = com.dogancaglar.common.time.Utc.nowInstant().toString()
            )

            return IdempotencyResult(
                response = pending,
                status = IdempotencyExecutionStatus.IN_PROGRESS
            )
        }

        // 2c — response now available
        val finalRecord = store.findByKey(key)
            ?: error("Record disappeared during wait")

        val responseObj = objectMapper.readValue(
            finalRecord.responsePayload,
            CreatePaymentIntentResponseDTO::class.java
        )

        return IdempotencyResult(
            response = responseObj,
            status = IdempotencyExecutionStatus.REPLAYED
        )
    }

    private fun waitForCompletion(key: String) {
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < maxWaitMs) {
            Thread.sleep(pollIntervalMs)

            val row = store.findByKey(key)
            if (row?.responsePayload != null) {
                return
            }
        }

        throw IllegalStateException(
            "Idempotent request timed out waiting for first execution to complete (key=$key)"
        )
    }
}

class IdempotencyConflictException(msg: String) : RuntimeException(msg)

data class IdempotencyResult<PaymentResponseDTO>(
    val response: PaymentResponseDTO,
    val status: IdempotencyExecutionStatus
)

enum class IdempotencyExecutionStatus {
    CREATED,     // 201
    REPLAYED,    // 200
    IN_PROGRESS  // 202
}