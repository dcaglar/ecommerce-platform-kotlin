package com.dogancaglar.paymentservice.adapter.redis.id


import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class RedisIdGeneratorIntegrationTest {

    @Autowired
    lateinit var idGenerator: RedisIdGenerator

    companion object {
        @Container
        val redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun overrideRedisProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host") { redisContainer.host }
            registry.add("spring.redis.port") { redisContainer.getMappedPort(6379) }
        }
    }

    @Test
    fun `should generate incrementing IDs`() {
        val first = idGenerator.nextId("test-payment-order")
        val second = idGenerator.nextId("test-payment-order")
        assertThat(second).isEqualTo(first + 1)
    }

    @Test
    fun `should generate prefixed ULID for public ID`() {
        val publicId = idGenerator.nextPublicId("paymentorder")
        assertThat(publicId).startsWith("paymentorder-")
    }

    @Test
    fun `should respect setMinValue contract`() {
        val namespace = "test-wallet"
        idGenerator.setMinValue(namespace, 1000L)
        val value1 = idGenerator.getRawValue(namespace)
        assertThat(value1).isEqualTo(1000L)

        idGenerator.setMinValue(namespace, 99L)
        val value2 = idGenerator.getRawValue(namespace)
        assertThat(value2).isEqualTo(1000L)
    }
}
}