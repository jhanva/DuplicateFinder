package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayDetectorOutputFormat
import com.duplicatefinder.domain.repository.OverlayInpainterInputFormat
import com.duplicatefinder.domain.repository.OverlayTensorRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class OverlayModelBundleRepositoryImplTest {

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
              "maskRefinerDecoderPath": "mobile_sam_decoder.onnx",
              "inpainterPath": "aot_gan.onnx"
            }
            """.trimIndent()
        )

        val repository = OverlayModelBundleRepositoryImpl(
            manifestUrl = "https://example.com/bundle.json",
            bundleDir = bundleDir
        )

        val activeBundle = kotlinx.coroutines.runBlocking { repository.getActiveBundleInfo() }

        assertNull(activeBundle)
        bundleDir.deleteRecursively()
    }

    @Test
    fun `download bundle stores assets and activates local bundle`() {
        val bundleDir = Files.createTempDirectory("overlay-bundle-download").toFile()
        val server = SimpleHttpServer(
            responses = mapOf(
                "/bundle.json" to """
                    {
                      "bundleVersion": "overlay-bundle-v2",
                      "runtime": "onnxruntime-android",
                      "textDetectorPath": "ppocrv5_mobile_det.onnx",
                      "maskRefinerEncoderPath": "mobile_sam_encoder.onnx",
                      "maskRefinerDecoderPath": "mobile_sam_decoder.onnx",
                      "inpainterPath": "aot_gan.onnx",
                      "inputSizeTextDetector": 256,
                      "inputSizeMaskRefiner": 512,
                      "inputSizeInpainter": 1024
                    }
                """.trimIndent().toByteArray(),
                "/ppocrv5_mobile_det.onnx" to byteArrayOf(1, 2, 3),
                "/mobile_sam_encoder.onnx" to byteArrayOf(4, 5, 6),
                "/mobile_sam_decoder.onnx" to byteArrayOf(7, 8, 9),
                "/aot_gan.onnx" to byteArrayOf(10, 11, 12)
            )
        )

        try {
            val repository = OverlayModelBundleRepositoryImpl(
                manifestUrl = "${server.baseUrl}/bundle.json",
                bundleDir = bundleDir
            )

            val downloaded = kotlinx.coroutines.runBlocking { repository.downloadBundle() }
            val active = kotlinx.coroutines.runBlocking { repository.getActiveBundleInfo() }

            val bundleInfo = downloaded.getOrNull()
            assertNotNull(downloaded.exceptionOrNull()?.toString(), bundleInfo)
            assertEquals("overlay-bundle-v2", bundleInfo?.bundleVersion)
            assertEquals(OverlayModelRuntime.ONNX_RUNTIME_ANDROID, bundleInfo?.runtime)
            assertEquals(bundleInfo, active)
            assertEquals(true, File(bundleDir, "bundle.json").exists())
            assertEquals(true, File(bundleDir, "ppocrv5_mobile_det.onnx").exists())
            assertEquals(true, File(bundleDir, "mobile_sam_encoder.onnx").exists())
            assertEquals(true, File(bundleDir, "mobile_sam_decoder.onnx").exists())
            assertEquals(true, File(bundleDir, "aot_gan.onnx").exists())
        } finally {
            server.close()
            bundleDir.deleteRecursively()
        }
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
              "inpainterPath": "migan.onnx",
              "onnx": {
                "detector": {
                  "inputName": "image",
                  "outputName": "boxes",
                  "outputFormat": "boxes_normalized",
                  "confidenceThreshold": 0.42,
                  "minRegionAreaRatio": 0.003
                },
                "inpainter": {
                  "imageInputName": "image",
                  "maskInputName": "mask",
                  "outputName": "inpainted",
                  "inputFormat": "image_and_mask",
                  "tensorRange": "negative_one_to_one"
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

        val repository = OverlayModelBundleRepositoryImpl(
            manifestUrl = "https://example.com/bundle.json",
            bundleDir = bundleDir
        )

        val activeBundle = kotlinx.coroutines.runBlocking { repository.getActiveBundleInfo() }

        assertNotNull(activeBundle)
        assertEquals(OverlayModelRuntime.ONNX_RUNTIME_ANDROID, activeBundle?.runtime)
        assertEquals(OverlayDetectorOutputFormat.BOXES_NORMALIZED, activeBundle?.onnx?.detector?.outputFormat)
        assertEquals(0.42f, activeBundle?.onnx?.detector?.confidenceThreshold)
        assertEquals(0.003f, activeBundle?.onnx?.detector?.minRegionAreaRatio)
        assertEquals(OverlayInpainterInputFormat.IMAGE_AND_MASK, activeBundle?.onnx?.inpainter?.inputFormat)
        assertEquals(OverlayTensorRange.NEGATIVE_ONE_TO_ONE, activeBundle?.onnx?.inpainter?.tensorRange)
        assertEquals("inpainted", activeBundle?.onnx?.inpainter?.outputName)
        assertEquals("embeddings", activeBundle?.onnx?.maskRefiner?.encoderOutputName)
        assertEquals("scores", activeBundle?.onnx?.maskRefiner?.decoderScoreOutputName)
        assertEquals(0.15f, activeBundle?.onnx?.maskRefiner?.maskThreshold)

        bundleDir.deleteRecursively()
    }

    private class SimpleHttpServer(
        private val responses: Map<String, ByteArray>
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        private val running = AtomicBoolean(true)
        private val thread = thread(start = true, isDaemon = true) {
            while (running.get()) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    null
                } ?: continue

                socket.use(::handleConnection)
            }
        }

        val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"

        override fun close() {
            running.set(false)
            serverSocket.close()
            thread.join(2_000)
        }

        private fun handleConnection(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (reader.readLine()?.isNotEmpty() == true) {
                // Drain headers.
            }

            val path = requestLine.split(' ').getOrNull(1) ?: "/"
            val body = responses[path]
            val output = socket.getOutputStream()
            if (body == null) {
                output.write(
                    (
                        "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray()
                )
                output.flush()
                return
            }

            output.write(
                (
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray()
            )
            output.write(body)
            output.flush()
        }
    }
}
