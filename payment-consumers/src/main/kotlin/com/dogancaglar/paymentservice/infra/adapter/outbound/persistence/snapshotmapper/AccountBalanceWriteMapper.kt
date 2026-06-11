package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.snapshotmapper

import com.dogancaglar.common.db.entity.AccountBalanceEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AccountBalanceWriteMapper {
    fun insertOrUpdateSnapshot(snapshot: AccountBalanceEntity): Int
}
