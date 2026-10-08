"""Job and company intelligence tasks.

Parsing uses the configured LLM, then every extracted field is grounded against the source text.
Metadata tasks are deterministic derivations of the parsed knowledge (provider ``taxonomy``) and
never fill in defaults: unknown values stay ``null``.
"""

from __future__ import annotations

import re
from typing import Any, Optional

from pydantic import BaseModel, Field

from app.core.llm.errors import InvalidTaskInputError
from app.core.llm.prompting import PromptSpec, UntrustedPart
from app.tasks.base import (
    TaskContext, TaskResult, appears_in, extracted, grounded_list, grounded_or_none, llm_result,
    focus_text, normalize, optional_dict, optional_text, require_text, task,
)

# ------------------------------------------------------------------------------------------
# Shared taxonomy (used for deterministic metadata only, never to invent values)
# ------------------------------------------------------------------------------------------

TECH_CATEGORIES: dict[str, set[str]] = {
    "Programming Languages": {"java", "python", "go", "golang", "typescript", "javascript", "kotlin", "scala", "rust",
                              "c++", "c#", "ruby", "php", "swift"},
    "Frontend": {"react", "angular", "vue", "next.js", "svelte", "html", "css", "tailwind"},
    "Backend": {"spring", "spring boot", "django", "flask", "fastapi", "node.js", "express", ".net", "rails"},
    "Databases": {"postgresql", "postgres", "mysql", "mongodb", "redis", "cassandra", "dynamodb", "elasticsearch",
                  "oracle", "sql server", "bigtable", "spanner"},
    "Cloud": {"aws", "amazon web services", "gcp", "google cloud", "azure"},
    "DevOps": {"docker", "kubernetes", "terraform", "jenkins", "github actions", "ci/cd", "ansible", "helm"},
    "AI/ML": {"pytorch", "tensorflow", "machine learning", "llm", "openai", "scikit-learn", "nlp"},
    "Data": {"kafka", "spark", "airflow", "hadoop", "flink", "snowflake", "dbt"},
}
_LANGS = ["java", "python", "go", "golang", "typescript", "javascript", "kotlin", "scala", "rust", "c#", "c++", "ruby"]


def categorize(names: list[str]) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    for name in names:
        key = normalize(name)
        for cat, members in TECH_CATEGORIES.items():
            if key in members:
                out.setdefault(cat, []).append(name)
    return out


def _first_present(candidates: list[str], names_lower: list[str]) -> str | None:
    for c in candidates:
        if c in names_lower:
            return "Go" if c == "golang" else c.title() if c not in ("c#", "c++") else c.upper()
    return None


def _level(count: int, high: int, medium: int) -> str | None:
    if count >= high:
        return "HIGH"
    if count >= medium:
        return "MEDIUM"
    return "LOW" if count >= 0 else None


