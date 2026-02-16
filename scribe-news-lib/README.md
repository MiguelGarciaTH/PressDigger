# Scribe News Library

Shared library containing common models, repositories, and utilities used across all ScribeRef modules.

## Overview

This library module provides common functionality and domain models that are shared across all other modules in the ScribeRef project. It eliminates code duplication and ensures consistency across the application.

## Components

### Domain Models
- **Article**: Core article entity with metadata
- **Keyword**: Search keywords for article discovery
- **Site**: Configured sites to crawl
- **URL**: URL entities and references

### Repositories
- Database repositories for all domain models
- JPA repository interfaces with custom query methods

### Utilities
- **Kafka Publisher**: Utility for publishing messages to Kafka topics
- **Rate Limiting Service**: Manages API rate limits
- **Text Embedding Client**: Integration with text embedding services

## Technologies

- Spring Data JPA
- Spring Kafka
- PostgreSQL with pgvector support
- Hypersistence Utils for advanced Hibernate features

## Usage

Add as a dependency in other modules' `pom.xml`:

```xml
<dependency>
    <groupId>arquivo</groupId>
    <artifactId>scribe-news-lib</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Import and use the provided models, repositories, and utilities in your Spring Boot applications.
