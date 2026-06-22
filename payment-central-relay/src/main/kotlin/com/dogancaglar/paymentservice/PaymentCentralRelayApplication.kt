package com.dogancaglar.paymentservice

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

import org.springframework.context.annotation.Import

@SpringBootApplication
@EnableScheduling
@EnableAsync
class PaymentCentralRelayApplication

fun main(args: Array<String>) {
    runApplication<PaymentCentralRelayApplication>(*args)
}
