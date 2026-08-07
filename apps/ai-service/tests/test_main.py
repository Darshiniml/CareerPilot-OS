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
            "payload": {
                "content": "Google\nSoftware Engineer\nSpring JS AWS Python"
            }
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "COMPLETED"
    assert "skills" in data["result"]
    
    # Assert raw -> canonical mappings work (AWS -> Amazon Web Services)
    skills = [s["skill"] for s in data["result"]["skills"]]
    assert "Amazon Web Services" in skills
    assert "Spring Framework" in skills
    assert "JavaScript" in skills

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

def test_resume_processing_flow():
    doc_id = "123e4567-e89b-12d3-a456-426614174321"
    # Process
    response = client.post(
        "/api/v1/ai/resume/process",
        json={
            "documentId": doc_id,
            "content": "Jane Doe\njane.doe@example.com\nGoogle\nSoftware Engineer\nPython JS AWS"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "knowledge" in data
    assert "ats" in data

    # Fetch knowledge
    response = client.get(f"/api/v1/ai/resume/{doc_id}")
    assert response.status_code == 200
    assert response.json()["personalInformation"]["name"] == "Jane Doe"

    # Fetch metadata
    response = client.get(f"/api/v1/ai/resume/{doc_id}/metadata")
    assert response.status_code == 200
    assert response.json()["status"] == "READY"

    # Fetch ATS
    response = client.get(f"/api/v1/ai/resume/{doc_id}/ats")
    assert response.status_code == 200
    assert "atsScore" in response.json()

def test_search_resumes_api():
    response = client.post(
        "/api/v1/ai/resume/search",
        json={
            "query": "React Developers",
            "documentType": "RESUME",
            "limit": 3
        }
    )
    assert response.status_code == 200
    assert "results" in response.json()
