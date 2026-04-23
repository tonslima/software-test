# ADR-0004: Respostas HTTP do endpoint GET /reconciliations/{runId}/results

**Status:** Aceito  
**Data:** 2026-04-23

## Contexto

O endpoint de consulta de resultados precisa comunicar estados distintos ao cliente: run não encontrado, run ainda em processamento, e run finalizado (com ou sem erro).

## Decisão

| Status | Condição |
|--------|----------|
| `404` | `runId` não existe |
| `202` | Run em `UPLOADING`, `PENDING` ou `PROCESSING` |
| `200` | Run em `COMPLETED` ou `FAILED` |

**`202` retorna:** `runId`, `runStatus` e `createdAt` — para que o cliente saiba o que está esperando e há quanto tempo.

**`200` com `FAILED` retorna:** lista vazia + `errorMessage` — o estado é terminal, não vai mudar, então `200` é mais honesto que `202`.

## Justificativa

`202 Accepted` semânticamente significa "foi aceito e está sendo processado" — cobre os três estados intermediários. Retornar `200` com lista vazia nesses casos seria ambíguo: o cliente não saberia se o run terminou sem resultados ou se ainda está processando.

`FAILED` retorna `200` porque é um estado terminal — diferente de `PENDING/PROCESSING`, não vai mudar. Retornar `202` implicaria que ainda há algo acontecendo, o que seria enganoso.

O `202` enriquecido com `runId` e `createdAt` evita que o cliente precise de contexto externo para entender o que está esperando.
