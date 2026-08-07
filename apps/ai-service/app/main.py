from fastapi import FastAPI, Depends, HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import jwt, JWTError
import os
from pydantic import BaseModel
import redis
from qdrant_client import QdrantClient

from app.core.llm_provider import get_llm_provider
from app.core.prompt_manager import PromptManager

app = FastAPI(
    title="CareerPilot OS AI Platform",
    description="Python FastAPI AI service providing multi-agent orchestration, LangGraph workflow execution, and semantic vector routing.",
    version="1.0.0",
    docs_url="/api/v1/ai/docs",
    openapi_url="/api/v1/ai/openapi.json"
)

security = HTTPBearer()

JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "default-very-secure-secret-key-that-is-at-least-256-bits-long-careerpilot-os-2026")
JWT_ALGORITHM = "HS256"

def verify_jwt(credentials: HTTPAuthorizationCredentials = Security(security)):
    token = credentials.credentials
    try:
        payload = jwt.decode(token, JWT_SECRET_KEY, algorithms=[JWT_ALGORITHM])
        return payload
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid token or expired token")

class PlannerRequest(BaseModel):
    intent: str

class MatchRequest(BaseModel):
    resume: str
    job_description: str

@app.get("/api/v1/ai/health")
def health_check():
    qdrant_status = "disconnected"
    redis_status = "disconnected"
    
    # Check Qdrant Connection
    try:
        qdrant_host = os.getenv("QDRANT_HOST", "localhost")
        qdrant_port = int(os.getenv("QDRANT_PORT", 6333))
        client = QdrantClient(host=qdrant_host, port=qdrant_port, timeout=1.0)
        client.get_collections()
        qdrant_status = "connected"
    except Exception as e:
        qdrant_status = f"error: {str(e)}"
        
    # Check Redis Connection
    try:
        redis_host = os.getenv("REDIS_HOST", "localhost")
        redis_port = int(os.getenv("REDIS_PORT", 6379))
        r = redis.Redis(host=redis_host, port=redis_port, socket_timeout=1.0)
        r.ping()
        redis_status = "connected"
    except Exception as e:
        redis_status = f"error: {str(e)}"

    # If mock provider is set and local dependencies are missing, we still report degraded status rather than crash
    return {
        "status": "healthy" if qdrant_status == "connected" and redis_status == "connected" else "degraded",
        "llm_provider": os.getenv("LLM_PROVIDER", "mock"),
        "connections": {
            "qdrant": qdrant_status,
            "redis": redis_status
        }
    }

@app.post("/api/v1/ai/planner")
def plan_action(request: PlannerRequest, user_data: dict = Depends(verify_jwt)):
    prompt_manager = PromptManager()
    llm = get_llm_provider()
    
    system_prompt = prompt_manager.get_prompt("planner", "system.txt")
    
    response = llm.generate(
        prompt="Orchestrate steps for intent: {intent}",
        system_prompt=system_prompt,
        variables={"intent": request.intent}
    )
    
    return {
        "user_email": user_data.get("sub"),
        "plan": response
    }

@app.post("/api/v1/ai/match")
def match_job(request: MatchRequest, user_data: dict = Depends(verify_jwt)):
    prompt_manager = PromptManager()
    llm = get_llm_provider()
    
    system_prompt = prompt_manager.get_prompt("matching", "system.txt")
    
    response = llm.generate(
        prompt="Analyze resume: {resume} against job: {job_description}",
        system_prompt=system_prompt,
        variables={
            "resume": request.resume,
            "job_description": request.job_description
        }
    )
    
    return {
        "user_email": user_data.get("sub"),
        "result": response
    }
