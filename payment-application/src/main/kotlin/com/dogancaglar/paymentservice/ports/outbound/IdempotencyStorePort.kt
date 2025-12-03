package com.dogancaglar.paymentservice.ports.outbound
interface IdempotencyStorePort {
    fun tryInsertPending(key: String, requestHash: String): Boolean
    fun findByKey(key: String): IdempotencyRecord?
    fun updatePaymentId(key: String, paymentId: Long)
    fun updateResponsePayload(key: String, payload: String)
    fun deletePending(key: String)
}