package com.dogancaglar.paymentservice.domain.model


enum class PaymentOrderStatus {
    // Initial state upon creation
    INITIATED_PENDING,

    // Final successful state
    SUCCESSFUL_FINAL,

    // Finalized failure states(non-retryable failures)
    FAILED_FINAL,
    DECLINED_FINAL,
    UNKNOWN_FINAL,            // UNKNOW

    // RETRYAsBLE FAILURES
    FAILED_TRANSIENT_ERROR,                 // Generic failure
    TIMEOUT_EXCEEDED_1S_TRANSIENT,                // PSP call timeout
    PSP_UNAVAILABLE_TRANSIENT,        // PSP temporarily down

    // PSP responses requiring status  check(non-final)
    AUTH_NEEDED_STAUS_CHECK_LATER,            // 3D Secure or authentication needed
    PENDING_STATUS_CHECK_LATER,                // Payment is pending, check later
    CAPTURE_PENDING_STATUS_CHECK_LATER,            // 3D Secure or authentication needed
}