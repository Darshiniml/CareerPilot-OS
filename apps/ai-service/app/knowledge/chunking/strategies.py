from abc import ABC, abstractmethod
from typing import List, Dict, Any

class ChunkStrategy(ABC):
    @abstractmethod
    def split(self, content: str, size: int = 500, overlap: int = 100) -> List[Dict[str, Any]]:
        """Split document text into structured chunks."""
        pass

class DefaultChunkStrategy(ChunkStrategy):
    def split(self, content: str, size: int = 500, overlap: int = 100) -> List[Dict[str, Any]]:
        chunks = []
        start = 0
        length = len(content)
        chunk_number = 1
        
        while start < length:
            end = min(start + size, length)
            chunk_text = content[start:end]
            chunks.append({
                "chunkNumber": chunk_number,
                "text": chunk_text,
                "tokenCount": len(chunk_text.split()),
                "metadata": {"chunk_type": "default"}
            })
            start += (size - overlap)
            chunk_number += 1
            
        return chunks

class ResumeChunkStrategy(DefaultChunkStrategy):
    # Specialized split strategy for resume formats (e.g. splitting by sections)
    pass

class JobChunkStrategy(DefaultChunkStrategy):
    # Specialized split strategy for job posting descriptions
    pass

class CompanyChunkStrategy(DefaultChunkStrategy):
    # Specialized split strategy for company profiles
    pass

class ConversationChunkStrategy(DefaultChunkStrategy):
    # Specialized split strategy for recruiter conversation logs
    pass
