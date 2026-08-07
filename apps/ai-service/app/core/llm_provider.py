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
        # Real implementation using langchain_openai or openai sdk
        # return ChatOpenAI(api_key=self.api_key, model=self.model).invoke(prompt).content
        return f"[OpenAI {self.model}] Response for: {prompt[:30]}..."

class GeminiProvider(LLMProvider):
    def __init__(self, api_key: str = None, model: str = "gemini-1.5-pro"):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY")
        self.model = model

    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        if not self.api_key:
            return MockProvider().generate(prompt, system_prompt, variables)
        # Real implementation using langchain_google_genai or google-generativeai
        return f"[Gemini {self.model}] Response for: {prompt[:30]}..."

class AnthropicProvider(LLMProvider):
    def __init__(self, api_key: str = None, model: str = "claude-3-5-sonnet"):
        self.api_key = api_key or os.getenv("ANTHROPIC_API_KEY")
        self.model = model

    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        if not self.api_key:
            return MockProvider().generate(prompt, system_prompt, variables)
        # Real implementation using langchain_anthropic or anthropic sdk
        return f"[Anthropic {self.model}] Response for: {prompt[:30]}..."

class LocalModelProvider(LLMProvider):
    def __init__(self, endpoint: str = "http://localhost:11434", model: str = "llama3"):
        self.endpoint = endpoint
        self.model = model

    def generate(self, prompt: str, system_prompt: Optional[str] = None, variables: Optional[Dict[str, Any]] = None) -> str:
        # Real implementation using Ollama / Llama.cpp / vLLM client
        return f"[LocalModel {self.model} at {self.endpoint}] Response for: {prompt[:30]}..."


def get_llm_provider(provider_name: str = None, **kwargs) -> LLMProvider:
    provider_name = provider_name or os.getenv("LLM_PROVIDER", "mock").lower()
    
    if provider_name == "openai":
        return OpenAIProvider(**kwargs)
    elif provider_name == "gemini":
        return GeminiProvider(**kwargs)
    elif provider_name == "anthropic":
        return AnthropicProvider(**kwargs)
    elif provider_name == "local":
        return LocalModelProvider(**kwargs)
    else:
        return MockProvider()
