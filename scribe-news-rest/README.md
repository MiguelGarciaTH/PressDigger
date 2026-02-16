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

### 1. Get Article by ID

**Method:** `GET`  
**URL:** `/scribe-ref/article/{articleId}`  
**Path Parameters:**
- `articleId` (integer, required): The ID of the article to retrieve

**Response (200 OK):**
```json
{
  "id": 123,
  "title": "Article Title",
  "summary": "Article summary text",
  "publishedDate": "2024-01-15",
  "publishedDateConfidence": 0.95,
  "linkToArchive": "https://arquivo.pt/...",
  "linkToArchiveTrimmed": "https://arquivo.pt/...",
  "linkToArchiveImage": "https://arquivo.pt/.../image",
  "originalImagePath": "/path/to/original.jpg",
  "smallImagePath": "/path/to/small.jpg",
  "articleHash": 456789,
  "site": {
    "id": 1,
    "name": "Site Name",
    "url": "https://example.com",
    "acronym": "EX"
  }
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Article not found"
}
```

---

### 2. Search Articles

**Method:** `POST`  
**URL:** `/scribe-ref/search`  
**Query Parameters:**
- `page` (integer, optional, default: 0): Zero-based page number
- `size` (integer, optional, default: 20): Items per page

**Request Body:**
```json
{
  "text": "search query string"
}
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 123,
      "title": "Article Title",
      "summary": "Article summary",
      "publishedDate": "2024-01-15",
      "site": {
        "id": 1,
        "name": "Site Name",
        "url": "https://example.com",
        "acronym": "EX"
      }
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 100,
  "totalPages": 5
}
```

---

### 3. Get Image Stream

**Method:** `GET`  
**URL:** `/scribe-ref/images/{size}/{filename}`  
**Path Parameters:**
- `size` (string, required): Image size category (e.g., "original", "small", "thumbnail")
- `filename` (string, required): Image filename

**Response (200 OK):**
- Binary image stream (PNG/JPEG/etc.)
- Headers:
  - `Content-Type`: Auto-detected (image/png, image/jpeg, etc.)
  - `Cache-Control`: max-age=31536000 (365 days)
  - `Content-Length`: Size in bytes

**Error Response (404 Not Found):**
```json
{
  "error": "Image not found"
}
```

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
