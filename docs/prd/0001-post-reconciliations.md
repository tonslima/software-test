# PRD 0001 — POST /reconciliations

## Problema

O serviço precisa receber arquivos CSV de liquidação enviados pelo PaySettler e iniciar o processo de reconciliação de forma assíncrona. Sem esse endpoint, não há como ingerir os dados externos que são o input central do sistema.

## O que será construído

Um endpoint `POST /reconciliations` que aceita um arquivo CSV e uma data de referência, valida a data, armazena o arquivo no S3, cria um `ReconciliationRun` avançando seu status até `PENDING` e publica um evento no Kafka para disparar o processamento assíncrono. Retorna `202 Accepted` com o `runId` gerado.

## Requisitos

1. O endpoint aceita `multipart/form-data` com dois campos: `file` (o CSV) e `referenceDate` (string `YYYY-MM-DD`).
2. `referenceDate` é obrigatória. Ausente ou malformada → `400 Bad Request`.
3. `referenceDate` não pode ser uma data no futuro (UTC) → `400 Bad Request`.
4. `referenceDate` não pode ser mais antiga que 90 dias em relação a hoje (UTC) → `400 Bad Request`.
5. `file` é obrigatório. Ausente → `400 Bad Request`.
6. O arquivo CSV é armazenado no S3 sem ser carregado inteiro em memória.
7. Um `ReconciliationRun` é criado com status `UPLOADING`, avança para `PENDING` após upload confirmado, ou `FAILED` em caso de erro no upload.
8. Um evento é publicado no Kafka com o `runId` para disparar o processamento assíncrono.
9. Retorna `202 Accepted` com corpo contendo o `runId`.
10. Erros de validação seguem o formato RFC 7807 (`ProblemDetail`).
11. Nenhuma validação de conteúdo do CSV ocorre nesta etapa — duplicatas e linhas malformadas são responsabilidade do consumer.

## Componentes

- **`ReconciliationController`** — recebe o multipart, faz binding de `referenceDate` como `LocalDate`, delega ao use case, retorna 202
- **`CreateReconciliationResponse`** — DTO com `runId: UUID`
- **`CreateReconciliationUseCase`** — orquestra criação do run, upload no S3, transição de status e publicação do evento Kafka via `TransactionTemplate`
- **`ReconciliationRun`** — entidade JPA com `id`, `status`, `referenceDate`, `createdAt`, `s3Key`; valida regras de negócio da data no construtor (`init` block)
- **`ReferenceDateException`** — exceção de domínio lançada pelo construtor de `ReconciliationRun` para violações de regra de negócio
- **`RunStatus`** — enum com os estados `UPLOADING`, `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`
- **`ReconciliationRunRepository`** — Spring Data JPA para persistência do run
- **`ReconciliationFileStorage`** — cliente S3 (LocalStack) responsável pelo upload do arquivo
- **`ReconciliationEventPublisher`** — publica evento no tópico Kafka com o `runId`
- **`GlobalExceptionHandler`** — `@ControllerAdvice` com handlers para `ReferenceDateException`, `MethodArgumentTypeMismatchException` e outros → `ProblemDetail`
- **Liquibase migration** — cria a tabela `reconciliation_runs`
- **`build.gradle.kts`** — dependências de Kafka (`spring-boot-starter-kafka`) e AWS SDK S3

## Decisões de implementação

**Contrato da API:**
- Método: `POST /reconciliations`
- Content-Type: `multipart/form-data`
- Campos: `file` (binário) e `referenceDate` (string `YYYY-MM-DD`)
- Resposta de sucesso: `202 Accepted` com `{ "runId": "<uuid>" }`
- Erros: `400 Bad Request` com `ProblemDetail` (RFC 7807)

**`referenceDate`** interpretada em UTC em todas as validações. O Spring faz o binding automático de `String` → `LocalDate` via `@DateTimeFormat(iso = ISO.DATE)` — formato inválido resulta em `MethodArgumentTypeMismatchException` → `400`.

**Separação de responsabilidades de validação:**
- Camada web: valida presença e formato (`@RequestParam` obrigatório + binding de tipo)
- Domínio: valida regras de negócio (futuro, >90 dias) no construtor de `ReconciliationRun`

**Máquina de status:**
```
UPLOADING → PENDING    (upload S3 bem-sucedido)
UPLOADING → FAILED     (upload S3 falhou)
```
O `runId` é gerado no use case antes da criação da entidade para garantir que a chave S3 e o ID do run sejam o mesmo UUID.

**Transações:** `TransactionTemplate` usado para controle programático — cada operação de persistência (`createRun`, `markAsFailed`, `confirmUpload`) roda em transação independente. Isso evita o problema de self-invocation do `@Transactional` via proxy AOP.

**Upload S3:** streaming direto do `InputStream` do `MultipartFile` — sem buffer em memória. A chave no S3 segue o padrão `reconciliations/runs/{runId}/input.csv`.

**Kafka:** evento publicado após transição para `PENDING` — se o Kafka falhar, o run fica em `PENDING` e o cliente recebe `500`. Não há retry automático nesta feature.

**Schema da tabela `reconciliation_runs`:**

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | UUID | PK |
| `status` | VARCHAR | `UPLOADING`, `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `reference_date` | DATE | Data de negócio |
| `created_at` | TIMESTAMPTZ | Timestamp de sistema |
| `s3_key` | VARCHAR | Referência ao arquivo no S3 |

**Arquivo `.http`:** criado na raiz do projeto para facilitar testes manuais na IDE.

## Decisões de teste

**Unitários:**
- `ReconciliationRunTest`: cobrir as regras de negócio da data diretamente no construtor da entidade (futuro, >90 dias, hoje válido, boundary de 90 dias)
- `CreateReconciliationUseCaseTest`: verificar que orquestra corretamente storage, repositório e publisher — dependências mockadas via Mockito; `TransactionTemplate` mockado para executar callbacks diretamente

**Integração:**
- Happy path completo: request multipart → `202` com `runId` → run persistido com status `PENDING` → evento publicado
- Usar H2 para persistência, mocks para S3 e Kafka
- Cenários de validação da `referenceDate`: ausente, malformada, futura, >90 dias

## Fora do escopo

- Validação do conteúdo do CSV (colunas, tipos, duplicatas)
- Processamento assíncrono (consumer Kafka) — feature separada
- Consulta de resultados e estatísticas — features separadas
- Idempotência por `referenceDate` (múltiplos uploads para a mesma data são permitidos)
- Retry automático de publicação no Kafka

## Notas

- A `referenceDate` é um conceito de negócio distinto do `createdAt` do sistema — ambos devem ser persistidos no run.
