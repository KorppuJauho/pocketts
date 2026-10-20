package io.github.korppujauho.mobilets6

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals short secrets (the identity, passwords) before they're saved, so a copy of the app's files is no use without
 * this phone. A sealed value is "enc1:" + base64 of the cipher's IV and output; [label] says what the value is (for
 * example "settings/identity"), so one secret can't be passed off as another.
 */
class SecretBox(private val key: Key) {
    /** AES-256-GCM: the IV (12 bytes) and the ciphertext with its tag, with [aad] as associated data. */
    interface Key {
        fun encrypt(plain: ByteArray, aad: ByteArray): ByteArray
        fun decrypt(sealed: ByteArray, aad: ByteArray): ByteArray
    }

    /** A saved secret that can't be opened (for example the key is gone). */
    class Unreadable(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Empty stays empty: there's nothing to hide. */
    fun seal(plain: String, label: String): String =
        if (plain.isEmpty()) "" else PREFIX + Base64.getEncoder().encodeToString(key.encrypt(plain.toByteArray(), label.toByteArray()))

    /** The secret in [stored]: opened when sealed, as is when it was saved before sealing existed. */
    fun open(stored: String, label: String): String {
        if (!isSealed(stored)) return stored
        return try {
            String(key.decrypt(Base64.getDecoder().decode(stored.removePrefix(PREFIX)), label.toByteArray()))
        } catch (e: Exception) {
            throw Unreadable("$label can't be decrypted: ${e.javaClass.simpleName}", e)
        }
    }

    companion object {
        const val PREFIX = "enc1:"

        fun isSealed(stored: String) = stored.startsWith(PREFIX)
    }
}

/** The app's [SecretBox], with its key in the Android Keystore (the key never leaves it). Tests put in another. */
object Secrets {
    @Volatile
    var box: SecretBox = SecretBox(KeystoreKey)

    private object KeystoreKey : SecretBox.Key {
        private const val ALIAS = "mobilets6-secrets"
        @Volatile private var key: SecretKey? = null

        @Synchronized
        private fun key(): SecretKey = key ?: run {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }).also { key = it }
        }

        override fun encrypt(plain: ByteArray, aad: ByteArray): ByteArray {
            // The Keystore picks a fresh IV each time
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad) }
            return c.iv + c.doFinal(plain)
        }

        override fun decrypt(sealed: ByteArray, aad: ByteArray): ByteArray {
            require(sealed.size > 12) { "Too short" }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, 12))
                updateAAD(aad)
            }
            return c.doFinal(sealed, 12, sealed.size - 12)
        }
    }
}

/**
 * The secret saved under [name] (null when none). One saved before sealing existed is sealed now, so it's in plain
 * text only until the first time it's read. Throws [SecretBox.Unreadable] when it can't be opened; the saved value is
 * then left as it is.
 */
fun SharedPreferences.getSecret(name: String, label: String): String? {
    val stored = getString(name, null) ?: return null
    val box = Secrets.box
    val plain = box.open(stored, label)
    if (!SecretBox.isSealed(stored) && stored.isNotEmpty()) edit().putSecret(name, label, plain).apply()
    return plain
}

/**
 * Saves [plain] sealed under [name]. Should the Keystore fail, it's saved as it is rather than lost, and sealed the
 * next time it's read.
 */
fun SharedPreferences.Editor.putSecret(name: String, label: String, plain: String): SharedPreferences.Editor =
    putString(name, sealOrPlain(plain, label))

/** [plain] sealed, or as it is when the Keystore fails (logged). */
fun sealOrPlain(plain: String, label: String): String = try {
    Secrets.box.seal(plain, label)
} catch (e: Exception) {
    AppLog.log("Couldn't encrypt $label, saving it unencrypted for now: ${e.javaClass.simpleName}")
    plain
}
