# Spec 0001 — POST /reconciliations

**PRD de referência:** `docs/prd/0001-post-reconciliations.md`

## O que foi implementado

Endpoint `POST /reconciliations` que recebe um arquivo CSV e uma data de referência via `multipart/form-data`, valida a data (formato e regras de negócio), armazena o arquivo no S3 via streaming, persiste um `ReconciliationRun` avançando de `UPLOADING` para `PENDING`, publica um evento no Kafka e retorna `202 Accepted` com o `runId`.

## Contratos de API

### POST /reconciliations

**Request**
- Content-Type: `multipart/form-data`
- Campos:
  - `file` (obrigatório) — arquivo CSV binário
  - `referenceDate` (obrigatório) — string no formato `YYYY-MM-DD`

**Response de sucesso**
- Status: `202 Accepted`
- Body:
```json
{ "runId": "550e8400-e29b-41d4-a716-446655440000" }
```

**Erros possíveis**

| Situação | Status | Detalhe |
|---|---|---|
| `referenceDate` ausente | `400` | `Required request parameter 'referenceDate' for method parameter type LocalDate is not present` |
| `referenceDate` malformada (ex: `"invalid"`) | `400` | `Invalid value for parameter 'referenceDate'` |
| `referenceDate` no futuro | `400` | `referenceDate cannot be in the future` |
| `referenceDate` > 90 dias atrás | `400` | `referenceDate cannot be older than 90 days` |
| `file` ausente | `400` | `Required part 'file' is not present.` |

Todos os erros seguem RFC 7807 (`ProblemDetail`).

## Decisões tomadas durante a implementação

**Binding de `referenceDate` como `LocalDate`**
O parâmetro é recebido diretamente como `LocalDate` no controller via `@DateTimeFormat(iso = ISO.DATE)`. O Spring converte automaticamente — formato inválido resulta em `MethodArgumentTypeMismatchException` → `400`, sem código extra.

**Validação de negócio no construtor da entidade**
As regras de futuro e >90 dias ficam no `init` block de `ReconciliationRun`. A exceção `ReferenceDateException` sobe até o `GlobalExceptionHandler` → `400`. Isso mantém o domínio como guardião das próprias regras e evita duplicação de validação em múltiplos pontos.

**Status `UPLOADING` como estado inicial**
A entidade é persistida com `UPLOADING` antes do upload começar. Isso permite rastrear runs que falharam durante o upload — distinguindo de `FAILED` (que indica falha no processamento assíncrono). O status avança para `PENDING` após upload confirmado, ou `FAILED` se o upload lançar exceção.

**`TransactionTemplate` no lugar de `@Transactional`**
Cada operação de persistência (`createRun`, `confirmUpload`, `markAsFailed`) roda em transação independente via `TransactionTemplate`. A razão: o use case precisa commitar a criação do run *antes* de iniciar o upload no S3 — com `@Transactional` em métodos privados do mesmo objeto, o proxy AOP não intercepta as chamadas internas (self-invocation).

**`runId` gerado no use case**
O UUID é gerado no use case antes de criar a entidade, para que a chave S3 (`reconciliations/runs/{runId}/input.csv`) e o `id` da entidade sejam o mesmo valor sem dependência do JPA para gerar o ID.

**Kafka: chave e valor são o `runId`**
O evento publicado no tópico `settlement.reconciliation.requested` usa o `runId` como chave e como valor (ambos como `String`). Simples e suficiente para o consumer localizar o run.

## Desvios do PRD

| Item do PRD | O que foi implementado | Motivo |
|---|---|---|
| `CreateReconciliationRequest` DTO com `referenceDate: String` | Sem DTO de request — binding direto no controller como `LocalDate` | Evita camada de mapeamento desnecessária |
| `ReferenceDateValidator` como componente separado | Deletado — regras movidas para o construtor de `ReconciliationRun` | Domínio como guardião das próprias regras |
| Run criado com status `PENDING` | Run criado com `UPLOADING`, avança para `PENDING` após upload | Permite rastrear falhas durante o upload |
| Dependência `spring-kafka` | Trocada por `spring-boot-starter-kafka` | O starter inclui autoconfiguration do `KafkaTemplate` |

## Limitações conhecidas

- **`S3Config` sem profile:** o endpoint, região e credenciais do S3 são sempre lidos de `application.properties` — não há separação por profile (local vs. prod). Em produção, o `endpointOverride` deveria ser omitido e as credenciais viriam de `DefaultCredentialsProvider` (IAM Role).
- **Sem retry no Kafka:** se o publish falhar, o run fica em `PENDING` sem evento publicado e não há mecanismo de retry ou dead-letter. O cliente recebe `500`.
- **Sem idempotência:** múltiplos uploads para a mesma `referenceDate` criam múltiplos runs. Não há deduplicação.
- **`status` como `VARCHAR(20)` sem constraint de enum:** o banco aceita qualquer string. Em produção, valeria adicionar um `CHECK` constraint ou usar um tipo `ENUM` nativo.

## Como testar

**Pré-requisitos**
```bash
docker-compose up -d
./gradlew bootRun
```

**Happy path** (via `requests.http` na raiz do projeto ou curl):
```bash
curl -X POST http://localhost:8080/reconciliations \
  -F "referenceDate=2026-04-20" \
  -F "file=@/caminho/para/settlement.csv"
# Esperado: 202 com { "runId": "..." }
```

**Validações:**
```bash
# referenceDate ausente → 400
curl -X POST http://localhost:8080/reconciliations -F "file=@settlement.csv"

# referenceDate malformada → 400
curl -X POST http://localhost:8080/reconciliations -F "referenceDate=abc" -F "file=@settlement.csv"

# referenceDate futura → 400
curl -X POST http://localhost:8080/reconciliations -F "referenceDate=2099-01-01" -F "file=@settlement.csv"

# referenceDate > 90 dias → 400
curl -X POST http://localhost:8080/reconciliations -F "referenceDate=2025-01-01" -F "file=@settlement.csv"

# file ausente → 400
curl -X POST http://localhost:8080/reconciliations -F "referenceDate=2026-04-20"
```

**Verificar persistência:**
```sql
SELECT * FROM reconciliation_runs ORDER BY created_at DESC LIMIT 5;
```

**Verificar arquivo no S3 (LocalStack):**
```bash
aws --endpoint-url=http://localhost:4566 s3 ls s3://settlement-files/reconciliations/runs/ --recursive
```

**Rodar testes:**
```bash
./gradlew test
```
