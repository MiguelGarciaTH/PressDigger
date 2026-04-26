# Scribe News Embeddings Processor

A Spring Boot application that generates and stores vector embeddings for article content using the Cohere API.

## Overview

This module processes article content by generating vector embeddings via Cohere's `embed-multilingual-v3.0` model. It chunks article text, creates 1024-dimensional semantic embeddings, and stores them in PostgreSQL with pgvector support for efficient similarity search.

## Features

- Generates 1024-dim vector embeddings for article chunks via Cohere API
- Stores embeddings in PostgreSQL with pgvector
- Supports chunk-based processing
- Backfill mode to process existing articles without embeddings
- Enables semantic similarity search

## Technologies

- Spring Boot
- Spring Kafka
- Cohere Java Client (`embed-multilingual-v3.0`)
- PostgreSQL with pgvector extension
- Hypersistence Utils

## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Kafka broker settings
- `COHERE_API_KEY` — Cohere API key
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
- Cohere API key with access to `embed-multilingual-v3.0`
