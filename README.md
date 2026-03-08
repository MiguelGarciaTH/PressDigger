# PressDigger

<p align="center">
  <img src="/scribe-news-web/public/pressdigger-logo.svg" alt="PressDigger" width="1000"/>
</p>


## Overview

PressDigger is a tool for journalists, researchers, and the general public to explore and analyze Portuguese Politicians (see db folder for all the people added) news articles from Arquivo.pt. The system provides powerful search capabilities, article summarization, and semantic analysis to help users discover relevant news content efficiently.

The inspiration for PressDigger was the microfilm reader machines that appeared on several american thriller movies, where police officers and detectives would search for news articles in a large archive of microfilms. PressDigger is a modern digital version of that concept, allowing users to search and analyze news articles from the comfort of their own devices.

Main features:

1. Search: The user can type keywords of phrases which want to search;
2. Text editor: The user can write a text and the system will return the most relevant news articles related to each paragraph of the text;
3. See public collections (based on most relevant keywords found in the articles);
4. See author collections: collection for the newspaper article authors; 
5. Create private collections with the articles the user wants;
6. Annotate articles with comments;
7. Web application display in Portugues and English languages;

The features 4, 5 and 6 required login (supported by google login);

PressDigger is a modular microservices-based system designed to crawl, process, analyze, and serve news articles from Portugal's web archive (Arquivo.pt). It leverages modern NLP techniques, including text embeddings and OpenAI's API, to enable intelligent article summarization and semantic search, through multiple Spring Boot microservices that communicate via Apache Kafka, along with Python-based auxiliary services for OCR and text embeddings, and a React-based web interface for end users.

NOTE: There are multiple references to "ScribeRef" in the codebase and documentation. This was the original project name during development, but the final product is branded as "PressDigger". The name "ScribeRef" may still appear in module names, package names, and some documentation, but it refers to the same system now called PressDigger.

## Architecture

### Backend processing pipeline

![PressDigger](/assets/PressDigger.jpg?raw=true)

### Backend REST Server + Frontend Web Application

![PressDigger](/assets/PressDigger2.jpg?raw=true)

## Project Structure

```
ScribeRef/
├── scribe-news-crawler/              # Article discovery service
├── scribe-news-image-processor/      # Image analysis service
├── scribe-news-text-processor/       # Text summarization service
├── scribe-news-embeddings-processor/ # Vector embeddings service
├── scribe-news-rest/                 # REST API service
├── scribe-news-lib/                  # Shared library
├── scribe-news-crawler-persons/      # Python person data crawler
├── scribe-news-web/                  # React web frontend
├── docker/                           # Docker compose + auxiliary services
│   ├── embedding-service/            # Text embedding service (port 8000)
│   ├── ocr-service/                  # OCR text detection service (port 8001)
│   ├── yake-service/                 # Keyword extraction service (port 8002)
│   ├── spacy-service/                # Named entity recognition service (port 8003)
│   └── scribe-ref-db-dev.yml         # Development environment compose file
├── db/                               # Database schema and seed data
├── images/                           # Downloaded article screenshots
│   ├── original/                     # Full-size images
│   └── small/                        # Thumbnail images
├── pom.xml                           # Parent Maven configuration
└── README.md                         # This file
```

## Components

### Backend Services (Spring Boot)

| # | Service | Description | Stack | Port | Docs |
|---|---------|-------------|-------|------|------|
| 1 | **scribe-news-crawler** | Discovers news articles from Arquivo.pt using keyword and site-based searches. Publishes discovered articles to Kafka for downstream processing. | Spring Boot, Kafka, Arquivo.pt API | — | [README](scribe-news-crawler/README.md) |
| 2 | **scribe-news-image-processor** | Processes article screenshots: blank page detection, OCR text filtering, auto-cropping and thumbnail generation. | Spring Boot, Kafka, Thumbnailator | — | [README](scribe-news-image-processor/README.md) |
| 3 | **scribe-news-text-processor** | Uses OpenAI's API to summarize article content, extract published dates and filter irrelevant articles. | Spring Boot, Kafka, OpenAI API | — | [README](scribe-news-text-processor/README.md) |
| 4 | **scribe-news-embeddings-processor** | Generates and stores 1024-dim vector embeddings for article chunks to enable semantic similarity search via pgvector. | Spring Boot, Kafka, PostgreSQL + pgvector | — | [README](scribe-news-embeddings-processor/README.md) |
| 5 | **scribe-news-rest** | RESTful API for text search, semantic search, image streaming, collections and user management. | Spring Boot, Spring Data JPA, pgvector | 8085 | [README](scribe-news-rest/README.md) |
| 6 | **scribe-news-lib** | Shared library with common domain models, repositories, Kafka publishers, rate limiting and embedding client. | Spring Data JPA, Spring Kafka | — | [README](scribe-news-lib/README.md) |

### Auxiliary Python Services (Docker)

| # | Service | Description | Model / Engine | Port | Docs |
|---|---------|-------------|----------------|------|------|
| 7 | **embedding-service** | Converts text to 1024-dim vectors for semantic search. Used by the embeddings processor and REST API at query time. | `intfloat/multilingual-e5-large` | 8000 | [README](docker/embedding-service/README.md) |
| 8 | **ocr-service** | Detects and extracts Portuguese text from article screenshots, filtering out blank and non-article images. | Tesseract OCR (`por`) | 8001 | [README](docker/ocr-service/README.md) |
| 9 | **yake-service** | Extracts the most relevant keywords and key-phrases from article summaries for tagging and collection generation. | YAKE (unsupervised, University of Porto) | 8002 | [README](docker/yake-service/README.md) |
| 10 | **spacy-service** | Named Entity Recognition to identify person names in article text, used for author extraction. | `pt_core_news_lg` + `en_core_web_sm` | 8003 | [README](docker/spacy-service/README.md) |

### Person Data Crawler

| # | Service | Description | Stack | Docs |
|---|---------|-------------|-------|------|
| 11 | **scribe-news-person-crawler** | Python script that crawls Portuguese government member data from Wikidata (ministers and secretaries from 1974–present). | Python 3, SPARQL, Wikidata | [README](scribe-news-crawler-persons/README.md) |

### Frontend

| # | Service | Description | Stack | Port | Docs |
|---|---------|-------------|-------|------|------|
| 12 | **scribe-news-web** | React web interface for searching, browsing, annotating articles and managing collections. | React 18, TypeScript, Vite, Tailwind CSS | 5173 | [README](scribe-news-web/README.md) |

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

Add an `.env` file in the project root with the following environment variables (replace placeholders with actual values):

```shell
IMAGE_PROCESSOR_CONCURRENCY=6
IMAGE_PROCESSOR_TOPIC="image-processor-topic"

TEXT_PROCESSOR_CONCURRENCY=6
TEXT_PROCESSOR_TOPIC="text-processor-topic"

EMBEDDING_PROCESSOR_CONCURRENCY=6
EMBEDDING_PROCESSOR_TOPIC="embedding-processor-topic"

OPEN_IA_API_KEY={THE_OPEN_IA_API_KEY}

GOOGLE_CLIENT_ID={THE_GOOGLE_GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET={THE_GOOGLE_CLIENT_SECRET}

IMAGES_BASE_PATH={THE_IMAGE_PATH_BASE_FOLDER}
```

Refer to individual component READMEs for detailed configuration options.