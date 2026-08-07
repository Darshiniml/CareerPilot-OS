from fastapi.testclient import TestClient
import os

from app.main import app, verify_jwt

client = TestClient(app)

# Override JWT verification for test context
def override_verify_jwt():
    return {"sub": "user@example.com"}

app.dependency_overrides[verify_jwt] = override_verify_jwt

def test_health_check():
    response = client.get("/api/v1/ai/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "llm_provider" in data
    assert "connections" in data

def test_planner_endpoint():
    response = client.post(
        "/api/v1/ai/planner",
        json={"intent": "Find python developer jobs"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "user_email" in data
    assert "plan" in data
    assert "[MOCK GENERATION]" in data["plan"]

def test_match_endpoint():
    response = client.post(
        "/api/v1/ai/match",
        json={"resume": "Python experience", "job_description": "We need python developer"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "user_email" in data
    assert "result" in data
    assert "[MOCK GENERATION]" in data["result"]
