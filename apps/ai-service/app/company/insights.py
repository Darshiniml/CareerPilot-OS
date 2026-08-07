from typing import Dict, Any, List

class CompanyInsightsService:
    def generate_insights(self, knowledge: Dict[str, Any], metadata: Dict[str, Any]) -> Dict[str, List[str]]:
        engineering = []
        business = []
        hiring_insights = []
        
        tech_val = knowledge.get("technologyStack", {})
        tech_list = tech_val.get("value", []) if isinstance(tech_val, dict) else []
        
        # 1. Engineering Insights
        tech_list_lower = [t.lower() for t in tech_list]
        if "java" in tech_list_lower:
            engineering.append("Java Ecosystem")
        if "python" in tech_list_lower:
            engineering.append("Python Ecosystem")
        if any(c in tech_list_lower for c in ["kubernetes", "docker", "amazon web services", "gcp"]):
            engineering.append("Cloud Native")
        if any(ai in tech_list_lower for ai in ["tensorflow", "pytorch", "openai"]):
            engineering.append("AI First")
            
        # 2. Business Insights
        ownership = knowledge.get("ownershipType", {})
        ownership_val = ownership.get("value", "") if isinstance(ownership, dict) else ""
        emp_range = knowledge.get("employeeRange", {})
        emp_range_val = emp_range.get("value", "") if isinstance(emp_range, dict) else ""
        
        if "public" in ownership_val.lower() or "10000+" in emp_range_val:
            business.append("Enterprise")
        elif "startup" in ownership_val.lower() or "1-50" in emp_range_val:
            business.append("Startup")
        else:
            business.append("Scale-Up")
            
        # 3. Hiring Insights
        hiring_val = knowledge.get("hiringSignals", {})
        hiring_list = hiring_val.get("value", []) if isinstance(hiring_val, dict) else []
        benefits_val = knowledge.get("benefits", {})
        benefits_list = benefits_val.get("value", []) if isinstance(benefits_val, dict) else []
        
        hiring_text = " ".join(hiring_list).lower() + " " + " ".join(benefits_list).lower()
        
        if "intern" in hiring_text or "internship" in hiring_text:
            hiring_insights.append("Internship Friendly")
        if "graduate" in hiring_text or "entry-level" in hiring_text:
            hiring_insights.append("Graduate Friendly")
        if "remote" in hiring_text or "hybrid" in hiring_text or "remote" in metadata.get("remotePolicy", "").lower():
            hiring_insights.append("Remote Friendly")
            
        # Ensure fallback lists
        if not engineering:
            engineering.append("Standard Tech Stack")
        if not hiring_insights:
            hiring_insights.append("Traditional Hiring Office")
            
        return {
            "engineering": engineering,
            "business": business,
            "hiring": hiring_insights
        }
