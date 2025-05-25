package com.dogancaglar.paymentservice.domain.model


enum class PaymentOrderStatus {
    // Initial state upon creation
    INITIATED,

    // Final successful state
    SUCCESSFUL,

    // Final failure states
    FINALIZE_FAILED,
    CANCELLED,  // Optional: for user/system cancellations

    // Retryable PSP failures
    FAILED,                 // Generic failure
    DECLINED,               // Card declined
    INSUFFICIENT_FUNDS,     // Not enough balance
    TIMEOUT,                // PSP call timeout
    PSP_UNAVAILABLE,        // PSP temporarily down
    PENDING,
    CAPTURE_PENDING,

    // PSP responses requiring follow-up (non-final)
    PENDING_CONFIG,         // PSP requires additional setup or merchant onboarding
    AUTH_NEEDED,            // 3D Secure or authentication needed
    REVIEW,                 // Fraud review or manual review pending

    // Other transitional or error states
    RETRY_SCHEDULED,// Internally used to mark retry scheduling, optional
    UNKNOWN
}