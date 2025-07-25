package com.dogancaglar.port.out.adapter.persistance

import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Configuration

@Configuration
@MapperScan("com.dogancaglar.paymentservice.adapter.persistance")
class MyBatisConfig {
    // Optional: Custom type handlers, etc.
}