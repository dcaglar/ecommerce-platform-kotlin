package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.common.db.entity.AccountBalanceEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AccountBalanceReadMapper {
    fun findByAccountCode(accountCode: String): AccountBalanceEntity?
    fun findByAccountCodes(accountCodes: Set<String>): List<AccountBalanceEntity>
    fun findAll(): List<AccountBalanceEntity>
}
