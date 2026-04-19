---
name: write-a-prd
description: Cria um PRD para uma feature antes de implementar — entrevista o usuário, explora o código, e documenta intenção, decisões e escopo
allowed-tools: Read Glob Grep
---

Você vai criar um PRD para uma feature. Siga os passos abaixo, pulando os que não forem necessários.

## Passo 1: Entender o problema

Peça ao usuário uma descrição da feature que quer implementar. Se $ARGUMENTS estiver preenchido, use como ponto de partida.

## Passo 2: Explorar o repositório

Explore o repositório para entender o estado atual do código — o que já existe, o que vai precisar ser criado ou modificado.

## Passo 3: Entrevistar o usuário

Faça perguntas práticas e diretas sobre aspectos não claros da feature. Foque em:
- Edge cases que afetam a implementação
- Comportamentos esperados não documentados
- Decisões de design que têm trade-offs reais

Faça uma ou duas perguntas por vez. Quando tiver entendimento suficiente, avance.

## Passo 4: Esboçar componentes

Identifique os principais componentes que precisam ser criados ou modificados. Prefira componentes com interfaces simples que encapsulam bastante lógica e podem ser testados isoladamente.

Confirme com o usuário se os componentes fazem sentido.

## Passo 5: Escrever o PRD

Salve em `docs/prd/` com o formato `0001-nome-da-feature.md` (id sequencial). Use o template abaixo.

---

## Problema

O que essa feature resolve e por quê é necessária.

## O que será construído

Descrição objetiva do comportamento esperado — o que entra, o que sai, como o sistema se comporta.

## Requisitos

Lista numerada de requisitos funcionais. Seja específico — inclua validações, regras de negócio, respostas esperadas.

## Componentes

Lista dos módulos/classes que serão criados ou modificados, com uma linha descrevendo a responsabilidade de cada um.

## Decisões de implementação

Decisões técnicas tomadas antes de implementar:
- Escolhas arquiteturais
- Contratos de API (endpoints, request/response)
- Mudanças de schema
- Integrações (Kafka, S3, etc)

Não inclua caminhos de arquivo ou snippets de código — ficam obsoletos rápido.

## Decisões de teste

- O que constitui um bom teste para essa feature (teste comportamento, não implementação)
- Quais componentes terão testes unitários
- Quais fluxos terão testes de integração

## Fora do escopo

O que explicitamente não será feito nessa implementação.

## Notas

Qualquer contexto adicional relevante para quem for implementar.
