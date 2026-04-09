# Especificação do Arquivo de Liquidação — Processador de Pagamentos

## Visão geral

O processador de pagamentos externo (PaySettler) envia diariamente um arquivo CSV contendo as transações liquidadas nas últimas 24 horas. Este arquivo é o input principal do processo de reconciliação.

## Formato do arquivo

- **Encoding:** UTF-8
- **Separador:** Vírgula (`,`)
- **Campos com vírgula:** Envolvidos em aspas duplas (`"`)
- **Header:** Primeira linha do arquivo contém os nomes das colunas

## Colunas

| Coluna | Tipo | Descrição | Exemplo |
|--------|------|-----------|---------|
| `transaction_id` | String (UUID) | Identificador único da transação | `550e8400-e29b-41d4-a716-446655440000` |
| `merchant_id` | String | Identificador do merchant | `MERCH_001` |
| `amount` | Decimal | Valor da transação em formato decimal | `152.30` |
| `currency` | String (ISO 4217) | Moeda da transação | `BRL` |
| `settled_at` | DateTime (ISO 8601) | Data/hora da liquidação em UTC | `2025-03-15T14:30:00Z` |
| `processor_reference` | String | Referência interna do processador | `PS-2025-00012345` |
| `status` | String | Status da liquidação | `SETTLED` |

## Status possíveis

- `SETTLED` — Transação liquidada normalmente
- `REVERSED` — Transação que foi revertida após liquidação (ex: chargeback, estorno)

## Observações importantes

- O campo `amount` sempre usa ponto como separador decimal, independente da moeda.
- O campo `settled_at` está sempre em UTC (sufixo `Z`).
- **Duplicatas:** Em raros casos, o processador pode enviar linhas duplicadas com o mesmo `transaction_id` no mesmo arquivo. Isso é um bug conhecido do lado deles que ainda não foi corrigido.
- Transações com status `REVERSED` também aparecem no arquivo e devem ser consideradas no processo de reconciliação.