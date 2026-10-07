\# Diretrizes do Projeto: API de Finanças Pessoais



\## Stack Tecnológico

\- Java 25 + Spring Boot 4.1.x

\- Gerenciador de dependências: Maven

\- Banco de Dados: PostgreSQL

\- Migrations: Flyway

\- ORM: Spring Data JPA / Hibernate

\- Segurança: Spring Security + JWT

\- Testes: JUnit 5, Mockito, Testcontainers (PostgreSQL)

\- Documentação: OpenAPI (Swagger)



\## Regras de Arquitetura e Código

\- Padrão de pacotes: Orientado a domínios (ex: `user`, `transaction`, `category`).

\- Isolamento de Dados: NENHUM endpoint deve receber o `userId` via payload ou parâmetro. O usuário atual DEVE ser extraído exclusivamente do `SecurityContext` (token JWT).

\- Todas as queries de busca, atualização ou deleção no JPA devem validar a posse da entidade (ex: `Optional<Transaction> findByIdAndUserId(...)`).

\- Tratamento de Erros: Usar `@RestControllerAdvice` e `ProblemDetail` (RFC 7807) para padronizar respostas de erro.

\- Prevenção de Duplicidade: Transações importadas (CSV/OFX) devem gerar um hash único (data + valor + descrição) salvo no banco para ignorar silenciosamente importações repetidas.

\- Paginação: Todos os endpoints de listagem (`GET /transactions`) devem retornar `Page<T>` usando `Pageable`.



\## Comandos Úteis

\- Build: `./mvnw clean package -DskipTests` (Windows: `mvnw.cmd`)

\- Testes: `./mvnw test` (requer Docker para o Testcontainers)

