CREATE TABLE IF NOT EXISTS discarded_bloom_filter_state (
    name               VARCHAR(100)  NOT NULL,
    bit_data           BYTEA         NOT NULL,
    filter_size        INTEGER       NOT NULL,
    num_hash_functions INTEGER       NOT NULL,
    added_elements     INTEGER       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP     NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT discarded_bloom_filter_state_pk PRIMARY KEY (name)
);

