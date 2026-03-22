# Scribe News REST API

A Spring Boot REST API for querying and retrieving news articles.

## Overview

This module provides a RESTful API for accessing articles stored in the database. It supports text search, semantic search using vector embeddings, and streaming of image content. The API is built with Spring Boot Web and supports CORS for cross-origin access.

## Features

- RESTful endpoints for article retrieval
- Text-based article search
- Semantic search using OpenAI vector embeddings (`text-embedding-3-small`)
- Image content streaming
- CORS support for cross-origin requests
- Pagination support

## Technologies

- Spring Boot Web
- Spring Data JPA
- PostgreSQL with pgvector
- OpenAI Java Client (`text-embedding-3-small`)
- Spring WebFlux (for streaming)


## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Server port and context path
- CORS allowed origins
- `OPEN_IA_API_KEY` — OpenAI API key (used for both narrative generation and query embeddings)
- Pagination defaults

## Running

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/scribe-news-rest-*.jar
```

The API will be available at `http://localhost:8080` by default.
