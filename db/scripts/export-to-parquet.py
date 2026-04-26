#!/home/miguel/github/ScribeRef/db/scripts/.venv/bin/python3
"""
Exports the article table to a Parquet file.

Embeddings are stored directly on the article row (embedding, embedding_cohere).

Prerequisites:
    pip install pandas pyarrow psycopg2-binary sqlalchemy

Usage:
    # Load env vars first, then run:
    set -a && source ../.env && set +a
    python export-to-parquet.py

    # Or override DB connection via env vars:
    LOCAL_DB_USER=myuser LOCAL_DB_PASSWORD=mypass python export-to-parquet.py
"""

import os
import sys

try:
    import pandas as pd
    import pyarrow as pa
    import pyarrow.parquet as pq
    from sqlalchemy import create_engine
except ImportError:
    print("ERROR: Required packages missing. Install with:")
    print("  pip install pandas pyarrow psycopg2-binary sqlalchemy")
    sys.exit(1)

DB_HOST     = os.getenv("DB_HOST", "localhost")
DB_PORT     = os.getenv("DB_PORT", "5432")
DB_NAME     = os.getenv("LOCAL_DB_NAME", "scribe-ref-db")
DB_USER     = os.getenv("LOCAL_DB_USER", "postgres")
DB_PASSWORD = os.getenv("LOCAL_DB_PASSWORD", "postgres")

OUTPUT_DIR  = os.getenv("PARQUET_OUTPUT_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "datasets"))

CHUNK_SIZE  = 10_000


def export_table(engine, query: str, output_path: str, label: str):
    print(f"Exporting {label} -> {output_path}")
    writer = None
    total = 0
    for chunk in pd.read_sql(query, engine, chunksize=CHUNK_SIZE):
        table = pa.Table.from_pandas(chunk, preserve_index=False)
        if writer is None:
            writer = pq.ParquetWriter(output_path, table.schema)
        writer.write_table(table)
        total += len(chunk)
        print(f"  {total} rows written...", end="\r")
    if writer:
        writer.close()
    print(f"  Done: {total} rows total.")


def main():
    url = f"postgresql+psycopg2://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
    print(f"Connecting to: postgresql://{DB_USER}:***@{DB_HOST}:{DB_PORT}/{DB_NAME}\n")
    engine = create_engine(url)

    export_table(
        engine,
        # Cast pgvector columns to text; omit generated tsv_summary column
        query="""
            SELECT id, site_id, author_id, published_date, published_date_confidence,
                   title, summary, article_hash,
                   link_to_archive, link_to_archive_trimmed, link_to_archive_image,
                   original_image_path, small_image_path,
                   embedding::text        AS embedding,
                   embedding_cohere::text AS embedding_cohere
            FROM article
        """,
        output_path=os.path.join(OUTPUT_DIR, "article.parquet"),
        label="article",
    )

    print("\nAll done!")


if __name__ == "__main__":
    main()