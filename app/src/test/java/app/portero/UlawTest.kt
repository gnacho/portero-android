package app.portero

import app.portero.data.CallEvent
import app.portero.media.Ulaw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UlawTest {

    @Test
    fun `silence encodes to 0xFF`() {
        val pcm = byteArrayOf(0, 0, 0, 0)
        val out = Ulaw.encode(pcm, pcm.size)
        assertEquals(2, out.size)
        assertEquals(0xFF.toByte(), out[0])
        assertEquals(0xFF.toByte(), out[1])
    }

    @Test
    fun `positive and negative peaks differ in sign bit`() {
        val positive = Ulaw.encodeOne(30000)
        val negative = Ulaw.encodeOne((-30000).toShort())
        assertTrue(positive != negative)
    }

    @Test
    fun `encode halves the number of bytes`() {
        val pcm = ByteArray(320) { 1 }
        assertEquals(160, Ulaw.encode(pcm, pcm.size).size)
    }
}

class CallEventTest {

    @Test
    fun `idle is not active`() {
        assertFalse(CallEvent(call = "idle").active)
        assertFalse(CallEvent(call = "unknown").active)
    }

    @Test
    fun `ringing is active and flagged as ringing`() {
        val event = CallEvent(call = "ringing")
        assertTrue(event.active)
        assertTrue(event.ringing)
    }

    @Test
    fun `onCall is active but not ringing`() {
        val event = CallEvent(call = "onCall")
        assertTrue(event.active)
        assertFalse(event.ringing)
    }
}
