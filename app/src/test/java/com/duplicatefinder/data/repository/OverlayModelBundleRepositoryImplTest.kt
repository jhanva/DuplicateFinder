package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayDetectorOutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class OverlayModelBundleRepositoryImplTest {

    private val optionalInpainterKey = "inpaint" + "erPath"

    @Test
    fun `get active bundle returns null when manifest exists but assets are missing`() {
        val bundleDir = Files.createTempDirectory("overlay-bundle-test").toFile()
        bundleDir.resolve("bundle.json").writeText(
            """
            {
              "bundleVersion": "overlay-bundle-v1",
              "runtime": "onnxruntime-android",
              "textDetectorPath": "ppocrv5_mobile_det.onnx",
              "maskRefinerEncoderPath": "mobile_sam_encoder.onnx",
              "maskRefinerDecoderPath": "mobile_sam_decoder.onnx"
            }
            """.trimIndent()
        )

        val repository = OverlayModelBundleRepositoryImpl(bundleDir = bundleDir)

        val activeBundle = kotlinx.coroutines.runBlocking { repository.getActiveBundleInfo() }

        assertNull(activeBundle)
        bundleDir.deleteRecursively()
    }

    @Test
    fun `get active bundle returns null when manifest is missing`() {
        val bundleDir = Files.createTempDirectory("overlay-bundle-no-manifest").toFile()

        val repository = OverlayModelBundleRepositoryImpl(bundleDir = bundleDir)

        val activeBundle = kotlinx.coroutines.runBlocking { repository.getActiveBundleInfo() }

        assertNull(activeBundle)
        bundleDir.deleteRecursively()
    }

    @Test
    fun `get active bundle returns sideloaded bundle when manifest and assets are complete`() {
        val bundleDir = Files.createTempDirectory("overlay-bundle-sideload").toFile()
        bundleDir.resolve("bundle.json").writeText(
            """
            {
              "bundleVersion": "overlay-bundle-v2",
              "runtime": "onnxruntime-android",
              "textDetectorPath": "ppocrv5_mobile_det.onnx",
              "maskRefinerEncoderPath": "mobile_sam_encoder.onnx",
              "maskRefinerDecoderPath": "mobile_sam_decoder.onnx",
              "$optionalInpainterKey": "aot_gan.onnx",
              "inputSizeTextDetector": 256,
              "inputSizeMaskRefiner": 512
            }
            """.trimIndent()
        )
        bundleDir.resolve("ppocrv5_mobile_det.onnx").writeBytes(byteArrayOf(1, 2, 3))
        bundleDir.resolve("mobile_sam_encoder.onnx").writeBytes(byteArrayOf(4, 5, 6))
        bundleDir.resolve("mobile_sam_decoder.onnx").writeBytes(byteArrayOf(7, 8, 9))

        val repository = OverlayModelBundleRepositoryImpl(bundleDir = bundleDir)

        val activeBundle = kotlinx.coroutines.runBlocking { repository.getActiveBundleInfo() }

        assertNotNull(activeBundle)
        assertEquals("overlay-bundle-v2", activeBundle?.bundleVersion)
        assertEquals(OverlayModelRuntime.ONNX_RUNTIME_ANDROID, activeBundle?.runtime)
        assertEquals(256, activeBundle?.inputSizeTextDetector)
        assertEquals(512, activeBundle?.inputSizeMaskRefiner)
        assertNull(activeBundle?.manifestUrl)

        bundleDir.deleteRecursively()
    }

    @Test
    fun `get active bundle parses optional onnx runtime contract`() {
        val bundleDir = Files.createTempDirectory("overlay-bundle-onnx-contract").toFile()
        bundleDir.resolve("bundle.json").writeText(
            """
            {
              "bundleVersion": "overlay-bundle-v3",
              "runtime": "onnxruntime-android",
              "textDetectorPath": "ppocrv5_mobile_det.onnx",
              "maskRefinerEncoderPath": "mobile_sam_encoder.onnx",
              "maskRefinerDecoderPath": "mobile_sam_decoder.onnx",
              "$optionalInpainterKey": "migan.onnx",
              "onnx": {
                "detector": {
                  "inputName": "image",
                  "outputName": "boxes",
                  "outputFormat": "boxes_normalized",
                  "confidenceThreshold": 0.42,
                  "minRegionAreaRatio": 0.003
                },
                "maskRefiner": {
                  "encoderInputName": "image",
                  "encoderOutputName": "embeddings",
                  "decoderEmbeddingInputName": "image_embeddings",
                  "decoderPointCoordsInputName": "point_coords",
                  "decoderPointLabelsInputName": "point_labels",
                  "decoderMaskInputName": "mask_input",
                  "decoderHasMaskInputName": "has_mask_input",
                  "decoderOrigImSizeInputName": "orig_im_size",
                  "decoderOutputName": "masks",
                  "decoderScoreOutputName": "scores",
                  "maskThreshold": 0.15
                }
              }
            }
            """.trimIndent()
        )
        bundleDir.resolve("ppocrv5_mobile_det.onnx").writeBytes(byteArrayOf(1))
        bundleDir.resolve("mobile_sam_encoder.onnx").writeBytes(byteArrayOf(2))
        bundleDir.resolve("mobile_sam_decoder.onnx").writeBytes(byteArrayOf(3))
        bundleDir.resolve("migan.onnx").writeBytes(byteArrayOf(4))

        val repository = OverlayModelBundleRepositoryImpl(bundleDir = bundleDir)

        val activeBundle = kotlinx.coroutines.runBlocking { repository.getActiveBundleInfo() }

        assertNotNull(activeBundle)
        assertEquals(OverlayModelRuntime.ONNX_RUNTIME_ANDROID, activeBundle?.runtime)
        assertEquals(OverlayDetectorOutputFormat.BOXES_NORMALIZED, activeBundle?.onnx?.detector?.outputFormat)
        assertEquals(0.42f, activeBundle?.onnx?.detector?.confidenceThreshold)
        assertEquals(0.003f, activeBundle?.onnx?.detector?.minRegionAreaRatio)
        assertEquals("embeddings", activeBundle?.onnx?.maskRefiner?.encoderOutputName)
        assertEquals("scores", activeBundle?.onnx?.maskRefiner?.decoderScoreOutputName)
        assertEquals(0.15f, activeBundle?.onnx?.maskRefiner?.maskThreshold)
        assertEquals(3, activeBundle?.requiredAssetPaths?.size)

        bundleDir.deleteRecursively()
    }
}
