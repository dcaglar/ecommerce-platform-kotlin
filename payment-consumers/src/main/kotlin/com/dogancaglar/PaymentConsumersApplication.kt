package com.dogancaglar

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.dogancaglar.infrastructure",
        "com.dogancaglar.consumers"
    ]
)
@MapperScan("com.dogancaglar.infrastructure.persistence.repository")
class PaymentConsumersApplication

fun main(args: Array<String>) {
    runApplication<PaymentConsumersApplication>(*args)
}
