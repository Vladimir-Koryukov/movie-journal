CREATE INDEX ix_diary_entries_user_status_id
    ON diary_entries (user_id, status, id DESC);

CREATE INDEX ix_diary_entries_user_rating
    ON diary_entries (user_id, rating)
    WHERE rating IS NOT NULL;

CREATE INDEX ix_movies_release_year
    ON movies (release_year)
    WHERE release_year IS NOT NULL;
