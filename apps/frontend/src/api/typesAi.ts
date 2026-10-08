import type { AiProcessingStatus, GroundingVerification, ISODate, UUID } from './types';

/* ------------------------------------------------------------- jobs (AI) */
export interface ExtractedValue<T> {
  value: T | null;
  confidence: number | null;
  source: string | null;
}
export interface ExtractedSkill {
  name: string;
  confidence: number | null;
  importance: 'REQUIRED' | 'PREFERRED' | string;
  category: string | null;
}
/** Every field may be missing depending on the model output. */
export interface JobKnowledge {
  jobTitle?: ExtractedValue<string>;
  normalizedJobTitle?: ExtractedValue<string>;
  companyName?: ExtractedValue<string>;
  department?: ExtractedValue<string>;
  declaredSeniority?: ExtractedValue<string>;
  inferredSeniority?: ExtractedValue<string>;
  employmentType?: ExtractedValue<string>;
  workMode?: ExtractedValue<string>;
  locations?: ExtractedValue<string[]>;
  salaryRange?: ExtractedValue<string>;
  experienceRequired?: ExtractedValue<string>;
  educationRequirements?: ExtractedValue<string[]>;
  certifications?: ExtractedValue<string[]>;
  requiredSkills?: ExtractedSkill[];
  preferredSkills?: ExtractedSkill[];
  responsibilities?: ExtractedValue<string[]>;
  qualifications?: ExtractedValue<string[]>;
  benefits?: ExtractedValue<string[]>;
  visaSupport?: ExtractedValue<boolean>;
  relocationSupport?: ExtractedValue<boolean>;
  applicationDeadline?: ExtractedValue<string>;
  salary?:
    | { salarySpecified: false; raw?: string }
    | { salarySpecified: true; minimum: number; maximum: number; currency: string | null; payPeriod: string | null; raw: string };
}
export interface JobMetadata {
  primaryLanguage?: string | null;
  cloud?: string | null;
  experience?: string | null;
  remote?: boolean | null;
  internship?: boolean | null;
  technologyCategories?: Record<string, string[]>;
}
export interface JobQualityMetrics {
  skillDensity?: number | null;
  technologyDiversity?: number | null;
  requirementCompleteness?: number | null;
  jobDetailQuality?: number | null;
  remoteFriendliness?: number | null;
  seniorityComplexity?: number | null;
  method?: string;
}
export interface InsightEvidence {
  insight: string;
  evidence: string;
}
export interface JobInsights {
  engineering?: string[];
  business?: string[];
  hiring?: string[];
  evidence?: { engineering?: InsightEvidence[]; business?: InsightEvidence[]; hiring?: InsightEvidence[] };
}
export interface JobAnalysisView {
  documentId: UUID;
  status: string;
  title: string | null;
  source: string | null;
  knowledge: JobKnowledge | null;
  metadata: JobMetadata;
  qualityMetrics: JobQualityMetrics;
  insights: JobInsights;
  updatedAt: ISODate | null;
}
export interface JobCard {
  jobId: UUID;
  title?: string;
  company?: string;
  location?: string;
  workMode?: string;
  employmentType?: string;
  salary?: string;
  postedDate?: string;
  sourceUrl?: string;
  source?: string;
  connectorId?: string;
  discoveredAt?: ISODate;
  lastSeenAt?: ISODate;
  provenance?: string;
  /** Semantic search only. */
  relevance?: number | null;
  matchedText?: string | null;
}
export interface JobSearchResponse {
  mode: 'recent' | 'semantic';
  results: JobCard[];
}
export type JobDetail = JobCard & { analysis: JobAnalysisView | null };

export interface CompanyResearch {
  summary: string;
  whatTheyDo: string | null;
  techStack: string[];
  cultureSignals: string[];
  talkingPoints: string[];
  questionsToAsk: string[];
  unknowns: string[];
  sources: string[];
  company: string | null;
  jobId: UUID;
  fetchErrors: Array<{ url: string; error: string }>;
}

