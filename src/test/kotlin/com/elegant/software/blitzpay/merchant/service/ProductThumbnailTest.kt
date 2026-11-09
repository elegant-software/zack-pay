package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.ProductImageQualityProperties
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.*

class ProductThumbnailTest {
    private val processor = ProductImageProcessor(ProductImageQualityProperties())

    @Test
    fun `thumbnail is bounded while the full detail source stays intact`() {
        val bytes = fixture("catalog-detail.png")
        val source = ProductImageUpload("image/png", bytes)
        val original = bytes.copyOf()
        val result = assertNotNull(processor.thumbnail(source))
        val decoded = ImageIO.read(ByteArrayInputStream(result.bytes))
        assertEquals(256, decoded.width)
        assertEquals(171, decoded.height)
        assertEquals("image/jpeg", result.contentType)
        assertTrue(result.bytes.size < bytes.size)
        assertContentEquals(original, source.bytes)
        assertEquals(768, ImageIO.read(ByteArrayInputStream(source.bytes)).width)
    }

    @Test
    fun `small transparent images keep alpha without upscaling`() {
        val source = ProductImageUpload("image/png", fixture("catalog-transparent.png"))
        val result = assertNotNull(processor.thumbnail(source))
        val decoded = ImageIO.read(ByteArrayInputStream(result.bytes))
        assertEquals("image/png", result.contentType)
        assertEquals(64, decoded.width)
        assertEquals(32, decoded.height)
        assertTrue(decoded.colorModel.hasAlpha())
    }

    @Test
    fun `webp has no generated thumbnail and retains its original bytes`() {
        val source = ProductImageUpload("image/webp", byteArrayOf(1, 2, 3))
        assertNull(processor.thumbnail(source))
        assertContentEquals(byteArrayOf(1, 2, 3), source.bytes)
    }

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResourceAsStream("testdata/merchant/$name")).use { it.readBytes() }
}
