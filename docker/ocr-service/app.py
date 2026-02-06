from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
import pytesseract
from PIL import Image
import io
import re
import logging
import os

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="OCR Text Detection Service")

# Configure Tesseract
pytesseract.pytesseract.tesseract_cmd = '/usr/bin/tesseract'

# ... rest of your endpoints stay the same ...

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy"}

@app.post("/detect-text")
async def detect_text(
        file: UploadFile = File(...),
        min_words: int = 20,
        language: str = "por"
):
    """
    Detect if an image contains readable text.
    """

    try:
        # Read image
        contents = await file.read()
        image = Image.open(io.BytesIO(contents))

        logger.info(f"Processing image: {file.filename}, size: {image.size}")

        # Perform OCR with detailed output
        ocr_data = pytesseract.image_to_data(
            image,
            lang=language,
            output_type=pytesseract.Output.DICT
        )

        # Extract text
        full_text = pytesseract.image_to_string(image, lang=language)

        # Count valid words (Portuguese characters, min 3 letters)
        words = full_text.strip().split()
        valid_words = []
        confidences = []

        for i, word in enumerate(ocr_data['text']):
            conf = int(ocr_data['conf'][i])
            if conf > 0:  # Valid detection
                # Check if word has Portuguese characters and is at least 3 chars
                if len(word) >= 3 and re.search(r'[a-záàâãéêíóôõúçA-ZÁÀÂÃÉÊÍÓÔÕÚÇ]', word):
                    valid_words.append(word)
                    confidences.append(conf)

        word_count = len(valid_words)
        avg_confidence = sum(confidences) / len(confidences) if confidences else 0
        has_text = word_count >= min_words

        logger.info(f"Detected {word_count} words with avg confidence {avg_confidence:.1f}%")

        return {
            "has_text": has_text,
            "word_count": word_count,
            "min_words": min_words,
            "confidence": round(avg_confidence, 2),
            "sample_text": " ".join(valid_words[:10]) if valid_words else ""
        }

    except Exception as e:
        logger.error(f"Error processing image: {str(e)}")
        raise HTTPException(status_code=500, detail=f"OCR processing failed: {str(e)}")

@app.post("/extract-text")
async def extract_text(
        file: UploadFile = File(...),
        language: str = "por"
):
    """
    Extract all text from an image.
    """

    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents))

        # Extract text
        text = pytesseract.image_to_string(image, lang=language)

        # Count words
        words = text.strip().split()
        valid_words = [w for w in words if len(w) >= 3 and
                       re.search(r'[a-záàâãéêíóôõúçA-ZÁÀÂÃÉÊÍÓÔÕÚÇ]', w)]

        return {
            "text": text.strip(),
            "word_count": len(valid_words)
        }

    except Exception as e:
        logger.error(f"Error extracting text: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Text extraction failed: {str(e)}")

@app.get("/")
async def root():
    return {
        "service": "OCR Text Detection",
        "endpoints": {
            "/detect-text": "POST - Check if image has text",
            "/extract-text": "POST - Extract all text from image",
            "/health": "GET - Health check"
        }
    }

# This allows the port to be configured
if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", 8001))
    uvicorn.run(app, host="0.0.0.0", port=port)