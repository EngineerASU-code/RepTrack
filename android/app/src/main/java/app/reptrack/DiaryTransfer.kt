package app.reptrack

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val backupMagic = byteArrayOf('R'.code.toByte(), 'P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), 1)
private const val saltSize = 16
private const val nonceSize = 12
private const val gcmTagBits = 128
private const val pbkdfIterations = 210_000
internal const val maxBackupBytes = 25 * 1024 * 1024

/** Password-based AES-GCM container. The password is never serialized into the backup. */
internal fun encryptBackup(plainText: ByteArray, password: CharArray): ByteArray {
    require(password.size >= 8) { "Пароль должен содержать не менее 8 символов" }
    require(plainText.size <= maxBackupBytes) { "Резервная копия слишком большая" }
    val salt = ByteArray(saltSize).also(SecureRandom()::nextBytes)
    val nonce = ByteArray(nonceSize).also(SecureRandom()::nextBytes)
    val header = backupMagic + salt + nonce
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(gcmTagBits, nonce))
    cipher.updateAAD(header)
    return header + cipher.doFinal(plainText)
}

internal fun decryptBackup(container: ByteArray, password: CharArray): ByteArray {
    require(container.size <= maxBackupBytes + saltSize + nonceSize + backupMagic.size + 32) { "Резервная копия слишком большая" }
    require(container.size > backupMagic.size + saltSize + nonceSize + 16) { "Файл резервной копии повреждён" }
    require(container.copyOfRange(0, backupMagic.size).contentEquals(backupMagic)) { "Неизвестный формат резервной копии" }
    require(password.isNotEmpty()) { "Введите пароль резервной копии" }
    val headerSize = backupMagic.size + saltSize + nonceSize
    val saltStart = backupMagic.size
    val nonceStart = saltStart + saltSize
    val salt = container.copyOfRange(saltStart, nonceStart)
    val nonce = container.copyOfRange(nonceStart, headerSize)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(gcmTagBits, nonce))
    cipher.updateAAD(container.copyOfRange(0, headerSize))
    return try {
        cipher.doFinal(container, headerSize, container.size - headerSize)
    } catch (error: javax.crypto.AEADBadTagException) {
        throw IllegalArgumentException("Неверный пароль или файл изменён", error)
    }
}

private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password, salt, pbkdfIterations, 256)
    return try {
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    } finally {
        spec.clearPassword()
    }
}
