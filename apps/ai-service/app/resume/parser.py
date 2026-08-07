from abc import ABC, abstractmethod
from typing import Dict, Any, List

class ResumeParser(ABC):
    @abstractmethod
    def parse(self, text: str) -> Dict[str, Any]:
        """Parse raw resume text into segmented sections."""
        pass

class BaseResumeParser(ResumeParser):
    def parse(self, text: str) -> Dict[str, Any]:
        lines = text.split("\n")
        sections = {
            "summary": "",
            "skills": "",
            "experience": "",
            "education": "",
            "projects": "",
            "certifications": "",
            "achievements": "",
            "languages": ""
        }
        
        current_section = "summary"
        
        # Section Aliases
        aliases = {
            "work experience": "experience",
            "professional experience": "experience",
            "employment history": "experience",
            "experience": "experience",
            "academic background": "education",
            "qualifications": "education",
            "education": "education",
            "academic projects": "projects",
            "personal projects": "projects",
            "key projects": "projects",
            "projects": "projects",
            "skills": "skills",
            "summary": "summary",
            "certifications": "certifications",
            "credentials": "certifications",
            "achievements": "achievements",
            "languages": "languages"
        }
        
        for line in lines:
            line_clean = line.strip().lower()
            # Detect section shift
            matched = False
            for alias, section_key in aliases.items():
                if line_clean == alias or line_clean == alias + ":":
                    current_section = section_key
                    matched = True
                    break
            
            if matched:
                continue
                
            sections[current_section] += line + "\n"
            
        return sections

class PdfResumeParser(BaseResumeParser):
    pass

class DocxResumeParser(BaseResumeParser):
    pass

class TextResumeParser(BaseResumeParser):
    pass
