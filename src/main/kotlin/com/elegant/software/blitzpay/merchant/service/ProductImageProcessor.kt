package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.ProductImageQualityProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class RawProductImage(val contentType: String, val bytes: ByteArray)

data class ProductImageCrop(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Turns a raw product image (up to [ProductImagePolicy.MaxSourceBytes]) into a storable
 * [ProductImageUpload] of at most [ProductImagePolicy.MaxBytes]: applies the optional crop, rejects
 * images that fail the technical quality gate ([ProductImageQualityInspector]) and, when the result
 * is too large, downsizes and re-encodes it. Images that need no changes are stored byte-for-byte.
 *
 * Memory is bounded for small pods: dimensions are checked from the header before decoding,
 * only the crop region is decoded, very large sources are decoded subsampled to [MaxDecodeEdge],
 * and at most [MaxConcurrentDecodes] images are decoded at once.
 */
@Component
@EnableConfigurationProperties(ProductImageQualityProperties::class)
class ProductImageProcessor(qualityProperties: ProductImageQualityProperties) {

    private val log = LoggerFactory.getLogger(ProductImageProcessor::class.java)
    private val decodePermits = Semaphore(MaxConcurrentDecodes)
    private val qualityInspector = ProductImageQualityInspector(qualityProperties)

    fun thumbnail(image: ProductImageUpload): ProductImageUpload? {
        if (image.contentType.lowercase() == "image/webp") return null
        require(decodePermits.tryAcquire(AcquireTimeoutSeconds, TimeUnit.SECONDS)) {
            "Product image processing is busy; retry shortly"
        }
        try {
            val decoded = decode(image.bytes, null, 512).image
            val contentType = if (decoded.colorModel.hasAlpha()) "image/png" else "image/jpeg"
            return ProductImageUpload(contentType, encode(scale(decoded, 256), contentType))
        } finally {
            decodePermits.release()
        }
    }

    fun prepare(image: RawProductImage, crop: ProductImageCrop? = null): ProductImageUpload {
        ProductImagePolicy.extensionFor(image.contentType)
        ProductImagePolicy.validateSourceSize(image.bytes.size.toLong())
        if (image.contentType.lowercase() == "image/webp") return prepareWebp(image, crop)

        val needsReencode = crop != null || image.bytes.size > ProductImagePolicy.MaxBytes
        require(decodePermits.tryAcquire(AcquireTimeoutSeconds, TimeUnit.SECONDS)) {
            "Product image processing is busy; retry shortly"
        }
        try {
            val decoded = decode(image.bytes, crop, if (needsReencode) MaxDecodeEdge else AnalysisDecodeEdge)
            qualityInspector.inspect(decoded.image, decoded.sourceWidth, decoded.sourceHeight)
            if (!needsReencode) return ProductImageUpload(contentType = image.contentType, bytes = image.bytes)

            val result = encodeWithinLimit(decoded.image, image.contentType)
            log.info(
                "Product image processed: sourceBytes={} cropped={} resultBytes={} type={}",
                image.bytes.size, crop != null, result.bytes.size, result.contentType
            )
            return result
        } finally {
            decodePermits.release()
        }
    }

    /** ImageIO has no WebP codec: only header-level checks are possible, and the image is stored as-is. */
    private fun prepareWebp(image: RawProductImage, crop: ProductImageCrop?): ProductImageUpload {
        require(crop == null) { "Server-side crop is supported for JPEG and PNG images only" }
        require(image.bytes.size <= ProductImagePolicy.MaxBytes) {
            "WebP product images must be 5 MB or smaller; supply a JPEG or PNG to have it resized"
        }
        val (width, height) = webpDimensions(image.bytes)
            ?: throw IllegalArgumentException("Unable to read WebP product image dimensions")
        qualityInspector.checkDimensions(width, height)
        return ProductImageUpload(contentType = image.contentType, bytes = image.bytes)
    }

    private class DecodedImage(val image: BufferedImage, val sourceWidth: Int, val sourceHeight: Int)

    private fun decode(bytes: ByteArray, crop: ProductImageCrop?, maxEdge: Int): DecodedImage {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: throw IllegalArgumentException("Unable to decode product image")
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width.toLong() * height <= ProductImagePolicy.MaxSourcePixels) {
                    "Product image dimensions ${width}x$height exceed the supported maximum"
                }
                val region = crop?.let { validCropRegion(it, width, height) } ?: Rectangle(0, 0, width, height)
                val subsampling = max(1, ceil(max(region.width, region.height).toDouble() / maxEdge).toInt())
                val param = reader.defaultReadParam.apply {
                    sourceRegion = region
                    setSourceSubsampling(subsampling, subsampling, 0, 0)
                }
                return DecodedImage(read(reader, param), region.width, region.height)
            } finally {
                reader.dispose()
            }
        }
    }

    private fun read(reader: ImageReader, param: ImageReadParam): BufferedImage =
        runCatching { reader.read(0, param) }.getOrElse {
            throw IllegalArgumentException("Unable to decode product image: ${it.message}", it)
        }

    private fun validCropRegion(crop: ProductImageCrop, width: Int, height: Int): Rectangle {
        require(crop.width > 0 && crop.height > 0) { "cropWidth and cropHeight must be positive" }
        require(crop.x >= 0 && crop.y >= 0) { "cropX and cropY must be >= 0" }
        require(crop.x + crop.width <= width && crop.y + crop.height <= height) {
            "Crop rectangle exceeds image bounds ${width}x$height"
        }
        return Rectangle(crop.x, crop.y, crop.width, crop.height)
    }

    /** Keeps the original format when it fits; otherwise steps down in size, preferring JPEG unless transparency matters. */
    private fun encodeWithinLimit(image: BufferedImage, sourceContentType: String): ProductImageUpload {
        val keepPng = sourceContentType.lowercase() == "image/png" && image.colorModel.hasAlpha()
        val fallbackType = if (keepPng) "image/png" else "image/jpeg"
        val longestEdge = max(image.width, image.height)

        encode(image, sourceContentType).takeIf { it.size <= ProductImagePolicy.MaxBytes }
            ?.let { return ProductImageUpload(sourceContentType, it) }

        for (edge in listOf(longestEdge) + ResizeSteps.filter { it < longestEdge }) {
            val bytes = encode(scale(image, edge), fallbackType)
            if (bytes.size <= ProductImagePolicy.MaxBytes) return ProductImageUpload(fallbackType, bytes)
        }
        throw IllegalArgumentException("Product image could not be reduced to 5 MB or smaller")
    }

    private fun scale(image: BufferedImage, longestEdge: Int): BufferedImage {
        val factor = longestEdge.toDouble() / max(image.width, image.height)
        if (factor >= 1.0) return image
        val width = max(1, (image.width * factor).roundToInt())
        val height = max(1, (image.height * factor).roundToInt())
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        return BufferedImage(width, height, type).also { scaled ->
            val graphics = scaled.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                graphics.drawImage(image, 0, 0, width, height, null)
            } finally {
                graphics.dispose()
            }
        }
    }

    private fun encode(image: BufferedImage, contentType: String): ByteArray {
        val output = ByteArrayOutputStream()
        if (contentType.lowercase() == "image/jpeg") {
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            try {
                ImageIO.createImageOutputStream(output).use { stream ->
                    writer.output = stream
                    val param = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = JpegQuality
                    }
                    writer.write(null, IIOImage(withoutAlpha(image), null, null), param)
                }
            } finally {
                writer.dispose()
            }
        } else {
            require(ImageIO.write(image, ProductImagePolicy.extensionFor(contentType), output)) {
                "Unable to encode product image as $contentType"
            }
        }
        return output.toByteArray()
    }

    private fun withoutAlpha(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_RGB || image.type == BufferedImage.TYPE_3BYTE_BGR) return image
        return BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { rgb ->
            val graphics = rgb.createGraphics()
            try {
                graphics.drawImage(image, 0, 0, java.awt.Color.WHITE, null)
            } finally {
                graphics.dispose()
            }
        }
    }

    private fun webpDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 30) return null
        fun u8(i: Int) = bytes[i].toInt() and 0xFF
        fun u24(i: Int) = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16)
        return when (String(bytes, 12, 4, Charsets.US_ASCII)) {
            "VP8X" -> (u24(24) + 1) to (u24(27) + 1)
            "VP8 " -> ((u8(26) or (u8(27) shl 8)) and 0x3FFF) to ((u8(28) or (u8(29) shl 8)) and 0x3FFF)
            "VP8L" -> {
                val bits = u8(21) or (u8(22) shl 8) or (u8(23) shl 16) or (u8(24) shl 24)
                ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
            }
            else -> null
        }
    }

    companion object {
        private const val MaxDecodeEdge = 4096
        private const val AnalysisDecodeEdge = 2048
        private const val MaxConcurrentDecodes = 2
        private const val AcquireTimeoutSeconds = 30L
        private const val JpegQuality = 0.88f
        private val ResizeSteps = listOf(3072, 2560, 2048, 1600, 1280)
    }
}
