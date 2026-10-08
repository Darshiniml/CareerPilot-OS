FROM python:3.12-slim

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY prompts ./prompts

RUN useradd --create-home --uid 10001 careerpilot
USER careerpilot

EXPOSE 8000

# No secrets or provider defaults are baked into the image: configure LLM_PROVIDER, LLM_MODEL,
# provider API keys, EMBEDDING_PROVIDER, QDRANT_URL and AI_SERVICE_TOKEN at runtime.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/api/v1/ai/health')"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
