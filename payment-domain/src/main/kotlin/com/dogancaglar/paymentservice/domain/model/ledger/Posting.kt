package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Amount

sealed class Posting(val account: Account, val amount: Amount){
        class Debit(account: Account,amount: Amount): Posting(account,amount) {
            override fun getSignedAmount(): Amount {
                if(account.isDebitAccount()){
                    return amount
                } else {
                    return Amount(amount.value*-1,amount.currency)
                }
            }
        }
    class Credit(account: Account,amount: Amount): Posting(account,amount) {
        override fun getSignedAmount(): Amount {
            if(account.isCreditAccount()){
                return amount
            } else {
                return Amount(amount.value*-1,amount.currency)
            }
        }
    }

    abstract  fun getSignedAmount(): Amount
}
