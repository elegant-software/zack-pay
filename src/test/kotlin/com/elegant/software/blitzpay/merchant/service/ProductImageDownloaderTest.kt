package com.elegant.software.blitzpay.merchant.service

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetAddress
import java.net.InetSocketAddress

class ProductImageDownloaderTest {

    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/latte.png") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, pngBytes.size.toLong())
            exchange.responseBody.use { it.write(pngBytes) }
        }
        createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/latte.png")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        createContext("/loop") { exchange ->
            exchange.responseHeaders.add("Location", "/loop")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        createContext("/text") { exchange ->
            val body = "not an image".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        createContext("/huge.png") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                out.write(pngBytes)
                val chunk = ByteArray(64 * 1024)
                repeat(((ProductImagePolicy.MaxSourceBytes / chunk.size) + 2).toInt()) { out.write(chunk) }
            }
        }
        start()
    }
    private val baseUrl = "http://127.0.0.1:${server.address.port}"
    private val downloader = ProductImageDownloader(WebClient.builder(), allowPrivateAddresses = true)

    @AfterEach
    fun tearDown() = server.stop(0)

    @Test
    fun `downloads image and detects content type from bytes`() {
        val upload = downloader.download("$baseUrl/latte.png")

        assertEquals("image/png", upload.contentType)
        assertArrayEquals(pngBytes, upload.bytes)
    }

    @Test
    fun `follows redirects`() {
        assertEquals("image/png", downloader.download("$baseUrl/redirect").contentType)
    }

    @Test
    fun `rejects too many redirects`() {
        assertThrows<IllegalArgumentException> { downloader.download("$baseUrl/loop") }
    }

    @Test
    fun `rejects non-image content`() {
        assertThrows<IllegalArgumentException> { downloader.download("$baseUrl/text") }
    }

    @Test
    fun `rejects sources larger than the download limit`() {
        val ex = assertThrows<IllegalArgumentException> { downloader.download("$baseUrl/huge.png") }
        assertTrue(ex.message!!.contains("15 MB"), ex.message)
    }

    @Test
    fun `rejects HTTP errors`() {
        assertThrows<IllegalArgumentException> { downloader.download("$baseUrl/missing.png") }
    }

    @Test
    fun `rejects private addresses by default`() {
        val strict = ProductImageDownloader(WebClient.builder())

        assertThrows<IllegalArgumentException> { strict.download("$baseUrl/latte.png") }
        assertThrows<IllegalArgumentException> { strict.download("http://169.254.169.254/latest/meta-data") }
        assertThrows<IllegalArgumentException> { strict.download("http://10.0.0.1/x.png") }
    }

    @Test
    fun `rejects unsupported schemes`() {
        assertThrows<IllegalArgumentException> { downloader.download("file:///etc/passwd") }
        assertThrows<IllegalArgumentException> { downloader.download("ftp://example.com/x.png") }
    }

    @Test
    fun `detects jpeg and webp signatures`() {
        assertEquals("image/jpeg", ProductImageDownloader.detectContentType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("image/webp", ProductImageDownloader.detectContentType("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
    }
}
