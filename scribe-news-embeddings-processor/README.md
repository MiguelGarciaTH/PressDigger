# Scribe News Embeddings Processor

A Spring Boot application that generates and stores vector embeddings for article content.

## Overview

This module processes article content by generating vector embeddings using a text embedding service. It chunks article text, creates semantic embeddings, and stores them in PostgreSQL with pgvector support for efficient similarity search.

## Features

- Generates vector embeddings for article chunks
- Uses text embedding service for semantic representation
- Stores embeddings in PostgreSQL with pgvector
- Supports chunk-based processing
- Enables similarity search and ML tasks

## Technologies

- Spring Boot
- Spring Kafka
- PostgreSQL with pgvector extension
- Text embedding service
- Hypersistence Utils

## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Kafka broker settings
- Text embedding service endpoints
- Chunking parameters
- Vector dimension settings

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
- Access to text embedding service
