# ADR-0001: Transações internas carregadas em memória como Map

**Status:** Aceito  
**Data:** 2026-04-22

## Contexto

Durante o processamento de reconciliação (`ProcessReconciliationUseCase`), cada linha do CSV precisa ser cruzada com uma transação interna pelo `transactionId`. O CSV pode ter até 500k linhas.

## Decisão

As transações internas são carregadas do banco **uma única vez** antes de iterar o CSV, indexadas em um `Map<UUID, InternalTransaction>` para lookup O(1) por `transactionId`.

## Alternativas consideradas

**Busca no banco por linha do CSV:** query individual para cada `transactionId` lido. Descartado — com 500k linhas, gera até 500k roundtrips ao banco.

**Lookup em batch por chunk do CSV:** processar o CSV em chunks de N linhas, coletar UUIDs e fazer `WHERE transaction_id IN (...)` por lote. Mantém o streaming do CSV e limita o uso de memória. Mais complexo de implementar.

## Trade-off aceito

O conjunto de transações internas fica inteiro em memória. Isso é aceitável porque o volume é delimitado pela janela de 7 dias usada na query — dados da própria empresa, com crescimento previsível.

## Quando revisar

Se o volume de transações internas crescer a ponto de pressionar a heap, migrar para o lookup em batch por chunk do CSV.
