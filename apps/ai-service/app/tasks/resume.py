"""Resume intelligence tasks: parse, ATS analysis, job-specific optimisation."""

from __future__ import annotations

import re
from datetime import date
from typing import Any, Optional

from pydantic import BaseModel, Field

from app.core.llm.prompting import PromptSpec, UntrustedPart
from app.tasks.base import (
    TaskContext, TaskResult, appears_in, clamp01, grounded_list, grounded_or_none, llm_result,
    normalize, optional_dict, optional_text, require_text, task,
)

# ------------------------------------------------------------------------------------------
# RESUME_PARSE
# ------------------------------------------------------------------------------------------


class _Experience(BaseModel):
    company: str
    title: Optional[str] = None
    startDate: Optional[str] = None
    endDate: Optional[str] = None
    responsibilities: list[str] = Field(default_factory=list)


class _Education(BaseModel):
    institution: str
    degree: Optional[str] = None
    fieldOfStudy: Optional[str] = None
    graduationYear: Optional[int] = None
    grade: Optional[str] = None


class _Project(BaseModel):
    name: str
    description: Optional[str] = None
    technologies: list[str] = Field(default_factory=list)
    url: Optional[str] = None


class _Certification(BaseModel):
    name: str
    issuer: Optional[str] = None
    date: Optional[str] = None
    credentialId: Optional[str] = None
    url: Optional[str] = None


class ResumeParseOutput(BaseModel):
    name: Optional[str] = None
    email: Optional[str] = None
    phone: Optional[str] = None
    location: Optional[str] = None
    links: list[str] = Field(default_factory=list)
    summary: Optional[str] = None
    skills: list[str] = Field(default_factory=list)
    experience: list[_Experience] = Field(default_factory=list)
    education: list[_Education] = Field(default_factory=list)
    projects: list[_Project] = Field(default_factory=list)
    certifications: list[_Certification] = Field(default_factory=list)
    achievements: list[str] = Field(default_factory=list)
    languages: list[str] = Field(default_factory=list)


RESUME_PARSE_INSTRUCTIONS = """\
You extract structured data from a candidate's resume.
- Copy values exactly as written in the resume (names, companies, titles, dates, URLs, IDs).
- Only extract what is explicitly present. Use null or an empty list for anything missing.
- skills: a flat list of skill names (e.g. "Java", "Spring Boot", "PostgreSQL") explicitly mentioned
  anywhere in the resume, one skill per item.
- experience: one entry per job, most recent first; dates as written (e.g. "Jan 2021", "2021-03",
  "Present"); responsibilities copied or minimally condensed from the bullet points.
- summary: the candidate's own summary/objective section if present, otherwise null."""

