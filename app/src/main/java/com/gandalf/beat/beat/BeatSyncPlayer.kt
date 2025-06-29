package com.gandalf.beat.beat

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.Log
import android.view.Surface
import kotlin.concurrent.thread
import kotlin.math.abs

class BeatSyncPlayer(
    private val context: Context,
    private val videoUri: Uri
) {
    private var isPlaying = false
    private var decodeThread: Thread? = null
    private var bpm: Float = 120f
    private val minSpeed = 0.5f
    private val maxSpeed = 2.0f

    fun setBpm(newBpm: Float) {
        bpm = newBpm
    }

    fun start(surface: Surface) {
        isPlaying = true
        decodeThread = thread {
            while (isPlaying) {
                val speed = computeSpeedFactor()
                playOnce(surface, speed)
            }
        }
    }

    fun stop() {
        isPlaying = false
        decodeThread?.interrupt()
    }

    private fun computeSpeedFactor(): Float {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoUri, null)
        val index = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: return 1.0f
        val duration = extractor.getTrackFormat(index).getLong(MediaFormat.KEY_DURATION) / 1000f
        extractor.release()

        val beatMs = 60000f / bpm
        val candidates = listOf(0.5f, 1f, 2f, 4f).mapNotNull {
            val target = beatMs * it
            val speed = duration / target
            if (speed in minSpeed..maxSpeed) Triple(it, speed, abs(1f - speed)) else null
        }

        val bestSpeed = candidates.minByOrNull { it.third }?.second ?: 1f
        Log.d("BeatSync", "🎚 speed: $bestSpeed bpm: $bpm")
        return bestSpeed
    }

    private fun playOnce(surface: Surface, speed: Float) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoUri, null)
        val index = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: return

        extractor.selectTrack(index)
        val format = extractor.getTrackFormat(index)
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, surface, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val startTime = System.nanoTime()
        var isEOS = false

        while (!Thread.interrupted()) {
            if (!isEOS) {
                val inIndex = decoder.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(inputBuffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex >= 0) {
                val adjPtsNs = (bufferInfo.presentationTimeUs * 1000 / speed).toLong()
                val delayMs = (adjPtsNs - (System.nanoTime() - startTime)) / 1_000_000
                if (delayMs > 0) Thread.sleep(delayMs)
                decoder.releaseOutputBuffer(outIndex, true)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()
    }
}
