CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    player_id UUID UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_accounts_player FOREIGN KEY (player_id) REFERENCES players (id)
);