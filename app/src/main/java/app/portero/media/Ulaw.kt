package app.portero.media

/** G.711 µ-law encoding, as expected by the doorbell two-way audio channel. */
object Ulaw {

    private val table = ShortArray(65536) { raw ->
        encodeOne(raw.toShort()).toShort()
    }

    fun encode(pcm: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length / 2)
        var i = 0
        var o = 0
        while (i + 1 < length) {
            val sample = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort()
            out[o++] = table[sample.toInt() and 0xFFFF].toByte()
            i += 2
        }
        return out
    }

    fun encodeOne(sampleIn: Short): Byte {
        var sample = sampleIn.toInt()
        val sign = if (sample < 0) 0x80 else 0x00
        if (sample < 0) sample = -sample
        if (sample > 32635) sample = 32635
        sample += 0x84
        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && (sample and mask) == 0) {
            exponent--
            mask = mask shr 1
        }
        val mantissa = (sample shr (exponent + 3)) and 0x0F
        return ((sign or (exponent shl 4) or mantissa) xor 0xFF).toByte()
    }
}
