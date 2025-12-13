package com.dogancaglar.paymentservice.idempotency

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.IdempotencyKeyMapper
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyRecord
import com.dogancaglar.paymentservice.ports.outbound.IdempotencyStorePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class IdempotencyStoreAdapter(
    private val mapper: IdempotencyKeyMapper
) : IdempotencyStorePort {
    private val logger = LoggerFactory.getLogger(IdempotencyStoreAdapter::class.java)


    override fun tryInsertPending(key: String, requestHash: String): Boolean {
        logger.info("🔵 [Idempotency] Trying insertPending(key='{}', hash='{}')", key, requestHash)
        val record = IdempotencyRecord(
            idempotencyKey = key,
            requestHash = requestHash
        )
        val insertedKey = mapper.insertPending(record)
        logger.info(
            "🟡 [Idempotency] insertPending returned key $insertedKey , key: $key")
        val dbRecord = mapper.findByKey(key)
        logger.info(
            "🟢 [Idempotency] After insertPending → DB says: $dbRecord "
        )
        return insertedKey != null      // first request → true, duplicate → false
    }

    override fun findByKey(key: String): IdempotencyRecord? =
        mapper.findByKey(key)

    override fun updatePaymentIntentId(key: String, paymentIntentId: Long) {
        mapper.updatePaymentIntentId(key, paymentIntentId)
    }

    override fun updateResponsePayload(key: String, payload: String, paymentIntentId: Long) {
        mapper.updateResponsePayload(key, payload, paymentIntentId)
    }

    override fun deletePending(key: String) {
        mapper.deletePending(key)
    }
}