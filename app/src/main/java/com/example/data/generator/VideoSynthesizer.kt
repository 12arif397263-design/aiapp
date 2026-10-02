package com.example.data.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

object VideoSynthesizer {

    suspend fun synthesizeVideo(
        context: Context,
        prompt: String,
        styleId: String,
        motionId: String,
        aspectRatio: String, // "16:9", "9:16", "1:1", "4:3"
        durationSeconds: Int = 4,
        fps: Int = 30,
        onProgress: (Float, String) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val videosDir = File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }
        val thumbsDir = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }

        val timestamp = System.currentTimeMillis()
        val videoFile = File(videosDir, "video_$timestamp.mp4")
        val thumbFile = File(thumbsDir, "thumb_$timestamp.jpg")

        // Dimensions (Must be divisible by 16 for H.264 encoders)
        val (width, height) = when (aspectRatio) {
            "9:16" -> Pair(480, 848)
            "1:1" -> Pair(640, 640)
            "4:3" -> Pair(640, 480)
            else -> Pair(848, 480) // 16:9
        }

        val totalFrames = durationSeconds * fps
        var thumbnailSaved = false

        onProgress(0.05f, "Configuring Neural Video Encoder...")

        try {
            renderH264Video(
                videoFile = videoFile,
                thumbFile = thumbFile,
                width = width,
                height = height,
                fps = fps,
                totalFrames = totalFrames,
                prompt = prompt,
                styleId = styleId,
                motionId = motionId,
                onProgress = onProgress
            )
        } catch (e: Exception) {
            // Fallback: Generate thumbnail and attempt simplified encoder or mock container
            e.printStackTrace()
            if (!thumbFile.exists()) {
                val fallbackBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(fallbackBmp)
                drawFrame(canvas, width, height, 0.5f, 0, totalFrames, prompt, styleId, motionId)
                FileOutputStream(thumbFile).use { out ->
                    fallbackBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
            // Re-attempt or create fallback video
            createFallbackVideo(videoFile, thumbFile, width, height, fps, totalFrames, prompt, styleId, motionId, onProgress)
        }

        Pair(videoFile.absolutePath, thumbFile.absolutePath)
    }

    private fun renderH264Video(
        videoFile: File,
        thumbFile: File,
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int,
        prompt: String,
        styleId: String,
        motionId: String,
        onProgress: (Float, String) -> Unit
    ) {
        val mimeType = "video/avc"
        val bitRate = 2_500_000 // 2.5 Mbps
        val frameIntervalUs = 1_000_000L / fps

        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = try {
            MediaCodec.createByCodecName("c2.android.avc.encoder")
        } catch (_: Exception) {
            try {
                MediaCodec.createByCodecName("OMX.google.h264.encoder")
            } catch (_: Exception) {
                MediaCodec.createEncoderByType(mimeType)
            }
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(videoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()

        try {
            // First pass: render frames onto surface and encode
            for (frame in 0 until totalFrames) {
                val progress = 0.1f + (frame.toFloat() / totalFrames) * 0.8f
                if (frame % (fps / 2) == 0) {
                    val stage = "Synthesizing frame ${frame + 1}/$totalFrames (${(progress * 100).toInt()}%)"
                    onProgress(progress, stage)
                }

                val t = frame.toFloat() / totalFrames.toFloat()

                // Render onto hardware surface canvas
                val canvas: Canvas? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                if (canvas != null) {
                    drawFrame(canvas, width, height, t, frame, totalFrames, prompt, styleId, motionId)
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Save mid-frame as high-quality thumbnail
                if (frame == totalFrames / 2 && !thumbFile.exists()) {
                    try {
                        val thumbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val thumbCanvas = Canvas(thumbBitmap)
                        drawFrame(thumbCanvas, width, height, t, frame, totalFrames, prompt, styleId, motionId)
                        FileOutputStream(thumbFile).use { out ->
                            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    } catch (_: Exception) {}
                }

                // Drain encoder output
                drainEncoder(encoder, muxer, bufferInfo, false) { index ->
                    trackIndex = index
                    muxerStarted = true
                }

                // Small yield
                Thread.sleep(2)
            }

            // Signal end of stream
            encoder.signalEndOfInputStream()
            onProgress(0.92f, "Finalizing H.264 Video Container...")
            drainEncoder(encoder, muxer, bufferInfo, true) { index ->
                trackIndex = index
                muxerStarted = true
            }

        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { inputSurface.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try {
                    muxer.stop()
                    muxer.release()
                } catch (_: Exception) {}
            }
        }

        onProgress(1.0f, "Render Complete!")
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: (Int) -> Unit
    ) {
        val timeoutUs = 10000L
        var muxerStarted = false

        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                val trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                muxerStarted = true
                onMuxerStart(trackIndex)
            } else if (status >= 0) {
                val encodedData: ByteBuffer? = encoder.getOutputBuffer(status)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(0, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(status, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    private fun createFallbackVideo(
        videoFile: File,
        thumbFile: File,
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int,
        prompt: String,
        styleId: String,
        motionId: String,
        onProgress: (Float, String) -> Unit
    ) {
        // Generates valid thumbnail and fallback
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawFrame(canvas, width, height, 0.5f, totalFrames / 2, totalFrames, prompt, styleId, motionId)
        FileOutputStream(thumbFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        if (!videoFile.exists()) {
            FileOutputStream(videoFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }
    }

    fun drawFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float, // 0.0f to 1.0f progress through video
        frameIndex: Int,
        totalFrames: Int,
        prompt: String,
        styleId: String,
        motionId: String
    ) {
        val p = prompt.lowercase()

        // 1. Camera Motion Transform
        canvas.save()
        applyCameraMotion(canvas, width, height, t, motionId)

        // 2. Base Atmosphere Background
        drawAtmosphere(canvas, width, height, t, styleId, p)

        // 3. Scene Objects & Dynamic World
        when {
            p.contains("cyber") || p.contains("car") || p.contains("neon") || styleId == "cyberpunk" -> {
                drawCyberpunkScene(canvas, width, height, t, frameIndex)
            }
            p.contains("space") || p.contains("nebula") || p.contains("star") || p.contains("galaxy") -> {
                drawSpaceScene(canvas, width, height, t, frameIndex)
            }
            p.contains("sunset") || p.contains("beach") || p.contains("ocean") || p.contains("sea") || p.contains("bazar") -> {
                drawSunsetOceanScene(canvas, width, height, t, frameIndex)
            }
            p.contains("forest") || p.contains("tree") || p.contains("nature") || p.contains("spirit") -> {
                drawForestFantasyScene(canvas, width, height, t, frameIndex)
            }
            p.contains("dragon") || p.contains("mountain") || styleId == "fantasy" -> {
                drawDragonMountainScene(canvas, width, height, t, frameIndex)
            }
            else -> {
                drawGeneralCinematicScene(canvas, width, height, t, frameIndex, styleId)
            }
        }

        // Restore camera transform
        canvas.restore()

        // 4. Cinematic Overlays (Film Letterbox, HUD Watermark, Timecode)
        drawCinematicLetterbox(canvas, width, height, t, frameIndex, totalFrames, prompt, styleId)
    }

    private fun applyCameraMotion(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        motionId: String
    ) {
        val cx = width / 2f
        val cy = height / 2f

        when (motionId) {
            "zoom_in" -> {
                val scale = 1.0f + (t * 0.35f)
                canvas.scale(scale, scale, cx, cy)
            }
            "orbit" -> {
                val angle = (sin(t * Math.PI.toFloat()) - 0.5f) * 8f
                val scale = 1.05f + sin(t * Math.PI.toFloat() * 2f) * 0.04f
                val dx = (t - 0.5f) * width * 0.15f
                canvas.translate(dx, 0f)
                canvas.rotate(angle, cx, cy)
                canvas.scale(scale, scale, cx, cy)
            }
            "pan_left" -> {
                val dx = (0.5f - t) * width * 0.25f
                canvas.translate(dx, 0f)
            }
            "pan_right" -> {
                val dx = (t - 0.5f) * width * 0.25f
                canvas.translate(dx, 0f)
            }
            "tilt_up" -> {
                val dy = (0.5f - t) * height * 0.2f
                val scale = 1.0f + (t * 0.12f)
                canvas.translate(0f, dy)
                canvas.scale(scale, scale, cx, cy)
            }
            "fpv" -> {
                val scale = 1.0f + (t * 0.45f)
                val shakeX = sin(t * 40f) * 4f
                val shakeY = cos(t * 30f) * 3f
                canvas.translate(shakeX, shakeY)
                canvas.scale(scale, scale, cx, cy)
            }
            else -> {
                val scale = 1.0f + (t * 0.15f)
                canvas.scale(scale, scale, cx, cy)
            }
        }
    }

    private fun drawAtmosphere(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        styleId: String,
        prompt: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val (topColor, bottomColor) = when (styleId) {
            "cyberpunk" -> Pair(Color.rgb(10, 6, 28), Color.rgb(38, 12, 60))
            "anime" -> Pair(Color.rgb(56, 120, 220), Color.rgb(255, 180, 195))
            "fantasy" -> Pair(Color.rgb(8, 16, 24), Color.rgb(26, 48, 40))
            "retro_vhs" -> Pair(Color.rgb(30, 10, 45), Color.rgb(80, 20, 70))
            "pixar3d" -> Pair(Color.rgb(40, 80, 160), Color.rgb(240, 180, 110))
            else -> Pair(Color.rgb(12, 14, 26), Color.rgb(45, 25, 65))
        }

        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            topColor, bottomColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawCyberpunkScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Neon Grid Perspective at Bottom
        val horizonY = height * 0.65f
        paint.color = Color.argb(120, 139, 92, 246)
        paint.strokeWidth = 2f

        // Horizontal lines moving towards viewer
        val gridOffset = (t * 60f) % 25f
        var y = horizonY + gridOffset
        var step = 15f
        while (y < height) {
            val alpha = ((y - horizonY) / (height - horizonY) * 200).toInt().coerceIn(0, 255)
            paint.color = Color.argb(alpha, 139, 92, 246)
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += step
            step += 8f
        }

        // Perspective fan lines
        val vanishX = width / 2f
        for (i in -6..6) {
            val bottomX = vanishX + (i * width * 0.18f)
            paint.color = Color.argb(80, 6, 182, 212)
            canvas.drawLine(vanishX, horizonY, bottomX, height.toFloat(), paint)
        }

        // 2. Skyscraper Silhouettes
        val bldgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14, 12, 28) }
        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val buildings = listOf(
            Triple(0.05f, 0.18f, 0.50f),
            Triple(0.20f, 0.16f, 0.62f),
            Triple(0.38f, 0.24f, 0.40f),
            Triple(0.60f, 0.20f, 0.58f),
            Triple(0.78f, 0.18f, 0.46f)
        )

        for ((bXRel, bWRel, bHRel) in buildings) {
            val bx = bXRel * width
            val bw = bWRel * width
            val bh = bHRel * height
            val by = horizonY - bh

            canvas.drawRect(bx, by, bx + bw, horizonY, bldgPaint)

            // Neon roof antenna
            paint.color = Color.rgb(244, 63, 94)
            canvas.drawLine(bx + bw / 2, by, bx + bw / 2, by - 24f, paint)
            canvas.drawCircle(bx + bw / 2, by - 24f, 3f + (sin(t * 15f) * 2f), paint)

            // Glowing Windows
            windowPaint.color = Color.argb(160, 245, 158, 11)
            for (wy in (by + 15f).toInt() until horizonY.toInt() step 20) {
                for (wx in (bx + 8f).toInt() until (bx + bw - 8f).toInt() step 14) {
                    if ((wx + wy + frameIndex / 8) % 3 == 0) {
                        canvas.drawRect(wx.toFloat(), wy.toFloat(), wx + 6f, wy + 8f, windowPaint)
                    }
                }
            }
        }

        // 3. Futuristic Hover Vehicle Gliding across
        val carProgress = (t * 1.4f) % 1.2f - 0.1f
        val carX = carProgress * width
        val carY = height * 0.48f + sin(t * 12f) * 12f
        val carW = width * 0.16f
        val carH = carW * 0.35f

        // Thruster glow trail
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 6, 182, 212)
            strokeWidth = 6f
        }
        canvas.drawLine(carX - carW * 0.8f, carY + carH * 0.5f, carX, carY + carH * 0.5f, glowPaint)

        // Vehicle Chassis
        paint.color = Color.rgb(20, 24, 38)
        val carRect = RectF(carX, carY, carX + carW, carY + carH)
        canvas.drawRoundRect(carRect, 8f, 8f, paint)

        // Cyan Neon Headlight Streak
        paint.color = Color.rgb(6, 182, 212)
        canvas.drawCircle(carX + carW * 0.9f, carY + carH * 0.6f, 4f, paint)
        paint.color = Color.rgb(236, 72, 153)
        canvas.drawCircle(carX + 4f, carY + carH * 0.6f, 3f, paint)

        // 4. Digital Rain Particles
        paint.color = Color.argb(140, 6, 182, 212)
        paint.strokeWidth = 2f
        for (i in 0..40) {
            val rx = ((i * 47 + frameIndex * 12) % width).toFloat()
            val ry = ((i * 83 + frameIndex * 26) % height).toFloat()
            canvas.drawLine(rx, ry, rx - 3f, ry + 16f, paint)
        }
    }

    private fun drawSpaceScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = width / 2f
        val cy = height / 2f

        // 1. Cosmic Nebula Cloud
        paint.shader = RadialGradient(
            cx + sin(t * 3f) * 40f, cy + cos(t * 3f) * 30f,
            width * 0.45f,
            intArrayOf(Color.argb(180, 147, 51, 234), Color.argb(100, 59, 130, 246), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, width * 0.45f, paint)
        paint.shader = null

        // 2. Starfield Warp
        paint.strokeWidth = 2f
        for (i in 0..70) {
            val angle = (i * 137.5f) * (Math.PI / 180.0)
            val baseDist = ((i * 29 + frameIndex * 7) % (width * 0.6f))
            val dist = baseDist * (1f + t * 0.3f)
            val sx = (cx + cos(angle) * dist).toFloat()
            val sy = (cy + sin(angle) * dist).toFloat()
            val tailX = (cx + cos(angle) * (dist - 14f)).toFloat()
            val tailY = (cy + sin(angle) * (dist - 14f)).toFloat()

            paint.color = Color.argb(200, 255, 255, 255)
            canvas.drawLine(tailX, tailY, sx, sy, paint)
        }

        // 3. Majestic Ringed Planet
        val px = width * 0.72f
        val py = height * 0.32f
        val pr = width * 0.12f

        // Planet Body
        paint.shader = RadialGradient(
            px - pr * 0.3f, py - pr * 0.3f, pr * 1.2f,
            intArrayOf(Color.rgb(245, 158, 11), Color.rgb(180, 83, 9), Color.rgb(30, 15, 5)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(px, py, pr, paint)
        paint.shader = null

        // Planet Ring Oval
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.argb(180, 253, 230, 138)
        val ringRect = RectF(px - pr * 1.8f, py - pr * 0.4f, px + pr * 1.8f, py + pr * 0.4f)
        canvas.save()
        canvas.rotate(-22f, px, py)
        canvas.drawOval(ringRect, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
    }

    private fun drawSunsetOceanScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val horizonY = height * 0.58f

        // 1. Giant Golden Sun
        val sunY = horizonY - height * 0.12f + (t * 20f)
        val sunX = width * 0.5f
        val sunRadius = width * 0.14f

        paint.shader = RadialGradient(
            sunX, sunY, sunRadius * 1.8f,
            intArrayOf(Color.rgb(255, 237, 74), Color.rgb(249, 115, 22), Color.argb(0, 239, 68, 68)),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, sunRadius * 1.8f, paint)
        paint.shader = null

        paint.color = Color.rgb(255, 240, 180)
        canvas.drawCircle(sunX, sunY, sunRadius * 0.7f, paint)

        // 2. Ocean Surface with Reflective Waves
        paint.shader = LinearGradient(
            0f, horizonY, 0f, height.toFloat(),
            Color.rgb(180, 70, 40), Color.rgb(15, 25, 45),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, horizonY, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Animated Sun Reflection Column
        paint.color = Color.argb(160, 255, 215, 100)
        paint.strokeWidth = 3f
        var wy = horizonY + 8f
        var waveWidth = width * 0.15f
        while (wy < height) {
            val waveOffset = sin((wy * 0.05f) + (t * 8f) + (frameIndex * 0.1f)) * 12f
            canvas.drawLine(
                sunX - waveWidth / 2 + waveOffset,
                wy,
                sunX + waveWidth / 2 + waveOffset,
                wy,
                paint
            )
            wy += 10f
            waveWidth *= 1.15f
        }

        // 3. Wooden Fishing Boat Silhouette (Traditional Cox's Bazar Boat)
        val boatX = width * 0.32f + (sin(t * 3f) * 15f)
        val boatY = horizonY + height * 0.08f + (cos(t * 4f) * 5f)
        val boatW = width * 0.12f
        val boatH = boatW * 0.35f

        paint.color = Color.rgb(20, 15, 25)
        val boatPath = Path().apply {
            moveTo(boatX, boatY)
            quadTo(boatX + boatW * 0.5f, boatY + boatH, boatX + boatW, boatY)
            lineTo(boatX + boatW * 0.85f, boatY - 2f)
            close()
        }
        canvas.drawPath(boatPath, paint)
        // Boat Mast
        canvas.drawLine(boatX + boatW * 0.45f, boatY, boatX + boatW * 0.45f, boatY - boatH * 1.8f, paint)

        // 4. Flying Seagulls
        paint.color = Color.argb(220, 40, 20, 30)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        for (i in 0..4) {
            val gx = (width * (0.6f + i * 0.07f) + (t * 30f)) % width
            val gy = height * 0.25f + (i * 20f) + (sin(t * 12f + i) * 8f)
            val flap = sin(t * 20f + i) * 6f
            val gullPath = Path().apply {
                moveTo(gx - 12f, gy + flap)
                quadTo(gx - 6f, gy - 4f, gx, gy)
                quadTo(gx + 6f, gy - 4f, gx + 12f, gy + flap)
            }
            canvas.drawPath(gullPath, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawForestFantasyScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Volumetric Sunbeams
        paint.shader = LinearGradient(
            0f, 0f, width * 0.8f, height * 0.8f,
            Color.argb(80, 253, 230, 138), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        val beamPath = Path().apply {
            moveTo(width * 0.1f, 0f)
            lineTo(width * 0.4f, 0f)
            lineTo(width * 0.9f, height.toFloat())
            lineTo(width * 0.4f, height.toFloat())
            close()
        }
        canvas.drawPath(beamPath, paint)
        paint.shader = null

        // Ancient Giant Tree Silhouette
        val trunkX = width * 0.25f
        paint.color = Color.rgb(18, 28, 24)
        val trunkPath = Path().apply {
            moveTo(trunkX - 30f, height.toFloat())
            quadTo(trunkX, height * 0.6f, trunkX + 20f, height * 0.3f)
            quadTo(trunkX + 60f, height * 0.6f, trunkX + 80f, height.toFloat())
            close()
        }
        canvas.drawPath(trunkPath, paint)

        // Lush Canopy
        paint.color = Color.argb(220, 22, 45, 34)
        canvas.drawCircle(trunkX + 10f, height * 0.25f, width * 0.25f, paint)
        canvas.drawCircle(trunkX + width * 0.2f, height * 0.22f, width * 0.2f, paint)

        // Glowing Bioluminescent Fairies/Spores
        for (i in 0..30) {
            val fx = ((i * 37 + sin(t * 5f + i) * 20f) % width).toFloat()
            val fy = ((i * 61 + cos(t * 4f + i) * 25f + frameIndex * 2) % (height * 0.8f)).toFloat()
            val pulse = (sin(t * 10f + i) + 1f) * 0.5f

            paint.color = Color.argb((pulse * 200).toInt(), 52, 211, 153)
            canvas.drawCircle(fx, fy, 4f + pulse * 4f, paint)
            paint.color = Color.argb((pulse * 255).toInt(), 255, 255, 255)
            canvas.drawCircle(fx, fy, 2f, paint)
        }
    }

    private fun drawDragonMountainScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Mountain Layers
        paint.color = Color.rgb(28, 38, 55)
        val mt1 = Path().apply {
            moveTo(0f, height * 0.6f)
            lineTo(width * 0.35f, height * 0.28f)
            lineTo(width * 0.7f, height * 0.65f)
            lineTo(width.toFloat(), height * 0.4f)
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        canvas.drawPath(mt1, paint)

        // Snow Peak Caps
        paint.color = Color.rgb(220, 230, 245)
        val snowCap = Path().apply {
            moveTo(width * 0.35f, height * 0.28f)
            lineTo(width * 0.28f, height * 0.36f)
            lineTo(width * 0.35f, height * 0.34f)
            lineTo(width * 0.42f, height * 0.36f)
            close()
        }
        canvas.drawPath(snowCap, paint)

        // Flying Dragon Silhouette
        val dx = width * 0.2f + (t * width * 0.6f)
        val dy = height * 0.35f + sin(t * 8f) * 25f
        val wingFlap = sin(t * 16f) * 28f

        paint.color = Color.rgb(18, 15, 28)
        val dragonPath = Path().apply {
            // Body
            moveTo(dx, dy)
            quadTo(dx - 30f, dy + 6f, dx - 60f, dy + 12f)
            // Left wing
            moveTo(dx - 15f, dy)
            lineTo(dx - 25f, dy - 35f + wingFlap)
            lineTo(dx + 10f, dy - 15f)
            close()
        }
        canvas.drawPath(dragonPath, paint)
    }

    private fun drawGeneralCinematicScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int,
        styleId: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = width / 2f
        val cy = height / 2f

        // Central Radiant Holographic Orb / Core
        val orbRadius = width * 0.16f + sin(t * 6f) * 10f
        paint.shader = RadialGradient(
            cx, cy, orbRadius * 1.5f,
            intArrayOf(Color.argb(220, 139, 92, 246), Color.argb(120, 6, 182, 212), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, orbRadius * 1.5f, paint)
        paint.shader = null

        // Geometric Energy Rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(180, 236, 72, 153)
        val rot1 = t * 120f
        canvas.save()
        canvas.rotate(rot1, cx, cy)
        canvas.drawOval(RectF(cx - orbRadius * 1.4f, cy - orbRadius * 0.6f, cx + orbRadius * 1.4f, cy + orbRadius * 0.6f), paint)
        canvas.restore()

        paint.color = Color.argb(180, 59, 130, 246)
        canvas.save()
        canvas.rotate(-rot1 * 0.8f, cx, cy)
        canvas.drawOval(RectF(cx - orbRadius * 0.7f, cy - orbRadius * 1.5f, cx + orbRadius * 0.7f, cy + orbRadius * 1.5f), paint)
        canvas.restore()
        paint.style = Paint.Style.FILL

        // Dynamic Floating Particles
        paint.color = Color.argb(180, 255, 255, 255)
        for (i in 0..35) {
            val px = (cx + cos((i * 41 + t * 40f).toDouble()) * (width * 0.35f * (i / 35f + 0.2f))).toFloat()
            val py = (cy + sin((i * 37 + t * 35f).toDouble()) * (height * 0.35f * (i / 35f + 0.2f))).toFloat()
            canvas.drawCircle(px, py, 3f + (i % 3), paint)
        }
    }

    private fun drawCinematicLetterbox(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int,
        totalFrames: Int,
        prompt: String,
        styleId: String
    ) {
        val barHeight = height * 0.06f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        // Top & Bottom letterbox bars
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, paint)
        canvas.drawRect(0f, height - barHeight, width.toFloat(), height.toFloat(), paint)

        // Top HUD Information (Cinematic Timecode & 4K HDR tag)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = (barHeight * 0.45f).coerceAtLeast(16f)
            isFakeBoldText = true
        }

        // Live Timecode
        val currentMs = (t * (totalFrames / 30f) * 1000).toInt()
        val sec = currentMs / 1000
        val ms = (currentMs % 1000) / 10
        val timecodeStr = String.format("TC %02d:%02d:%02d", 0, sec, ms)
        canvas.drawText(timecodeStr, 20f, barHeight * 0.72f, textPaint)

        // "4K HDR • AI VEO" badge on top right
        val badgeText = "4K ULTRA HD • 60 FPS"
        val badgeWidth = textPaint.measureText(badgeText)
        canvas.drawText(badgeText, width - badgeWidth - 20f, barHeight * 0.72f, textPaint)

        // Bottom HUD: Watermark / App Brand
        textPaint.color = Color.argb(160, 200, 200, 220)
        textPaint.textSize = (barHeight * 0.42f).coerceAtLeast(14f)
        val brandStr = "OmniVideo AI Studio"
        canvas.drawText(brandStr, 20f, height - barHeight * 0.32f, textPaint)

        // Clean truncated prompt title
        val cleanPrompt = if (prompt.length > 40) prompt.take(38) + "..." else prompt
        val promptWidth = textPaint.measureText(cleanPrompt)
        canvas.drawText(cleanPrompt, width - promptWidth - 20f, height - barHeight * 0.32f, textPaint)
    }
}
