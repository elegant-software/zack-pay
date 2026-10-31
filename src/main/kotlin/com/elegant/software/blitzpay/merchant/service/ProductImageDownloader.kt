package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.config.propagateRequestId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration

/**
 * Fetches a product image from a public URL so MCP clients can reference large images
 * instead of inlining them as base64. Guards against SSRF by allowing only HTTP(S) URLs
 * that resolve to public addresses, re-validating every redirect hop and the connected peer.
 */
@Component
class ProductImageDownloader internal constructor(
    webClientBuilder: WebClient.Builder,
    private val allowPrivateAddresses: Boolean
) {
    @Autowired
    constructor(webClientBuilder: WebClient.Builder) : this(webClientBuilder, allowPrivateAddresses = false)

    private val log = LoggerFactory.getLogger(ProductImageDownloader::class.java)

    private val webClient: WebClient = webClientBuilder
        .propagateRequestId()
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .responseTimeout(Timeout)
                    .followRedirect(false)
                    .doOnConnected { connection ->
                        val remote = connection.channel().remoteAddress() as? InetSocketAddress
                        if (!allowPrivateAddresses && remote?.address?.let(::isNonPublic) == true) {
                            connection.dispose()
                        }
                    }
            )
        )
        .codecs { it.defaultCodecs().maxInMemorySize(ProductImagePolicy.MaxSourceBytes.toInt()) }
        .build()

    /** Returns the raw image (up to [ProductImagePolicy.MaxSourceBytes]); [ProductImageProcessor] fits it for storage. */
    fun download(imageUrl: String): RawProductImage {
        var uri = parseAndValidate(imageUrl)
        repeat(MaxRedirects + 1) {
            val response = fetch(uri)
            if (response.location == null) {
                val bytes = response.body
                    ?: throw IllegalArgumentException("Product image URL returned an empty body: $uri")
                ProductImagePolicy.validateSourceSize(bytes.size.toLong())
                val contentType = detectContentType(bytes)
                    ?: throw IllegalArgumentException("Product image URL did not return a JPEG, PNG, or WebP image: $uri")
                log.info("Product image downloaded: host={} bytes={} type={}", uri.host, bytes.size, contentType)
                return RawProductImage(contentType = contentType, bytes = bytes)
            }
            uri = parseAndValidate(uri.resolve(response.location).toString())
        }
        throw IllegalArgumentException("Product image URL redirected more than $MaxRedirects times")
    }

    private class FetchResult(val body: ByteArray?, val location: String?)

    private fun fetch(uri: URI): FetchResult =
        try {
            webClient.get()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "image/jpeg, image/png, image/webp")
                .exchangeToMono { response ->
                    val status = response.statusCode()
                    when {
                        status.is3xxRedirection -> {
                            val location = response.headers().header(HttpHeaders.LOCATION).firstOrNull()
                                ?: throw IllegalArgumentException("Product image URL redirect has no Location header: $uri")
                            response.releaseBody().thenReturn(FetchResult(null, location))
                        }
                        !status.is2xxSuccessful -> response.releaseBody().then(
                            Mono.error(
                                IllegalArgumentException("Product image URL returned HTTP ${status.value()}: $uri")
                            )
                        )
                        else -> {
                            response.headers().contentLength().ifPresent { ProductImagePolicy.validateSourceSize(it) }
                            response.bodyToMono(ByteArray::class.java)
                                .map { FetchResult(it, null) }
                                .defaultIfEmpty(FetchResult(null, null))
                        }
                    }
                }
                .block(Timeout) ?: FetchResult(null, null)
        } catch (ex: DataBufferLimitException) {
            throw IllegalArgumentException("Product image source must be 15 MB or smaller", ex)
        } catch (ex: IllegalArgumentException) {
            throw ex
        } catch (ex: Exception) {
            throw IllegalArgumentException("Unable to download product image from $uri: ${ex.message}", ex)
        }

    private fun parseAndValidate(imageUrl: String): URI {
        val uri = runCatching { URI(imageUrl.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid product image URL: $imageUrl")
        require(uri.scheme?.lowercase() in AllowedSchemes) { "Product image URL must use http or https: $imageUrl" }
        val host = uri.host ?: throw IllegalArgumentException("Product image URL has no host: $imageUrl")
        if (!allowPrivateAddresses) {
            val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrNull()
                ?: throw IllegalArgumentException("Product image URL host cannot be resolved: $host")
            require(addresses.none(::isNonPublic)) { "Product image URL must point to a public host: $host" }
        }
        return uri
    }

    companion object {
        private val Timeout: Duration = Duration.ofSeconds(20)
        private const val MaxRedirects = 3
        private val AllowedSchemes = setOf("http", "https")

        internal fun isNonPublic(address: InetAddress): Boolean =
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress ||
                (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC) || // fc00::/7 unique local
                (address.address.size == 4 && address.address[0].toInt() and 0xFF == 100 &&
                    address.address[1].toInt() and 0xC0 == 64) // 100.64.0.0/10 carrier-grade NAT

        internal fun detectContentType(bytes: ByteArray): String? = when {
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            bytes.size >= 12 && bytes.startsWith(0x52, 0x49, 0x46, 0x46) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
            else -> null
        }

        private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
            size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }
    }
}
