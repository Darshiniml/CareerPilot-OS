# CareerPilot OS - Milestone 3 AI Foundation Architecture Diagrams

This document contains ER diagrams, sequence diagrams, and class mappings for Milestone 3 (AI Foundation Infrastructure: Workflows, Tasks, Registries, and Prompt Assets).

---

## 1. Database Schema (Workflows & Tasks)

This diagram details the PostgreSQL tables for workflows and tasks, supporting JSONB payloads and metadata.

```mermaid
erDiagram
    ai_workflows {
        UUID id PK
        VARCHAR name
        VARCHAR status
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP completed_at
    }

    ai_tasks {
        UUID id PK
        UUID workflow_id FK
        VARCHAR task_type
        VARCHAR status
        VARCHAR priority
        VARCHAR correlation_id
        INT retry_count
        INT max_retries
        UUID created_by
        VARCHAR assigned_agent
        TIMESTAMP started_at
        TIMESTAMP completed_at
        TEXT failure_reason
        JSONB payload
        JSONB metadata
        TIMESTAMP created_at
    }

    ai_workflows ||--o{ ai_tasks : "contains"
```

---

## 2. Sequence Diagram (Event-Driven Task Dispatching)

This diagram outlines how domain events spark new AI workflows and tasks, routing them to the Gateway and python AI Service:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Consumer as AiEventConsumer
    participant Repo as JPA Repositories
    participant Dispatcher as AiTaskDispatcher
    participant Executor as SyncExecutor
    participant Gateway as AiGatewayClient
    participant FastAPI as FastAPI Execute Endpoint
    participant Registry as ProviderRegistry

    Client->>Consumer: Publish Domain Event (e.g. ResumeUploadedEvent)
    Consumer->>Repo: Create & persist AiWorkflow (Parent)
    Consumer->>Repo: Create & persist AiTask (Child with JSONB)
    Consumer->>Dispatcher: dispatch(AiTask)
    Dispatcher->>Executor: execute(AiTask)
    Note over Executor: Set Status to RUNNING
    Executor->>Gateway: executeTask(AiTaskRequestDto)
    Note over Gateway: Inject Bearer Token & X-Correlation-ID
    Gateway->>FastAPI: POST /api/v1/ai/execute (standardized request)
    FastAPI->>Registry: Route by taskType (e.g., RESUME_PARSE)
    Registry-->>FastAPI: Return active model response
    FastAPI-->>Gateway: Return standardized ExecuteTaskResponse
    Gateway-->>Executor: Map response DTO
    Note over Executor: Update Status to COMPLETED / FAILED
    Executor->>Repo: Update task & workflow states
    Executor->>Client: Publish AiTaskCompletedEvent
```

---

## 3. Class Diagram (FastAPI Registry & Prompt Framework)

This diagram outlines how Python FastAPI services use provider registries, hot settings routing, and versioned Prompt Assets:

```mermaid
classDiagram
    class Settings {
        +active_llm_provider str
        +active_embedding_provider str
        +default_model str
        +temperature float
    }

    class LLMProviderRegistry {
        -_providers dict
        +register(name, class)
        +get_provider(name) LLMProvider
    }

    class EmbeddingProviderRegistry {
        -_providers dict
        +register(name, class)
        +get_provider(name) EmbeddingProvider
    }

    class PromptManager {
        +get_prompt(category, template_name) str
        +load_prompt_asset(category, version, template_name) PromptAsset
    }

    class PromptAsset {
        +metadata dict
        +template str
        +validate_and_format(variables) str
    }

    class MemoryProviderRegistry {
        -_providers dict
        +register(name, provider)
        +get_provider(name) MemoryProvider
    }

    PromptManager --> PromptAsset
    LLMProviderRegistry ..> Settings : "reads active"
    EmbeddingProviderRegistry ..> Settings : "reads active"
```
