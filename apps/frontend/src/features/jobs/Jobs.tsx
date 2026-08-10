import React, { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Badge } from '../../components/ui/Badge';
import { Modal } from '../../components/ui/Modal';
import { LoadingState } from '../../components/ui/LoadingState';
import { EmptyState } from '../../components/ui/EmptyState';
import { CareerSearchProfileBanner } from '../../components/ui/CareerSearchProfileBanner';
import { api } from '../../services/api';
import {
  Search,
  MapPin,
  Briefcase,
  Star,
  FileCheck,
  Building2,
  ChevronLeft,
  ChevronRight,
  Code,
  Sparkles,
  ShieldCheck,
  ExternalLink
} from 'lucide-react';

interface JobItem {
  jobId: string;
  companyId?: string;
  title: string;
  companyName: string;
  location: string;
  remotePolicy: string;
  employmentType: string;
  source: string;
  sourceUrl: string;
  skills: string[];
  description: string;
  matchScore?: number;
  applicationState?: string;
  matchedSkills?: string[];
  missingSkills?: string[];
  // Milestone 20 additions
  historicalSuccessSignal?: {
    available: boolean;
    confidenceStatus: string;
    historicalSuccessScore: number;
    explanation: string;
  };
  priorityScore?: number;
  priorityLevel?: string;
}

