# Scribe News Text Processor

A Spring Boot application that processes article text using OpenAI API for summarization and insight extraction.

## Overview

This module listens to Kafka messages containing article text and uses OpenAI's API to generate summaries and extract meaningful insights from the content. It leverages advanced NLP capabilities to analyze and process news articles.

## Features

- Processes article text from Kafka messages
- Generates article summaries using OpenAI API
- Extracts insights and key information
- Integrates with OpenAI's Java client
- Publishes processed text data downstream

## Technologies

- Spring Boot
- Spring Kafka
- OpenAI Java Client
- PostgreSQL (via scribe-news-lib)

## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Kafka broker settings
- OpenAI API key and endpoints
- Text processing parameters

## Running

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/scribe-news-text-processor-*.jar
```

## Requirements

- OpenAI API key (set via environment variable or configuration)
