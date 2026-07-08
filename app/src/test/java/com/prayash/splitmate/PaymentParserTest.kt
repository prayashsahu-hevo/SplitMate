package com.prayash.splitmate

import com.prayash.splitmate.service.PaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val GPAY = "com.google.android.apps.nbu.paisa.user"
private const val PAYTM = "net.one97.paytm"

/**
 * JVM unit tests for the notification parser — these run on the Mac (no device needed),
 * so the risky parsing logic is verified before installing anything.
 *
 * NOTE: real UPI wordings are added here as we capture them from the on-device debug log,
 * which locks each confirmed format against regressions.
 */
class PaymentParserTest {

    @Test
    fun gpay_paid_to_person() {
        val p = PaymentParser.parse(GPAY, "Google Pay", "You paid ₹500 to John Doe", 1_000L)
        assertNotNull(p)
        assertEquals(500.0, p!!.amount, 0.001)
        assertEquals("John Doe", p.vendor)
        assertEquals("Google Pay", p.source)
    }

    @Test
    fun gpay_paid_with_decimals_and_comma() {
        val p = PaymentParser.parse(GPAY, "Google Pay", "You paid ₹1,250.50 to Big Bazaar on 7 Jul", 1L)
        assertNotNull(p)
        assertEquals(1250.50, p!!.amount, 0.001)
        assertEquals("Big Bazaar", p.vendor)
    }

    @Test
    fun paytm_rs_paid_to_merchant() {
        val p = PaymentParser.parse(PAYTM, "Paytm", "Rs.300 paid to Coffee House successfully", 1L)
        assertNotNull(p)
        assertEquals(300.0, p!!.amount, 0.001)
        assertEquals("Coffee House", p.vendor)
        assertEquals("Paytm", p.source)
    }

    @Test
    fun paytm_rupee_symbol_paid() {
        val p = PaymentParser.parse(PAYTM, "Paytm", "₹150 paid to Auto Driver", 1L)
        assertNotNull(p)
        assertEquals(150.0, p!!.amount, 0.001)
    }

    @Test
    fun debited_wording_is_outgoing() {
        val p = PaymentParser.parse(PAYTM, "Paytm", "₹99 debited from your account and sent to Zomato", 1L)
        assertNotNull(p)
        assertEquals(99.0, p!!.amount, 0.001)
    }

    @Test
    fun incoming_money_is_ignored() {
        assertNull(PaymentParser.parse(GPAY, "Google Pay", "You received ₹500 from Alice", 1L))
        assertNull(PaymentParser.parse(PAYTM, "Paytm", "₹200 credited to your account", 1L))
    }

    @Test
    fun unsupported_package_is_ignored() {
        assertNull(PaymentParser.parse("com.whatsapp", "WhatsApp", "You paid ₹500 to Bob", 1L))
    }

    @Test
    fun promo_without_payment_verb_is_ignored() {
        assertNull(PaymentParser.parse(PAYTM, "Paytm", "Get ₹50 cashback on your next recharge!", 1L))
    }
}
