import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState } from '../../components/ui/LoadingState';
import { EmptyState } from '../../components/ui/EmptyState';
import { api } from '../../services/api';
import { Play, RotateCcw, Check, X, ExternalLink, CheckCircle2 } from 'lucide-react';

interface Application {
  applicationId: string;
  jobId: string;
  candidateId: string;
  workflowState: string;
  submissionMethod?: string;
  matchScore: number;
  submittedAt: string | null;
  createdAt: string;
  metadata?: Record<string, any>;
}

interface ApplicationDetails extends Application {
  jobTitle: string;
  companyName: string;
  applyUrl?: string;
}

export const Applications: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [applications, setApplications] = useState<ApplicationDetails[]>([]);
  const [stats, setStats] = useState({ total: 0, submitted: 0, manual: 0, failed: 0 });
  const [activePackage, setActivePackage] = useState<any>(null);
  const [activeDecision, setActiveDecision] = useState<any>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [statusAppId, setStatusAppId] = useState<string | null>(null);
  const [targetState, setTargetState] = useState('INTERVIEW');
  const [statusReason, setStatusReason] = useState('');

  const handleUpdateCandidateStatus = async () => {
    if (!statusAppId) return;
    try {
      await api.put(`/applications/${statusAppId}/status`, {
        targetState,
        reason: statusReason || `Manually reported ${targetState} by user`
      });
      alert(`Application status updated to ${targetState} (Manually reported by you).`);
      setStatusModalOpen(false);
      fetchApplicationsAndDetails();
    } catch (e) {
      console.error(e);
      alert('Failed to update application status.');
    }
  };

  const handleOpenPackageModal = async (appId: string) => {
    try {
      const [decRes, pkgRes] = await Promise.all([
        api.get(`/applications/${appId}/decision`),
        api.get(`/applications/${appId}/package`)
      ]);
      setActiveDecision(decRes.data);
      setActivePackage(pkgRes.data);
      setModalOpen(true);
    } catch (e) {
      console.error('Failed to load application package:', e);
      alert('Could not load application decision package.');
    }
  };

  const fetchApplicationsAndDetails = async () => {
    try {
      const appsRes = await api.get('/applications');
      const appsList: Application[] = appsRes.data || [];

      // Fetch corresponding job info for display
      const detailedApps = await Promise.all(
        appsList.map(async (app) => {
          let jobTitle = 'Software Engineer';
          let companyName = 'Enterprise Partner';
          let applyUrl = app.metadata?.applyUrl || '';

          try {
            const jobRes = await api.get(`/ai/job/${app.jobId}`);
            jobTitle = jobRes.data?.title || jobTitle;
            companyName = jobRes.data?.companyName || jobRes.data?.company || companyName;
            if (!applyUrl && jobRes.data?.sourceUrl) {
              applyUrl = jobRes.data.sourceUrl;
            }
          } catch (e) {
            console.warn(`Job details missing for ${app.jobId}`);
          }
          return {
            ...app,
            jobTitle,
            companyName,
            applyUrl,
          };
        })
      );

      setApplications(detailedApps);

      try {
        const statsRes = await api.get('/applications/statistics');
        setStats({
          total: statsRes.data?.totalApplications || detailedApps.length,
          submitted: statsRes.data?.applicationsSubmitted || detailedApps.filter(x => x.workflowState?.includes('SUBMITTED')).length,
          manual: detailedApps.filter(x => x.workflowState === 'MANUAL_ACTION_REQUIRED').length,
          failed: statsRes.data?.failed || detailedApps.filter(x => x.workflowState?.includes('FAILED')).length,
        });
      } catch (e) {
        setStats({
          total: detailedApps.length,
          submitted: detailedApps.filter(x => x.workflowState?.includes('SUBMITTED')).length,
          manual: detailedApps.filter(x => x.workflowState === 'MANUAL_ACTION_REQUIRED').length,
          failed: detailedApps.filter(x => x.workflowState?.includes('FAILED')).length,
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
      DISCOVERED: { label: 'Discovered', variant: 'neutral' },
      MATCHED: { label: 'Matched', variant: 'neutral' },
      ELIGIBLE: { label: 'Eligible', variant: 'neutral' },
      APPLICATION_PREPARING: { label: 'Preparing Package', variant: 'warning' },
      APPLICATION_READY: { label: 'Ready for Review', variant: 'warning' },
      READY_FOR_APPROVAL: { label: 'Ready for Approval', variant: 'warning' },
      APPROVED: { label: 'Approved', variant: 'active' },
      SUBMISSION_IN_PROGRESS: { label: 'Submitting', variant: 'active' },
      SUBMITTED: { label: 'Submitted', variant: 'success' },
      SUBMITTED_VERIFIED: { label: 'Submitted & Verified ✓', variant: 'success' },
      MANUAL_ACTION_REQUIRED: { label: 'Manual Action Required ⚠', variant: 'warning' },
      UNSUPPORTED_CONNECTOR: { label: 'Unsupported Source ⚠', variant: 'warning' },
      ALREADY_APPLIED: { label: 'Already Applied', variant: 'neutral' },
      APPLICATION_BLOCKED_BY_DAILY_LIMIT: { label: 'Daily Limit Reached ⛔', variant: 'danger' },
      APPLICATION_FAILED: { label: 'Application Failed', variant: 'danger' },
      SUBMISSION_FAILED: { label: 'Submission Failed', variant: 'danger' },
      SUBMISSION_UNVERIFIED: { label: 'Unverified Submission', variant: 'warning' },
      REJECTED: { label: 'Rejected', variant: 'danger' },
      RETRYING: { label: 'Retrying', variant: 'warning' },
      COMPLETED: { label: 'Completed', variant: 'success' },
    };

    const cfg = states[state] || { label: state, variant: 'neutral' as const };
    return <Badge variant={cfg.variant}>{cfg.label}</Badge>;
  };

  if (loading) {
    return (
      <AppShell title="Candidate Workflows" description="Monitor automation sequences, submission evidence, and candidate applications.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="Candidate Workflows" description="Monitor automation sequences, submission evidence, and candidate applications.">
      <div className="space-y-8">
        
        {/* Statistics Cards */}
        <section className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Total Pipelines</span>
            <p className="text-heading font-normal text-jet-black mt-2">{stats.total}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Submitted & Verified</span>
            <p className="text-heading font-normal text-jet-black mt-2 text-emerald-800">{stats.submitted}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Manual Action Needed</span>
            <p className="text-heading font-normal text-jet-black mt-2 text-amber-800">{stats.manual}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Pipeline Failures</span>
            <p className="text-heading font-normal text-jet-black mt-2 text-red-800">{stats.failed}</p>
          </Card>
        </section>

        {/* Workflow Table */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Active Application Records</h3>

          {applications.length === 0 ? (
            <EmptyState
              title="No active applications"
              description="Start job discovery and launch candidate automation workflows to track applications."
            />
          ) : (
            <Card variant="white" className="overflow-x-auto p-0 border border-iron-gray/15">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-iron-gray/10 text-caption font-semibold text-slate uppercase bg-mist-gray/40 select-none">
                    <th className="p-4 pl-6">Job Opportunity</th>
                    <th className="p-4">Submission Mode & Status</th>
                    <th className="p-4">Match Score</th>
                    <th className="p-4">Created</th>
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
                      <td className="p-4 space-y-1">
                        <div>{getStatusBadge(app.workflowState)}</div>
                        {app.submissionMethod && (
                          <span className="text-[11px] font-mono text-slate bg-iron-gray/10 px-1.5 py-0.5 rounded">
                            Mode: {app.submissionMethod}
                          </span>
                        )}
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
                        <div className="flex justify-end items-center gap-2">
                          <button
                            onClick={() => { setStatusAppId(app.applicationId); setStatusModalOpen(true); }}
                            title="Report Manual Status Update"
                            className="px-2.5 py-1.5 text-xs font-semibold rounded bg-mist-gray hover:bg-iron-gray/10 text-jet-black border border-iron-gray/20 transition-all"
                          >
                            Update Status
                          </button>
                          <button
                            onClick={() => handleOpenPackageModal(app.applicationId)}
                            title="Review Decision & Application Package"
                            className="px-2.5 py-1.5 text-xs font-semibold rounded bg-mist-gray hover:bg-iron-gray/10 text-jet-black border border-iron-gray/20 transition-all"
                          >
                            Review Package
                          </button>
                          {app.workflowState === 'MANUAL_ACTION_REQUIRED' && app.applyUrl && (
                            <a
                              href={app.applyUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded bg-indigo-600 text-white hover:bg-indigo-700 transition-all"
                            >
                              Open Official Application
                              <ExternalLink className="w-3.5 h-3.5" />
                            </a>
                          )}
                          {(app.workflowState === 'READY_FOR_APPROVAL' || app.workflowState === 'PENDING_APPROVAL') && (
                            <>
                              <button
                                onClick={() => triggerStateAction(app.applicationId, 'approve')}
                                title="Approve Submission"
                                className="p-1.5 rounded bg-emerald-500/10 text-emerald-700 hover:bg-emerald-500/20 transition-all"
                              >
                                <Check className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => triggerStateAction(app.applicationId, 'reject')}
                                title="Reject Submission"
                                className="p-1.5 rounded bg-red-500/10 text-red-700 hover:bg-red-500/20 transition-all"
                              >
                                <X className="w-4 h-4" />
                              </button>
                            </>
                          )}
                          {app.workflowState === 'APPROVED' && (
                            <button
                              onClick={() => triggerStateAction(app.applicationId, 'submit')}
                              title="Submit Application"
                              className="px-3 py-1.5 text-xs font-semibold rounded bg-jet-black text-white hover:bg-charcoal transition-all inline-flex items-center gap-1"
                            >
                              <Play className="w-3.5 h-3.5" />
                              Submit
                            </button>
                          )}
                          {(app.workflowState === 'SUBMISSION_FAILED' || app.workflowState === 'APPLICATION_FAILED' || app.workflowState === 'FAILED') && (
                            <button
                              onClick={() => triggerStateAction(app.applicationId, 'retry')}
                              title="Retry Submission"
                              className="p-1.5 rounded bg-mist-gray text-jet-black hover:bg-iron-gray/10 border border-iron-gray/15 transition-all"
                            >
                              <RotateCcw className="w-4 h-4" />
                            </button>
                          )}
                          {app.workflowState === 'SUBMITTED_VERIFIED' && (
                            <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-700 bg-emerald-500/10 px-2 py-1 rounded">
                              <CheckCircle2 className="w-3.5 h-3.5" /> Verified
                            </span>
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

        {/* Decision & Package Workspace Modal */}
        {modalOpen && activeDecision && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl max-w-2xl w-full p-6 space-y-6 shadow-2xl border border-iron-gray/20 max-h-[90vh] overflow-y-auto">
              <div className="flex justify-between items-start border-b border-iron-gray/10 pb-4">
                <div>
                  <h3 className="text-subheading font-bold text-jet-black">Application Decision & Package</h3>
                  <span className="text-caption text-slate">Evaluated from real candidate profile, matching engine & company intelligence</span>
                </div>
                <button onClick={() => setModalOpen(false)} className="p-1 text-slate hover:text-jet-black">
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Recommendation Badge & Rationale */}
              <div className="p-4 rounded-lg bg-mist-gray/50 border border-iron-gray/15 space-y-2">
                <div className="flex items-center gap-2">
                  <span className="text-caption uppercase font-semibold text-slate">Recommendation:</span>
                  <span className={`px-2.5 py-0.5 rounded text-xs font-bold ${
                    activeDecision.recommendation === 'RECOMMENDED_TO_APPLY' ? 'bg-emerald-500/10 text-emerald-700 border border-emerald-500/20' :
                    activeDecision.recommendation === 'APPLY_WITH_CAUTION' ? 'bg-amber-500/10 text-amber-700 border border-amber-500/20' :
                    'bg-red-500/10 text-red-700 border border-red-500/20'
                  }`}>
                    {activeDecision.recommendation}
                  </span>
                </div>
                <p className="text-body-sm text-jet-black font-medium">{activeDecision.decisionRationale}</p>
              </div>

              {/* Selected Resume */}
              <div className="space-y-2">
                <h4 className="text-caption uppercase font-semibold text-slate">Selected Resume Version</h4>
                <div className="p-3 bg-white rounded border border-iron-gray/20 flex justify-between items-center">
                  <span className="text-body-sm font-bold text-jet-black">{activeDecision.recommendedResumeTitle || 'Default Resume'}</span>
                  <span className="text-xs text-slate">Resume ID: {activeDecision.recommendedResumeId ? String(activeDecision.recommendedResumeId).substring(0, 8) + '...' : 'Primary'}</span>
                </div>
              </div>

              {/* Official Apply URL */}
              {activePackage?.officialApplyUrl && (
                <div className="p-4 bg-indigo-50/50 rounded-lg border border-indigo-100 flex items-center justify-between">
                  <div>
                    <span className="text-caption font-bold text-indigo-900 block">Official Application Link</span>
                    <span className="text-xs text-indigo-700 truncate max-w-md block">{activePackage.officialApplyUrl}</span>
                  </div>
                  <a
                    href={activePackage.officialApplyUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs rounded transition-all flex items-center gap-1"
                  >
                    Open Link <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
              )}

              <div className="flex justify-end pt-4 border-t border-iron-gray/10">
                <button onClick={() => setModalOpen(false)} className="px-4 py-2 bg-jet-black text-white text-xs font-semibold rounded hover:bg-charcoal transition-all">
                  Close Review Workspace
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Candidate Manual Status Update Modal */}
        {statusModalOpen && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl max-w-md w-full p-6 space-y-6 shadow-2xl border border-iron-gray/20">
              <div className="flex justify-between items-start border-b border-iron-gray/10 pb-4">
                <div>
                  <h3 className="text-subheading font-bold text-jet-black">Report Application Status Update</h3>
                  <span className="text-caption text-slate">Manually reported by you</span>
                </div>
                <button onClick={() => setStatusModalOpen(false)} className="p-1 text-slate hover:text-jet-black">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="text-caption font-semibold text-slate block mb-1">New Status State</label>
                  <select
                    value={targetState}
                    onChange={(e) => setTargetState(e.target.value)}
                    className="w-full p-2.5 rounded-lg border border-iron-gray/20 text-body-sm font-semibold text-jet-black bg-mist-gray/30"
                  >
                    <option value="UNDER_REVIEW">UNDER_REVIEW (Recruiter reviewing profile)</option>
                    <option value="ASSESSMENT">ASSESSMENT (Technical screening test)</option>
                    <option value="INTERVIEW">INTERVIEW (Interview scheduled / in progress)</option>
                    <option value="OFFER">OFFER (Job offer extended)</option>
                    <option value="REJECTED">REJECTED (Application rejected by company)</option>
                    <option value="WITHDRAWN">WITHDRAWN (Withdrawn by candidate)</option>
                  </select>
                </div>

                <div>
                  <label className="text-caption font-semibold text-slate block mb-1">Reason / Notes (Optional)</label>
                  <textarea
                    rows={3}
                    value={statusReason}
                    onChange={(e) => setStatusReason(e.target.value)}
                    placeholder="e.g. Received interview invitation email from recruiter..."
                    className="w-full p-2.5 rounded-lg border border-iron-gray/20 text-body-sm text-jet-black bg-white"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-4 border-t border-iron-gray/10">
                <button onClick={() => setStatusModalOpen(false)} className="px-4 py-2 bg-mist-gray text-jet-black text-xs font-semibold rounded hover:bg-iron-gray/20 transition-all">
                  Cancel
                </button>
                <button onClick={handleUpdateCandidateStatus} className="px-4 py-2 bg-indigo-600 text-white text-xs font-semibold rounded hover:bg-indigo-700 transition-all">
                  Save Status Update
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </AppShell>
  );
};
