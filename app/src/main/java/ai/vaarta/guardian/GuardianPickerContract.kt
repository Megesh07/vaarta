package ai.vaarta.guardian

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract

/**
 * Wraps the system contact picker (Task 9 hardening fix: picks directly against the
 * `CommonDataKinds.Phone` data table, NOT `Contacts.CONTENT_URI`) so the caller gets back an
 * already-resolved [Guardian] (display name + a phone number) instead of a raw content [Uri] it
 * would then have to query itself — keeping that ContentResolver plumbing in one place (spec §7).
 *
 * Picking against `Phone.CONTENT_URI` means the returned Uri points directly at a row in the Data
 * table for that one phone number — Android grants a temporary read permission for exactly that
 * Uri, so [resolveGuardian] can read `DISPLAY_NAME`/`NUMBER` straight off it with **no
 * `READ_CONTACTS` permission needed at all**, matching `docs/PRIVACY_SECURITY.md`'s explicit
 * "Never: READ_CONTACTS (system picker instead)" line (§7/§88). Verified against the current
 * Android "Common intents" guide (developer.android.com/guide/components/intents-common) and the
 * `ContactsContract.CommonDataKinds.Phone` reference, both re-checked at implementation time.
 *
 * `parseResult` has no Context parameter, so the [Context] passed to [createIntent] is captured on
 * the instance for the resolve step — the standard pattern for result-transforming
 * ActivityResultContracts that need to do more than parse the raw Intent.
 */
class GuardianPickerContract : ActivityResultContract<Unit, Guardian?>() {
    private var appContext: Context? = null

    override fun createIntent(context: Context, input: Unit): Intent {
        appContext = context.applicationContext
        return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Guardian? {
        if (resultCode != Activity.RESULT_OK) return null
        val phoneUri = intent?.data ?: return null
        val context = appContext ?: return null
        return resolveGuardian(context, phoneUri)
    }
}

/**
 * Reads the picked phone-data row's display name + number directly off [phoneUri] via the
 * [Context]'s ContentResolver. Needs no `READ_CONTACTS` — the picker grants a temporary read on
 * this one Uri (see [GuardianPickerContract] doc). Fails closed to null on any query error rather
 * than crashing or leaving a half-set guardian. Logs the exception's class name only — never
 * [Throwable.message], which could echo back contact PII (matches `LinkChecker`'s house style).
 */
fun resolveGuardian(context: Context, phoneUri: Uri): Guardian? {
    return try {
        context.contentResolver.query(
            phoneUri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val name = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            )
            val number = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER),
            )
            if (name.isNullOrBlank() || number.isNullOrBlank()) null else Guardian(name, number)
        }
    } catch (e: Exception) {
        Log.w("GuardianPickerContract", "contact resolve failed: ${e.javaClass.simpleName}")
        null
    }
}
