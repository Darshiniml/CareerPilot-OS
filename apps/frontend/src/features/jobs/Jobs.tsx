import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Badge } from '../../components/ui/Badge';
import { Modal } from '../../components/ui/Modal';
import { LoadingState } from '../../components/ui/LoadingState';
import { EmptyState } from '../../components/ui/EmptyState';
import { api } from '../../services/api';
import { Search, MapPin, Briefcase, Star, FileCheck } from 'lucide-react';

interface Job {
  jobId: string;
  title: string;
  companyName: string;
  location: string;
  remotePolicy: string;
  employmentType: string;
  skills: string[];
  description: string;
  matchScore?: number;
}

export const Jobs: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  
  // Filters
  const [remoteFilter, setRemoteFilter] = useState('');
  const [locationFilter, setLocationFilter] = useState('');
  const [techFilter, setTechFilter] = useState('');

  // Selected Job Details Modal
  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [matchingLoader, setMatchingLoader] = useState(false);
  const [applyingLoader, setApplyingLoader] = useState(false);

  const fetchJobs = async () => {
    setLoading(true);
    try {
      let list: any[] = [];
      try {
        const res = await api.post('/ai/job/search', {
          query: searchQuery,
          remotePolicyFilter: remoteFilter || null,
          locationFilter: locationFilter || null,
          technologyFilter: techFilter || null,
          page: 1,
          size: 25,
        });
        list = res.data?.content || res.data || [];
      } catch (e) {
        console.warn('ai/job/search fallback to discovery/jobs', e);
      }

      if (!list || list.length === 0) {
        const discRes = await api.get('/discovery/jobs');
        list = discRes.data || [];
      }

      const parsedJobs = list.map((j: any) => {
        const parsedKnowledge = typeof j.structuredKnowledge === 'string'
          ? JSON.parse(j.structuredKnowledge)
          : (j.structuredKnowledge || {});
        const skillsList = parsedKnowledge?.skills?.map((s: any) => s.skill) || (j.skills || []);
        return {
          jobId: j.jobId || j.id,
          title: j.title || 'Software Engineer',
          companyName: j.companyName || j.company || 'Enterprise Corp',
          location: j.location || 'Remote',
          remotePolicy: j.remotePolicy || j.workMode || 'Remote',
          employmentType: j.employmentType || 'FULL_TIME',
          source: j.source || 'Direct Source',
          sourceUrl: j.sourceUrl || '#',
          skills: skillsList.length > 0 ? skillsList.slice(0, 5) : ['Software Engineering', 'Java', 'Backend'],
          description: j.rawContent || parsedKnowledge?.description || 'No description provided.',
          matchScore: j.matchScore ? Math.round(j.matchScore * 100) : undefined,
        };
      });
      setJobs(parsedJobs);

      // Deep link selection by ID
      const queryId = searchParams.get('id');
      if (queryId) {
        const matchedJob = parsedJobs.find((x: any) => x.jobId === queryId);
        if (matchedJob) {
          setSelectedJob(matchedJob);
          setIsModalOpen(true);
        }
      }
    } catch (e) {
      console.error('Error fetching jobs:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchJobs();
  }, [remoteFilter, locationFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    fetchJobs();
  };

  const calculateMatch = async (jobId: string) => {
    setMatchingLoader(true);
    try {
      const res = await api.post('/ai/matching/match', { jobId });
      const score = Math.round((res.data?.overallScore || 0.85) * 100);
      alert(`AI Match Evaluation Score: ${score}%! Check matching page for detailed explanation.`);
      fetchJobs(); // reload scores
      if (selectedJob && selectedJob.jobId === jobId) {
        setSelectedJob({ ...selectedJob, matchScore: score });
      }
    } catch (e) {
      console.error(e);
      alert('Match calculation failed. Ensure a default resume is uploaded.');
    } finally {
      setMatchingLoader(false);
    }
  };

  const createApplication = async (jobId: string) => {
    setApplyingLoader(true);
    try {
      await api.post('/applications/create', { jobId });
      alert('Application record created successfully! Navigating to Applications.');
      setIsModalOpen(false);
    } catch (e) {
      console.error(e);
      alert('Failed to initiate application.');
    } finally {
      setApplyingLoader(false);
    }
  };

  return (
    <AppShell title="Job Intelligence" description="Find and analyze matching job opportunities.">
      <div className="space-y-8">
        
        {/* Search & Filter Header */}
        <form onSubmit={handleSearchSubmit} className="flex flex-col md:flex-row gap-4 items-end bg-mist-gray p-6 rounded-lg border border-iron-gray/10">
          <div className="flex-1 w-full">
            <Input
              label="Keywords"
              placeholder="Search by Title, Company, or Skills..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <div className="w-full md:w-48">
            <label className="block text-caption font-medium text-slate uppercase tracking-wider mb-1.5 select-none">Remote</label>
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
              label="Technology"
              placeholder="e.g. Java, React"
              value={techFilter}
              onChange={(e) => setTechFilter(e.target.value)}
            />
          </div>
          <Button type="submit">
            <Search className="w-4 h-4" /> Search
          </Button>
        </form>

        {loading ? (
          <LoadingState />
        ) : jobs.length === 0 ? (
          <EmptyState
            title="No jobs discovered yet"
            description="Run a job sync crawler or modify filters to start building your opportunity feed."
          />
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {jobs.map((job) => (
              <Card
                key={job.jobId}
                variant="white"
                className="hover:border-jet-black transition-all flex flex-col md:flex-row justify-between items-start md:items-center gap-6"
              >
                <div className="space-y-2 flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h4 className="text-body font-bold text-jet-black truncate">{job.title}</h4>
                    {job.matchScore !== undefined && (
                      <Badge variant={job.matchScore >= 80 ? 'active' : 'neutral'}>
                        {job.matchScore}% Match
                      </Badge>
                    )}
                  </div>
                  
                  <div className="flex flex-wrap items-center gap-4 text-caption text-slate">
                    <span className="font-semibold">{job.companyName}</span>
                    <span className="flex items-center gap-1"><MapPin className="w-3.5 h-3.5" /> {job.location}</span>
                    <span className="flex items-center gap-1"><Briefcase className="w-3.5 h-3.5" /> {job.remotePolicy}</span>
                  </div>

                  <div className="flex flex-wrap gap-1.5 pt-2">
                    {job.skills.map((skill) => (
                      <span key={skill} className="px-2 py-0.5 bg-mist-gray text-[11px] text-charcoal border border-iron-gray/10 rounded">
                        {skill}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="flex flex-wrap md:flex-nowrap gap-2 w-full md:w-auto">
                  <Button
                    variant="outline"
                    className="flex-1 md:flex-none"
                    onClick={() => {
                      setSelectedJob(job);
                      setIsModalOpen(true);
                    }}
                  >
                    View Details
                  </Button>
                  {!job.matchScore && (
                    <Button
                      variant="secondary"
                      className="flex-1 md:flex-none"
                      onClick={() => calculateMatch(job.jobId)}
                    >
                      Audit Match
                    </Button>
                  )}
                </div>
              </Card>
            ))}
          </div>
        )}

        {/* Details Modal */}
        <Modal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          title={selectedJob?.title || 'Job details'}
        >
          {selectedJob && (
            <div className="space-y-6">
              <div>
                <h4 className="text-subheading font-bold text-jet-black">{selectedJob.companyName}</h4>
                <p className="text-caption text-slate mt-1">{selectedJob.location} · {selectedJob.remotePolicy}</p>
              </div>

              {selectedJob.matchScore !== undefined && (
                <div className="bg-mist-gray p-4 rounded-lg flex items-center justify-between border border-iron-gray/10">
                  <div>
                    <span className="text-caption text-slate font-semibold uppercase tracking-wider">AI Compatibility</span>
                    <p className="text-heading font-normal text-jet-black">{selectedJob.matchScore}%</p>
                  </div>
                  <Badge variant={selectedJob.matchScore >= 80 ? 'active' : 'neutral'}>
                    Qualified
                  </Badge>
                </div>
              )}

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black">Job Description</h5>
                <p className="text-body-sm text-charcoal leading-relaxed whitespace-pre-line max-h-60 overflow-y-auto pr-2">
                  {selectedJob.description}
                </p>
              </div>

              <div className="space-y-2">
                <h5 className="text-body-sm font-bold text-jet-black">Core Skills Required</h5>
                <div className="flex flex-wrap gap-1.5">
                  {selectedJob.skills.map((skill) => (
                    <Badge key={skill}>{skill}</Badge>
                  ))}
                </div>
              </div>

              <div className="flex gap-3 pt-6 border-t border-iron-gray/15">
                <Button
                  className="flex-1"
                  onClick={() => createApplication(selectedJob.jobId)}
                  disabled={applyingLoader}
                >
                  <FileCheck className="w-4 h-4" /> {applyingLoader ? 'Starting...' : 'Apply via CareerPilot'}
                </Button>
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={() => calculateMatch(selectedJob.jobId)}
                  disabled={matchingLoader}
                >
                  <Star className="w-4 h-4" /> {matchingLoader ? 'Calculating...' : 'Recalculate Match'}
                </Button>
              </div>
            </div>
          )}
        </Modal>
      </div>
    </AppShell>
  );
};
