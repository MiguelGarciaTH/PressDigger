CREATE EXTENSION vector;

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

    CONSTRAINT article_pk PRIMARY KEY (id),
    CONSTRAINT article_fk_site_id FOREIGN KEY (site_id) REFERENCES site(id)
);

CREATE SEQUENCE IF NOT EXISTS article_chunk_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE article_chunk (
    id BIGINT NOT NULL DEFAULT nextval('article_chunk_seq'),
    article_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('portuguese', content)) STORED,
    embedding vector(768) NOT NULL,

    CONSTRAINT article_chunk_pk PRIMARY KEY (id),
    CONSTRAINT article_chunk_fk_article_id FOREIGN KEY (article_id) REFERENCES article(id)
);

CREATE INDEX idx_chunks_tsv
ON article_chunk USING GIN (tsv);

CREATE INDEX idx_chunks_embedding
ON article_chunk USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

CREATE SEQUENCE IF NOT EXISTS article_chunk_medium_seq START WITH 1 INCREMENT BY 1;


CREATE TABLE article_chunk_medium (
    id BIGINT NOT NULL DEFAULT nextval('article_chunk_medium_seq'),
    article_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('portuguese', content)) STORED,
    embedding vector(768) NOT NULL,

    CONSTRAINT article_chunk_medium_pk PRIMARY KEY (id),
    CONSTRAINT article_chunk_medium_fk_article_id FOREIGN KEY (article_id) REFERENCES article(id)
);

CREATE INDEX idx_chunks_medium_tsv
ON article_chunk_medium USING GIN (tsv);

CREATE INDEX idx_chunks_medium_embedding
ON article_chunk_medium USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);


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