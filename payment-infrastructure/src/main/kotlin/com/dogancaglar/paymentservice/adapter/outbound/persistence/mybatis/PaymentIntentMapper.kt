package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentIntentEntity
import com.dogancaglar.paymentservice.domain.model.Payment
import org.apache.ibatis.annotations.Mapper

@Mapper
interface PaymentIntentMapper {
    // Removed @Select annotation to avoid duplicate mapping with XML
    fun getMaxPaymentIntentId(): Long?

    fun tryMarkPendingAuth(id: Long, now: java.time.Instant): Int
    fun updatePspReference(paymentIntentId: Long, pspReference: String, now: java.time.Instant): Int

    // Add other CRUD methods as needed, e.g.:
    fun insert(paymentIntent: PaymentIntentEntity): Int
    fun findById(id: Long): PaymentIntentEntity?
    fun update(paymentIntent: PaymentIntentEntity): Int
    fun deleteById(id: Long): Int
    fun deleteAll(): Int
}