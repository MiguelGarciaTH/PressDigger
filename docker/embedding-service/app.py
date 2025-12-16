from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

app = FastAPI()

model = SentenceTransformer("intfloat/multilingual-e5-base")

class EmbedRequest(BaseModel):
    text: str
    kind: str = "query"  # "query" or "passage"

@app.post("/embed")
def embed(req: EmbedRequest):
    text = f"{req.kind}: {req.text}"
    embedding = model.encode(
        text,
        normalize_embeddings=True
    )
    return {"embedding": embedding.tolist()}
