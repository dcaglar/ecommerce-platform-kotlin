// payment-infrastructure/src/main/kotlin/.../idempotency/IdempotencyKeyMapper.kt
package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.ports.outbound.IdempotencyRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface IdempotencyKeyMapper {

    /** Returns number of inserted rows (1 for first request, 0 for duplicate) */
    fun insertPending(record: IdempotencyRecord): Int

    fun findByKey(@Param("id") key: String): IdempotencyRecord?

    fun updatePaymentId(
        @Param("key") key: String,
        @Param("paymentId") paymentId: Long
    ): Int

    fun updateResponsePayload(
        @Param("key") key: String,
        @Param("payload") payload: String
    ): Int
}