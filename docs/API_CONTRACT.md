# CareerPilot API contract (frontend ↔ backend)

All routes are under `/api/v1`, require `Authorization: Bearer <accessToken>` (except `/auth/*` and the
OAuth callback), and act **only on the authenticated user**. Never send `userId`, `candidateId` or
`ownerId` — they are ignored or rejected. Errors use one shape:

```json
{ "timestamp": "...", "status": 503, "error": "AI Unavailable", "code": "AI_PROVIDER_NOT_CONFIGURED",
  "message": "human readable", "details": null, "correlationId": "..." }
```

Important `code` values: `AI_PROVIDER_NOT_CONFIGURED`, `AI_PROVIDER_UNAVAILABLE`, `AI_TIMEOUT` /
`AI_PROVIDER_TIMEOUT` (504), `DATABASE_UNAVAILABLE` (503 — not an auth failure; do not log out),
`AI_INVALID_STRUCTURED_OUTPUT`, `AI_SERVICE_UNREACHABLE`, `CONFLICT` (409, e.g. invalid state transition
or "no processed resume yet"), `FORBIDDEN`, `NOT_FOUND`, `DRAFT_HAS_PLACEHOLDERS`, `RECIPIENT_NOT_ALLOWED`,
`NO_EMAIL_CONNECTION`, `EMAIL_PROVIDER_NOT_CONFIGURED`, `EMAIL_NOT_CONFIGURED`.
**The UI must show these errors honestly. It must never substitute placeholder/demo values.**

AI calls run on a real model and can take **30 s – several minutes** on a local CPU model. Show
progress states and never time out the request in the browser earlier than ~5 minutes.

## Auth
- `POST /auth/register {email, password, firstName, lastName}`
- `POST /auth/login {email, password}` → `{accessToken, refreshToken, user}`
- `POST /auth/refresh {refreshToken}` → same shape. `POST /auth/logout`.
- 401 = token invalid/expired → refresh once, else log out. **403 = forbidden (do NOT log out).**

## Profile — `/profile`
`GET /profile`; `PUT /profile/personal-info|preferences|social-links`;
`GET|POST /profile/{experience|education|projects|certifications}`, `DELETE /profile/{section}/{id}`.

## Resumes
- `GET /resumes` → `ResumeDto[]`: `id, title, originalFilename, mimeType, fileSize, parsingStatus,
  aiProcessingStatus (PENDING|PROCESSING|READY|FAILED), processingError, isDefault, isArchived,
  uploadedAt, versions[{id, versionNumber, changeReason, generatedByAi, createdAt}]`.
- `POST /resumes/upload` multipart (`file`, `title`) — PDF/DOCX/TXT ≤ 10 MB. AI processing starts
  automatically; poll `GET /resumes` until `aiProcessingStatus` is READY or FAILED.
- `PUT /resumes/{id}/default`, `DELETE /resumes/{id}`, `GET /resumes/{id}/download` (blob),
  `GET /resumes/storage-status`.
- `POST /ai/resume/process {resumeId}` → 202 (re-run processing).
- `GET /ai/resume/{resumeId}` → `{resumeId, aiProcessingStatus, processingError, versionNumber, structuredKnowledge}`
  where `structuredKnowledge` = `{personalInformation, summary, skills[{skill, category}], experience[{company,
  designation, startDate, endDate, durationMonths, responsibilities[]}], education[...], projects[...],
  certifications[...], intelligence{experienceIntelligence{totalYearsExperience, ...}}, grounding{dropped}}`.
- `GET /ai/resume/{resumeId}/ats` → `{atsScore, completenessScore, sectionCoverage, contactQuality,
  skillDiversity, projectStrength, experienceStrength, readabilityScore, keywordCoverage, formattingQuality,
  details{unavailableMetrics[], warnings[]}, aiReview{overallAssessment, strengths[], weaknesses[], improvements[]}}`.
  Any metric may be `null` = not measurable → show "Not available", never a number.
