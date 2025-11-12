package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.AccountBalanceEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AccountBalanceMapper {
    fun findByAccountCode(accountCode: String): AccountBalanceEntity?
    fun insertOrUpdateSnapshot(snapshot: AccountBalanceEntity): Int
    fun findAll(): List<AccountBalanceEntity>
    fun findByAccountCodes(accountCodes: Set<String>): List<AccountBalanceEntity>
}

