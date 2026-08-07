import os
from pydantic import BaseModel

class Settings(BaseModel):
    @property
    def active_llm_provider(self) -> str:
        return os.getenv("ACTIVE_LLM_PROVIDER", os.getenv("LLM_PROVIDER", "mock")).lower()
        
    @property
    def active_embedding_provider(self) -> str:
        return os.getenv("ACTIVE_EMBEDDING_PROVIDER", "mock").lower()
        
    @property
    def default_model(self) -> str:
        return os.getenv("DEFAULT_MODEL", "mock-model")

    @property
    def temperature(self) -> float:
        return float(os.getenv("TEMPERATURE", "0.7"))

    @property
    def max_context_tokens(self) -> int:
        return int(os.getenv("MAX_CONTEXT_TOKENS", "4096"))

    @property
    def max_output_tokens(self) -> int:
        return int(os.getenv("MAX_OUTPUT_TOKENS", "1024"))

settings = Settings()
