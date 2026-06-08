package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.common.db.entity.TransferEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface TransferMapper {
    fun insert(transfer: TransferEntity): Int
    fun findById(id: Long): TransferEntity?
    fun update(transfer: TransferEntity): Int
    fun upsert(transfer: TransferEntity): Int
}
