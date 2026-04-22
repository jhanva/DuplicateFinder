package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayTensorRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class OverlayCleaningModelRepositoryImplTest {

    @Test
    fun `download model stores file and metadata and reuses local copy`() {
        val modelDir = Files.createTempDirectory("overlay-cleaning-model").toFile()
        val server = SimpleHttpServer(
            responses = mapOf(
                "/AOT-GAN.onnx" to byteArrayOf(1, 2, 3, 4)
            )
        )

        try {
            val repository = OverlayCleaningModelRepositoryImpl(
                modelUrl = "${server.baseUrl}/AOT-GAN.onnx",
                modelDir = modelDir,
                bundledModelSource = FakeBundledModelSource()
            )

            val downloaded = runBlocking { repository.downloadModel() }
            val active = runBlocking { repository.getActiveModelInfo() }

            val modelInfo = downloaded.getOrNull()
            assertNotNull(downloaded.exceptionOrNull()?.toString(), modelInfo)
            assertEquals("overlay-cleaning-aot-gan-v1", modelInfo?.bundleVersion)
            assertEquals(OverlayModelRuntime.ONNX_RUNTIME_ANDROID, modelInfo?.runtime)
            assertEquals("AOT-GAN.onnx", modelInfo?.inpainterPath)
            assertEquals(512, modelInfo?.inputSizeInpainter)
            assertEquals(OverlayTensorRange.NEGATIVE_ONE_TO_ONE, modelInfo?.onnx?.inpainter?.tensorRange)
            assertEquals(modelInfo, active)
            assertEquals(true, File(modelDir, "AOT-GAN.onnx").exists())
            assertEquals(true, File(modelDir, "cleaning-model.json").exists())
        } finally {
            server.close()
            modelDir.deleteRecursively()
        }
    }

    @Test
    fun `get active model returns null when metadata exists but file is missing`() {
        val modelDir = Files.createTempDirectory("overlay-cleaning-model-metadata").toFile()
        modelDir.resolve("cleaning-model.json").writeText(
            """
            {
              "bundleVersion": "overlay-cleaning-aot-gan-v1",
              "runtime": "onnxruntime-android",
              "inpainterPath": "AOT-GAN.onnx",
              "inputSizeInpainter": 512,
              "modelUrl": "https://example.com/AOT-GAN.onnx",
              "onnx": {
                "inpainter": {
                  "imageInputName": "image",
                  "maskInputName": "mask",
                  "outputName": "output",
                  "inputFormat": "image_and_mask",
                  "tensorRange": "negative_one_to_one"
                }
              }
            }
            """.trimIndent()
        )

        val repository = OverlayCleaningModelRepositoryImpl(
            modelUrl = "",
            modelDir = modelDir,
            bundledModelSource = FakeBundledModelSource()
        )

        val active = runBlocking { repository.getActiveModelInfo() }

        assertEquals(null, active)
        modelDir.deleteRecursively()
    }

    @Test
    fun `get active model materializes bundled asset for offline use`() {
        val modelDir = Files.createTempDirectory("overlay-cleaning-bundled-model").toFile()
        val repository = OverlayCleaningModelRepositoryImpl(
            modelUrl = "",
            modelDir = modelDir,
            bundledModelSource = FakeBundledModelSource(bytes = byteArrayOf(9, 8, 7, 6))
        )

        val active = runBlocking { repository.getActiveModelInfo() }

        assertNotNull(active)
        assertEquals("AOT-GAN.onnx", active?.inpainterPath)
        assertEquals("painted_image", active?.onnx?.inpainter?.outputName)
        assertEquals(true, File(modelDir, "AOT-GAN.onnx").exists())
        assertEquals(true, File(modelDir, "cleaning-model.json").exists())
        modelDir.deleteRecursively()
    }

    private class FakeBundledModelSource(
        private val bytes: ByteArray? = null
    ) : OverlayCleaningBundledModelSource {
        override val fileName: String = "AOT-GAN.onnx"
        override val sourceId: String = "asset://overlay-cleaning/AOT-GAN.onnx"

        override fun exists(): Boolean = bytes != null

        override fun open(): InputStream? = bytes?.let(::ByteArrayInputStream)
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
