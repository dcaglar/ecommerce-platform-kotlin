package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.AccountDirectoryMapper
import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import org.springframework.stereotype.Service

@Service
class AccountDirectoryImpl(
    private val mapper: AccountDirectoryMapper
) : AccountDirectoryPort {

    override fun getAccountProfile(accountType: AccountType, entityId: String): AccountProfile {
        return mapper.findByEntityAndType(accountType.name, entityId)
            ?: throw IllegalArgumentException("Account not found: ${accountType.name}.$entityId")
    }
}