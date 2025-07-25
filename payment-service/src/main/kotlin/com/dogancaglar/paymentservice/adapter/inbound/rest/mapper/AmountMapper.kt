package com.dogancaglar.paymentservice.adapter.inbound.rest.mapper

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum

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