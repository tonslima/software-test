package br.com.marvin.api.infrastructure.persistence

import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.model.ReconciliationResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.NativeQuery
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ReconciliationResultRepository : JpaRepository<ReconciliationResult, UUID> {

    fun countByRunId(runId: UUID): Long

    fun countByRunIdAndCategoryIn(runId: UUID, categories: Collection<ReconciliationCategory>): Long

    fun findByRunId(runId: UUID, pageable: Pageable): Page<ReconciliationResult>

    fun findByRunIdAndCategoryIn(runId: UUID, categories: Collection<ReconciliationCategory>, pageable: Pageable): Page<ReconciliationResult>
}
