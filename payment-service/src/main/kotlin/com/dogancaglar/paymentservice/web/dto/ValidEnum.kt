package com.dogancaglar.paymentservice.web.dto

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [EnumValidator::class])
annotation class ValidEnum(
    val enumClass: KClass<out Enum<*>>,
    val message: String = "must be any of enum {enumClass}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)



class EnumValidator : ConstraintValidator<ValidEnum, String> {
    private lateinit var acceptedValues: Set<String>

    override fun initialize(annotation: ValidEnum) {
        acceptedValues = annotation.enumClass.java.enumConstants.map { it.name }.toSet()
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        return value != null && acceptedValues.contains(value)
    }
}