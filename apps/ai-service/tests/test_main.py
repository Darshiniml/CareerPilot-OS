from fastapi.testclient import TestClient
import os

from app.main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/api/v1/ai/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "llm_provider" in data
    assert "embedding_provider" in data

def test_ready_endpoint():
    response = client.get("/api/v1/ai/ready")
    assert response.status_code == 200
    assert response.json()["status"] == "ready"

def test_metrics_endpoint():
    response = client.get("/api/v1/ai/metrics")
    assert response.status_code == 200
    data = response.json()
    assert "total_requests" in data

def test_providers_endpoint():
    response = client.get("/api/v1/ai/providers")
    assert response.status_code == 200
    data = response.json()
    assert "active_llm_provider" in data

def test_execute_parse_task():
    response = client.post(
        "/api/v1/ai/execute",
        json={
            "taskId": "123e4567-e89b-12d3-a456-426614174000",
            "taskType": "RESUME_PARSE",
            "payload": {"resumeId": "123", "userId": "abc"}
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "COMPLETED"

def test_create_document_api():
    response = client.post(
        "/api/v1/ai/documents",
        json={
            "title": "My Resume",
            "documentType": "RESUME",
            "content": "Worked as a Software Engineer at Google for 5 years."
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "VALIDATED"
    assert data["extractedMetadata"]["experienceYears"] == 5

def test_chunk_document_api():
    response = client.post(
        "/api/v1/ai/documents/chunks",
        json={
            "documentType": "RESUME",
            "content": "Worked as a Software Engineer at Google for 5 years.",
            "chunkSize": 50,
            "chunkOverlap": 10
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "chunks" in data
    assert len(data["chunks"]) > 0

def test_embed_chunks_api():
    response = client.post(
        "/api/v1/ai/documents/embeddings",
        json={
            "documentType": "RESUME",
            "chunks": ["Work experience sentence number one", "Second chunk text details"]
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "embeddings" in data
    assert len(data["embeddings"]) == 2

def test_search_retrieval_api():
    response = client.post(
        "/api/v1/ai/retrieval/search",
        json={
            "query": "Looking for python developers",
            "documentType": "JOB",
            "limit": 2
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "results" in data
    assert len(data["results"]) > 0
