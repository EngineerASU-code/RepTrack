package app.reptrack

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureExchangeTest {
    @Test
    fun ecdhLinkKeysMatchAndAreBoundToLinkAndDevices() {
        fun keyPair() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val a = keyPair()
        val b = keyPair()

        val fromA = ExchangeCrypto.deriveLinkKey(a.private, b.public.encoded, "link-1", "device-a", "device-b")
        val fromB = ExchangeCrypto.deriveLinkKey(b.private, a.public.encoded, "link-1", "device-b", "device-a")
        val otherLink = ExchangeCrypto.deriveLinkKey(a.private, b.public.encoded, "link-2", "device-a", "device-b")

        assertTrue(fromA.contentEquals(fromB))
        assertFalse(fromA.contentEquals(otherLink))
        assertEquals(20, ExchangeCrypto.fingerprint(fromA).replace(" ", "").length)
        assertNotEquals(ExchangeCrypto.fingerprint(fromA), ExchangeCrypto.fingerprint(otherLink))
        fromA.fill(0)
        fromB.fill(0)
        otherLink.fill(0)
    }
}
