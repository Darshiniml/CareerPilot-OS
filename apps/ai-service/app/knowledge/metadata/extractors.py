from abc import ABC, abstractmethod
from typing import Dict, Any

class MetadataExtractor(ABC):
    @abstractmethod
    def extract(self, content: str) -> Dict[str, Any]:
        """Extract structured metadata fields from document text."""
        pass

class DefaultMetadataExtractor(MetadataExtractor):
    def extract(self, content: str) -> Dict[str, Any]:
        return {
            "processed": True,
            "wordCount": len(content.split())
        }

class ResumeMetadataExtractor(MetadataExtractor):
    def extract(self, content: str) -> Dict[str, Any]:
        # Return mock/deterministic structured candidate profile metrics
        return {
            "skills": ["Java", "Python", "REST APIs"],
            "education": ["Bachelor of Science"],
            "experienceYears": 5,
            "topKeywords": ["Docker", "Kubernetes"]
        }

class JobMetadataExtractor(MetadataExtractor):
    def extract(self, content: str) -> Dict[str, Any]:
        return {
            "requiredSkills": ["FastAPI", "React", "TypeScript"],
            "salaryRange": "100k-130k",
            "location": "Remote"
        }

from app.company.parser import CompanyParser
from app.company.extractors import CompanyExtractor

class CompanyMetadataExtractor(MetadataExtractor):
    def extract(self, content: str) -> Dict[str, Any]:
        parser = CompanyParser()
        extractor = CompanyExtractor()
        sections = parser.parse(content)
        return extractor.extract_all(sections)
