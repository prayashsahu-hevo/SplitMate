package com.prayash.splitmate.data

/**
 * A tiny in-memory hand-off between the two detection signals.
 *
 * The notification listener parses an amount whenever a UPI app or a bank app bothers to
 * post one, and drops it here. The usage watcher — which fires for *every* UPI app, including
 * ones that stay silent — then claims a matching entry to pre-fill the amount.
 *
 * When there is a match the prompt is fully populated and stays zero-tap. When there is none
 * (Paytm, which posts nothing for outgoing payments) the prompt still fires, and the user
 * types the amount.
 *
 * In-memory on purpose: entries are only useful for seconds, and this avoids persisting
 * financial data we do not need to keep.
 */
object RecentPayments {

    /** How long a parsed notification stays claimable. */
    private const val TTL_MS = 90_000L

    private val entries = mutableListOf<Payment>()
    private val lock = Any()

    fun record(payment: Payment) {
        synchronized(lock) {
            prune()
            entries.add(payment)
        }
    }

    /**
     * Claim a payment seen between [fromMillis] and [toMillis], preferring one from [pkgLabel]
     * (the UPI app the user was actually in) over a bank's notification about the same payment.
     *
     * Claiming removes it, so a single payment cannot be logged twice when both the UPI app
     * and the bank notify.
     */
    fun claim(fromMillis: Long, toMillis: Long, pkgLabel: String?): Payment? {
        synchronized(lock) {
            prune()
            val inWindow = entries.filter { it.timestampMillis in fromMillis..toMillis }
            if (inWindow.isEmpty()) return null

            val best = inWindow.firstOrNull { pkgLabel != null && it.source == pkgLabel }
                ?: inWindow.maxByOrNull { it.timestampMillis }!!
            entries.remove(best)
            return best
        }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - TTL_MS
        entries.removeAll { it.timestampMillis < cutoff }
    }
}
