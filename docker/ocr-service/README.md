# OCR Service

**Port:** `8001`  
**Base Image:** `python:3.11-slim`  
**Framework:** FastAPI + Uvicorn

## Description

An Optical Character Recognition (OCR) microservice that detects and extracts text from images. In the ScribeRef pipeline, this service is used by the **scribe-news-image-processor** to determine whether a screenshot captured from Arquivo.pt contains meaningful readable text (i.e., is an actual news article page and not a blank page, an error page, or a non-textual image).

The image processor sends each downloaded screenshot to this service. If the OCR detects fewer than a configurable minimum number of valid words, the image is discarded — ensuring only genuine article screenshots are stored and forwarded for further processing.

## Model / Engine

**[Tesseract OCR](https://github.com/tesseract-ocr/tesseract)** (via `pytesseract` Python wrapper)

| Property         | Value                              |
|------------------|------------------------------------|
| Engine           | Tesseract 5.x                      |
| Languages        | Portuguese (`por`)                 |
| Type             | Traditional OCR (LSTM-based)       |
| License          | Apache 2.0                         |

### Why Tesseract?

- **Lightweight** — Unlike cloud-based OCR services, Tesseract runs locally with minimal resource usage, making it ideal for a high-throughput pipeline processing thousands of archived web screenshots.
- **Portuguese language pack** — The `tesseract-ocr-por` package provides pre-trained models specifically for Portuguese text recognition, matching the ScribeRef corpus language.
- **No external API dependency** — Runs entirely within the Docker container, avoiding rate limits, costs, and network latency of cloud OCR APIs.
- **Sufficient accuracy for detection** — The service primarily needs to answer "does this image contain text?" rather than produce perfect transcription, so Tesseract's accuracy is more than adequate.

## API Endpoints

| Method | Path            | Description                                        |
|--------|-----------------|----------------------------------------------------|
| GET    | `/health`       | Health check                                       |
| GET    | `/`             | Service info and available endpoints                |
| POST   | `/detect-text`  | Detect if an image contains readable text           |
| POST   | `/extract-text` | Extract all text content from an image              |

### POST `/detect-text`

Determines whether an uploaded image contains a minimum number of valid words.

**Request:** `multipart/form-data`
- `file` — Image file (PNG, JPEG, etc.)
- `min_words` — Minimum word count threshold (default: `20`)
- `language` — Tesseract language code (default: `"por"`)

**Response:**
```json
{
  "has_text": true,
  "word_count": 142,
  "min_words": 20,
  "confidence": 78.5,
  "sample_text": "António Costa demitiu-se do cargo de primeiro-ministro após..."
}
```

### POST `/extract-text`

Extracts all text from an uploaded image.

**Request:** `multipart/form-data`
- `file` — Image file
- `language` — Tesseract language code (default: `"por"`)

**Response:**
```json
{
  "text": "Full extracted text content...",
  "word_count": 142
}
```

## Word Validation

The service applies Portuguese-aware word validation:
- Words must be at least **3 characters** long.
- Words must contain at least one **Portuguese alphabetic character** (including accented characters: á, à, â, ã, é, ê, í, ó, ô, õ, ú, ç).
- OCR confidence scores are tracked and averaged per image.

## Resource Requirements

- **Memory:** ~200 MB
- **Disk:** ~50 MB (Tesseract + Portuguese language data)
- **Startup time:** < 5 seconds

