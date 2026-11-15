package app.portero.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Captures the microphone at 8 kHz mono, converts to G.711 µ-law and hands the
 * bytes to an OkHttp request body that streams them to /api/talk.
 */
class MicStreamer {

    private var recorder: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    private val frames = LinkedBlockingQueue<ByteArray>(64)

    fun start(): Boolean {
        if (running) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val size = maxOf(minBuffer, SAMPLE_RATE)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                size * 2,
            )
        } catch (e: SecurityException) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
        }

        recorder = record
        running = true
        record.startRecording()
        worker = thread(name = "mic-streamer") {
            val buffer = ByteArray(320) // 20 ms at 8 kHz, 16 bit
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val ulaw = Ulaw.encode(buffer, read)
                    if (frames.remainingCapacity() == 0) frames.poll()
                    frames.offer(ulaw)
                }
            }
        }
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        frames.offer(ByteArray(0))
        worker?.join(500)
        worker = null
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        aec?.release()
        aec = null
        frames.clear()
    }

    fun body(): RequestBody = object : RequestBody() {
        override fun contentType() = "audio/basic".toMediaType()

        override fun writeTo(sink: BufferedSink) {
            while (true) {
                val frame = frames.take()
                if (frame.isEmpty()) break
                sink.write(frame)
                sink.flush()
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 8000
    }
}
