package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Amount
sealed class Posting(open val account: Account, open val amount: Amount) {

    abstract fun getSignedAmount(): Amount
    class Debit private  constructor(override val account: Account, override val amount: Amount) : Posting(account, amount) {
        override fun getSignedAmount(): Amount {
            if (account.isDebitAccount()) {
                return amount
            } else {
                return amount.negate()
            }
        }

        companion object{
            fun create(account: Account,amount: Amount): Debit
            {
                return Debit(account,amount)
            }
        }

    }
    class Credit private  constructor(override val account: Account, override val amount: Amount) : Posting(account, amount) {
        override fun getSignedAmount(): Amount {
            if (account.isCreditAccount()) {
                return amount
            } else {
                return amount.negate()
            }
        }

        companion object{
            fun create(account: Account,amount: Amount): Credit
            {
                return Credit(account,amount)
            }
        }

    }
}
