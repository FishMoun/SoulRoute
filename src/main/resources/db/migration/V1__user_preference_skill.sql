CREATE TABLE IF NOT EXISTS app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    display_name VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    departure_city VARCHAR(80),
    budget_level VARCHAR(40),
    travel_pace VARCHAR(40),
    companion_type VARCHAR(40),
    interests TEXT,
    dietary_restrictions TEXT,
    accommodation_preference VARCHAR(80),
    extra_notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    chat_id VARCHAR(120) NOT NULL,
    title VARCHAR(160) NOT NULL DEFAULT '新的旅行会话',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, chat_id)
);

CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    meta VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_conversations_user_updated ON conversations(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_conversation ON conversation_messages(conversation_id, created_at, id);

CREATE TABLE IF NOT EXISTS travel_skills (
    id BIGSERIAL PRIMARY KEY,
    skill_key VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    active_version_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS travel_skill_versions (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES travel_skills(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    prompt TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    source VARCHAR(80),
    metrics_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (skill_id, version_no)
);

ALTER TABLE travel_skills
    ADD CONSTRAINT fk_travel_skills_active_version
    FOREIGN KEY (active_version_id) REFERENCES travel_skill_versions(id);

CREATE TABLE IF NOT EXISTS skill_evolution_events (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT REFERENCES travel_skills(id) ON DELETE SET NULL,
    user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    source VARCHAR(80) NOT NULL,
    signal TEXT NOT NULL,
    proposal TEXT NOT NULL,
    created_version_id BIGINT REFERENCES travel_skill_versions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