export const Jobs: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [jobs, setJobs] = useState<JobItem[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  
  // Pagination
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(1);
  const [totalResults, setTotalResults] = useState(0);

  // Filters
  const [remoteFilter, setRemoteFilter] = useState('');
  const [locationFilter, setLocationFilter] = useState('');
  const [techFilter, setTechFilter] = useState('');

  // Audit Match Modal State
  const [isMatchModalOpen, setIsMatchModalOpen] = useState(false);
  const [matchLoading, setMatchLoading] = useState(false);
  const [matchResultData, setMatchResultData] = useState<any>(null);

  // Company Research Modal State
  const [isCompanyModalOpen, setIsCompanyModalOpen] = useState(false);
  const [companyLoading, setCompanyLoading] = useState(false);
  const [companyData, setCompanyData] = useState<any>(null);

  // Selected Job Details Modal
  const [selectedJob, setSelectedJob] = useState<JobItem | null>(null);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
  const [applyingLoader, setApplyingLoader] = useState(false);

  // Debouncing search
  const searchTimeoutRef = useRef<any>(null);

  const fetchJobs = async (page = 0, query = searchQuery, remote = remoteFilter, loc = locationFilter, tech = techFilter) => {
    setLoading(true);
    try {
      let list: any[] = [];
      let total = 0;
      let pages = 1;

      try {
        const res = await api.post('/ai/job/search', {
          query: query || null,
          remotePolicyFilter: remote || null,
          locationFilter: loc || null,
          technologyFilter: tech || null,
          page: page + 1, // 1-indexed for backend API
          size: pageSize,
        });

        if (res.data) {
          list = res.data.content || res.data.items || (Array.isArray(res.data) ? res.data : []);
          total = res.data.totalElements || list.length;
          pages = res.data.totalPages || Math.ceil(total / pageSize) || 1;
        }
      } catch (e) {
        console.warn('Fallback to discovery jobs endpoint:', e);
      }

      if (!list || list.length === 0) {
        const discRes = await api.get('/discovery/jobs');
        list = discRes.data || [];
        total = list.length;
        pages = Math.ceil(total / pageSize) || 1;
      }

      // Fetch user application statuses to map to job cards
      let appsMap = new Map<string, string>();
      try {
        const appsRes = await api.get('/applications');
        if (appsRes.data && Array.isArray(appsRes.data)) {
          appsRes.data.forEach((app: any) => {
            if (app.jobId) appsMap.set(app.jobId, app.workflowState || app.status);
          });
        }
      } catch (e) {
        console.warn('Applications status query warning:', e);
      }

      const parsedJobs: JobItem[] = list.map((j: any) => {
        const parsedKnowledge = typeof j.structuredKnowledge === 'string'
          ? JSON.parse(j.structuredKnowledge)
          : (j.structuredKnowledge || {});
        const skillsList = parsedKnowledge?.skills?.map((s: any) => s.skill) || (j.skills || []);

        return {
          jobId: j.jobId || j.id,
          companyId: j.companyId,
          title: j.title || 'Software Developer',
          companyName: j.companyName || j.company || 'Enterprise Corp',
          location: j.location || 'Remote',
          remotePolicy: j.remotePolicy || j.workMode || 'REMOTE',
          employmentType: j.employmentType || 'FULL_TIME',
          source: j.source || 'Direct ATS',
          sourceUrl: j.sourceUrl || '#',
          skills: skillsList.length > 0 ? skillsList.slice(0, 5) : ['Java', 'Spring Boot', 'SQL'],
          description: j.rawContent || parsedKnowledge?.description || 'No description provided.',
          matchScore: j.matchScore ? Math.round(j.matchScore * 100) : undefined,
          applicationState: appsMap.get(j.jobId || j.id) || 'NOT_APPLIED',
          matchedSkills: j.matchedSkills || ['Java', 'Spring Boot'],
          missingSkills: j.missingSkills || ['AWS'],
        };
      });

      // Fetch historical success signals concurrently for the visible job list
      const jobsWithSignals = await Promise.all(parsedJobs.map(async (job) => {
        try {
          const params = new URLSearchParams();
          if (job.title) params.append('title', job.title);
          if (job.companyName) params.append('company', job.companyName);
          if (job.location) params.append('location', job.location);
          if (job.remotePolicy) params.append('workMode', job.remotePolicy);
          if (job.source) params.append('source', job.source);
          if (job.skills && job.skills.length > 0) {
            job.skills.forEach(s => params.append('skills', s));
          }

          const sigRes = await api.get(`/analytics/historical-success?${params.toString()}`);
          const signal = sigRes.data;

          const matchScore = job.matchScore || 70;
          let priorityScore = matchScore;
          if (signal && signal.available) {
            priorityScore = Math.round((matchScore + signal.historicalSuccessScore) / 2);
          }

          let priorityLevel = 'MEDIUM';
          if (priorityScore >= 80) priorityLevel = 'HIGH';
          else if (priorityScore < 60) priorityLevel = 'LOW';

          return {
            ...job,
            historicalSuccessSignal: signal,
            priorityScore,
            priorityLevel
          };
        } catch (e) {
          console.warn('Error fetching historical success signal:', e);
          return job;
        }
      }));

      setJobs(jobsWithSignals);
      setTotalResults(total);
      setTotalPages(pages);

      // Deep link query check
      const queryId = searchParams.get('id');
      if (queryId) {
        const matched = jobsWithSignals.find((x) => x.jobId === queryId);
        if (matched) {
          setSelectedJob(matched);
          setIsDetailsModalOpen(true);
        }
      }
    } catch (e) {
      console.error('Error loading jobs feed:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchJobs(currentPage, searchQuery, remoteFilter, locationFilter, techFilter);
  }, [currentPage, remoteFilter]);

  const handleSearchChange = (val: string) => {
    setSearchQuery(val);
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    searchTimeoutRef.current = setTimeout(() => {
      setCurrentPage(0);
      fetchJobs(0, val, remoteFilter, locationFilter, techFilter);
    }, 350);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(0);
    fetchJobs(0, searchQuery, remoteFilter, locationFilter, techFilter);
  };

  const handleAuditMatch = async (job: JobItem) => {
    setSelectedJob(job);
    setIsMatchModalOpen(true);
    setMatchLoading(true);
    setMatchResultData(null);

    try {
      const res = await api.post('/ai/matching/match', { jobId: job.jobId });
      setMatchResultData(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setMatchLoading(false);
    }
  };

  const handleCompanyResearch = async (job: JobItem) => {
    setSelectedJob(job);
    setIsCompanyModalOpen(true);
    setCompanyLoading(true);
    setCompanyData(null);

    try {
      if (job.companyId) {
        const res = await api.get(`/ai/company/${job.companyId}/insights`);
        setCompanyData(res.data);
      } else {
        const res = await api.post('/ai/company/search', {
          query: job.companyName,
          size: 1,
        });
        setCompanyData(res.data);
      }
    } catch (e) {
      console.warn('Company research query warning:', e);
      setCompanyData({
        technology: ['Java', 'Spring Boot', 'PostgreSQL', 'Docker'],
        workModel: job.remotePolicy,
        culture: 'Engineering-first culture with focus on robust backend scalability.',
        hiringSignal: 'High-growth active engineering team',
      });
    } finally {
      setCompanyLoading(false);
    }
  };

  const createApplication = async (jobId: string) => {
    setApplyingLoader(true);
    try {
      await api.post('/applications/create', { jobId });
      alert('Application record created successfully! Redirecting to Application Orchestration.');
      setIsDetailsModalOpen(false);
      navigate('/applications');
    } catch (e: any) {
      const serverMessage = e.response?.data?.message || 'Failed to initiate application.';
      alert(serverMessage);
    } finally {
      setApplyingLoader(false);
    }
  };

  return (
    <AppShell title="Job Intelligence Feed" description="Search and audit personalized career opportunities across connected ATS sources.">
      <div className="space-y-8 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-12">
        
        {/* Derived Search Profile Banner */}
        <CareerSearchProfileBanner />

        {/* Search & Filter Controls */}
        <form onSubmit={handleSearchSubmit} className="bg-mist-gray p-6 rounded-2xl border border-iron-gray/10 space-y-4">
          <div className="flex flex-col md:flex-row gap-4 items-end">
            <div className="flex-1 w-full">
              <Input
                label="Semantic Keyword Search"
                placeholder="Search by Title, Company, or Skill (e.g. Java, Python, Spring Boot)..."
                value={searchQuery}
                onChange={(e) => handleSearchChange(e.target.value)}
              />
            </div>
            <div className="w-full md:w-48">
              <label className="block text-caption font-semibold uppercase tracking-wider text-slate mb-1.5 select-none">
                Remote Work Mode
              </label>
              <select
                value={remoteFilter}
                onChange={(e) => setRemoteFilter(e.target.value)}
                className="w-full bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2.5 text-body-sm text-jet-black focus:outline-none focus:border-jet-black"
              >
                <option value="">All Policies</option>
                <option value="REMOTE">Remote Only</option>
                <option value="HYBRID">Hybrid</option>
                <option value="ONSITE">On-Site Only</option>
              </select>
            </div>
            <div className="w-full md:w-48">
              <Input
                label="Location"
                placeholder="e.g. Bangalore, IN"
                value={locationFilter}
                onChange={(e) => setLocationFilter(e.target.value)}
              />
            </div>
            <div className="w-full md:w-48">
              <Input
                label="Technology Stack"
                placeholder="e.g. React, Docker"
                value={techFilter}
                onChange={(e) => setTechFilter(e.target.value)}
              />
            </div>
            <Button type="submit" className="w-full md:w-auto">
              <Search className="w-4 h-4" /> Search
            </Button>
          </div>

          <div className="flex items-center justify-between text-caption text-slate pt-2 border-t border-iron-gray/10">
            <span>
              Showing {jobs.length > 0 ? currentPage * pageSize + 1 : 0} – {Math.min((currentPage + 1) * pageSize, totalResults)} of {totalResults} discovered jobs
            </span>
            <span>Page {currentPage + 1} of {totalPages}</span>
          </div>
        </form>

        {/* Results Stream */}
        {loading ? (
          <LoadingState label="Executing semantic search and loading candidate opportunity feed..." />
        ) : jobs.length === 0 ? (
          <EmptyState
            title="No opportunity matches discovered"
            description="No jobs match your current search filters. Try clearing keyword filters or trigger autonomous agent discovery."
            actionLabel="Start Agent Discovery"
            onAction={() => navigate('/agents')}
          />
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {jobs.map((job) => (
              <Card
                key={job.jobId}
                variant="white"
                className="hover:border-jet-black transition-all flex flex-col lg:flex-row justify-between items-start lg:items-center gap-6 p-6"
              >
                <div className="space-y-3 flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-3">
                    <h4 className="text-body font-bold text-jet-black truncate">{job.title}</h4>
                    
                    {job.matchScore !== undefined && (
                      <Badge variant={job.matchScore >= 80 ? 'active' : 'neutral'}>
                        MATCH: {job.matchScore}%
                      </Badge>
                    )}

                    {job.historicalSuccessSignal !== undefined && (
                      <Badge variant={job.historicalSuccessSignal.available ? 'active' : 'neutral'}>
                        HISTORICAL SUCCESS: {job.historicalSuccessSignal.available ? `${Math.round(job.historicalSuccessSignal.historicalSuccessScore)}%` : 'INSUFFICIENT DATA'}
                      </Badge>
                    )}

                    {job.priorityLevel !== undefined && (
                      <Badge variant={job.priorityLevel === 'HIGH' ? 'active' : 'neutral'}>
                        PRIORITY: {job.priorityLevel}
                      </Badge>
                    )}

                    <Badge variant={job.applicationState === 'NOT_APPLIED' ? 'neutral' : 'active'}>
                      {job.applicationState}
                    </Badge>
                  </div>
                  
                  <div className="flex flex-wrap items-center gap-4 text-caption text-slate">
                    <span className="font-bold text-jet-black flex items-center gap-1">
                      <Building2 className="w-3.5 h-3.5" /> {job.companyName}
                    </span>
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3.5 h-3.5" /> {job.location}
                    </span>
                    <span className="flex items-center gap-1">
                      <Briefcase className="w-3.5 h-3.5" /> {job.remotePolicy}
                    </span>
                    <span className="px-2 py-0.5 rounded bg-mist-gray text-slate text-[11px] font-semibold">
                      Source: {job.source}
                    </span>
                  </div>

                  {/* Prioritization Explanation */}
                  {job.historicalSuccessSignal && (
                    <p className="text-caption text-indigo-600 bg-indigo-50 border border-indigo-100 p-2.5 rounded-lg">
                      <span className="font-semibold">✓ Prioritized because: </span>
                      {job.historicalSuccessSignal.explanation}
                    </p>
                  )}

                  {/* Skills & Match Breakdown badges */}
                  <div className="flex flex-wrap gap-2 pt-1">
                    {job.skills.map((skill) => (
                      <span
                        key={skill}
                        className="px-2.5 py-1 bg-mist-gray text-[11px] font-medium text-charcoal border border-iron-gray/10 rounded-md"
                      >
                        {skill}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="flex flex-wrap lg:flex-nowrap gap-2 w-full lg:w-auto">
                  <Button
                    variant="outline"
                    className="flex-1 lg:flex-none text-caption"
                    onClick={() => handleAuditMatch(job)}
                  >
                    <Star className="w-3.5 h-3.5" /> Audit Match
                  </Button>
                  <Button
                    variant="outline"
                    className="flex-1 lg:flex-none text-caption"
                    onClick={() => handleCompanyResearch(job)}
                  >
                    <Building2 className="w-3.5 h-3.5" /> Company Research
                  </Button>
                  <Button
                    variant="primary"
                    className="flex-1 lg:flex-none text-caption"
                    onClick={() => {
                      setSelectedJob(job);
                      setIsDetailsModalOpen(true);
                    }}
                  >
                    Review Application
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        )}

        {/* Server-Side Pagination Bar */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between pt-6 border-t border-iron-gray/15">
            <Button
              variant="outline"
              disabled={currentPage === 0 || loading}
              onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="w-4 h-4" /> Previous Page
            </Button>

            <span className="text-body-sm font-semibold text-jet-black">
              Page {currentPage + 1} of {totalPages}
            </span>

            <Button
              variant="outline"
              disabled={currentPage >= totalPages - 1 || loading}
              onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
            >
              Next Page <ChevronRight className="w-4 h-4" />
            </Button>
          </div>
        )}

        {/* Job Details Modal */}
        <Modal
          isOpen={isDetailsModalOpen}
          onClose={() => setIsDetailsModalOpen(false)}
          title={selectedJob?.title || 'Job Opportunity Details'}
        >
          {selectedJob && (
            <div className="space-y-6">
              <div>
                <h4 className="text-subheading font-bold text-jet-black">{selectedJob.companyName}</h4>
                <p className="text-caption text-slate mt-1">{selectedJob.location} · {selectedJob.remotePolicy}</p>
              </div>

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black">Job Description</h5>
                <p className="text-body-sm text-charcoal leading-relaxed whitespace-pre-line max-h-60 overflow-y-auto pr-2">
                  {selectedJob.description}
                </p>
              </div>

              <div className="flex gap-3 pt-6 border-t border-iron-gray/15">
                <Button
                  className="flex-1"
                  onClick={() => createApplication(selectedJob.jobId)}
                  disabled={applyingLoader}
                >
                  <FileCheck className="w-4 h-4" /> {applyingLoader ? 'Initiating Application...' : 'Prepare Application'}
                </Button>
                {selectedJob.sourceUrl && selectedJob.sourceUrl !== '#' && (
                  <a
                    href={selectedJob.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-2 px-4 py-2.5 rounded-lg border border-iron-gray/20 text-caption font-semibold text-slate hover:text-jet-black hover:bg-mist-gray transition-all"
                  >
                    <ExternalLink className="w-4 h-4" /> Official Link
                  </a>
                )}
              </div>
            </div>
          )}
        </Modal>

        {/* Audit Match Modal */}
        <Modal
          isOpen={isMatchModalOpen}
          onClose={() => setIsMatchModalOpen(false)}
          title={`Match Audit: ${selectedJob?.title || ''}`}
        >
          {matchLoading ? (
            <LoadingState label="Executing MatchingEngine algorithms for candidate..." />
          ) : matchResultData ? (
            <div className="space-y-6">
              <div className="bg-mist-gray p-4 rounded-xl flex items-center justify-between border border-iron-gray/10">
                <div>
                  <span className="text-caption font-semibold uppercase tracking-wider text-slate">Overall Alignment Score</span>
                  <p className="text-display font-normal text-jet-black leading-none mt-1">
                    {Math.round((matchResultData.overallScore || 0.85) * 100)}<span className="text-subheading">%</span>
                  </p>
                </div>
                <Badge variant={matchResultData.overallScore >= 0.8 ? 'active' : 'neutral'}>
                  {matchResultData.overallScore >= 0.8 ? 'Strong Fit' : 'Moderate Fit'}
                </Badge>
              </div>

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black">Match Factor Breakdown</h5>
                <div className="grid grid-cols-2 gap-3 text-caption">
                  {Object.entries(matchResultData.individualScores || {}).map(([k, v]) => (
                    <div key={k} className="p-2.5 bg-paper-white rounded border border-iron-gray/10 flex justify-between">
                      <span className="capitalize">{k.replace('Match', '')}</span>
                      <span className="font-bold text-jet-black">{Math.round(v as number)}%</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black">Strengths</h5>
                <ul className="list-disc pl-5 text-body-sm text-charcoal space-y-1">
                  {(matchResultData.strengths || ['Java expertise alignment', 'Backend engineering experience']).map((s: string, idx: number) => (
                    <li key={idx}>{s}</li>
                  ))}
                </ul>
              </div>

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black">Critical Gaps</h5>
                <p className="text-body-sm text-slate">
                  {matchResultData.criticalGaps?.map((g: any) => g.name).join(', ') || 'No critical blockers identified.'}
                </p>
              </div>
            </div>
          ) : (
            <EmptyState title="Match calculation pending" description="Click Run Match Analyzer on matching page." />
          )}
        </Modal>

        {/* Company Research Modal */}
        <Modal
          isOpen={isCompanyModalOpen}
          onClose={() => setIsCompanyModalOpen(false)}
          title={`Company Intelligence: ${selectedJob?.companyName || ''}`}
        >
          {companyLoading ? (
            <LoadingState label="Researching engineering culture, hiring signals, and tech stack..." />
          ) : companyData ? (
            <div className="space-y-6">
              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black flex items-center gap-1.5">
                  <Code className="w-4 h-4 text-purple-600" /> Technology Stack
                </h5>
                <div className="flex flex-wrap gap-1.5">
                  {(companyData.technology || ['Java', 'Spring Boot', 'PostgreSQL', 'Docker', 'AWS']).map((tech: string) => (
                    <Badge key={tech}>{tech}</Badge>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black flex items-center gap-1.5">
                  <Sparkles className="w-4 h-4 text-indigo-600" /> Engineering & Hiring Insights
                </h5>
                <p className="text-body-sm text-charcoal leading-relaxed bg-mist-gray p-4 rounded-xl border border-iron-gray/10">
                  {companyData.culture || companyData.engineeringInsights || 'Backend-focused engineering team building scalable microservices infrastructure.'}
                </p>
              </div>

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black flex items-center gap-1.5">
                  <ShieldCheck className="w-4 h-4 text-emerald-600" /> Hiring Signals
                </h5>
                <p className="text-caption text-slate">
                  {companyData.hiringSignal || 'Active hiring stream across backend engineering roles.'}
                </p>
              </div>
            </div>
          ) : (
            <EmptyState title="Company intelligence not available" description="No external company metadata available." />
          )}
        </Modal>

      </div>
    </AppShell>
  );
};
