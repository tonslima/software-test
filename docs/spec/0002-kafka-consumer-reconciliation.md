# Spec 0002 — Kafka Consumer: Processamento Assíncrono da Reconciliação

## Feature

Implementação do consumer Kafka e do fluxo completo de processamento da reconciliação, conforme [PRD 0002](../prd/0002-kafka-consumer-reconciliation.md).

## O que foi implementado

Um consumer Kafka escuta o tópico `settlement.reconciliation.requested`, recebe o `runId`, e executa o fluxo completo de reconciliação:

1. Busca o `ReconciliationRun` no banco
2. Faz download do CSV do S3 via streaming
3. Carrega todas as transações internas da janela `[referenceDate - 7 dias, referenceDate]` em memória, indexadas por `transaction_id`
4. Itera o CSV linha por linha, deduplicando por `transaction_id` (mantém primeira ocorrência)
5. Classifica cada linha em uma das quatro categorias usando `ReconciliationMatcher`
6. Persiste os resultados em batch (lotes de 500)
7. Adiciona entradas `UNRECONCILED_INTERNAL` para transações internas não presentes no CSV
8. Atualiza o status do run para `COMPLETED`
9. Calcula o percentual de discrepância e publica alerta em `settlement.reconciliation.events` se > 5%

Em caso de falha: rollback dos resultados, run marcado como `FAILED` com `error_message`.

## Contratos

Não há endpoint novo nesta feature — o fluxo é disparado pelo evento Kafka publicado pelo `POST /reconciliations` (PRD 0001).

**Tópico consumido:** `settlement.reconciliation.requested`
- Payload: `runId` (UUID como string)
- Group ID: `settlement-reconciliation`

**Tópico produzido (alerta):** `settlement.reconciliation.events`
- Payload: `{"runId":"<uuid>","discrepancyPercentage":<double>}`
- Publicado apenas quando `(mismatched + unreconciled_processor + unreconciled_internal) / total > 0.05`

## Decisões tomadas durante a implementação

**`ReconciliationResult` implementa `Persistable<UUID>` com `isNew() = true`**
Spring Data JPA, sem `Persistable`, detecta entidades novas pela presença do ID. Como o ID é sempre gerado no construtor (`UUID.randomUUID()`), nunca seria `null` — o JPA chamaria `merge` em vez de `persist`, gerando um `SELECT` desnecessário por linha. `isNew() = true` garante `persist` direto, essencial para performance no batch.

**`TransactionTemplate` em vez de `@Transactional` no use case**
O use case é invocado pelo consumer Kafka (sem contexto web). Usar `@Transactional` no método `execute()` englobaria inclusive as atualizações de status (`COMPLETED`, `FAILED`), causando conflito com o controle manual de status fora do bloco de processamento. O `TransactionTemplate` delimita a transação apenas em torno do processamento e persistência dos resultados.

**`KafkaTopicConfig` com declaração explícita dos tópicos**
Identificado durante testes: o producer publicava no tópico `settlement.reconciliation.events` mas recebia `UNKNOWN_TOPIC_OR_PARTITION` porque o tópico não existia. O Spring Kafka cria automaticamente tópicos declarados via `@Bean NewTopic` ao inicializar o `KafkaAdmin`. Ambos os tópicos (`requested` e `events`) foram declarados para garantir criação automática em qualquer ambiente.

**Seed data atualizado para 2026**
O seed original (`internal-transactions.csv`) usava datas de 2025. A validação de `ReconciliationRun` rejeita `referenceDate` com mais de 90 dias de antecedência — tornando impossível testar com `referenceDate=2025-03-15` a partir de 2026. As datas do seed foram atualizadas para 2026 para manter o happy path testável manualmente.

## Desvios do PRD

**Escopo da transação**
O PRD especificava que "todo o processamento (resultados + atualização de status) roda em uma única transação". Na implementação, as atualizações de status (`COMPLETED`, `FAILED`) acontecem fora da `TransactionTemplate`, em transações auto-gerenciadas pelo Spring Data. Apenas a persistência dos `ReconciliationResult`s ocorre dentro da transação explícita. O comportamento de rollback em falha está preservado: se o `transactionTemplate.execute` lançar exceção, os resultados são descartados e o status é marcado como `FAILED` em transação separada.

## Limitações conhecidas

- **Sem retry automático**: falhas deixam o run em `FAILED`; reprocessamento requer novo upload
- **Transações internas carregadas em memória**: para arquivos com janela de 7 dias muito populada (ex: milhões de transações internas), o `Map` em memória pode ser um gargalo — não é o cenário atual
- **Sem reprocessamento de run `COMPLETED`**: o consumer não valida se o run já foi processado; um evento duplicado no Kafka re-processaria o mesmo run

## Como testar

**Pré-requisitos:** infra rodando (`docker-compose up -d`), app iniciada.

1. Executar o request `POST /reconciliations` no `requests.http` (happy path com `referenceDate=2026-03-15`)
2. Aguardar o 202 e copiar o `runId` da resposta
3. Nos logs, verificar em ~1 segundo:
   - `INFO ReconciliationConsumer - Received reconciliation event for runId=<uuid>`
   - `WARN ProcessReconciliationUseCase - Discrepancy alert for runId=<uuid>: 30.00%`
4. No banco (`reconciliation_results`): 21 linhas para o `runId`
   - 15 `MATCHED` (maioria dos valores iguais ou dentro de ±R$0,01)
   - 1 `MISMATCHED` (`b4c5d6e7...` — CSV: 999.99, interno: 1050.00)
   - 5 `UNRECONCILED_INTERNAL` (transações do seed de 2026-03-13/14 ausentes no CSV)
5. Em `reconciliation_runs`: status `COMPLETED`
