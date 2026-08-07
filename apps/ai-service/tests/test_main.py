from fastapi.testclient import TestClient
import os

from app.main import app
from app.core.prompt_manager import PromptManager

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
    assert "available_llm_providers" in data
    assert "available_embedding_providers" in data

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
    assert "extracted_skills" in data["result"]

def test_prompt_manager_loader():
    manager = PromptManager()
    asset = manager.load_prompt_asset("planner", "v1", "system.txt")
    assert asset.metadata["version"] == "v1"
    formatted = asset.validate_and_format({"intent": "find work"})
    assert "find work" in formatted
