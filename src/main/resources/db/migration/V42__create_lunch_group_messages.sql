CREATE TABLE lunch_group_messages (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    sender_profile_id UUID NOT NULL,
    client_message_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_lunch_group_messages_sender_client_message
        UNIQUE (sender_profile_id, client_message_id),
    CONSTRAINT fk_lunch_group_messages_membership
        FOREIGN KEY (group_id, sender_profile_id)
        REFERENCES lunch_group_members (group_id, profile_id)
        ON DELETE CASCADE,
    CONSTRAINT lunch_group_messages_content_not_blank
        CHECK (content ~ '[^[:space:]]')
);

CREATE INDEX idx_lunch_group_messages_group_created
ON lunch_group_messages (group_id, created_at DESC, id DESC);
