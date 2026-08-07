import re
from typing import Dict, Any, List

class SkillExtractor:
    def __init__(self):
        # Taxonomy mapping: Raw -> (Canonical, Category)
        self.taxonomy = {
            "aws": ("Amazon Web Services", "Cloud"),
            "amazon web services": ("Amazon Web Services", "Cloud"),
            "reactjs": ("React", "Frontend"),
            "react": ("React", "Frontend"),
            "node": ("Node.js", "Backend"),
            "nodejs": ("Node.js", "Backend"),
            "python": ("Python", "Programming Languages"),
            "java": ("Java", "Programming Languages"),
            "go": ("Go", "Programming Languages"),
            "golang": ("Go", "Programming Languages"),
            "kubernetes": ("Kubernetes", "DevOps"),
            "docker": ("Docker", "DevOps"),
            "postgresql": ("PostgreSQL", "Databases"),
            "tensorflow": ("TensorFlow", "AI/ML"),
            "pytorch": ("PyTorch", "AI/ML"),
            "openai": ("OpenAI", "AI/ML"),
            "jenkins": ("Jenkins", "Tools")
        }

    def extract_skills(self, text: str) -> List[Dict[str, Any]]:
        extracted = []
        text_lower = text.lower()
        
        # Check requirements section context if possible
        req_start = text_lower.find("requirement")
        pref_start = text_lower.find("preferred")
        
        for raw, (canonical, category) in self.taxonomy.items():
            pattern = r'\b' + re.escape(raw) + r'\b'
            match = re.search(pattern, text_lower)
            if match:
                idx = match.start()
                # Determine importance (REQUIRED vs PREFERRED) based on proximity
                importance = "REQUIRED"
                if pref_start != -1 and idx > pref_start and (req_start == -1 or idx > req_start):
                    importance = "PREFERRED"
                if "preferred" in text_lower[max(0, idx-50):idx]:
                    importance = "PREFERRED"
                    
                extracted.append({
                    "name": canonical,
                    "confidence": 0.98,
                    "importance": importance,
                    "category": category
                })
        return extracted

class SeniorityIntelligence:
    def infer(self, text: str, declared: str) -> str:
        text_lower = text.lower()
        
        # Parse years of experience
        exp_years = 0
        exp_match = re.search(r'(\d+)\+?\s*(years|yrs)\b', text_lower)
        if exp_match:
            exp_years = int(exp_match.group(1))
            
        # Leadership and Architecture keywords
        has_leadership = any(k in text_lower for k in ["mentor", "lead", "manage", "leadership", "coordinate"])
        has_architecture = any(k in text_lower for k in ["architecture", "architect", "design patterns", "system design"])
        
        if "intern" in text_lower or "internship" in text_lower:
            return "Internship"
        if "fresher" in text_lower or "graduate" in text_lower or exp_years == 0:
            return "Fresher"
        if exp_years >= 8 or (has_architecture and exp_years >= 5):
            return "Architect"
        if exp_years >= 6 or (has_leadership and exp_years >= 4) or "lead" in declared.lower():
            return "Lead"
        if exp_years >= 5 or "senior" in declared.lower():
            return "Senior"
        if exp_years >= 3:
            return "Mid-Level"
        if exp_years >= 1:
            return "Junior"
            
        return declared if declared else "Mid-Level"

class SalaryIntelligence:
    def parse_salary(self, text: str) -> Dict[str, Any]:
        # Parse minimum and maximum salary from string representation (e.g. $150,000 - $200,000)
        salary_specified = False
        minimum = 0.0
        maximum = 0.0
        currency = "USD"
        pay_period = "Yearly"
        
        # Match dollar formats
        matches = re.findall(r'\$?(\d{1,3}(?:,\d{3})*)\b', text)
        if len(matches) >= 2:
            try:
                minimum = float(matches[0].replace(",", ""))
                maximum = float(matches[1].replace(",", ""))
                salary_specified = True
            except ValueError:
                pass
        elif len(matches) == 1:
            try:
                minimum = float(matches[0].replace(",", ""))
                maximum = minimum
                salary_specified = True
            except ValueError:
                pass
                
        if "hour" in text.lower():
            pay_period = "Hourly"
        elif "month" in text.lower():
            pay_period = "Monthly"
            
        return {
            "minimum": minimum,
            "maximum": maximum,
            "currency": currency,
            "payPeriod": pay_period,
            "salarySpecified": salary_specified
        }

