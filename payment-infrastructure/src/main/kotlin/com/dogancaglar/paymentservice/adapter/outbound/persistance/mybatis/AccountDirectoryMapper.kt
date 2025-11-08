package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface AccountDirectoryMapper {
    fun findByEntityAndType(
        @Param("accountType") accountType: String,
        @Param("entityId") entityId: String
    ): AccountProfile?

    fun insertAccount(params: Map<String, Any>)
}