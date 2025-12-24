package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId


interface PaymentIntentRepository {
    fun save(paymentIntent: PaymentIntent): PaymentIntent
     fun findById(paymentIntentId: PaymentIntentId): PaymentIntent
        fun getMaxPaymentIntentId(): PaymentIntentId
    fun updatePaymentIntent(paymentIntent: PaymentIntent)
     fun tryMarkPendingAuth(id: PaymentIntentId, now: java.time.Instant): Boolean
     fun updatePspReference(paymentIntentId: Long, pspReference: String, now: java.time.Instant)
    }