def parse_salary_text(text: str | None) -> dict[str, Any]:
    """Deterministically parse a salary string that already exists in the posting."""
    if not text:
        return {"salarySpecified": False}
    def number(raw: str) -> float:
        # "150,000" / "1.500.000" are thousands separators; a single "." with <3 decimals is a decimal point.
        if raw.count(".") > 1 or re.fullmatch(r"\d{1,3}(\.\d{3})+", raw):
            return float(raw.replace(".", "").replace(",", ""))
        return float(raw.replace(",", ""))

    def scale(value: float, suffix: str | None) -> float:
        s = (suffix or "").lower()
        return value * {"k": 1_000, "m": 1_000_000, "lpa": 100_000, "lakh": 100_000, "lakhs": 100_000}.get(s, 1)

    num = r"(\d{1,3}(?:[,.]\d{3})+|\d+(?:\.\d+)?)"
    suf = r"\s*(k|m|lpa|lakhs?)?\b"
    numbers: list[float] = []
    consumed = text
    # Ranges first, so "30-45 LPA" applies the unit to both ends.
    for a, sa, b, sb in re.findall(num + suf + r"\s*(?:-|–|to)\s*[^\d\s]{0,3}\s*" + num + suf, text, re.IGNORECASE):
        numbers += [scale(number(a), sa or sb), scale(number(b), sb or sa)]
    if numbers:
        consumed = ""
    for raw, s in re.findall(num + suf, consumed, re.IGNORECASE):
        numbers.append(scale(number(raw), s))
    numbers = [n for n in numbers if n >= 100]
    currency = None
    for symbol, code in (("$", "USD"), ("usd", "USD"), ("€", "EUR"), ("eur", "EUR"), ("£", "GBP"), ("gbp", "GBP"),
                         ("₹", "INR"), ("inr", "INR"), ("lpa", "INR"), ("lakh", "INR"), ("cad", "CAD"), ("aud", "AUD")):
        if symbol in text.lower():
            currency = code
            break
    period = "HOURLY" if re.search(r"/\s*h(ou)?r|per hour|hourly", text, re.I) else \
        "MONTHLY" if re.search(r"/\s*mo|per month|monthly", text, re.I) else \
        "YEARLY" if re.search(r"year|annual|/\s*yr|lpa|per annum|p\.a", text, re.I) else None
    if not numbers:
        return {"salarySpecified": False, "raw": text}
    return {"salarySpecified": True, "minimum": min(numbers), "maximum": max(numbers), "currency": currency,
            "payPeriod": period, "raw": text}


# ------------------------------------------------------------------------------------------
# JOB_PARSE
# ------------------------------------------------------------------------------------------


class _JobSkill(BaseModel):
    name: str
    importance: str  # REQUIRED | PREFERRED
    category: Optional[str] = None


class JobParseOutput(BaseModel):
    jobTitle: Optional[str] = None
    companyName: Optional[str] = None
    department: Optional[str] = None
    seniority: Optional[str] = None
    employmentType: Optional[str] = None
    workMode: Optional[str] = None
    locations: list[str] = Field(default_factory=list)
    salaryText: Optional[str] = None
    experienceRequired: Optional[str] = None
    educationRequirements: list[str] = Field(default_factory=list)
    certifications: list[str] = Field(default_factory=list)
    skills: list[_JobSkill] = Field(default_factory=list)
    responsibilities: list[str] = Field(default_factory=list)
    qualifications: list[str] = Field(default_factory=list)
    benefits: list[str] = Field(default_factory=list)
    visaSponsorshipMentioned: Optional[bool] = None
    relocationMentioned: Optional[bool] = None
    applicationDeadline: Optional[str] = None


JOB_PARSE_INSTRUCTIONS = """\
You extract structured data from a job posting.
- Copy values exactly as they appear in the posting. Use null / empty lists for anything not stated.
- skills: each technology or skill mentioned, with importance REQUIRED (must-have / requirements) or
  PREFERRED (nice-to-have / bonus / preferred), and a short category.
- seniority: as stated (e.g. "Senior", "Junior", "Lead", "Intern"), null if not stated.
- workMode: Remote, Hybrid or Onsite only if the posting says so.
- salaryText: the salary/compensation text copied exactly, or null.
- visaSponsorshipMentioned / relocationMentioned: true only if explicitly offered, false only if
  explicitly not offered, otherwise null.
- Never guess the company, location, salary or dates.
- Keep the output compact: at most 25 skills, at most 8 responsibilities, 8 qualifications and
  6 benefits, each copied as a short phrase (under 20 words) from the posting."""


def _infer_seniority(title: str | None, stated: str | None) -> str | None:
    text = normalize(f"{stated or ''} {title or ''}")
    for label, words in (("Internship", ("intern",)), ("Principal", ("principal", "staff", "architect")),
                         ("Lead", ("lead", "manager", "head of")), ("Senior", ("senior", "sr.", "sr ")),
                         ("Junior", ("junior", "jr.", "graduate", "entry level", "entry-level", "fresher")),
                         ("Mid-Level", ("mid-level", "mid level", "intermediate"))):
        if any(w in text for w in words):
            return label
    return None