- `GET /ai/resume/parse-summary` → `{available:false}` or `{available:true, skills, experience, atsScore, ...}`.
- `POST /ai/resume/search {query}` → semantic search in MY resumes.
- `POST /ai/resume/{resumeId}/optimize {jobId}` → `{missingSkills[{skill, importance, jobEvidence}], keywordGaps[],
  bulletImprovements[{original, suggested, rationale, introducesUnverifiedClaims, unverifiedValues[]}],
  summarySuggestion, projectRelevance[], measurableImpactSuggestions[], atsImprovements[], rejectedSuggestions[]}`.

## Jobs & discovery
- `GET /discovery/jobs` (all discovered jobs), `GET /discovery/jobs/{id}`, `GET /discovery/connectors/health`.
- `POST /ai/job/search {query?, limit?}` → `{mode: "semantic"|"recent", results:[jobCard]}`; jobCard =
  `{jobId, title, company, location, workMode, employmentType, salary, postedDate, sourceUrl, source, connectorId,
  discoveredAt, provenance, relevance?}` (fields absent when the source did not provide them).
- `GET /ai/job/{jobId}` → jobCard + `analysis` (null until analysed) `{knowledge, metadata, qualityMetrics, insights}`.
- `POST /ai/job/{jobId}/analyze` → runs AI parse/metadata/insights (slow) and returns the analysis.
- `POST /ai/job/process-url?url=` / `POST /ai/job/process-text {content, title?}` → analyse an external posting.
- `POST /ai/company/research {jobId, urls?[≤3]}` → `{summary, whatTheyDo, techStack[], cultureSignals[],
  talkingPoints[], questionsToAsk[], unknowns[], sources[], fetchErrors[]}`.

## Matching
- `POST /ai/matching/match {jobId, analyzeJob?}` → MatchResult: `overallScore (0-100), individualScores
  {skillMatch, experienceMatch, ...}, notAssessedFactors[], jobAnalyzed, matchedSkills[], missingSkills[],
  strengths[], weaknesses[], criticalGaps[], recommendations[], explanation, confidenceScore`.
  409 when no processed resume. When `jobAnalyzed=false`, show "Analyse job for a full match".
- `POST /ai/matching/match/explain {jobId}` → `{match, aiExplanation{headline, explanation, strengths[], gaps[], recommendations[], verification}}`.
- `POST /ai/matching/gap-analysis {jobId, ai?}`; `POST /ai/matching/recommendations {jobId}`.
- `GET /ai/matching/weights` (read-only for normal users; POST is admin-only).

## Opportunities
- `GET /opportunities?search=&priority=&workMode=` → OpportunityDto[]: `jobId, title, company, location, workMode,
  sourceUrl, connectorId, matchScore, matchAvailable, notAssessedFactors, priorityScore, priorityLevel
  (HIGH_PRIORITY|MEDIUM_PRIORITY|LOW_PRIORITY|NOT_RECOMMENDED|UNSCORED), historicalConfidence, applicationStatus,
  submissionMode, recommendedAction, reasons[]`. When `matchAvailable=false` show "Unscored", not 0%.
- `POST /opportunities/{jobId}/applications` → creates an application.

## Applications
- `GET /applications`, `GET /applications/{id}`, `GET /applications/statistics`, `POST /applications/create {jobId}`.
- Actions: `POST /applications/{id}/approve|reject|submit|retry`, `PUT /applications/{id}/status {status, reason}` —
  invalid transitions return **409** with a message; show it.
- `POST /applications/{id}/verify {evidenceType, evidenceReference?, confirmationId?}` → recorded as USER_ATTESTED
  ("I applied on the company site"). From a pre-submission state the application moves to SUBMITTED
  (never SUBMITTED_VERIFIED) and `submittedAt` is set; later states are left unchanged.
- `GET /applications/{id}/timeline` → entries `{source: STATE_HISTORY|COMMUNICATION, eventType, outcome, fromState,
  toState, timestamp, actorType: USER|SYSTEM|COMMUNICATION, communicationId, classification,
  classificationConfidence, evidence, reason, stateChanged}`.
- `GET /applications/{id}/decision`, `POST /applications/{id}/decision/evaluate`, `GET /applications/{id}/package`,
  `GET /applications/{id}/preflight`.
