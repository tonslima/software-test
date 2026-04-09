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

A reconciliação compara o arquivo do processador com transações internas criadas nos **últimos 7 dias**. Transações internas mais antigas que 7 dias não devem ser consideradas como "unreconciled (internal)" — elas já foram (ou deveriam ter sido) reconciliadas em execuções anteriores.

### Reconciliation Run

Cada execução do processo de reconciliação é um **reconciliation run**. Um run tem:

- Um identificador único
- Timestamp de início e fim
- O nome/referência do arquivo processado
- O status do run (ex: em andamento, concluído, falhou)
- Os resultados (lista de transações categorizadas)

## Matching

A correspondência entre uma transação do processador e uma transação interna é feita pelo campo `transaction_id`. Ambos os sistemas usam o mesmo identificador.