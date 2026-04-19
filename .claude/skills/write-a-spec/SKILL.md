---
name: write-a-spec
description: Documenta o que foi implementado em uma feature — lê o PRD correspondente e o código, registra o que foi construído, decisões tomadas e desvios do plano original
allowed-tools: Read Glob Grep
---

Você vai criar uma spec de implementação para uma feature já implementada.

## Passo 1: Localizar o PRD

Leia o PRD correspondente em `docs/prd/`. Se $ARGUMENTS estiver preenchido, use como referência para encontrar o arquivo correto.

## Passo 2: Explorar o código implementado

Leia o código relevante para a feature — controllers, use cases, domain, infra. Entenda o que foi realmente construído.

## Passo 3: Comparar PRD com implementação

Identifique:
- O que foi implementado conforme planejado
- O que mudou durante a implementação e por quê
- Decisões tomadas no momento da implementação que não estavam no PRD

## Passo 4: Escrever a spec

Salve em `docs/spec/` com o mesmo id e nome do PRD correspondente: `0001-nome-da-feature.md`.

---

## Feature

Nome e referência ao PRD correspondente.

## O que foi implementado

Descrição objetiva do que foi construído — comportamento real, não o planejado.

## Contratos de API

Para cada endpoint implementado:
- Método e path
- Request (campos, tipos, validações)
- Response (campos, tipos, status codes)
- Erros possíveis (status code + mensagem)

## Decisões tomadas durante a implementação

Decisões que surgiram no momento de codar — não estavam no PRD mas foram necessárias.

## Desvios do PRD

O que mudou em relação ao planejado e por quê.

## Limitações conhecidas

O que foi simplificado deliberadamente e o que a versão de produção precisaria ter.

## Como testar

Passos para verificar manualmente que a feature funciona — útil para revisão e para a entrevista.
