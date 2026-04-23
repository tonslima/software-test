# SPEC-0004: GET /reconciliations/{runId}/stats

**PRD:** [0004-get-reconciliation-stats.md](../prd/0004-get-reconciliation-stats.md)

## O que foi implementado

Endpoint `GET /reconciliations/{runId}/stats` que retorna contagens por categoria de reconciliação e métricas agregadas (total de transações e percentual de discrepância) para um run específico.

## Contratos de API

### `GET /reconciliations/{runId}/stats`

**Sem query params.**

**Respostas:**

`404 Not Found` — run não existe
```json
{ "status": 404, "detail": "ReconciliationRun not found: {runId}" }
```

`202 Accepted` — run em `UPLOADING`, `PENDING` ou `PROCESSING`
```json
{
  "runId": "uuid",
  "runStatus": "PENDING",
  "createdAt": "2026-04-23T01:00:00Z"
}
```

`200 OK` — run em `COMPLETED`
```json
{
  "runId": "uuid",
  "runStatus": "COMPLETED",
  "totalTransactions": 20,
  "discrepancyRate": 30.0,
  "categories": {
    "MATCHED": 14,
    "MISMATCHED": 1,
    "UNRECONCILED_PROCESSOR": 0,
    "UNRECONCILED_INTERNAL": 5
  }
}
```

`200 OK` — run em `FAILED`
```json
{
  "runId": "uuid",
  "runStatus": "FAILED",
  "totalTransactions": 0,
  "discrepancyRate": 0.0,
  "categories": {
    "MATCHED": 0,
    "MISMATCHED": 0,
    "UNRECONCILED_PROCESSOR": 0,
    "UNRECONCILED_INTERNAL": 0
  }
}
```

## Decisões tomadas durante a implementação

**Interface de projeção `CategoryCount`:** a query JPQL com `GROUP BY` retornaria `List<Array<Any>>` por padrão. Foi criada uma interface de projeção (`CategoryCount`) para tipar os resultados e evitar casts manuais.

**Sealed class separada (`ReconciliationStatsOutput`):** em vez de reutilizar a sealed class do `/results`, foi criada uma própria para o stats. `StillProcessing` tem a mesma estrutura, mas `Done` é diferente (contagens em vez de `Page`). Manter separado evita acoplamento entre os dois endpoints.

**`discrepancyRate` com `BigDecimal`:** o cálculo do percentual usa `BigDecimal` para evitar imprecisão de ponto flutuante, arredondado para 2 casas decimais com `HALF_UP`.

**Mapper separado (`ReconciliationStatsMapper`):** segue o mesmo padrão do endpoint de results — controller só faz mapeamento HTTP, mapper faz a transformação de output para DTO.

## Desvios do PRD

Nenhum desvio. A implementação segue o PRD integralmente.

## Limitações conhecidas

- Não há cache — cada chamada executa a query no banco. Para runs `COMPLETED`, o resultado nunca muda e poderia ser cacheado.
- Categorias que não existem no banco aparecem com `0` — isso é intencional, mas o frontend precisa estar ciente de que todas as 4 categorias sempre estarão presentes.
- Não há agregações financeiras (soma de valores por categoria).

## Como testar

**Happy path (COMPLETED):**
1. `docker-compose up -d`
2. `./gradlew bootRun`
3. Executar `POST /reconciliations` em `requests/post-reconciliations.http` com `referenceDate=2026-03-15`
4. Aguardar processamento
5. Executar `GET /reconciliations/{runId}/stats` em `requests/get-reconciliation-stats.http`
6. Esperar `200` com `totalTransactions: 20`, `discrepancyRate: 30.0`, `MATCHED: 14`, `MISMATCHED: 1`, `UNRECONCILED_INTERNAL: 5`, `UNRECONCILED_PROCESSOR: 0`

**Run ainda processando (202):**
1. Inserir diretamente no banco:
```sql
INSERT INTO reconciliation_runs (id, status, reference_date, created_at, s3_key)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'PROCESSING', '2026-03-15', NOW(), 'test/input.csv');
```
2. `GET /reconciliations/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/stats`
3. Esperar `202` com `runId`, `runStatus` e `createdAt`

**Run não encontrado (404):**
- `GET /reconciliations/00000000-0000-0000-0000-000000000000/stats`
- Esperar `404`
