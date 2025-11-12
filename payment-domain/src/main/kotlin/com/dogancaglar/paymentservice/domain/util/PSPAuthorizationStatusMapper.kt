package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.model.PaymentStatus

/**
 * Maps PSP authorization responses to internal PaymentStatus values.
 * Used only in the synchronous authorization (web/API) flow.
 */
object PSPAuthorizationStatusMapper {

    fun fromPspAuthCode(code: String): PaymentStatus = when (code.uppercase()) {
        // Typical PSP authorization outcomes
        "AUTHORIZED", "SUCCESS" -> PaymentStatus.AUTHORIZED
        "DECLINED", "INSUFFICIENT_FUNDS", "CARD_EXPIRED" -> PaymentStatus.DECLINED
        else -> PaymentStatus.DECLINED // default defensive fallback
    }

    fun isAuthorized(code: String): Boolean {
        return fromPspAuthCode(code) == PaymentStatus.AUTHORIZED
    }

    fun isDeclined(code: String): Boolean {
        return fromPspAuthCode(code) == PaymentStatus.DECLINED
    }
}