package com.dogancaglar.paymentservice.domain.model.ledger

enum class JournalType {
    AUTH_HOLD,
    CAPTURE,
    SETTLEMENT,
    PAYOUT,
    REFUND,
    FEE,
    ADJUSTMENT
}