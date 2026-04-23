package br.com.marvin.api.application.port

import br.com.marvin.api.application.usecase.process.CsvTransaction
import java.io.InputStream

interface CsvParser {
    fun parse(inputStream: InputStream): Sequence<CsvTransaction>
}
