# SPEC-0003: GET /reconciliations/{runId}/results

**PRD:** [0003-get-reconciliation-results.md](../prd/0003-get-reconciliation-results.md)

## O que foi implementado

Endpoint `GET /reconciliations/{runId}/results` que retorna os resultados de um reconciliation run, com filtro opcional por categoria e paginação offset-based. O endpoint comunica o estado do run via HTTP status code e adapta o body conforme o estado.

## Contratos de API

### `GET /reconciliations/{runId}/results`

**Query params:**

| Parâmetro | Tipo | Obrigatório | Default | Descrição |
|-----------|------|-------------|---------|-----------|
| `category` | `ReconciliationCategory` (enum) | Não | — | Filtro por categoria. Aceita múltiplos valores. |
| `page` | `Int` | Não | `0` | Página (zero-based) |
| `size` | `Int` | Não | `50` | Tamanho da página. Máximo: `200`. |

**Respostas:**

`404 Not Found` — run não existe
```json
{ "status": 404, "detail": "ReconciliationRun not found: {runId}" }
```

`202 Accepted` — run em `UPLOADING` ou `PENDING`
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
  "runStatus": "COMPLETED",
  "errorMessage": null,
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
```

`200 OK` — run em `FAILED`
```json
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

`400 Bad Request` — `size` excede o máximo
```json
{ "status": 400, "detail": "Page size must not exceed 200" }
```

## Decisões tomadas durante a implementação

**`UPLOADING` tratado como estado intermediário:** o PRD mencionava apenas `PENDING` como estado que retorna `202`. Durante a implementação, percebeu-se que `UPLOADING` também é um estado onde os resultados ainda não existem — foi incluído no mesmo bloco.

**`202` enriquecido com `runId` e `createdAt`:** o PRD previa apenas `runStatus` no body do `202`. Durante a implementação, decidiu-se incluir `runId` e `createdAt` para dar contexto útil ao cliente — saber o que está esperando e há quanto tempo.

**Sealed class para o output do use case:** o use case retorna `ReconciliationResultsOutput`, uma sealed class com dois subtipos: `StillProcessing` e `Done`. Garante em tempo de compilação que todos os estados são tratados no controller.

**Mapper separado:** a transformação de `ReconciliationResult` (entidade JPA) para `ReconciliationResultItem` (DTO) foi extraída para `ReconciliationResultMapper`, mantendo o controller responsável apenas pelo mapeamento HTTP.

**Exceção própria para run não encontrado:** `ReconciliationRunNotFoundException` em vez de `NoSuchElementException` genérica, evitando que outras exceções da stdlib sejam incorretamente mapeadas para `404`.

## Desvios do PRD

| PRD | Implementado | Motivo |
|-----|-------------|--------|
| `202` retorna apenas `runStatus` | `202` retorna `runId`, `runStatus` e `createdAt` | Resposta mais útil pro cliente |
| `PENDING` retorna `202` | `UPLOADING` e `PENDING` retornam `202` | `UPLOADING` também é estado sem resultados |

## Limitações conhecidas

- `errorMessage` aparece como `null` no JSON quando o status é `COMPLETED` — poderia ser omitido com `@JsonInclude(NON_NULL)`.
- Não há ordenação customizável dos resultados.
- Paginação offset-based pode ter degradação de performance em páginas muito altas (ver ADR-0002).

## Como testar

**Happy path (COMPLETED):**
1. `docker-compose up -d`
2. `./gradlew bootRun`
3. Executar `POST /reconciliations` em `requests/post-reconciliations.http` com `referenceDate=2026-03-15`
4. Aguardar processamento (run ficará `COMPLETED` em alguns segundos)
5. Executar `GET /reconciliations/{runId}/results` em `requests/get-reconciliation-results.http`
6. Esperar `200` com 20 resultados (14 MATCHED, 1 MISMATCHED, 5 UNRECONCILED_INTERNAL)

**Run ainda processando (202):**
1. Inserir diretamente no banco:
```sql
INSERT INTO reconciliation_runs (id, status, reference_date, created_at, s3_key)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'PENDING', '2026-03-15', NOW(), 'test/input.csv');
```
2. `GET /reconciliations/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/results`
3. Esperar `202` com `runId`, `runStatus` e `createdAt`

**Run não encontrado (404):**
- Executar o request `GET /reconciliations/00000000-0000-0000-0000-000000000000/results`
- Esperar `404`

**Size excedido (400):**
- `GET /reconciliations/{runId}/results?size=201`
- Esperar `400` com mensagem de erro
