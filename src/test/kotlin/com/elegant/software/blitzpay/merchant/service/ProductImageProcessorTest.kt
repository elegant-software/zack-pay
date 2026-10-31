package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.ProductImageQualityProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO

class ProductImageProcessorTest {

    private val processor = ProductImageProcessor(ProductImageQualityProperties())

    @Test
    fun `passes good images through untouched`() {
        val bytes = encode(noise(800, 600, BufferedImage.TYPE_INT_RGB), "png")

        val result = processor.prepare(RawProductImage("image/png", bytes))

        assertEquals("image/png", result.contentType)
        assertSame(bytes, result.bytes)
    }

    @Test
    fun `crops to the requested region keeping the format`() {
        val bytes = encode(noise(1200, 800, BufferedImage.TYPE_INT_RGB), "png")

        val result = processor.prepare(RawProductImage("image/png", bytes), ProductImageCrop(100, 50, 600, 500))

        assertEquals("image/png", result.contentType)
        val decoded = decode(result.bytes)
        assertEquals(600, decoded.width)
        assertEquals(500, decoded.height)
    }

    @Test
    fun `rejects crop outside image bounds`() {
        val bytes = encode(noise(800, 600, BufferedImage.TYPE_INT_RGB), "png")

        assertThrows<IllegalArgumentException> {
            processor.prepare(RawProductImage("image/png", bytes), ProductImageCrop(500, 100, 400, 450))
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
        val bytes = webp(800, 600) + ByteArray((ProductImagePolicy.MaxBytes + 1).toInt())

        assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/webp", bytes)) }
    }

    @Test
    fun `rejects sources above the source limit`() {
        val bytes = ByteArray((ProductImagePolicy.MaxSourceBytes + 1).toInt())

        assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/png", bytes)) }
    }

    @Test
    fun `rejects low resolution images`() {
        val bytes = encode(noise(300, 200, BufferedImage.TYPE_INT_RGB), "png")

        val ex = assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/png", bytes)) }
        assertTrue(ex.message!!.contains("resolution too low"), ex.message)
    }

    @Test
    fun `rejects low resolution crops of large images`() {
        val bytes = encode(noise(1200, 800, BufferedImage.TYPE_INT_RGB), "png")

        val ex = assertThrows<IllegalArgumentException> {
            processor.prepare(RawProductImage("image/png", bytes), ProductImageCrop(0, 0, 200, 200))
        }
        assertTrue(ex.message!!.contains("resolution too low"), ex.message)
    }

    @Test
    fun `rejects extreme aspect ratios`() {
        val bytes = encode(noise(2500, 500, BufferedImage.TYPE_INT_RGB), "png")

        val ex = assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/png", bytes)) }
        assertTrue(ex.message!!.contains("aspect ratio"), ex.message)
    }

    @Test
    fun `rejects blurry images`() {
        val bytes = encode(gradient(800, 600), "png")

        val ex = assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/png", bytes)) }
        assertTrue(ex.message!!.contains("blurry"), ex.message)
    }

    @Test
    fun `rejects blank images`() {
        val bytes = encode(solid(800, 600, Color(200, 120, 40)), "png")

        val ex = assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/png", bytes)) }
        assertTrue(ex.message!!.contains("blank"), ex.message)
    }

    @Test
    fun `rejects dark images`() {
        val lenient = ProductImageProcessor(ProductImageQualityProperties(minSharpness = 0.0, minContrast = 0.0))
        val bytes = encode(noise(800, 600, BufferedImage.TYPE_INT_RGB, maxChannel = 20), "png")

        val ex = assertThrows<IllegalArgumentException> { lenient.prepare(RawProductImage("image/png", bytes)) }
        assertTrue(ex.message!!.contains("too dark"), ex.message)
    }

    @Test
    fun `accepts a sharp product on a plain white background`() {
        val image = solid(1000, 800, Color.WHITE)
        val product = noise(300, 250, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { drawImage(product, 350, 275, null); dispose() }

        processor.prepare(RawProductImage("image/png", encode(image, "png")))
    }

    @Test
    fun `checks webp resolution from the header`() {
        val ex = assertThrows<IllegalArgumentException> { processor.prepare(RawProductImage("image/webp", webp(300, 200))) }
        assertTrue(ex.message!!.contains("resolution too low"), ex.message)

        processor.prepare(RawProductImage("image/webp", webp(800, 600)))
    }

    @Test
    fun `skips quality checks when disabled`() {
        val disabled = ProductImageProcessor(ProductImageQualityProperties(enabled = false))
        val bytes = encode(solid(100, 50, Color.GRAY), "png")

        assertSame(bytes, disabled.prepare(RawProductImage("image/png", bytes)).bytes)
    }

    private fun noise(width: Int, height: Int, type: Int, maxChannel: Int = 255): BufferedImage {
        val random = Random(42)
        return BufferedImage(width, height, type).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val alpha = if (type == BufferedImage.TYPE_INT_ARGB) random.nextInt(256) else 255
                val r = random.nextInt(maxChannel + 1)
                val g = random.nextInt(maxChannel + 1)
                val b = random.nextInt(maxChannel + 1)
                setRGB(x, y, (alpha shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
    }

    private fun gradient(width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val value = x * 255 / width
                setRGB(x, y, (value shl 16) or (value shl 8) or value)
            }
        }

    private fun solid(width: Int, height: Int, color: Color): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply { this.color = color; fillRect(0, 0, width, height); dispose() }
        }

    /** Minimal RIFF/VP8X header carrying only the canvas size. */
    private fun webp(width: Int, height: Int): ByteArray {
        val header = ByteArray(30)
        "RIFF".toByteArray().copyInto(header, 0)
        "WEBP".toByteArray().copyInto(header, 8)
        "VP8X".toByteArray().copyInto(header, 12)
        fun u24(value: Int, offset: Int) {
            header[offset] = value.toByte()
            header[offset + 1] = (value shr 8).toByte()
            header[offset + 2] = (value shr 16).toByte()
        }
        u24(width - 1, 24)
        u24(height - 1, 27)
        return header
    }

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

    private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
}
