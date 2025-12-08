CREATE SEQUENCE IF NOT EXISTS site_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS site (
    id integer NOT NULL DEFAULT nextval('site_seq'),
    name varchar(255) NOT NULL,
    acronym varchar(5),
    url varchar(255) NOT NULL,

    CONSTRAINT site_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS keyword_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS keyword (
    id integer NOT NULL DEFAULT nextval('keyword_seq'),
    name varchar(255) NOT NULL,

    CONSTRAINT keyword_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS url_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS url (
    id integer NOT NULL DEFAULT nextval('url_seq'),
    "date" timestamp without time zone,
    url text NOT NULL,
    processed boolean,

    CONSTRAINT url_pk PRIMARY KEY (id)
);

CREATE SEQUENCE IF NOT EXISTS article_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS article (
    id integer NOT NULL DEFAULT nextval('article_seq'),
    "date" timestamp without time zone NOT NULL DEFAULT LOCALTIMESTAMP,
    site_id integer NOT NULL,
    title text NOT NULL,
    url text NOT NULL,
    url_trimmed text NOT NULL, -- for duplication lookup
    url_image text NOT NULL,
    url_text text NOT NULL,
    CONSTRAINT article_pk PRIMARY KEY (id),
    CONSTRAINT article_fk_site_id FOREIGN KEY (site_id) REFERENCES site(id)
);

CREATE SEQUENCE IF NOT EXISTS url_log_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS url_log (
    id integer NOT NULL DEFAULT nextval('url_log_seq'),
    site_id integer NOT NULL,
    keyword_id integer NOT NULL,
    "timestamp" timestamp without time zone NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT url_log_pk PRIMARY KEY (id),
    CONSTRAINT url_log_fk_site_id FOREIGN KEY (site_id) REFERENCES site(id),
    CONSTRAINT url_log_fk_keyword_id FOREIGN KEY (keyword_id) REFERENCES keyword(id)
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