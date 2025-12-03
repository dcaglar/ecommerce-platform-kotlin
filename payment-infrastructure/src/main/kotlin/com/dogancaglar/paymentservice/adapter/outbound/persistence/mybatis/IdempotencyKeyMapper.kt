// payment-infrastructure/src/main/kotlin/.../idempotency/IdempotencyKeyMapper.kt
package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.ports.outbound.IdempotencyRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface IdempotencyKeyMapper {

    /** Returns number of inserted rows (1 for first request, 0 for duplicate) */
    fun insertPending(record: IdempotencyRecord): String?

    fun findByKey(key: String): IdempotencyRecord?

    fun updatePaymentId(
        key: String,
        paymentId: Long
    ): Int

    fun updateResponsePayload(
        key: String,
        payload: String
    ): Int

    fun deletePending(key: String): Int
}