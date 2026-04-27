-- Migration 003: Shared lists, list items, participants, completed lists, copied_from
-- Covers PRD-0003, PRD-0004, PRD-0005, PRD-0006

-- Lists table
CREATE TABLE lists (
    id            SERIAL PRIMARY KEY,
    code          VARCHAR(6) UNIQUE NOT NULL,
    name          VARCHAR(100) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active', 'completed', 'deleted')),
    version       INTEGER NOT NULL DEFAULT 1,
    created_by    INTEGER REFERENCES users(id) ON DELETE SET NULL,
    copied_from   INTEGER REFERENCES lists(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- List items table
CREATE TABLE list_items (
    id            SERIAL PRIMARY KEY,
    list_id       INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    quantity      VARCHAR(50) DEFAULT '',
    observations  TEXT DEFAULT '',
    checked       BOOLEAN NOT NULL DEFAULT false,
    position      INTEGER NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- List participants table
CREATE TABLE list_participants (
    id            SERIAL PRIMARY KEY,
    list_id       INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at     TIMESTAMPTZ DEFAULT now(),
    UNIQUE(list_id, user_id)
);

-- Completed lists archive
CREATE TABLE completed_lists (
    id                 SERIAL PRIMARY KEY,
    original_list_id   INTEGER REFERENCES lists(id) ON DELETE SET NULL,
    code               VARCHAR(6),
    name               VARCHAR(100),
    completed_at       TIMESTAMPTZ NOT NULL,
    archived_data      JSONB
);

-- Indexes
CREATE INDEX idx_lists_code ON lists(code);
CREATE INDEX idx_list_items_list_position ON list_items(list_id, position);
CREATE INDEX idx_list_items_active ON list_items(list_id) WHERE checked = false;
CREATE INDEX idx_list_participants_list ON list_participants(list_id);
CREATE INDEX idx_list_participants_user ON list_participants(user_id);
CREATE INDEX idx_lists_copied_from ON lists(copied_from) WHERE copied_from IS NOT NULL;
CREATE INDEX idx_completed_lists_original ON completed_lists(original_list_id);