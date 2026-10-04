package com.example

import com.example.data.model.VideoPresets
import com.example.data.remote.GeminiErrorDetails
import com.example.data.remote.VeoGenerateVideoResponse
import com.example.data.remote.VeoGeneratedVideo
import com.example.data.remote.VeoOperationResponse
import com.example.data.remote.VeoResult
import com.example.data.remote.VeoVideoPayload
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
    fun testVeoOperationExtractUriFromNestedResponse() {
        val op = VeoOperationResponse(
            name = "operations/veo-job-12345",
            done = true,
            response = VeoResult(
                generateVideoResponse = VeoGenerateVideoResponse(
                    generatedVideos = listOf(
                        VeoGeneratedVideo(
                            video = VeoVideoPayload(
                                uri = "https://generativelanguage.googleapis.com/v1beta/files/test-video.mp4"
                            )
                        )
                    )
                )
            )
        )

        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/test-video.mp4", op.extractVideoUri())
        assertNull(op.extractVideoBase64())
    }

    @Test
    fun testVeoOperationExtractBase64() {
        val testBase64 = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAAIZnJlZQ=="
        val op = VeoOperationResponse(
            name = "operations/veo-job-67890",
            done = true,
            response = VeoResult(
                generatedVideos = listOf(
                    VeoGeneratedVideo(
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
}