/* -------------------------------------------------------------- matching */
export interface GapItem {
  type: string;
  name: string | null;
  category: string | null;
  description: string | null;
  severity: 'CRITICAL' | 'RECOMMENDED' | 'OPTIONAL' | string;
  suggestedAction: string | null;
}
export interface RecommendationItem {
  type: string;
  action: string | null;
  category: string | null;
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string;
  description: string | null;
  rationale: string | null;
  estimatedEffort: string | null;
}
export interface MatchResult {
  notAssessedFactors: string[] | null;
  jobAnalyzed: boolean | null;
  jobId: UUID;
  /** 0..100 */
  overallScore: number;
  /** 0..100 per factor; not-assessed factors are absent. */
  individualScores: Record<string, number> | null;
  strengths: string[] | null;
  weaknesses: string[] | null;
  criticalGaps: GapItem[] | null;
  recommendedImprovements: GapItem[] | null;
  optionalImprovements: GapItem[] | null;
  recommendations: RecommendationItem[] | null;
  /** 0..1 */
  confidenceScore: number | null;
  explanation: string | null;
  matchedSkills: string[] | null;
  missingSkills: string[] | null;
}
export interface MatchExplanation {
  match: MatchResult;
  aiExplanation: {
    headline: string;
    explanation: string;
    strengths: string[];
    gaps: string[];
    recommendations: string[];
    verification: GroundingVerification | null;
  };
}

/* ---------------------------------------------------------- resumes (AI) */
export interface ResumeSkill {
  skill: string;
  category: string | null;
  confidence?: number | null;
}
export interface ResumeExperience {
  company: string;
  designation: string | null;
  startDate: string | null;
  endDate: string | null;
  durationMonths: number | null;
  responsibilities: string[] | null;
}
export interface ResumeEducation {
  institution: string;
  degree: string | null;
  specialization: string | null;
  graduationYear: number | null;
  gpa: string | null;
}
export interface ResumeProject {
  name: string;
  description: string | null;
  technologies: string[] | null;
  gitHubUrl: string | null;
  liveUrl: string | null;
}
export interface ResumeCertification {
  certificationName: string;
  issuingOrganization: string | null;
  issueDate: string | null;
  credentialId: string | null;
  credentialUrl: string | null;
}
export interface ExperienceIntelligence {
  totalYearsExperience: number | null;
  datedRoles?: number;
  undatedRoles?: number;
  currentRole?: string | null;
  careerProgression?: string[];
  employmentGaps?: Array<{ from: string; to: string; months: number }>;
  overlappingPeriods?: Array<{ company: string; overlapsUntil: string }>;
}
export interface ResumeStructuredKnowledge {
  personalInformation: {
    name: string | null;
    email: string | null;
    phone: string | null;
    location: string | null;
    linkedin: string | null;
    links: string[] | null;
  } | null;
  summary: string | null;
  skills: ResumeSkill[] | null;
  education: ResumeEducation[] | null;
  experience: ResumeExperience[] | null;
  projects: ResumeProject[] | null;
  certifications: ResumeCertification[] | null;
  achievements?: string[] | null;
  languages?: string[] | null;
  intelligence?: { experienceIntelligence?: ExperienceIntelligence; projectCount?: number; certificationCount?: number } | null;
  grounding?: { policy?: string; dropped?: Record<string, number> } | null;
}
export interface ResumeKnowledgeResponse {
  resumeId: UUID;
  aiProcessingStatus: AiProcessingStatus;
  processingError: string | null;
  structuredKnowledge: ResumeStructuredKnowledge | null;
  documentId?: UUID;
  versionNumber?: number;
}
/** All metrics are 0..1; null means "not measurable". Empty object when not processed. */
export interface ResumeAts {
  atsScore?: number | null;
  completenessScore?: number | null;
  sectionCoverage?: number | null;
  contactQuality?: number | null;
  skillDiversity?: number | null;
  projectStrength?: number | null;
  experienceStrength?: number | null;
  readabilityScore?: number | null;
  keywordCoverage?: number | null;
  formattingQuality?: number | null;
  details?: { method?: string; unavailableMetrics?: string[]; warnings?: string[] } | null;
  aiReview?: {
    overallAssessment?: string;
    strengths?: string[];
    weaknesses?: string[];
    improvements?: string[];
    missingSections?: string[];
  } | null;
}
export interface ResumeOptimization {
  missingSkills: Array<{ skill: string; importance: string; jobEvidence: string }>;
  keywordGaps: string[];
  experienceGaps?: string[];
  bulletImprovements: Array<{
    original: string;
    suggested: string;
    rationale: string;
    introducesUnverifiedClaims: boolean;
    unverifiedValues?: string[];
  }>;
  summarySuggestion: string | null;
  projectRelevance: Array<{ project: string; relevance: string; reason: string }>;
  measurableImpactSuggestions: string[];
  atsImprovements: string[];
  rejectedSuggestions: Array<{ suggested: string; reason: string }>;
  policy?: string;
  resumeId: UUID;
  resumeVersion: number;
  jobId: UUID;
}

