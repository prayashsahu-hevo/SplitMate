package com.prayash.splitmate.data

/**
 * Shared state between the two detection signals.
 *
 * A notification that names an amount is already proof a payment happened, so the listener
 * prompts on it directly. The usage watcher exists for the harder case where *nothing*
 * announced the payment — it fires on the UPI session ending instead.
 *
 * Both can see the same payment, so entries are kept here with a [Entry.prompted] flag and
 * the watcher stays quiet about anything the listener already raised.
 *
 * In-memory on purpose: entries matter for seconds, and this avoids persisting financial
 * data we have no reason to keep.
 */
object RecentPayments {

    private const val TTL_MS = 90_000L

    /** Duplicate posts of one payment (Truecaller posts the same debit three times). */
    private const val DUPLICATE_WINDOW_MS = 60_000L

    private class Entry(val payment: Payment, var prompted: Boolean = false)

    private val entries = mutableListOf<Entry>()
    private val lock = Any()

    /** A claimed payment, and whether it was already shown to the user. */
    data class Claim(val payment: Payment, val alreadyPrompted: Boolean)

    /**
     * Record a parsed payment.
     *
     * Duplicates are still stored rather than dropped: when both the bank and the UPI app
     * announce one payment, [claim] needs both present to prefer the UPI app's wording, which
     * names the payee. Only the prompting decision is deduplicated.
     *
     * @return true if this is the first sighting of the payment, and so worth prompting about.
     */
    fun recordIfNew(payment: Payment): Boolean {
        synchronized(lock) {
            prune()
            val group = duplicatesOf(payment)
            entries.add(Entry(payment, prompted = group.any { it.prompted }))
            return group.isEmpty()
        }
    }

    /**
     * Note that the user has been shown this payment. Marks every sighting of it, so the
     * watcher stays quiet even if it later claims the bank's copy rather than the UPI app's.
     */
    fun markPrompted(payment: Payment) {
        synchronized(lock) {
            entries.filter {
                it.payment === payment || isSamePayment(it.payment, payment)
            }.forEach { it.prompted = true }
        }
    }

    /** Sightings of the same payment already on record, excluding [payment] itself. */
    private fun duplicatesOf(payment: Payment): List<Entry> =
        entries.filter { it.payment !== payment && isSamePayment(it.payment, payment) }

    private fun isSamePayment(a: Payment, b: Payment): Boolean =
        a.amount == b.amount &&
            kotlin.math.abs(a.timestampMillis - b.timestampMillis) < DUPLICATE_WINDOW_MS

    /**
     * Claim a payment seen between [fromMillis] and [toMillis], preferring one from [pkgLabel]
     * (the UPI app the user was in) over a bank's notification about the same payment.
     *
     * Claiming removes it, so one payment cannot be logged twice when both announce it.
     */
    fun claim(fromMillis: Long, toMillis: Long, pkgLabel: String?): Claim? {
        synchronized(lock) {
            prune()
            val inWindow = entries.filter { it.payment.timestampMillis in fromMillis..toMillis }
            if (inWindow.isEmpty()) return null

            val best = inWindow.firstOrNull { pkgLabel != null && it.payment.source == pkgLabel }
                ?: inWindow.maxByOrNull { it.payment.timestampMillis }!!
            entries.remove(best)
            return Claim(best.payment, best.prompted)
        }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - TTL_MS
        entries.removeAll { it.payment.timestampMillis < cutoff }
    }
}
