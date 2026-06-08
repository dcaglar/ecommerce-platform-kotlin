package com.dogancaglar.common.db.converter

import com.dogancaglar.common.db.entity.TransferEntity
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.payment.InternalTransfer
import com.dogancaglar.paymentservice.domain.model.payment.InternalTransferStatus
import com.dogancaglar.paymentservice.domain.model.vo.InternalTransferId
import com.dogancaglar.paymentservice.domain.model.vo.TxId

object TransferEntityMapper {

    fun toDomain(entity: TransferEntity): InternalTransfer {
        return InternalTransfer.rehydrate(
            transferId          = InternalTransferId(entity.transferId),
            sourceTransactionId = TxId(entity.sourceTransactionId),
            amount              = Amount.of(entity.amountValue, Currency(entity.currency)),
            reversedAmount      = Amount.of(entity.reversedAmountValue, Currency(entity.currency)),
            targetAccountType   = AccountType.valueOf(entity.targetAccountType),
            targetEntityId      = entity.targetEntityId,
            sourceAccountType   = AccountType.valueOf(entity.sourceAccountType),
            sourceEntityId      = entity.sourceEntityId,
            status              = InternalTransferStatus.valueOf(entity.status),
            createdAt           = entity.createdAt,
            updatedAt           = entity.updatedAt
        )
    }

    fun toEntity(domain: InternalTransfer): TransferEntity {
        return TransferEntity(
            transferId          = domain.transferId.value,
            sourceTransactionId = domain.sourceTransactionId.value,
            amountValue         = domain.amount.quantity,
            currency            = domain.amount.currency.currencyCode,
            reversedAmountValue = domain.reversedAmount.quantity,
            targetAccountType   = domain.targetAccountType.name,
            targetEntityId      = domain.targetEntityId,
            sourceAccountType   = domain.sourceAccountType.name,
            sourceEntityId      = domain.sourceEntityId,
            status              = domain.status.name,
            createdAt           = domain.createdAt,
            updatedAt           = domain.updatedAt
        )
    }
}
