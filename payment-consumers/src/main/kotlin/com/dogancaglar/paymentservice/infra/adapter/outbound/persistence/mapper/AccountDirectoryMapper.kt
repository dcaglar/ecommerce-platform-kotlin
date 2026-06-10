package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface AccountDirectoryMapper {
    fun findByEntityAndType(
        @Param("accountType") accountType: String,
        @Param("masterAccountCode") masterAccountCode: String
    ): AccountProfile?

    fun findByAccountCode(
        @Param("accountCode") accountCode: String
    ): AccountProfile?

    fun insertAccount(params: Map<String, Any>)
}
