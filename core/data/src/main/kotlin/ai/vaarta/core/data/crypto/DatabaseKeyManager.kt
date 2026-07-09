package ai.vaarta.core.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the SQLCipher database passphrase (Phase 4B, ADR-0004).
 *
 * Security scheme — the passphrase is never hardcoded and never leaves the device:
 * 1. On first launch we generate a cryptographically random 32-byte passphrase.
 * 2. That passphrase is encrypted (AES/GCM) with a hardware-backed key that lives inside the Android
 *    Keystore and can never be exported — only the OS can use it to encrypt/decrypt on this device.
 * 3. The resulting IV + ciphertext are stored in ordinary [android.content.SharedPreferences]. On a
 *    stolen/imaged device the stored blob is useless without the Keystore key, which is bound to this
 *    hardware (and, on most 2026 devices, the secure element / StrongBox).
 *
 * We deliberately avoid the deprecated `androidx.security:security-crypto` (EncryptedSharedPreferences)
 * and wrap the passphrase directly with the Keystore — fewer moving parts, no deprecated dependency,
 * and the exact same trust anchor (a non-exportable Keystore key).
 *
 * The passphrase is returned as a [ByteArray] for SQLCipher. Callers should zero it after the database
 * is opened; SQLCipher copies it internally.
 */
class DatabaseKeyManager(private val context: Context) {

    fun getOrCreatePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedIv = prefs.getString(KEY_IV, null)
        val storedCt = prefs.getString(KEY_CIPHERTEXT, null)

        if (storedIv != null && storedCt != null) {
            return decrypt(
                iv = Base64.decode(storedIv, Base64.NO_WRAP),
                ciphertext = Base64.decode(storedCt, Base64.NO_WRAP),
            )
        }

        // First launch (or reset): mint a fresh passphrase and seal it under the Keystore key.
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val (iv, ciphertext) = encrypt(passphrase)
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    /**
     * Wipes the wrapped passphrase and the Keystore key. After this the on-disk database becomes
     * permanently undecryptable (crypto-shredding) — used by "delete all history" so a deleted DB file
     * can never be recovered even if the raw file lingered. A subsequent [getOrCreatePassphrase] mints
     * a new key for a fresh database.
     */
    fun destroyKey() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEYSTORE_ALIAS)
        }
    }

    private fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv to ciphertext
    }

    private fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /** Loads the non-exportable AES key from the Keystore, generating it once on first use. */
    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "vaarta_db_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PREFS_NAME = "vaarta_db_key"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ct"
        const val PASSPHRASE_BYTES = 32
    }
}
