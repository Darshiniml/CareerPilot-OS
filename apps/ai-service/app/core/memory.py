from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional

class MemoryProvider(ABC):
    @abstractmethod
    def store(self, key: str, data: Dict[str, Any], vector: Optional[List[float]] = None) -> bool:
        """Store information inside semantic/meta memory."""
        pass

    @abstractmethod
    def search(self, query_vector: List[float], limit: int = 5) -> List[Dict[str, Any]]:
        """Search similar memories matching vector query."""
        pass

class BaseMemoryProvider(MemoryProvider):
    def __init__(self, collection_name: str):
        self.collection_name = collection_name
        self._storage = {}

    def store(self, key: str, data: Dict[str, Any], vector: Optional[List[float]] = None) -> bool:
        self._storage[key] = {
            "data": data,
            "vector": vector
        }
        return True

    def search(self, query_vector: List[float], limit: int = 5) -> List[Dict[str, Any]]:
        # Mock search: returns whatever is stored up to limit
        results = []
        for key, val in self._storage.items():
            if len(results) >= limit:
                break
            results.append(val["data"])
        return results

class UserMemoryProvider(BaseMemoryProvider):
    def __init__(self):
        super().__init__("user_memories")

class ResumeMemoryProvider(BaseMemoryProvider):
    def __init__(self):
        super().__init__("resume_memories")

class CompanyMemoryProvider(BaseMemoryProvider):
    def __init__(self):
        super().__init__("company_memories")

class JobMemoryProvider(BaseMemoryProvider):
    def __init__(self):
        super().__init__("job_memories")

class ConversationMemoryProvider(BaseMemoryProvider):
    def __init__(self):
        super().__init__("conversation_memories")

# Registry
class MemoryProviderRegistry:
    def __init__(self):
        self._providers = {}

    def register(self, name: str, provider: MemoryProvider):
        self._providers[name.lower()] = provider

    def get_provider(self, name: str) -> MemoryProvider:
        return self._providers.get(name.lower())

    def get_registered_names(self):
        return list(self._providers.keys())

memory_registry = MemoryProviderRegistry()
memory_registry.register("user", UserMemoryProvider())
memory_registry.register("resume", ResumeMemoryProvider())
memory_registry.register("company", CompanyMemoryProvider())
memory_registry.register("job", JobMemoryProvider())
memory_registry.register("conversation", ConversationMemoryProvider())
