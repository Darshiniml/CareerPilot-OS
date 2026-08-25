import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState } from '../../components/ui/LoadingState';
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
  location: string;
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

  const [timelineModalOpen, setTimelineModalOpen] = useState(false);
  const [timelineEvents, setTimelineEvents] = useState<any[]>([]);

  const handleUpdateCandidateStatus = async () => {
    if (!statusAppId) return;
    try {
      await api.put(`/applications/${statusAppId}/status`, {
        targetState,
        reason: statusReason || `Manually reported ${targetState} by user`
      });
      alert(`Application status updated to ${targetState}.`);
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

  const handleOpenTimelineModal = async (appId: string) => {
    try {
      const timelineRes = await api.get(`/applications/${appId}/timeline`);
      setTimelineEvents(timelineRes.data || []);
      setTimelineModalOpen(true);
    } catch (e) {
      console.error('Failed to load application timeline:', e);
      alert('Could not load application timeline.');
    }
  };

  const fetchApplicationsAndDetails = async () => {
    try {
      const appsRes = await api.get('/applications');
      const appsList: Application[] = appsRes.data || [];

      const detailedApps = await Promise.all(
        appsList.map(async (app) => {
          let jobTitle = 'Software Engineer';
          let companyName = 'Enterprise Partner';
          let location = 'Remote';
          let applyUrl = app.metadata?.applyUrl || '';

          try {
            const jobRes = await api.get(`/ai/job/${app.jobId}`);
            jobTitle = jobRes.data?.title || jobTitle;
            companyName = jobRes.data?.companyName || jobRes.data?.company || companyName;
            location = jobRes.data?.location || location;
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
            location,
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
      await api.post(`/applications/${id}/${action}`, { actorId: null });
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
      REJECTED: { label: 'Rejected', variant: 'danger' },
      RETRYING: { label: 'Retrying', variant: 'warning' },
      COMPLETED: { label: 'Completed', variant: 'success' },
    };

    const cfg = states[state] || { label: state, variant: 'neutral' as const };
    return <Badge variant={cfg.variant}>{cfg.label}</Badge>;
  };

  const sections = [
    'READY FOR REVIEW',
    'HIGH PRIORITY',
    'MANUAL ACTION REQUIRED',
    'SUBMISSION IN PROGRESS',
    'SUBMITTED',
    'UNDER REVIEW',
    'INTERVIEW',
    'OFFER',
    'REJECTED',
    'FAILED'
  ];

  const getSectionApplications = (section: string) => {
    return applications.filter((app) => {
      const state = app.workflowState;
      const matchPct = app.matchScore ? Math.round(app.matchScore * 100) : 0;
      switch (section) {
        case 'READY FOR REVIEW':
          return state === 'READY_FOR_APPROVAL' || state === 'PENDING_APPROVAL' || state === 'APPLICATION_READY' || state === 'WAITING_APPROVAL';
        case 'HIGH_PRIORITY':
          return matchPct >= 80 && (state === 'DISCOVERED' || state === 'MATCHED' || state === 'ELIGIBLE' || state === 'READY' || state === 'APPLICATION_PREPARING');
        case 'MANUAL_ACTION_REQUIRED':
          return state === 'MANUAL_ACTION_REQUIRED' || state === 'UNSUPPORTED_CONNECTOR' || state === 'UNSUPPORTED_CONNECTOR_FALLBACK';
        case 'SUBMISSION IN PROGRESS':
          return state === 'SUBMITTING' || state === 'SUBMISSION_IN_PROGRESS' || state === 'APPROVED';
        case 'SUBMITTED':
          return state === 'SUBMITTED' || state === 'SUBMITTED_VERIFIED' || state === 'COMPLETED';
        case 'UNDER REVIEW':
          return state === 'UNDER_REVIEW' || state === 'ASSESSMENT';
        case 'INTERVIEW':
          return state === 'INTERVIEW';
        case 'OFFER':
          return state === 'OFFER';
        case 'REJECTED':
          return state === 'REJECTED' || state === 'REJECTED_BY_COMPANY' || state === 'WITHDRAWN' || state === 'ALREADY_APPLIED';
        case 'FAILED':
          return state === 'FAILED' || state === 'APPLICATION_FAILED' || state === 'SUBMISSION_FAILED' || state === 'RETRYING' || state === 'APPLICATION_BLOCKED_BY_DAILY_LIMIT';
        default:
          return false;
      }
    });
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

        {/* Grouped Workflow Queue sections */}
        <section className="space-y-8">
          {sections.map((sec) => {
            const secApps = getSectionApplications(sec);
            if (secApps.length === 0) return null;

            return (
              <div key={sec} className="space-y-4">
                <div className="flex items-center gap-2 border-b border-iron-gray/10 pb-2">
                  <h3 className="text-subheading font-bold text-jet-black tracking-tight">{sec}</h3>
                  <Badge variant="neutral">{secApps.length}</Badge>
                </div>

                <div className="grid grid-cols-1 gap-4">
                  {secApps.map((app) => (
                    <Card key={app.applicationId} variant="white" className="border border-iron-gray/15 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                      <div>
                        <h4 className="text-body-sm font-bold text-jet-black">{app.jobTitle}</h4>
                        <p className="text-caption text-slate mt-0.5">{app.companyName} · {app.location}</p>
                        
                        <div className="flex flex-wrap gap-2 mt-2 items-center">
                          {getStatusBadge(app.workflowState)}
                          <span className="text-[11px] font-mono text-slate bg-iron-gray/10 px-1.5 py-0.5 rounded">
                            Match Score: {app.matchScore ? `${Math.round(app.matchScore * 100)}%` : '—'}
                          </span>
                          {app.submissionMethod && (
                            <span className="text-[11px] font-mono text-slate bg-iron-gray/10 px-1.5 py-0.5 rounded">
                              Mode: {app.submissionMethod}
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Right Hand Actions */}
                      <div className="flex flex-wrap items-center gap-2 mt-2 md:mt-0 ml-auto">
                        <button
                          onClick={() => handleOpenTimelineModal(app.applicationId)}
                          className="px-2.5 py-1.5 text-xs font-semibold rounded bg-mist-gray hover:bg-iron-gray/10 text-jet-black border border-iron-gray/20 transition-all"
                        >
                          Timeline
                        </button>
                        <button
                          onClick={() => { setStatusAppId(app.applicationId); setStatusModalOpen(true); }}
                          className="px-2.5 py-1.5 text-xs font-semibold rounded bg-mist-gray hover:bg-iron-gray/10 text-jet-black border border-iron-gray/20 transition-all"
                        >
                          Update Status
                        </button>
                        <button
                          onClick={() => handleOpenPackageModal(app.applicationId)}
                          className="px-2.5 py-1.5 text-xs font-semibold rounded bg-mist-gray hover:bg-iron-gray/10 text-jet-black border border-iron-gray/20 transition-all"
                        >
                          Review Package
                        </button>

                        {(app.workflowState === 'MANUAL_ACTION_REQUIRED' || app.workflowState === 'UNSUPPORTED_CONNECTOR') && app.applyUrl && (
                          <a
                            href={app.applyUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded bg-indigo-600 text-white hover:bg-indigo-700 transition-all"
                          >
                            Open Official Apply
                            <ExternalLink className="w-3.5 h-3.5" />
                          </a>
                        )}

                        {(app.workflowState === 'READY_FOR_APPROVAL' || app.workflowState === 'PENDING_APPROVAL') && (
                          <>
                            <button
                              onClick={() => triggerStateAction(app.applicationId, 'approve')}
                              className="p-1.5 rounded bg-emerald-500/10 text-emerald-700 hover:bg-emerald-500/20 transition-all"
                            >
                              <Check className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => triggerStateAction(app.applicationId, 'reject')}
                              className="p-1.5 rounded bg-red-500/10 text-red-700 hover:bg-red-500/20 transition-all"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </>
                        )}

                        {app.workflowState === 'APPROVED' && (
                          <button
                            onClick={() => triggerStateAction(app.applicationId, 'submit')}
                            className="px-3 py-1.5 text-xs font-semibold rounded bg-jet-black text-white hover:bg-charcoal transition-all inline-flex items-center gap-1"
                          >
                            <Play className="w-3.5 h-3.5" />
                            Submit
                          </button>
                        )}

                        {(app.workflowState === 'SUBMISSION_FAILED' || app.workflowState === 'APPLICATION_FAILED' || app.workflowState === 'FAILED') && (
                          <button
                            onClick={() => triggerStateAction(app.applicationId, 'retry')}
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
                    </Card>
                  ))}
                </div>
              </div>
            );
          })}
        </section>

        {/* Timeline Modal */}
        {timelineModalOpen && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl max-w-md w-full p-6 space-y-6 shadow-2xl border border-iron-gray/20">
              <div className="flex justify-between items-start border-b border-iron-gray/10 pb-4">
                <h3 className="text-subheading font-bold text-jet-black">Workflow Transition Timeline</h3>
                <button onClick={() => setTimelineModalOpen(false)} className="p-1 text-slate hover:text-jet-black">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="max-h-60 overflow-y-auto space-y-3">
                {timelineEvents.map((ev: any, index: number) => (
                  <div key={index} className="flex gap-3 text-body-sm">
                    <div className="w-2 h-2 rounded-full bg-indigo-600 mt-1.5" />
                    <div>
                      <p className="font-bold text-jet-black">{ev.state}</p>
                      <p className="text-xs text-slate mt-0.5">{ev.reason || 'State transition'}</p>
                      <p className="text-[10px] text-ash-gray mt-0.5">{new Date(ev.timestamp).toLocaleString()}</p>
                    </div>
                  </div>
                ))}
              </div>

              <div className="flex justify-end pt-4 border-t border-iron-gray/10">
                <button onClick={() => setTimelineModalOpen(false)} className="px-4 py-2 bg-jet-black text-white text-xs font-semibold rounded hover:bg-charcoal transition-all">
                  Close
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Decision & Package Modal */}
        {modalOpen && activeDecision && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl max-w-2xl w-full p-6 space-y-6 shadow-2xl border border-iron-gray/20 max-h-[90vh] overflow-y-auto">
              <div className="flex justify-between items-start border-b border-iron-gray/10 pb-4">
                <div>
                  <h3 className="text-subheading font-bold text-jet-black">Application Decision & Package</h3>
                  <span className="text-caption text-slate">Evaluated from real candidate profile & live intelligence</span>
                </div>
                <button onClick={() => setModalOpen(false)} className="p-1 text-slate hover:text-jet-black">
                  <X className="w-5 h-5" />
                </button>
              </div>

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

              <div className="space-y-2">
                <h4 className="text-caption uppercase font-semibold text-slate">Selected Resume Version</h4>
                <div className="p-3 bg-white rounded border border-iron-gray/20 flex justify-between items-center">
                  <span className="text-body-sm font-bold text-jet-black">{activeDecision.recommendedResumeTitle || 'Default Resume'}</span>
                </div>
              </div>

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

        {/* Status Update Modal */}
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
                    <option value="UNDER_REVIEW">UNDER_REVIEW</option>
                    <option value="ASSESSMENT">ASSESSMENT</option>
                    <option value="INTERVIEW">INTERVIEW</option>
                    <option value="OFFER">OFFER</option>
                    <option value="REJECTED">REJECTED</option>
                  </select>
                </div>

                <div>
                  <label className="text-caption font-semibold text-slate block mb-1">Reason / Notes (Optional)</label>
                  <textarea
                    rows={3}
                    value={statusReason}
                    onChange={(e) => setStatusReason(e.target.value)}
                    placeholder="Notes..."
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
