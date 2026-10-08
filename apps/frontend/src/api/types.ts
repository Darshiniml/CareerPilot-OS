/* Types mirror the Spring DTOs/entities (see docs/API_CONTRACT.md). Fields documented as nullable are
 * typed `| null`; fields that may be absent are optional. JSON-string columns are typed as string and
 * parsed with `parseJson` at the point of use. */

export type UUID = string;
export type ISODate = string;
export type LocalDate = string;

/* ----------------------------------------------------------------- auth */
export interface User {
  id: UUID;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: string[];
  createdAt?: ISODate | null;
}
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds?: number;
  user: User;
}
export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/* -------------------------------------------------------------- profile */
export interface PersonalInfo {
  firstName: string;
  lastName: string;
  email: string;
}
export type WorkStyle = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'FLEXIBLE';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERN' | 'FREELANCE' | 'TEMPORARY';
export type SalaryPeriod = 'YEARLY' | 'MONTHLY' | 'HOURLY';
export interface Preferences {
  workStyle: WorkStyle | null;
  salaryMin: number | null;
  salaryMax: number | null;
  currencyCode: string | null;
  salaryPeriod: SalaryPeriod | null;
  employmentType: EmploymentType | null;
  jobAlertSettings: boolean;
  preferredRoles: string[] | null;
  preferredLocations: string[] | null;
  preferredCompanies: string[] | null;
}
export interface SocialLinks {
  id?: UUID | null;
  linkedin: string | null;
  github: string | null;
  portfolio: string | null;
  twitter: string | null;
}
export interface Experience {
  id?: UUID | null;
  companyName: string;
  title: string;
  location: string | null;
  startDate: LocalDate | null;
  endDate: LocalDate | null;
  currentJob: boolean;
  description: string | null;
}
export interface Education {
  id?: UUID | null;
  institution: string;
  degree: string | null;
  fieldOfStudy: string | null;
  startDate: LocalDate | null;
  endDate: LocalDate | null;
  description: string | null;
}
export interface Project {
  id?: UUID | null;
  name: string;
  description: string | null;
  url: string | null;
  role: string | null;
}
export interface Certification {
  id?: UUID | null;
  name: string;
  issuingOrganization: string | null;
  issueDate: LocalDate | null;
  expirationDate: LocalDate | null;
  credentialId: string | null;
  credentialUrl: string | null;
}
export interface Profile {
  userId: UUID;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  socialLinks: SocialLinks | null;
  skills: string[] | null;
  education: Education[] | null;
  experience: Experience[] | null;
  projects: Project[] | null;
  certifications: Certification[] | null;
  preferences: Preferences | null;
}
export type ProfileSection = 'experience' | 'education' | 'projects' | 'certifications';

/* -------------------------------------------------------------- resumes */
export type AiProcessingStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';
export interface ResumeVersion {
  id: UUID;
  versionNumber: number;
  fileUrl?: string | null;
  changeReason: string | null;
  generatedByAi: boolean;
  createdAt: ISODate;
}
export interface Resume {
  id: UUID;
  title: string;
  originalFilename: string | null;
  mimeType: string | null;
  fileSize: number;
  parsingStatus: string | null;
  aiProcessingStatus: AiProcessingStatus | null;
  processingError: string | null;
  isDefault: boolean;
  isArchived: boolean;
  uploadedAt: ISODate;
  versions: ResumeVersion[] | null;
}

/* ---------------------------------------------------------- workflow */
export type WorkflowState =
  | 'DISCOVERED' | 'MATCHED' | 'ELIGIBLE' | 'READY' | 'APPLICATION_PREPARING' | 'APPLICATION_READY'
  | 'WAITING_APPROVAL' | 'READY_FOR_APPROVAL' | 'APPROVED' | 'SUBMITTING' | 'SUBMISSION_IN_PROGRESS'
  | 'SUBMITTED' | 'SUBMITTED_VERIFIED' | 'VERIFICATION_PENDING' | 'MANUAL_ACTION_REQUIRED'
  | 'UNSUPPORTED_CONNECTOR' | 'ALREADY_APPLIED' | 'APPLICATION_BLOCKED_BY_DAILY_LIMIT' | 'FAILED'
  | 'APPLICATION_FAILED' | 'SUBMISSION_FAILED' | 'SUBMISSION_UNVERIFIED' | 'UNDER_REVIEW' | 'ASSESSMENT'
  | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'REJECTED_BY_COMPANY' | 'WITHDRAWN' | 'RETRYING' | 'TRACKING'
  | 'COMPLETED' | 'ARCHIVED';

