package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.web.dto.AmountDto

object AmountMapper {

    fun toDomain(dto: AmountDto): Amount {
        return Amount(
            value = dto.value,
            currency = dto.currency
        )
    }

    fun toDto(amount: Amount): AmountDto {
        return AmountDto(
            value = amount.value,
            currency = amount.currency
        )
    }
}