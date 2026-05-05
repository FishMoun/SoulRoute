CREATE TABLE IF NOT EXISTS react_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    chat_id VARCHAR(120) NOT NULL,
    task TEXT NOT NULL,
    answer TEXT,
    state VARCHAR(32) NOT NULL,
    failed BOOLEAN NOT NULL DEFAULT false,
    failure_type VARCHAR(80),
    failure_signal TEXT,
    created_skill_version_id BIGINT REFERENCES travel_skill_versions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS react_run_steps (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES react_runs(id) ON DELETE CASCADE,
    step_no INTEGER NOT NULL,
    thought TEXT,
    action VARCHAR(120),
    action_input TEXT,
    observation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_react_runs_chat_created ON react_runs(chat_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_react_runs_failure ON react_runs(failed, failure_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_react_run_steps_run ON react_run_steps(run_id, step_no);

CREATE TABLE IF NOT EXISTS skill_evaluation_runs (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES travel_skills(id) ON DELETE CASCADE,
    version_id BIGINT NOT NULL REFERENCES travel_skill_versions(id) ON DELETE CASCADE,
    evaluation_source VARCHAR(80) NOT NULL,
    score NUMERIC(6, 4) NOT NULL,
    passed BOOLEAN NOT NULL,
    metrics_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS skill_activation_history (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES travel_skills(id) ON DELETE CASCADE,
    previous_version_id BIGINT REFERENCES travel_skill_versions(id) ON DELETE SET NULL,
    active_version_id BIGINT NOT NULL REFERENCES travel_skill_versions(id) ON DELETE CASCADE,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
