import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState } from '../../components/ui/LoadingState';
import { EmptyState } from '../../components/ui/EmptyState';
import { api } from '../../services/api';
import { ExternalLink, CheckCircle2 } from 'lucide-react';

interface Opportunity {
  jobId: string;
  title: string;
  company: string;
  location: string;
  workMode: string;
  source: string;
  sourceUrl: string;
  connectorId: string;
  matchScore: number;
  historicalSuccessScore: number;
  historicalConfidence: string;
  priorityScore: number;
  priorityLevel: string;
  applicationStatus: string;
  submissionMode: string;
  recommendedAction: string;
  reasons: string[];
}

export const OpportunityWorkspace: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [opportunities, setOpportunities] = useState<Opportunity[]>([]);
  const [filters, setFilters] = useState({
    search: '',
    priority: '',
    workMode: '',
  });

  const fetchOpportunities = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (filters.search) params.append('search', filters.search);
      if (filters.priority) params.append('priority', filters.priority);
      if (filters.workMode) params.append('workMode', filters.workMode);
      
      const res = await api.get(`/opportunities?${params.toString()}`);
      setOpportunities(res.data?.content || res.data || []);
    } catch (e) {
      console.error('Failed to fetch opportunities:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOpportunities();
  }, [filters.priority, filters.workMode]);

  const handleApply = async (opp: Opportunity) => {
    try {
      await api.post(`/opportunities/${opp.jobId}/applications`);
      alert('Application review workflow initiated successfully!');
      fetchOpportunities();
    } catch (e: any) {
      console.error('Failed to create application:', e);
      alert(e.response?.data?.message || 'Failed to create application.');
    }
  };

  const getPriorityBadge = (level: string) => {
    switch (level) {
      case 'HIGH_PRIORITY':
        return <Badge variant="danger">High Priority</Badge>;
      case 'MEDIUM_PRIORITY':
        return <Badge variant="warning">Medium Priority</Badge>;
      case 'LOW_PRIORITY':
        return <Badge variant="neutral">Low Priority</Badge>;
      default:
        return <Badge variant="neutral">Not Recommended</Badge>;
    }
  };

  return (
    <AppShell title="Personalized Opportunity Workspace" description="Review candidate-scoped matching opportunities prioritized by live historical outcomes.">
      <div className="space-y-6">
        
        {/* Filters Panel */}
        <section className="flex flex-col md:flex-row gap-4 items-center justify-between bg-mist-gray/30 p-4 rounded-xl border border-iron-gray/10">
          <div className="flex flex-1 gap-3 w-full md:w-auto">
            <input
              type="text"
              placeholder="Search by title, company, location..."
              value={filters.search}
              onChange={(e) => setFilters({ ...filters, search: e.target.value })}
              onKeyDown={(e) => e.key === 'Enter' && fetchOpportunities()}
              className="flex-1 max-w-md px-4 py-2 text-body-sm rounded-lg border border-iron-gray/20 focus:outline-none focus:ring-1 focus:ring-indigo-500 bg-white"
            />
            <button
              onClick={fetchOpportunities}
              className="px-4 py-2 bg-jet-black text-white text-xs font-semibold rounded-lg hover:bg-charcoal transition-all"
            >
              Search
            </button>
          </div>

          <div className="flex gap-4 w-full md:w-auto justify-end">
            <select
              value={filters.priority}
              onChange={(e) => setFilters({ ...filters, priority: e.target.value })}
              className="px-3 py-2 text-body-sm rounded-lg border border-iron-gray/20 bg-white font-semibold"
            >
              <option value="">All Priorities</option>
              <option value="HIGH_PRIORITY">High Priority</option>
              <option value="MEDIUM_PRIORITY">Medium Priority</option>
              <option value="LOW_PRIORITY">Low Priority</option>
            </select>

            <select
              value={filters.workMode}
              onChange={(e) => setFilters({ ...filters, workMode: e.target.value })}
              className="px-3 py-2 text-body-sm rounded-lg border border-iron-gray/20 bg-white font-semibold"
            >
              <option value="">All Work Modes</option>
              <option value="REMOTE">Remote</option>
              <option value="HYBRID">Hybrid</option>
              <option value="ON_SITE">On Site</option>
            </select>
          </div>
        </section>

        {loading ? (
          <LoadingState />
        ) : opportunities.length === 0 ? (
          <EmptyState title="No opportunities found" description="Adjust your filters or upload a resume to discover personalized opportunities." />
        ) : (
          <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {opportunities.map((opp) => (
              <Card key={opp.jobId} variant="white" className="border border-iron-gray/15 flex flex-col justify-between h-full">
                <div className="space-y-4">
                  {/* Title & Metadata */}
                  <div className="flex justify-between items-start gap-4">
                    <div>
                      <h4 className="text-subheading font-bold text-jet-black">{opp.title}</h4>
                      <p className="text-body-sm font-semibold text-slate mt-0.5">{opp.company}</p>
                      <p className="text-caption text-ash-gray mt-1">{opp.location} · {opp.workMode}</p>
                    </div>
                    <div>{getPriorityBadge(opp.priorityLevel)}</div>
                  </div>

                  {/* Metrics Row */}
                  <div className="grid grid-cols-3 gap-2 bg-mist-gray/20 p-3 rounded-lg border border-iron-gray/10 text-center">
                    <div>
                      <span className="text-[10px] text-slate font-bold uppercase block">Match Score</span>
                      <span className="text-body-sm font-extrabold text-indigo-700">{Math.round(opp.matchScore)}%</span>
                    </div>
                    <div>
                      <span className="text-[10px] text-slate font-bold uppercase block">Historical Success</span>
                      <span className="text-body-sm font-extrabold text-emerald-700">
                        {opp.historicalConfidence === 'SUFFICIENT' ? `${Math.round(opp.historicalSuccessScore)}%` : '—'}
                      </span>
                    </div>
                    <div>
                      <span className="text-[10px] text-slate font-bold uppercase block">Priority Score</span>
                      <span className="text-body-sm font-extrabold text-rose-700">{Math.round(opp.priorityScore)}</span>
                    </div>
                  </div>

                  {/* Why this job / Reasons */}
                  <div className="space-y-1.5 pt-2">
                    <span className="text-caption font-bold text-slate block uppercase tracking-wider">Why this job?</span>
                    <ul className="space-y-1 text-body-sm text-charcoal">
                      {opp.reasons.map((r, i) => (
                        <li key={i} className="flex items-start gap-1.5">
                          <span className="text-emerald-600 mt-1">•</span>
                          <span>{r}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  {/* Status Banner */}
                  {opp.applicationStatus !== 'NOT_APPLIED' && (
                    <div className="flex items-center gap-1.5 text-caption font-semibold bg-emerald-500/10 text-emerald-800 p-2 rounded border border-emerald-500/20">
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Pipeline state: {opp.applicationStatus}</span>
                    </div>
                  )}
                </div>

                {/* Card Actions */}
                <div className="flex gap-3 pt-6 mt-auto border-t border-iron-gray/10">
                  {opp.applicationStatus === 'NOT_APPLIED' ? (
                    <>
                      <button
                        onClick={() => handleApply(opp)}
                        className="flex-1 px-4 py-2 bg-indigo-600 text-white font-semibold text-xs rounded hover:bg-indigo-700 transition-all disabled:opacity-50"
                      >
                        {opp.submissionMode === 'MANUAL_REQUIRED' ? 'Prepare Manual Apply' : 'Review Application'}
                      </button>
                      {opp.sourceUrl && (
                      <a
                        href={opp.sourceUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="px-4 py-2 bg-mist-gray hover:bg-iron-gray/10 text-jet-black font-semibold text-xs rounded border border-iron-gray/20 transition-all inline-flex items-center gap-1"
                      >
                        Open Apply Link
                        <ExternalLink className="w-3 h-3" />
                      </a>
                      )}
                    </>
                  ) : (
                    <button
                      disabled
                      className="w-full px-4 py-2 bg-mist-gray text-slate font-semibold text-xs rounded border border-iron-gray/10 cursor-not-allowed text-center"
                    >
                      Already in Workflow Queue
                    </button>
                  )}
                </div>
              </Card>
            ))}
          </section>
        )}

      </div>
    </AppShell>
  );
};
