# CareerPilot OS

CareerPilot OS is a production-grade, AI-powered Career Operating System designed to help candidates pilot their job search journey, manage profile parameters, customize and version resumes, search jobs via connectors, and orchestrate automation agents.

---

## Repository Monorepo Structure

This project uses a monorepo layout:
```
careerpilot-os/
├── apps/
│   ├── backend/        # Spring Boot 3 + Java 21 REST backend
│   ├── frontend/       # Vite + React 19 + TypeScript + Tailwind CSS v4 dashboard
│   └── ai-service/     # FastAPI Python 3.12 AI service orchestrator
├── packages/
│   ├── connector-sdk/  # Java integration interfaces for ATS crawling
│   ├── shared-dto/     # Common serialization schemas and validation DTOs
│   └── shared-events/  # Domain event contracts (e.g. UserCreatedEvent)
├── deployment/
│   ├── nginx/          # Unified reverse proxy routing configurations
│   └── observability/  # Prometheus and Grafana dashboards telemetry configs
├── docs/               # Architecture designs and UML diagrams (Mermaid)
└── docker-compose.yml  # Orchestrates full local/production stack services
```

---

## Technology Stack

### Core Services
- **Backend**: Java 21, Spring Boot 3.x, Spring Security (JWT authentication), JPA Hibernate, Flyway migrations, MapStruct.
- **AI Service**: Python 3.12, FastAPI, Uvicorn, LangGraph, LangChain, Sentence Transformers.
- **Frontend**: React 19, Vite, TypeScript, Zustand, TanStack Query, Axios, Tailwind CSS v4.
- **Infrastructure**: PostgreSQL 16, Redis 7, Qdrant Vector DB, MinIO S3-compatible storage, Nginx proxy, Prometheus, Grafana.

---

## Getting Started

### Prerequisites
- Docker & Docker Compose (v2.x+)
- Node.js v20+ & NPM v10+ (for local frontend work)
- Python 3.12+ (for local AI service work)

### Running the Stack
To boot the full application suite (PostgreSQL, Redis, Qdrant, MinIO, Actuators, Nginx, and all three application modules):
```bash
docker compose up --build
```

### Access Ports & Mappings (Nginx Routing)
- **Frontend Dashboard**: `http://localhost/` (routes to Vite port `5173`)
- **Backend API Docs**: `http://localhost/swagger-ui/index.html` (routes to backend port `8080`)
- **AI Service API Docs**: `http://localhost/api/v1/ai/docs` (routes to FastAPI port `8000`)
- **MinIO Dashboard Console**: `http://localhost:9001` (login: `minioadmin` / `minioadmin`)
- **Prometheus Dashboard**: `http://localhost:9090`
- **Grafana Dashboard**: `http://localhost:3000`

---

## Project Milestones

### Completed
- [x] **Milestone 1**: Directory structure, Gradle setups, JWT Authentication provider design, flyway baselines, MinIO connections, MDC logs correlation mapping, and FastAPI prompt loader configs.
- [x] **Milestone 2**: User profile CRUD (Experiences, Educations, Projects, Certifications, Social links), normalized preferences tables (preferred roles/locations/companies), salary preference bounds, S3 resume upload, metadata hashing, soft deletes, and separated sub-service architecture.
