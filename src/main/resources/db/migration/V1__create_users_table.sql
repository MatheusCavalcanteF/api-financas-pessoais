-- "user" é palavra reservada no PostgreSQL, por isso a tabela é "users".
CREATE TABLE users (
    id         UUID         PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    -- Hash BCrypt tem 60 caracteres; folga para trocar o algoritmo no futuro.
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_users_email UNIQUE (email),
    -- A aplicação normaliza o e-mail; o banco garante que nada escape disso,
    -- senão "A@x.com" e "a@x.com" virariam duas contas.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);
