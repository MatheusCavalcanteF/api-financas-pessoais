-- Regra de categorização automática: na importação, a transação cuja descrição
-- contém "keyword" (sem diferenciar maiúsculas nem acentos) recebe a categoria.
CREATE TABLE category_rules (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- CASCADE: regra sem categoria não tem o que aplicar. Se a categoria tiver
    -- transações, o RESTRICT de transactions barra o delete e a regra fica.
    category_id UUID         NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    keyword     VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Keyword curta demais casaria com quase toda descrição
    CONSTRAINT ck_category_rules_keyword_length CHECK (length(btrim(keyword)) >= 3)
);

-- Uma keyword por usuário: a mesma palavra em duas categorias seria ambígua.
-- Também serve de índice para carregar as regras do usuário na importação.
-- Variações só de acento ("café"/"cafe") são barradas pela aplicação.
CREATE UNIQUE INDEX uk_category_rules_user_keyword ON category_rules (user_id, lower(keyword));
-- Sem esse índice, apagar uma categoria varre a tabela para o CASCADE
CREATE INDEX ix_category_rules_category ON category_rules (category_id);
