from fastapi import FastAPI, Depends, HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import jwt, JWTError
import os
from pydantic import BaseModel
from typing import Dict, Any, Optional
import time
import redis
from qdrant_client import QdrantClient

from app.core.config import settings
from app.core.llm_provider import get_llm_provider, llm_registry
from app.core.embedding_provider import get_embedding_provider, embedding_registry
from app.core.memory import memory_registry
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

# Internal metrics tracker
_metrics = {
    "total_requests": 0,
    "completed_tasks": 0,
    "failed_tasks": 0,
    "total_latency_ms": 0.0
}

def verify_jwt(credentials: HTTPAuthorizationCredentials = Security(security)):
    token = credentials.credentials
    try:
        payload = jwt.decode(token, JWT_SECRET_KEY, algorithms=[JWT_ALGORITHM])
        return payload
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid token or expired token")

class ExecuteTaskRequest(BaseModel):
    taskId: Optional[str] = None
    taskType: str
    payload: Optional[Dict[str, Any]] = None
    metadata: Optional[Dict[str, Any]] = None

class ExecuteTaskResponse(BaseModel):
    taskId: str
    status: str
    provider: str
    executionTimeMs: int
    result: Dict[str, Any]
    metadata: Dict[str, Any]

@app.post("/api/v1/ai/execute", response_model=ExecuteTaskResponse)
def execute_task(request: ExecuteTaskRequest):
    start_time = time.time()
    _metrics["total_requests"] += 1
    
    task_id = request.taskId or "mock-task-id"
    task_type = request.taskType
    payload = request.payload or {}
    
    result = {}
    provider_name = "mock"
    
    try:
        if task_type == "RESUME_PARSE":
            # Simulate parsing
            provider_name = settings.active_llm_provider
            llm = get_llm_provider()
            prompt_manager = PromptManager()
            
            # Simple metadata mock parsing simulation
            system_prompt = prompt_manager.get_prompt("matching", "system.txt")
            
            result = {
                "parsed": True,
                "resumeId": payload.get("resumeId"),
                "userId": payload.get("userId"),
                "extracted_skills": ["Java", "Python", "Docker"],
                "extracted_experience": [
                    {"company": "Google", "role": "Senior Engineer"}
                ]
            }
        elif task_type == "GENERATE_EMBEDDINGS":
            provider_name = settings.active_embedding_provider
            embedder = get_embedding_provider()
            vector = embedder.embed_query("Sample resume text for vector embedding generation")
            result = {
                "vector_dimension": len(vector),
                "preview": vector[:5]
            }
        elif task_type == "JOB_MATCH":
            provider_name = settings.active_llm_provider
            llm = get_llm_provider()
            result = {
                "match_score": 85.5,
                "keywords_found": ["Spring Boot", "FastAPI"],
                "matched": True
            }
        else:
            result = {
                "message": f"Generic execution completed for type: {task_type}"
            }
            
        execution_time = int((time.time() - start_time) * 1000)
        _metrics["completed_tasks"] += 1
        _metrics["total_latency_ms"] += execution_time
        
        return ExecuteTaskResponse(
            taskId=task_id,
            status="COMPLETED",
            provider=provider_name,
            executionTimeMs=execution_time,
            result=result,
            metadata={
                "model": settings.default_model,
                "temperature": settings.temperature
            }
        )
    except Exception as e:
        _metrics["failed_tasks"] += 1
        execution_time = int((time.time() - start_time) * 1000)
        raise HTTPException(
            status_code=500,
            detail=f"Task execution failed: {str(e)}"
        )

@app.get("/api/v1/ai/health")
def health_check():
    return {
        "status": "healthy",
        "llm_provider": settings.active_llm_provider,
        "embedding_provider": settings.active_embedding_provider
    }

@app.get("/api/v1/ai/ready")
def readiness_check():
    # Simple check that config variables and mock registries are loaded
    ready = len(llm_registry.get_registered_names()) > 0
    return {
        "status": "ready" if ready else "not_ready",
        "timestamp": time.time()
    }

@app.get("/api/v1/ai/metrics")
def metrics():
    avg_latency = 0.0
    if _metrics["completed_tasks"] > 0:
        avg_latency = _metrics["total_latency_ms"] / _metrics["completed_tasks"]
        
    return {
        "total_requests": _metrics["total_requests"],
        "completed_tasks": _metrics["completed_tasks"],
        "failed_tasks": _metrics["failed_tasks"],
        "avg_latency_ms": avg_latency
    }

@app.get("/api/v1/ai/providers")
def providers():
    return {
        "active_llm_provider": settings.active_llm_provider,
        "active_embedding_provider": settings.active_embedding_provider,
        "available_llm_providers": llm_registry.get_registered_names(),
        "available_embedding_providers": embedding_registry.get_registered_names(),
        "available_memory_providers": memory_registry.get_registered_names(),
        "loaded_prompt_versions": ["v1"]
    }
