package com.dogancaglar.paymentservice.idempotency

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.IdempotencyKeyMapper
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyRecord
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyStorePort
import org.springframework.stereotype.Repository

@Repository
class IdempotencyStoreAdapter(
    private val mapper: IdempotencyKeyMapper
) : IdempotencyStorePort {

    override fun tryInsertPending(key: String, requestHash: String): Boolean {
        val record = IdempotencyRecord(
            idempotencyKey = key,
            requestHash = requestHash
        )
        return mapper.insertPending(record) == 1
    }

    override fun findByKey(key: String): IdempotencyRecord? =
        mapper.findByKey(key)

    override fun updatePaymentId(key: String, paymentId: Long) {
        mapper.updatePaymentId(key, paymentId)
    }

    override fun updateResponsePayload(key: String, payload: String) {
        mapper.updateResponsePayload(key, payload)
    }
}