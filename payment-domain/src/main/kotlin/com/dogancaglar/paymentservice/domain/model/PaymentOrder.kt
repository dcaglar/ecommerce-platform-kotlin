package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.LocalDateTime

/**
 * Represents a payment order for capturing funds from a payment.
 * Payment orders track the lifecycle of capture attempts, including retries and status transitions.
 */
class PaymentOrder private constructor(
    val paymentOrderId: PaymentOrderId,
    val paymentId: PaymentId,
    val sellerId: SellerId,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val retryCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    
    init {
        // Constructor validation - enforces domain invariants
        // This is the gatekeeper that ensures all PaymentOrder instances are valid
        require(amount.isPositive()) {
            "Payment order amount must be positive. " +
                    "Received amount: ${amount.quantity} ${amount.currency.currencyCode}. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        require(sellerId.value.isNotBlank()) {
            "Seller ID cannot be blank. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        require(retryCount >= 0) {
            "Retry count cannot be negative. " +
                    "Received retryCount: $retryCount. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        require(!updatedAt.isBefore(createdAt)) {
            "UpdatedAt cannot be before createdAt. " +
                    "CreatedAt: $createdAt, UpdatedAt: $updatedAt. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        
        // Enforce retry count constraints based on status
        when (status) {
            PaymentOrderStatus.CAPTURE_REQUESTED -> {
                require(retryCount == 0) {
                    "Status '${status.name}' must have retryCount = 0. " +
                            "Received retryCount: $retryCount. " +
                            "PaymentOrderId: ${paymentOrderId.value}"
                }
            }
            PaymentOrderStatus.PENDING_CAPTURE -> {
                require(retryCount > 0) {
                    "PENDING_CAPTURE status must have retryCount > 0 (this is a retry state). " +
                            "Received retryCount: $retryCount. " +
                            "PaymentOrderId: ${paymentOrderId.value}"
                }
            }
            PaymentOrderStatus.CAPTURED,
            PaymentOrderStatus.CAPTURE_FAILED -> {
                // Terminal statuses can have any non-negative retry count
                require(retryCount >= 0) {
                    "Terminal status must have retryCount >= 0. " +
                            "Status: ${status.name}, retryCount: $retryCount. " +
                            "PaymentOrderId: ${paymentOrderId.value}"
                }
            }
            else -> {
                // Other statuses - allow any non-negative retry count
                require(retryCount >= 0) {
                    "Retry count must be non-negative for status ${status.name}. " +
                            "Received retryCount: $retryCount. " +
                            "PaymentOrderId: ${paymentOrderId.value}"
                }
            }
        }
    }

    // ============================================================================
    // Status Transition Methods
    // ============================================================================

    /**
     * Transitions the payment order to CAPTURE_REQUESTED status.
     * Only allowed from INITIATED_PENDING status with retryCount = 0.
     */
    fun markCaptureRequested(): PaymentOrder {
        require(status == PaymentOrderStatus.INITIATED_PENDING) {
            "Invalid transtion Cannot request capture from status '${status.name}'. " +
                    "Expected status: ${PaymentOrderStatus.INITIATED_PENDING.name}. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        require(retryCount == 0) {
            "Invalid transtion Cannot request capture when retryCount is not zero. " +
                    "Current retryCount: $retryCount. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        return copy(status = PaymentOrderStatus.CAPTURE_REQUESTED)
    }

    /**
     * Transitions the payment order to CAPTURED status.
     * Only allowed from CAPTURE_REQUESTED or PENDING_CAPTURE status.
     */
    fun markAsCaptured(): PaymentOrder {
        require(status in ALLOWED_STATUSES_FOR_CAPTURE) {
            "Invalid transtion Cannot mark as captured from status '${status.name}'. " +
                    "Allowed statuses: ${ALLOWED_STATUSES_FOR_CAPTURE.joinToString { it.name }}. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        return copy(status = PaymentOrderStatus.CAPTURED)
    }

    /**
     * Transitions the payment order to CAPTURE_FAILED status.
     * Only allowed from CAPTURE_REQUESTED or PENDING_CAPTURE status.
     */
    fun markCaptureDeclined(): PaymentOrder {
        require(status in ALLOWED_STATUSES_FOR_CAPTURE) {
            "Invalid transtion.Cannot mark capture as declined from status '${status.name}'. " +
                    "Allowed statuses: ${ALLOWED_STATUSES_FOR_CAPTURE.joinToString { it.name }}. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        return copy(status = PaymentOrderStatus.CAPTURE_FAILED)
    }
    fun markCapturePendingAndIncrementRetry(): PaymentOrder {
        require(status in ALLOWED_STATUSES_FOR_PENDING_CAPTURE) {
            "Invalid transition Cannot mark as pending capture from status '${status.name}'. " +
                    "Allowed statuses: ${ALLOWED_STATUSES_FOR_PENDING_CAPTURE.joinToString { it.name }}. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        require(!isTerminal()) {
            "Cannot increment retry count for terminal order. " +
                    "Current status: ${status.name}. " +
                    "Terminal statuses: ${TERMINAL_STATUSES.joinToString { it.name }}. " +
                    "PaymentOrderId: ${paymentOrderId.value}"
        }
        // Atomically set both status and retryCount in a single copy() call
        // This avoids intermediate invalid states
        return copy(status = PaymentOrderStatus.PENDING_CAPTURE, retryCount = retryCount + 1)
    }

    // ============================================================================
    // Query Methods
    // ============================================================================

    /**
     * Checks if the payment order is in a terminal state (cannot be retried).
     */
    fun isTerminal(): Boolean = status.isTerminalPspResponse()

    // ============================================================================
    // Utility Methods
    // ============================================================================

    /**
     * Updates the updatedAt timestamp of the payment order.
     */
    fun withUpdateAt(updatedAt: LocalDateTime): PaymentOrder {
        return copy(updatedAt = updatedAt)
    }

    // ============================================================================
    // Private Helper Methods
    // ============================================================================

    private fun copy(
        status: PaymentOrderStatus = this.status,
        retryCount: Int = this.retryCount,
        updatedAt: LocalDateTime = Utc.nowLocalDateTime()
    ): PaymentOrder = PaymentOrder(
        paymentOrderId = paymentOrderId,
        paymentId = paymentId,
        sellerId = sellerId,
        amount = amount,
        status = status,
        retryCount = retryCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ============================================================================
    // Companion Object - Factory Methods
    // ============================================================================

    companion object {
        /**
         * Statuses that allow transition to CAPTURED or CAPTURE_FAILED.
         */
        private val ALLOWED_STATUSES_FOR_CAPTURE = setOf(
            PaymentOrderStatus.CAPTURE_REQUESTED,
            PaymentOrderStatus.PENDING_CAPTURE
        )

        /**
         * Statuses that allow transition to PENDING_CAPTURE (for retries).
         */
        private val ALLOWED_STATUSES_FOR_PENDING_CAPTURE = setOf(
            PaymentOrderStatus.CAPTURE_REQUESTED,
            PaymentOrderStatus.PENDING_CAPTURE,
            PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT,
            PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        )

        /**
         * Terminal statuses that cannot be retried.
         */
        private val TERMINAL_STATUSES = setOf(
            PaymentOrderStatus.CAPTURED,
            PaymentOrderStatus.CAPTURE_FAILED
        )

        /**
         * Creates a new payment order with INITIATED_PENDING status.
         * Validation is performed by the constructor's init block.
         */
        fun createNew(
            paymentOrderId: PaymentOrderId,
            paymentId: PaymentId,
            sellerId: SellerId,
            amount: Amount
        ): PaymentOrder {
            val now = Utc.nowLocalDateTime()
            return PaymentOrder(
                paymentOrderId = paymentOrderId,
                paymentId = paymentId,
                sellerId = sellerId,
                amount = amount,
                status = PaymentOrderStatus.INITIATED_PENDING,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
        }

        /**
         * Rehydrates a payment order from persistence.
         * Used when loading existing payment orders from the database.
         * 
         * No validation is performed here because:
         * - PaymentOrderEntity can only be created from valid PaymentOrder instances (via toEntity())
         * - PaymentOrder instances are only created via createNew() or valid transition methods
         * - Therefore, entities loaded from the database are guaranteed to be valid
         * 
         * Note: The constructor's init block will still run and validate, but since entities
         * are created from validated domain objects, this validation should always pass.
         * This follows the same pattern as JournalEntry.fromPersistence().
         */
        fun rehydrate(
            paymentOrderId: PaymentOrderId,
            paymentId: PaymentId,
            sellerId: SellerId,
            amount: Amount,
            status: PaymentOrderStatus,
            retryCount: Int,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): PaymentOrder = PaymentOrder(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = sellerId,
            amount = amount,
            status = status,
            retryCount = retryCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

