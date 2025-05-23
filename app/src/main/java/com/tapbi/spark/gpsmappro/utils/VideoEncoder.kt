import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.core.app.ActivityCompat
import com.tapbi.spark.gpsmappro.App
import java.io.File
import kotlin.concurrent.thread

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    outputFile: File,
    private val videoFrameRate: Int = 30
) {
    private val VIDEO_MIME_TYPE = "video/avc"
    private val AUDIO_MIME_TYPE = "audio/mp4a-latm"

    private val VIDEO_BIT_RATE = 5_000_000
    private val VIDEO_IFRAME_INTERVAL = 1

    private val AUDIO_SAMPLE_RATE = 44100
    private val AUDIO_CHANNEL_COUNT = 1
    private val AUDIO_BIT_RATE = 128_000

    private val videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
    private val audioCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
    private val mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    private val videoBufferInfo = MediaCodec.BufferInfo()
    private val audioBufferInfo = MediaCodec.BufferInfo()

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private var isRecording = true

    val inputSurface: Surface
    private lateinit var audioThread: Thread

    // Đồng bộ thời gian dựa trên frame video đầu tiên
    private var baseTimeUs: Long = -1L
    private var lastAudioPtsUs: Long = 0L
    private val mediaMuxerPath = outputFile.absolutePath

    init {
        // Cấu hình video encoder
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_IFRAME_INTERVAL)
        }

        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoCodec.createInputSurface()
        videoCodec.start()

        // Cấu hình audio encoder
        val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioCodec.start()
    }
    private var totalAudioBytesRecorded = 0L
    fun startAudioRecording() {
        audioThread = Thread {
            val minBufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val buffer = ByteArray(minBufferSize * 2)

            if (ActivityCompat.checkSelfPermission(
                    App.instance!!.applicationContext,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                isRecording = false
                Log.e("AudioRecord", "Permission not granted")
                return@Thread
            }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )

            audioRecord.startRecording()
            Log.d("AudioRecord", "Recording started")

            while (isRecording) {
                val readBytes = audioRecord.read(buffer, 0, buffer.size)
                if (readBytes > 0) {
                    val inputIndex = audioCodec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = audioCodec.getInputBuffer(inputIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(buffer, 0, readBytes)

                        // Tính presentationTimeUs dựa trên tổng số byte đã ghi
                        val presentationTimeUs = (totalAudioBytesRecorded * 1_000_000L) / (AUDIO_SAMPLE_RATE * 2) // 2 bytes per sample for 16bit PCM

                        audioCodec.queueInputBuffer(
                            inputIndex,
                            0,
                            readBytes,
                            presentationTimeUs,
                            0
                        )
                        totalAudioBytesRecorded += readBytes.toLong()
                    }
                    drainAudioEncoder()
                } else {
                    Log.w("AudioRecord", "readBytes = $readBytes")
                }
            }

            // Kết thúc stream audio
            val inputIndex = audioCodec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val presentationTimeUs = (totalAudioBytesRecorded * 1_000_000L) / (AUDIO_SAMPLE_RATE * 2)
                audioCodec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    presentationTimeUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drainAudioEncoder()
            audioRecord.stop()
            audioRecord.release()
            Log.d("AudioRecord", "Recording stopped")
        }
        audioThread.start()
    }

    private fun calculateAudioPresentationTimeUs(): Long {
        // Nếu baseTimeUs chưa set thì audio pts = 0
        if (baseTimeUs < 0) return 0L

        // Tính pts dựa trên thời gian hiện tại trừ base time theo micro giây
        val currentTimeNs = System.nanoTime()
        val ptsUs = (currentTimeNs / 1000) - baseTimeUs

        // Đảm bảo pts audio không giảm dần (ghi buffer không trùng hoặc giảm)
        if (ptsUs <= lastAudioPtsUs) {
            return lastAudioPtsUs + 1
        } else {
            lastAudioPtsUs = ptsUs
            return ptsUs
        }
    }

    fun drainVideoEncoder() {
        while (true) {
            val outputIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, 10_000)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                videoTrackIndex = mediaMuxer.addTrack(videoCodec.outputFormat)
                startMuxerIfReady()
                continue
            }

            if (outputIndex >= 0) {
                val encodedData = videoCodec.getOutputBuffer(outputIndex) ?: continue

                if (videoBufferInfo.size > 0 && muxerStarted) {
                    // Lấy thời điểm gốc của video (điểm khởi đầu timestamp)
                    if (baseTimeUs < 0) {
                        baseTimeUs = videoBufferInfo.presentationTimeUs
                    }
                    // Tính timestamp đã chỉnh để bắt đầu từ 0
                    val adjustedPts = videoBufferInfo.presentationTimeUs - baseTimeUs

                    encodedData.position(videoBufferInfo.offset)
                    encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)

                    val newBufferInfo = MediaCodec.BufferInfo().apply {
                        set(videoBufferInfo.offset, videoBufferInfo.size, adjustedPts, videoBufferInfo.flags)
                    }

                    mediaMuxer.writeSampleData(videoTrackIndex, encodedData, newBufferInfo)
                }

                videoCodec.releaseOutputBuffer(outputIndex, false)

                if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }

    fun drainAudioEncoder() {
        while (true) {
            val outputIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, 10_000)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                audioTrackIndex = mediaMuxer.addTrack(audioCodec.outputFormat)
                startMuxerIfReady()
                continue
            }

            if (outputIndex >= 0) {
                val encodedData = audioCodec.getOutputBuffer(outputIndex) ?: continue
                if (audioBufferInfo.size > 0 && muxerStarted) {
                    // Sử dụng pts đã tính toán trong calculateAudioPresentationTimeUs
                    val ptsUs = calculateAudioPresentationTimeUs()

                    encodedData.position(audioBufferInfo.offset)
                    encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)

                    val newBufferInfo = MediaCodec.BufferInfo().apply {
                        set(audioBufferInfo.offset, audioBufferInfo.size, ptsUs, audioBufferInfo.flags)
                    }

                    mediaMuxer.writeSampleData(audioTrackIndex, encodedData, newBufferInfo)
                }

                audioCodec.releaseOutputBuffer(outputIndex, false)

                if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }

    private fun startMuxerIfReady() {
        if (videoTrackIndex != -1 && audioTrackIndex != -1 && !muxerStarted) {
            mediaMuxer.start()
            muxerStarted = true
        }
    }

    fun stop() {
        isRecording = false

        if (::audioThread.isInitialized) {
            try {
                audioThread.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        try {
            videoCodec.signalEndOfInputStream()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        drainVideoEncoder()
        drainAudioEncoder()

        try {
            videoCodec.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videoCodec.release()

        try {
            audioCodec.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioCodec.release()

        if (muxerStarted) {
            try {
                mediaMuxer.stop()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
            mediaMuxer.release()
        }

        // Cập nhật thư viện media để video hiển thị trên thiết bị
        App.instance?.applicationContext?.sendBroadcast(
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(Uri.fromFile(File(mediaMuxerPath)))
        )
    }
}


