package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.yugabyte

import com.dogancaglar.paymentservice.ports.outbound.IdempotencyRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface IdempotencyKeyMapper {

    /** Returns number of inserted rows (1 for first request, 0 for duplicate) */
    fun insertPending(record: IdempotencyRecord): Long?

    fun findByKey(key: String): IdempotencyRecord?

    fun updatePaymentIntentId(
        key: String,
        paymentIntentId: Long
    ): Int

    fun updateResponsePayload(
        key: String,
        payload: String,
        paymentIntentId: Long
    ): Int

    fun deletePending(key: String): Int
}