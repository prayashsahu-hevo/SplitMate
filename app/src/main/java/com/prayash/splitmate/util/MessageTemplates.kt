package com.prayash.splitmate.util

/** Builds the WhatsApp text sent to friends and the self-reminder note. */
object MessageTemplates {

    private fun money(a: Double): String =
        if (a % 1.0 == 0.0) "₹${a.toLong()}" else "₹%.2f".format(a)

    /** Message to a friend you're splitting with. */
    fun forFriend(
        friendName: String, ownName: String, vendor: String,
        reason: String, total: Double, share: Double
    ): String {
        val me = ownName.ifBlank { "me" }
        val what = listOfNotNull(vendor.ifBlank { null }, reason.ifBlank { null })
            .joinToString(" — ")
            .ifBlank { "our shared expense" }
        return buildString {
            append("Hey ${friendName.ifBlank { "there" }}! 👋\n\n")
            append("We spent ${money(total)} on $what.\n")
            append("Your share is *${money(share)}*.\n\n")
            append("Please send it to $me whenever you get a chance. Thanks! 🙏")
        }
    }

    /** Self-note reminding you to collect from people whose contact you didn't have. */
    fun forSelf(
        unreachedNames: List<String>, vendor: String,
        reason: String, share: Double
    ): String {
        val what = listOfNotNull(vendor.ifBlank { null }, reason.ifBlank { null })
            .joinToString(" — ")
            .ifBlank { "a shared expense" }
        return buildString {
            append("💰 Collection reminder\n\n")
            append("Chase these people for *${money(share)}* each (for $what):\n")
            unreachedNames.forEachIndexed { i, n -> append("${i + 1}. $n\n") }
        }
    }
}
