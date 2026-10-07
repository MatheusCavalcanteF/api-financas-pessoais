CREATE TABLE transactions (
    id               UUID          PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- RESTRICT: categoria com transações não pode ser apagada (a API responde 409).
    -- Nullable porque transações importadas (CSV/OFX) chegam sem categoria.
    category_id      UUID          REFERENCES categories (id) ON DELETE RESTRICT,
    amount           NUMERIC(19,2) NOT NULL,
    transaction_date DATE          NOT NULL,
    description      VARCHAR(255)  NOT NULL,
    type             VARCHAR(10)   NOT NULL,
    -- SHA-256 em hex de data + valor + tipo + ocorrência + descrição normalizada
    unique_hash      VARCHAR(64)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Valor sempre positivo; o sentido vem de "type"
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT uk_transactions_user_hash UNIQUE (user_id, unique_hash)
);

-- Listagem paginada padrão: transações do usuário, mais recentes primeiro
CREATE INDEX ix_transactions_user_date ON transactions (user_id, transaction_date DESC);
-- Sem esse índice, apagar uma categoria varre a tabela para checar a FK
CREATE INDEX ix_transactions_category ON transactions (category_id);
