package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.ProductImageQualityProperties
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ProductImageQualityReport(
    val width: Int,
    val height: Int,
    val sharpness: Double,
    val brightness: Double,
    val contrast: Double
)

/**
 * Deterministic technical quality checks (resolution, aspect ratio, blur, exposure, contrast).
 * Rejections list every failed check with its measured value so MCP clients can pick a better image.
 * Semantic checks (does the image show the product?) are out of scope; see ADR-0019.
 */
class ProductImageQualityInspector(private val properties: ProductImageQualityProperties) {

    private val log = LoggerFactory.getLogger(ProductImageQualityInspector::class.java)

    fun checkDimensions(width: Int, height: Int) {
        if (!properties.enabled) return
        rejectIfAny(dimensionFailures(width, height))
    }

    fun inspect(image: BufferedImage, sourceWidth: Int, sourceHeight: Int): ProductImageQualityReport {
        val luminance = luminance(scaleForAnalysis(image))
        val report = ProductImageQualityReport(
            width = sourceWidth,
            height = sourceHeight,
            sharpness = sharpness(luminance),
            brightness = luminance.values.average(),
            contrast = standardDeviation(luminance.values)
        )
        log.info(
            "Product image quality: size={}x{} sharpness={} brightness={} contrast={}",
            report.width, report.height, report.sharpness.roundToInt(),
            report.brightness.roundToInt(), report.contrast.roundToInt()
        )
        if (properties.enabled) {
            rejectIfAny(dimensionFailures(sourceWidth, sourceHeight) + pixelFailures(report))
        }
        return report
    }

    private fun dimensionFailures(width: Int, height: Int): List<String> = buildList {
        val shortEdge = min(width, height)
        if (shortEdge < properties.minShortEdge) {
            add("resolution too low (${width}x$height; shorter side must be at least ${properties.minShortEdge} px)")
        }
        val aspectRatio = max(width, height).toDouble() / max(1, shortEdge)
        if (aspectRatio > properties.maxAspectRatio) {
            add("aspect ratio too extreme (${"%.1f".format(aspectRatio)}:1; maximum ${properties.maxAspectRatio}:1)")
        }
    }

    private fun pixelFailures(report: ProductImageQualityReport): List<String> = buildList {
        if (report.contrast < properties.minContrast) {
            add("image is blank or nearly a single colour (contrast ${report.contrast.roundToInt()}; minimum ${properties.minContrast.roundToInt()})")
        } else if (report.sharpness < properties.minSharpness) {
            add("image is blurry (sharpness ${report.sharpness.roundToInt()}; minimum ${properties.minSharpness.roundToInt()})")
        }
        if (report.brightness < properties.minBrightness) {
            add("image is too dark (brightness ${report.brightness.roundToInt()}; minimum ${properties.minBrightness.roundToInt()})")
        }
        if (report.brightness > properties.maxBrightness) {
            add("image is overexposed (brightness ${report.brightness.roundToInt()}; maximum ${properties.maxBrightness.roundToInt()})")
        }
    }

    private fun rejectIfAny(failures: List<String>) {
        require(failures.isEmpty()) {
            "Product image rejected for quality: ${failures.joinToString("; ")}. " +
                "Supply a clearer, higher-resolution photo of the product."
        }
    }

    private class Luminance(val width: Int, val height: Int, val values: DoubleArray)

    /** Normalises to [AnalysisEdge] so thresholds do not depend on source resolution; flattens transparency onto white. */
    private fun scaleForAnalysis(image: BufferedImage): BufferedImage {
        val factor = min(1.0, AnalysisEdge.toDouble() / max(image.width, image.height))
        val width = max(1, (image.width * factor).roundToInt())
        val height = max(1, (image.height * factor).roundToInt())
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { scaled ->
            val graphics = scaled.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(image, 0, 0, width, height, Color.WHITE, null)
            } finally {
                graphics.dispose()
            }
        }
    }

    private fun luminance(image: BufferedImage): Luminance {
        val rgb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val values = DoubleArray(rgb.size) { i ->
            val pixel = rgb[i]
            0.299 * (pixel shr 16 and 0xFF) + 0.587 * (pixel shr 8 and 0xFF) + 0.114 * (pixel and 0xFF)
        }
        return Luminance(image.width, image.height, values)
    }

    /**
     * Variance of the Laplacian per tile, taking the 90th percentile so a sharp product on a plain
     * background (most tiles flat) still scores as sharp.
     */
    private fun sharpness(luminance: Luminance): Double {
        val (width, height, values) = Triple(luminance.width, luminance.height, luminance.values)
        if (width < 3 || height < 3) return 0.0
        val tileWidth = max(3, width / SharpnessGrid)
        val tileHeight = max(3, height / SharpnessGrid)
        val tileVariances = mutableListOf<Double>()
        var tileY = 1
        while (tileY < height - 1) {
            var tileX = 1
            while (tileX < width - 1) {
                var sum = 0.0
                var sumOfSquares = 0.0
                var count = 0
                for (y in tileY until min(tileY + tileHeight, height - 1)) {
                    for (x in tileX until min(tileX + tileWidth, width - 1)) {
                        val i = y * width + x
                        val laplacian = values[i - width] + values[i + width] + values[i - 1] + values[i + 1] - 4 * values[i]
                        sum += laplacian
                        sumOfSquares += laplacian * laplacian
                        count++
                    }
                }
                if (count > 0) {
                    val mean = sum / count
                    tileVariances += sumOfSquares / count - mean * mean
                }
                tileX += tileWidth
            }
            tileY += tileHeight
        }
        tileVariances.sort()
        return tileVariances[((tileVariances.size - 1) * SharpnessPercentile).roundToInt()]
    }

    private fun standardDeviation(values: DoubleArray): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private companion object {
        const val AnalysisEdge = 1024
        const val SharpnessGrid = 8
        const val SharpnessPercentile = 0.9
    }
}
