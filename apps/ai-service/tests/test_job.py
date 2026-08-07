from fastapi.testclient import TestClient
from app.main import app
from app.job.parser import JobParser
from app.job.extractors import SkillExtractor, SeniorityIntelligence, SalaryIntelligence, ResponsibilityClassifier, JobExtractor, JobQualityMetricsGenerator
from app.job.insights import JobInsightsService

client = TestClient(app)

def test_job_parser():
    parser = JobParser()
    text = (
        "# [Overview]\n"
        "Position description details.\n"
        "# [Qualifications]\n"
        "Must have Java experience.\n"
        "# [Key Duties]\n"
        "Write clean testable code.\n"
        "# [Perks]\n"
        "Free healthcare benefits.\n"
    )
    sections = parser.parse(text)
    assert "overview" in sections
    assert "requirements" in sections
    assert "responsibilities" in sections
    assert "benefits" in sections
    assert "Position description" in sections["overview"]
    assert "Java experience" in sections["requirements"]
    assert "clean testable code" in sections["responsibilities"]
    assert "Free healthcare" in sections["benefits"]

def test_skill_extractor():
    extractor = SkillExtractor()
    text = "Required Skills: Java. Preferred Skills: ReactJS, AWS."
    skills = extractor.extract_skills(text)
    
    # Verify importance mapping based on context
    java_skill = next(s for s in skills if s["name"] == "Java")
    react_skill = next(s for s in skills if s["name"] == "React")
    aws_skill = next(s for s in skills if s["name"] == "Amazon Web Services")
    
    assert java_skill["importance"] == "REQUIRED"
    assert react_skill["importance"] == "PREFERRED"
    assert aws_skill["importance"] == "PREFERRED"
    assert java_skill["category"] == "Programming Languages"
    assert react_skill["category"] == "Frontend"

def test_seniority_intelligence():
    intel = SeniorityIntelligence()
    # Test years of experience mapping
    assert intel.infer("Requires 8 years of experience with mentoring.", "Mid-Level") == "Architect"
    assert intel.infer("Requires 6 years and lead coordinates.", "Junior") == "Lead"
    assert intel.infer("Looking for internships.", "Mid-Level") == "Internship"
    assert intel.infer("No experience required. Fresher welcome.", "Senior") == "Fresher"
    assert intel.infer("Requires 5 years of software design.", "Mid-Level") == "Senior"

def test_salary_intelligence():
    intel = SalaryIntelligence()
    data = intel.parse_salary("Salary range is $120,000 - $160,000 yearly plus equity.")
    assert data["salarySpecified"] is True
    assert data["minimum"] == 120000.0
    assert data["maximum"] == 160000.0
    assert data["payPeriod"] == "Yearly"
    
    data_omitted = intel.parse_salary("Competitive salary depending on experience.")
    assert data_omitted["salarySpecified"] is False

def test_responsibility_classifier():
    classifier = ResponsibilityClassifier()
    resps = [
        "Write backend code APIs.",
        "Design scalable system patterns.",
        "Mentor and coordinate junior devs.",
        "Create Docker deployment pipelines."
    ]
    mapped = classifier.classify(resps)
    assert len(mapped["Engineering"]) == 1
    assert len(mapped["Architecture"]) == 1
    assert len(mapped["Leadership"]) == 1
    assert len(mapped["DevOps"]) == 1

def test_quality_metrics_generator():
    gen = JobQualityMetricsGenerator()
    knowledge = {
        "requiredSkills": [{"name": "Java", "category": "Programming Languages"}],
        "preferredSkills": [{"name": "React", "category": "Frontend"}, {"name": "AWS", "category": "Cloud"}],
        "inferredSeniority": {"value": "Senior"},
        "workMode": {"value": "Hybrid"}
    }
    content = "This is a job post description looking for senior engineers with Java, React and AWS skills."
    metrics = gen.generate_metrics(knowledge, content)
    
    assert metrics["skillDensity"] > 0.0
    assert metrics["technologyDiversity"] > 0.0
    assert metrics["seniorityComplexity"] == 0.7
    assert metrics["remoteFriendliness"] == 0.5

def test_job_insights_service():
    insights_service = JobInsightsService()
    knowledge = {
        "requiredSkills": [{"name": "Java", "category": "Programming Languages"}],
        "preferredSkills": [{"name": "AWS", "category": "Cloud"}],
        "inferredSeniority": {"value": "Senior"},
        "companyName": {"value": "Google"},
        "workMode": {"value": "Remote"}
    }
    metadata = {"remote": True}
    insights = insights_service.generate_insights(knowledge, metadata)
    
    assert "Java Ecosystem" in insights["engineering"]
    assert "Cloud Native" in insights["engineering"]
    assert "Backend Heavy" in insights["engineering"]
    assert "Enterprise Role" in insights["business"]
    assert "Remote Friendly" in insights["hiring"]

def test_job_process_flow_api():
    payload = {
        "documentId": "job-doc-123",
        "content": (
            "# [Overview]\n"
            "Title: Senior Backend Developer. We are Google. We build scale systems.\n"
            "# [Responsibilities]\n"
            "- Write robust Java microservices.\n"
            "# [Requirements]\n"
            "Requires 5 years experience. Skills: Java, AWS, Kubernetes.\n"
            "# [Benefits]\n"
            "Free meals.\n"
        )
    }
    
    # Process
    resp = client.post("/api/v1/ai/job/process", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert "knowledge" in data
    assert "metadata" in data
    assert "qualityMetrics" in data
    assert "insights" in data
    
    # Verify Get Knowledge
    resp_get = client.get("/api/v1/ai/job/job-doc-123")
    assert resp_get.status_code == 200
    assert resp_get.json()["jobTitle"]["value"] == "Senior Backend Developer"
    
    # Verify Get Metadata
    resp_meta = client.get("/api/v1/ai/job/job-doc-123/metadata")
    assert resp_meta.status_code == 200
    assert "metadata" in resp_meta.json()
    assert "qualityMetrics" in resp_meta.json()
    assert resp_meta.json()["metadata"]["primaryLanguage"] == "Java"
    
    # Verify Get Insights
    resp_insights = client.get("/api/v1/ai/job/job-doc-123/insights")
    assert resp_insights.status_code == 200
    assert "Java Ecosystem" in resp_insights.json()["engineering"]
    
    # Verify Search
    search_payload = {
        "query": "Java AWS engineer",
        "documentType": "JOB",
        "limit": 3
    }
    resp_search = client.post("/api/v1/ai/job/search", json=search_payload)
    assert resp_search.status_code == 200
    assert len(resp_search.json()["results"]) > 0
