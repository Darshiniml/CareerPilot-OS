import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useIsAuthenticated } from '../store/auth';
import { AppShell } from '../components/layout/AppShell';
import { Spinner } from '../components/ui/Feedback';
import { LoginPage, RegisterPage } from '../features/auth/AuthPages';

const page = <K extends string>(loader: () => Promise<Record<K, ComponentType>>, name: K) =>
  lazy(() => loader().then((m) => ({ default: m[name] })));

const DashboardPage = page(() => import('../features/dashboard/DashboardPage'), 'DashboardPage');
const ProfilePage = page(() => import('../features/profile/ProfilePage'), 'ProfilePage');
const ResumesPage = page(() => import('../features/resumes/ResumesPage'), 'ResumesPage');
const JobsPage = page(() => import('../features/jobs/JobsPage'), 'JobsPage');
const OpportunitiesPage = page(() => import('../features/opportunities/OpportunitiesPage'), 'OpportunitiesPage');
const ApplicationsPage = page(() => import('../features/applications/ApplicationsPage'), 'ApplicationsPage');
const InboxPage = page(() => import('../features/inbox/InboxPage'), 'InboxPage');
const FollowUpsPage = page(() => import('../features/followups/FollowUpsPage'), 'FollowUpsPage');
const InterviewsPage = page(() => import('../features/interviews/InterviewsPage'), 'InterviewsPage');
const CoverLettersPage = page(() => import('../features/coverletters/CoverLettersPage'), 'CoverLettersPage');
const LearningPage = page(() => import('../features/learning/LearningPage'), 'LearningPage');
const AnalyticsPage = page(() => import('../features/analytics/AnalyticsPage'), 'AnalyticsPage');
const CopilotPage = page(() => import('../features/copilot/CopilotPage'), 'CopilotPage');
const AgentsPage = page(() => import('../features/agents/AgentsPage'), 'AgentsPage');
const SettingsPage = page(() => import('../features/settings/SettingsPage'), 'SettingsPage');

function RequireAuth({ children }: { children: ReactNode }) {
  const authed = useIsAuthenticated();
  const location = useLocation();
  if (!authed) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

function GuestOnly({ children }: { children: ReactNode }) {
  const authed = useIsAuthenticated();
  return authed ? <Navigate to="/dashboard" replace /> : <>{children}</>;
}

const fallback = (
  <div className="flex min-h-[40vh] items-center justify-center">
    <Spinner label="Loading…" />
  </div>
);

export function AppRoutes() {
  return (
    <BrowserRouter>
      <Suspense fallback={fallback}>
        <Routes>
          <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
          <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
          <Route
            element={
              <RequireAuth>
                <AppShell />
              </RequireAuth>
            }
          >
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/resumes" element={<ResumesPage />} />
            <Route path="/jobs" element={<JobsPage />} />
            <Route path="/opportunities" element={<OpportunitiesPage />} />
            <Route path="/applications" element={<ApplicationsPage />} />
            <Route path="/inbox" element={<InboxPage />} />
            <Route path="/follow-ups" element={<FollowUpsPage />} />
            <Route path="/interviews" element={<InterviewsPage />} />
            <Route path="/cover-letters" element={<CoverLettersPage />} />
            <Route path="/learning" element={<LearningPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/copilot" element={<CopilotPage />} />
            <Route path="/agents" element={<AgentsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/matching" element={<Navigate to="/jobs" replace />} />
          </Route>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
