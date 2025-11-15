package  com.dogancaglar.paymentservice.domain.events



import java.time.LocalDateTime

interface PaymentEvent {
    val paymentId: String
    val publicPaymentId: String
    val buyerId: String
    val orderId: String
    val totalAmountValue: Long
    val currency: String
    val status: String
    val createdAt: LocalDateTime
    val updatedAt: LocalDateTime
}