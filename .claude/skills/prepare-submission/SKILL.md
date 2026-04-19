---
name: prepare-submission
description: Gera um rascunho das seções obrigatórias do README para o PR de entrega — lê os PRDs, specs, CLAUDE.md e código para montar a documentação final
allowed-tools: Read Glob Grep Bash
---

Você vai gerar um rascunho das seções obrigatórias do README para o Pull Request de entrega do case.

## Passo 1: Coletar contexto

Leia os seguintes arquivos:
- `CLAUDE.md` — arquitetura e padrões do projeto
- `docs/prd/` — todos os PRDs (features planejadas)
- `docs/spec/` — todas as specs (features implementadas)
- `README.md` — estrutura esperada do PR
- `docker-compose.yml` — para a seção "Como rodar"
- Código principal (controllers, use cases, domain) para entender o que foi construído

## Passo 2: Gerar rascunho

Produza um rascunho para cada seção obrigatória do README:

---

### Como rodar

Passos para compilar e executar a aplicação localmente, incluindo pré-requisitos (Docker, Java) e comandos exatos.

### Premissas e decisões

Liste as ambiguidades encontradas e as decisões tomadas — extraídas dos PRDs, specs e CLAUDE.md. Para cada decisão: o que foi escolhido, as alternativas consideradas, e o raciocínio.

### Visão geral da arquitetura

Descreva a estrutura do projeto: pacotes, camadas, fluxo principal (POST → S3 → Kafka → Consumer → PostgreSQL). Inclua um diagrama em texto simples se fizer sentido.

### Documentação da API

Para cada endpoint: método, path, request, response, status codes e erros. Extraído das specs.

### O que faria diferente em produção

O que foi simplificado deliberadamente nesse case e o que a versão de produção precisaria ter (observabilidade, autenticação, retry, DLQ, etc).

### Ferramentas de IA utilizadas

Liste as ferramentas de IA usadas e para quê — seja específico sobre o que foi gerado, revisado ou decidido com auxílio de IA. Inclua as skills configuradas no projeto (`.claude/skills/`) como parte da metodologia.

---

## Passo 3: Apresentar ao usuário

Apresente o rascunho completo e avise que ele deve:
1. Revisar cada seção — você não tem acesso a decisões informais que não foram documentadas
2. Preencher a seção "Tempo gasto" manualmente
3. Ajustar o tom para a própria voz antes de publicar