@task("JOB_PARSE")
def job_parse(ctx: TaskContext) -> TaskResult:
    full_text = require_text(ctx.payload, "content", "job description")
    # Long postings are reduced to their relevant sections (verbatim) for slow local models.
    text = focus_text(full_text, ctx.gateway.config.max_document_chars)
    source_url = optional_text(ctx.payload, "sourceUrl") or optional_text(ctx.payload, "url")
    source_platform = optional_text(ctx.payload, "sourcePlatform")
    spec = PromptSpec(JOB_PARSE_INSTRUCTIONS, untrusted=[UntrustedPart("job_posting", text)])
    p, outcome = ctx.gateway.generate("JOB_PARSE", spec, JobParseOutput, max_tokens=1600)
    # enforce the documented bounds even if the model ignored them
    p.skills = p.skills[:25]
    p.responsibilities = p.responsibilities[:8]
    p.qualifications = p.qualifications[:8]
    p.benefits = p.benefits[:6]

    title = grounded_or_none(p.jobTitle, text)
    stated_seniority = grounded_or_none(p.seniority, text)
    required, preferred, seen = [], [], set()
    for s in p.skills:
        if not appears_in(s.name, text) or normalize(s.name) in seen:
            continue
        seen.add(normalize(s.name))
        item = {"name": s.name.strip(), "confidence": 0.9,
                "importance": "PREFERRED" if (s.importance or "").upper().startswith("PREF") else "REQUIRED",
                "category": s.category}
        (preferred if item["importance"] == "PREFERRED" else required).append(item)
    salary_text = grounded_or_none(p.salaryText, text)
    salary = parse_salary_text(salary_text)
    work_mode = p.workMode if p.workMode and appears_in(p.workMode.split()[0], text) else None
    knowledge = {
        "jobTitle": extracted(title, 0.95),
        "normalizedJobTitle": extracted(re.sub(r"\s*[\(\[].*?[\)\]]", "", title).strip() if title else None, 0.85,
                                        "normalized"),
        "companyName": extracted(grounded_or_none(p.companyName, text), 0.9),
        "department": extracted(grounded_or_none(p.department, text), 0.8),
        "declaredSeniority": extracted(stated_seniority, 0.9),
        "inferredSeniority": extracted(_infer_seniority(title, stated_seniority), 0.75, "rule-from-title"),
        "employmentType": extracted(grounded_or_none(p.employmentType, text), 0.85),
        "workMode": extracted(work_mode, 0.85),
        "locations": extracted(grounded_list(p.locations, text), 0.9),
        "salaryRange": extracted(salary_text, 0.95),
        "currency": extracted(salary.get("currency"), 0.9, "parsed-from-salary-text"),
        "experienceRequired": extracted(grounded_or_none(p.experienceRequired, text), 0.9),
        "educationRequirements": extracted(grounded_list(p.educationRequirements, text), 0.85),
        "certifications": extracted(grounded_list(p.certifications, text), 0.85),
        "requiredSkills": required,
        "preferredSkills": preferred,
        "responsibilities": extracted(grounded_list(p.responsibilities, text), 0.85),
        "qualifications": extracted(grounded_list(p.qualifications, text), 0.85),
        "benefits": extracted(grounded_list(p.benefits, text), 0.8),
        "visaSupport": extracted(p.visaSponsorshipMentioned if appears_in("visa", text) else None, 0.8),
        "relocationSupport": extracted(p.relocationMentioned if appears_in("relocat", text) else None, 0.8),
        "applicationDeadline": extracted(grounded_or_none(p.applicationDeadline, text), 0.85),
        "sourceUrl": extracted(source_url, 1.0, "caller-provided"),
        "sourcePlatform": extracted(source_platform, 1.0, "caller-provided"),
        "salary": salary,
        "analysedText": {"characters": len(text), "sourceCharacters": len(full_text),
                         "reduced": len(text) < len(full_text)},
    }
    return llm_result(knowledge, outcome)


