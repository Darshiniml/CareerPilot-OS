/* Typed API functions grouped by domain. Every route here exists in the Spring controllers; no
 * endpoint is invented. AI-triggering calls pass `aiRequest` (long timeout) and must never be retried
 * automatically. */
import { api, aiRequest } from './client';
import type * as T from './types';

const data = <R>(p: Promise<{ data: R }>) => p.then((r) => r.data);

/* ---------------------------------------------------------------- auth */
export const authApi = {
  login: (email: string, password: string) => data<T.LoginResponse>(api.post('/auth/login', { email, password })),
  register: (body: T.RegisterRequest) => data<T.User>(api.post('/auth/register', body)),
  logout: () => api.post('/auth/logout').then(() => undefined),
};

/* ------------------------------------------------------------- profile */
export const profileApi = {
  get: () => data<T.Profile>(api.get('/profile')),
  updatePersonal: (b: T.PersonalInfo) => api.put('/profile/personal-info', b).then(() => undefined),
  updatePreferences: (b: T.Preferences) => api.put('/profile/preferences', b).then(() => undefined),
  updateSocial: (b: T.SocialLinks) => api.put('/profile/social-links', b).then(() => undefined),
  addItem: (section: T.ProfileSection, body: unknown) => api.post(`/profile/${section}`, body).then(() => undefined),
  deleteItem: (section: T.ProfileSection, id: string) => api.delete(`/profile/${section}/${id}`).then(() => undefined),
};

/* ------------------------------------------------------------- resumes */
export const resumesApi = {
  list: () => data<T.Resume[]>(api.get('/resumes')),
  upload: (file: File, title: string, onProgress?: (pct: number) => void) => {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('title', title);
    return data<T.Resume>(
      api.post('/resumes/upload', fd, {
        timeout: 5 * 60_000,
        onUploadProgress: (ev) => {
          if (onProgress && ev.total) onProgress(Math.round((ev.loaded / ev.total) * 100));
        },
      }),
    );
  },
  setDefault: (id: string) => api.put(`/resumes/${id}/default`).then(() => undefined),
  remove: (id: string) => api.delete(`/resumes/${id}`).then(() => undefined),
  download: (id: string) => data<Blob>(api.get(`/resumes/${id}/download`, { responseType: 'blob', timeout: 2 * 60_000 })),
  reprocess: (resumeId: string) => data<{ resumeId: string; aiProcessingStatus: string }>(api.post('/ai/resume/process', { resumeId })),
  knowledge: (id: string) => data<T.ResumeKnowledgeResponse>(api.get(`/ai/resume/${id}`)),
  ats: (id: string) => data<T.ResumeAts>(api.get(`/ai/resume/${id}/ats`)),
  optimize: (id: string, jobId: string) =>
    data<T.ResumeOptimization>(api.post(`/ai/resume/${id}/optimize`, { jobId }, aiRequest)),
};

/* ---------------------------------------------------------------- jobs */
export const jobsApi = {
  search: (query: string, limit = 30) =>
    data<T.JobSearchResponse>(api.post('/ai/job/search', query.trim() ? { query: query.trim(), limit } : { limit }, aiRequest)),
  get: (jobId: string) => data<T.JobDetail>(api.get(`/ai/job/${jobId}`)),
  analyze: (jobId: string) => data<T.JobAnalysisView>(api.post(`/ai/job/${jobId}/analyze`, undefined, aiRequest)),
  processText: (content: string, title?: string) =>
    data<T.JobAnalysisView>(api.post('/ai/job/process-text', title ? { content, title } : { content }, aiRequest)),
  processUrl: (url: string) => data<T.JobAnalysisView>(api.post(`/ai/job/process-url?url=${encodeURIComponent(url)}`, undefined, aiRequest)),
  research: (jobId: string, urls: string[]) =>
    data<T.CompanyResearch>(api.post('/ai/company/research', urls.length ? { jobId, urls } : { jobId }, aiRequest)),
  /** All discovered jobs in one request (used as a lookup table; avoids one request per job). */
  all: () => data<T.DiscoveryJob[]>(api.get('/discovery/jobs')),
  connectorsHealth: () => data<T.ConnectorHealth[]>(api.get('/discovery/connectors/health')),
};

/* ------------------------------------------------------------ matching */
export const matchingApi = {
  match: (jobId: string, analyzeJob = false) =>
    data<T.MatchResult>(api.post('/ai/matching/match', { jobId, analyzeJob }, aiRequest)),
  explain: (jobId: string) => data<T.MatchExplanation>(api.post('/ai/matching/match/explain', { jobId }, aiRequest)),
};

