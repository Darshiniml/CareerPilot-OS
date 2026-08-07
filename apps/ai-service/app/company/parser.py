import re
from typing import Dict, List, Any

class CompanyParser:
    def __init__(self):
        # Section header matches: "# [Section Name]" or similar markdown headers
        self.header_pattern = re.compile(r'^\s*#+\s*\[?([^\]\n#]+)\]?', re.MULTILINE)
        
        self.aliases = {
            "about": ["about", "about company", "general", "overview", "company profile", "who we are"],
            "careers": ["careers", "jobs", "hiring", "careers page", "work with us"],
            "products": ["products", "services", "products & services", "what we do"],
            "tech_stack": ["tech stack", "technology stack", "technologies", "stack", "technology"],
            "culture": ["culture", "engineering culture", "values", "benefits"]
        }

    def parse(self, text: str) -> Dict[str, str]:
        """Split textual content into canonical sections based on headers and aliases."""
        sections = {}
        matches = list(self.header_pattern.finditer(text))
        
        if not matches:
            # Default fallback when no structural markdown headers are found
            sections["about"] = text
            return sections
            
        for i, match in enumerate(matches):
            header = match.group(1).strip().lower()
            start_idx = match.end()
            end_idx = matches[i + 1].start() if i + 1 < len(matches) else len(text)
            content = text[start_idx:end_idx].strip()
            
            # Map header to canonical section based on aliases
            mapped = False
            for canonical, alias_list in self.aliases.items():
                if header in alias_list or any(alias in header for alias in alias_list):
                    sections[canonical] = sections.get(canonical, "") + "\n" + content
                    sections[canonical] = sections[canonical].strip()
                    mapped = True
                    break
            
            if not mapped:
                sections["about"] = sections.get("about", "") + "\n" + content
                sections["about"] = sections["about"].strip()
                
        return sections

class WebsiteCompanyParser(CompanyParser):
    pass

class CareersCompanyParser(CompanyParser):
    pass

class DefaultCompanyParser(CompanyParser):
    pass
