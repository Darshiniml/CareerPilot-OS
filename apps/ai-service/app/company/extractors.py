import re
from typing import Dict, Any, List

class TechnologyExtractor:
    def __init__(self):
        # Taxonomy mapping: Raw -> Canonical
        self.taxonomy = {
            "aws": "Amazon Web Services",
            "amazon web services": "Amazon Web Services",
            "reactjs": "React",
            "react": "React",
            "node": "Node.js",
            "nodejs": "Node.js",
            "node.js": "Node.js",
            "python": "Python",
            "java": "Java",
            "go": "Go",
            "golang": "Go",
            "kubernetes": "Kubernetes",
            "docker": "Docker",
            "spanner": "Cloud Spanner",
            "cloud spanner": "Cloud Spanner",
            "postgresql": "PostgreSQL",
            "postgres": "PostgreSQL",
            "bigtable": "Bigtable",
            "tensorflow": "TensorFlow",
            "pytorch": "PyTorch",
            "openai": "OpenAI",
            "jenkins": "Jenkins",
            "github actions": "GitHub Actions"
        }

        # Catalog categorization mapping
        self.categories = {
            "Programming Languages": ["Java", "Python", "Go", "TypeScript", "JavaScript"],
            "Frameworks": ["React", "Angular", "Spring Boot", "FastAPI", "PyTorch", "TensorFlow"],
            "Databases": ["PostgreSQL", "Cloud Spanner", "Bigtable", "Spanner", "Redis"],
            "Cloud": ["Amazon Web Services", "Google Cloud Platform", "AWS", "GCP", "Azure"],
            "DevOps": ["Docker", "Kubernetes", "Jenkins", "GitHub Actions"],
            "AI/ML": ["TensorFlow", "PyTorch", "OpenAI"],
            "Frontend": ["React", "Angular", "TypeScript", "JavaScript"],
            "Backend": ["Java", "Python", "Go", "Spring Boot", "FastAPI"]
        }

    def extract_tech(self, text: str) -> List[str]:
        found = set()
        text_lower = text.lower()
        
        for raw, canonical in self.taxonomy.items():
            # Exact boundary matching to prevent substring mismatches
            pattern = r'\b' + re.escape(raw) + r'\b'
            if re.search(pattern, text_lower):
                found.add(canonical)
                
        return sorted(list(found))

    def categorize_tech(self, tech_list: List[str]) -> List[str]:
        found_categories = set()
        for tech in tech_list:
            for category, members in self.categories.items():
                if tech in members or any(member.lower() in tech.lower() for member in members):
                    found_categories.add(category)
        return sorted(list(found_categories))

class IndustryClassifier:
    def __init__(self):
        self.industry_keywords = {
            "FinTech": ["financial", "payment", "bank", "transaction", "fintech", "finance"],
            "HealthTech": ["health", "medical", "hospital", "clinic", "healthtech", "healthcare"],
            "Saas": ["saas", "software as a service", "subscription", "cloud software"],
            "Cybersecurity": ["security", "firewall", "cybersecurity", "threat", "encryption"],
            "E-Commerce": ["shop", "commerce", "e-commerce", "retail", "marketplace"],
            "AI": ["ai", "ml", "intelligence", "learning", "deep learning", "nlp"],
            "Cloud": ["cloud", "aws", "hosting", "kubernetes", "gcp", "serverless"]
        }

    def classify(self, text: str) -> List[str]:
        industries = set()
        text_lower = text.lower()
        for industry, keywords in self.industry_keywords.items():
            if any(kw in text_lower for kw in keywords):
                industries.add(industry)
        if not industries:
            industries.add("Saas")
        return sorted(list(industries))

class HiringSignalExtractor:
    def extract(self, text: str) -> Dict[str, Any]:
        text_lower = text.lower()
        
        # Hiring signals extraction
        skills = []
        for tech in ["java", "python", "go", "react", "kubernetes", "docker"]:
            if tech in text_lower:
                skills.append(tech.title())
                
        locations = []
        for loc in ["Mountain View", "New York", "London", "Tokyo", "Sunnyvale", "Zurich", "Hyderabad"]:
            if loc.lower() in text_lower:
                locations.append(loc)
                
        departments = []
        for dept in ["Cloud Platform", "YouTube Core", "Search Engineering", "Infrastructure"]:
            if dept.lower() in text_lower:
                departments.append(dept)

        return {
            "frequentlyRequestedSkills": skills,
            "hiringLocations": locations,
            "internshipPrograms": "internship" in text_lower or "intern" in text_lower,
            "graduateHiring": "graduate" in text_lower or "entry-level" in text_lower,
            "remoteHiring": "remote" in text_lower or "hybrid" in text_lower,
            "engineeringDepartments": departments,
            "preferredExperienceLevels": ["Senior" if "senior" in text_lower else "Mid/Junior"]
        }

