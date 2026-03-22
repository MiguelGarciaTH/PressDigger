# Scribe News Embeddings Processor

A Spring Boot application that generates and stores vector embeddings for article content using the OpenAI API.

## Overview

This module processes article content by generating vector embeddings via OpenAI's `text-embedding-3-small` model. It chunks article text, creates 1024-dimensional semantic embeddings, and stores them in PostgreSQL with pgvector support for efficient similarity search.

## Features

- Generates 1024-dim vector embeddings for article chunks via OpenAI API
- Stores embeddings in PostgreSQL with pgvector
- Supports chunk-based processing
- Backfill mode to process existing articles without embeddings
- Enables semantic similarity search

## Technologies

- Spring Boot
- Spring Kafka
- OpenAI Java Client (`text-embedding-3-small`)
- PostgreSQL with pgvector extension
- Hypersistence Utils

## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Kafka broker settings
- `OPEN_IA_API_KEY` — OpenAI API key
- Chunking parameters
- Vector dimension settings (1024)

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
- OpenAI API key with access to `text-embedding-3-small`
