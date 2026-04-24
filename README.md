# Settlement Reconciliation Service

## Contexto

Você está entrando no time de engenharia de uma fintech que processa liquidações de pagamentos para merchants. O desenvolvedor anterior saiu da empresa e deixou documentação sobre o serviço que estava construindo, mas não concluiu a implementação.

Sua missão: **construir o Settlement Reconciliation Service** — um serviço que compara transações liquidadas por um processador de pagamentos externo com os registros internos do nosso sistema, identificando discrepâncias.

O desenvolvedor anterior deixou notas, especificações e exemplos na pasta `docs/` deste repositório. **Explore esses arquivos antes de começar a implementar.**

## Requisitos

### Obrigatórios

- **Linguagem e framework:** Kotlin + Spring Boot
- **Banco de dados:** PostgreSQL (docker-compose fornecido)
- **Testes automatizados:** Testes unitários são obrigatórios. Testes de integração são bem-vindos.
- **Seed data:** As transações internas de exemplo (`docs/sample-files/internal-transactions.json`) devem ser carregadas no banco de forma automatizada (migration, seed script, ou outro mecanismo à sua escolha).

### Funcionalidades

1. **Upload de arquivo de liquidação:** Endpoint que recebe um arquivo CSV do processador de pagamentos junto com a `referenceDate` correspondente, e dispara o processo de reconciliação (veja `docs/processor-api-spec.md`).

2. **Reconciliação:** Comparação entre as transações do arquivo recebido e as transações internas do sistema, categorizando cada transação conforme as regras do domínio.

3. **Consulta de resultados:** Endpoint para consultar os resultados de uma reconciliação, com suporte a filtros (ex: por categoria, por merchant).

4. **Estatísticas:** Endpoint que retorna um resumo estatístico de uma execução de reconciliação (totais por categoria, percentuais, etc).

### Documentação complementar

As regras de negócio, especificações técnicas e exemplos de dados estão na pasta `docs/`. Recomendamos fortemente que você leia esses arquivos antes de começar:

- `docs/processor-api-spec.md` — Especificação do arquivo do processador
- `docs/domain-glossary.md` — Glossário e regras do domínio
- `docs/sample-files/` — Exemplos de dados
- `docs/architecture-notes.md` — Notas do desenvolvedor anterior

## Como começar

1. **Faça um fork** deste repositório para sua conta pessoal do GitHub.
2. Clone o seu fork e trabalhe nele normalmente.
3. Suba o banco de dados:

```bash
docker-compose up -d

# O PostgreSQL estará disponível em localhost:5432
# Database: settlement_db
# User: app
# Password: app
```

## Entrega

1. Ao finalizar, **abra um Pull Request do seu fork para o repositório original** (branch `main`).
2. Preencha as seções abaixo no README do seu PR com informações sobre sua implementação. Além do código, essa documentação é parte importante da avaliação.

### Como rodar

**Pré-requisitos:** Docker, Java 25

**1. Subir a infraestrutura**

```bash
docker-compose up -d
```

Isso inicia PostgreSQL (porta 5432), Kafka (porta 9092) e LocalStack S3 (porta 4566). O bucket S3 é criado automaticamente via `infra/localstack-init.sh`.

**2. Rodar a aplicação**

```bash
./gradlew bootRun
```

A aplicação sobe em `http://localhost:8080`. As migrations do banco e o seed de transações internas são aplicados automaticamente na inicialização.

**3. Rodar os testes**

```bash
./gradlew test
```

Não requer infraestrutura rodando — os testes de integração usam H2 em memória e mockam S3 e Kafka.

### Premissas e decisões

**Processamento assíncrono com Kafka**

O principal motivador foi o volume de linhas por CSV, que pode chegar a 500.000 em picos como Black Friday. Processar de forma síncrona bloquearia a thread do servidor e deixaria o cliente esperando até um possível timeout.

Avaliei três abordagens antes de decidir:

- **`@Async` do Spring**: simples, mas sem garantia de recuperação. Se o processo cair após o upload e antes do processamento terminar, o evento se perde.
- **Job de varredura com `@Scheduled`**: busca runs em `PENDING` periodicamente. Resolve a recuperação, mas complica o escalonamento horizontal porque precisaria de lock distribuído para evitar que dois pods processem o mesmo run ao mesmo tempo.
- **Kafka**: o evento é persistido no broker. Se o consumer cair, o evento continua disponível. O próprio design do Kafka (partições + group ID) garante que apenas um consumer no grupo processe cada evento, sem precisar de lock externo.

