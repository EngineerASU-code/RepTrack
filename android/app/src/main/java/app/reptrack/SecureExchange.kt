package app.reptrack

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal const val maxExchangeBytes = 8 * 1024 * 1024
internal const val maxExchangePayloadBytes = 5 * 1024 * 1024
internal const val exchangeTtlMs = 30L * 24 * 60 * 60 * 1000
private const val wrapAlias = "reptrack.exchange.wrap.v1"
private const val tagBits = 128
private const val nonceBytes = 12

internal data class PairingFile(
    val kind: String,
    val inviteId: String,
    val linkId: String,
    val deviceId: String,
    val publicKey: String,
    val createdAt: Long,
    val expiresAt: Long,
)

internal data class TrustedLink(
    val id: String,
    val localProfileId: String,
    val remoteDeviceId: String,
    val wrappedSecret: ByteArray,
    val fingerprint: String,
    val createdAt: Long,
)

data class TrustedLinkSummary(
    val id: String,
    val localProfileId: String,
    val remoteDeviceId: String,
    val fingerprint: String,
    val createdAt: Long,
)

data class PairingPending(
    val inviteId: String,
    val linkId: String,
    val localProfileId: String,
    val role: String,
    val remoteDeviceId: String?,
    val fingerprint: String?,
    val expiresAt: Long,
)

internal fun TrustedLink.summary() = TrustedLinkSummary(id, localProfileId, remoteDeviceId, fingerprint, createdAt)

internal data class PairingResponse(val file: ByteArray, val fingerprint: String)

internal data class ExchangeHeader(
    val packageId: String,
    val linkId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val type: String,
    val createdAt: Long,
    val expiresAt: Long,
    val version: Int = 1,
    val programVersion: Int? = null,
)

internal data class DecryptedExchange(val header: ExchangeHeader, val payload: ByteArray)

internal data class ExchangePreview(
    val packageId: String,
    val type: String,
    val title: String,
    val summary: String,
    val recordCount: Int,
    val plan: TrainingPlan? = null,
    val currentPlanName: String? = null,
    val hasLocalEdits: Boolean = false,
    val alreadyImported: Boolean = false,
    val changes: List<String> = emptyList(),
)

internal data class ExchangeImportResult(val importedSessions: Int = 0, val planName: String? = null, val deferred: Boolean = false)

internal data class IncomingProgramState(
    val localPlanId: String?,
    val appliedVersion: Int,
    val appliedLocalVersion: Int,
    val pendingVersion: Int?,
    val pendingAccepted: Boolean,
    val pendingPackageId: String?,
)

internal object ExchangeCrypto {
    fun newEphemeralKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    fun deleteEphemeralKey(alias: String) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    fun wrapPrivateKey(privateKey: java.security.PrivateKey, linkId: String): ByteArray {
        val encoded = privateKey.encoded
        return try { wrapSecret(encoded, "pairing:$linkId") } finally { encoded.fill(0) }
    }

