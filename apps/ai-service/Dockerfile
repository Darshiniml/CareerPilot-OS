FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

ENV LLM_PROVIDER=mock
ENV JWT_SECRET_KEY=default-very-secure-secret-key-that-is-at-least-256-bits-long-careerpilot-os-2026

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
