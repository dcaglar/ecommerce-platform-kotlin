package com.dogancaglar.infrastructure.persistence.repository

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication  // This acts as @SpringBootConfiguration for test
@MapperScan("com.dogancaglar.infrastructure.persistence.repository")
class TestConfig