- `POST /applications/{id}/preparation` → AI `{checklist[], talkingPoints[], tailoringTips[], questionsToAsk[], risks[], verification}`.
- Workflow states: DISCOVERED, MATCHED, ELIGIBLE, APPLICATION_PREPARING, APPLICATION_READY, READY_FOR_APPROVAL, APPROVED,
  SUBMISSION_IN_PROGRESS, SUBMITTED, SUBMITTED_VERIFIED, VERIFICATION_PENDING, SUBMISSION_UNVERIFIED,
  MANUAL_ACTION_REQUIRED, APPLICATION_BLOCKED_BY_DAILY_LIMIT, SUBMISSION_FAILED, RETRYING, UNDER_REVIEW, ASSESSMENT,
  INTERVIEW, OFFER, REJECTED, WITHDRAWN, COMPLETED, FAILED, ... (MANUAL_ACTION_REQUIRED = apply on the official site).

## HR communications
- `GET /communications`, `GET /communications/{id}`, `POST /communications {provider, sender, subject, body, receivedAt}`
  (manual paste), `POST /communications/{id}/classify` (AI), `GET /communications/{id}/classification`,
  `POST /communications/{id}/process` (deterministic state transition; outcomes STATE_TRANSITIONED, EVENT_RECORDED,
  ALREADY_IN_TARGET_STATE, INVALID_TRANSITION_REJECTED, TERMINAL_STATE_PROTECTED, WITHHELD_LOW_CONFIDENCE,
  UNMATCHED_NO_APPLICATION, NOT_CLASSIFIED, NO_ACTIONABLE_CLASSIFICATION, OUT_OF_ORDER_IGNORED).

## Follow-ups (M22.5–22.7)
- `GET /follow-ups` → `[{key, applicationId, jobTitle, company, ruleCode, reason, urgency (HIGH|MEDIUM|LOW),
  recommendedDate, recommendedChannel (EMAIL|APPLICATION_PORTAL), evidence[], relatedCommunicationId,
  suggestedDraftType, suggestedRecipient, confidence}]`.
- `POST /follow-ups/decisions {key, decision: DISMISSED|SNOOZED|DONE, snoozeDays?}`.
- `POST /follow-ups/drafts {applicationId, draftType, communicationId?, recommendationKey?, userInstructions?,
  interviewCompletedOn? (YYYY-MM-DD, required for INTERVIEW_THANK_YOU)}` → draft
  `{id, subject, body, placeholdersJson, verificationJson, status, recipient, ...}`. Draft types:
  APPLICATION_FOLLOW_UP, INTERVIEW_THANK_YOU, RECRUITER_RESPONSE, ADDITIONAL_INFORMATION_RESPONSE,
  INTERVIEW_RESCHEDULE_RESPONSE, OFFER_RESPONSE.
- `GET /follow-ups/drafts`, `GET /follow-ups/drafts/{id}` → `{draft, allowedRecipients[], events[]}`,
  `PUT /follow-ups/drafts/{id} {subject?, body?, recipient?}` (edits require re-approval),
  `POST /follow-ups/drafts/{id}/discard`, `POST /follow-ups/drafts/{id}/approve` (explicit user approval),
  `POST /follow-ups/drafts/{id}/send {provider?}` (only APPROVED drafts; uses connected mailbox).
- Email: `GET /email/connections` → `[{provider: GMAIL|OUTLOOK, providerConfigured, connected, emailAddress}]`,
  `POST /email/connections/{provider}/authorize` → `{authorizationUrl}` (redirect the browser there),
  `DELETE /email/connections/{provider}`. After OAuth the browser lands on
  `/settings?email=connected|failed|denied&provider=...`.

## Interview coach
- `POST /interview/sessions {jobId? | applicationId? | targetRole + targetCompany? + jobDescription?, questionTypes
  [TECHNICAL|BEHAVIORAL|SYSTEM_DESIGN|CODING], difficulty EASY|MEDIUM|HARD, questionCount 1-10}` → session
  `{sessionId, status, targetRole, targetCompany, difficulty, questions[{questionId, questionOrder, questionText,
  questionType, skillArea, difficulty, rationale, provenance, candidateAnswer, overallScore, technicalAccuracyScore,
  completenessScore, clarityScore, relevanceScore, evaluationFeedback, evaluationJson}]}`. Questions are labelled
  AI-generated practice questions.
