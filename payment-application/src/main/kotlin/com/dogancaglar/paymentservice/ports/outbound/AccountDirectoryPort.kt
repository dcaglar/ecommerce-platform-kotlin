package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType

interface AccountDirectoryPort {
    fun getAccountProfile(accountType: AccountType, entityId: String): AccountProfile
}