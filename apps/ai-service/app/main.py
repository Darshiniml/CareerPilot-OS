from fastapi import FastAPI, Depends, HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import jwt, JWTError
import os
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
import time
import redis
from qdrant_client import QdrantClient

from app.core.config import settings
from app.core.llm_provider import get_llm_provider, llm_registry
from app.core.embedding_provider import get_embedding_provider, embedding_registry
from app.core.memory import memory_registry
from app.core.prompt_manager import PromptManager

# Knowledge Platform imports
from app.knowledge.factory.processors import document_factory

# Resume Intelligence imports
from app.resume.parser import BaseResumeParser
from app.resume.extractors import ResumeExtractor
from app.resume.ats import ATSAnalysisService

# Company Intelligence imports
from app.company.parser import CompanyParser
from app.company.extractors import CompanyExtractor, TechnologyExtractor
from app.company.insights import CompanyInsightsService

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
    "total_latency_ms": 0.0,
    "documents_processed": 0,
    "chunk_count": 0
}

# In-memory document storage cache for process endpoints
_resume_db: Dict[str, Dict[str, Any]] = {}
_company_db: Dict[str, Dict[str, Any]] = {}

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

class IngestDocumentRequest(BaseModel):
    title: str
    documentType: str
    content: str

class ChunkDocumentRequest(BaseModel):
    documentType: str
    content: str
    chunkSize: Optional[int] = 500
    chunkOverlap: Optional[int] = 100

class EmbedChunksRequest(BaseModel):
    documentType: str
    chunks: List[str]

class RetrievalSearchRequest(BaseModel):
    query: str
    documentType: str
    limit: Optional[int] = 5
    filters: Optional[Dict[str, Any]] = None

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
            provider_name = settings.active_llm_provider
            parser = BaseResumeParser()
            extractor = ResumeExtractor()
            sections = parser.parse(payload.get("content", "Sample resume content"))
            result = extractor.extract_all(sections)
        elif task_type == "GENERATE_EMBEDDINGS":
            provider_name = settings.active_embedding_provider
            embedder = get_embedding_provider()
            vector = embedder.embed_query(payload.get("chunkText", "Sample text"))
            result = {
                "vector_dimension": len(vector),
                "preview": vector[:5]
            }
        elif task_type == "JOB_MATCH":
            provider_name = settings.active_llm_provider
            # Acts as the ATS Analyzer
            ats_service = ATSAnalysisService()
            result = ats_service.analyze(payload.get("knowledge", {}))
        elif task_type == "COMPANY_PARSE":
            provider_name = settings.active_llm_provider
            parser = CompanyParser()
            extractor = CompanyExtractor()
            sections = parser.parse(payload.get("content", ""))
            result = extractor.extract_all(sections)
        elif task_type == "COMPANY_METADATA":
            provider_name = "taxonomy-service"
            knowledge = payload.get("knowledge", {})
            tech_stack_dict = knowledge.get("technologyStack", {})
            tech_list = tech_stack_dict.get("value", []) if isinstance(tech_stack_dict, dict) else []
            
            # Technology Categories mapping
            tech_extractor = TechnologyExtractor()
            tech_categories = tech_extractor.categorize_tech(tech_list)
            
            # Maturity calculations
            tech_list_lower = [t.lower() for t in tech_list]
            backend_count = sum(1 for t in tech_list_lower if t in ["java", "python", "go", "spring boot", "fastapi"])
            frontend_count = sum(1 for t in tech_list_lower if t in ["react", "angular", "typescript", "javascript"])
            total_focus = backend_count + frontend_count
            backend_focus = backend_count / total_focus if total_focus > 0 else 0.5
            frontend_focus = frontend_count / total_focus if total_focus > 0 else 0.5
            
            devops_maturity = "LOW"
            if "docker" in tech_list_lower and "kubernetes" in tech_list_lower:
                devops_maturity = "HIGH"
            elif "docker" in tech_list_lower or "kubernetes" in tech_list_lower:
                devops_maturity = "MEDIUM"
                
            cloud_maturity = "LOW"
            if "amazon web services" in tech_list_lower or "gcp" in tech_list_lower or "aws" in tech_list_lower:
                cloud_maturity = "MEDIUM"
            if ("amazon web services" in tech_list_lower or "aws" in tech_list_lower) and "kubernetes" in tech_list_lower:
                cloud_maturity = "HIGH"
                
            ai_adoption = "LOW"
            if any(ai in tech_list_lower for ai in ["tensorflow", "pytorch", "openai"]):
                ai_adoption = "HIGH"
                
            # Classify Industries
            industries_dict = knowledge.get("industries", {})
            industries = industries_dict.get("value", ["SaaS"]) if isinstance(industries_dict, dict) else ["SaaS"]
            
            # Primary/Secondary stacks
            primary_stack = [t for t in tech_list if t in ["Java", "Go", "Python", "React", "Kubernetes", "Docker"]]
            secondary_stack = [t for t in tech_list if t not in primary_stack]
            
            result = {
                "industries": industries,
                "employeeRange": knowledge.get("employeeRange", {}).get("value", "1000-5000"),
                "remotePolicy": "Hybrid",
                "technologyCategories": tech_categories,
                "primaryLanguage": "Java" if "Java" in tech_list else ("Python" if "Python" in tech_list else "Go"),
                "primaryTechnologyStack": primary_stack[:4],
                "secondaryTechnologies": secondary_stack[:6],
                "backendFocus": backend_focus,
                "frontendFocus": frontend_focus,
                "cloudMaturity": cloud_maturity,
                "aiAdoption": ai_adoption,
                "devOpsMaturity": devops_maturity
            }
        elif task_type == "COMPANY_INSIGHTS":
            provider_name = "insights-classifier"
            knowledge = payload.get("knowledge", {})
            metadata = payload.get("metadata", {})
            insights_service = CompanyInsightsService()
            result = insights_service.generate_insights(knowledge, metadata)
        elif task_type == "COMPANY_SEARCH":
            provider_name = "qdrant"
            processor = document_factory.get_processor("COMPANY")
            retrieved = processor.retriever.retrieve(
                query=payload.get("query", ""),
                limit=payload.get("size", 5),
                filters=payload
            )
            result = {"results": retrieved}
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
        raise HTTPException(
            status_code=500,
            detail=f"Task execution failed: {str(e)}"
        )

