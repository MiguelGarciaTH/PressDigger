# Scribe News Embeddings Processor

A Spring Boot application that generates and stores vector embeddings for article content using the Cohere or OpenAI API.

## Overview

This module processes articles by generating a single vector embedding per article using the full `title + summary` text. Embeddings are stored directly on the `article` table in PostgreSQL with pgvector support for efficient similarity search.

Two modes of operation:

- **Listener** (`TextEmbeddingListener`): Consumes articles from Kafka as they are crawled, saves them to the DB, and immediately computes + stores the article-level embedding.
- **Backfill** (`TextEmbeddingBackfill`): On startup, finds all articles with a `NULL` embedding and fills them in batches of 96.

## Features

- Generates 1024-dim vector embeddings (Cohere `embed-multilingual-v3.0`) or 2000-dim (OpenAI `text-embedding-3-large`) per article
- Uses asymmetric embedding: `search_document` input type at index time, `search_query` at query time (Cohere)
- Stores embeddings directly in the `article` table (`embedding` / `embedding_cohere` columns)
- Backfill mode processes existing articles without embeddings in batches
- Enables semantic similarity search with RRF hybrid scoring (cosine + BM25 via `tsv_summary`)

## Technologies

- Spring Boot
- Spring Kafka
- Cohere API (`embed-multilingual-v3.0`)
- OpenAI API (`text-embedding-3-large`)
- PostgreSQL with pgvector extension
- Hypersistence Utils

## Configuration

Configure the application using `application.properties` or environment variables:

| Variable | Description |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | Database credentials |
| `EMBEDDING_PROVIDER` | `cohere` or `openai` (default: `openai`) |
| `COHERE_API_KEY` | Cohere API key (required when provider is `cohere`) |
| `OPEN_AI_API_KEY` | OpenAI API key (required when provider is `openai`) |
| `EMBEDDING_PROCESSOR_TOPIC` | Kafka topic to consume articles from |
| `EMBEDDING_PROCESSOR_CONCURRENCY` | Kafka listener concurrency |

To enable the **backfill** mode set:
```properties
scribe-ref.arquivo.scribe-news-embeddings-processor-backfill.enable=true
```

To enable the **Kafka listener** mode set:
```properties
scribe-ref.arquivo.scribe-news-embeddings-processor.enable=true
```

## Running

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/scribe-news-embeddings-processor-*.jar
```

## Requirements

- PostgreSQL with pgvector extension installed
- Cohere API key with access to `embed-multilingual-v3.0`, or OpenAI API key
