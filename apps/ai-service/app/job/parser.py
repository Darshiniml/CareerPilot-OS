import re
from typing import Dict

class JobParser:
    def __init__(self):
        # Section header matches: "# [Section Name]" or similar markdown headers
        self.header_pattern = re.compile(r'^\s*#+\s*\[?([^\]\n#]+)\]?', re.MULTILINE)
        
        self.aliases = {
            "overview": ["overview", "description", "introduction", "about the role", "about"],
            "responsibilities": ["responsibilities", "what you will do", "key duties", "duties", "role description"],
            "requirements": ["requirements", "qualifications", "what you need", "who you are", "skills"],
            "benefits": ["benefits", "perks", "what we offer"]
        }

    def parse(self, text: str) -> Dict[str, str]:
        """Split job text into canonical sections based on headers and aliases."""
        sections = {}
        matches = list(self.header_pattern.finditer(text))
        
        if not matches:
            # Fallback when no structural markdown headers are found
            sections["overview"] = text
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
                sections["overview"] = sections.get("overview", "") + "\n" + content
                sections["overview"] = sections["overview"].strip()
                
        return sections

class WebsiteJobParser(JobParser):
    pass

class ATSJobParser(JobParser):
    pass

class TextJobParser(JobParser):
    pass

class DefaultJobParser(JobParser):
    pass
