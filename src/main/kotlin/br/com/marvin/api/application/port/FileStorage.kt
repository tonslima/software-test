package br.com.marvin.api.application.port

import java.io.InputStream

interface FileStorage {
    fun upload(key: String, inputStream: InputStream, contentLength: Long)
    fun download(key: String): InputStream
}
