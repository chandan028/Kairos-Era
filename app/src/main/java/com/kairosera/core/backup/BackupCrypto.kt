package com.kairosera.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The `.kairos` file format. Everything after the fixed header is AES-256-GCM ciphertext, so the
 * file reveals nothing about its contents, and any change to any byte (header included, as it is
 * the associated data) makes opening fail.
 *
 * ```
 * magic "KAIROSBK" (8) | format 1 (1) | kdf 1 = PBKDF2-HMAC-SHA256 (1) | iterations (4, big-endian)
 * | salt (16) | nonce (12) | ciphertext + 16-byte tag
 * plaintext = gzip( manifestLength (4) | manifest JSON (UTF-8) | database bytes )
 * ```
 *
 * The key is derived from the passphrase each time and never stored anywhere.
 */
object BackupCrypto {
    private val MAGIC = "KAIROSBK".toByteArray(Charsets.US_ASCII)
    private const val FORMAT: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    private const val SALT = 16
    private const val NONCE = 12
    private const val TAG_BITS = 128
    private const val HEADER = 8 + 1 + 1 + 4 + SALT + NONCE

    /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256. Stored per file, so it can be raised later. */
    const val ITERATIONS = 600_000
    private val ITERATION_RANGE = 100_000..10_000_000
    const val MIN_PASSPHRASE = 8

    class Contents(val manifest: String, val database: ByteArray)

    sealed class OpenError(message: String) : Exception(message) {
        /** Not a Kairos Era backup at all. */
        class NotABackup : OpenError("not_a_backup")
        /** A newer file format than this app understands. */
        class NewerFormat : OpenError("newer_format")
        /** Wrong passphrase, or the file was changed or damaged. GCM cannot tell these apart. */
        class CannotDecrypt : OpenError("cannot_decrypt")
    }

    fun seal(passphrase: CharArray, contents: Contents, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE) { "passphrase_too_short" }
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(FORMAT).put(KDF_PBKDF2_SHA256).putInt(iterations).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(pack(contents))
    }

    fun open(passphrase: CharArray, file: ByteArray): Contents {
        if (file.size < HEADER + TAG_BITS / 8 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw OpenError.NotABackup()
        val buf = ByteBuffer.wrap(file, MAGIC.size, HEADER - MAGIC.size)
        val format = buf.get()
        val kdf = buf.get()
        if (format != FORMAT || kdf != KDF_PBKDF2_SHA256) throw OpenError.NewerFormat()
        val iterations = buf.getInt()
        if (iterations !in ITERATION_RANGE) throw OpenError.NotABackup()
        val salt = ByteArray(SALT).also { buf.get(it) }
        val nonce = ByteArray(NONCE).also { buf.get(it) }
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(file, 0, HEADER)
            cipher.doFinal(file, HEADER, file.size - HEADER)
        } catch (_: GeneralSecurityException) {
            throw OpenError.CannotDecrypt()
        }
        return try {
            unpack(plain)
        } catch (_: Exception) {
            throw OpenError.NotABackup()
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun pack(c: Contents): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(out)).use { d ->
            val m = c.manifest.toByteArray(Charsets.UTF_8)
            d.writeInt(m.size)
            d.write(m)
            d.write(c.database)
        }
        return out.toByteArray()
    }

    private fun unpack(bytes: ByteArray): Contents {
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { d ->
            val size = d.readInt()
            require(size in 2..1_000_000)
            val m = ByteArray(size).also { d.readFully(it) }
            return Contents(String(m, Charsets.UTF_8), d.readBytes())
        }
    }
}
