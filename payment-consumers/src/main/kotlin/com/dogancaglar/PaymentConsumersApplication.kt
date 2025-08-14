package com.dogancaglar

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.dogancaglar.infrastructure",
        "com.dogancaglar.consumers",
        "com.dogancaglar.paymentservice"
    ]
)
@EnableScheduling
@EnableAsync
class PaymentConsumersApplication

fun main(args: Array<String>) {
    runApplication<PaymentConsumersApplication>(*args)
}
