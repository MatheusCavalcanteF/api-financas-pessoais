CREATE TABLE categories (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_categories_name_not_blank CHECK (btrim(name) <> '')
);

-- Nome único por usuário, ignorando maiúsculas: "Mercado" e "mercado" seriam
-- a mesma categoria. Também serve de índice para as buscas por user_id.
CREATE UNIQUE INDEX uk_categories_user_name ON categories (user_id, lower(name));
