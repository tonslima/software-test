package br.com.marvin.api.exception

import java.util.UUID

class ReconciliationRunNotFoundException(runId: UUID) : RuntimeException("ReconciliationRun not found: $runId")
