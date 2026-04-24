package br.com.marvin.api.web

import br.com.marvin.api.application.usecase.create.CreateReconciliationUseCase
import br.com.marvin.api.application.usecase.results.GetReconciliationResultsUseCase
import br.com.marvin.api.application.usecase.stats.GetReconciliationStatsUseCase
import br.com.marvin.api.application.usecase.results.ReconciliationResultsOutput
import br.com.marvin.api.application.usecase.stats.ReconciliationStatsOutput
import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.web.dto.CreateReconciliationResponse
import br.com.marvin.api.web.mapper.ReconciliationResultMapper
import br.com.marvin.api.web.mapper.ReconciliationStatsMapper
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/reconciliations")
class ReconciliationController(
    private val createReconciliationUseCase: CreateReconciliationUseCase,
    private val getReconciliationResultsUseCase: GetReconciliationResultsUseCase,
    private val getReconciliationStatsUseCase: GetReconciliationStatsUseCase,
) {

    @PostMapping(consumes = ["multipart/form-data"])
    fun create(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("referenceDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) referenceDate: LocalDate,
    ): ResponseEntity<CreateReconciliationResponse> {
        val runId = createReconciliationUseCase.execute(referenceDate, file.inputStream, file.size)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CreateReconciliationResponse(runId))
    }

    @GetMapping("/{runId}/results")
    fun getResults(
        @PathVariable runId: UUID,
        @RequestParam(required = false) category: List<ReconciliationCategory>?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<*> {
        return when (val output = getReconciliationResultsUseCase.execute(runId, category, page, size)) {
            is ReconciliationResultsOutput.StillProcessing ->
                ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ReconciliationResultMapper.toStatusResponse(output))

            is ReconciliationResultsOutput.Done ->
                ResponseEntity.ok(ReconciliationResultMapper.toResultResponse(output))
        }
    }

    @GetMapping("/{runId}/stats")
    fun getStats(@PathVariable runId: UUID): ResponseEntity<*> {
        return when (val output = getReconciliationStatsUseCase.execute(runId)) {
            is ReconciliationStatsOutput.StillProcessing ->
                ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ReconciliationStatsMapper.toStatusResponse(output))

            is ReconciliationStatsOutput.Done ->
                ResponseEntity.ok(ReconciliationStatsMapper.toStatsResponse(output))
        }
    }
}
