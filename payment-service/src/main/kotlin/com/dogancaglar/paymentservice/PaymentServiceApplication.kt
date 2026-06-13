package com.dogancaglar.paymentservice

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.DataSourceConfig
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.MultiDbLiquibaseConfig
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
class PaymentServiceApplication

fun main(args: Array<String>) {
    val profiles = System.getProperty("spring.profiles.active") ?: System.getenv("SPRING_PROFILES_ACTIVE") ?: ""

    if (profiles.contains("liquibase-job")) {
        SpringApplicationBuilder(MultiDbLiquibaseConfig::class.java, DataSourceConfig::class.java)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .profiles("liquibase-job")
            // This is crucial: make sure the properties are actually loaded
            .properties("spring.config.additional-location=classpath:application-liquibase-job.yml")
            .run(*args)
        System.exit(0)
    }else {
        // Run the actual web app
        runApplication<PaymentServiceApplication>(*args)
    }
}