def _value(knowledge: dict[str, Any], key: str) -> Any:
    v = knowledge.get(key)
    return v.get("value") if isinstance(v, dict) else v


@task("JOB_METADATA")
def job_metadata(ctx: TaskContext) -> TaskResult:
    knowledge = optional_dict(ctx.payload, "knowledge")
    if not knowledge:
        raise InvalidTaskInputError("payload.knowledge (parsed job) is required")
    skills = (knowledge.get("requiredSkills") or []) + (knowledge.get("preferredSkills") or [])
    names = [s.get("name") for s in skills if isinstance(s, dict) and s.get("name")]
    lower = [normalize(n) for n in names]
    work_mode = (_value(knowledge, "workMode") or "")
    seniority = _value(knowledge, "inferredSeniority")
    cloud = next((("AWS" if c in ("aws", "amazon web services") else "GCP" if c in ("gcp", "google cloud") else "Azure")
                  for c in ("aws", "amazon web services", "gcp", "google cloud", "azure") if c in lower), None)
    responsibilities = _value(knowledge, "responsibilities") or []
    metadata = {
        "primaryLanguage": _first_present(_LANGS, lower),
        "cloud": cloud,
        "industry": None,  # not derivable from the posting alone
        "experience": _value(knowledge, "experienceRequired"),
        "remote": True if "remote" in work_mode.lower() else (False if work_mode else None),
        "internship": seniority == "Internship" if seniority else None,
        "technologyCategories": categorize(names),
    }
    filled = [k for k in ("jobTitle", "companyName", "locations", "experienceRequired", "salaryRange", "workMode",
                          "employmentType") if _value(knowledge, k)]
    quality = {
        "skillDensity": round(min(len(names) / 12.0, 1.0), 3),
        "technologyDiversity": round(min(len(categorize(names)) / 5.0, 1.0), 3),
        "requirementCompleteness": round(len(filled) / 7.0, 3),
        "jobDetailQuality": round(min(len(responsibilities) / 8.0, 1.0), 3),
        "remoteFriendliness": 1.0 if metadata["remote"] else (0.0 if metadata["remote"] is False else None),
        "seniorityComplexity": {"Internship": 0.1, "Junior": 0.3, "Mid-Level": 0.5, "Senior": 0.75,
                                "Lead": 0.85, "Principal": 0.95}.get(seniority),
        "learningOpportunity": None,  # not measurable from the posting without inventing signals
        "method": "deterministic, derived from parsed job fields",
    }
    return TaskResult(result={"metadata": metadata, "qualityMetrics": quality}, provider="taxonomy",
                      method="deterministic")


class _Insight(BaseModel):
    insight: str
    evidence: str


class InsightsOutput(BaseModel):
    engineering: list[_Insight] = Field(default_factory=list)
    business: list[_Insight] = Field(default_factory=list)
    hiring: list[_Insight] = Field(default_factory=list)


INSIGHTS_INSTRUCTIONS = """\
You produce short, evidence-based insights about a {subject} for a job seeker.
Three groups: engineering (tech stack, practices), business (product, market, stage), hiring (team,
seniority, process, remote/relocation). Each insight is one short phrase, and "evidence" must be an
exact short quote from the supplied text that supports it. If there is no evidence for a group,
return an empty list for that group. Do not use general knowledge about the company."""


def _grounded_insights(out: InsightsOutput, source: str) -> dict[str, Any]:
    result: dict[str, Any] = {}
    evidence: dict[str, list[dict[str, str]]] = {}
    for group in ("engineering", "business", "hiring"):
        kept = [i for i in getattr(out, group) if i.insight and appears_in(i.evidence, source)]
        result[group] = [i.insight for i in kept]
        evidence[group] = [i.model_dump() for i in kept]
    result["evidence"] = evidence
    return result


