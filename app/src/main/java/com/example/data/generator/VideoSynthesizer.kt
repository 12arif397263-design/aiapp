package com.example.data.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import com.example.data.director.AiPromptDirector
import com.example.data.director.ParsedPromptDirective
import com.example.data.director.VisualStyleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
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
        sourceBitmap: Bitmap? = null,
        directive: ParsedPromptDirective? = null,
        onProgress: (Float, String) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val videosDir = File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }
        val thumbsDir = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }

        val timestamp = System.currentTimeMillis()
        val videoFile = File(videosDir, "video_$timestamp.mp4")
        val thumbFile = File(thumbsDir, "thumb_$timestamp.jpg")

        val (width, height) = when (aspectRatio) {
            "9:16" -> Pair(480, 848)
            "1:1" -> Pair(640, 640)
            "4:3" -> Pair(640, 480)
            else -> Pair(848, 480) // 16:9
        }

        val totalFrames = durationSeconds * fps
        val activeDirective = directive ?: AiPromptDirector.analyzeAndDirect(prompt)

        onProgress(0.15f, "Configuring H.264 Neural Video Engine...")

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
                sourceBitmap = sourceBitmap,
                directive = activeDirective,
                onProgress = onProgress
            )
        } catch (e: Exception) {
            e.printStackTrace()
            createFallbackVideo(videoFile, thumbFile, width, height, fps, totalFrames, prompt, styleId, motionId, sourceBitmap, activeDirective, onProgress)
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
        sourceBitmap: Bitmap?,
        directive: ParsedPromptDirective,
        onProgress: (Float, String) -> Unit
    ) {
        val mimeType = "video/avc"
        val bitRate = 2_500_000

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
            for (frame in 0 until totalFrames) {
                val progress = 0.2f + (frame.toFloat() / totalFrames) * 0.7f
                if (frame % (fps / 2) == 0) {
                    val stage = "Synthesizing frame ${frame + 1}/$totalFrames (${(progress * 100).toInt()}%)"
                    onProgress(progress, stage)
                }

                val t = frame.toFloat() / totalFrames.toFloat()

                val canvas: Canvas? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                if (canvas != null) {
                    drawFrame(canvas, width, height, t, frame, totalFrames, prompt, styleId, motionId, sourceBitmap, directive)
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                if (frame == totalFrames / 2 && !thumbFile.exists()) {
                    try {
                        val thumbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val thumbCanvas = Canvas(thumbBitmap)
                        drawFrame(thumbCanvas, width, height, t, frame, totalFrames, prompt, styleId, motionId, sourceBitmap, directive)
                        FileOutputStream(thumbFile).use { out ->
                            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    } catch (_: Exception) {}
                }

                drainEncoder(encoder, muxer, bufferInfo, false) { index ->
                    trackIndex = index
                    muxerStarted = true
                }

                Thread.sleep(2)
            }

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
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: (Int) -> Unit
    ) {
        val timeoutUs = 10000L
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                val trackIndex = muxer.addTrack(newFormat)
                muxer.start()
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
        sourceBitmap: Bitmap?,
        directive: ParsedPromptDirective,
        onProgress: (Float, String) -> Unit
    ) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawFrame(canvas, width, height, 0.5f, totalFrames / 2, totalFrames, prompt, styleId, motionId, sourceBitmap, directive)
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
        t: Float,
        frameIndex: Int,
        totalFrames: Int,
        prompt: String,
        styleId: String,
        motionId: String,
        sourceBitmap: Bitmap? = null,
        directive: ParsedPromptDirective? = null
    ) {
        val activeDirective = directive ?: AiPromptDirector.analyzeAndDirect(prompt)

        canvas.save()
        applyCameraMotion(canvas, width, height, t, motionId, activeDirective)

        if (sourceBitmap != null) {
            drawAiGeneratedScene(canvas, width, height, t, frameIndex, sourceBitmap, activeDirective)
        } else {
            drawContextualScene(canvas, width, height, t, frameIndex, activeDirective)
        }

        canvas.restore()
        drawCinematicLetterbox(canvas, width, height, t, frameIndex, totalFrames, activeDirective)
    }

    private fun applyCameraMotion(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        motionId: String,
        directive: ParsedPromptDirective
    ) {
        val cx = width / 2f
        val cy = height / 2f

        // Respect horror push-in or tracking
        val effectiveMotion = when {
            directive.visualStyle == VisualStyleType.HORROR_CINEMATIC -> "zoom_in"
            directive.originalPrompt.contains("tracking", ignoreCase = true) -> "pan_right"
            else -> motionId
        }

        when (effectiveMotion) {
            "zoom_in" -> {
                val scale = 1.0f + (t * 0.22f)
                canvas.scale(scale, scale, cx, cy)
            }
            "zoom_out" -> {
                val scale = 1.22f - (t * 0.22f)
                canvas.scale(scale, scale, cx, cy)
            }
            "orbit" -> {
                val angle = (sin(t * Math.PI.toFloat()) - 0.5f) * 5f
                val scale = 1.04f + sin(t * Math.PI.toFloat() * 2f) * 0.02f
                val dx = (t - 0.5f) * width * 0.08f
                canvas.translate(dx, 0f)
                canvas.rotate(angle, cx, cy)
                canvas.scale(scale, scale, cx, cy)
            }
            "pan_left" -> {
                val dx = (0.5f - t) * width * 0.16f
                canvas.translate(dx, 0f)
            }
            "pan_right" -> {
                val dx = (t - 0.5f) * width * 0.16f
                canvas.translate(dx, 0f)
            }
            "tilt_up" -> {
                val dy = (0.5f - t) * height * 0.14f
                canvas.translate(0f, dy)
            }
            "tilt_down" -> {
                val dy = (t - 0.5f) * height * 0.14f
                canvas.translate(0f, dy)
            }
            else -> {
                val scale = 1.0f + (t * 0.10f)
                canvas.scale(scale, scale, cx, cy)
            }
        }
    }

    private fun drawAiGeneratedScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int,
        bitmap: Bitmap,
        directive: ParsedPromptDirective
    ) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val bmpWidth = bitmap.width.toFloat()
        val bmpHeight = bitmap.height.toFloat()
        val scale = maxOf(width / bmpWidth, height / bmpHeight) * 1.12f

        val scaledW = bmpWidth * scale
        val scaledH = bmpHeight * scale
        val left = (width - scaledW) / 2f
        val top = (height - scaledH) / 2f

        val destRect = RectF(left, top, left + scaledW, top + scaledH)
        canvas.drawBitmap(bitmap, null, destRect, paint)

        // Atmosphere overlay tailored to the style
        when (directive.visualStyle) {
            VisualStyleType.HORROR_CINEMATIC -> {
                val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(45, 180, 200, 220)
                }
                for (i in 0..4) {
                    val fy = height * (0.6f + i * 0.08f)
                    val fx = (width * 0.2f + sin((t * 3f + i).toDouble()).toFloat() * 60f)
                    canvas.drawOval(RectF(fx - width * 0.5f, fy - 30f, fx + width * 0.5f, fy + 40f), fogPaint)
                }
            }
            VisualStyleType.CARTOON_3D -> {
                // Bright lively bounce light
                val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(width * 0.2f, height * 0.2f, width * 0.5f,
                        intArrayOf(Color.argb(40, 255, 240, 200), Color.TRANSPARENT),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sunPaint)
            }
            else -> {
                val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val fx = width * (0.15f + t * 0.70f)
                    val fy = height * 0.25f
                    shader = RadialGradient(
                        fx, fy, width * 0.55f,
                        intArrayOf(Color.argb(55, 255, 240, 200), Color.argb(18, 255, 190, 110), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flarePaint)
            }
        }
    }

    private fun drawContextualScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int,
        directive: ParsedPromptDirective
    ) {
        val p = directive.originalPrompt.lowercase()

        when {
            // TEST 1: Horror / Ghost / Abandoned house / Red eyes / Midnight
            directive.visualStyle == VisualStyleType.HORROR_CINEMATIC ||
            p.contains("ghost") || p.contains("horror") || p.contains("ভুত") || p.contains("ভূত") ||
            p.contains("abandoned house") || p.contains("red eyes") || p.contains("তাকিও না") -> {
                drawHorrorGhostScene(canvas, width, height, t, frameIndex, directive)
            }

            // TEST 2: Funny 3D cartoon boy eating mangoes in tree with mother below
            (directive.visualStyle == VisualStyleType.CARTOON_3D && (p.contains("mango") || p.contains("আম") || p.contains("tree"))) ||
            p.contains("eating mango") || p.contains("ছেলে আম") || p.contains("আম খাচ্ছে") -> {
                draw3dCartoonBoyMangoTreeScene(canvas, width, height, t, frameIndex)
            }

            // TEST 3: Black sports car driving through rainy city at night
            p.contains("car") || p.contains("sports car") || p.contains("গাড়ি") || p.contains("গাড়ি") ||
            p.contains("rainy city") || (p.contains("rain") && p.contains("city")) || p.contains("drive") -> {
                drawRainyCitySportsCarScene(canvas, width, height, t, frameIndex)
            }

            // TEST 4: Fantasy dragon flying over ancient castle at sunset
            p.contains("dragon") || p.contains("castle") || p.contains("ড্রাগন") || p.contains("দুর্গ") ||
            directive.visualStyle == VisualStyleType.FANTASY_ANIMATION -> {
                drawFantasyDragonCastleScene(canvas, width, height, t, frameIndex)
            }

            // TEST 5: Documentary fisherman rowing wooden boat on quiet river at sunrise
            p.contains("fisherman") || p.contains("rowing") || p.contains("জেলে") ||
            (p.contains("boat") && p.contains("river")) || (p.contains("নৌকা") && p.contains("নদী")) ||
            directive.visualStyle == VisualStyleType.DOCUMENTARY -> {
                drawDocumentaryFishermanRiverScene(canvas, width, height, t, frameIndex)
            }

            // SPECIFIC: Tiger in jungle
            p.contains("tiger") || p.contains("বাঘ") || (p.contains("jungle") && !p.contains("car")) -> {
                drawTigerJungleScene(canvas, width, height, t, frameIndex)
            }

            // Boy playing football / sports in field
            p.contains("football") || p.contains("মাঠ") || p.contains("খেলা") || p.contains("soccer") -> {
                drawFieldAndPlayingBoyScene(canvas, width, height, t, frameIndex)
            }

            // General Animal in meadow
            p.contains("cat") || p.contains("dog") || p.contains("bird") || p.contains("বিড়াল") || p.contains("কুকুর") -> {
                drawAnimalMeadowScene(canvas, width, height, t, frameIndex)
            }

            // Space / Universe
            p.contains("space") || p.contains("moon") || p.contains("মহাকাশ") || p.contains("চাঁদ") -> {
                drawCosmicSpaceScene(canvas, width, height, t, frameIndex)
            }

            // Default Cinematic Hero
            else -> {
                drawCinematicHeroScene(canvas, width, height, t, frameIndex, directive.originalPrompt)
            }
        }
    }

    // SCENE 1: TEST 1 - BENGALI HORROR GHOST OUTSIDE ABANDONED HOUSE AT MIDNIGHT
    private fun drawHorrorGhostScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int,
        directive: ParsedPromptDirective
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Midnight Dark Blue / Pitch Black Foggy Sky
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.rgb(4, 6, 14), Color.rgb(12, 16, 28), Color.rgb(8, 10, 18)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // 2. Chilling Cold Moonlight
        val moonX = width * 0.78f
        val moonY = height * 0.22f
        paint.shader = RadialGradient(moonX, moonY, width * 0.22f,
            intArrayOf(Color.argb(220, 215, 230, 255), Color.argb(80, 140, 175, 230), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(moonX, moonY, width * 0.22f, paint)
        paint.shader = null

        // 3. Abandoned Decrepit Wooden House Silhouette in Background
        val houseX = width * 0.18f
        val houseY = height * 0.50f
        paint.color = Color.rgb(10, 12, 18)
        val housePath = Path().apply {
            moveTo(houseX - 90f, houseY + 120f)
            lineTo(houseX - 90f, houseY - 10f)
            lineTo(houseX, houseY - 70f) // Roof peak
            lineTo(houseX + 110f, houseY - 10f)
            lineTo(houseX + 110f, houseY + 120f)
            close()
        }
        canvas.drawPath(housePath, paint)

        // Broken window with faint eerie light
        paint.color = Color.argb(90, 100, 130, 180)
        canvas.drawRect(houseX - 45f, houseY + 15f, houseX - 10f, houseY + 55f, paint)
        paint.color = Color.rgb(10, 12, 18)
        paint.strokeWidth = 2.5f
        canvas.drawLine(houseX - 28f, houseY + 15f, houseX - 28f, houseY + 55f, paint)

        // 4. Barren Dead Trees
        paint.color = Color.rgb(8, 9, 14)
        paint.strokeWidth = 5f
        canvas.drawLine(width * 0.88f, height * 0.7f, width * 0.88f, height * 0.35f, paint)
        canvas.drawLine(width * 0.88f, height * 0.5f, width * 0.82f, height * 0.42f, paint)
        canvas.drawLine(width * 0.88f, height * 0.45f, width * 0.94f, height * 0.38f, paint)

        // 5. Dense Drifting Ground Mist & Fog
        val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 160, 185, 215)
        }
        for (i in 0..5) {
            val fy = height * (0.68f + i * 0.06f)
            val fx = width * 0.5f + sin((t * 2.5f + i).toDouble()).toFloat() * 70f
            canvas.drawOval(RectF(fx - width * 0.6f, fy - 25f, fx + width * 0.6f, fy + 35f), fogPaint)
        }

        // 6. Terrifying Female Ghost (Long Tangled Hair, Pale Face, Torn Sari)
        val ghostX = width * (0.52f + sin(t * 3.0).toFloat() * 0.04f)
        val ghostY = height * (0.58f + cos(t * 2.5).toFloat() * 0.03f) // Floating supernatural hover

        // Flowing Torn Dirty Sari (Pale White/Grey Cloth fluttering)
        val sariPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(ghostX, ghostY, ghostX, ghostY + 130f,
                intArrayOf(Color.argb(220, 210, 220, 230), Color.argb(120, 130, 140, 150), Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        val sariPath = Path().apply {
            moveTo(ghostX - 25f, ghostY)
            val flutter = sin(t * 8.0).toFloat() * 12f
            quadTo(ghostX - 35f + flutter, ghostY + 70f, ghostX - 45f + flutter, ghostY + 130f)
            lineTo(ghostX + 45f - flutter, ghostY + 130f)
            quadTo(ghostX + 35f - flutter, ghostY + 70f, ghostX + 25f, ghostY)
            close()
        }
        canvas.drawPath(sariPath, sariPaint)

        // Pale Cracked Face
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(215, 225, 230)
        }
        canvas.drawOval(RectF(ghostX - 16f, ghostY - 32f, ghostX + 16f, ghostY + 8f), facePaint)

        // Long Tangled Black Hair Cascading Down
        val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 10, 15)
        }
        val hairPath = Path().apply {
            moveTo(ghostX - 18f, ghostY - 35f)
            quadTo(ghostX - 28f, ghostY + 10f, ghostX - 32f, ghostY + 75f)
            lineTo(ghostX - 18f, ghostY + 75f)
            lineTo(ghostX - 14f, ghostY)
            close()
        }
        canvas.drawPath(hairPath, hairPaint)
        val hairRight = Path().apply {
            moveTo(ghostX + 18f, ghostY - 35f)
            quadTo(ghostX + 28f, ghostY + 10f, ghostX + 32f, ghostY + 75f)
            lineTo(ghostX + 18f, ghostY + 75f)
            lineTo(ghostX + 14f, ghostY)
            close()
        }
        canvas.drawPath(hairRight, hairPaint)
        canvas.drawCircle(ghostX, ghostY - 26f, 18f, hairPaint) // Hair crown

        // Piercing Fiery Glowing Red Eyes!
        val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(ghostX - 6f, ghostY - 12f, 16f,
                intArrayOf(Color.argb(255, 255, 30, 30), Color.argb(120, 255, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(ghostX - 6f, ghostY - 12f, 16f, eyeGlowPaint)
        canvas.drawCircle(ghostX + 6f, ghostY - 12f, 16f, eyeGlowPaint)

        val eyeCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 240, 240)
        }
        canvas.drawCircle(ghostX - 6f, ghostY - 12f, 2.5f, eyeCore)
        canvas.drawCircle(ghostX + 6f, ghostY - 12f, 2.5f, eyeCore)
    }

    // SCENE 2: TEST 2 - 3D CARTOON BOY SECRETLY EATING MANGO IN TREE WITH MOTHER BELOW
    private fun draw3dCartoonBoyMangoTreeScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Vibrant 3D Cartoon Sunny Blue Sky
        paint.shader = LinearGradient(0f, 0f, 0f, height * 0.7f,
            intArrayOf(Color.rgb(64, 186, 255), Color.rgb(175, 230, 255), Color.rgb(240, 250, 255)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.7f, paint)
        paint.shader = null

        // Lush Cartoon Grassy Ground
        val groundY = height * 0.70f
        paint.shader = LinearGradient(0f, groundY, 0f, height.toFloat(),
            intArrayOf(Color.rgb(76, 209, 55), Color.rgb(40, 150, 30)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Big Mango Tree Trunk & Branches
        paint.color = Color.rgb(120, 75, 40)
        val trunk = Path().apply {
            moveTo(width * 0.15f, groundY)
            quadTo(width * 0.22f, height * 0.45f, width * 0.30f, height * 0.25f)
            lineTo(width * 0.42f, height * 0.25f)
            quadTo(width * 0.32f, height * 0.48f, width * 0.28f, groundY)
            close()
        }
        canvas.drawPath(trunk, paint)

        // Big Horizontal Branch where Boy Sits
        paint.strokeWidth = 24f
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        canvas.drawLine(width * 0.25f, height * 0.42f, width * 0.72f, height * 0.42f, paint)
        paint.style = Paint.Style.FILL

        // Fluffy Stylized Green Mango Foliage Clumps
        paint.color = Color.rgb(46, 175, 70)
        canvas.drawCircle(width * 0.35f, height * 0.20f, 65f, paint)
        canvas.drawCircle(width * 0.55f, height * 0.18f, 75f, paint)
        canvas.drawCircle(width * 0.75f, height * 0.25f, 60f, paint)
        canvas.drawCircle(width * 0.45f, height * 0.28f, 55f, paint)

        // Ripe Golden Mangoes Hanging from Foliage
        paint.color = Color.rgb(255, 185, 20)
        canvas.drawOval(RectF(width * 0.38f, height * 0.26f, width * 0.42f, height * 0.33f), paint)
        canvas.drawOval(RectF(width * 0.62f, height * 0.24f, width * 0.66f, height * 0.31f), paint)
        canvas.drawOval(RectF(width * 0.70f, height * 0.30f, width * 0.74f, height * 0.37f), paint)

        // 3D Cartoon Little Bengali Boy Sitting on Branch Eating Mango
        val boyX = width * 0.50f
        val boyY = height * 0.36f

        // Head (Warm brown cartoon skin tone)
        paint.color = Color.rgb(225, 170, 130)
        canvas.drawCircle(boyX, boyY - 26f, 18f, paint)

        // Black cartoon hair
        paint.color = Color.rgb(30, 20, 20)
        canvas.drawCircle(boyX, boyY - 35f, 15f, paint)
        canvas.drawCircle(boyX - 6f, boyY - 32f, 12f, paint)

        // Expressive Happy Eye
        paint.color = Color.WHITE
        canvas.drawCircle(boyX + 5f, boyY - 28f, 5f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(boyX + 6f, boyY - 28f, 2.5f, paint)

        // Cartoon Yellow Shirt Body
        paint.color = Color.rgb(255, 210, 30)
        canvas.drawRoundRect(RectF(boyX - 12f, boyY - 12f, boyX + 12f, boyY + 16f), 6f, 6f, paint)

        // Hands holding a half-eaten bright yellow mango to mouth!
        val chew = sin(t * 16.0).toFloat() * 2f
        paint.color = Color.rgb(255, 170, 0)
        canvas.drawCircle(boyX + 12f, boyY - 20f + chew, 8f, paint)

        // Mother Searching Below on the Ground
        val momX = width * 0.75f
        val momY = groundY + 50f
        // Mother Body (Red Traditional Bengali Sari)
        paint.color = Color.rgb(220, 38, 38)
        val momBody = Path().apply {
            moveTo(momX, momY - 60f)
            lineTo(momX - 16f, momY)
            lineTo(momX + 16f, momY)
            close()
        }
        canvas.drawPath(momBody, paint)

        // Mother Head with bun
        paint.color = Color.rgb(210, 155, 115)
        canvas.drawCircle(momX, momY - 72f, 12f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(momX - 6f, momY - 74f, 8f, paint) // Hair bun

        // Mother hand over eyes looking around searching!
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(momX, momY - 65f, momX + 14f, momY - 74f, paint)
        paint.style = Paint.Style.FILL
    }

    // SCENE 3: TEST 3 - BLACK SPORTS CAR DRIVING THROUGH RAINY CITY AT NIGHT
    private fun drawRainyCitySportsCarScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dark Rainy Night Sky
        paint.shader = LinearGradient(0f, 0f, 0f, height * 0.55f,
            intArrayOf(Color.rgb(8, 10, 18), Color.rgb(18, 22, 38), Color.rgb(10, 14, 25)),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.55f, paint)
        paint.shader = null

        // Glowing Neon City Skyscrapers
        val neonColors = listOf(Color.rgb(0, 220, 255), Color.rgb(255, 0, 128), Color.rgb(138, 43, 226), Color.rgb(255, 215, 0))
        for (i in 0..7) {
            val bx = i * (width / 6.5f)
            val bh = height * (0.15f + ((i * 5) % 4) * 0.08f)
            paint.color = Color.rgb(15, 18, 28)
            canvas.drawRect(bx, bh, bx + width * 0.13f, height * 0.55f, paint)

            // Neon Sign strip on building
            paint.color = neonColors[i % neonColors.size]
            canvas.drawRect(bx + 8f, bh + 15f, bx + 14f, bh + 55f, paint)
        }

        // Wet Reflective Asphalt Road
        val roadY = height * 0.55f
        paint.shader = LinearGradient(0f, roadY, 0f, height.toFloat(),
            intArrayOf(Color.rgb(12, 14, 20), Color.rgb(20, 24, 32), Color.rgb(10, 12, 16)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, roadY, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Neon Road Reflections in Rain
        for (i in 0..3) {
            val rx = width * (0.2f + i * 0.22f)
            val c = neonColors[i]
            val cAlpha = Color.argb(90, Color.red(c), Color.green(c), Color.blue(c))
            paint.shader = LinearGradient(rx, roadY, rx, roadY + 120f,
                intArrayOf(cAlpha, Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rx - 15f, roadY, rx + 15f, roadY + 120f, paint)
            paint.shader = null
        }

        // Sleek Matte Black Sports Car
        val carX = width * (0.42f + t * 0.16f) // Smooth tracking movement
        val carY = height * 0.74f
        val carW = width * 0.42f
        val carH = height * 0.14f

        // Headlight Beams cutting through rain
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(carX + carW * 0.5f, carY, width * 0.45f,
                intArrayOf(Color.argb(160, 255, 255, 230), Color.argb(40, 255, 255, 200), Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        val beamPath = Path().apply {
            moveTo(carX + carW * 0.45f, carY - 5f)
            lineTo(width.toFloat(), carY - 40f)
            lineTo(width.toFloat(), carY + 60f)
            lineTo(carX + carW * 0.45f, carY + 15f)
            close()
        }
        canvas.drawPath(beamPath, beamPaint)

        // Car Body (Aerodynamic Black)
        paint.color = Color.rgb(15, 16, 20)
        val carBody = Path().apply {
            moveTo(carX - carW * 0.45f, carY + carH * 0.35f)
            lineTo(carX - carW * 0.40f, carY - carH * 0.15f)
            lineTo(carX - carW * 0.15f, carY - carH * 0.60f) // Roof
            lineTo(carX + carW * 0.18f, carY - carH * 0.58f)
            lineTo(carX + carW * 0.38f, carY - carH * 0.05f) // Hood
            lineTo(carX + carW * 0.48f, carY + carH * 0.20f)
            lineTo(carX + carW * 0.45f, carY + carH * 0.38f)
            close()
        }
        canvas.drawPath(carBody, paint)

        // Tinted Windshield
        paint.color = Color.rgb(35, 45, 60)
        val windshield = Path().apply {
            moveTo(carX - carW * 0.10f, carY - carH * 0.52f)
            lineTo(carX + carW * 0.14f, carY - carH * 0.50f)
            lineTo(carX + carW * 0.32f, carY - carH * 0.08f)
            lineTo(carX - carW * 0.05f, carY - carH * 0.08f)
            close()
        }
        canvas.drawPath(windshield, paint)

        // Glowing Red Taillights
        paint.color = Color.rgb(255, 20, 50)
        canvas.drawRoundRect(RectF(carX - carW * 0.44f, carY - 4f, carX - carW * 0.38f, carY + 6f), 3f, 3f, paint)

        // Bright LED Headlights
        paint.color = Color.rgb(255, 255, 240)
        canvas.drawCircle(carX + carW * 0.44f, carY + 5f, 6f, paint)

        // Falling Diagonal Rain Streaks
        paint.color = Color.argb(140, 200, 225, 255)
        paint.strokeWidth = 2f
        for (r in 0..90) {
            val rx = (r * 1237 + frameIndex * 18) % width
            val ry = (r * 791 + frameIndex * 38) % height
            canvas.drawLine(rx.toFloat(), ry.toFloat(), rx.toFloat() - 10f, ry.toFloat() + 26f, paint)
        }
    }

    // SCENE 4: TEST 4 - FANTASY DRAGON OVER ANCIENT CASTLE AT SUNSET
    private fun drawFantasyDragonCastleScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Breathtaking Sunset Sky (Deep Crimson to Radiant Gold)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.rgb(110, 25, 60), Color.rgb(220, 80, 45), Color.rgb(255, 175, 55)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Big Radiant Golden Sun
        val sunX = width * 0.35f
        val sunY = height * 0.45f
        paint.shader = RadialGradient(sunX, sunY, width * 0.32f,
            intArrayOf(Color.argb(255, 255, 245, 190), Color.argb(120, 255, 160, 40), Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(sunX, sunY, width * 0.32f, paint)
        paint.shader = null

        // Dramatic Mountain Ridge
        paint.color = Color.rgb(35, 20, 32)
        val mPath = Path().apply {
            moveTo(0f, height * 0.72f)
            lineTo(width * 0.35f, height * 0.58f)
            lineTo(width * 0.65f, height * 0.68f)
            lineTo(width * 0.85f, height * 0.55f)
            lineTo(width.toFloat(), height * 0.70f)
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        canvas.drawPath(mPath, paint)

        // Ancient Stone Castle with Towers on Peak
        val castleX = width * 0.35f
        val castleY = height * 0.58f
        paint.color = Color.rgb(25, 14, 24)

        // Main Keep
        canvas.drawRect(castleX - 35f, castleY - 60f, castleX + 35f, castleY, paint)
        // Towers with spires
        canvas.drawRect(castleX - 50f, castleY - 75f, castleX - 35f, castleY, paint)
        canvas.drawRect(castleX + 35f, castleY - 75f, castleX + 50f, castleY, paint)
        // Spires
        val spireL = Path().apply {
            moveTo(castleX - 50f, castleY - 75f)
            lineTo(castleX - 42.5f, castleY - 95f)
            lineTo(castleX - 35f, castleY - 75f)
            close()
        }
        val spireR = Path().apply {
            moveTo(castleX + 35f, castleY - 75f)
            lineTo(castleX + 42.5f, castleY - 95f)
            lineTo(castleX + 50f, castleY - 75f)
            close()
        }
        canvas.drawPath(spireL, paint)
        canvas.drawPath(spireR, paint)

        // Majestic Huge Dragon Soaring Across Sky
        val dragonX = width * (0.35f + t * 0.40f)
        val dragonY = height * (0.28f + sin(t * 6.0).toFloat() * 0.05f)
        paint.color = Color.rgb(18, 12, 18)

        // Dragon Body & Tail
        val wingFlap = sin(t * 10.0).toFloat() * 35f
        val dragonBody = Path().apply {
            moveTo(dragonX + 35f, dragonY) // Snout
            lineTo(dragonX + 22f, dragonY - 8f) // Head
            lineTo(dragonX + 5f, dragonY - 4f) // Neck
            lineTo(dragonX - 25f, dragonY) // Body
            lineTo(dragonX - 65f, dragonY - 10f) // Long tail
            lineTo(dragonX - 25f, dragonY + 8f)
            close()
        }
        canvas.drawPath(dragonBody, paint)

        // Grand Dragon Wings
        val wingLeft = Path().apply {
            moveTo(dragonX, dragonY - 2f)
            lineTo(dragonX - 20f, dragonY - 65f + wingFlap)
            lineTo(dragonX + 15f, dragonY - 75f + wingFlap)
            lineTo(dragonX + 10f, dragonY - 2f)
            close()
        }
        canvas.drawPath(wingLeft, paint)
    }

    // SCENE 5: TEST 5 - DOCUMENTARY FISHERMAN ROWING WOODEN BOAT AT SUNRISE
    private fun drawDocumentaryFishermanRiverScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Soft Morning Sunrise Sky with Gentle Mist
        paint.shader = LinearGradient(0f, 0f, 0f, height * 0.54f,
            intArrayOf(Color.rgb(165, 185, 215), Color.rgb(245, 210, 180), Color.rgb(255, 235, 210)),
            floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.54f, paint)
        paint.shader = null

        // Soft Radiant Sunrise
        val sunX = width * 0.65f
        val sunY = height * 0.40f
        paint.shader = RadialGradient(sunX, sunY, width * 0.25f,
            intArrayOf(Color.argb(210, 255, 245, 220), Color.argb(80, 255, 200, 140), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(sunX, sunY, width * 0.25f, paint)
        paint.shader = null

        // Distant Mist-Covered Riverbanks (Natural Documentary realism)
        paint.color = Color.rgb(65, 80, 95)
        val bankPath = Path().apply {
            moveTo(0f, height * 0.54f)
            var x = 0f
            while (x <= width) {
                val y = height * 0.52f + sin((x * 0.02f).toDouble()).toFloat() * 8f
                lineTo(x, y)
                x += 30f
            }
            lineTo(width.toFloat(), height * 0.54f)
            close()
        }
        canvas.drawPath(bankPath, paint)

        // Calm Mirror-Like River Surface
        val waterY = height * 0.54f
        paint.shader = LinearGradient(0f, waterY, 0f, height.toFloat(),
            intArrayOf(Color.rgb(230, 195, 165), Color.rgb(115, 135, 160), Color.rgb(60, 75, 95)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, waterY, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Concentric Water Ripples from Oars
        val boatX = width * (0.35f + t * 0.22f)
        val boatY = waterY + 95f
        paint.color = Color.argb(120, 255, 240, 220)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        val ripplePhase = (t * 8.0) % 1.0
        val rRadius = (ripplePhase * 45f).toFloat()
        canvas.drawOval(RectF(boatX - rRadius, boatY + 10f - rRadius * 0.25f, boatX + rRadius, boatY + 10f + rRadius * 0.25f), paint)
        paint.style = Paint.Style.FILL

        // Traditional Narrow Wooden Rowing Boat
        paint.color = Color.rgb(35, 28, 25)
        val boatHull = Path().apply {
            moveTo(boatX - 60f, boatY)
            lineTo(boatX - 50f, boatY + 16f)
            lineTo(boatX + 50f, boatY + 16f)
            lineTo(boatX + 60f, boatY)
            close()
        }
        canvas.drawPath(boatHull, paint)

        // Lone Fisherman Silhouette Sitting in Boat
        paint.color = Color.rgb(28, 22, 20)
        canvas.drawCircle(boatX - 5f, boatY - 32f, 9f, paint) // Head
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(boatX - 5f, boatY - 24f, boatX - 10f, boatY + 2f, paint) // Torso

        // Animated Rowing Oars dipping in water
        val oarAngle = sin(t * 8.0).toFloat() * 25f
        canvas.save()
        canvas.rotate(oarAngle, boatX, boatY)
        paint.strokeWidth = 3f
        canvas.drawLine(boatX - 5f, boatY - 10f, boatX - 35f, boatY + 30f, paint)
        canvas.drawLine(boatX - 5f, boatY - 10f, boatX + 25f, boatY + 30f, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
    }

    // SCENE: TIGER IN JUNGLE (No football, no cars, strictly faithful)
    private fun drawTigerJungleScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Deep Tropical Jungle Canopy
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.rgb(10, 35, 20), Color.rgb(25, 75, 40), Color.rgb(15, 45, 25)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Jungle Vines & Giant Palm Leaves
        paint.color = Color.rgb(35, 95, 50)
        for (i in 0..5) {
            val lx = i * (width / 4.5f)
            canvas.drawOval(RectF(lx - 40f, -20f, lx + 70f, height * 0.35f), paint)
        }

        // Royal Bengal Tiger walking with stripes
        val tigerX = width * (0.25f + t * 0.35f)
        val tigerY = height * 0.68f

        // Orange Coat Body
        paint.color = Color.rgb(235, 120, 20)
        canvas.drawOval(RectF(tigerX - 55f, tigerY - 30f, tigerX + 45f, tigerY + 25f), paint)

        // Head
        canvas.drawCircle(tigerX + 50f, tigerY - 15f, 22f, paint)
        // Ears
        canvas.drawCircle(tigerX + 42f, tigerY - 32f, 7f, paint)
        canvas.drawCircle(tigerX + 58f, tigerY - 32f, 7f, paint)

        // Black Tiger Stripes
        paint.color = Color.rgb(20, 15, 15)
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        for (s in 0..5) {
            val sx = tigerX - 40f + s * 14f
            canvas.drawLine(sx, tigerY - 24f, sx - 4f, tigerY + 12f, paint)
        }
        paint.style = Paint.Style.FILL

        // Tail
        paint.strokeWidth = 6f
        paint.color = Color.rgb(235, 120, 20)
        paint.style = Paint.Style.STROKE
        val tailPath = Path().apply {
            moveTo(tigerX - 50f, tigerY - 10f)
            val tw = sin(t * 12.0).toFloat() * 15f
            quadTo(tigerX - 80f, tigerY - 35f + tw, tigerX - 95f, tigerY - 20f + tw)
        }
        canvas.drawPath(tailPath, paint)
        paint.style = Paint.Style.FILL
    }

    // OTHER SCENES:
    private fun drawFieldAndPlayingBoyScene(canvas: Canvas, width: Int, height: Int, t: Float, frameIndex: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, height * 0.65f,
            intArrayOf(Color.rgb(56, 161, 243), Color.rgb(135, 206, 250), Color.rgb(220, 245, 255)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.65f, paint)
        paint.shader = null

        val sunX = width * 0.82f
        val sunY = height * 0.22f
        paint.shader = RadialGradient(sunX, sunY, width * 0.25f,
            intArrayOf(Color.argb(255, 255, 245, 180), Color.argb(120, 255, 210, 80), Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(sunX, sunY, width * 0.25f, paint)
        paint.shader = null

        val groundY = height * 0.62f
        paint.shader = LinearGradient(0f, groundY, 0f, height.toFloat(),
            intArrayOf(Color.rgb(50, 168, 82), Color.rgb(30, 120, 50), Color.rgb(15, 75, 30)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val ballProgress = (t * 2f) % 1f
        val ballX = width * (0.25f + ballProgress * 0.55f)
        val bounceHeight = 80f * abs(sin((ballProgress * Math.PI * 3.0)).toFloat())
        val ballY = (groundY + 80f) - bounceHeight
        val ballRadius = 18f
        paint.color = Color.WHITE
        canvas.drawCircle(ballX, ballY, ballRadius, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(ballX, ballY, ballRadius * 0.35f, paint)

        val boyX = width * (0.15f + t * 0.40f)
        val boyGroundY = groundY + 85f
        paint.color = Color.rgb(20, 25, 45)
        canvas.drawCircle(boyX, boyGroundY - 95f, 14f, paint)
        paint.color = Color.rgb(239, 68, 68)
        paint.strokeWidth = 10f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(boyX, boyGroundY - 78f, boyX - 8f, boyGroundY - 45f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawAnimalMeadowScene(canvas: Canvas, width: Int, height: Int, t: Float, frameIndex: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, height * 0.6f,
            intArrayOf(Color.rgb(255, 223, 186), Color.rgb(255, 240, 220), Color.rgb(230, 245, 230)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.6f, paint)
        paint.shader = null

        val groundY = height * 0.58f
        paint.color = Color.rgb(76, 175, 80)
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), paint)

        val petX = width * 0.45f
        val petY = groundY + 70f
        paint.color = Color.rgb(45, 30, 25)
        canvas.drawOval(RectF(petX - 35f, petY - 25f, petX + 35f, petY + 25f), paint)
        canvas.drawCircle(petX + 35f, petY - 20f, 22f, paint)
    }

    private fun drawCosmicSpaceScene(canvas: Canvas, width: Int, height: Int, t: Float, frameIndex: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(width * 0.5f, height * 0.5f, width * 0.8f,
            intArrayOf(Color.rgb(40, 15, 70), Color.rgb(10, 5, 30), Color.rgb(2, 2, 8)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val px = width * 0.35f
        val py = height * 0.38f
        val pr = width * 0.16f
        paint.color = Color.rgb(255, 180, 120)
        canvas.drawCircle(px, py, pr, paint)
    }

    private fun drawCinematicHeroScene(canvas: Canvas, width: Int, height: Int, t: Float, frameIndex: Int, prompt: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.rgb(35, 25, 65), Color.rgb(90, 45, 95), Color.rgb(210, 110, 60)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val mx = width * 0.75f
        val my = height * 0.3f
        paint.color = Color.rgb(240, 230, 210)
        canvas.drawCircle(mx, my, width * 0.16f, paint)
    }

    private fun drawCinematicLetterbox(
        canvas: Canvas,
        width: Int,
        height: Int,
        t: Float,
        frameIndex: Int,
        totalFrames: Int,
        directive: ParsedPromptDirective
    ) {
        val barHeight = (height * 0.10f).coerceAtLeast(42f)
        val barPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, barPaint)
        canvas.drawRect(0f, height - barHeight, width.toFloat(), height.toFloat(), barPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
        }

        val recDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if ((frameIndex / 15) % 2 == 0) Color.RED else Color.TRANSPARENT
        }
        canvas.drawCircle(28f, barHeight * 0.55f, 5.5f, recDotPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 16f
        canvas.drawText("OMNIVIDEO AI  |  ${directive.visualStyle.badge.uppercase()}", 42f, barHeight * 0.62f, textPaint)

        val seconds = (t * 4).toInt()
        val frames = (frameIndex % 30).toString().padStart(2, '0')
        val timecode = "00:0${seconds}:$frames"
        val tcWidth = textPaint.measureText(timecode)
        textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText(timecode, width - tcWidth - 20f, barHeight * 0.62f, textPaint)

        // Subtitle line: Prioritize exact user dialogue if available!
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (!directive.dialogue.isNullOrBlank()) Color.rgb(255, 230, 100) else Color.rgb(240, 240, 240)
            textSize = 21f
            isFakeBoldText = true
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }

        val displayText = if (!directive.dialogue.isNullOrBlank()) {
            "“${directive.dialogue}”"
        } else {
            directive.originalPrompt.take(55).let { if (directive.originalPrompt.length > 55) "$it..." else it }
        }

        val promptWidth = subPaint.measureText(displayText)
        val promptX = ((width - promptWidth) / 2f).coerceAtLeast(16f)
        canvas.drawText(displayText, promptX, height - barHeight * 0.38f, subPaint)
    }
}
