import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState } from '../../components/ui/LoadingState';
import { EmptyState } from '../../components/ui/EmptyState';
import { api } from '../../services/api';
import { Play, RotateCcw, Check, X } from 'lucide-react';

interface Application {
  applicationId: string;
  jobId: string;
  candidateId: string;
  workflowState: string;
  matchScore: number;
  submittedAt: string | null;
  createdAt: string;
}

interface ApplicationDetails extends Application {
  jobTitle: string;
  companyName: string;
}

export const Applications: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [applications, setApplications] = useState<ApplicationDetails[]>([]);
  const [stats, setStats] = useState({ total: 0, submitted: 0, failed: 0 });

  const fetchApplicationsAndDetails = async () => {
    try {
      const appsRes = await api.get('/applications');
      const appsList: Application[] = appsRes.data || [];

      // Fetch corresponding job info for display
      const detailedApps = await Promise.all(
        appsList.map(async (app) => {
          let jobTitle = 'Software Engineer';
          let companyName = 'Enterprise Corp';
          try {
            const jobRes = await api.get(`/ai/job/${app.jobId}`);
            jobTitle = jobRes.data?.title || jobTitle;
            companyName = jobRes.data?.companyName || jobRes.data?.company || companyName;
          } catch (e) {
            console.warn(`Job details missing for ${app.jobId}`);
          }
          return {
            ...app,
            jobTitle,
            companyName,
          };
        })
      );

      setApplications(detailedApps);

      // Fetch statistics
      try {
        const statsRes = await api.get('/applications/statistics');
        setStats({
          total: statsRes.data?.totalApplications || detailedApps.length,
          submitted: statsRes.data?.submitted || detailedApps.filter(x => x.workflowState === 'SUBMITTED' || x.workflowState === 'COMPLETED').length,
          failed: statsRes.data?.failed || detailedApps.filter(x => x.workflowState === 'FAILED').length,
        });
      } catch (e) {
        setStats({
          total: detailedApps.length,
          submitted: detailedApps.filter(x => x.workflowState === 'SUBMITTED' || x.workflowState === 'COMPLETED').length,
          failed: detailedApps.filter(x => x.workflowState === 'FAILED').length,
        });
      }
    } catch (e) {
      console.error('Error fetching applications:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApplicationsAndDetails();
  }, []);

  const triggerStateAction = async (id: string, action: string) => {
    try {
      await api.post(`/applications/${id}/${action}`);
      alert(`Action '${action}' triggered successfully!`);
      fetchApplicationsAndDetails();
    } catch (e) {
      console.error(e);
      alert(`Failed to execute '${action}' action.`);
    }
  };

  const getStatusBadge = (state: string) => {
    const states: Record<string, { label: string; variant: 'neutral' | 'active' | 'success' | 'warning' | 'danger' }> = {
      CREATED: { label: 'Created', variant: 'neutral' },
      ELIGIBILITY_CHECKED: { label: 'Eligible', variant: 'neutral' },
      PENDING_APPROVAL: { label: 'Pending Approval', variant: 'warning' },
      APPROVED: { label: 'Approved', variant: 'active' },
      REJECTED: { label: 'Rejected', variant: 'danger' },
      SUBMITTED: { label: 'Submitted', variant: 'success' },
      FAILED: { label: 'Failed', variant: 'danger' },
      RETRYING: { label: 'Retrying', variant: 'warning' },
      COMPLETED: { label: 'Completed', variant: 'success' },
    };

    const cfg = states[state] || { label: state, variant: 'neutral' as const };
    return <Badge variant={cfg.variant}>{cfg.label}</Badge>;
  };

  if (loading) {
    return (
      <AppShell title="Candidate Workflows" description="Monitor automation sequences and active job applications.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="Candidate Workflows" description="Monitor automation sequences and active job applications.">
      <div className="space-y-8">
        
        {/* Statistics Cards */}
        <section className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Total Workflows</span>
            <p className="text-heading font-normal text-jet-black mt-2">{stats.total}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Submitted Applications</span>
            <p className="text-heading font-normal text-jet-black mt-2 text-emerald-800">{stats.submitted}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Pipeline Failures</span>
            <p className="text-heading font-normal text-jet-black mt-2 text-red-800">{stats.failed}</p>
          </Card>
        </section>

        {/* Workflow Table */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Active Pipelines</h3>

          {applications.length === 0 ? (
            <EmptyState
              title="No active applications"
              description="Start a job matching evaluation and submit an application to launch a candidates workflow."
            />
          ) : (
            <Card variant="white" className="overflow-x-auto p-0 border border-iron-gray/15">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-iron-gray/10 text-caption font-semibold text-slate uppercase bg-mist-gray/40 select-none">
                    <th className="p-4 pl-6">Job Opportunity</th>
                    <th className="p-4">Status</th>
                    <th className="p-4">Match Score</th>
                    <th className="p-4">Initiated</th>
                    <th className="p-4 pr-6 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-iron-gray/10">
                  {applications.map((app) => (
                    <tr key={app.applicationId} className="hover:bg-mist-gray/10 transition-all">
                      {/* Name & Company */}
                      <td className="p-4 pl-6">
                        <p className="text-body-sm font-bold text-jet-black">{app.jobTitle}</p>
                        <p className="text-caption text-slate">{app.companyName}</p>
                      </td>

                      {/* Status */}
                      <td className="p-4">
                        {getStatusBadge(app.workflowState)}
                      </td>

                      {/* Score */}
                      <td className="p-4">
                        <span className="text-body-sm font-semibold text-jet-black">
                          {app.matchScore ? `${Math.round(app.matchScore * 100)}%` : '—'}
                        </span>
                      </td>

                      {/* Date */}
                      <td className="p-4 text-caption text-slate">
                        {new Date(app.createdAt).toLocaleDateString()}
                      </td>

                      {/* Actions */}
                      <td className="p-4 pr-6 text-right">
                        <div className="flex justify-end gap-1.5">
                          {app.workflowState === 'PENDING_APPROVAL' && (
                            <>
                              <button
                                onClick={() => triggerStateAction(app.applicationId, 'approve')}
                                title="Approve Submission"
                                className="p-1.5 rounded bg-emerald-500/10 text-emerald-700 hover:bg-emerald-500/20 transition-all"
                              >
                                <Check className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => triggerStateAction(app.applicationId, 'reject')}
                                title="Reject Submission"
                                className="p-1.5 rounded bg-red-500/10 text-red-700 hover:bg-red-500/20 transition-all"
                              >
                                <X className="w-3.5 h-3.5" />
                              </button>
                            </>
                          )}
                          {app.workflowState === 'APPROVED' && (
                            <button
                              onClick={() => triggerStateAction(app.applicationId, 'submit')}
                              title="Submit Application"
                              className="p-1.5 rounded bg-jet-black text-paper-white hover:bg-charcoal transition-all"
                            >
                              <Play className="w-3.5 h-3.5" />
                            </button>
                          )}
                          {app.workflowState === 'FAILED' && (
                            <button
                              onClick={() => triggerStateAction(app.applicationId, 'retry')}
                              title="Retry Submission"
                              className="p-1.5 rounded bg-mist-gray text-jet-black hover:bg-iron-gray/10 border border-iron-gray/15 transition-all"
                            >
                              <RotateCcw className="w-3.5 h-3.5" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}
        </section>

      </div>
    </AppShell>
  );
};
