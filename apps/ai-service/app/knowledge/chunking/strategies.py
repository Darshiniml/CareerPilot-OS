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

from app.job.parser import JobParser

class JobChunkStrategy(ChunkStrategy):
    def split(self, content: str, size: int = 500, overlap: int = 100) -> List[Dict[str, Any]]:
        parser = JobParser()
        sections = parser.parse(content)
        chunks = []
        chunk_number = 1
        
        for section_name, section_text in sections.items():
            if not section_text.strip():
                continue
                
            heading_title = section_name.replace("_", " ").title()
            
            # Formulate metadata tags
            tech_tags = []
            for tech in ["Java", "Python", "Go", "Kubernetes", "Docker", "AWS"]:
                if tech.lower() in section_text.lower():
                    tech_tags.append(tech)
                    
            chunks.append({
                "chunkNumber": chunk_number,
                "text": f"[{heading_title}]\n{section_text}",
                "tokenCount": len(section_text.split()),
                "metadata": {
                    "sourcePage": "Job Description",
                    "section": section_name,
                    "heading": heading_title,
                    "category": "Job Posting",
                    "technologyTags": tech_tags,
                    "documentVersion": 1
                }
            })
            chunk_number += 1
            
        if not chunks:
            chunks.append({
                "chunkNumber": 1,
                "text": content,
                "tokenCount": len(content.split()),
                "metadata": {
                    "sourcePage": "Job Description",
                    "section": "overview",
                    "heading": "General Job Overview",
                    "category": "Job Posting",
                    "technologyTags": [],
                    "documentVersion": 1
                }
            })
            
        return chunks

from app.company.parser import CompanyParser

class CompanyChunkStrategy(ChunkStrategy):
    def split(self, content: str, size: int = 500, overlap: int = 100) -> List[Dict[str, Any]]:
        parser = CompanyParser()
        sections = parser.parse(content)
        chunks = []
        chunk_number = 1
        
        for section_name, section_text in sections.items():
            if not section_text.strip():
                continue
                
            heading_title = section_name.replace("_", " ").title()
            
            # Formulate metadata tags
            tech_tags = []
            for tech in ["Java", "Python", "Go", "Kubernetes", "Docker", "AWS"]:
                if tech.lower() in section_text.lower():
                    tech_tags.append(tech)
                    
            chunks.append({
                "chunkNumber": chunk_number,
                "text": f"[{heading_title}]\n{section_text}",
                "tokenCount": len(section_text.split()),
                "metadata": {
                    "sourcePage": "Website Source",
                    "section": section_name,
                    "heading": heading_title,
                    "category": "Company Profile",
                    "technologyTags": tech_tags,
                    "documentVersion": 1
                }
            })
            chunk_number += 1
            
        # Fallback if no chunks generated
        if not chunks:
            chunks.append({
                "chunkNumber": 1,
                "text": content,
                "tokenCount": len(content.split()),
                "metadata": {
                    "sourcePage": "Website Source",
                    "section": "about",
                    "heading": "General Overview",
                    "category": "Company Profile",
                    "technologyTags": [],
                    "documentVersion": 1
                }
            })
            
        return chunks

class ConversationChunkStrategy(DefaultChunkStrategy):
    # Specialized split strategy for recruiter conversation logs
    pass
