package com.prayash.splitmate

import com.prayash.splitmate.data.Payment
import com.prayash.splitmate.data.RecentPayments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The hand-off between the two signals. Getting this wrong shows up as either a missing
 * amount (prompt asks when it did not need to) or a double-logged payment (both the UPI app
 * and the bank announced the same spend).
 */
class RecentPaymentsTest {

    private val t0 = System.currentTimeMillis()

    private fun payment(amount: Double, source: String, at: Long) =
        Payment(amount, "Rahul", source, at, "raw")

    @Before
    fun setUp() = RecentPayments.clear()

    @Test
    fun `claims a payment inside the session window`() {
        RecentPayments.record(payment(500.0, "Google Pay", t0))
        val claimed = RecentPayments.claim(t0 - 10_000, t0 + 10_000, "Google Pay")
        assertNotNull(claimed)
        assertEquals(500.0, claimed!!.amount, 0.001)
    }

    @Test
    fun `ignores a payment outside the window`() {
        RecentPayments.record(payment(500.0, "Google Pay", t0 - 300_000))
        assertNull(RecentPayments.claim(t0 - 10_000, t0 + 10_000, "Google Pay"))
    }

    @Test
    fun `prefers the upi app over the bank for the same payment`() {
        // Both announce it; the UPI app's wording names the payee, so it wins.
        RecentPayments.record(payment(500.0, "HDFC Bank", t0))
        RecentPayments.record(payment(500.0, "Paytm", t0 + 1_000))
        val claimed = RecentPayments.claim(t0 - 10_000, t0 + 10_000, "Paytm")
        assertEquals("Paytm", claimed!!.source)
    }

    @Test
    fun `claiming removes the entry so one payment is never logged twice`() {
        RecentPayments.record(payment(500.0, "Google Pay", t0))
        assertNotNull(RecentPayments.claim(t0 - 10_000, t0 + 10_000, "Google Pay"))
        assertNull(RecentPayments.claim(t0 - 10_000, t0 + 10_000, "Google Pay"))
    }

    @Test
    fun `falls back to the bank when the upi app said nothing`() {
        // This is the Paytm case: only the bank announced it.
        RecentPayments.record(payment(500.0, "HDFC Bank", t0))
        val claimed = RecentPayments.claim(t0 - 10_000, t0 + 10_000, "Paytm")
        assertEquals("HDFC Bank", claimed!!.source)
    }

    @Test
    fun `nothing recorded means nothing to claim`() {
        assertNull(RecentPayments.claim(t0 - 10_000, t0 + 10_000, "Paytm"))
    }
}
