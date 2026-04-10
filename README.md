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

1. **Upload de arquivo de liquidação:** Endpoint que recebe um arquivo CSV do processador de pagamentos e dispara o processo de reconciliação.

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

_Descreva os passos para compilar e executar a aplicação._

### Premissas e decisões

_Documente as ambiguidades que encontrou e as decisões que tomou. Explique o raciocínio por trás de cada escolha._

### Visão geral da arquitetura

_Descreva brevemente a estrutura do seu projeto. Um diagrama simples é bem-vindo, mas não obrigatório._

### Documentação da API

_Liste os endpoints, formatos de request/response, e códigos de erro._

### O que faria diferente em produção

_O que você simplificou deliberadamente? O que a versão de produção precisaria ter?_

### Tempo gasto

_Estimativa de quanto tempo você dedicou e como dividiu esse tempo._

### Ferramentas de IA utilizadas

_Quais ferramentas de IA você usou e para quê? Encorajamos o uso de IA — queremos entender como você a utiliza como ferramenta de trabalho._

## Observações

- Você tem **2 a 3 dias** para completar o desafio.
- Valorizamos uma solução **completa, limpa e bem documentada** mais do que uma rica em features. Se o tempo estiver curto, reduza escopo, não qualidade.
- **Mantenha no repositório todos os arquivos de configuração de IA que você utilizou** (ex: `CLAUDE.md`, `.cursorrules`, `.github/copilot-instructions.md`, custom skills, prompts de sistema, ou qualquer outro arquivo de configuração de ferramentas de IA). Esses arquivos fazem parte da avaliação — queremos entender como você configura e direciona suas ferramentas.
- **Não inclua código malicioso no projeto.** É responsabilidade do candidato garantir que o repositório não contenha scripts ou dependências prejudiciais. Caso seja identificado qualquer código malicioso, o projeto será desconsiderado e não será avaliado.
- Após a entrega, faremos uma conversa técnica de ~45 minutos sobre sua implementação.
