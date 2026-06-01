package com.dogancaglar.paymentservice

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan(basePackages = ["com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper"])
class PaymentConsumersApplication

fun main(args: Array<String>) {
    runApplication<PaymentConsumersApplication>(*args)
}