def _source_text(ctx: TaskContext) -> str:
    content = optional_text(ctx.payload, "content")
    knowledge = optional_dict(ctx.payload, "knowledge")
    if content:
        return content
    if knowledge:
        parts = []
        for k, v in knowledge.items():
            val = v.get("value") if isinstance(v, dict) else v
            if isinstance(val, list):
                val = "; ".join(x.get("name", str(x)) if isinstance(x, dict) else str(x) for x in val)
            if val:
                parts.append(f"{k}: {val}")
        return "\n".join(parts)
    raise InvalidTaskInputError("payload.content or payload.knowledge is required")


@task("JOB_INSIGHTS")
def job_insights(ctx: TaskContext) -> TaskResult:
    source = _source_text(ctx)
    spec = PromptSpec(INSIGHTS_INSTRUCTIONS.format(subject="job posting"),
                      untrusted=[UntrustedPart("job_posting", source)])
    out, outcome = ctx.gateway.generate("JOB_INSIGHTS", spec, InsightsOutput, max_tokens=1200)
    return llm_result(_grounded_insights(out, source), outcome)


# ------------------------------------------------------------------------------------------
# COMPANY_PARSE / COMPANY_METADATA / COMPANY_INSIGHTS / COMPANY_RESEARCH_SUMMARY
# ------------------------------------------------------------------------------------------


class CompanyParseOutput(BaseModel):
    companyName: Optional[str] = None
    website: Optional[str] = None
    headquarters: Optional[str] = None
    offices: list[str] = Field(default_factory=list)
    foundedYear: Optional[int] = None
    employeeCountText: Optional[str] = None
    fundingStage: Optional[str] = None
    ownershipType: Optional[str] = None
    industries: list[str] = Field(default_factory=list)
    products: list[str] = Field(default_factory=list)
    technologyStack: list[str] = Field(default_factory=list)
    engineeringCulture: Optional[str] = None
    hiringSignals: list[str] = Field(default_factory=list)
    benefits: list[str] = Field(default_factory=list)
    remotePolicyText: Optional[str] = None


COMPANY_PARSE_INSTRUCTIONS = """\
You extract structured facts about a company from the supplied text only (e.g. its careers or about
page). Copy values as written. Use null / empty lists when the text does not state something. Do not
use outside knowledge about the company, even if you recognise it."""


@task("COMPANY_PARSE")
def company_parse(ctx: TaskContext) -> TaskResult:
    text = focus_text(require_text(ctx.payload, "content", "company text"), ctx.gateway.config.max_document_chars)
    spec = PromptSpec(COMPANY_PARSE_INSTRUCTIONS, untrusted=[UntrustedPart("company_text", text)])
    p, outcome = ctx.gateway.generate("COMPANY_PARSE", spec, CompanyParseOutput, max_tokens=2000)
    founded = p.foundedYear if p.foundedYear and str(p.foundedYear) in text else None
    knowledge = {
        "companyName": extracted(grounded_or_none(p.companyName, text), 0.9),
        "website": extracted(grounded_or_none(p.website, text) or optional_text(ctx.payload, "url"), 0.9),
        "headquarters": extracted(grounded_or_none(p.headquarters, text), 0.85),
        "offices": extracted(grounded_list(p.offices, text), 0.85),
        "foundedYear": extracted(founded, 0.9),
        "employeeRange": extracted(grounded_or_none(p.employeeCountText, text), 0.8),
        "fundingStage": extracted(grounded_or_none(p.fundingStage, text), 0.8),
        "ownershipType": extracted(grounded_or_none(p.ownershipType, text), 0.7),
        "industries": extracted(grounded_list(p.industries, text), 0.75),
        "products": extracted(grounded_list(p.products, text), 0.8),
        "technologyStack": extracted(grounded_list(p.technologyStack, text), 0.9),
        "engineeringCulture": extracted(p.engineeringCulture if p.engineeringCulture else None, 0.6, "llm-summary"),
        "hiringSignals": extracted([h for h in p.hiringSignals if h], 0.6, "llm-summary"),
        "benefits": extracted([b for b in p.benefits if b], 0.7, "llm-summary"),
        "remotePolicy": extracted(grounded_or_none(p.remotePolicyText, text), 0.8),
    }
    return llm_result(knowledge, outcome)


