ALTER TABLE movies
    ADD COLUMN catalog_provider VARCHAR(40),
    ADD COLUMN catalog_id VARCHAR(64),
    ADD COLUMN overview TEXT,
    ADD COLUMN poster_url VARCHAR(1000),

    ADD CONSTRAINT ck_movies_catalog_reference
        CHECK (
            (catalog_provider IS NULL AND catalog_id IS NULL)
            OR
            (length(trim(catalog_provider)) > 0
                AND length(trim(catalog_id)) > 0)
        );

CREATE INDEX ix_movies_catalog_reference
    ON movies (catalog_provider, catalog_id);

CREATE TABLE movie_cast (
    movie_id BIGINT NOT NULL,
    billing_order INTEGER NOT NULL,
    actor_name VARCHAR(200) NOT NULL,

    CONSTRAINT pk_movie_cast
        PRIMARY KEY (movie_id, billing_order),

    CONSTRAINT fk_movie_cast_movie
        FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE,

    CONSTRAINT ck_movie_cast_actor_name_not_blank
        CHECK (length(trim(actor_name)) > 0)
);
