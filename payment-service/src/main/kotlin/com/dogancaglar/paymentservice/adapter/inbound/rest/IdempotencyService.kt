// payment-service/src/main/kotlin/.../idempotency/IdempotencyService.kt
package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.domain.exception.PaymentIntentNotReadyException
import com.dogancaglar.paymentservice.idempotency.CanonicalJsonHasher
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyStorePort
import com.dogancaglar.paymentservice.ports.outbound.InitialRequestStatus
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
            throw IdempotencyConflictClientException(
                "Request hash is not same with intial request's hash,client sent bad request"
            )
            //return http 422
        }
        if(record.status== InitialRequestStatus.PENDING){
            //it mean request is a retry possible caused by client sending multiple times
            // before even intial request is compelte, throw PaymentIntentNotReadyException, and in controller advice
            //return 409 http
            throw PaymentIntentNotReadyException("It is a duplicate request,but originial request not completed yet," +
                    "so we cant return 200 or ")
        }
        else{
            //it is retried this time record is COMPLETED,then replayed
            val responseObj = objectMapper.readValue(
                record.responsePayload,
                CreatePaymentIntentResponseDTO::class.java
            )

            return IdempotencyResult(
                response = responseObj,
                status = IdempotencyExecutionStatus.REPLAYED
            )
        }

    }
}

class IdempotencyConflictClientException(msg: String) : RuntimeException(msg)

data class IdempotencyResult<PaymentResponseDTO>(
    val response: PaymentResponseDTO,
    val status: IdempotencyExecutionStatus
)

enum class IdempotencyExecutionStatus {
    CREATED,     // 201
    REPLAYED,    // 200
}