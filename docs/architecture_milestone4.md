# CareerPilot OS - Milestone 4 AI Knowledge Platform Architecture Diagrams

This document contains ER diagrams, sequence diagrams, and class mappings for Milestone 4 (AI Knowledge Platform: Processing Pipeline, Lifecycle, Factories, and Retrievers).

---

## 1. Document Lifecycle State Machine

This diagram maps out the document processing lifecycle states. Transition triggers are persisted to PostgreSQL:

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> VALIDATED : Title & checksum verified
    VALIDATED --> PARSED : Content text extracted
    PARSED --> METADATA_EXTRACTED : Extractor strategy applied
    METADATA_EXTRACTED --> CHUNKED : Configurable split strategy
    CHUNKED --> EMBEDDED : EmbeddingProvider sets vectors
    EMBEDDED --> INDEXED : Qdrant indexing coordinates mapped
    INDEXED --> READY : Ready for search retrieval
    
    CREATED --> FAILED : Error caught
    VALIDATED --> FAILED : Error caught
    PARSED --> FAILED : Error caught
    METADATA_EXTRACTED --> FAILED : Error caught
    CHUNKED --> FAILED : Error caught
    EMBEDDED --> FAILED : Error caught
    INDEXED --> FAILED : Error caught
    
    READY --> ARCHIVED : User deletes document
```

---

## 2. Sequence Diagram (Pipeline Execution Flow)

This diagram details the execution pipeline stages:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as AiKnowledgeController
    participant Pipeline as KnowledgePipelineService
    participant Repo as JPA Repositories
    participant Gateway as AiGatewayClient
    participant FastAPI as FastAPI Platform

    Client->>Controller: POST /api/v1/ai/documents (metadata & text)
    Controller->>Repo: Save AiDocument (status: CREATED)
    Controller->>Pipeline: processDocument(documentId)
    
    Note over Pipeline: Stage 1: Validation
    Pipeline->>Repo: Update status: VALIDATED
    
    Note over Pipeline: Stage 2: Parsing
    Pipeline->>Repo: Update status: PARSED
    
    Note over Pipeline: Stage 3: Metadata Extraction
    Pipeline->>Gateway: executeTask(type: RESUME_PARSE)
    Gateway->>FastAPI: Ingest metadata extraction request
    FastAPI-->>Gateway: Return structured & flexible metadata layers
    Gateway-->>Pipeline: Map metadata result DTO
    Pipeline->>Repo: Save metadata & status: METADATA_EXTRACTED
    
    Note over Pipeline: Stage 4: Chunking
    Pipeline->>Repo: Split text content (size: 500, overlap: 100) & save AiChunks
    Pipeline->>Repo: Update status: CHUNKED
    
    Note over Pipeline: Stage 5: Embedding & Indexing
    Pipeline->>Gateway: executeTask(type: GENERATE_EMBEDDINGS) for each chunk
    Gateway->>FastAPI: Embed chunk text & upload to Qdrant collection
    FastAPI-->>Gateway: Return Qdrant vectorId & collection
    Gateway-->>Pipeline: Map EmbeddingReferenceDto
    Pipeline->>Repo: Save EmbeddingReference in database
    Pipeline->>Repo: Update status: EMBEDDED
    
    Note over Pipeline: Stage 6: Ready Indexing
    Pipeline->>Repo: Update status: INDEXED
    Pipeline->>Repo: Update status: READY
    Pipeline->>Client: Return AiDocumentDto (Status READY)
```

---

## 3. Class Diagram (Knowledge Core Components)

This diagram outlines how Python FastAPI services use chunk split strategies, metadata extractors, document factories, and retrievers:

```mermaid
classDiagram
    class DocumentFactory {
        -_processors dict
        +register(type, processor)
        +get_processor(type) DocumentProcessor
    }

    class DocumentProcessor {
        -MetadataExtractor extractor
        -ChunkStrategy chunker
        -Retriever retriever
    }

    class MetadataExtractor {
        <<interface>>
        +extract(content) dict
    }
    class ResumeMetadataExtractor { +extract(content) dict }
    class JobMetadataExtractor { +extract(content) dict }

    class ChunkStrategy {
        <<interface>>
        +split(content, size, overlap) list
    }
    class ResumeChunkStrategy { +split(content, size, overlap) list }
    class DefaultChunkStrategy { +split(content, size, overlap) list }

    class Retriever {
        <<interface>>
        +retrieve(query, limit, filters) list
    }
    class ResumeRetriever { +retrieve(query, limit, filters) list }
    class JobRetriever { +retrieve(query, limit, filters) list }

    DocumentFactory --> DocumentProcessor
    DocumentProcessor --> MetadataExtractor
    DocumentProcessor --> ChunkStrategy
    DocumentProcessor --> Retriever
    
    MetadataExtractor <|-- ResumeMetadataExtractor
    MetadataExtractor <|-- JobMetadataExtractor
    
    ChunkStrategy <|-- ResumeChunkStrategy
    ChunkStrategy <|-- DefaultChunkStrategy
    
    Retriever <|-- ResumeRetriever
    Retriever <|-- JobRetriever
```

---

## 4. Qdrant Collection Strategy

Vectors are kept in isolated Qdrant vector databases according to document type:
- `resume_vectors` (dimensions: 1536/384)
- `company_vectors` (dimensions: 1536/384)
- `job_vectors` (dimensions: 1536/384)
- `interview_vectors` (dimensions: 1536/384)
- `conversation_vectors` (dimensions: 1536/384)
- `default_vectors` (dimensions: 1536/384)
