package com.prayash.splitmate

import com.prayash.splitmate.data.NotifLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money-only log is the survey's signal: if [NotifLog.looksFinancial] drops a real
 * payment notification, that payment never reaches us and we would wrongly conclude the
 * app is silent. So it is tuned to over-capture, and these tests pin that down.
 */
class NotifLogFilterTest {

    @Test
    fun `captures upi app outgoing wordings`() {
        assertTrue(NotifLog.looksFinancial("You paid ₹500 to Rahul"))
        assertTrue(NotifLog.looksFinancial("Paid Rs.1,200 to Big Bazaar"))
        assertTrue(NotifLog.looksFinancial("₹250 sent to Anil Kumar"))
        assertTrue(NotifLog.looksFinancial("INR 99 debited for UPI payment"))
    }

    @Test
    fun `captures bank debit notifications`() {
        // The bank is the fallback when a UPI app posts nothing for outgoing payments.
        assertTrue(NotifLog.looksFinancial("A/c XX1234 debited by 500.00 on 20-09-26 UPI/P2P"))
        assertTrue(NotifLog.looksFinancial("Rs 500 debited from your account. Bal Rs 12,340"))
        assertTrue(NotifLog.looksFinancial("Txn of 750 on your card"))
    }

    @Test
    fun `captures incoming too so dedup and direction can be studied`() {
        assertTrue(NotifLog.looksFinancial("₹500 received from Rahul"))
        assertTrue(NotifLog.looksFinancial("A/c credited by Rs 2000"))
    }

    @Test
    fun `ignores ordinary chat and system noise`() {
        assertFalse(NotifLog.looksFinancial("Rahul: see you at 6"))
        assertFalse(NotifLog.looksFinancial("3 new messages"))
        assertFalse(NotifLog.looksFinancial("Screenshot saved"))
        assertFalse(NotifLog.looksFinancial(null))
        assertFalse(NotifLog.looksFinancial("   "))
    }
}
