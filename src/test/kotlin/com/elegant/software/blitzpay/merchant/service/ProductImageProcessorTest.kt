package com.elegant.software.blitzpay.merchant.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO

class ProductImageProcessorTest {

    private val processor = ProductImageProcessor()

    @Test
    fun `passes small images through untouched`() {
        val bytes = encode(noise(64, 48, BufferedImage.TYPE_INT_RGB), "png")

        val result = processor.prepare(RawProductImage("image/png", bytes))

        assertEquals("image/png", result.contentType)
        assertSame(bytes, result.bytes)
    }

    @Test
    fun `crops to the requested region keeping the format`() {
        val bytes = encode(noise(200, 100, BufferedImage.TYPE_INT_RGB), "png")

        val result = processor.prepare(RawProductImage("image/png", bytes), ProductImageCrop(10, 20, 50, 40))

        assertEquals("image/png", result.contentType)
        val decoded = decode(result.bytes)
        assertEquals(50, decoded.width)
        assertEquals(40, decoded.height)
    }

    @Test
    fun `rejects crop outside image bounds`() {
        val bytes = encode(noise(100, 100, BufferedImage.TYPE_INT_RGB), "png")

        assertThrows<IllegalArgumentException> {
            processor.prepare(RawProductImage("image/png", bytes), ProductImageCrop(50, 50, 80, 10))
        }
    }

    @Test
    fun `shrinks oversized opaque images to a JPEG within the storage limit`() {
        val bytes = encode(noise(2200, 1600, BufferedImage.TYPE_INT_RGB), "png")
        assertTrue(bytes.size > ProductImagePolicy.MaxBytes, "fixture must exceed 5 MB, was ${bytes.size}")

        val result = processor.prepare(RawProductImage("image/png", bytes))

        assertEquals("image/jpeg", result.contentType)
        assertTrue(result.bytes.size <= ProductImagePolicy.MaxBytes)
        val decoded = decode(result.bytes)
        assertEquals(2200.0 / 1600, decoded.width.toDouble() / decoded.height, 0.01)
    }

    @Test
    fun `keeps PNG for oversized transparent images`() {
        val bytes = encode(noise(2000, 1500, BufferedImage.TYPE_INT_ARGB), "png")
        assertTrue(bytes.size > ProductImagePolicy.MaxBytes, "fixture must exceed 5 MB, was ${bytes.size}")

        val result = processor.prepare(RawProductImage("image/png", bytes))

        assertEquals("image/png", result.contentType)
        assertTrue(result.bytes.size <= ProductImagePolicy.MaxBytes)
    }

    @Test
    fun `rejects oversized webp because it cannot be resized`() {
        val bytes = "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray() + ByteArray((ProductImagePolicy.MaxBytes + 1).toInt())

        assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/webp", bytes)) }
    }

    @Test
    fun `rejects sources above the source limit`() {
        val bytes = ByteArray((ProductImagePolicy.MaxSourceBytes + 1).toInt())

        assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/png", bytes)) }
    }

    private fun noise(width: Int, height: Int, type: Int): BufferedImage {
        val random = Random(42)
        return BufferedImage(width, height, type).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, random.nextInt())
        }
    }

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

    private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
}
