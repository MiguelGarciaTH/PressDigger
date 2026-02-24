from fastapi import FastAPI
from pydantic import BaseModel
import spacy

app = FastAPI()

# Load models at startup
nlp_pt = spacy.load("pt_core_news_lg", disable=["parser", "lemmatizer"])
nlp_en = spacy.load("en_core_web_sm", disable=["parser", "lemmatizer"])

class Article(BaseModel):
    text: str
    lang: str = "pt"

@app.get("/health")
def health():
    return {"status": "ok"}

@app.get("/ready")
def ready():
    # Ensure models are loaded
    if nlp_pt and nlp_en:
        return {"status": "ready"}
    return {"status": "not_ready"}

@app.post("/extract-persons")
def extract_persons(article: Article):
    if article.lang == "en":
        doc = nlp_en(article.text)
    else:
        doc = nlp_pt(article.text)

    persons = []

    for ent in doc.ents:
        if ent.label_ in ["PERSON", "PER"]:
            persons.append({
                "text": ent.text,
                "start": ent.start_char,
                "end": ent.end_char
            })

    return {"persons": persons}