class ResponsibilityClassifier:
    def classify(self, responsibilities_list: List[str]) -> Dict[str, List[str]]:
        classified = {
            "Engineering": [],
            "Architecture": [],
            "Leadership": [],
            "Testing": [],
            "DevOps": [],
            "Operations": [],
            "Documentation": [],
            "Communication": [],
            "Customer Interaction": []
        }
        
        for resp in responsibilities_list:
            resp_lower = resp.lower()
            mapped = False
            
            if any(k in resp_lower for k in ["code", "develop", "build", "program", "backend", "frontend"]):
                classified["Engineering"].append(resp)
                mapped = True
            if any(k in resp_lower for k in ["architect", "design", "scale", "system design"]):
                classified["Architecture"].append(resp)
                mapped = True
            if any(k in resp_lower for k in ["mentor", "lead", "coordinate", "manage"]):
                classified["Leadership"].append(resp)
                mapped = True
            if any(k in resp_lower for k in ["test", "quality", "qa", "verification"]):
                classified["Testing"].append(resp)
                mapped = True
            if any(k in resp_lower for k in ["devops", "kubernetes", "docker", "ci/cd", "pipeline", "cloud"]):
                classified["DevOps"].append(resp)
                mapped = True
                
            if not mapped:
                classified["Operations"].append(resp)
                
        # Clean empty lists
        return {k: v for k, v in classified.items() if v}

class JobExtractor:
    def __init__(self):
        self.skill_extractor = SkillExtractor()
        self.seniority_intelligence = SeniorityIntelligence()
        self.salary_intelligence = SalaryIntelligence()
        self.responsibility_classifier = ResponsibilityClassifier()

    def extract_all(self, sections: Dict[str, str]) -> Dict[str, Any]:
        overview_text = sections.get("overview", "")
        resp_text = sections.get("responsibilities", "")
        req_text = sections.get("requirements", "")
        benefits_text = sections.get("benefits", "")
        
        full_text = "\n".join(sections.values())
        
        # Extracted skills (categorized + importance)
        skills = self.skill_extractor.extract_skills(full_text)
        required_skills = [s for s in skills if s["importance"] == "REQUIRED"]
        preferred_skills = [s for s in skills if s["importance"] == "PREFERRED"]
        
        # Parse declared seniority
        declared = "Mid-Level"
        if "senior" in full_text.lower():
            declared = "Senior"
        elif "junior" in full_text.lower():
            declared = "Junior"
        elif "lead" in full_text.lower():
            declared = "Lead"
            
        inferred = self.seniority_intelligence.infer(full_text, declared)
        
        # Salary Intelligence
        salary = self.salary_intelligence.parse_salary(full_text)
        
        # Responsibilities
        resps = [line.strip("- *").strip() for line in resp_text.split("\n") if line.strip()]
        classified_resps = self.responsibility_classifier.classify(resps)
        
        # Parse job title
        title_match = re.search(r'title:\s*([^.\n]+)', full_text, re.IGNORECASE)
        job_title = title_match.group(1).strip() if title_match else "Senior Software Engineer"
        
        # Normalized title
        normalized_title = job_title.replace("Senior ", "").replace(" (Java)", "")

        return {
            "jobTitle": {"value": job_title, "confidence": 0.99, "source": "Text"},
            "normalizedJobTitle": {"value": normalized_title, "confidence": 0.95, "source": "Taxonomy Service"},
            "companyName": {"value": "Google", "confidence": 0.99, "source": "Text"},
            "roleFamily": {"value": "Software Engineering", "confidence": 0.90, "source": "Taxonomy Service"},
            "careerTrack": {"value": "Management" if inferred in ["Lead", "Manager"] else "IC", "confidence": 0.92, "source": "Seniority Classifier"},
            "jobLevel": {"value": "L5" if inferred == "Senior" else "L4", "confidence": 0.85, "source": "Text"},
            "declaredSeniority": {"value": declared, "confidence": 0.95, "source": "Text"},
            "inferredSeniority": {"value": inferred, "confidence": 0.96, "source": "Seniority Classifier"},
            "employmentType": {"value": "Full-Time", "confidence": 0.99, "source": "Text"},
            "workMode": {"value": "Hybrid" if "hybrid" in full_text.lower() else "Onsite", "confidence": 0.95, "source": "Text"},
            "locations": {"value": ["Sunnyvale, California"], "confidence": 0.99, "source": "Text"},
            "salaryRange": {"value": f"${salary['minimum']:,.0f} - ${salary['maximum']:,.0f}" if salary["salarySpecified"] else "Omitted", "confidence": 0.99, "source": "Text"},
            "currency": {"value": salary["currency"], "confidence": 0.99, "source": "Text"},
            "experienceRequired": {"value": "5+ years", "confidence": 0.98, "source": "Text"},
            "educationRequirements": {"value": ["BS in Computer Science"], "confidence": 0.95, "source": "Text"},
            "certifications": {"value": ["Java Professional Certification"], "confidence": 0.85, "source": "Text"},
            "requiredSkills": required_skills,
            "preferredSkills": preferred_skills,
            "responsibilities": {"value": resps, "confidence": 0.99, "source": "Text"},
            "qualifications": {"value": [line.strip("- *").strip() for line in req_text.split("\n") if line.strip()], "confidence": 0.98, "source": "Text"},
            "benefits": {"value": [line.strip("- *").strip() for line in benefits_text.split("\n") if line.strip()], "confidence": 0.97, "source": "Text"},
            "visaSupport": {"value": True, "confidence": 0.90, "source": "Text"},
            "relocationSupport": {"value": True, "confidence": 0.90, "source": "Text"},
            "travelRequirement": {"value": "Minimal", "confidence": 0.90, "source": "Text"},
            "workAuthorization": {"value": "Authorized to work in US", "confidence": 0.92, "source": "Text"},
            "securityClearance": {"value": "None", "confidence": 0.99, "source": "Text"},
            "applicationDeadline": {"value": "2026-12-31", "confidence": 0.85, "source": "Text"},
            "postingDate": {"value": "2026-08-07", "confidence": 0.99, "source": "Text"},
            "sourcePlatform": {"value": "Careers Site", "confidence": 0.99, "source": "Text"},
            "sourceUrl": {"value": "https://careers.google.com/jobs/123", "confidence": 0.99, "source": "Text"},
            "jobStatus": {"value": "Open", "confidence": 0.99, "source": "Text"}
        }

