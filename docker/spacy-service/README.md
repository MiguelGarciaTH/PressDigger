# spaCy NER Service

**Port:** `8003`  
**Base Image:** `python:3.11-slim`  
**Framework:** FastAPI + Gunicorn + Uvicorn workers

## Description

A Named Entity Recognition (NER) microservice that extracts person names from article text. In the ScribeRef pipeline, this service is used by the **scribe-news-text-processor** via the `AuthorExtractor` component to identify the author of a news article. The text processor sends the raw article text (truncated to the first 2000 characters, where bylines typically appear) to this service, which returns all detected person entities. These candidates are then scored and ranked by the `AuthorExtractor` to determine the most likely author.

## Models

The service loads **two spaCy NER models** at startup:

### 1. `pt_core_news_lg` (Primary)

| Property         | Value                                  |
|------------------|----------------------------------------|
| Language         | Portuguese                             |
| Size             | ~500 MB                                |
| Pipeline         | NER (parser and lemmatizer disabled)   |
| Entity Labels    | `PER`, `ORG`, `LOC`, `MISC`           |
| Vectors          | 500k keys, 300 dimensions             |
| Version          | 3.7.0                                 |

### 2. `en_core_web_sm` (Fallback)

| Property         | Value                                  |
|------------------|----------------------------------------|
| Language         | English                                |
| Size             | ~12 MB                                 |
| Pipeline         | NER (parser and lemmatizer disabled)   |
| Entity Labels    | `PERSON`, `ORG`, `GPE`, etc.           |
| Version          | 3.7.0                                 |

### Why these models?

- **`pt_core_news_lg` (large)** — The largest Portuguese model available from spaCy, trained on a large corpus of Portuguese news text. The `lg` (large) variant includes full word vectors, providing significantly better entity recognition accuracy compared to the `sm` or `md` variants — essential for correctly identifying Portuguese person names with their characteristic accented characters and naming patterns (e.g., "António Costa", "Maria de Belém").
- **NER-focused loading** — The `parser` and `lemmatizer` pipeline components are disabled at load time (`disable=["parser", "lemmatizer"]`), reducing memory usage and improving inference speed since only NER is needed.
- **`en_core_web_sm` (small)** — A lightweight English fallback model for occasional English-language articles in the Arquivo.pt corpus. The `sm` variant is sufficient here since English NER is simpler and less frequently needed.
- **`--preload` flag** — The Gunicorn server uses `--preload` to load models once in the master process before forking workers, so models are shared via copy-on-write memory instead of each worker loading its own copy.

## API Endpoints

| Method | Path               | Description                            |
|--------|--------------------|----------------------------------------|
| GET    | `/health`          | Health check                           |
| GET    | `/ready`           | Readiness check (models loaded)        |
| POST   | `/extract-persons` | Extract person entities from text      |

### POST `/extract-persons`

**Request:**
```json
{
  "text": "O primeiro-ministro António Costa reuniu-se com Marcelo Rebelo de Sousa em Belém.",
  "lang": "pt"
}
```

**Response:**
```json
{
  "persons": [
    { "text": "António Costa", "start": 23, "end": 36 },
    { "text": "Marcelo Rebelo de Sousa", "start": 51, "end": 74 }
  ]
}
```

The `lang` parameter accepts `"pt"` (default) or `"en"` to select the appropriate model.

## Resource Requirements

- **Memory:** 2 GB (configured in docker-compose)
- **Workers:** 2 (Gunicorn with Uvicorn workers)
- **Disk:** ~520 MB (models)
- **Startup time:** 10–20 seconds (model loading)

## Notes

- The service uses `gunicorn` with `uvicorn.workers.UvicornWorker` for production-grade async serving with multiple workers.
- The `HEALTHCHECK` instruction in the Dockerfile ensures Docker monitors service availability.
- Models are installed via direct wheel URLs to avoid issues with the `spacy download` command in Docker builds.

