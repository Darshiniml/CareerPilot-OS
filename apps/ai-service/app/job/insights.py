from typing import Dict, Any, List

class JobInsightsService:
    def generate_insights(self, knowledge: Dict[str, Any], metadata: Dict[str, Any]) -> Dict[str, List[str]]:
        engineering = []
        business = []
        hiring_insights = []
        
        all_skills = knowledge.get("requiredSkills", []) + knowledge.get("preferredSkills", [])
        tech_list_lower = [s["name"].lower() for s in all_skills]
        
        # 1. Engineering Insights
        if "java" in tech_list_lower:
            engineering.append("Java Ecosystem")
        if "python" in tech_list_lower:
            engineering.append("Python Ecosystem")
        if any(c in tech_list_lower for c in ["kubernetes", "docker", "amazon web services", "aws"]):
            engineering.append("Cloud Native")
        if any(ai in tech_list_lower for ai in ["tensorflow", "pytorch", "openai"]):
            engineering.append("AI Focused")
            
        backend_count = sum(1 for s in all_skills if s["category"] in ["Backend", "Programming Languages"])
        frontend_count = sum(1 for s in all_skills if s["category"] == "Frontend")
        if backend_count > frontend_count:
            engineering.append("Backend Heavy")
            
        # 2. Business Insights
        company_name = knowledge.get("companyName", {}).get("value", "")
        if company_name.lower() in ["google", "netflix", "meta", "amazon", "microsoft"]:
            business.append("Enterprise Role")
        else:
            business.append("Startup Role")
            
        # 3. Hiring Insights
        inferred = knowledge.get("inferredSeniority", {}).get("value", "Mid-Level")
        if inferred in ["Junior", "Fresher", "Internship"]:
            hiring_insights.append("Graduate Friendly")
            
        remote_mode = knowledge.get("workMode", {}).get("value", "Hybrid").lower()
        if "remote" in remote_mode or metadata.get("remote", False):
            hiring_insights.append("Remote Friendly")
            
        hiring_insights.append("High Growth Team")
            
        # Fallback values
        if not engineering:
            engineering.append("Standard Tech Stack")
        if not hiring_insights:
            hiring_insights.append("Traditional Hiring Office")
            
        return {
            "engineering": engineering,
            "business": business,
            "hiring": hiring_insights
        }
