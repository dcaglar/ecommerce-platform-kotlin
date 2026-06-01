package com.dogancaglar.paymentservice.adapter.inbound.rest.dto

import com.dogancaglar.port.out.web.dto.AmountDto
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * PaymentSplitRequestDTO
 *
 * REST-layer DTO for a single split routing instruction in the inbound
 * CreatePaymentIntent API request.
 *
 * Strict layer separation:
 *  - This class lives in the REST adapter layer (payment-service).
 *  - It uses Jakarta validation annotations because it is bound to an
 *    HTTP request body — annotations that have no place in domain code.
 *  - It carries [BalanceAccountTypeDto] directly as an enum because Spring MVC
 *    can deserialize enum values from JSON by name without custom converters.
 *  - Conversion to the application-layer [PaymentSplitDto] (and then to the
 *    domain [PaymentSplit]) happens exclusively in [PaymentRequestMapper].
 *
 * THREE-LAYER CHAIN for this concept:
 *   PaymentSplitRequestDTO  (REST layer — Jakarta validation, Spring MVC)
 *       ↓  PaymentRequestMapper.toPaymentSplitDto()
 *   PaymentSplitDto         (Application layer — Jackson @JsonProperty, event transport)
 *       ↓  PaymentSplitDto.toDomain()
 *   PaymentSplit            (Domain layer — pure Kotlin, no frameworks)
 *
 * @param targetAccountType  Which internal ledger bucket receives these funds.
 * @param targetEntityId     Identifier of the beneficiary entity (seller ID, etc.).
 * @param amount             Amount to route, in smallest currency unit.
 */
data class PaymentSplitRequestDTO(
    @field:NotNull
    val targetAccountType: BalanceAccountTypeDto,

    @field:NotBlank
    val targetEntityId: String,

    @field:NotNull
    @field:Valid
    val amount: AmountDto
)
