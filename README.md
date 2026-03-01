# ScribeRef

A comprehensive news article processing pipeline that discovers, processes, and provides semantic search capabilities for Portuguese news articles from the Arquivo.pt digital archive.

## Overview

ScribeRef is a modular microservices-based system designed to crawl, process, analyze, and serve news articles from Portugal's web archive (Arquivo.pt). The system leverages modern NLP techniques, including text embeddings and OpenAI's API, to enable intelligent article summarization and semantic search.

The project consists of multiple Spring Boot microservices that communicate via Apache Kafka, along with Python-based auxiliary services for OCR and text embeddings, and a React-based web interface for end users.

## Architecture

ScribeRef follows a microservices architecture with event-driven communication:

```
┌─────────────────┐
│  Arquivo.pt API │
└────────┬────────┘
         │
         ▼
┌──────────────────┐      ┌─────────────┐
│ News Crawler     │─────▶│   Kafka     │
└──────────────────┘      └──────┬──────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
┌──────────────────┐    ┌──────────────────┐   ┌──────────────────┐
│ Image Processor  │    │ Text Processor   │   │Embeddings        │
│                  │    │                  │   │Processor         │
└────────┬─────────┘    └────────┬─────────┘   └────────┬─────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 ▼
                         ┌──────────────┐
                         │  PostgreSQL  │
                         │  + pgvector  │
                         └──────┬───────┘
                                │
                                ▼
                ┌──────────────────────────────┐
                │    REST API    │   Web UI    │
                └──────────────────────────────┘
```

## Components

### Backend Services (Spring Boot)

#### 1. **scribe-news-crawler**
Discovers news articles from Arquivo.pt digital archive using keyword and site-based searches. Publishes discovered articles to Kafka for downstream processing.

- **Technology**: Spring Boot, Spring Kafka, Arquivo.pt API
- **[Documentation](scribe-news-crawler/README.md)**

#### 2. **scribe-news-image-processor**
Processes images from crawled articles with advanced image analysis including blank page detection, OCR text extraction, and auto-cropping.

- **Technology**: Spring Boot, Kafka, Thumbnailator, OCR libraries
- **[Documentation](scribe-news-image-processor/README.md)**

#### 3. **scribe-news-text-processor**
Leverages OpenAI's API to generate article summaries and extract meaningful insights from article content.

- **Technology**: Spring Boot, Kafka, OpenAI Java Client
- **[Documentation](scribe-news-text-processor/README.md)**

#### 4. **scribe-news-embeddings-processor**
Generates and stores vector embeddings for article content to enable semantic similarity search. Chunks article text and creates semantic representations stored in PostgreSQL with pgvector.

- **Technology**: Spring Boot, Kafka, PostgreSQL + pgvector, Hypersistence Utils
- **[Documentation](scribe-news-embeddings-processor/README.md)**

#### 5. **scribe-news-rest**
RESTful API for querying articles with support for text search, semantic search using vector embeddings, and image content streaming.

- **Technology**: Spring Boot Web, Spring Data JPA, PostgreSQL + pgvector
- **[Documentation](scribe-news-rest/README.md)**

#### 6. **scribe-news-lib**
Shared library containing common domain models, repositories, and utilities used across all backend services. Provides Kafka publishers, rate limiting, and text embedding client integrations.

- **Technology**: Spring Data JPA, Spring Kafka, Hypersistence Utils
- **[Documentation](scribe-news-lib/README.md)**

### Auxiliary Services

#### 7. **scribe-news-person-crawler**
Python script that crawls Portuguese government member data from Wikidata (ministers and secretaries from 1974-present) for reference and analysis.

- **Technology**: Python 3, SPARQL, Wikidata
- **[Documentation](scribe-news-crawler-persons/README.md)**

#### 8. **embedding-service** (Docker)
Python-based service that provides text embedding generation endpoints for semantic analysis.

- **Technology**: Python, FastAPI
- **Port**: 8000

#### 9. **ocr-service** (Docker)
OCR service for extracting text from images in news articles.

- **Technology**: Python, OCR libraries
- **Port**: 8001

### Frontend

#### 10. **scribe-news-web**
React-based web interface for searching and browsing news articles with a modern, responsive UI.

