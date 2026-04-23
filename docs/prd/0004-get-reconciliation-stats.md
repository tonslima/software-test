# PRD-0004: GET /reconciliations/{runId}/stats

## Problema

Após o processamento de uma reconciliação, o time de operações precisa de uma visão consolidada do run — quantas transações caíram em cada categoria e qual o percentual de discrepância — sem precisar paginar os resultados completos.

## O que será construído

Endpoint `GET /reconciliations/{runId}/stats` que retorna contagens por categoria e métricas agregadas de um reconciliation run.

## Requisitos

1. Retornar `404` se o `runId` não existir.
2. Retornar `202` se o run estiver em `UPLOADING`, `PENDING` ou `PROCESSING`, com `runId`, `runStatus` e `createdAt` — mesmo comportamento do `/results`.
3. Retornar `200` com as stats se o run estiver em `COMPLETED`.
4. Retornar `200` com todas as categorias zeradas e `runStatus: FAILED` se o run falhou — o rollback garante que não há resultados persistidos.
5. O body de `200` contém:
   - `runId`
   - `runStatus`
   - `totalTransactions` — total de resultados persistidos
   - `discrepancyRate` — percentual de transações que não são `MATCHED` (arredondado para 2 casas decimais)
   - `categories` — mapa com contagem por categoria: `MATCHED`, `MISMATCHED`, `UNRECONCILED_PROCESSOR`, `UNRECONCILED_INTERNAL`

## Componentes

- **`ReconciliationResultRepository`** — adicionar query que retorna contagem agrupada por categoria para um `runId`.
- **`GetReconciliationStatsUseCase`** — buscar run, validar status, calcular stats.
- **`ReconciliationStatsResponse`** — DTO de resposta com as métricas agregadas.
- **`ReconciliationController`** — adicionar handler do `GET /{runId}/stats`.

## Decisões de implementação

**Sem nova migration:** o índice composto `(run_id, category)` criado para o `/results` já cobre a query de agrupamento por categoria.

**`discrepancyRate` calculado na aplicação:** buscar as contagens por categoria e calcular o percentual no use case — evita lógica de negócio no banco.

**Run `FAILED` retorna zeros:** o `transactionTemplate` faz rollback de todos os resultados em caso de falha, então não há dados parciais. Retornar zeros com `runStatus: FAILED` é a resposta correta.

**Reutilizar `StillProcessing`:** o mesmo padrão do `/results` — sealed class com `StillProcessing` e `Done` para o output do use case.

**Contrato da API:**

```
GET /reconciliations/{runId}/stats

200 OK (COMPLETED)
{
  "runId": "uuid",
  "runStatus": "COMPLETED",
  "totalTransactions": 20,
  "discrepancyRate": 30.00,
  "categories": {
    "MATCHED": 14,
    "MISMATCHED": 1,
    "UNRECONCILED_PROCESSOR": 0,
    "UNRECONCILED_INTERNAL": 5
  }
}

200 OK (FAILED)
{
  "runId": "uuid",
  "runStatus": "FAILED",
  "totalTransactions": 0,
  "discrepancyRate": 0.00,
  "categories": {
    "MATCHED": 0,
    "MISMATCHED": 0,
    "UNRECONCILED_PROCESSOR": 0,
    "UNRECONCILED_INTERNAL": 0
  }
}

202 Accepted
{
  "runId": "uuid",
  "runStatus": "PENDING",
  "createdAt": "2026-04-23T01:00:00Z"
}
```

## Decisões de teste

**Unitários:**
- `GetReconciliationStatsUseCase`: run não encontrado, run PENDING, run COMPLETED com resultados, run FAILED com zeros, cálculo correto do `discrepancyRate`.

**Integração:**
- Happy path: run COMPLETED com stats corretas.
- Run inexistente → 404.
- Run PENDING → 202.

## Fora do escopo

- Agregações financeiras (soma de `processorAmount` vs `internalAmount` por categoria).
- Filtros por data ou merchant.
- Histórico comparativo entre runs.

## Notas

- O `discrepancyRate` usa a mesma lógica do alerta de 5% implementado no `ProcessReconciliationUseCase` — transações não-`MATCHED` divididas pelo total.
- `UNRECONCILED_PROCESSOR` pode ser zero quando o seed está carregado corretamente — isso é esperado, não um bug.