export interface ApplicationRecord {
  applicationId: UUID;
  candidateId: UUID;
  companyId: UUID | null;
  jobId: UUID;
  connectorId: string | null;
  workflowState: WorkflowState;
  selectedResumeId: UUID | null;
  selectedResumeVersion: number | null;
  submissionMethod: string | null;
  matchScore: number | null;
  createdAt: ISODate;
  updatedAt: ISODate;
  submittedAt: ISODate | null;
  lastVerifiedAt: ISODate | null;
  retryCount: number;
  failureReason: string | null;
  externalApplicationId: string | null;
  metadata: Record<string, unknown> | null;
  version: number;
}
export interface ApplicationStatistics {
  applicationsSubmitted: number;
  approvalRate: number | null;
  submissionSuccessRate: number | null;
  failureRate: number | null;
  interviewRate: number | null;
  offerRate: number | null;
  averageMatchScore: number | null;
  averageTimeToSubmit: number | null;
}
export interface VerifyRequest {
  evidenceType: string;
  evidenceReference?: string | null;
  confirmationId?: string | null;
}
export interface ApplicationVerificationResult {
  id: UUID;
  applicationId: UUID;
  verificationStatus: string;
  evidenceType: string;
  evidenceReference: string | null;
  confirmationId: string | null;
  reason: string | null;
  verifiedAt: ISODate;
}
export type CommunicationClassification =
  | 'APPLICATION_RECEIVED' | 'APPLICATION_UNDER_REVIEW' | 'ASSESSMENT_REQUEST' | 'INTERVIEW_INVITATION'
  | 'INTERVIEW_RESCHEDULED' | 'ADDITIONAL_INFORMATION_REQUESTED' | 'REJECTION' | 'OFFER' | 'UNKNOWN';
export type TimelineEventOutcome =
  | 'STATE_TRANSITIONED' | 'EVENT_RECORDED' | 'ALREADY_IN_TARGET_STATE' | 'INVALID_TRANSITION_REJECTED'
  | 'TERMINAL_STATE_PROTECTED' | 'WITHHELD_LOW_CONFIDENCE' | 'UNMATCHED_NO_APPLICATION' | 'NOT_CLASSIFIED'
  | 'NO_ACTIONABLE_CLASSIFICATION' | 'OUT_OF_ORDER_IGNORED';
export interface TimelineEntry {
  source: 'STATE_HISTORY' | 'COMMUNICATION';
  eventType: string;
  outcome: TimelineEventOutcome | null;
  fromState: WorkflowState | 'INITIAL' | null;
  toState: WorkflowState | null;
  timestamp: ISODate;
  actorId: UUID | null;
  actorType: 'USER' | 'SYSTEM' | 'COMMUNICATION' | null;
  reason: string | null;
  durationSeconds: number | null;
  stateChanged: boolean;
  communicationId: UUID | null;
  timelineEventId: UUID | null;
  classification: CommunicationClassification | null;
  classificationConfidence: number | null;
  evidence: string | null;
}
export interface PreflightCheck {
  name: string;
  passed: boolean;
  details: string;
}
export interface PreflightResult {
  allowed: boolean;
  suggestedState: string;
  checks: PreflightCheck[];
  timestamp: ISODate;
}
export interface ApplicationDecision {
  id: UUID;
  applicationId: UUID;
  jobId: UUID;
  recommendation: 'INSUFFICIENT_DATA' | 'RECOMMENDED_TO_APPLY' | 'APPLY_WITH_CAUTION' | 'NOT_RECOMMENDED';
  decisionRationale: string | null;
  strengthsJson: string | null;
  criticalGapsJson: string | null;
  recommendedResumeId: UUID | null;
  recommendedResumeTitle: string | null;
  preflightResultJson: string | null;
  submissionCapabilityJson: string | null;
  evaluatedAt: ISODate;
}
export interface SubmissionCapability {
  source: string;
  submissionMode: string;
  enabled: boolean;
  supportsSubmission: boolean;
  supportsVerification: boolean;
  supportsStatusTracking: boolean;
  reason: string | null;
}
export interface ApplicationPackage {
  id: UUID;
  applicationId: UUID;
  jobId: UUID;
  candidateProfileJson: string | null;
  selectedResumeJson: string | null;
  jobDetailsJson: string | null;
  matchResultJson: string | null;
  companyIntelligenceJson: string | null;
  submissionCapabilityJson: string | null;
  preflightResultJson: string | null;
  officialApplyUrl: string | null;
  generatedAt: ISODate;
}
export interface GroundingVerification {
  status: 'GROUNDED' | 'NEEDS_REVIEW' | string;
  unsupportedNumbers?: string[];
  unsupportedTechnologies?: string[];
  policy?: string;
}
export interface ApplicationPreparation {
  checklist: string[];
  talkingPoints: string[];
  tailoringTips: string[];
  questionsToAsk: string[];
  risks: string[];
  verification: GroundingVerification | null;
}