/* -------------------------------------------------------- opportunities */
export const opportunitiesApi = {
  list: (params: { search?: string; priority?: string; workMode?: string; size?: number }) =>
    data<T.SpringPage<T.Opportunity>>(
      api.get('/opportunities', {
        params: {
          page: 0,
          size: params.size ?? 50,
          search: params.search || undefined,
          priority: params.priority || undefined,
          workMode: params.workMode || undefined,
        },
      }),
    ),
  createApplication: (jobId: string) => data<T.ApplicationRecord>(api.post(`/opportunities/${jobId}/applications`)),
};

/* --------------------------------------------------------- applications */
export type ApplicationAction = 'approve' | 'reject' | 'submit' | 'retry';
export const applicationsApi = {
  list: () => data<T.ApplicationRecord[]>(api.get('/applications')),
  get: (id: string) => data<T.ApplicationRecord>(api.get(`/applications/${id}`)),
  statistics: () => data<T.ApplicationStatistics>(api.get('/applications/statistics')),
  create: (jobId: string) => data<T.ApplicationRecord>(api.post('/applications/create', { jobId })),
  action: (id: string, action: ApplicationAction, reason?: string) =>
    data<T.ApplicationRecord>(api.post(`/applications/${id}/${action}`, reason ? { reason } : {})),
  updateStatus: (id: string, targetState: T.WorkflowState, reason?: string) =>
    data<T.ApplicationRecord>(api.put(`/applications/${id}/status`, { targetState, reason: reason || null })),
  verify: (id: string, body: T.VerifyRequest) => data<T.ApplicationVerificationResult>(api.post(`/applications/${id}/verify`, body)),
  timeline: (id: string) => data<T.TimelineEntry[]>(api.get(`/applications/${id}/timeline`)),
  decision: (id: string) => data<T.ApplicationDecision>(api.get(`/applications/${id}/decision`)),
  evaluateDecision: (id: string) => data<T.ApplicationDecision>(api.post(`/applications/${id}/decision/evaluate`, undefined, aiRequest)),
  package: (id: string) => data<T.ApplicationPackage>(api.get(`/applications/${id}/package`)),
  preflight: (id: string) => data<T.PreflightResult>(api.get(`/applications/${id}/preflight`)),
  preparation: (id: string) => data<T.ApplicationPreparation>(api.post(`/applications/${id}/preparation`, undefined, aiRequest)),
};

/* ------------------------------------------------------- notifications */
export const notificationsApi = {
  list: () => data<T.PlatformNotification[]>(api.get('/notifications')),
  markRead: (id: string) => data<T.PlatformNotification>(api.put(`/notifications/${id}/read`)),
  markAllRead: () => data<{ success: boolean; updated: number }>(api.put('/notifications/read-all')),
};

/* ------------------------------------------------------ communications */
export const communicationsApi = {
  list: () => data<T.Communication[]>(api.get('/communications')),
  create: (b: T.IngestCommunicationRequest) => data<T.Communication>(api.post('/communications', b)),
  classify: (id: string) => data<T.ClassificationResponse>(api.post(`/communications/${id}/classify`, undefined, aiRequest)),
  process: (id: string) => data<T.CommunicationProcessingResponse>(api.post(`/communications/${id}/process`)),
};

/* ----------------------------------------------------------- follow-ups */
export const followUpsApi = {
  list: () => data<T.FollowUpRecommendation[]>(api.get('/follow-ups')),
  decide: (key: string, decision: 'DISMISSED' | 'SNOOZED' | 'DONE', snoozeDays?: number) =>
    api.post('/follow-ups/decisions', { key, decision, snoozeDays: snoozeDays ?? null }).then(() => undefined),
  createDraft: (b: T.CreateDraftRequest) => data<T.FollowUpDraft>(api.post('/follow-ups/drafts', b, aiRequest)),
  drafts: () => data<T.FollowUpDraft[]>(api.get('/follow-ups/drafts')),
  draft: (id: string) => data<T.FollowUpDraftDetail>(api.get(`/follow-ups/drafts/${id}`)),
  updateDraft: (id: string, b: { subject?: string; body?: string; recipient?: string | null }) =>
    data<T.FollowUpDraft>(api.put(`/follow-ups/drafts/${id}`, b)),
  discard: (id: string) => data<T.FollowUpDraft>(api.post(`/follow-ups/drafts/${id}/discard`)),
  approve: (id: string) => data<T.FollowUpDraft>(api.post(`/follow-ups/drafts/${id}/approve`)),
  send: (id: string, provider?: T.EmailProvider) =>
    data<T.FollowUpDraft>(api.post(`/follow-ups/drafts/${id}/send`, provider ? { provider } : {}, { timeout: 2 * 60_000 })),
};

