CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    username VARCHAR(32) NOT NULL UNIQUE,
    skill_rating INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS queue_entries (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    player_id UUID NOT NULL,
    skill_rating_at_queue INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    match_id UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS game_matches (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    player_one_id UUID NOT NULL,
    player_two_id UUID NOT NULL,
    player_one_rating INTEGER NOT NULL,
    player_two_rating INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS game_sessions (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    match_id UUID NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    maximum_participants INTEGER NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS session_participants (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    session_id UUID NOT NULL,
    player_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMPTZ,
    last_heartbeat_at TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_session_participant UNIQUE (session_id, player_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_queue_entries_active_player
    ON queue_entries (player_id)
    WHERE status = 'QUEUED';