import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  applicationsApi,
  coverLettersApi,
  followUpsApi,
  interviewApi,
  jobsApi,
  notificationsApi,
  profileApi,
  resumesApi,
  emailApi,
} from './endpoints';
import type { DiscoveryJob, Resume } from './types';

/** Central query keys so invalidation stays consistent. */
export const qk = {
  profile: ['profile'] as const,
  resumes: ['resumes'] as const,
  resumeKnowledge: (id: string) => ['resumes', id, 'knowledge'] as const,
  resumeAts: (id: string) => ['resumes', id, 'ats'] as const,
  jobsIndex: ['jobs', 'index'] as const,
  jobSearch: (q: string) => ['jobs', 'search', q] as const,
  job: (id: string) => ['jobs', 'detail', id] as const,
  connectorsHealth: ['connectors', 'health'] as const,
  opportunities: (f: Record<string, string>) => ['opportunities', f] as const,
  applications: ['applications'] as const,
  application: (id: string) => ['applications', id] as const,
  applicationTimeline: (id: string) => ['applications', id, 'timeline'] as const,
  applicationDecision: (id: string) => ['applications', id, 'decision'] as const,
  applicationPackage: (id: string) => ['applications', id, 'package'] as const,
  applicationPreflight: (id: string) => ['applications', id, 'preflight'] as const,
  applicationStats: ['applications', 'statistics'] as const,
  notifications: ['notifications'] as const,
  communications: ['communications'] as const,
  followUps: ['follow-ups'] as const,
  drafts: ['follow-ups', 'drafts'] as const,
  draft: (id: string) => ['follow-ups', 'drafts', id] as const,
  emailConnections: ['email', 'connections'] as const,
  interviewSessions: ['interview', 'sessions'] as const,
  interviewSession: (id: string) => ['interview', 'sessions', id] as const,
  readiness: ['interview', 'readiness'] as const,
  coverLetters: ['cover-letters'] as const,
  learningRecs: ['learning', 'recommendations'] as const,
  learningPaths: ['learning', 'paths'] as const,
  learningProgress: ['learning', 'progress'] as const,
  learningGoals: ['learning', 'goals'] as const,
  copilotHistory: ['copilot', 'history'] as const,
  copilotTools: ['copilot', 'tools'] as const,
  health: ['copilot', 'health'] as const,
  careerDashboard: ['analytics', 'career-dashboard'] as const,
  agentPolicy: ['agents', 'policy'] as const,
  agentActive: ['agents', 'active'] as const,
  agentStatus: (id: string) => ['agents', 'status', id] as const,
  agentRegistry: ['agents', 'registry'] as const,
};

export const isProcessing = (r: Resume) => r.aiProcessingStatus === 'PENDING' || r.aiProcessingStatus === 'PROCESSING';

/** Resume list; polls every 4 s only while a resume is still PENDING/PROCESSING. */
export function useResumes() {
  return useQuery({
    queryKey: qk.resumes,
    queryFn: resumesApi.list,
    refetchInterval: (q) => (q.state.data?.some(isProcessing) ? 4000 : false),
  });
}

export function useProfile() {
  return useQuery({ queryKey: qk.profile, queryFn: profileApi.get });
}

export function useApplications() {
  return useQuery({ queryKey: qk.applications, queryFn: applicationsApi.list });
}

/** All discovered jobs fetched once and indexed by id (used to label applications etc.). */
export function useJobIndex() {
  const q = useQuery({ queryKey: qk.jobsIndex, queryFn: jobsApi.all, staleTime: 5 * 60_000 });
  const index = useMemo(() => {
    const m = new Map<string, DiscoveryJob>();
    for (const j of q.data ?? []) m.set(j.id, j);
    return m;
  }, [q.data]);
  return { ...q, index };
}

export function useNotifications() {
  return useQuery({ queryKey: qk.notifications, queryFn: notificationsApi.list, refetchInterval: 60_000 });
}

export function useFollowUps() {
  return useQuery({ queryKey: qk.followUps, queryFn: followUpsApi.list });
}

export function useDrafts() {
  return useQuery({ queryKey: qk.drafts, queryFn: followUpsApi.drafts });
}

export function useEmailConnections() {
  return useQuery({ queryKey: qk.emailConnections, queryFn: emailApi.connections });
}

export function useInterviewSessions() {
  return useQuery({ queryKey: qk.interviewSessions, queryFn: interviewApi.sessions });
}

export function useCoverLetters() {
  return useQuery({ queryKey: qk.coverLetters, queryFn: coverLettersApi.list });
}
