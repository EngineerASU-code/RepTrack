CREATE TABLE IF NOT EXISTS relay_registrations (
    link_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('TRAINER', 'TRAINEE')),
    local_device_id TEXT NOT NULL,
    remote_device_id TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    chat_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (link_id, role)
);

CREATE INDEX IF NOT EXISTS relay_registrations_chat ON relay_registrations(chat_id);

CREATE TABLE IF NOT EXISTS telegram_routes (
    link_id TEXT PRIMARY KEY,
    trainer_chat_id TEXT NOT NULL,
    trainee_chat_id TEXT NOT NULL,
    trainer_device_id TEXT NOT NULL,
    trainee_device_id TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS telegram_routes_trainer ON telegram_routes(trainer_chat_id);
CREATE INDEX IF NOT EXISTS telegram_routes_trainee ON telegram_routes(trainee_chat_id);

CREATE TABLE IF NOT EXISTS telegram_updates (
    update_id INTEGER PRIMARY KEY,
    status TEXT NOT NULL,
    claimed_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS telegram_updates_claimed ON telegram_updates(claimed_at);

CREATE TABLE IF NOT EXISTS telegram_forwards (
    package_id TEXT PRIMARY KEY,
    link_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('RESULT', 'PROGRAM')),
    status TEXT NOT NULL CHECK (status IN ('RECEIVED', 'PDF_SENT', 'DONE')),
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS telegram_forwards_updated ON telegram_forwards(updated_at);
