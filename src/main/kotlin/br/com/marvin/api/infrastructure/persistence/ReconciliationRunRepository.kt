package br.com.marvin.api.infrastructure.persistence

import br.com.marvin.api.domain.ReconciliationRun
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReconciliationRunRepository : JpaRepository<ReconciliationRun, UUID>
