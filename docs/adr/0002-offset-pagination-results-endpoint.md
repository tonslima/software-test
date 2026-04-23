# ADR-0002: Paginação offset-based no endpoint de resultados

**Status:** Aceito  
**Data:** 2026-04-22

## Contexto

O endpoint `GET /reconciliations/{runId}/results` pode retornar até 500k resultados por run. Paginação é necessária. Existem duas abordagens principais: offset-based e cursor-based.

## Decisão

Usar paginação offset-based (`page` + `size`) via `Pageable` do Spring Data.

## Alternativas consideradas

**Cursor-based (keyset pagination):** mais eficiente em páginas altas — em vez de `OFFSET N`, usa `WHERE id > cursor LIMIT size`, que tem custo constante independente da posição. Mais complexo de implementar e de consumir na API.

## Trade-off aceito

Offset-based sofre com performance em páginas altas (`OFFSET 490000` força o banco a ler e descartar 490k linhas). Porém, o caso de uso real é o time de operações investigando discrepâncias — eles vão filtrar por `category` (ex: só MISMATCHED), o que reduz drasticamente o conjunto. Chegar em páginas muito altas é improvável na prática.

O índice composto em `(run_id, category)` mitiga parte do custo.

## Quando revisar

Se o uso real mostrar que usuários navegam para páginas altas com frequência, ou se o filtro por category não for suficiente para reduzir o conjunto a tamanhos razoáveis.
