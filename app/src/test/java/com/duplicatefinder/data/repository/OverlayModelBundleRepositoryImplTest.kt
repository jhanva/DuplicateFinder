package com.duplicatefinder.data.repository

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
              "detectorStage1Path": "stage1.tflite",
              "detectorStage2Path": "stage2.tflite",
              "inpainterPath": "inpainter.tflite"
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
                      "detectorStage1Path": "stage1.tflite",
                      "detectorStage2Path": "stage2.tflite",
                      "inpainterPath": "inpainter.tflite",
                      "inputSizeStage1": 256,
                      "inputSizeStage2": 512,
                      "inputSizeInpainter": 1024
                    }
                """.trimIndent().toByteArray(),
                "/stage1.tflite" to byteArrayOf(1, 2, 3),
                "/stage2.tflite" to byteArrayOf(4, 5, 6),
                "/inpainter.tflite" to byteArrayOf(7, 8, 9)
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
            assertEquals(bundleInfo, active)
            assertEquals(true, File(bundleDir, "bundle.json").exists())
            assertEquals(true, File(bundleDir, "stage1.tflite").exists())
            assertEquals(true, File(bundleDir, "stage2.tflite").exists())
            assertEquals(true, File(bundleDir, "inpainter.tflite").exists())
        } finally {
            server.close()
            bundleDir.deleteRecursively()
        }
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
