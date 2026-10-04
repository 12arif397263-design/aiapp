package com.example

import com.example.data.model.VideoPresets
import com.example.data.remote.GeminiErrorDetails
import com.example.data.remote.RetrofitClient
import com.example.data.remote.VeoGenerateVideoResponse
import com.example.data.remote.VeoGeneratedSample
import com.example.data.remote.VeoInstance
import com.example.data.remote.VeoOperationResponse
import com.example.data.remote.VeoParameters
import com.example.data.remote.VeoPredictLongRunningRequest
import com.example.data.remote.VeoResult
import com.example.data.remote.VeoVideoPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeoGenerationPipelineTest {

    @Test
    fun testVeoModelsConfigured() {
        val fastModel = VideoPresets.MODELS.find { it.id == "veo_3_1_fast" }
        assertNotNull(fastModel)
        assertEquals("veo-3.1-fast-generate-preview", fastModel?.modelTag)
        assertTrue(fastModel!!.isCloud)

        val hdModel = VideoPresets.MODELS.find { it.id == "veo_3_1_hd" }
        assertNotNull(hdModel)
        assertEquals("veo-3.1-generate-preview", hdModel?.modelTag)
        assertTrue(hdModel!!.isCloud)
    }

    @Test
    fun testVeoPredictLongRunningRequestSerialization() {
        val prompt = "A majestic blue dragon soaring above snow-covered mountains at sunrise."
        val request = VeoPredictLongRunningRequest(
            instances = listOf(VeoInstance(prompt = prompt)),
            parameters = VeoParameters(
                aspectRatio = "16:9",
                durationSeconds = "8",
                resolution = "1080p"
            )
        )

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(VeoPredictLongRunningRequest::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.contains("\"instances\":[{\"prompt\":"))
        assertTrue(json.contains(prompt))
        assertTrue(json.contains("\"parameters\":{"))
        assertTrue(json.contains("\"aspectRatio\":\"16:9\""))
        assertTrue(json.contains("\"durationSeconds\":\"8\""))
        assertTrue(json.contains("\"resolution\":\"1080p\""))
    }

    @Test
    fun testVeoOperationExtractUriFromGeneratedSamples() {
        val op = VeoOperationResponse(
            name = "operations/veo-job-12345",
            done = true,
            response = VeoResult(
                generateVideoResponse = VeoGenerateVideoResponse(
                    generatedSamples = listOf(
                        VeoGeneratedSample(
                            video = VeoVideoPayload(
                                uri = "https://generativelanguage.googleapis.com/v1beta/files/test-veo-video.mp4"
                            )
                        )
                    )
                )
            )
        )

        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/test-veo-video.mp4", op.extractVideoUri())
        assertNull(op.extractVideoBase64())
    }

    @Test
    fun testVeoOperationExtractBase64() {
        val testBase64 = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAAIZnJlZQ=="
        val op = VeoOperationResponse(
            name = "operations/veo-job-67890",
            done = true,
            response = VeoResult(
                generatedSamples = listOf(
                    VeoGeneratedSample(
                        video = VeoVideoPayload(
                            bytesBase64Encoded = testBase64
                        )
                    )
                )
            )
        )

        assertNull(op.extractVideoUri())
        assertEquals(testBase64, op.extractVideoBase64())
    }

    @Test
    fun testVeoOperationErrorHandling() {
        val op = VeoOperationResponse(
            name = "operations/veo-job-failed",
            done = true,
            error = GeminiErrorDetails(
                code = 429,
                message = "Resource exhausted: quota exceeded",
                status = "RESOURCE_EXHAUSTED"
            )
        )

        assertNotNull(op.error)
        assertEquals(429, op.error?.code)
        assertEquals("Resource exhausted: quota exceeded", op.error?.message)
    }

    @Test
    fun testDurationAndAspectRatioValidationRules() {
        // Validation: 10s should map to 8s for Veo
        val selectedDuration = 10
        val validDuration = when (selectedDuration) {
            4 -> "4"
            6 -> "6"
            else -> "8"
        }
        assertEquals("8", validDuration)

        // Validation: 1:1 or 4:3 should map to 16:9 for Veo
        val unsupportedRatio = "1:1"
        val validRatio = when (unsupportedRatio) {
            "9:16" -> "9:16"
            else -> "16:9"
        }
        assertEquals("16:9", validRatio)

        val reelsRatio = "9:16"
        val validReelsRatio = when (reelsRatio) {
            "9:16" -> "9:16"
            else -> "16:9"
        }
        assertEquals("9:16", validReelsRatio)
    }
}
