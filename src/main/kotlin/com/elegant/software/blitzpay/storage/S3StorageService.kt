package com.elegant.software.blitzpay.storage

import com.elegant.software.blitzpay.config.ObservabilitySupport
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import java.time.Duration
import java.time.Instant

internal class S3StorageService(
    private val s3Client: S3Client,
    private val presigner: S3Presigner,
    private val props: StorageProperties,
    private val observability: ObservabilitySupport,
) : StorageService {

    private val log = LoggerFactory.getLogger(S3StorageService::class.java)

    override fun upload(storageKey: String, contentType: String, bytes: ByteArray) {
        val putRequest = PutObjectRequest.builder()
            .bucket(props.bucket)
            .key(storageKey)
            .contentType(contentType)
            .build()
        observe("upload") {
            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes))
        }
        log.info("Uploaded storage object key={}", storageKey)
    }

    override fun presignUpload(storageKey: String, contentType: String, ttlMinutes: Long): PresignedUpload {
        val putRequest = PutObjectRequest.builder()
            .bucket(props.bucket)
            .key(storageKey)
            .contentType(contentType)
            .build()
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(ttlMinutes))
            .putObjectRequest(putRequest)
            .build()
        val presigned = observe("presign-upload") {
            presigner.presignPutObject(presignRequest)
        }
        log.debug("Presigned upload URL generated for key={}", storageKey)
        return PresignedUpload(
            storageKey = storageKey,
            uploadUrl = presigned.url().toString(),
            expiresAt = Instant.now().plusSeconds(ttlMinutes * 60)
        )
    }

    override fun presignDownload(storageKey: String, ttlMinutes: Long): String {
        val getRequest = GetObjectRequest.builder()
            .bucket(props.bucket)
            .key(storageKey)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(ttlMinutes))
            .getObjectRequest(getRequest)
            .build()
        return observe("presign-download") {
            presigner.presignGetObject(presignRequest).url().toString()
        }
    }

    override fun delete(storageKey: String) {
        observe("delete") {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(props.bucket)
                    .key(storageKey)
                    .build()
            )
        }
        log.info("Deleted storage object key={}", storageKey)
    }

    private fun <T> observe(operation: String, block: () -> T): T =
        observability.observe(
            name = "blitzpay.external",
            contextualName = "s3 $operation",
            lowCardinality = mapOf("provider" to "s3", "operation" to operation),
            block = block,
        )
}
