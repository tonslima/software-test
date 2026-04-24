# Glossário do Domínio — Reconciliação de Liquidações

## Categorias de reconciliação

Ao comparar as transações do arquivo de liquidação com os registros internos, cada transação é classificada em uma das seguintes categorias:

### Matched

Transação existe tanto no arquivo do processador quanto no sistema interno, e os valores estão de acordo (dentro da tolerância aceitável).

### Mismatched

Transação existe em ambos os lados, mas o valor do processador difere do valor interno **além da tolerância aceitável**. Essas discrepâncias precisam ser investigadas pelo time de operações.

### Unreconciled (processor)

Transação presente no arquivo do processador, mas que **não existe** nos registros internos do sistema. Pode indicar uma transação que não foi registrada internamente, ou um erro do processador.

### Unreconciled (internal)

Transação presente nos registros internos que **não apareceu** no arquivo do processador dentro do escopo temporal da reconciliação. Pode indicar que a liquidação ainda não ocorreu, ou que houve uma falha no processamento.

## Regras

### Tolerância

Diferenças de até **R$ 0,01** (um centavo) entre o valor do processador e o valor interno são consideradas aceitáveis e não configuram mismatch. Isso se deve a arredondamentos que ocorrem no processamento de pagamentos.

Exemplo: se o valor interno é `R$ 100,00` e o processador reporta `R$ 99,99`, a transação é classificada como **matched** (diferença de R$ 0,01, dentro da tolerância).

### Escopo temporal

A reconciliação compara o arquivo do processador com transações internas criadas em uma **janela de 7 dias** ancorada em uma **data de referência** (`referenceDate`).

#### Data de referência (`referenceDate`)

- É a **data à qual o arquivo CSV se refere** — ou seja, o dia de liquidação que aquele arquivo representa (o "dia do PaySettler"). **Não** é a data em que o upload está sendo feito, nem `now()` do servidor.
- Deve ser **informada explicitamente pelo cliente na request** que dispara a reconciliação. Não é derivada do conteúdo do arquivo (o CSV pode conter transações com `settled_at` ligeiramente antes/depois por causa de timezone e janelas de corte do processador).
- Formato: `ISO-8601 date` (`YYYY-MM-DD`), interpretada em **UTC** (mesmo timezone do `settled_at` do CSV).
- Validações:
  - Obrigatória.
  - Não pode ser uma data no futuro (em UTC).
  - Não pode ser mais antiga que 90 dias em relação a hoje (evita varreduras acidentais de histórico longo).

#### Janela de 7 dias

- A janela considerada é `[referenceDate - 7 dias, referenceDate]` (inclusiva nas duas pontas), aplicada sobre o `createdAt` das transações internas.
- Transações internas fora dessa janela **não** são consideradas como "unreconciled (internal)" — elas já foram (ou deveriam ter sido) reconciliadas em execuções anteriores.
- Transações do CSV que caem fora da janela ainda são processadas normalmente — o filtro temporal é aplicado **apenas no lado interno**, pra decidir o universo de candidatos a "unreconciled (internal)".

#### Persistência

- A `referenceDate` usada deve ser persistida no `ReconciliationRun` correspondente, junto com `createdAt` (timestamp real do sistema). Os dois conceitos são distintos:
  - `referenceDate` → tempo de **negócio** (qual dia está sendo reconciliado).
  - `createdAt` → tempo de **sistema** (quando o run foi executado).

### Reconciliation Run

Cada execução do processo de reconciliação é um **reconciliation run**. Um run tem:

- Um identificador único
- Timestamp de início e fim
- O nome/referência do arquivo processado
- O status do run (ex: em andamento, concluído, falhou)
- Os resultados (lista de transações categorizadas)

## Matching

A correspondência entre uma transação do processador e uma transação interna é feita pelo campo `transaction_id`. Ambos os sistemas usam o mesmo identificador.