# AI Knowledge Platform APIs
@app.post("/api/v1/ai/documents")
def create_document(request: IngestDocumentRequest):
    start_time = time.time()
    _metrics["total_requests"] += 1
    _metrics["documents_processed"] += 1
    
    processor = document_factory.get_processor(request.documentType)
    extracted_metadata = processor.extractor.extract(request.content)
    
    execution_time = int((time.time() - start_time) * 1000)
    _metrics["total_latency_ms"] += execution_time
    
    return {
        "status": "VALIDATED",
        "documentType": request.documentType,
        "extractedMetadata": extracted_metadata,
        "executionTimeMs": execution_time
    }

@app.post("/api/v1/ai/documents/chunks")
def chunk_document(request: ChunkDocumentRequest):
    start_time = time.time()
    _metrics["total_requests"] += 1
    
    processor = document_factory.get_processor(request.documentType)
    chunks = processor.chunker.split(
        request.content, 
        size=request.chunkSize, 
        overlap=request.chunkOverlap
    )
    
    _metrics["chunk_count"] += len(chunks)
    execution_time = int((time.time() - start_time) * 1000)
    _metrics["total_latency_ms"] += execution_time
    
    return {
        "chunks": chunks,
        "executionTimeMs": execution_time
    }

@app.post("/api/v1/ai/documents/embeddings")
def embed_chunks(request: EmbedChunksRequest):
    start_time = time.time()
    _metrics["total_requests"] += 1
    
    embedder = get_embedding_provider()
    results = []
    
    for i, chunk_text in enumerate(request.chunks):
        vector = embedder.embed_query(chunk_text)
        results.append({
            "vectorId": f"vec-{i}-{int(time.time())}",
            "collection": f"{request.documentType.lower()}_vectors",
            "vectorDimension": len(vector),
            "embeddingModel": settings.default_model,
            "embeddingVersion": "v1"
        })
        
    execution_time = int((time.time() - start_time) * 1000)
    _metrics["total_latency_ms"] += execution_time
    
    return {
        "embeddings": results,
        "executionTimeMs": execution_time
    }

@app.post("/api/v1/ai/retrieval/search")
def search_retrieval(request: RetrievalSearchRequest):
    start_time = time.time()
    _metrics["total_requests"] += 1
    
    processor = document_factory.get_processor(request.documentType)
    results = processor.retriever.retrieve(
        query=request.query, 
        limit=request.limit, 
        filters=request.filters
    )
    
    execution_time = int((time.time() - start_time) * 1000)
    _metrics["total_latency_ms"] += execution_time
    
    return {
        "results": results,
        "executionTimeMs": execution_time
    }

# Resume Intelligence Endpoints
class ProcessResumeRequest(BaseModel):
    documentId: str
    content: str

@app.post("/api/v1/ai/resume/process")
def process_resume_api(request: ProcessResumeRequest):
    parser = BaseResumeParser()
    extractor = ResumeExtractor()
    ats_service = ATSAnalysisService()

    sections = parser.parse(request.content)
    knowledge = extractor.extract_all(sections)
    ats_metrics = ats_service.analyze(knowledge)

    # Cache locally in-memory
    _resume_db[request.documentId] = {
        "knowledge": knowledge,
        "ats": ats_metrics,
        "metadata": {
            "documentId": request.documentId,
            "status": "READY"
        }
    }
    return _resume_db[request.documentId]

