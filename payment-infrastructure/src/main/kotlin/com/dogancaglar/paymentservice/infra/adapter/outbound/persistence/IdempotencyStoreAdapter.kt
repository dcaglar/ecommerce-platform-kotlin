package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.IdempotencyKeyMapper
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
        logger.debug("🔵 [Idempotency] Trying insertPending(key='{}', hash='{}')", key, requestHash)
        val record = IdempotencyRecord(
            idempotencyKey = key,
            requestHash = requestHash
        )
        val start = System.currentTimeMillis()
        val insertedKey = mapper.insertPending(record)
        val finish = System.currentTimeMillis()
        logger.info("IdempotencyStoreAdapter.insertPending took {} ms", finish - start)

        logger.debug(
            "🟡 [Idempotency] insertPending returned key $insertedKey , key: $key")

        val startFind = System.currentTimeMillis()
        val dbRecord = mapper.findByKey(key)
        val finishFind = System.currentTimeMillis()
        logger.info("db.findByKey took {} ms", finishFind - startFind)

        logger.debug(
            "🟢 [Idempotency] After insertPending → DB says: $dbRecord "
        )
        return insertedKey != null      // first request → true, duplicate → false
    }

    override fun findByKey(key: String): IdempotencyRecord? {
        val start = System.currentTimeMillis()
        val result = mapper.findByKey(key)
        val finish = System.currentTimeMillis()
        logger.info("IdempotencyStoreAdapter.findByKey took {} ms", finish - start)
        return result
    }

    override fun updatePaymentIntentId(key: String, paymentIntentId: Long) {
        val start = System.currentTimeMillis()
        mapper.updatePaymentIntentId(key, paymentIntentId)
        val finish = System.currentTimeMillis()
        logger.info("IdempotencyStoreAdapter.updatePaymentIntentId took {} ms", finish - start)
    }

    override fun updateResponsePayload(key: String, payload: String, paymentIntentId: Long) {
        val start = System.currentTimeMillis()
        mapper.updateResponsePayload(key, payload, paymentIntentId)
        val finish = System.currentTimeMillis()
        logger.info("IdempotencyStoreAdapter.updateResponsePayload took {} ms", finish - start)
    }

    override fun deletePending(key: String) {
        val start = System.currentTimeMillis()
        mapper.deletePending(key)
        val finish = System.currentTimeMillis()
        logger.info("IdempotencyStoreAdapter.deletePending took {} ms", finish - start)
    }
}