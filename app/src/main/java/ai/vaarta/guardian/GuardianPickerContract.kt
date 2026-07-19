package ai.vaarta.guardian

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract

/**
 * Wraps the system contact picker (`ActivityResultContracts.PickContact` uses the same underlying
 * `ACTION_PICK` + `Contacts.CONTENT_URI` intent) so the caller gets back an already-resolved
 * [Guardian] (display name + a phone number) instead of a raw content [Uri] it would then have to
 * query itself — keeping that ContentResolver plumbing in one place (spec §7).
 *
 * `parseResult` has no Context parameter, so the [Context] passed to [createIntent] is captured on
 * the instance for the resolve step — the standard pattern for result-transforming
 * ActivityResultContracts that need to do more than parse the raw Intent.
 */
class GuardianPickerContract : ActivityResultContract<Unit, Guardian?>() {
    private var appContext: Context? = null

    override fun createIntent(context: Context, input: Unit): Intent {
        appContext = context.applicationContext
        return Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Guardian? {
        if (resultCode != Activity.RESULT_OK) return null
        val contactUri = intent?.data ?: return null
        val context = appContext ?: return null
        return resolveGuardian(context, contactUri)
    }
}

/**
 * Reads the picked contact's display name + a phone number via the [Context]'s ContentResolver.
 * Requires `READ_CONTACTS` to already be granted (requested immediately before the picker launches
 * — the picked-contact Uri alone is not enough to read the Phone data table). Fails closed to null
 * on any query error rather than crashing or leaving a half-set guardian.
 */
fun resolveGuardian(context: Context, contactUri: Uri): Guardian? {
    return try {
        val resolver = context.contentResolver
        val (contactId, name) = resolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
            id to displayName
        } ?: return null

        if (name.isNullOrBlank()) return null

        val number = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            } else {
                null
            }
        }

        if (number.isNullOrBlank()) null else Guardian(name, number)
    } catch (e: Exception) {
        Log.w("GuardianPickerContract", "contact resolve failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}