    fun unwrapPrivateKey(wrapped: ByteArray, linkId: String): java.security.PrivateKey {
        val encoded = unwrapSecret(wrapped, "pairing:$linkId")
        return try { java.security.KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encoded)) }
        finally { encoded.fill(0) }
    }

    fun deriveLinkKey(privateKey: java.security.PrivateKey, remotePublicKey: ByteArray, linkId: String, deviceA: String, deviceB: String): ByteArray {
        val peer = java.security.KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(remotePublicKey))
        val agreement = KeyAgreement.getInstance("ECDH").apply { init(privateKey); doPhase(peer, true) }
        val shared = agreement.generateSecret()
        return try {
            val peers = listOf(deviceA, deviceB).sorted().joinToString("|")
            hkdf(shared, sha256("reptrack-link-v1|$linkId|$peers".toByteArray()), "RepTrack exchange link v1".toByteArray(), 32)
        } finally { shared.fill(0) }
    }

    fun fingerprint(secret: ByteArray): String = sha256(secret).take(10).joinToString("") { (it.toInt() and 0xff).toString(16).uppercase().padStart(2, '0') }
        .chunked(4).joinToString(" ")

    fun wrapSecret(secret: ByteArray, linkId: String): ByteArray {
        val key = wrappingKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(linkId.toByteArray())
        return cipher.iv + cipher.doFinal(secret)
    }

    fun unwrapSecret(wrapped: ByteArray, linkId: String): ByteArray {
        require(wrapped.size >= nonceBytes + 16) { "Повреждён ключ связи" }
        val nonce = wrapped.copyOfRange(0, nonceBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(tagBits, nonce))
        cipher.updateAAD(linkId.toByteArray())
        return cipher.doFinal(wrapped, nonceBytes, wrapped.size - nonceBytes)
    }

    fun encodePairing(pairing: PairingFile): ByteArray = JSONObject()
        .put("format", "reptrack-invite").put("version", 1).put("kind", pairing.kind)
        .put("inviteId", pairing.inviteId).put("linkId", pairing.linkId).put("deviceId", pairing.deviceId)
        .put("publicKey", pairing.publicKey).put("createdAt", pairing.createdAt).put("expiresAt", pairing.expiresAt)
        .toString().toByteArray(Charsets.UTF_8)

    fun decodePairing(bytes: ByteArray, now: Long = System.currentTimeMillis()): PairingFile {
        require(bytes.size in 1..64_000) { "Файл приглашения слишком большой" }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.optString("format") == "reptrack-invite" && json.optInt("version") == 1) { "Неизвестный формат приглашения" }
        val kind = json.getString("kind")
        require(kind == "request" || kind == "response") { "Неизвестный тип приглашения" }
        val pairing = PairingFile(kind, json.getString("inviteId"), json.getString("linkId"), json.getString("deviceId"), json.getString("publicKey"), json.getLong("createdAt"), json.getLong("expiresAt"))
        require(pairing.inviteId.length <= 128 && pairing.linkId.length <= 128 && pairing.deviceId.length <= 128) { "Некорректный идентификатор приглашения" }
        require(pairing.publicKey.length <= 2048 && runCatching { Base64.getDecoder().decode(pairing.publicKey).size in 64..256 }.getOrDefault(false)) { "Некорректный открытый ключ" }
        require(pairing.createdAt in 0..(now + 5 * 60_000) && pairing.expiresAt > now && pairing.expiresAt > pairing.createdAt && pairing.expiresAt - pairing.createdAt in 1..exchangeTtlMs) { "Приглашение истекло или имеет некорректный срок" }
        return pairing
    }

    fun encrypt(header: ExchangeHeader, secret: ByteArray, payload: ByteArray): ByteArray {
        require(payload.size <= maxExchangePayloadBytes) { "Пакет слишком большой" }
        require(header.version == 1 && header.type in setOf("result", "program")) { "Некорректный пакет" }
        require(if (header.type == "program") header.programVersion != null && header.programVersion > 0 else header.programVersion == null) { "Некорректная версия программы в пакете" }
        val aad = headerJson(header).toString().toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(nonceBytes).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(tagBits, nonce))
        cipher.updateAAD(aad)
        val root = JSONObject().put("format", "reptrack-exchange").put("version", 1)
            .put("aad", Base64.getEncoder().encodeToString(aad))
            .put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(cipher.doFinal(payload)))
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxExchangeBytes) { "Пакет слишком большой" }
        return bytes
    }

    fun decrypt(bytes: ByteArray, secret: ByteArray, now: Long = System.currentTimeMillis()): DecryptedExchange {
        val header = peekHeader(bytes, now)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val aad = Base64.getDecoder().decode(root.getString("aad"))
        val nonce = Base64.getDecoder().decode(root.getString("nonce"))
        val ciphertext = Base64.getDecoder().decode(root.getString("ciphertext"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(tagBits, nonce))
        cipher.updateAAD(aad)
        return DecryptedExchange(header, cipher.doFinal(ciphertext))
    }

    fun peekHeader(bytes: ByteArray, now: Long = System.currentTimeMillis()): ExchangeHeader {
        require(bytes.size in 1..maxExchangeBytes) { "Пакет слишком большой или пустой" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "reptrack-exchange" && root.optInt("version") == 1) { "Неизвестный формат пакета" }
        val aad = Base64.getDecoder().decode(root.getString("aad"))
        require(aad.size <= 4096) { "Некорректные метаданные пакета" }
        val nonce = Base64.getDecoder().decode(root.getString("nonce"))
        val ciphertext = Base64.getDecoder().decode(root.getString("ciphertext"))
        require(nonce.size == nonceBytes && ciphertext.size in 16..(maxExchangePayloadBytes + 16)) { "Повреждён зашифрованный пакет" }
        val headerJson = JSONObject(aad.toString(Charsets.UTF_8))
        val header = ExchangeHeader(
            headerJson.getString("packageId"), headerJson.getString("linkId"), headerJson.getString("senderDeviceId"),
            headerJson.getString("recipientDeviceId"), headerJson.getString("type"), headerJson.getLong("createdAt"),
            headerJson.getLong("expiresAt"), headerJson.getInt("version"), if (headerJson.isNull("programVersion")) null else headerJson.getInt("programVersion"),
        )
        require(header.version == 1 && header.type in setOf("result", "program")) { "Неподдерживаемый тип пакета" }
        require(if (header.type == "program") header.programVersion != null && header.programVersion > 0 else header.programVersion == null) { "Некорректная версия программы в пакете" }
        require(header.packageId.length <= 128 && header.linkId.length <= 128 && header.senderDeviceId.length <= 128 && header.recipientDeviceId.length <= 128) { "Некорректные метаданные пакета" }
        require(header.createdAt in 0..(now + 5 * 60_000) && header.expiresAt > now && header.expiresAt > header.createdAt && header.expiresAt - header.createdAt in 1..exchangeTtlMs) { "Пакет истёк или имеет некорректный срок" }
        return header
    }

    private fun headerJson(header: ExchangeHeader) = JSONObject()
        .put("packageId", header.packageId).put("linkId", header.linkId)
        .put("senderDeviceId", header.senderDeviceId).put("recipientDeviceId", header.recipientDeviceId)
        .put("type", header.type).put("createdAt", header.createdAt).put("expiresAt", header.expiresAt).put("version", header.version)
        .put("programVersion", header.programVersion ?: JSONObject.NULL)

    @Synchronized
    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(wrapAlias)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(wrapAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        return store.getKey(wrapAlias, null) as SecretKey
    }

    private fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(input)
        val output = ByteArray(size)
        var previous = byteArrayOf()
        var offset = 0
        var counter = 1
        while (offset < size) {
            val block = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(extract, "HmacSHA256")) }
                .doFinal(previous + info + counter.toByte())
            previous.fill(0)
            previous = block
            val count = minOf(previous.size, size - offset)
            previous.copyInto(output, offset, 0, count)
            offset += count
            counter++
        }
        extract.fill(0)
        previous.fill(0)
        return output
    }

    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
}
