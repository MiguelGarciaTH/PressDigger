# Scribe News REST API

A Spring Boot REST API for querying and retrieving news articles.

## Overview

This module provides a RESTful API for accessing articles stored in the database. It supports text search, semantic search using vector embeddings, and streaming of image content. The API is built with Spring Boot Web and supports CORS for cross-origin access.

## Features

- RESTful endpoints for article retrieval
- Text-based article search
- Semantic search using vector embeddings
- Image content streaming
- CORS support for cross-origin requests
- Pagination support

## Technologies

- Spring Boot Web
- Spring Data JPA
- PostgreSQL with pgvector
- Spring WebFlux (for streaming)

## API Endpoints

The API provides endpoints for:
- Retrieving articles by ID
- Searching articles by text
- Semantic search using embeddings
- Streaming image content
- Listing articles with pagination

## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Server port and context path
- CORS allowed origins
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