O S3 (LocalStack localmente) foi escolhido para armazenar o CSV antes do processamento. Desacopla o upload do consumer: o arquivo fica disponível independente de quando o consumer vai processá-lo, e evita trafegar o conteúdo pelo Kafka.

**`referenceDate` validada no domínio**

As regras de negócio (não pode ser futura, não pode ter mais de 90 dias) ficam no `init` block de `ReconciliationRun`. Qualquer camada que tente criar um run inválido recebe uma exceção, sem precisar duplicar a validação no controller ou no use case.

**`TransactionTemplate` em vez de `@Transactional`**

Cada operação de persistência roda em transação independente. O `@Transactional` em self-invocation não é interceptado pelo proxy AOP do Spring. No consumer Kafka, isso também separa as atualizações de status da transação dos resultados: em caso de falha, os resultados fazem rollback mas o status é atualizado corretamente em transação separada.

**Transações internas carregadas em memória**

As transações internas da janela de 7 dias são indexadas em um `Map<UUID, InternalTransaction>` antes de iterar o CSV. Evita uma query por linha. Para o volume atual é suficiente, mas é um ponto de atenção que comento na seção seguinte.

**`ReconciliationResult` com `Persistable<UUID>`**

Sem isso, o Spring Data JPA emitiria um `SELECT` por linha antes de decidir entre `persist` e `merge` (porque o ID nunca é nulo). Com `isNew() = true`, o `persist` é direto, o que faz diferença num batch de 500k linhas.

### Visão geral da arquitetura

O serviço segue uma arquitetura em camadas com separação explícita de responsabilidades:

```
br.com.marvin.api
  domain/
    model/        ← entidades JPA (ReconciliationRun, ReconciliationResult, InternalTransaction)
    vo/           ← enums (RunStatus, ReconciliationCategory)
  application/
    port/         ← interfaces de infraestrutura (FileStorage, CsvParser, EventPublisher)
    usecase/      ← orquestração e regras de aplicação
  infrastructure/
    persistence/  ← Spring Data JPA repositories
    storage/      ← S3FileStorage
    csv/          ← ApacheCsvParser
    messaging/    ← Kafka producers, consumer, topic config
  web/
    dto/          ← request/response objects
    mapper/       ← transformação entre camadas
  exception/      ← exceções de domínio + GlobalExceptionHandler
```

**Regras de dependência:** `domain` não conhece `infrastructure` nem `web`. `application` depende apenas de interfaces (`port`), nunca de implementações concretas. A injeção é feita via Spring DI.

**Fluxo principal:**

```
POST /reconciliations
  → valida referenceDate (domínio)
  → upload CSV → S3 via streaming
  → persiste ReconciliationRun (UPLOADING → PENDING)
  → publica runId → Kafka (settlement.reconciliation.requested)
  → retorna 202 com runId

Kafka Consumer
  → recebe runId
  → download CSV ← S3 via streaming
  → carrega transações internas (janela 7 dias) em Map
  → itera CSV linha a linha: deduplica, classifica por categoria
  → batch insert ReconciliationResult (lotes de 500)
  → atualiza run → COMPLETED ou FAILED
  → se discrepância > 5% → publica alerta (settlement.reconciliation.events)
```

### Documentação da API

Todos os erros seguem **RFC 7807** (`ProblemDetail`) — suporte nativo do Spring Boot.

#### `POST /reconciliations`

**Request:** `multipart/form-data`

| Campo | Tipo | Obrigatório |
|-------|------|-------------|
| `file` | CSV binário | Sim |
| `referenceDate` | `YYYY-MM-DD` | Sim |

| Status | Condição |
|--------|----------|
| `202` | Sucesso — `{ "runId": "uuid" }` |
| `400` | `referenceDate` ausente, malformada, futura ou com mais de 90 dias |
| `400` | `file` ausente |

---

#### `GET /reconciliations/{runId}/results`

**Query params:** `category` (múltiplos valores), `page` (default: `0`), `size` (default: `50`, máx: `200`)

| Status | Condição |
|--------|----------|
| `404` | `runId` não existe |
| `202` | Run em `UPLOADING` ou `PENDING` — `{ runId, runStatus, createdAt }` |
| `200` | Run `COMPLETED` — `{ runStatus, page, size, totalElements, totalPages, results[] }` |
| `200` | Run `FAILED` — igual ao `COMPLETED`, lista vazia e `errorMessage` preenchido |
| `400` | `size` acima do máximo |