@task("COMPANY_METADATA")
def company_metadata(ctx: TaskContext) -> TaskResult:
    knowledge = optional_dict(ctx.payload, "knowledge")
    if not knowledge:
        raise InvalidTaskInputError("payload.knowledge (parsed company) is required")
    tech = [t for t in (_value(knowledge, "technologyStack") or []) if isinstance(t, str)]
    lower = [normalize(t) for t in tech]
    cats = categorize(tech)
    backend = len(cats.get("Backend", [])) + len(cats.get("Programming Languages", []))
    frontend = len(cats.get("Frontend", []))
    focus_total = backend + frontend
    metadata = {
        "industries": _value(knowledge, "industries") or [],
        "employeeRange": _value(knowledge, "employeeRange"),
        "remotePolicy": _value(knowledge, "remotePolicy"),
        "technologyCategories": sorted(cats.keys()),
        "primaryLanguage": _first_present(_LANGS, lower),
        "primaryTechnologyStack": tech[:4],
        "secondaryTechnologies": tech[4:10],
        "backendFocus": round(backend / focus_total, 3) if focus_total else None,
        "frontendFocus": round(frontend / focus_total, 3) if focus_total else None,
        "cloudMaturity": _level(len(cats.get("Cloud", [])) + len(cats.get("DevOps", [])), 3, 1) if tech else None,
        "aiAdoption": _level(len(cats.get("AI/ML", [])), 2, 1) if tech else None,
        "devOpsMaturity": _level(len(cats.get("DevOps", [])), 3, 1) if tech else None,
        "method": "deterministic, derived from the company's stated technology stack",
    }
    return TaskResult(result=metadata, provider="taxonomy", method="deterministic")


@task("COMPANY_INSIGHTS")
def company_insights(ctx: TaskContext) -> TaskResult:
    source = _source_text(ctx)
    spec = PromptSpec(INSIGHTS_INSTRUCTIONS.format(subject="company"), untrusted=[UntrustedPart("company_text", source)])
    out, outcome = ctx.gateway.generate("COMPANY_INSIGHTS", spec, InsightsOutput, max_tokens=1200)
    return llm_result(_grounded_insights(out, source), outcome)


class CompanyResearchOutput(BaseModel):
    summary: str
    whatTheyDo: Optional[str] = None
    techStack: list[str] = Field(default_factory=list)
    cultureSignals: list[str] = Field(default_factory=list)
    talkingPoints: list[str] = Field(default_factory=list)
    questionsToAsk: list[str] = Field(default_factory=list)
    unknowns: list[str] = Field(default_factory=list)


@task("COMPANY_RESEARCH_SUMMARY")
def company_research_summary(ctx: TaskContext) -> TaskResult:
    sources = ctx.payload.get("sources") or []
    if not isinstance(sources, list) or not sources:
        raise InvalidTaskInputError("payload.sources (list of {source, text}) is required")
    parts = [UntrustedPart(str(s.get("source", f"source-{i}")), str(s.get("text", "")))
             for i, s in enumerate(sources) if isinstance(s, dict) and s.get("text")]
    if not parts:
        raise InvalidTaskInputError("payload.sources contains no text")
    spec = PromptSpec(
        "Summarise what a candidate should know about this company before applying or interviewing, "
        "using ONLY the supplied sources. List what the sources do not tell us under 'unknowns'.",
        trusted_context=optional_dict(ctx.payload, "context") or None,
        untrusted=parts,
    )
    out, outcome = ctx.gateway.generate("COMPANY_RESEARCH_SUMMARY", spec, CompanyResearchOutput, max_tokens=1500)
    combined = "\n".join(p.text for p in parts)
    result = out.model_dump()
    result["techStack"] = grounded_list(out.techStack, combined)
    result["sources"] = [p.name for p in parts]
    return llm_result(result, outcome)
