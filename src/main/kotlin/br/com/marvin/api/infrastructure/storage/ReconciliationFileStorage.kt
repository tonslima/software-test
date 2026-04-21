package br.com.marvin.api.infrastructure.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import java.io.InputStream

@Component
class ReconciliationFileStorage(
    private val s3Client: S3Client,
    @Value("\${app.s3.bucket}") private val bucket: String,
) {

    fun upload(key: String, inputStream: InputStream, contentLength: Long) {
        s3Client.putObject(
            { it.bucket(bucket).key(key) },
            RequestBody.fromInputStream(inputStream, contentLength),
        )
    }
}