Cada item de `results`: `{ transactionId, category, processorAmount, internalAmount }`

---

#### `GET /reconciliations/{runId}/stats`

| Status | Condição |
|--------|----------|
| `404` | `runId` não existe |
| `202` | Run em `UPLOADING` ou `PENDING` — `{ runId, runStatus, createdAt }` |
| `200` | Run `COMPLETED` ou `FAILED` — `{ runId, runStatus, totalTransactions, discrepancyRate, categories }` |

`categories` sempre retorna as 4 chaves (`MATCHED`, `MISMATCHED`, `UNRECONCILED_PROCESSOR`, `UNRECONCILED_INTERNAL`), zeradas quando não há resultados.

### O que faria diferente em produção

**Carregamento das transações internas em memória**

Esse é meu principal ponto de atenção. As transações da janela de 7 dias são carregadas num `Map` antes de iterar o CSV. No volume do case não foi problema, mas em produção faria um teste de carga antes de confiar nessa abordagem. Dependendo do resultado, avaliaria processamento em chunks ou outra estrutura.

**Outbox Pattern para publicação no Kafka**

Hoje o evento é publicado diretamente após o upload. Se o Kafka estiver indisponível naquele momento, o run fica preso em `PENDING` e o cliente recebe `500`. Com Outbox Pattern, o evento seria salvo na mesma transação do banco e publicado por um processo separado, garantindo consistência entre a persistência e a mensageria.

**Autenticação**

Desenvolvi assumindo que o serviço faz parte de um ecossistema de microsserviços com autenticação resolvida em outra camada (API Gateway ou service mesh). Em produção seria necessário configurar os filtros de segurança adequados ao ecossistema, mas não fazia sentido fazer isso sem contexto do ambiente.

**Configuração do S3**

O `endpointOverride` do LocalStack está nas properties sem separação por ambiente. Em produção o override sairia e as credenciais viriam de `DefaultCredentialsProvider` (IAM Role). Também revisaria a estrutura de diretórios no bucket: hoje é `reconciliations/runs/{runId}/input.csv`, mas uma organização por data pode facilitar lifecycle rules e auditoria dependendo do volume e das políticas de retenção.

**Observabilidade**

Expor métricas por run (volume de linhas processadas, tempo de processamento, taxa de discrepância) para o time de operações conseguir acompanhar o comportamento ao longo do tempo e pegar runs fora do padrão antes que virem incidente.

### Tempo gasto

Cerca de 6 dias. Nos dois primeiros foquei em entender o produto e os requisitos, e fui esboçando uma arquitetura. Lá pelo terceiro dia já tinha algo mais concreto e parti para a implementação. Os dias finais foram de revisão, ajustes e documentação.

### Ferramentas de IA utilizadas

Utilizei apenas o **Claude Code CLI**.

Configurei algumas skills customizadas para estruturar o workflow. A principal foi o `/grill-me`, que recentemente está bem famosa na comunidade: antes de cada feature, ela simula perguntas de um dev sênior para clarificar requisitos, edge cases e decisões de design antes de escrever qualquer código. Também usei `/write-a-prd` e `/write-a-spec` para a documentação de cada feature.

O Claude funcionou principalmente como executor, especialmente em trechos de código mais repetitivos, e como consultor técnico quando eu queria levantar prós e contras ou validar uma decisão. A tomada de decisão foi minha.

## Observações

- Você tem **7 dias** para completar o desafio.
- Valorizamos uma solução **completa, limpa e bem documentada** mais do que uma rica em features. Se o tempo estiver curto, reduza escopo, não qualidade.
- **Mantenha no repositório todos os arquivos de configuração de IA que você utilizou** (ex: `CLAUDE.md`, `.cursorrules`, `.github/copilot-instructions.md`, custom skills, prompts de sistema, ou qualquer outro arquivo de configuração de ferramentas de IA). Esses arquivos fazem parte da avaliação — queremos entender como você configura e direciona suas ferramentas.
- **Não inclua código malicioso no projeto.** É responsabilidade do candidato garantir que o repositório não contenha scripts ou dependências prejudiciais. Caso seja identificado qualquer código malicioso, o projeto será desconsiderado e não será avaliado.
- Após a entrega, faremos uma conversa técnica de ~45 minutos sobre sua implementação.
