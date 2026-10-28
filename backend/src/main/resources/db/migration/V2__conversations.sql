-- ---------------------------------------------------------------------------
-- Chat history: a user's conversations and the question/answer turns in them.
-- The full AskResponse (answer, sources, metrics) is stored as JSON so a past
-- chat re-renders exactly as it was first shown, citations included.
-- ---------------------------------------------------------------------------
CREATE TABLE conversations (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(100) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_updated ON conversations (username, updated_at DESC);

CREATE TABLE chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    question        TEXT        NOT NULL,
    response_json   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, id);