export interface PlatformNotification {
  id: UUID;
  applicationId: UUID | null;
  type: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: ISODate;
}

/* -------------------------------------------------------- opportunities */
export type PriorityLevel = 'UNSCORED' | 'HIGH_PRIORITY' | 'MEDIUM_PRIORITY' | 'LOW_PRIORITY' | 'NOT_RECOMMENDED';
export interface Opportunity {
  jobId: UUID;
  title: string | null;
  company: string | null;
  location: string | null;
  workMode: string | null;
  source: string | null;
  sourceUrl: string | null;
  connectorId: string | null;
  matchScore: number;
  matchAvailable: boolean | null;
  notAssessedFactors: string[] | null;
  historicalSuccessScore: number;
  historicalConfidence: string | null;
  priorityScore: number;
  priorityLevel: PriorityLevel;
  applicationStatus: string | null;
  submissionMode: string | null;
  recommendedAction: string | null;
  reasons: string[] | null;
}

/* -------------------------------------------------------- communications */
export type CommunicationProvider = 'N8N' | 'GMAIL' | 'OUTLOOK' | 'IMAP' | 'MANUAL' | 'OTHER';
export type CommunicationProcessingStatus = 'RECEIVED' | 'PROCESSING' | 'PROCESSED' | 'UNMATCHED' | 'FAILED';
export interface Communication {
  id: UUID;
  provider: CommunicationProvider;
  externalMessageId: string;
  threadId: string | null;
  sender: string;
  recipient: string | null;
  subject: string | null;
  body: string | null;
  receivedAt: ISODate;
  matchedApplicationId: UUID | null;
  matchConfidence: number | null;
  matchEvidence: string | null;
  classification: CommunicationClassification;
  classificationConfidence: number | null;
  classificationReason: string | null;
  processingStatus: CommunicationProcessingStatus;
  createdAt: ISODate;
  updatedAt: ISODate;
}
export interface IngestCommunicationRequest {
  provider: CommunicationProvider;
  externalMessageId: string;
  sender: string;
  recipient?: string | null;
  subject?: string | null;
  body?: string | null;
  receivedAt: ISODate;
}
export interface ClassificationResponse {
  communicationId: UUID;
  classification: CommunicationClassification;
  confidence: number | null;
  evidence: string | null;
  processingStatus: CommunicationProcessingStatus;
  classifiedAt: ISODate;
}
export interface CommunicationProcessingResponse {
  communicationId: UUID;
  applicationId: UUID | null;
  eventType: string | null;
  outcome: TimelineEventOutcome;
  stateChanged: boolean;
  previousState: WorkflowState | null;
  newState: WorkflowState | null;
  applicationState: WorkflowState | null;
  timelineEventId: UUID | null;
}

/* -------------------------------------------------------------- writing */
export interface CoverLetter {
  id: UUID;
  jobId: UUID;
  applicationId: UUID | null;
  resumeId: UUID | null;
  resumeVersion: number | null;
  tone: string | null;
  content: string;
  verificationJson: string | null;
  editedByUser: boolean;
  createdAt: ISODate;
  updatedAt: ISODate;
}
export interface CoverLetterRequest {
  jobId?: UUID | null;
  applicationId?: UUID | null;
  tone?: string | null;
  notes?: string | null;
  hiringManagerName?: string | null;
}

