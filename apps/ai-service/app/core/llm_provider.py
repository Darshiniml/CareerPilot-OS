from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import os

class LLMProvider(ABC):

    @abstractmethod
    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        """
        Generates text using the specific LLM model.
        """
        pass

class MockProvider(LLMProvider):
    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        formatted_prompt = prompt
        if variables:
            try:
                formatted_prompt = prompt.format(**variables)
            except KeyError:
                pass
        return f"[MOCK GENERATION] System: {system_prompt} | Prompt: {formatted_prompt}"

class OpenAIProvider(LLMProvider):
    def __init__(self, api_key: str = None, model: str = "gpt-4o"):
        self.api_key = api_key or os.getenv("OPENAI_API_KEY")
        self.model = model

    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        if not self.api_key:
            return MockProvider().generate(prompt, system_prompt, variables)
        return f"[OpenAI {self.model}] Response for: {prompt[:30]}..."

class GeminiProvider(LLMProvider):
    def __init__(self, api_key: str = None, model: str = "gemini-1.5-pro"):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY")
        self.model = model

    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        if not self.api_key:
            return MockProvider().generate(prompt, system_prompt, variables)
        return f"[Gemini {self.model}] Response for: {prompt[:30]}..."

class AnthropicProvider(LLMProvider):
    def __init__(self, api_key: str = None, model: str = "claude-3-5-sonnet"):
        self.api_key = api_key or os.getenv("ANTHROPIC_API_KEY")
        self.model = model

    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        if not self.api_key:
            return MockProvider().generate(prompt, system_prompt, variables)
        return f"[Anthropic {self.model}] Response for: {prompt[:30]}..."

class LocalModelProvider(LLMProvider):
    def __init__(self, endpoint: str = "http://localhost:11434", model: str = "llama3"):
        self.endpoint = endpoint
        self.model = model

    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        return f"[LocalModel {self.model} at {self.endpoint}] Response for: {prompt[:30]}..."

# Provider Registry
class LLMProviderRegistry:
    def __init__(self):
        self._providers = {}

    def register(self, name: str, provider_class):
        self._providers[name.lower()] = provider_class

    def get_provider(self, name: str, **kwargs) -> LLMProvider:
        name_lower = name.lower()
        if name_lower not in self._providers:
            return MockProvider()
        return self._providers[name_lower](**kwargs)

    def get_registered_names(self):
        return list(self._providers.keys())

llm_registry = LLMProviderRegistry()
llm_registry.register("mock", MockProvider)
llm_registry.register("openai", OpenAIProvider)
llm_registry.register("gemini", GeminiProvider)
llm_registry.register("anthropic", AnthropicProvider)
llm_registry.register("local", LocalModelProvider)

def get_llm_provider(provider_name: str = None, **kwargs) -> LLMProvider:
    from app.core.config import settings
    provider_name = provider_name or settings.active_llm_provider
    return llm_registry.get_provider(provider_name, **kwargs)
