from fastapi.testclient import TestClient
from app.main import app
from app.company.parser import CompanyParser
from app.company.extractors import TechnologyExtractor, IndustryClassifier, HiringSignalExtractor, CompanyExtractor
from app.company.insights import CompanyInsightsService

client = TestClient(app)

def test_company_parser_aliases():
    parser = CompanyParser()
    sample_text = (
        "# [Who We Are]\n"
        "We are a leading fintech firm.\n"
        "# [Technologies]\n"
        "We build apps using React and Python.\n"
        "# [Hiring]\n"
        "Looking for senior engineers in London.\n"
    )
    sections = parser.parse(sample_text)
    assert "about" in sections
    assert "We are a leading fintech firm." in sections["about"]
    assert "tech_stack" in sections
    assert "React and Python" in sections["tech_stack"]
    assert "careers" in sections
    assert "senior engineers" in sections["careers"]

def test_technology_extractor():
    extractor = TechnologyExtractor()
    text = "We use ReactJS, AWS, and Node for our main systems."
    techs = extractor.extract_tech(text)
    assert "React" in techs
    assert "Amazon Web Services" in techs
    assert "Node.js" in techs
    
    categories = extractor.categorize_tech(techs)
    assert "Frontend" in categories
    assert "Cloud" in categories
    assert "DevOps" not in categories  # Docker/K8s not in text

def test_industry_classifier():
    classifier = IndustryClassifier()
    text1 = "We offer mobile banking and payment gateway integrations."
    industries1 = classifier.classify(text1)
    assert "FinTech" in industries1
    
    text2 = "We build cloud hosting infrastructure."
    industries2 = classifier.classify(text2)
    assert "Cloud" in industries2

def test_hiring_signals_extractor():
    extractor = HiringSignalExtractor()
    text = "Looking for Senior backend devs in Tokyo. Internship positions are also available."
    signals = extractor.extract(text)
    assert signals["internshipPrograms"] is True
    assert "Tokyo" in signals["hiringLocations"]
    assert "Senior" in signals["preferredExperienceLevels"]

def test_insights_service():
    insights_service = CompanyInsightsService()
    knowledge = {
        "technologyStack": {"value": ["Java", "Docker", "Kubernetes", "OpenAI"]},
        "employeeRange": {"value": "10000+"},
        "ownershipType": {"value": "Public"},
        "hiringSignals": {"value": ["Hiring interns", "Remote friendly options"]},
        "benefits": {"value": ["free meals"]}
    }
    metadata = {"remotePolicy": "Hybrid"}
    insights = insights_service.generate_insights(knowledge, metadata)
    
    assert "Java Ecosystem" in insights["engineering"]
    assert "Cloud Native" in insights["engineering"]
    assert "AI First" in insights["engineering"]
    assert "Enterprise" in insights["business"]
    assert "Internship Friendly" in insights["hiring"]
    assert "Remote Friendly" in insights["hiring"]

def test_company_process_flow_api():
    payload = {
        "documentId": "company-doc-123",
        "content": (
            "# [About]\n"
            "Welcome to Netflix. Legal entity name is Netflix Inc. Headquarters in Los Gatos, California.\n"
            "# [Stack]\n"
            "We run on AWS, Java, and React.\n"
            "# [Jobs]\n"
            "We hire remote engineers.\n"
        )
    }
    
    # Process
    resp = client.post("/api/v1/ai/company/process", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert "knowledge" in data
    assert "metadata" in data
    assert "insights" in data
    
    # Verify Get Knowledge
    resp_get = client.get("/api/v1/ai/company/company-doc-123")
    assert resp_get.status_code == 200
    assert resp_get.json()["companyName"]["value"] == "Netflix"
    
    # Verify Get Metadata
    resp_meta = client.get("/api/v1/ai/company/company-doc-123/metadata")
    assert resp_meta.status_code == 200
    assert "Amazon Web Services" in resp_meta.json()["primaryTechnologyStack"]
    
    # Verify Get Insights
    resp_insights = client.get("/api/v1/ai/company/company-doc-123/insights")
    assert resp_insights.status_code == 200
    assert "Java Ecosystem" in resp_insights.json()["engineering"]
    
    # Verify Search
    search_payload = {
        "query": "Java AWS streaming",
        "documentType": "COMPANY",
        "limit": 3
    }
    resp_search = client.post("/api/v1/ai/company/search", json=search_payload)
    assert resp_search.status_code == 200
    assert len(resp_search.json()["results"]) > 0
