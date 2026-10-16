# Segurança

O resumo das decisões de segurança está no [README](../README.md#decisões-de-segurança). Este documento detalha o rate limit atrás de proxy, o que precisa valer em produção e as limitações conhecidas.

## Como reportar uma vulnerabilidade

Não abra uma issue pública. Use o botão **Report a vulnerability** na aba [Security](https://github.com/MatheusCavalcanteF/api-financas-pessoais/security) do repositório: o relato fica privado entre você e o mantenedor até a correção ser publicada.

## Rate limit

`POST /auth/login` e `POST /auth/register` têm limite de tentativas por IP:

| Regra | Padrão |
| --- | --- |
| Falhas de login seguidas do mesmo e-mail + IP | 5 bloqueiam o par por 15 minutos. Um login com sucesso zera o contador |
| Tentativas de login por IP, qualquer e-mail | 20 por minuto |
| Tentativas de cadastro por IP | 5 por hora |

- **Resposta.** Estourou, a API responde `429` com `ProblemDetail` (título "Muitas tentativas") e o header `Retry-After` em segundos. Durante o bloqueio a senha nem é conferida, e a resposta é a mesma para e-mail cadastrado ou não.
- **Janela.** Os limites por IP usam janela deslizante: "20 por minuto" vale para qualquer intervalo de 60 segundos, não para o minuto do relógio.
- **Configuração.** Os números ficam em `security.rate-limit` no `application.yml`. Os contadores ficam em memória e expiram sozinhos, com teto de `max-tracked-keys` chaves por contador. Por isso cada instância da API conta separado e um restart zera tudo.

## IP do cliente e `TRUSTED_PROXIES`

Por padrão, o IP é o da conexão TCP (`getRemoteAddr()`), e os headers `X-Forwarded-For`, `Forwarded` e `X-Real-IP` são ignorados, porque qualquer cliente pode escrevê-los.

Quando a conexão vem de um IP listado em `TRUSTED_PROXIES`, a API lê o `X-Forwarded-For` da direita para a esquerda e usa o primeiro endereço que não é de proxy confiável. O que estiver à esquerda dele foi escrito pelo cliente e não vale. `server.forward-headers-strategy` fica desligado de propósito: ele confiaria no header vindo de qualquer IP de rede interna.

Isso só funciona se algum proxy do caminho **acrescentar** o IP real ao `X-Forwarded-For`. O proxy do Next (Route Handler `/api/[...path]` do frontend) não acrescenta: quando o cliente não manda o header, o Next o preenche com o IP da conexão; quando manda, o Next mantém o valor enviado pelo cliente, sem acrescentar nada. Como esse valor pode ter sido escrito pelo cliente, o header só é confiável com um proxy na frente do Next que acrescente o IP. Na prática:

- **Navegador → Next → API, sem nada na frente do Next.** Deixe `TRUSTED_PROXIES` vazio. Todos os usuários chegam com o IP do Next, então os limites por IP viram limites globais (20 logins por minuto e 5 cadastros por hora para o sistema inteiro), e 5 senhas erradas bloqueiam aquele e-mail para todo mundo. Se você colocar o IP do Next em `TRUSTED_PROXIES` nesse cenário, o cliente escolhe o próprio IP pelo header.
- **Navegador → proxy reverso → Next → API.** Use um proxy que acrescente o IP (nginx com `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`, ou um load balancer) e liste em `TRUSTED_PROXIES` o IP do Next e de qualquer outro proxy entre ele e a API. O Next também não pode ser acessível sem passar por esse proxy.

Prefira IPs exatos a faixas largas como `10.0.0.0/8`: qualquer coisa que abra conexão a partir de um IP da lista pode escolher o IP que a API vai enxergar.

## Em produção, a API não pode ficar acessível direto

A porta da API (8080) só deve aceitar conexões do Next ou do proxy, nunca da internet: rede interna do Docker sem `ports:` publicado, firewall ou security group. A confiança no `X-Forwarded-For` depende de quem consegue chegar à API vindo de um IP de `TRUSTED_PROXIES`. Um exemplo de como isso falha: com a porta publicada no host, o Docker pode trocar o IP de origem de quem vem de fora pelo IP do gateway da rede (ex.: `172.17.0.1`). Se esse IP estiver dentro de um CIDR de `TRUSTED_PROXIES`, qualquer pessoa na internet escolhe o próprio IP pelo header.

No cenário com proxy reverso, o mesmo vale para o Next: ele só pode ser acessado pelo proxy. Quem chega no Next sem passar pelo proxy escolhe o IP que a API enxerga, porque o Next mantém o `X-Forwarded-For` que o cliente mandou (só o preenche com o IP da conexão quando o header não vem) e a API confia no Next.

Também em produção:

- Defina `APP_ORIGIN` no frontend com a origem pública do app. Sem ela, toda mutação em `/api/*` é recusada com `403` (veja o [README do frontend](../frontend/README.md#variáveis-de-ambiente)).
- Sirva o app só por HTTPS. O frontend envia HSTS e marca o cookie de sessão como `Secure` quando roda em produção.
- Deixe `SWAGGER_ENABLED` ausente ou `false`.

## CSRF

A API desabilita a proteção CSRF do Spring Security (`SecurityConfig`) de propósito. Ela é stateless: a autenticação vem do JWT no header `Authorization: Bearer` (`JwtAuthenticationFilter`), a sessão é `STATELESS` e a API não lê nenhum cookie. O navegador não anexa esse header sozinho em requisições de outro site, então não há credencial ambiente que um site de terceiros consiga reaproveitar. Por isso o scanner de código aponta `java/spring-disabled-csrf-protection` como falso positivo.

O cookie de sessão existe só no frontend (`HttpOnly`, `SameSite=Strict`), e é lá que a defesa contra CSRF fica: todo `/api/*` do Next recusa `Sec-Fetch-Site: cross-site` e exige `Origin` igual a `APP_ORIGIN` fora de GET/HEAD.

Se a API passar a aceitar autenticação por cookie, a proteção CSRF dela precisa ser reativada.

## Limitações conhecidas

- **Logout não revoga o token.** A API é stateless: o logout apaga o cookie, mas um token copiado antes continua valendo até expirar (2h por padrão, `JWT_EXPIRATION`).
- **Rate limit em memória.** Cada instância da API conta separado e um restart zera os contadores. Com mais de uma instância, o limite efetivo se multiplica.
- **CSP com `'unsafe-inline'` em scripts.** As páginas do Next saem prerenderizadas e não usam nonce; a troca exigiria renderizar tudo por requisição.
- **Histórico do git.** Os primeiros commits tinham valores padrão de desenvolvimento para o segredo JWT e a senha do banco. Eles foram removidos e nunca devem ser usados em nenhum ambiente.