@app.get("/api/v1/ai/resume/{id}")
def get_resume_knowledge_api(id: str):
    if id not in _resume_db:
        raise HTTPException(status_code=404, detail="Resume not processed or not found")
    return _resume_db[id]["knowledge"]

@app.get("/api/v1/ai/resume/{id}/metadata")
def get_resume_metadata_api(id: str):
    if id not in _resume_db:
        raise HTTPException(status_code=404, detail="Resume not processed or not found")
    return _resume_db[id]["metadata"]

@app.get("/api/v1/ai/resume/{id}/ats")
def get_resume_ats_api(id: str):
    if id not in _resume_db:
        raise HTTPException(status_code=404, detail="Resume not processed or not found")
    return _resume_db[id]["ats"]

@app.post("/api/v1/ai/resume/search")
def search_resumes_api(request: RetrievalSearchRequest):
    processor = document_factory.get_processor("RESUME")
    results = processor.retriever.retrieve(
        query=request.query, 
        limit=request.limit, 
        filters=request.filters
    )
    return {"results": results}

# Company Intelligence Endpoints
class ProcessCompanyRequest(BaseModel):
    documentId: str
    content: str
    url: Optional[str] = None

@app.post("/api/v1/ai/company/process")
def process_company_api(request: ProcessCompanyRequest):
    parser = CompanyParser()
    extractor = CompanyExtractor()
    insights_service = CompanyInsightsService()
    
    sections = parser.parse(request.content)
    knowledge = extractor.extract_all(sections)
    
    # Calculate metadata metrics
    tech_stack_dict = knowledge.get("technologyStack", {})
    tech_list = tech_stack_dict.get("value", []) if isinstance(tech_stack_dict, dict) else []
    tech_extractor = TechnologyExtractor()
    tech_categories = tech_extractor.categorize_tech(tech_list)
    
    metadata = {
        "industries": knowledge.get("industries", {}).get("value", ["SaaS"]),
        "employeeRange": knowledge.get("employeeRange", {}).get("value", "1000-5000"),
        "remotePolicy": "Hybrid",
        "technologyCategories": tech_categories,
        "primaryLanguage": "Java" if "Java" in tech_list else "Python",
        "primaryTechnologyStack": tech_list[:4],
        "secondaryTechnologies": tech_list[4:10],
        "backendFocus": 0.7,
        "frontendFocus": 0.3,
        "cloudMaturity": "HIGH",
        "aiAdoption": "MEDIUM",
        "devOpsMaturity": "HIGH"
    }
    
    insights = insights_service.generate_insights(knowledge, metadata)
    
    # Cache locally in-memory
    _company_db[request.documentId] = {
        "knowledge": knowledge,
        "metadata": metadata,
        "insights": insights
    }
    
    return _company_db[request.documentId]

@app.get("/api/v1/ai/company/{id}")
def get_company_knowledge_api(id: str):
    if id not in _company_db:
        raise HTTPException(status_code=404, detail="Company not processed or not found")
    return _company_db[id]["knowledge"]

@app.get("/api/v1/ai/company/{id}/metadata")
def get_company_metadata_api(id: str):
    if id not in _company_db:
        raise HTTPException(status_code=404, detail="Company not processed or not found")
    return _company_db[id]["metadata"]

@app.get("/api/v1/ai/company/{id}/insights")
def get_company_insights_api(id: str):
    if id not in _company_db:
        raise HTTPException(status_code=404, detail="Company not processed or not found")
    return _company_db[id]["insights"]

@app.post("/api/v1/ai/company/search")
def search_companies_api(request: RetrievalSearchRequest):
    processor = document_factory.get_processor("COMPANY")
    results = processor.retriever.retrieve(
        query=request.query, 
        limit=request.limit, 
        filters=request.filters
    )
    return {"results": results}

@app.get("/api/v1/ai/health")
def health_check():
    return {
        "status": "healthy",
        "llm_provider": settings.active_llm_provider,
        "embedding_provider": settings.active_embedding_provider
    }

@app.get("/api/v1/ai/ready")
def readiness_check():
    ready = len(llm_registry.get_registered_names()) > 0
    return {
        "status": "ready" if ready else "not_ready",
        "timestamp": time.time()
    }

@app.get("/api/v1/ai/metrics")
def metrics():
    avg_latency = 0.0
    if _metrics["completed_tasks"] > 0:
        avg_latency = _metrics["total_latency_ms"] / _metrics["total_requests"]
        
    return {
        "total_requests": _metrics["total_requests"],
        "completed_tasks": _metrics["completed_tasks"],
        "failed_tasks": _metrics["failed_tasks"],
        "avg_latency_ms": avg_latency,
        "documents_processed": _metrics["documents_processed"],
        "chunk_count": _metrics["chunk_count"]
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
