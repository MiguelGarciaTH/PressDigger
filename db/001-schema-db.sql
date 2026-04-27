CREATE EXTENSION vector;


CREATE SEQUENCE IF NOT EXISTS author_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS author (
    id integer NOT NULL DEFAULT nextval('author_seq'),
    name varchar(255) NOT NULL,

    CONSTRAINT author_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS site_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS site (
    id integer NOT NULL DEFAULT nextval('site_seq'),
    name varchar(255) NOT NULL,
    acronym varchar(5),
    url varchar(255) NOT NULL,

    CONSTRAINT site_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS person_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS person (
    id integer NOT NULL DEFAULT nextval('person_seq'),
    name varchar(255) NOT NULL,

    CONSTRAINT person_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS url_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS url (
    id integer NOT NULL DEFAULT nextval('url_seq'),
    "date" timestamp without time zone,
    site_id integer NOT NULL,
    person_name text,
    url text NOT NULL,
    processed boolean,

    CONSTRAINT url_pk PRIMARY KEY (id),
    CONSTRAINT url_fk_site_id FOREIGN KEY (site_id) REFERENCES site(id)
);

CREATE SEQUENCE IF NOT EXISTS article_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS article (
    id integer NOT NULL DEFAULT nextval('article_seq'),
    site_id integer NOT NULL,
    author_id integer,
    published_date timestamp without time zone,
    published_date_confidence double precision, -- epoch time
    title text NOT NULL,
    summary text NOT NULL,
    article_hash integer NOT NULL,
    link_to_archive text NOT NULL,
    link_to_archive_trimmed text NOT NULL, -- for duplication lookup
    link_to_archive_image text NOT NULL,
    original_image_path text NOT NULL,
    small_image_path text NOT NULL,

    -- Article-level embeddings (title + full summary, single vector per article)
    embedding vector(2000),                  -- OpenAI text-embedding-3-large (2000 dims)
    embedding_cohere vector(1024),           -- Cohere embed-multilingual-v3.0 (1024 dims)

    -- Full-text search over title + summary
    tsv_summary tsvector GENERATED ALWAYS AS (
        to_tsvector('portuguese', coalesce(title, '') || ' ' || coalesce(summary, ''))
    ) STORED,

    CONSTRAINT article_pk PRIMARY KEY (id),
    CONSTRAINT article_fk_site_id FOREIGN KEY (site_id) REFERENCES site(id),
    CONSTRAINT article_fk_author_id FOREIGN KEY (author_id) REFERENCES author(id)
);

DROP INDEX IF EXISTS idx_article_embedding;
DROP INDEX IF EXISTS idx_article_embedding_cohere;
DROP INDEX IF EXISTS idx_article_tsv_summary;

-- OpenAI text-embedding-3-large (2000 dims)
CREATE INDEX idx_article_embedding
    ON article USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Cohere embed-multilingual-v3.0 (1024 dims)
CREATE INDEX idx_article_embedding_cohere
    ON article USING hnsw (embedding_cohere vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for full-text search on article title + summary
CREATE INDEX idx_article_tsv_summary
    ON article USING gin (tsv_summary);

CREATE SEQUENCE IF NOT EXISTS keyword_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS keyword (
    id integer NOT NULL DEFAULT nextval('keyword_seq'),
    name varchar(255) NOT NULL,

    CONSTRAINT keyword_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS article_keyword_score_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE article_keyword_score (
    id integer NOT NULL DEFAULT nextval('article_keyword_score_seq'),
    article_id BIGINT NOT NULL,
    keyword_id integer NOT NULL,
    score DOUBLE PRECISION,

    CONSTRAINT article_keyword_score_pk PRIMARY KEY (id),
    CONSTRAINT article_keyword_score_fk_article_id FOREIGN KEY (article_id) REFERENCES article(id),
    CONSTRAINT article_keyword_score_fk_keyword_id FOREIGN KEY (keyword_id) REFERENCES keyword(id)
);


CREATE SEQUENCE IF NOT EXISTS url_log_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS url_log (
    id integer NOT NULL DEFAULT nextval('url_log_seq'),
    site_id integer NOT NULL,
    person_id integer NOT NULL,
    "timestamp" timestamp without time zone NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT url_log_pk PRIMARY KEY (id),
    CONSTRAINT url_log_fk_site_id FOREIGN KEY (site_id) REFERENCES site(id),
    CONSTRAINT url_log_fk_person_id FOREIGN KEY (person_id) REFERENCES person(id)
);

CREATE SEQUENCE IF NOT EXISTS rate_limiter_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS rate_limiter (
    id integer NOT NULL DEFAULT nextval('rate_limiter_seq'),
    "description" text NOT NULL,
    counter integer,
    counter_limit integer,
    sleep_time integer,
    locked boolean,
    "timestamp" timestamp without time zone NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT rate_limiter_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS metric_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS metric (
    id integer NOT NULL DEFAULT nextval('metric_seq'),
    "key" varchar(255),
    "value" bigint,
    "timestamp" timestamp without time zone NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT metric_pk PRIMARY KEY (id),
    CONSTRAINT metric_uq_key UNIQUE("key")
);

CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE "user" (
    id integer NOT NULL DEFAULT nextval('user_seq'),
    google_id   VARCHAR(255) NOT NULL,  -- from OAuth "sub"
    email       VARCHAR(255),
    name        VARCHAR(255),
    created_at  TIMESTAMP DEFAULT NOW(),
    openai_usage_count integer NOT NULL DEFAULT 0,
    openai_usage_last_timestamp TIMESTAMP,

    CONSTRAINT user_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS collection_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE collection (
    id integer NOT NULL DEFAULT nextval('collection_seq'),
    user_id     integer,
    name        VARCHAR(255) NOT NULL,
    description text NOT NULL,
    is_public   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW(),

    CONSTRAINT collection_pk PRIMARY KEY (id),
    CONSTRAINT collection_fk_user_id FOREIGN KEY (user_id) REFERENCES "user"(id)
);

CREATE SEQUENCE IF NOT EXISTS collection_article_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE collection_article (
    collection_id integer NOT NULL,
    article_id    integer NOT NULL,

    CONSTRAINT collection_article_pk PRIMARY KEY (collection_id, article_id),
    CONSTRAINT collection_article_fk_collection_id FOREIGN KEY (collection_id) REFERENCES collection(id),
    CONSTRAINT collection_article_fk_article_id FOREIGN KEY (article_id) REFERENCES article(id)
);

CREATE SEQUENCE IF NOT EXISTS annotation_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE annotation (
    id          integer NOT NULL DEFAULT nextval('annotation_seq'),
    user_id     integer,
    article_id  integer NOT NULL,
    text text   NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW(),

    CONSTRAINT annotation_pk PRIMARY KEY (id),
    CONSTRAINT annotation_fk_user_id FOREIGN KEY (user_id) REFERENCES "user"(id),
    CONSTRAINT annotation_fk_article_id FOREIGN KEY (article_id) REFERENCES article(id)
);