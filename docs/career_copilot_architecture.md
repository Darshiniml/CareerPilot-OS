# Career Copilot Architecture

## Purpose
The Career Copilot is a persistent orchestration layer for CareerPilot OS. It coordinates the existing resume, company, job, matching, application, interview, and learning intelligence modules without duplicating their business logic.

## Core Components
- IntentRouter: classifies user requests into deterministic intents.
- ContextBuilder: assembles the minimal context required for the active request.
- KnowledgeRetriever: pulls only the relevant knowledge slices for the active intent.
- PlanningEngine: creates structured execution plans.
- RecommendationEngine: produces evidence-based recommendations.
- ActionOrchestrator: maps copilot actions to existing platform modules.
- ExplanationEngine: explains every recommendation with evidence.
- ConversationMemory: stores structured interaction history.
- ResponseAssembler: combines plan, recommendations, and context into a copilot response.

## API Surface
- POST /api/v1/copilot/chat
- POST /api/v1/copilot/plan
- POST /api/v1/copilot/recommendations
- POST /api/v1/copilot/explain-match
- GET /api/v1/copilot/dashboard
- GET /api/v1/copilot/health-score
- GET /api/v1/copilot/history
- POST /api/v1/copilot/clear-session