- `POST /interview/sessions/{id}/answers {questionId, answer, timeTakenSeconds}` → evaluated question.
- `POST /interview/sessions/{id}/complete` → session with `summaryJson` (AI feedback).
- `GET /interview/sessions`, `GET /interview/sessions/{id}`,
  `GET /interview/readiness` → `{available:false, message}` or `{available:true, overallReadiness (0-1),
  byQuestionType, strongestAreas[], weakestAreas[], answeredQuestions}`.

## Cover letters
- `POST /cover-letters {jobId | applicationId, tone?, notes?, hiringManagerName?}` → `{id, content, verificationJson, ...}`.
- `GET /cover-letters`, `PUT /cover-letters/{id} {content}`, `DELETE /cover-letters/{id}`.

## Learning
- `GET /learning/recommendations` → `{gaps[{skill, source: JOB_DEMAND|INTERVIEW_PRACTICE, evidence, priority}], reason?}`.
- `POST /learning/path {skill, currentLevel?, targetLevel?, weeklyHours?}` → AI LearningPath `{id, skill, priority,
  estimatedHours, whyItMatters, practiceProject, demandEvidence, learningSequence[{sequenceNumber, stepName,
  description, estimatedHours, resourcesJson}]}`. `GET /learning/paths`.
- `GET|POST /learning/progress {skill, progressPercentage, status}`, `GET|POST /learning/goals`, `PUT /learning/goals/{id}`.

## Copilot
- `POST /copilot/chat {message}` → `{answer, citations[], suggestedActions[{action, label, targetId?}], dataGaps[],
  toolsUsed[{name, arguments}], toolSelection: MODEL|FALLBACK, verification}`. Actions: OPEN_JOB, ANALYZE_JOB,
  OPEN_APPLICATION, START_INTERVIEW_PRACTICE, OPTIMIZE_RESUME, DRAFT_FOLLOW_UP, GENERATE_COVER_LETTER,
  VIEW_LEARNING_PLAN, UPLOAD_RESUME — render as buttons that navigate; the Copilot never performs writes.
- `GET /copilot/history`, `POST /copilot/clear-session`, `GET /copilot/tools`.
- `GET /copilot/health-score` → `{available, overallScore|null, measuredComponents, components{resumeQuality,
  profileCompleteness, applicationResponseRate, interviewReadiness, learningProgress: {available, score?, evidence?, reason?}}}`.

## Analytics
- `GET /analytics/career-dashboard` → `{counts{applications, submitted, responses, interviews, offers, rejections},
  rates{responseRate, interviewRate, offerRate: {value|null, numerator, denominator, reason?},
  averageResponseTimeDays{value|null, samples, reason?}}, breakdowns{byState, byCompany, byRole, bySource},
  skills{available, reason, analysedJobs, missingSkills[{skill, jobs}], matchedSkills[...]}, weakInterviewAreas[],
  interviewReadiness, learningProgress[]}`. `value: null` ⇒ render "Not enough data".
- Existing M20 endpoints (`/analytics/role-performance`, `/skill-performance`, `/company-performance`,
  `/source-performance`, `/location-performance`, `/adaptive-insights`, `/dashboard-summary`) return
  `INSUFFICIENT_DATA` markers when there is no history.

## Agents & connectors
- `GET /agents/policy`, `PUT /agents/policy`, `POST /agents/workflow/start`, `GET /agents/workflow/active`,
  `GET /agents/workflow/{id}/status`, pause/resume/cancel, `GET /agents/registry`.
- `GET /connectors`, `GET /discovery/connectors/health` (read-only for users; registering/toggling connectors and
  forcing synchronisation are admin-only).
- `GET /notifications`, `PUT /notifications/{id}/read`, `PUT /notifications/read-all`.
