// payment-application/src/main/kotlin/.../ports/outbound/IdempotencyStorePort.kt
package com.dogancaglar.paymentservice.ports.outbound

import java.time.Instant

data class IdempotencyRecord(
    val idempotencyKey: String,
    val requestHash: String,
    val paymentId: Long? = null,
    val responsePayload: String? = null,
    val status: IdempotencyStatus = IdempotencyStatus.PENDING,
    val createdAt: Instant = Instant.now()
)

enum class IdempotencyStatus {
    PENDING,
    COMPLETED
}
