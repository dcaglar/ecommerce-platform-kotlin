package com.dogancaglar.paymentservice.domain.model


enum class PaymentOrderStatus {
    // Initial state upon creation
    INITIATED,

    // Final successful state
    SUCCESSFUL,

    // Finalized failure states(non-retryable failures)
    FINALIZED_FAILED,
    DECLINED,
    UNKNOWN,            // UNKNOW

    // RETRYAsBLE FAILURES
    FAILED,                 // Generic failure
    TIMEOUT,                // PSP call timeout
    PSP_UNAVAILABLE,        // PSP temporarily down

    // PSP responses requiring status  check(non-final)
    AUTH_NEEDED,            // 3D Secure or authentication needed
    PENDING,
    CAPTURE_PENDING,            // 3D Secure or authentication needed
}