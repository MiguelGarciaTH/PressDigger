FROM python:3.11-slim

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Install dependencies first (better layer caching)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

RUN pip install --no-cache-dir gunicorn==22.0.0

# Run as non-root user
RUN useradd -m appuser
USER appuser

EXPOSE 8002

CMD ["gunicorn", "app:app", "-w", "6", "-k", "uvicorn.workers.UvicornWorker", "--bind", "0.0.0.0:8002"]