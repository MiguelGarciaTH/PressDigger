# Scribe News Crawler

A Spring Boot application that crawls news articles from the Arquivo.pt digital archive.

## Overview

This module searches for news articles using configured keywords and sites by querying the Arquivo.pt API. It discovers articles from the Portuguese web archive and publishes them to Kafka for downstream processing.

## Features

- Crawls articles from Arquivo.pt digital archive
- Supports keyword-based and site-based searching
- Extracts article metadata (titles, links, archived content references)
- Publishes discovered articles to Kafka topics
- Configurable search parameters

## Technologies

- Spring Boot
- Spring Kafka
- Arquivo.pt API
- PostgreSQL (via scribe-news-lib)

## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Kafka broker settings
- Arquivo.pt API endpoints
- Search keywords and sites

## Running

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/scribe-news-crawler-*.jar
```
