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

    /**
     * Known UPI packages, watched whenever they are installed even if discovery misses them.
     *
     * Discovery via `upi://pay` is what makes this work on any phone, but it is not something
     * to bet the whole feature on: if package-visibility filtering or a missing CATEGORY_DEFAULT
     * makes the query come back empty, nothing would ever be watched and the app would look
     * completely dead — which is exactly the symptom we hit. The union means discovery can only
     * ever add apps, never silently remove the common ones.
     */
    private val KNOWN_UPI_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",   // Google Pay
        "net.one97.paytm",                          // Paytm
        "com.phonepe.app",                          // PhonePe
        "com.phonepe.simulator",
        "in.org.npci.upiapp",                       // BHIM
        "in.amazon.mShop.android.shopping",         // Amazon Pay
        "com.dreamplug.androidapp",                 // CRED
        "com.mobikwik_new",
        "com.freecharge.android",
        "com.snapwork.hdfc",                        // HDFC
        "com.csam.icici.bank.imobile",              // ICICI iMobile
        "com.sbi.lotusintouch",                     // SBI YONO
        "com.axis.mobile",                          // Axis
        "com.msf.kbank.mobile",                     // Kotak
        "com.bankofbaroda.mconnect",
        "com.infrasofttech.CentralBankofIndia",
        "com.YESBANK",
        "com.idbibank.abhay"
    )

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    data class UpiApp(val packageName: String, val label: String)

    /** All installed UPI-capable apps, excluding our own and known noisy ones. */
    fun installed(context: Context): List<UpiApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        val discovered = resolved.mapNotNull { it.activityInfo?.packageName }
        val known = KNOWN_UPI_PACKAGES.filter { isInstalled(context, it) }

        return (discovered + known)
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
