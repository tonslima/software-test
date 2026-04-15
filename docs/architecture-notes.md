## Stack escolhida

- **Kotlin** com **Spring Boot 4**
- **PostgreSQL** para persistência
- **Liquibase** para migrations do banco
- **JPA/Hibernate** para acesso a dados

## O que foi feito

- Setup inicial do projeto Spring Boot
- docker-compose com PostgreSQL

## O que ficou pendente

- Implementação da lógica de reconciliação
- API REST (upload de CSV, consulta de resultados, estatísticas)
- Migrations do banco de dados
- Testes automatizados

## Preocupações com performance

Os arquivos de liquidação costumam ter entre **1.000 e 50.000 linhas** no dia a dia. Porém, em períodos de pico como **Black Friday**, já recebemos arquivos com até **500.000 linhas**. O serviço precisa aguentar isso sem cair.

## Pedido do time de operações

O time de operações pediu para ser notificado quando uma reconciliação encontrar um percentual alto de discrepâncias. A regra sugerida: **se mais de 5% das transações forem mismatch ou unreconciled, disparar uma notificação**.

Pensei em usar **Kafka** para isso — publicar um evento no tópico `settlement.reconciliation.events` com o resumo do run. Outros serviços (como o de alertas) poderiam consumir esses eventos. Mas não tive tempo de implementar.

Se alguém quiser implementar isso, fica como bônus. Se não, pelo menos documentar como faria já ajuda o time a planejar.