_MONTHS = {m: i for i, m in enumerate(
    ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"], start=1)}


def parse_resume_date(value: str | None, *, is_end: bool = False) -> date | None:
    if not value:
        return None
    v = value.strip().lower()
    if v in ("present", "current", "now", "till date", "to date", "ongoing"):
        return date.today() if is_end else None
    m = re.search(r"(\d{4})[-/.](\d{1,2})", v)
    if m:
        return date(int(m.group(1)), max(1, min(12, int(m.group(2)))), 1)
    m = re.search(r"(\d{1,2})[-/.](\d{4})", v)
    if m:
        return date(int(m.group(2)), max(1, min(12, int(m.group(1)))), 1)
    m = re.search(r"([a-z]{3})[a-z]*\.?\s*,?\s*(\d{4})", v)
    if m and m.group(1) in _MONTHS:
        return date(int(m.group(2)), _MONTHS[m.group(1)], 1)
    m = re.search(r"\b(19|20)\d{2}\b", v)
    if m:
        return date(int(m.group(0)), 12 if is_end else 1, 1)
    return None


def _months_between(start: date, end: date) -> int:
    return max(0, (end.year - start.year) * 12 + (end.month - start.month))


def _experience_intelligence(experience: list[dict[str, Any]]) -> dict[str, Any]:
    periods = []
    for exp in experience:
        start = parse_resume_date(exp.get("startDate"))
        end = parse_resume_date(exp.get("endDate"), is_end=True) if exp.get("endDate") else None
        if start and end and end >= start:
            periods.append((start, end, exp))
    periods.sort(key=lambda p: p[0])
    total_months = 0
    gaps, overlaps = [], []
    merged_end: date | None = None
    for start, end, exp in periods:
        if merged_end is None or start > merged_end:
            if merged_end is not None and _months_between(merged_end, start) >= 3:
                gaps.append({"from": merged_end.isoformat(), "to": start.isoformat(),
                             "months": _months_between(merged_end, start)})
            total_months += _months_between(start, end)
            merged_end = end
        else:
            overlaps.append({"company": exp.get("company"), "overlapsUntil": min(end, merged_end).isoformat()})
            if end > merged_end:
                total_months += _months_between(merged_end, end)
                merged_end = end
    return {
        "totalYearsExperience": round(total_months / 12.0, 1) if periods else None,
        "datedRoles": len(periods),
        "undatedRoles": len(experience) - len(periods),
        "currentRole": experience[0].get("designation") if experience else None,
        "careerProgression": [p[2].get("designation") for p in periods if p[2].get("designation")],
        "employmentGaps": gaps,
        "overlappingPeriods": overlaps,
        "method": "computed-from-extracted-dates",
    }


def skill_category(name: str) -> str | None:
    from app.tasks.job_company import TECH_CATEGORIES

    key = normalize(name)
    for category, members in TECH_CATEGORIES.items():
        if key in members:
            return category
    return None


def ground_resume(parsed: ResumeParseOutput, text: str) -> tuple[dict[str, Any], dict[str, int]]:
    dropped = {"skills": 0, "experience": 0, "education": 0, "projects": 0, "certifications": 0, "fields": 0}

    def keep(value):
        g = grounded_or_none(value, text)
        if value and g is None:
            dropped["fields"] += 1
        return g

    skills, seen = [], set()
    for raw in parsed.skills:
        for name in re.split(r"\s*[,;/|]\s*", raw or ""):  # tolerate "Kotlin, Java" in one item
            if not name or normalize(name) in seen:
                continue
            if appears_in(name, text):
                seen.add(normalize(name))
                skills.append({"skill": name.strip(), "category": skill_category(name), "confidence": 0.9,
                               "source": "llm-extraction-grounded"})
            else:
                dropped["skills"] += 1

    experience = []
    for e in parsed.experience:
        if not appears_in(e.company, text):
            dropped["experience"] += 1
            continue
        start, end = keep(e.startDate), keep(e.endDate)
        sd = parse_resume_date(start)
        ed = parse_resume_date(end, is_end=True) if end else None
        experience.append({
            "company": e.company.strip(),
            "designation": keep(e.title),
            "startDate": start,
            "endDate": end,
            "durationMonths": _months_between(sd, ed) if sd and ed and ed >= sd else None,
            "responsibilities": [r.strip() for r in e.responsibilities if r and r.strip()],
        })

    education = []
    for ed_ in parsed.education:
        if not appears_in(ed_.institution, text):
            dropped["education"] += 1
            continue
        year = ed_.graduationYear if ed_.graduationYear and str(ed_.graduationYear) in text else None
        field_of_study = ed_.fieldOfStudy if ed_.fieldOfStudy and not ed_.fieldOfStudy.strip().isdigit() else None
        if year is None:
            m = re.search(r"(19|20)\d{2}", f"{ed_.fieldOfStudy or ''} {ed_.degree or ''}")
            year = int(m.group(0)) if m and m.group(0) in text else None
        education.append({
            "institution": ed_.institution.strip(),
            "degree": keep(ed_.degree),
            "specialization": keep(field_of_study),
            "graduationYear": year,
            "gpa": keep(ed_.grade),
        })

    projects = []
    for p in parsed.projects:
        if not appears_in(p.name, text):
            dropped["projects"] += 1
            continue
        url = keep(p.url)
        techs = grounded_list(p.technologies, text)
        projects.append({
            "name": p.name.strip(),
            "description": p.description,
            "technologies": techs,
            "gitHubUrl": url if url and "github" in url.lower() else None,
            "liveUrl": url if url and "github" not in url.lower() else None,
            "technologyDiversityCount": len(techs),
        })

    certifications = []
    for c in parsed.certifications:
        if not appears_in(c.name, text):
            dropped["certifications"] += 1
            continue
        certifications.append({
            "certificationName": c.name.strip(),
            "issuingOrganization": keep(c.issuer),
            "issueDate": keep(c.date),
            "expirationDate": None,
            "credentialId": keep(c.credentialId),
            "credentialUrl": keep(c.url),
        })

    links = grounded_list(parsed.links, text)
    linkedin = next((l for l in links if "linkedin" in l.lower()), None)
    knowledge = {
        "personalInformation": {
            "name": keep(parsed.name),
            "email": keep(parsed.email),
            "phone": keep(parsed.phone),
            "location": keep(parsed.location),
            "linkedin": linkedin,
            "links": links,
        },
        "summary": parsed.summary if parsed.summary and len(parsed.summary.split()) > 3 else None,
        "skills": skills,
        "education": education,
        "experience": experience,
        "projects": projects,
        "certifications": certifications,
        "achievements": grounded_list(parsed.achievements, text),
        "languages": grounded_list(parsed.languages, text),
    }
    knowledge["intelligence"] = {
        "experienceIntelligence": _experience_intelligence(experience),
        "projectCount": len(projects),
        "certificationCount": len(certifications),
    }
    return knowledge, dropped


@task("RESUME_PARSE")
def resume_parse(ctx: TaskContext) -> TaskResult:
    text = require_text(ctx.payload, "content", "resume text")
    spec = PromptSpec(RESUME_PARSE_INSTRUCTIONS, untrusted=[UntrustedPart("resume", text)])
    parsed, outcome = ctx.gateway.generate("RESUME_PARSE", spec, ResumeParseOutput, max_tokens=3000)
    knowledge, dropped = ground_resume(parsed, text)
    knowledge["grounding"] = {
        "policy": "values not found verbatim in the resume text are discarded",
        "dropped": dropped,
    }
    return llm_result(knowledge, outcome)


# ------------------------------------------------------------------------------------------
# RESUME_ATS (legacy alias: JOB_MATCH, which the backend historically used for ATS analysis)
# ------------------------------------------------------------------------------------------


class AtsReviewOutput(BaseModel):
    overallAssessment: str
    strengths: list[str] = Field(default_factory=list)
    weaknesses: list[str] = Field(default_factory=list)
    improvements: list[str] = Field(default_factory=list)
    missingSections: list[str] = Field(default_factory=list)


ATS_INSTRUCTIONS = """\
You are an ATS (applicant tracking system) resume reviewer. Review the structured resume data (and
the raw resume text when provided). Report concrete strengths, weaknesses and actionable
improvements about content, structure, keyword clarity and quantified impact. Refer only to what is
in the data. Do not invent experience or skills the candidate does not have."""


def _readability(text: str | None) -> float | None:
    if not text or len(text.split()) < 40:
        return None
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    bullet_ratio = sum(1 for l in lines if re.match(r"^[-*•▪●◦]|^\d+[.)]", l)) / max(len(lines), 1)
    sentences = [s for s in re.split(r"[.!?\n]+", text) if len(s.split()) >= 3]
    avg_len = sum(len(s.split()) for s in sentences) / max(len(sentences), 1)
    length_score = 1.0 if avg_len <= 22 else max(0.0, 1.0 - (avg_len - 22) / 30)
    return round(0.6 * length_score + 0.4 * min(1.0, bullet_ratio * 2.5), 3)


def deterministic_ats(knowledge: dict[str, Any], content: str | None, job_keywords: list[str]) -> dict[str, Any]:
    info = knowledge.get("personalInformation") or {}
    skills = knowledge.get("skills") or []
    experience = knowledge.get("experience") or []
    projects = knowledge.get("projects") or []
    contact = sum([bool(info.get("email")), bool(info.get("phone")),
                   bool(info.get("linkedin") or info.get("links"))]) / 3.0
    sections = [bool(knowledge.get("summary")), bool(skills), bool(experience),
                bool(knowledge.get("education")), bool(projects), bool(knowledge.get("certifications"))]
    section_cov = sum(sections) / len(sections)
    categories = {s.get("category") for s in skills if s.get("category")}
    years = ((knowledge.get("intelligence") or {}).get("experienceIntelligence") or {}).get("totalYearsExperience")
    keyword_cov = None
    matched_kw, missing_kw = [], []
    if job_keywords and content:
        for kw in job_keywords:
            (matched_kw if appears_in(kw, content) else missing_kw).append(kw)
        keyword_cov = round(len(matched_kw) / len(job_keywords), 3)
    metrics = {
        "completenessScore": round((contact + section_cov) / 2.0, 3),
        "sectionCoverage": round(section_cov, 3),
        "contactQuality": round(contact, 3),
        "skillDiversity": round(min(len(categories) / 5.0, 1.0), 3),
        "projectStrength": round(min(len(projects) * 0.25, 1.0), 3),
        "experienceStrength": round(min(years / 8.0, 1.0), 3) if years is not None else None,
        "readabilityScore": _readability(content),
        "keywordCoverage": keyword_cov,
        # Visual formatting cannot be measured from extracted text; reported as unavailable.
        "formattingQuality": None,
    }
    components = [v for k, v in metrics.items() if v is not None and k not in ("sectionCoverage", "contactQuality")]
    metrics["atsScore"] = round(sum(components) / len(components), 3) if components else None
    metrics["details"] = {
        "method": "deterministic metrics computed from extracted resume data",
        "unavailableMetrics": [k for k, v in metrics.items() if v is None],
        "matchedKeywords": matched_kw,
        "missingKeywords": missing_kw,
        "warnings": [name for name, present in zip(
            ["summary", "skills", "experience", "education", "projects", "certifications"], sections) if not present],
    }
    return metrics


@task("RESUME_ATS", "JOB_MATCH")
def resume_ats(ctx: TaskContext) -> TaskResult:
    knowledge = optional_dict(ctx.payload, "knowledge")
    if not knowledge:
        from app.core.llm.errors import InvalidTaskInputError

        raise InvalidTaskInputError("payload.knowledge (parsed resume) is required for ATS analysis")
    content = optional_text(ctx.payload, "content")
    keywords = [k for k in (ctx.payload.get("jobKeywords") or []) if isinstance(k, str) and k.strip()]
    metrics = deterministic_ats(knowledge, content, keywords)
    untrusted = [UntrustedPart("resume_structured", str({k: v for k, v in knowledge.items() if k != "grounding"}))]
    if content:
        untrusted.append(UntrustedPart("resume_text", content))
    review, outcome = ctx.gateway.generate("RESUME_ATS", PromptSpec(ATS_INSTRUCTIONS, untrusted=untrusted),
                                           AtsReviewOutput, max_tokens=1200)
    metrics["aiReview"] = review.model_dump()
    return llm_result(metrics, outcome, method="hybrid")


# ------------------------------------------------------------------------------------------
# RESUME_OPTIMIZE (job-specific)
# ------------------------------------------------------------------------------------------


class _MissingSkill(BaseModel):
    skill: str
    importance: str  # REQUIRED | PREFERRED
    jobEvidence: str


class _Bullet(BaseModel):
    original: str
    suggested: str
    rationale: str


class _ProjectRelevance(BaseModel):
    project: str
    relevance: str  # HIGH | MEDIUM | LOW
    reason: str


class ResumeOptimizeOutput(BaseModel):
    missingSkills: list[_MissingSkill] = Field(default_factory=list)
    keywordGaps: list[str] = Field(default_factory=list)
    experienceGaps: list[str] = Field(default_factory=list)
    bulletImprovements: list[_Bullet] = Field(default_factory=list)
    summarySuggestion: Optional[str] = None
    projectRelevance: list[_ProjectRelevance] = Field(default_factory=list)
    measurableImpactSuggestions: list[str] = Field(default_factory=list)
    atsImprovements: list[str] = Field(default_factory=list)


OPTIMIZE_INSTRUCTIONS = """\
You help a candidate tailor their resume to one specific job.
- missingSkills: skills the job asks for that do not appear in the resume; jobEvidence must be an
  exact short quote from the job description.
- bulletImprovements: pick existing resume bullets ("original" must be copied exactly from the
  resume) and rewrite them to be clearer and more relevant to the job. You may improve wording only.
  NEVER add achievements, numbers, technologies, employers or responsibilities that are not in the
  original. Where a metric would help, insert a placeholder such as [X%] or [N users] for the
  candidate to fill in.
- summarySuggestion: a rewritten summary using only facts from the resume.
- Be honest: if the candidate lacks something, list it as a gap rather than hiding it."""

_NUMBER = re.compile(r"\d+(?:[.,]\d+)?\s*%?")


@task("RESUME_OPTIMIZE")
def resume_optimize(ctx: TaskContext) -> TaskResult:
    resume_text = require_text(ctx.payload, "resumeText", "resume text")
    job_text = require_text(ctx.payload, "jobDescription", "job description")
    spec = PromptSpec(OPTIMIZE_INSTRUCTIONS, trusted_context=optional_dict(ctx.payload, "context") or None,
                      untrusted=[UntrustedPart("resume", resume_text), UntrustedPart("job_description", job_text)])
    out, outcome = ctx.gateway.generate("RESUME_OPTIMIZE", spec, ResumeOptimizeOutput, max_tokens=2500)

    missing = [m.model_dump() for m in out.missingSkills
               if appears_in(m.skill, job_text) and not appears_in(m.skill, resume_text)]
    bullets, rejected = [], []
    for b in out.bulletImprovements:
        if not appears_in(b.original, resume_text):
            rejected.append({"suggested": b.suggested, "reason": "original bullet not found in resume"})
            continue
        new_numbers = {n.strip() for n in _NUMBER.findall(b.suggested)} - {n.strip() for n in _NUMBER.findall(b.original)}
        item = b.model_dump()
        item["introducesUnverifiedClaims"] = bool(new_numbers)
        if new_numbers:
            item["unverifiedValues"] = sorted(new_numbers)
        bullets.append(item)
    result = out.model_dump()
    result.update({
        "missingSkills": missing,
        "keywordGaps": [k for k in out.keywordGaps if appears_in(k, job_text) and not appears_in(k, resume_text)],
        "bulletImprovements": bullets,
        "rejectedSuggestions": rejected,
        "policy": "suggestions may reword existing content only; unverified numbers are flagged for the candidate",
    })
    return llm_result(result, outcome)
