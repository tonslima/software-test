package br.com.marvin.api.application.port

import br.com.marvin.api.application.CsvTransaction
import java.io.InputStream

interface CsvParser {
    fun parse(inputStream: InputStream): Sequence<CsvTransaction>
}