/* ------------------------------------------------------------- follow-ups */
export type Urgency = 'HIGH' | 'MEDIUM' | 'LOW';
export type DraftType =
  | 'APPLICATION_FOLLOW_UP' | 'INTERVIEW_THANK_YOU' | 'RECRUITER_RESPONSE' | 'ADDITIONAL_INFORMATION_RESPONSE'
  | 'INTERVIEW_RESCHEDULE_RESPONSE' | 'OFFER_RESPONSE';
export const DRAFT_TYPES: DraftType[] = [
  'APPLICATION_FOLLOW_UP',
  'INTERVIEW_THANK_YOU',
  'RECRUITER_RESPONSE',
  'ADDITIONAL_INFORMATION_RESPONSE',
  'INTERVIEW_RESCHEDULE_RESPONSE',
  'OFFER_RESPONSE',
];
export type DraftStatus = 'DRAFT' | 'APPROVED' | 'SENDING' | 'SENT' | 'FAILED' | 'DISCARDED';
export type EmailProvider = 'GMAIL' | 'OUTLOOK';
export interface FollowUpRecommendation {
  key: string;
  applicationId: UUID;
  jobTitle: string | null;
  company: string | null;
  ruleCode: string;
  reason: string;
  urgency: Urgency;
  recommendedDate: LocalDate | null;
  recommendedChannel: 'EMAIL' | 'APPLICATION_PORTAL';
  evidence: string[] | null;
  relatedCommunicationId: UUID | null;
  suggestedDraftType: DraftType | null;
  suggestedRecipient: string | null;
  confidence: number | null;
}
export interface CreateDraftRequest {
  applicationId: UUID;
  draftType: DraftType;
  communicationId?: UUID | null;
  recommendationKey?: string | null;
  userInstructions?: string | null;
  interviewCompletedOn?: LocalDate | null;
}
export interface FollowUpDraft {
  id: UUID;
  applicationId: UUID;
  communicationId: UUID | null;
  recommendationKey: string | null;
  draftType: DraftType;
  subject: string;
  body: string;
  placeholdersJson: string | null;
  verificationJson: string | null;
  editedByUser: boolean;
  status: DraftStatus;
  recipient: string | null;
  approvedAt: ISODate | null;
  sentAt: ISODate | null;
  provider: EmailProvider | null;
  providerMessageId: string | null;
  sendError: string | null;
  createdAt: ISODate;
  updatedAt: ISODate;
}
export interface EmailSendEvent {
  id: UUID;
  draftId: UUID;
  eventType: 'APPROVED' | 'SEND_REQUESTED' | 'SENT' | 'FAILED' | 'DISCARDED' | string;
  provider: EmailProvider | null;
  recipient: string | null;
  detail: string | null;
  correlationId: string | null;
  createdAt: ISODate;
}
export interface FollowUpDraftDetail {
  draft: FollowUpDraft;
  allowedRecipients: string[];
  events: EmailSendEvent[];
}
export interface EmailConnection {
  provider: EmailProvider;
  providerConfigured: boolean;
  connected: boolean;
  emailAddress: string | null;
  connectedAt?: ISODate | null;
}

/* ------------------------------------------------------------- interview */
export type QuestionType = 'TECHNICAL' | 'BEHAVIORAL' | 'SYSTEM_DESIGN' | 'CODING' | 'COMPANY';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export interface StartInterviewRequest {
  jobId?: UUID | null;
  applicationId?: UUID | null;
  targetRole?: string | null;
  targetCompany?: string | null;
  jobDescription?: string | null;
  questionTypes: QuestionType[];
  difficulty: Difficulty;
  questionCount: number;
}
export interface InterviewQuestion {
  questionId: UUID;
  questionOrder: number | null;
  questionText: string;
  questionType: QuestionType | null;
  skillArea: string | null;
  difficulty: string | null;
  rationale: string | null;
  provenance: string | null;
  candidateAnswer: string | null;
  timeTakenSeconds: number | null;
  answeredAt: ISODate | null;
  technicalAccuracyScore: number | null;
  completenessScore: number | null;
  clarityScore: number | null;
  relevanceScore: number | null;
  /** 0..1 */
  overallScore: number | null;
  evaluationFeedback: string | null;
  evaluationJson: string | null;
}
export interface InterviewEvaluation {
  scores?: { technicalAccuracy?: number; completeness?: number; clarity?: number; relevance?: number; overall?: number };
  strengths?: string[];
  improvements?: string[];
  missingPoints?: string[];
  modelAnswerOutline?: string[];
  feedback?: string;
  answerWordCount?: number;
  gradingManipulationDetected?: boolean;
}
export interface InterviewSummary {
  summary?: string;
  strongAreas?: string[];
  weakAreas?: string[];
  nextSteps?: string[];
}
export interface InterviewSession {
  sessionId: UUID;
  applicationId: UUID | null;
  jobId: UUID | null;
  interviewType: string | null;
  status: 'ACTIVE' | 'COMPLETED';
  difficulty: Difficulty;
  targetRole: string | null;
  targetCompany: string | null;
  startedAt: ISODate | null;
  completedAt: ISODate | null;
  /** 0..1 mean of answered questions; meaningless when nothing was answered. */
  overallReadiness: number;
  summaryJson: string | null;
  questions: InterviewQuestion[] | null;
}
export interface SkillAreaScore {
  skillArea: string;
  score: number;
}
export type InterviewReadiness =
  | { available: false; answeredQuestions: number; message?: string }
  | {
      available: true;
      answeredQuestions: number;
      overallReadiness: number;
      byQuestionType: Record<string, number>;
      strongestAreas: SkillAreaScore[];
      weakestAreas: SkillAreaScore[];
      method?: string;
    };