- **Technology**: React, TypeScript, Vite, Tailwind CSS
- **[Documentation](scribe-news-web/README.md)**

### Infrastructure

#### Database
PostgreSQL with pgvector extension for storing articles, metadata, and vector embeddings. Database schema and seed data are in the `db/` directory.

#### Message Broker
Apache Kafka for event-driven communication between microservices.

## Technology Stack

### Backend
- **Java 21**
- **Spring Boot 3.5.3**
- **Spring Data JPA**
- **Spring Kafka**
- **PostgreSQL 16** with **pgvector** extension
- **Maven** (build tool)

### Frontend
- **React 18**
- **TypeScript**
- **Vite** (build tool)
- **Tailwind CSS**

### Infrastructure
- **Apache Kafka 3.8.1**
- **Docker & Docker Compose**
- **Python 3** (auxiliary services)

### AI/ML
- **OpenAI API** (text summarization)
- **Text Embedding Models** (semantic search)
- **OCR libraries** (image text extraction)

## Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **Node.js 18+** (for web frontend)
- **Docker & Docker Compose**
- **PostgreSQL 16** with pgvector extension (or use Docker)
- **Apache Kafka** (or use Docker)
- **OpenAI API key** (for text processing)

## Quick Start

### 1. Start Infrastructure Services

Start PostgreSQL, Kafka, and auxiliary services using Docker Compose:

```bash
cd docker
docker-compose -f scribe-ref-db-dev.yml up -d
```

This will start:
- PostgreSQL with pgvector (port 5432)
- Apache Kafka (port 9092)
- Embedding service (port 8000)
- OCR service (port 8001)

### 2. Build All Modules

From the project root:

```bash
mvn clean install
```

### 3. Run Backend Services

Each service can be run independently:

```bash
# Start the crawler
cd scribe-news-crawler
mvn spring-boot:run

# Start the image processor
cd scribe-news-image-processor
mvn spring-boot:run

# Start the text processor
cd scribe-news-text-processor
mvn spring-boot:run

# Start the embeddings processor
cd scribe-news-embeddings-processor
mvn spring-boot:run

# Start the REST API
cd scribe-news-rest
mvn spring-boot:run
```

### 4. Run Frontend

```bash
cd scribe-news-web
npm install
npm run dev
```

The web interface will be available at `http://localhost:5173` (default Vite port).

## Configuration

Each component has its own `application.properties` or configuration file. Common configuration includes:

- **Database connection**: PostgreSQL connection settings
- **Kafka broker**: Kafka connection and topic configuration
- **OpenAI API**: API key for text processing
- **Arquivo.pt API**: Endpoints and search parameters
- **Embedding service**: Service endpoint URLs

Refer to individual component READMEs for detailed configuration options.

## Project Structure

```
ScribeRef/
├── scribe-news-crawler/           # Article discovery service
├── scribe-news-image-processor/   # Image analysis service
├── scribe-news-text-processor/    # Text summarization service
├── scribe-news-embeddings-processor/ # Vector embeddings service
├── scribe-news-rest/              # REST API service
├── scribe-news-lib/               # Shared library
├── scribe-news-person-crawler/    # Python person data crawler
├── scribe-news-web/               # React web frontend
├── docker/                        # Docker compose configurations
│   ├── embedding-service/         # Text embedding service
│   ├── ocr-service/              # OCR service
│   └── scribe-ref-db-dev.yml     # Development environment
├── db/                            # Database schema and seed data
├── pom.xml                        # Parent Maven configuration
└── README.md                      # This file
```

## Development

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for a specific module
cd scribe-news-rest
mvn test
```

### Building for Production

```bash
# Build all modules
mvn clean package

# Build specific module
cd scribe-news-rest
mvn clean package
```

JAR files will be generated in each module's `target/` directory.

## License

This project is part of the Arquivo.pt initiative for preserving and analyzing Portuguese web content.

## Contributing

Contributions are welcome! Please ensure:
- Code follows existing patterns and styles
- Tests are included for new features
- Documentation is updated accordingly

## Support

For issues and questions, please refer to the individual component READMEs or contact the Arquivo.pt team.