/* --------------------------------------------------------------- copilot */
export type CopilotActionType =
  | 'OPEN_JOB' | 'ANALYZE_JOB' | 'OPEN_APPLICATION' | 'START_INTERVIEW_PRACTICE' | 'OPTIMIZE_RESUME'
  | 'DRAFT_FOLLOW_UP' | 'GENERATE_COVER_LETTER' | 'VIEW_LEARNING_PLAN' | 'UPLOAD_RESUME';
export interface SuggestedAction {
  action: CopilotActionType | string;
  label: string;
  targetId: string | null;
}
export interface CopilotChatResponse {
  answer: string;
  citations: string[];
  suggestedActions: SuggestedAction[];
  dataGaps: string[];
  verification: GroundingVerification | null;
  toolsUsed: Array<{ name: string; arguments: Record<string, string> }>;
  toolSelection: 'MODEL' | 'FALLBACK';
}
export interface CopilotHistoryItem {
  id: UUID;
  role: 'USER' | 'ASSISTANT';
  content: string;
  toolsUsed: string[];
  citations: string[];
  suggestedActions: SuggestedAction[];
  createdAt: ISODate;
}
export interface ToolSpec {
  name: string;
  description: string;
  arguments: string[];
}
export type HealthComponent =
  | { available: true; score: number; evidence: string }
  | { available: false; reason: string };
export type HealthComponentKey =
  | 'resumeQuality'
  | 'profileCompleteness'
  | 'applicationResponseRate'
  | 'interviewReadiness'
  | 'learningProgress';
export interface CareerHealth {
  available: boolean;
  /** 0..1 */
  overallScore: number | null;
  measuredComponents: number;
  method?: string;
  components: Partial<Record<HealthComponentKey, HealthComponent>>;
}

/* ------------------------------------------------------------- discovery */
export type ConnectorHealthStatus = 'UNKNOWN' | 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'DISABLED';
export interface ConnectorHealth {
  connectorId: string | null;
  status: ConnectorHealthStatus;
  lastSynchronization: ISODate | null;
  lastSuccess: ISODate | null;
  lastFailure: ISODate | null;
  responseTimeMs: number | null;
  failureCount: number | null;
  message: string | null;
}

/* ---------------------------------------------------------------- agents */
export interface AgentPolicy {
  id?: UUID;
  enabled: boolean;
  maxApplicationsPerDay: number;
  minimumMatchScore: number;
  allowedEmploymentTypes: string | null;
  allowedLocations: string | null;
  allowedRemoteTypes: string | null;
  allowedCompanies: string | null;
  blockedCompanies: string | null;
  requireApproval: boolean;
  allowAutomaticSubmission: boolean;
  allowReferenceResearch: boolean;
  allowExternalConnectors: boolean;
  createdAt?: ISODate;
  updatedAt?: ISODate;
}
export type AgentWorkflowStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'BLOCKED';
export interface AgentWorkflow {
  id: UUID;
  status: AgentWorkflowStatus;
  correlationId: string | null;
  createdAt: ISODate;
  updatedAt: ISODate;
}
export interface AgentTask {
  id: UUID;
  taskType: string;
  status: string;
  agentId: string | null;
  retryCount: number;
  errorMessage: string | null;
  startedAt: ISODate | null;
  completedAt: ISODate | null;
}
export interface WorkflowStatus {
  workflowId: UUID;
  status: AgentWorkflowStatus;
  progress: number;
  completedTasks: number;
  totalTasks: number;
  currentTask: string | null;
  tasks: AgentTask[];
}
export interface AgentRegistryItem {
  agentId: string;
  name: string;
  version: string;
  capabilities: string[];
  supportedTaskTypes: string[];
  health: string;
  enabled: boolean;
}

export interface DiscoveryJob {
  id: UUID;
  connectorId: string;
  source: string | null;
  sourceUrl: string | null;
  title: string | null;
  company: string | null;
  location: string | null;
  employmentType: string | null;
  workMode: string | null;
  salary: string | null;
  postedDate: string | null;
  discoveredAt: ISODate | null;
  lastSeenAt: ISODate | null;
}
