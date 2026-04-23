package br.com.marvin.api.infrastructure.persistence

import br.com.marvin.api.domain.vo.ReconciliationCategory

interface CategoryCount {
    val category: ReconciliationCategory
    val count: Long
}
