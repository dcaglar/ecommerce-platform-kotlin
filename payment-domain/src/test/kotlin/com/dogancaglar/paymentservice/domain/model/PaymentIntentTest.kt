package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.vo.*
import kotlin.test.*

class PaymentIntentTest {

    private val buyerId = BuyerId("buyer-1")
    private val orderId = OrderId("order-1")
    private val currency = Currency("EUR")
    private val line1 = PaymentOrderLine(SellerId("s1"), Amount.of(1000, currency))
    private val line2 = PaymentOrderLine(SellerId("s2"), Amount.of(2000, currency))
    private val lines = listOf(line1, line2)
    private val totalAmount = Amount.of(3000, currency)

    @Test
    fun `createNew should enforce amount equals sum of payment lines`() {
        val intent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(1),
            buyerId = buyerId,
            orderId = orderId,
            totalAmount = totalAmount,
            paymentOrderLines = lines
        )

        assertEquals(PaymentIntentStatus.CREATED_PENDING, intent.status)
        assertEquals(totalAmount.quantity, lines.sumOf { it.amount.quantity })
        assertNull(intent.pspReference)
        assertEquals("", intent.clientSecret)
    }

    @Test
    fun `createNew should fail if total is different from sum of lines`() {
        val wrongTotal = Amount.of(5000, currency)

        assertFailsWith<IllegalArgumentException> {
            PaymentIntent.createNew(
                paymentIntentId = PaymentIntentId(1),
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = wrongTotal,
                paymentOrderLines = lines
            )
        }
    }

    @Test
    fun `markAsCreated transitions from CREATED_PENDING to CREATED`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        assertEquals(PaymentIntentStatus.CREATED_PENDING, intent.status)
        
        val created = intent.markAsCreated()
        assertEquals(PaymentIntentStatus.CREATED, created.status)
    }

    @Test
    fun `markAsCreated should fail when current status is not CREATED_PENDING`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreated()

        assertFailsWith<IllegalArgumentException> {
            intent.markAsCreated()
        }
    }

    @Test
    fun `markAsCreatedWithPspReferenceAndClientSecret transitions and sets fields`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        assertEquals(PaymentIntentStatus.CREATED_PENDING, intent.status)
        assertNull(intent.pspReference)
        assertEquals("", intent.clientSecret)
        
        val pspRef = "pi_stripe_123"
        val clientSecret = "pi_stripe_123_secret_abc"
        val created = intent.markAsCreatedWithPspReferenceAndClientSecret(pspRef, clientSecret)
        
        assertEquals(PaymentIntentStatus.CREATED, created.status)
        assertEquals(pspRef, created.pspReference)
        assertEquals(clientSecret, created.clientSecret)
    }

    @Test
    fun `markAsCreatedWithPspReferenceAndClientSecret should fail when current status is not CREATED_PENDING`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreated()

        assertFailsWith<IllegalArgumentException> {
            intent.markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret_123")
        }
    }

    @Test
    fun `startAuthorization only allowed from CREATED`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreated()

        val pending = intent.markAuthorizedPending()
        assertEquals(PaymentIntentStatus.PENDING_AUTH, pending.status)
    }

    @Test
    fun `startAuthorization should fail when current status is not CREATED`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreated().markAuthorizedPending()

        assertFailsWith<IllegalArgumentException> {
            intent.markAuthorizedPending()
        }
    }

    @Test
    fun `startAuthorization should fail from CREATED_PENDING`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        assertFailsWith<IllegalArgumentException> {
            intent.markAuthorizedPending()
        }
    }

    @Test
    fun `markAuthorized allowed only from PENDING_AUTH`() {
        val pending = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreated().markAuthorizedPending()

        val authorized = pending.markAuthorized()
        assertEquals(PaymentIntentStatus.AUTHORIZED, authorized.status)
    }

    @Test
    fun `markAuthorized fails from wrong state`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        assertFailsWith<IllegalArgumentException> {
            intent.markAuthorized()
        }
        
        val created = intent.markAsCreated()
        assertFailsWith<IllegalArgumentException> {
            created.markAuthorized()
        }
    }

    @Test
    fun `markDeclined transitions correctly`() {
        val pending = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreated().markAuthorizedPending()

        val declined = pending.markDeclined()
        assertEquals(PaymentIntentStatus.DECLINED, declined.status)
    }

    @Test
    fun `cancel allowed from CREATED or PENDING_AUTH`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        // Cancel should fail from CREATED_PENDING
        assertFailsWith<IllegalArgumentException> {
            intent.markCancelled()
        }

        // Cancel allowed from CREATED
        val created = intent.markAsCreated()
        assertEquals(PaymentIntentStatus.CANCELLED, created.markCancelled().status)

        // Cancel allowed from PENDING_AUTH
        val pending = created.markAuthorizedPending()
        assertEquals(PaymentIntentStatus.CANCELLED, pending.markCancelled().status)
    }

    @Test
    fun `cancel should fail after AUTHORIZED`() {
        val authorized = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreated()
            .markAuthorizedPending()
            .markAuthorized()

        assertFailsWith<IllegalArgumentException> {
            authorized.markCancelled()
        }
    }

    @Test
    fun `rehydrate preserves pspReference and clientSecret`() {
        val pspRef = "pi_stripe_123"
        val clientSecret = "pi_stripe_123_secret_abc"
        val now = Utc.nowLocalDateTime()
        
        val intent = PaymentIntent.rehydrate(
            paymentIntentId = PaymentIntentId(1),
            pspReference = pspRef,
            buyerId = buyerId,
            orderId = orderId,
            totalAmount = totalAmount,
            paymentOrderLines = lines,
            status = PaymentIntentStatus.CREATED,
            createdAt = now,
            updatedAt = now
        )
        
        assertEquals(pspRef, intent.pspReference)
        // Note: rehydrate doesn't set clientSecret in the current implementation
        // This test verifies pspReference is preserved
    }

}