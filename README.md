# SplitMate

A personal, Splitwise-style Android app that pops up the moment you make a **UPI payment**
(Google Pay / Paytm) and lets you log it as **personal** or **split it** with friends —
writing everything to your own Google Sheet and preparing WhatsApp messages to the people
who owe you.

---

## How it works

1. A **NotificationListenerService** reads the payment notification from GPay / Paytm and
   parses **amount, vendor, date, time**.
2. It launches a **pop-up over the UPI app** (needs "Display over other apps").
3. You choose:
   - **Personal** → add a *reason* + pick a *category* → appended to the **Personal** tab.
   - **Split** → enter how many people, pick contacts (+ type names for people you don't
     have contacts of), pick category + reason → appended to the **Shared** tab, and the app
     prepares a **pre-filled WhatsApp message per person** (you tap send), plus a
     **self-reminder** listing the people you must chase manually.

> WhatsApp has no official API to auto-send to personal contacts, so this uses the safe
> "tap-to-send" approach: the chat opens with the message already typed — you just hit send.

---

## Part A — Set up the Google Sheet backend

1. Open your Sheet:
   `https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit`
2. **Extensions ▸ Apps Script**.
3. Delete any boilerplate, paste the contents of [`apps-script/Code.gs`](apps-script/Code.gs), **Save**.
4. **Deploy ▸ New deployment** → gear icon → **Web app**.
   - *Execute as:* **Me**
   - *Who has access:* **Anyone**
   - Click **Deploy**, authorise when prompted.
5. Copy the **Web app URL** (ends in `/exec`). You'll paste this into the app.

The `Personal` and `Shared` tabs (with headers) are created automatically on the first save.

**Personal columns:** Date · Time · Vendor · Amount · Reason · Category · Source · Logged At
**Shared columns:** Date · Time · Vendor · Total Amount · Category · Reason · Split Count ·
Per-Person Share · Your Share · Contacts Messaged · To Chase Manually · Source · Logged At

---

## Part B — Build & install the app

1. Open the `SplitMate/` folder in **Android Studio** (Giraffe or newer).
2. Let it sync Gradle (it will download the Gradle wrapper automatically).
3. Plug in your Android phone (USB debugging on) → **Run ▶**.
   - Or build an APK: **Build ▸ Build Bundle(s)/APK(s) ▸ Build APK(s)** and install it.

> Requires Android 8.0+ (minSdk 26). **iPhone is not supported** — iOS blocks
> notification-reading and overlays.

---

## Part C — Configure & grant permissions (in the app)

Open **SplitMate**, then:

1. **Settings** → paste your **Apps Script URL**, your **name**, your **WhatsApp number**
   (with country code, e.g. `9198XXXXXXXX`), and country code (default `91`). Tap **Save**.
2. **Setup** → grant all three:
   - **Notification access** → toggle SplitMate on.
   - **Display over other apps** → allow.
   - **Contacts** → allow.
3. Tap **Test the pop-up (fake payment)** to rehearse the whole flow without paying anyone.
4. Make a real ₹1 payment on GPay/Paytm to confirm the live trigger fires.

---

## Tuning the payment parser

UPI apps reword their notifications over time. If a real payment doesn't trigger the pop-up:

- The detection logic lives in
  [`PaymentParser.kt`](app/src/main/java/com/prayash/splitmate/service/PaymentParser.kt).
- Every detected payment logs to Logcat with tag `SplitMateListener`; every ignored one is
  silent. Filter Logcat by that tag to see what text arrived, then adjust the regexes.

## Known limitations

- **Tap-to-send:** one tap per person on WhatsApp (ban-safe; no auto-send).
- **Vendor names** from UPI notifications are sometimes generic (e.g. a UPI handle). You can
  edit the reason field to clarify.
- Parser currently targets **Google Pay** and **Paytm** package names; add more in
  `PaymentParser.SUPPORTED_PACKAGES`.
