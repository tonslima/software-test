# ADR-0003: Deduplicação de transações duplicadas no CSV

**Status:** Aceito  
**Data:** 2026-04-23

## Contexto

O spec do processador documenta que em raros casos o PaySettler pode enviar linhas duplicadas com o mesmo `transaction_id` no mesmo arquivo — bug conhecido do lado deles que ainda não foi corrigido.

A transação `b4c5d6e7` no arquivo de exemplo aparece duas vezes com dados idênticos.

## Decisão

A segunda ocorrência de um `transaction_id` é ignorada — apenas a primeira linha é processada. A deduplicação acontece no parser (`ApacheCsvParser`) usando um `Set` de IDs já vistos.

## Alternativas consideradas

**Reportar ambas:** gerar dois resultados para o mesmo `transaction_id`. Descartado — quebra a semântica do matching, que é 1:1 por `transaction_id`. Geraria dois `MISMATCHED` para a mesma transação, inflando métricas de discrepância.

**Somar os valores:** agregar as duplicatas antes do matching. Descartado — o processador enviou um bug, não uma intenção de duplicar o valor. Assumir semântica de soma seria uma interpretação não documentada.

**Lançar erro:** rejeitar o CSV inteiro ao encontrar duplicata. Descartado — o spec documenta isso como bug conhecido e recorrente. Rejeitar travaria o processo de reconciliação com frequência.

## Trade-off aceito

Ignorar a segunda ocorrência é a interpretação mais conservadora: processa o que é processável, descarta o ruído. O risco é que se as duas linhas tiverem dados diferentes (ex: valores distintos), a segunda é silenciosamente descartada. Na prática, duplicatas do PaySettler são linhas idênticas.

## Quando revisar

Se o PaySettler começar a enviar duplicatas com dados divergentes, será necessário uma estratégia mais elaborada (ex: logar o descarte, alertar o time de operações).
