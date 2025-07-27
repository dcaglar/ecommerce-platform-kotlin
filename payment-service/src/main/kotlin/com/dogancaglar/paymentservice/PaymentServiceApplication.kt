package com.dogancaglar.paymentservice

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ComponentScan(
    basePackages = [
        "com.dogancaglar.paymentservice", "com.dogancaglar.infrastructure"
    ]
)
@MapperScan("com.dogancaglar.infrastructure.persistence.repository")
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}