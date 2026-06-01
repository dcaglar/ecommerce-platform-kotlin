import re

with open("payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/inbound/kafka/KafkaTypedConsumerFactoryConfig.kt", "r") as f:
    content = f.read()

# Remove paymentOrderCreatedFactory
content = re.sub(r'    @Bean\("\$\{Topics\.PAYMENT_ORDER_CREATED\}-factory"\)\n    fun paymentOrderCreatedFactory\(.*?batchMode = false\n        \)\n    }', '', content, flags=re.DOTALL)

# Remove paymentOrderPspCallRequestedFactory
content = re.sub(r'    @Bean\("\$\{Topics\.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE\}-factory"\)\n    fun paymentOrderPspCallRequestedFactory\(.*?batchMode = false\n        \)\n    }', '', content, flags=re.DOTALL)

# Remove MissingPaymentOrderException from errorHandler
content = content.replace("                MissingPaymentOrderException::class.java,\n", "")

# Remove imports
content = re.sub(r'import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived\n', '', content)
content = re.sub(r'import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand\n', '', content)
content = re.sub(r'import com.dogancaglar.paymentservice.domain.exception.MissingPaymentOrderException\n', '', content)

with open("payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/inbound/kafka/KafkaTypedConsumerFactoryConfig.kt", "w") as f:
    f.write(content)