/* ---------------------------------------------------------------- email */
export const emailApi = {
  connections: () => data<T.EmailConnection[]>(api.get('/email/connections')),
  authorize: (provider: T.EmailProvider) =>
    data<{ authorizationUrl: string }>(api.post(`/email/connections/${provider}/authorize`)),
  disconnect: (provider: T.EmailProvider) => api.delete(`/email/connections/${provider}`).then(() => undefined),
};

/* ------------------------------------------------------------ interview */
export const interviewApi = {
  start: (b: T.StartInterviewRequest) => data<T.InterviewSession>(api.post('/interview/sessions', b, aiRequest)),
  answer: (sessionId: string, questionId: string, answer: string, timeTakenSeconds: number) =>
    data<T.InterviewQuestion>(api.post(`/interview/sessions/${sessionId}/answers`, { questionId, answer, timeTakenSeconds }, aiRequest)),
  complete: (sessionId: string) => data<T.InterviewSession>(api.post(`/interview/sessions/${sessionId}/complete`, undefined, aiRequest)),
  sessions: () => data<T.InterviewSession[]>(api.get('/interview/sessions')),
  session: (id: string) => data<T.InterviewSession>(api.get(`/interview/sessions/${id}`)),
  readiness: () => data<T.InterviewReadiness>(api.get('/interview/readiness')),
};

/* --------------------------------------------------------- cover letters */
export const coverLettersApi = {
  list: () => data<T.CoverLetter[]>(api.get('/cover-letters')),
  generate: (b: T.CoverLetterRequest) => data<T.CoverLetter>(api.post('/cover-letters', b, aiRequest)),
  update: (id: string, content: string) => data<T.CoverLetter>(api.put(`/cover-letters/${id}`, { content })),
  remove: (id: string) => api.delete(`/cover-letters/${id}`).then(() => undefined),
};

/* -------------------------------------------------------------- learning */
export const learningApi = {
  recommendations: () => data<T.LearningRecommendations>(api.get('/learning/recommendations')),
  createPath: (b: { skill: string; currentLevel?: string; targetLevel?: string; weeklyHours?: number }) =>
    data<T.LearningPath>(api.post('/learning/path', b, aiRequest)),
  paths: () => data<T.LearningPath[]>(api.get('/learning/paths')),
  progress: () => data<T.LearningProgress[]>(api.get('/learning/progress')),
  saveProgress: (b: { skill: string; progressPercentage: number; status: string }) =>
    data<T.LearningProgress>(api.post('/learning/progress', b)),
  goals: () => data<T.GoalWithProgress[]>(api.get('/learning/goals')),
  createGoal: (b: T.GoalRequest) => data<T.CareerGoal>(api.post('/learning/goals', b)),
  updateGoal: (id: string, b: { learningPlanProgress: number; overallProgress: number; isCompleted: boolean }) =>
    data<T.CareerGoalProgress>(api.put(`/learning/goals/${id}`, b)),
};

/* --------------------------------------------------------------- copilot */
export const copilotApi = {
  chat: (message: string) => data<T.CopilotChatResponse>(api.post('/copilot/chat', { message }, aiRequest)),
  history: () => data<T.CopilotHistoryItem[]>(api.get('/copilot/history', { params: { limit: 100 } })),
  clear: () => data<{ deletedMessages: number }>(api.post('/copilot/clear-session')),
  tools: () => data<T.ToolSpec[]>(api.get('/copilot/tools')),
  health: () => data<T.CareerHealth>(api.get('/copilot/health-score')),
};

/* ------------------------------------------------------------- analytics */
export const analyticsApi = {
  careerDashboard: () => data<T.CareerDashboard>(api.get('/analytics/career-dashboard')),
};

/* ---------------------------------------------------------------- agents */
export const agentsApi = {
  policy: () => data<T.AgentPolicy>(api.get('/agents/policy')),
  savePolicy: (p: T.AgentPolicy) => data<T.AgentPolicy>(api.put('/agents/policy', p)),
  start: () => data<T.AgentWorkflow>(api.post('/agents/workflow/start')),
  active: () => data<T.AgentWorkflow[]>(api.get('/agents/workflow/active')),
  status: (id: string) => data<T.WorkflowStatus>(api.get(`/agents/workflow/${id}/status`)),
  control: (id: string, op: 'pause' | 'resume' | 'cancel') => api.post(`/agents/workflow/${id}/${op}`).then(() => undefined),
  registry: () => data<T.AgentRegistryItem[]>(api.get('/agents/registry')),
};