/* -------------------------------------------------------------- learning */
export interface LearningGap {
  skill: string;
  source: 'JOB_DEMAND' | 'INTERVIEW_PRACTICE';
  evidence: string;
  priority: 'HIGH' | 'MEDIUM' | string;
  demandJobs?: number;
}
export interface LearningRecommendations {
  gaps: LearningGap[];
  reason?: string;
}
export interface LearningPathItem {
  id: UUID;
  stepName: string;
  sequenceNumber: number;
  description: string | null;
  estimatedHours: number | null;
  resourcesJson: string | null;
}
export interface LearningResource {
  name: string;
  type?: string | null;
}
export interface LearningPath {
  id: UUID;
  skill: string;
  currentLevel: string | null;
  targetLevel: string | null;
  priority: string | null;
  estimatedHours: number | null;
  whyItMatters: string | null;
  practiceProject: string | null;
  demandEvidence: string | null;
  learningSequence: LearningPathItem[] | null;
  createdAt: ISODate;
}
export interface LearningProgress {
  id: UUID;
  skill: string;
  progressPercentage: number;
  status: string;
  updatedAt: ISODate;
}
export interface CareerGoal {
  id: UUID;
  targetRole: string;
  targetIndustry: string | null;
  targetSalary: number | null;
  targetLocation: string | null;
  timelineMonths: number | null;
  createdAt: ISODate;
}
export interface CareerGoalProgress {
  id: UUID;
  goalId: UUID;
  learningPlanProgress: number;
  overallProgress: number;
  completed: boolean;
  updatedAt: ISODate;
}
export interface GoalWithProgress {
  goal: CareerGoal;
  progress: CareerGoalProgress | null;
}
export interface GoalRequest {
  targetRole: string;
  targetIndustry?: string | null;
  targetSalary?: number | null;
  targetLocation?: string | null;
  timelineMonths?: number | null;
}

/* ------------------------------------------------------------- analytics */
export interface RateMetric {
  numerator: number;
  denominator: number;
  value: number | null;
  reason?: string;
}
export interface ResponseTimeMetric {
  samples: number;
  value: number | null;
  reason?: string;
}
export interface SkillCount {
  skill: string;
  jobs: number;
}
export interface CareerDashboard {
  counts: { applications: number; submitted: number; responses: number; interviews: number; offers: number; rejections: number };
  rates: {
    responseRate: RateMetric;
    interviewRate: RateMetric;
    offerRate: RateMetric;
    averageResponseTimeDays: ResponseTimeMetric;
  };
  breakdowns: {
    byState: Record<string, number>;
    byCompany: Record<string, number>;
    byRole: Record<string, number>;
    bySource: Record<string, number>;
  };
  skills: {
    available: boolean;
    reason: string | null;
    analysedJobs: number;
    missingSkills: SkillCount[];
    matchedSkills: SkillCount[];
  };
  weakInterviewAreas: SkillAreaScore[];
  interviewReadiness: InterviewReadiness | null;
  learningProgress: LearningProgress[];
  minimumSample?: number;
  generatedAt?: ISODate;
}

export * from './typesAi';
