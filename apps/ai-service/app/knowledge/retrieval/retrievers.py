from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional

class Retriever(ABC):
    @abstractmethod
    def retrieve(self, query: str, limit: int = 5, filters: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """Retrieve similar items matching a query string."""
        pass

class BaseRetriever(Retriever):
    def __init__(self, collection_name: str):
        self.collection_name = collection_name

    def retrieve(self, query: str, limit: int = 5, filters: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        # Mock retrieval returning structured matching candidates
        return [
            {
                "text": f"Mocked retrieve result matching query: {query}",
                "score": 0.95 - (i * 0.05),
                "collection": self.collection_name,
                "metadata": filters or {}
            } for i in range(min(limit, 3))
        ]

class ResumeRetriever(BaseRetriever):
    def __init__(self):
        super().__init__("resume_vectors")

class JobRetriever(BaseRetriever):
    def __init__(self):
        super().__init__("job_vectors")

class CompanyRetriever(BaseRetriever):
    def __init__(self):
        super().__init__("company_vectors")

    def retrieve(self, query: str, limit: int = 5, filters: Optional[Dict[str, Any]] = None, mode: str = "SEMANTIC") -> List[Dict[str, Any]]:
        # Supports future expansions for METADATA and HYBRID modes
        if mode == "METADATA":
            return [
                {
                    "text": f"Metadata retrieval match for company search: {query}",
                    "score": 0.85,
                    "collection": self.collection_name,
                    "metadata": filters or {}
                }
            ]
        elif mode == "HYBRID":
            return [
                {
                    "text": f"Hybrid retrieval match for company search: {query}",
                    "score": 0.90,
                    "collection": self.collection_name,
                    "metadata": filters or {}
                }
            ]
        else: # SEMANTIC
            return super().retrieve(query, limit, filters)

class ConversationRetriever(BaseRetriever):
    def __init__(self):
        super().__init__("conversation_vectors")

class DefaultRetriever(BaseRetriever):
    def __init__(self):
        super().__init__("default_vectors")
