package com.kairosera.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val pass = "correct horse battery".toCharArray()
    private val contents = BackupCrypto.Contents("{\"backupVersion\":1}", ByteArray(4096) { (it % 251).toByte() })
    private fun sealed() = BackupCrypto.seal(pass.copyOf(), contents, iterations = 100_000)

    @Test
    fun roundTripKeepsManifestAndDatabase() {
        val out = BackupCrypto.open(pass.copyOf(), sealed())
        assertEquals(contents.manifest, out.manifest)
        assertArrayEquals(contents.database, out.database)
    }

    @Test
    fun theFileRevealsNothingReadable() {
        val text = String(sealed(), Charsets.ISO_8859_1)
        assertFalse(text.contains("backupVersion"))
    }

    @Test
    fun twoBackupsOfTheSameDataDiffer() {
        assertFalse(sealed().contentEquals(sealed()))
    }

    @Test
    fun wrongPassphraseFails() {
        assertThrows(BackupCrypto.OpenError.CannotDecrypt::class.java) { BackupCrypto.open("wrong horse battery".toCharArray(), sealed()) }
    }

    @Test
    fun anyChangedByteFails() {
        val file = sealed()
        listOf(20, file.size / 2, file.size - 1).forEach { i ->
            val bad = file.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows(BackupCrypto.OpenError::class.java) { BackupCrypto.open(pass.copyOf(), bad) }
        }
    }

    @Test
    fun otherFilesAreNotBackups() {
        assertThrows(BackupCrypto.OpenError.NotABackup::class.java) { BackupCrypto.open(pass.copyOf(), "hello".toByteArray()) }
        assertThrows(BackupCrypto.OpenError.NotABackup::class.java) { BackupCrypto.open(pass.copyOf(), ByteArray(200)) }
    }

    @Test
    fun aNewerFormatIsRecognised() {
        val file = sealed().also { it[8] = 9 }
        assertThrows(BackupCrypto.OpenError.NewerFormat::class.java) { BackupCrypto.open(pass.copyOf(), file) }
    }

    @Test
    fun shortPassphrasesAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.seal("short".toCharArray(), contents) }
    }
}
