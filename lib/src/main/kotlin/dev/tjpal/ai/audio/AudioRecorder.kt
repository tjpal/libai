package dev.tjpal.ai.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

class AudioRecorder {
    private val format: AudioFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        16000f,
        16,
        1,
        2,
        16000f,
        false
    )

    private var line: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private var outStream: ByteArrayOutputStream? = null

    @Throws(LineUnavailableException::class)
    fun start() {
        if (line != null && line!!.isOpen) return

        val info = DataLine.Info(TargetDataLine::class.java, format)
        line = AudioSystem.getLine(info) as TargetDataLine
        line!!.open(format)
        line!!.start()

        outStream = ByteArrayOutputStream()
        recordingThread = Thread {
            val buffer = ByteArray(4096)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val count = line!!.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        outStream!!.write(buffer, 0, count)
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { start() }
    }

    fun stop() {
        recordingThread?.interrupt()
        line?.stop()
        line?.close()
        recordingThread = null
        line = null
    }

    @Throws(Exception::class)
    fun save(file: File) {
        val audioBytes = outStream?.toByteArray()
            ?: throw IllegalStateException("No audio recorded. Call start() first.")

        val bais = ByteArrayInputStream(audioBytes)
        val audioStream = AudioInputStream(bais, format, (audioBytes.size / format.frameSize).toLong())

        AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, file)

        audioStream.close()
        bais.close()
    }
}
