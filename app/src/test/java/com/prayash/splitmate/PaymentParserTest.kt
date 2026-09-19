package com.prayash.splitmate

import com.prayash.splitmate.service.PaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parser no longer decides *whether* a payment happened — the usage watcher does that.
 * Its job is to recover the amount, from whichever app announced it: the UPI app, or the bank.
 *
 * So the tests that matter are: does it get the right number out of real wordings, and does it
 * stay quiet on anything that is not a completed outgoing payment.
 */
class PaymentParserTest {

    private val now = 1_700_000_000_000L

    private fun parse(text: String, source: String = "Google Pay") =
        PaymentParser.parse(source, null, text, now)

    // ------------------------------------------------ UPI app wordings

    @Test
    fun `gpay you paid`() {
        val p = parse("You paid ₹500 to Rahul Sharma")
        assertNotNull(p)
        assertEquals(500.0, p!!.amount, 0.001)
        assertEquals("Rahul Sharma", p.vendor)
    }

    @Test
    fun `amount with comma and decimals`() {
        val p = parse("Paid Rs.1,250.50 to Big Bazaar")
        assertEquals(1250.50, p!!.amount, 0.001)
        assertEquals("Big Bazaar", p.vendor)
    }

    @Test
    fun `amount before the verb`() {
        val p = parse("₹250 sent to Anil Kumar successfully")
        assertEquals(250.0, p!!.amount, 0.001)
        assertEquals("Anil Kumar", p.vendor)
    }

    @Test
    fun `inr prefix`() {
        assertEquals(99.0, parse("INR 99 debited for UPI payment")!!.amount, 0.001)
    }

    // ------------------------------------------------ bank wordings
    // These matter most: the bank is what covers UPI apps that post nothing themselves.

    @Test
    fun `bank debit picks the amount not the closing balance`() {
        val p = parse("Rs 500 debited from your account. Bal Rs 12,340", source = "HDFC Bank")
        assertNotNull(p)
        assertEquals(500.0, p!!.amount, 0.001)
    }

    @Test
    fun `bank debited by with no currency token`() {
        val p = parse("A/c XX1234 debited by 750.00 on 20-09-26", source = "ICICI Bank")
        assertEquals(750.0, p!!.amount, 0.001)
    }

    @Test
    fun `payee recovered from a upi reference string`() {
        val p = parse(
            "A/c XX1234 debited by 300.00 UPI/P2P/512345678901/RAHUL KUMAR",
            source = "Axis Bank"
        )
        assertEquals(300.0, p!!.amount, 0.001)
        assertEquals("RAHUL KUMAR", p.vendor)
    }

    @Test
    fun `source is carried through so the watcher can prefer the upi app over the bank`() {
        assertEquals("Paytm", parse("Paid ₹40 to Chai Point", source = "Paytm")!!.source)
    }

    // ------------------------------------------------ must stay quiet

    @Test
    fun `incoming money is ignored`() {
        assertNull(parse("You received ₹500 from Rahul"))
        assertNull(parse("A/c credited by Rs 2000", source = "SBI"))
    }

    @Test
    fun `payment requests are ignored`() {
        assertNull(parse("Rahul is requesting ₹500"))
        assertNull(parse("Payment request of ₹200 from Anil"))
    }

    @Test
    fun `failed and pending payments are ignored`() {
        assertNull(parse("Your payment of ₹500 failed"))
        assertNull(parse("Payment of ₹500 is pending"))
        assertNull(parse("Payment of ₹500 was declined"))
    }

    @Test
    fun `cashback and refunds are ignored`() {
        assertNull(parse("You got ₹50 cashback"))
        assertNull(parse("Refund of ₹120 paid to your account"))
    }

    @Test
    fun `text with no amount is ignored`() {
        assertNull(parse("You paid Rahul"))
    }

    @Test
    fun `ordinary chat is ignored`() {
        assertNull(parse("Rahul: I sent you the photos", source = "WhatsApp"))
        assertNull(parse("3 new messages", source = "WhatsApp"))
    }

    @Test
    fun `blank input is ignored`() {
        assertNull(PaymentParser.parse("Google Pay", null, null, now))
        assertNull(PaymentParser.parse("Google Pay", "", "   ", now))
    }
}
