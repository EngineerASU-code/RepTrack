package app.reptrack

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

internal const val maxTelegramDeliveryBytes = 16 * 1024 * 1024

internal fun encodeTelegramDelivery(
    header: ExchangeHeader,
    session: TrainingSession,
    reportPdf: ByteArray,
    encryptedResult: ByteArray,
): ByteArray {
    require(header.type == "result" && session.status == SessionStatus.COMPLETED && session.endedAt != null) { "Некорректный отчёт для Telegram" }
    require(reportPdf.size in 1..(4 * 1024 * 1024) && reportPdf.copyOfRange(0, minOf(4, reportPdf.size)).toString(Charsets.US_ASCII) == "%PDF") { "PDF-отчёт повреждён" }
    require(encryptedResult.size in 1..maxExchangeBytes) { "Зашифрованный отчёт слишком большой" }
    val plain = JSONObject()
        .put("format", "reptrack-telegram-delivery")
        .put("version", 1)
        .put("linkId", header.linkId)
        .put("packageId", header.packageId)
        .put("senderDeviceId", header.senderDeviceId)
        .put("recipientDeviceId", header.recipientDeviceId)
        .put("sessionId", session.id)
        .put("planName", session.planName)
        .put("startedAt", session.startedAt)
        .put("endedAt", session.endedAt)
        .put("durationMinutes", ((session.endedAt - session.startedAt).coerceAtLeast(0) / 60_000L))
        .put("exerciseCount", session.exercises.size)
        .put("completedWorkSets", session.completedSets)
        .put("reportPdf", Base64.getEncoder().encodeToString(reportPdf))
        .put("resultPackage", Base64.getEncoder().encodeToString(encryptedResult))
        .toString().toByteArray(Charsets.UTF_8)
    return try {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(plain) }
        output.toByteArray().also { require(it.size in 1..maxTelegramDeliveryBytes) { "Файл для Telegram слишком большой" } }
    } finally { plain.fill(0) }
}

internal fun encodeTelegramRoute(link: TrustedLink, profileKind: ProfileKind, localDeviceId: String): ByteArray {
    require(profileKind == ProfileKind.PERSONAL || profileKind == ProfileKind.TRAINEE) { "Неизвестная роль профиля" }
    return JSONObject()
        .put("format", "reptrack-telegram-route")
        .put("version", 1)
        .put("linkId", link.id)
        .put("role", if (profileKind == ProfileKind.TRAINEE) "TRAINER" else "TRAINEE")
        .put("localDeviceId", localDeviceId)
        .put("remoteDeviceId", link.remoteDeviceId)
        .put("fingerprint", link.fingerprint)
        .toString().toByteArray(Charsets.UTF_8)
}
