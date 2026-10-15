# Diretrizes do projeto: API de Finanças Pessoais

Convenções para quem for contribuir. O frontend tem as próprias em [`frontend/CONTRIBUTING.md`](./frontend/CONTRIBUTING.md).

## Stack

- Java 25 + Spring Boot 4.1, Maven Wrapper
- PostgreSQL 16 + Flyway (o esquema é só do Flyway; o Hibernate roda com `ddl-auto: validate`)
- Spring Data JPA, e JDBC com `ON CONFLICT` na importação em lote
- Spring Security + JWT (jjwt)
- Testes: JUnit 5, Mockito e Testcontainers (PostgreSQL real)
- Documentação: OpenAPI (springdoc), desligada por padrão (`SWAGGER_ENABLED`)

## Regras de arquitetura e código

- **Pacotes por domínio:** `auth`, `user`, `category`, `transaction`, `report`, `security`, `shared`.
- **Isolamento de dados:** nenhum endpoint recebe `userId` por payload ou parâmetro. O usuário vem sempre do token (`@AuthenticationPrincipal AuthenticatedUser`).
- **Posse da entidade:** toda busca, alteração ou exclusão valida o dono na própria query (ex.: `findByIdAndUserId`). Recurso de outro usuário responde 404, igual a um inexistente.
- **Erros:** `@RestControllerAdvice` + `ProblemDetail` (RFC 7807), sem stacktrace na resposta.
- **Deduplicação da importação:** hash de data + valor + tipo + descrição normalizada + ocorrência, ou do `FITID` do OFX com a conta. Duplicatas são ignoradas por `UNIQUE (user_id, unique_hash)` + `ON CONFLICT DO NOTHING`.
- **Listagens:** paginadas com `Pageable`, tamanho máximo de 100.
- **Entrada:** só por DTOs (records) com Bean Validation; nunca aceitar campos que o usuário não deve controlar (ex.: `role`).
- **Dependências:** os overrides de versão no `pom.xml` (Tomcat e Jackson) saem quando o Spring Boot trouxer versões iguais ou maiores.

## Comandos

- Build: `./mvnw clean package -DskipTests` (Windows: `mvnw.cmd`)
- Testes: `./mvnw test` (requer Docker para o Testcontainers)
- CI: `.github/workflows/ci.yml` roda backend, frontend e E2E a cada pull request