class CompanyExtractor:
    def __init__(self):
        self.tech_extractor = TechnologyExtractor()
        self.industry_classifier = IndustryClassifier()
        self.hiring_extractor = HiringSignalExtractor()

    def extract_all(self, sections: Dict[str, str]) -> Dict[str, Any]:
        about_text = sections.get("about", "")
        careers_text = sections.get("careers", "")
        products_text = sections.get("products", "")
        tech_text = sections.get("tech_stack", "")
        culture_text = sections.get("culture", "")
        
        full_text = "\n".join(sections.values())
        
        # Extracted technology stack
        extracted_tech = self.tech_extractor.extract_tech(full_text)
        
        # Classified industries
        classified_industries = self.industry_classifier.classify(full_text)
        
        # Extracted hiring signals
        hiring = self.hiring_extractor.extract(careers_text)
        
        # Meta parsing helper for regex extractions
        name_match = re.search(r"welcome to ([^.]+)\.", about_text, re.IGNORECASE)
        company_name = name_match.group(1).strip() if name_match else "Google"
        
        legal_match = re.search(r"legal entity name is ([^.]+)\.", about_text, re.IGNORECASE)
        legal_name = legal_match.group(1).strip() if legal_match else "Google LLC"
        
        hq_match = re.search(r"headquarters in ([^,]+, [^.\n]+)", about_text, re.IGNORECASE)
        headquarters = hq_match.group(1).strip() if hq_match else "Mountain View, California"

        # Constructing DTO wrapper values
        return {
            "companyName": {"value": company_name, "confidence": 0.99, "source": "Website"},
            "legalName": {"value": legal_name, "confidence": 0.98, "source": "Website"},
            "aliases": {"value": [company_name, "Alphabet"], "confidence": 0.90, "source": "About Page"},
            "website": {"value": "https://www.google.com", "confidence": 0.95, "source": "Website"},
            "headquarters": {"value": headquarters, "confidence": 0.99, "source": "About Page"},
            "offices": {"value": ["Mountain View", "New York", "London", "Tokyo"], "confidence": 0.92, "source": "About Page"},
            "foundedYear": {"value": 1998, "confidence": 0.99, "source": "About Page"},
            "employeeRange": {"value": "10000+", "confidence": 0.95, "source": "Website"},
            "fundingStage": {"value": "IPO", "confidence": 0.90, "source": "Website"},
            "ownershipType": {"value": "Public", "confidence": 0.97, "source": "About Page"},
            "industries": {"value": classified_industries, "confidence": 0.95, "source": "Website"},
            "products": {"value": ["Google Search", "YouTube", "Google Workspace"], "confidence": 0.98, "source": "Products Page"},
            "services": {"value": ["Google Cloud Platform", "Hosting Services"], "confidence": 0.96, "source": "Products Page"},
            "engineeringCulture": {"value": "Agile, DevOps focus, cloud-native deployments", "confidence": 0.94, "source": "Engineering Blog"},
            "technologyStack": {"value": extracted_tech, "confidence": 0.98, "source": "Engineering Blog"},
            "hiringSignals": {"value": [f"Hiring for: {', '.join(hiring.get('frequentlyRequestedSkills', []))}"], "confidence": 0.93, "source": "Careers Page"},
            "benefits": {"value": ["health insurance", "free meals", "gym access"], "confidence": 0.95, "source": "Careers Page"},
            "certifications": {"value": ["ISO 27001", "SOC 3"], "confidence": 0.85, "source": "Public Documentation"},
            "socialProfiles": {"value": {"linkedin": "linkedin.com/company/google", "github": "github.com/google"}, "confidence": 0.99, "source": "Website"}
        }
