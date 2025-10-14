package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PSPStatusMapperTest {

    @Test
    fun `fromPspStatus should map SUCCESSFUL_FINAL`() {
        val result = PSPStatusMapper.fromPspStatus("SUCCESSFUL_FINAL")
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, result)
    }

    @Test
    fun `fromPspStatus should map FAILED_FINAL`() {
        val result = PSPStatusMapper.fromPspStatus("FAILED_FINAL")
        assertEquals(PaymentOrderStatus.FAILED_FINAL, result)
    }

    @Test
    fun `fromPspStatus should map DECLINED_FINAL`() {
        val result = PSPStatusMapper.fromPspStatus("DECLINED_FINAL")
        assertEquals(PaymentOrderStatus.DECLINED_FINAL, result)
    }

    @Test
    fun `fromPspStatus should map FAILED_TRANSIENT_ERROR`() {
        val result = PSPStatusMapper.fromPspStatus("FAILED_TRANSIENT_ERROR")
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, result)
    }

    @Test
    fun `fromPspStatus should map PSP_UNAVAILABLE_TRANSIENT`() {
        val result = PSPStatusMapper.fromPspStatus("PSP_UNAVAILABLE_TRANSIENT")
        assertEquals(PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT, result)
    }

    @Test
    fun `fromPspStatus should map TIMEOUT_EXCEEDED_1S_TRANSIENT`() {
        val result = PSPStatusMapper.fromPspStatus("TIMEOUT_EXCEEDED_1S_TRANSIENT")
        assertEquals(PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT, result)
    }

    @Test
    fun `fromPspStatus should map AUTH_NEEDED_STAUS_CHECK_LATER`() {
        val result = PSPStatusMapper.fromPspStatus("AUTH_NEEDED_STAUS_CHECK_LATER")
        assertEquals(PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER, result)
    }

    @Test
    fun `fromPspStatus should map CAPTURE_PENDING_STATUS_CHECK_LATER`() {
        val result = PSPStatusMapper.fromPspStatus("CAPTURE_PENDING_STATUS_CHECK_LATER")
        assertEquals(PaymentOrderStatus.CAPTURE_PENDING_STATUS_CHECK_LATER, result)
    }

    @Test
    fun `fromPspStatus should return UNKNOWN_FINAL for unknown status`() {
        val result = PSPStatusMapper.fromPspStatus("SOME_UNKNOWN_STATUS")
        assertEquals(PaymentOrderStatus.UNKNOWN_FINAL, result)
    }

    @Test
    fun `fromPspStatus should be case insensitive - lowercase`() {
        val result = PSPStatusMapper.fromPspStatus("successful_final")
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, result)
    }

    @Test
    fun `fromPspStatus should be case insensitive - mixed case`() {
        val result = PSPStatusMapper.fromPspStatus("Failed_Transient_Error")
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, result)
    }

    @Test
    fun `fromPspStatus should handle uppercase`() {
        val result = PSPStatusMapper.fromPspStatus("PSP_UNAVAILABLE_TRANSIENT")
        assertEquals(PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT, result)
    }

    @Test
    fun `requiresRetryPayment should return true for FAILED_TRANSIENT_ERROR`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.FAILED_TRANSIENT_ERROR)
        assertTrue(result)
    }

    @Test
    fun `requiresRetryPayment should return true for PSP_UNAVAILABLE_TRANSIENT`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT)
        assertTrue(result)
    }

    @Test
    fun `requiresRetryPayment should return true for TIMEOUT_EXCEEDED_1S_TRANSIENT`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT)
        assertTrue(result)
    }

    @Test
    fun `requiresRetryPayment should return false for SUCCESSFUL_FINAL`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.SUCCESSFUL_FINAL)
        assertFalse(result)
    }

    @Test
    fun `requiresRetryPayment should return false for FAILED_FINAL`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.FAILED_FINAL)
        assertFalse(result)
    }

    @Test
    fun `requiresRetryPayment should return false for DECLINED_FINAL`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.DECLINED_FINAL)
        assertFalse(result)
    }

    @Test
    fun `requiresRetryPayment should return false for INITIATED_PENDING`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.INITIATED_PENDING)
        assertFalse(result)
    }

    @Test
    fun `requiresRetryPayment should return false for AUTH_NEEDED_STAUS_CHECK_LATER`() {
        val result = PSPStatusMapper.requiresRetryPayment(PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER)
        assertFalse(result)
    }

    @Test
    fun `requiresStatusCheck should return true for AUTH_NEEDED_STAUS_CHECK_LATER`() {
        val result = PSPStatusMapper.requiresStatusCheck(PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER)
        assertTrue(result)
    }

    @Test
    fun `requiresStatusCheck should return true for UNKNOWN_FINAL`() {
        val result = PSPStatusMapper.requiresStatusCheck(PaymentOrderStatus.UNKNOWN_FINAL)
        assertTrue(result)
    }

    @Test
    fun `requiresStatusCheck should return false for SUCCESSFUL_FINAL`() {
        val result = PSPStatusMapper.requiresStatusCheck(PaymentOrderStatus.SUCCESSFUL_FINAL)
        assertFalse(result)
    }

    @Test
    fun `requiresStatusCheck should return false for FAILED_TRANSIENT_ERROR`() {
        val result = PSPStatusMapper.requiresStatusCheck(PaymentOrderStatus.FAILED_TRANSIENT_ERROR)
        assertFalse(result)
    }

    @Test
    fun `requiresStatusCheck should return false for INITIATED_PENDING`() {
        val result = PSPStatusMapper.requiresStatusCheck(PaymentOrderStatus.INITIATED_PENDING)
        assertFalse(result)
    }

    @Test
    fun `requiresStatusCheck should return false for PSP_UNAVAILABLE_TRANSIENT`() {
        val result = PSPStatusMapper.requiresStatusCheck(PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT)
        assertFalse(result)
    }

    @Test
    fun `requiresStatusCheck should return false for CAPTURE_PENDING_STATUS_CHECK_LATER`() {
        val result = PSPStatusMapper.requiresStatusCheck(PaymentOrderStatus.CAPTURE_PENDING_STATUS_CHECK_LATER)
        assertFalse(result)
    }

    @Test
    fun `all transient error statuses should require retry`() {
        val transientStatuses = listOf(
            PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
            PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT,
            PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        )

        transientStatuses.forEach { status ->
            assertTrue(
                PSPStatusMapper.requiresRetryPayment(status),
                "Expected $status to require retry"
            )
        }
    }

    @Test
    fun `no final status should require retry`() {
        val finalStatuses = listOf(
            PaymentOrderStatus.SUCCESSFUL_FINAL,
            PaymentOrderStatus.FAILED_FINAL,
            PaymentOrderStatus.DECLINED_FINAL
        )

        finalStatuses.forEach { status ->
            assertFalse(
                PSPStatusMapper.requiresRetryPayment(status),
                "Expected $status to NOT require retry"
            )
        }
    }

    @Test
    fun `status check and retry should be mutually exclusive for transient errors`() {
        val transientStatuses = listOf(
            PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
            PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT,
            PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        )

        transientStatuses.forEach { status ->
            val requiresRetry = PSPStatusMapper.requiresRetryPayment(status)
            val requiresCheck = PSPStatusMapper.requiresStatusCheck(status)
            assertTrue(
                requiresRetry && !requiresCheck,
                "Expected $status to require retry but NOT status check"
            )
        }
    }

    @Test
    fun `fromPspStatus followed by requiresRetryPayment should work correctly`() {
        val pspStatus = "FAILED_TRANSIENT_ERROR"
        val mappedStatus = PSPStatusMapper.fromPspStatus(pspStatus)
        val requiresRetry = PSPStatusMapper.requiresRetryPayment(mappedStatus)

        assertTrue(requiresRetry)
    }

    @Test
    fun `fromPspStatus followed by requiresStatusCheck should work correctly`() {
        val pspStatus = "AUTH_NEEDED_STAUS_CHECK_LATER"
        val mappedStatus = PSPStatusMapper.fromPspStatus(pspStatus)
        val requiresCheck = PSPStatusMapper.requiresStatusCheck(mappedStatus)

        assertTrue(requiresCheck)
    }
}
