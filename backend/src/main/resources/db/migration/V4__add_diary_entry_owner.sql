ALTER TABLE diary_entries
    ADD COLUMN user_id BIGINT NOT NULL;

ALTER TABLE diary_entries
    ADD CONSTRAINT fk_diary_entries_user
        FOREIGN KEY (user_id) REFERENCES app_users (id);

CREATE INDEX ix_diary_entries_user_id
    ON diary_entries (user_id, id);