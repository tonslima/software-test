# PRD-0003: GET /reconciliations/{runId}/results

## Problema

Após o processamento de uma reconciliação, o time de operações precisa consultar os resultados para investigar discrepâncias — transações com mismatch ou não reconciliadas. Hoje não existe endpoint para isso.

## O que será construído

Endpoint `GET /reconciliations/{runId}/results` que retorna os resultados de um reconciliation run, com filtro opcional por categoria e paginação offset-based.

## Requisitos

1. Retornar `404` se o `runId` não existir.
2. Retornar `202` se o run estiver em status `PENDING` ou `PROCESSING`, com o status atual no body.
3. Retornar `200` com a lista de resultados se o run estiver em status `COMPLETED` ou `FAILED`.
   - Se `FAILED`, a lista estará vazia e o body incluirá o `errorMessage` do run.
4. Suportar filtro por `category` via query param — aceita múltiplos valores (`?category=MISMATCHED&category=UNRECONCILED_PROCESSOR`).
5. Paginação offset-based com parâmetros `page` (default: `0`) e `size` (default: `50`, máximo: `200`).
6. Se `size` exceder `200`, retornar `400 Bad Request` com `ProblemDetail` descrevendo o limite máximo.
7. Cada item da lista contém: `transactionId`, `category`, `processorAmount`, `internalAmount`.
8. O body de resposta inclui sempre o `status` do run, além dos dados de paginação (`page`, `size`, `totalElements`, `totalPages`).

## Componentes

- **`ReconciliationResultRepository`** — adicionar query paginada com filtro opcional por lista de categorias.
- **`GetReconciliationResultsUseCase`** — orquestrar: buscar run, validar status, buscar resultados paginados.
- **`ReconciliationResultResponse`** — DTO de resposta com status do run, lista de resultados e metadados de paginação.
- **`ReconciliationController`** — adicionar handler do `GET /{runId}/results`.
- **Migration Liquibase** — adicionar índice em `reconciliation_results(run_id)` e `reconciliation_results(run_id, category)`.

## Decisões de implementação

**Paginação:** offset-based via `Pageable` do Spring Data. Simples e suficiente dado que o filtro por `category` já reduz bastante o conjunto na prática.

**Índices:** índice composto em `(run_id, category)` cobre tanto queries filtradas quanto não filtradas por categoria. Necessário para evitar full table scan em runs com 500k resultados.

**Contrato da API:**

```
GET /reconciliations/{runId}/results?category=MISMATCHED&page=0&size=50

200 OK
{
  "runStatus": "COMPLETED",
  "page": 0,
  "size": 50,
  "totalElements": 320,
  "totalPages": 7,
  "results": [
    {
      "transactionId": "uuid",
      "category": "MISMATCHED",
      "processorAmount": 152.30,
      "internalAmount": 150.00
    }
  ]
}

202 Accepted
{
  "runStatus": "PROCESSING"
}

200 OK (FAILED)
{
  "runStatus": "FAILED",
  "errorMessage": "...",
  "page": 0,
  "size": 50,
  "totalElements": 0,
  "totalPages": 0,
  "results": []
}
```

**Validação do `size`:** feita no use case — lança exceção mapeada pelo `GlobalExceptionHandler` para `400` com `ProblemDetail`.

## Decisões de teste

**Unitários:**
- `GetReconciliationResultsUseCase`: run não encontrado, run PENDING, run PROCESSING, run FAILED, run COMPLETED sem filtro, run COMPLETED com filtro por category, `size` acima do máximo.

**Integração:**
- Happy path: POST + processamento + GET com resultado real.
- GET com filtro por categoria.
- GET com runId inexistente → 404.
- GET com run em PROCESSING → 202.

## Fora do escopo

- Filtros por campos além de `category` (ex: `transactionId`, faixas de valor).
- Cursor-based pagination.
- Ordenação customizável.
- Endpoint de stats (`GET /reconciliations/{runId}/stats`) — PRD separado.

## Notas

- `processorAmount` e `internalAmount` podem ser `null` dependendo da categoria (ex: `UNRECONCILED_INTERNAL` não tem `processorAmount`).
- O `errorMessage` só aparece no body quando o status for `FAILED`.
