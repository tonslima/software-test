package br.com.marvin.api.web

import br.com.marvin.api.application.CreateReconciliationUseCase
import br.com.marvin.api.web.dto.CreateReconciliationResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@RestController
@RequestMapping("/reconciliations")
class ReconciliationController(
    private val createReconciliationUseCase: CreateReconciliationUseCase,
) {

    @PostMapping(consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun create(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("referenceDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) referenceDate: LocalDate,
    ): CreateReconciliationResponse {
        val runId = createReconciliationUseCase.execute(referenceDate, file.inputStream, file.size)
        return CreateReconciliationResponse(runId)
    }
}
