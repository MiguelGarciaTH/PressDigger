from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
import yake
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="YAKE Keyword Extraction Service")


class ExtractionRequest(BaseModel):
    text: str
    language: str = "pt"
    max_keywords: int = 10
    max_ngram_size: int = 2
    deduplication_threshold: float = 0.7


class Keyword(BaseModel):
    keyword: str
    score: float


class ExtractionResponse(BaseModel):
    keywords: list[Keyword]


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/extract", response_model=ExtractionResponse)
async def extract_keywords(req: ExtractionRequest):
    logger.info(f"Received extraction request for text of length: {len(req.text)}")

    if not req.text or not req.text.strip():
        logger.warning("Empty text received")
        raise HTTPException(status_code=400, detail="Text must not be empty")

    try:
        extractor = yake.KeywordExtractor(
            lan=req.language,
            n=req.max_ngram_size,
            top=req.max_keywords,
            dedupLim=req.deduplication_threshold,
            windowsSize=2,
        )

        raw = extractor.extract_keywords(req.text)

        # YAKE returns (keyword, score) — lower score = more relevant
        keywords = [
            Keyword(keyword=kw, score=round(score, 6))
            for kw, score in raw
            if score < 0.3  # ← add this filter
        ]
        keywords.sort(key=lambda k: k.score)

        logger.info(f"Extracted {len(keywords)} keywords")
        return ExtractionResponse(keywords=keywords)
    except Exception as e:
        logger.error(f"Error during keyword extraction: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Extraction failed: {str(e)}")


@app.middleware("http")
async def log_requests(request: Request, call_next):
    logger.info(f"Request: {request.method} {request.url}")
    try:
        body = await request.body()
        logger.info(f"Request body length: {len(body)} bytes")
        if len(body) > 0:
            logger.debug(f"Request body preview: {body[:200]}")
    except:
        pass
    response = await call_next(request)
    logger.info(f"Response status: {response.status_code}")
    return response
