package com.prayash.splitmate.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * The set of UPI apps installed on this device.
 *
 * Discovered dynamically by asking the package manager which apps can handle a `upi://pay`
 * intent — NPCI requires every UPI app to register that handler. This is why the app works
 * on any phone without a hardcoded list: a regional bank's UPI app we have never heard of
 * is found the same way Google Pay is.
 *
 * Requires the `<queries>` entry for the `upi` scheme in the manifest (Android 11+ package
 * visibility). That is a manifest declaration, not a runtime permission — nothing is prompted.
 */
object UpiApps {

    /** Apps that handle upi:// but are not primarily payment apps — too noisy to watch. */
    private val EXCLUDED = setOf(
        "com.whatsapp",              // chat first; foregrounded constantly
        "com.whatsapp.w4b"
    )

    data class UpiApp(val packageName: String, val label: String)

    /** All installed UPI-capable apps, excluding our own and known noisy ones. */
    fun installed(context: Context): List<UpiApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        return resolved
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != context.packageName && it !in EXCLUDED }
            .distinct()
            .map { UpiApp(it, labelFor(context, it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Just the package names — what the usage watcher matches foreground events against. */
    fun installedPackages(context: Context): Set<String> =
        installed(context).map { it.packageName }.toSet()

    fun labelFor(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
