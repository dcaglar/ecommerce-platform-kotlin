package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.payment.domain.model.Amount
import com.dogancaglar.paymentservice.web.dto.AmountDto
import com.dogancaglar.paymentservice.web.dto.CurrencyEnum

object AmountMapper {

    fun toDomain(dto: AmountDto): Amount {
        return Amount(
            value = dto.value,
            currency = dto.currency.name
        )
    }

    fun toDto(amount: Amount): AmountDto {
        return AmountDto(
            value = amount.value,
            currency = CurrencyEnum.valueOf(amount.currency)
        )
    }
}