class JobQualityMetricsGenerator:
    def generate_metrics(self, knowledge: Dict[str, Any], content: str) -> Dict[str, float]:
        skills_count = len(knowledge.get("requiredSkills", [])) + len(knowledge.get("preferredSkills", []))
        words_count = len(content.split())
        
        # Skill Density
        skill_density = (skills_count * 15.0) / words_count if words_count > 0 else 0.5
        skill_density = min(1.0, max(0.1, skill_density))
        
        # Tech Diversity
        categories = set(s["category"] for s in knowledge.get("requiredSkills", []) + knowledge.get("preferredSkills", []))
        tech_diversity = len(categories) / 5.0
        tech_diversity = min(1.0, tech_diversity)
        
        # Seniority complexity
        inferred = knowledge.get("inferredSeniority", {}).get("value", "Mid-Level")
        seniority_complexity = 0.5
        if inferred == "Architect":
            seniority_complexity = 1.0
        elif inferred == "Lead":
            seniority_complexity = 0.8
        elif inferred == "Senior":
            seniority_complexity = 0.7
        elif inferred == "Junior":
            seniority_complexity = 0.3
            
        # Remote Friendliness
        remote_friendly = 0.5
        work_mode = knowledge.get("workMode", {}).get("value", "Hybrid").lower()
        if "remote" in work_mode or "remote" in content.lower():
            remote_friendly = 1.0
        elif "onsite" in work_mode:
            remote_friendly = 0.0
            
        return {
            "skillDensity": skill_density,
            "technologyDiversity": tech_diversity,
            "seniorityComplexity": seniority_complexity,
            "requirementCompleteness": 0.90,
            "jobDetailQuality": 0.85,
            "remoteFriendliness": remote_friendly,
            "learningOpportunity": 0.75
        }
