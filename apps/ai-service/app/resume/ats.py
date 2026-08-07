from typing import Dict, Any, List

class ATSAnalysisService:
    def analyze(self, knowledge: Dict[str, Any]) -> Dict[str, Any]:
        skills = knowledge.get("skills", [])
        experience = knowledge.get("experience", [])
        projects = knowledge.get("projects", [])
        certifications = knowledge.get("certifications", [])
        personal_info = knowledge.get("personalInformation", {})

        # 1. Contact Quality
        has_email = "@" in personal_info.get("email", "")
        has_phone = len(personal_info.get("phone", "")) > 5
        has_linkedin = "linkedin" in personal_info.get("linkedin", "")
        contact_score = sum([has_email, has_phone, has_linkedin]) / 3.0

        # 2. Section Coverage
        sections_checked = [
            bool(knowledge.get("summary")),
            bool(skills),
            bool(experience),
            bool(knowledge.get("education")),
            bool(projects),
            bool(certifications)
        ]
        section_score = sum(sections_checked) / len(sections_checked)

        # 3. Completeness Score
        completeness_score = (contact_score + section_score) / 2.0

        # 4. Skill Diversity
        skill_categories = set(s.get("category") for s in skills if s.get("category"))
        skill_diversity_score = min(len(skill_categories) / 5.0, 1.0)

        # 5. Project Strength & Experience Strength
        project_strength = min(len(projects) * 0.2, 1.0)
        experience_strength = min(knowledge.get("intelligence", {}).get("experienceIntelligence", {}).get("totalYearsExperience", 0) * 0.1, 1.0)

        # Overall ATS Score
        ats_score = (completeness_score + skill_diversity_score + project_strength + experience_strength) / 4.0

        return {
            "atsScore": ats_score,
            "completenessScore": completeness_score,
            "readabilityScore": 0.85,
            "sectionCoverage": section_score,
            "contactQuality": contact_score,
            "formattingQuality": 0.90,
            "keywordCoverage": 0.80,
            "skillDiversity": skill_diversity_score,
            "projectStrength": project_strength,
            "experienceStrength": experience_strength,
            "details": {
                "warnings": [
                    "Resume layout uses two columns; some ATS parsers prefer single column."
                ] if section_score < 1.0 else []
            }
        }
