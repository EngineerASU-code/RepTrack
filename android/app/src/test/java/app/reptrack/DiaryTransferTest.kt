package app.reptrack

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DiaryTransferTest {
    @Test
    fun encryptedBackupRoundTripsAndUsesFreshSalt() {
        val plain = "offline workout archive".toByteArray()
        val first = encryptBackup(plain, "long-passphrase".toCharArray())
        val second = encryptBackup(plain, "long-passphrase".toCharArray())

        assertArrayEquals(plain, decryptBackup(first, "long-passphrase".toCharArray()))
        assertArrayEquals(plain, decryptBackup(second, "long-passphrase".toCharArray()))
        assertEquals(false, first.contentEquals(second))
    }

    @Test
    fun wrongPasswordAndTamperingAreRejected() {
        val container = encryptBackup("backup".toByteArray(), "long-passphrase".toCharArray())
        assertThrows(IllegalArgumentException::class.java) { decryptBackup(container, "wrong-password".toCharArray()) }

        val corrupted = container.copyOf().apply { this[this.lastIndex] = (this.last().toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { decryptBackup(corrupted, "long-passphrase".toCharArray()) }
    }

    @Test
    fun unsupportedAndTruncatedContainersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { decryptBackup(byteArrayOf(1, 2, 3), "passphrase".toCharArray()) }
        val container = encryptBackup(byteArrayOf(1), "long-passphrase".toCharArray())
        container[4] = 2
        assertThrows(IllegalArgumentException::class.java) { decryptBackup(container, "long-passphrase".toCharArray()) }
    }
}
