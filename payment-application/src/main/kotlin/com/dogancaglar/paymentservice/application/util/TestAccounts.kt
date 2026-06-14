package com.dogancaglar.paymentservice.application.util

object TestAccounts {
    private val ACCOUNTS: Set<String> = setOf("MARKETPLACE-5")

    fun contains(merchantAccount: String): Boolean {
        return ACCOUNTS.contains(merchantAccount)
    }
}
