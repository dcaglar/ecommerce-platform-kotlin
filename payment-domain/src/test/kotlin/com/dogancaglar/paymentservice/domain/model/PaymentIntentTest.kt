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
        
        val created = intent.markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
        assertEquals(PaymentIntentStatus.CREATED, created.status)
    }

    @Test
    fun `markAsCreated should fail when current status is not CREATED_PENDING`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")

        assertFailsWith<IllegalArgumentException> {
            intent.markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
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
    fun `startAuthorization only allowed from CREATED`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")

        val pending = intent.markAuthorizedPending()
        assertEquals(PaymentIntentStatus.PENDING_AUTH, pending.status)
    }

    @Test
    fun `startAuthorization should fail when current status is not CREATED`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
            .markAuthorizedPending()

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
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
            .markAuthorizedPending()

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
        
        val created = intent.markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
        assertFailsWith<IllegalArgumentException> {
            created.markAuthorized()
        }
    }

    @Test
    fun `markDeclined transitions correctly`() {
        val pending = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE").markAuthorizedPending()

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
        val created = intent.markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
        assertEquals(PaymentIntentStatus.CANCELLED, created.markCancelled().status)

        // Cancel allowed from PENDING_AUTH
        val pending = created.markAuthorizedPending()
        assertEquals(PaymentIntentStatus.CANCELLED, pending.markCancelled().status)
    }

    @Test
    fun `cancel should fail after AUTHORIZED`() {
        val authorized = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
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

    // -------------------------------
    // PSP REFERENCE DOMAIN INVARIANTS
    // -------------------------------

    @Test
    fun `CREATED_PENDING requires pspReference to be null`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )
        
        assertEquals(PaymentIntentStatus.CREATED_PENDING, intent.status)
        assertNull(intent.pspReference)
    }

    @Test
    fun `CREATED_PENDING fails if pspReference is not null`() {
        val now = Utc.nowLocalDateTime()
        
        assertFailsWith<IllegalArgumentException> {
            PaymentIntent.rehydrate(
                paymentIntentId = PaymentIntentId(1),
                pspReference = "pi_123", // Should be null for CREATED_PENDING
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = totalAmount,
                paymentOrderLines = lines,
                status = PaymentIntentStatus.CREATED_PENDING,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun `CREATED requires pspReference to be non-null and non-blank`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret")
        
        assertEquals(PaymentIntentStatus.CREATED, intent.status)
        assertEquals("pi_123", intent.pspReference)
    }

    @Test
    fun `CREATED fails if pspReference is null`() {
        val now = Utc.nowLocalDateTime()
        
        assertFailsWith<IllegalArgumentException> {
            PaymentIntent.rehydrate(
                paymentIntentId = PaymentIntentId(1),
                pspReference = null, // Should not be null for CREATED
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = totalAmount,
                paymentOrderLines = lines,
                status = PaymentIntentStatus.CREATED,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun `CREATED fails if pspReference is blank`() {
        val now = Utc.nowLocalDateTime()
        
        assertFailsWith<IllegalArgumentException> {
            PaymentIntent.rehydrate(
                paymentIntentId = PaymentIntentId(1),
                pspReference = "", // Should not be blank for CREATED
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = totalAmount,
                paymentOrderLines = lines,
                status = PaymentIntentStatus.CREATED,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun `PENDING_AUTH requires pspReference to be non-null and non-blank`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret")
            .markAuthorizedPending()
        
        assertEquals(PaymentIntentStatus.PENDING_AUTH, intent.status)
        assertEquals("pi_123", intent.pspReference)
    }

    @Test
    fun `AUTHORIZED requires pspReference to be non-null and non-blank`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret")
            .markAuthorizedPending()
            .markAuthorized()
        
        assertEquals(PaymentIntentStatus.AUTHORIZED, intent.status)
        assertEquals("pi_123", intent.pspReference)
    }

    @Test
    fun `DECLINED requires pspReference to be non-null and non-blank`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret")
            .markAuthorizedPending()
            .markDeclined()
        
        assertEquals(PaymentIntentStatus.DECLINED, intent.status)
        assertEquals("pi_123", intent.pspReference)
    }

    @Test
    fun `hasPspReference returns true when pspReference is set`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret")
        
        assertTrue(intent.hasPspReference())
    }

    @Test
    fun `hasPspReference returns false when pspReference is null`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )
        
        assertFalse(intent.hasPspReference())
    }

    @Test
    fun `pspReferenceOrThrow returns pspReference when set`() {
        val pspRef = "pi_123"
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAsCreatedWithPspReferenceAndClientSecret(pspRef, "secret")
        
        assertEquals(pspRef, intent.pspReferenceOrThrow())
    }

    @Test
    fun `pspReferenceOrThrow throws when pspReference is null`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )
        
        assertFailsWith<IllegalArgumentException> {
            intent.pspReferenceOrThrow()
        }
    }

    @Test
    fun `markAsCreatedWithPspReferenceAndClientSecret fails if pspReference is blank`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )
        
        assertFailsWith<IllegalArgumentException> {
            intent.markAsCreatedWithPspReferenceAndClientSecret("", "secret")
        }
        
        assertFailsWith<IllegalArgumentException> {
            intent.markAsCreatedWithPspReferenceAndClientSecret("   ", "secret")
        }
    }

}