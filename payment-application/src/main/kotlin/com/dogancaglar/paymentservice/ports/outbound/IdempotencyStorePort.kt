package com.dogancaglar.paymentservice.ports.outbound
interface IdempotencyStorePort {
    fun tryInsertPending(key: String, requestHash: String): Boolean
    fun findByKey(key: String): IdempotencyRecord?
    fun updatePaymentIntentId(key: String, paymentIntentId: Long)
    fun updateResponsePayload(key: String, payload: String, paymentIntentId: Long)
    fun deletePending(key: String)
}