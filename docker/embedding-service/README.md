# Embedding Service

**Port:** `8000`  
**Base Image:** `python:3.11-slim`  
**Framework:** FastAPI + Uvicorn

## Description

A text embedding microservice that converts text into dense 1024-dimensional vector representations. These embeddings enable **semantic search** across the ScribeRef article corpus — allowing users to search by meaning rather than exact keyword matching.

The service is consumed by:
- **scribe-news-embeddings-processor** — generates embeddings for each article chunk (sentence-level and 3-sentence chunks) at ingestion time, storing them in PostgreSQL via pgvector.
- **scribe-news-rest** — converts user search queries into embeddings at query time, enabling cosine-distance similarity search against the stored article chunk embeddings.

## Model

**[`intfloat/multilingual-e5-large`](https://huggingface.co/intfloat/multilingual-e5-large)**

| Property            | Value                           |
|---------------------|---------------------------------|
| Architecture        | XLM-RoBERTa (large)             |
| Dimensions          | 1024                            |
| Max Sequence Length  | 512 tokens                      |
| Languages           | 100+ (including Portuguese)     |
| Parameters          | ~560M                           |

### Why this model?

- **Multilingual support** — ScribeRef processes Portuguese news articles from Arquivo.pt, so a multilingual model is essential. E5-large covers 100+ languages with strong Portuguese performance, avoiding the need for a language-specific model.
- **High quality embeddings** — E5-large consistently ranks among the top multilingual embedding models on the MTEB benchmark, providing excellent semantic similarity scores.
- **Query/Passage prefixing** — The model uses a `query:` / `passage:` prefix convention that improves retrieval accuracy by distinguishing between search queries and stored documents. The service supports this via the `kind` parameter.
- **Normalized embeddings** — Embeddings are L2-normalized, making cosine similarity equivalent to dot product for efficient search with pgvector.

## API Endpoints

| Method | Path      | Description                        |
|--------|-----------|------------------------------------|
| GET    | `/health` | Health check                       |
| POST   | `/embed`  | Generate embedding for input text  |

### POST `/embed`

**Request:**
```json
{
  "text": "António Costa demite-se do cargo de primeiro-ministro",
  "kind": "query"
}
```

**Response:**
```json
{
  "embedding": [0.0123, -0.0456, ..., 0.0789]
}
```

The `kind` parameter accepts `"query"` (for search queries) or `"passage"` (for article text being indexed).

## Resource Requirements

- **Memory:** 4 GB (configured in docker-compose)
- **Disk:** ~2.2 GB for model weights (downloaded on first start)
- **Startup time:** 30–60 seconds (model loading)

