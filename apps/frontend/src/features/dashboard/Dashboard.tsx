import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { LoadingState } from '../../components/ui/LoadingState';
import { CareerSearchProfileBanner } from '../../components/ui/CareerSearchProfileBanner';
import { useUserContextStore } from '../../store/userContextStore';
import { api } from '../../services/api';
import { FileText, Briefcase, Award, Send, ArrowRight } from 'lucide-react';

interface HealthScore {
  overallScore: number;
  categoryScores: {
    profileCompleteness: number;
    resumeQuality: number;
    skillAlignment: number;
    interviewReadiness: number;
    applicationActivity: number;
  };
}

interface RecommendedJob {
  jobId: string;
  title: string;
  companyName: string;
  location: string;
  remotePolicy: string;
  matchScore: number;
  skills: string[];
}

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const fetchUserContext = useUserContextStore((state) => state.fetchUserContext);
  const profile = useUserContextStore((state) => state.profile);

  const [loading, setLoading] = useState(true);
  const [kpis, setKpis] = useState({
    resumes: 0,
    jobs: 0,
    applications: 0,
    avgMatch: 0,
  });
  const [health, setHealth] = useState<HealthScore | null>(null);
  const [jobs, setJobs] = useState<RecommendedJob[]>([]);
  
  // Milestone 20 states
  const [careerIntel, setCareerIntel] = useState<any>(null);
  const [rolePerf, setRolePerf] = useState<any[]>([]);
  const [skillPerf, setSkillPerf] = useState<any[]>([]);
  const [sourcePerf, setSourcePerf] = useState<any[]>([]);
  const [resumePerf, setResumePerf] = useState<any[]>([]);

  useEffect(() => {
    fetchUserContext();

    const fetchDashboardData = async () => {
      setLoading(true);
      try {
        // Fetch resumes count
        let resumesCount = 0;
        try {
          const resumesRes = await api.get('/resumes');
          resumesCount = resumesRes.data?.length || 0;
        } catch (e) {
          console.warn('Resumes endpoint error:', e);
        }

        // Fetch applications count
        let appsCount = 0;
        try {
          const appsRes = await api.get('/applications');
          appsCount = appsRes.data?.length || 0;
        } catch (e) {
          console.warn('Applications endpoint error:', e);
        }

        // Fetch analytics overview
        let avgMatch = 0;
        try {
          const overviewRes = await api.get('/analytics/overview');
          avgMatch = Math.round(overviewRes.data?.averageMatchScore || 0);
        } catch (e) {
          console.warn('Analytics overview not ready yet');
        }

        // Fetch discovered jobs
        let discoveredJobsCount = 0;
        let recommendedJobs: RecommendedJob[] = [];
        try {
          const jobsRes = await api.post('/ai/job/search', { size: 10 });
          const jobsList = jobsRes.data?.content || jobsRes.data || [];
          discoveredJobsCount = jobsList.length;

          recommendedJobs = jobsList.slice(0, 3).map((j: any) => {
            const parsedKnowledge = typeof j.structuredKnowledge === 'string'
              ? JSON.parse(j.structuredKnowledge)
              : j.structuredKnowledge;
            const skillsList = parsedKnowledge?.skills?.map((s: any) => s.skill) || (j.skills || []);
            return {
              jobId: j.jobId || j.id,
              title: j.title || 'Software Engineer',
              companyName: j.companyName || j.company || 'Technology Partner',
              location: j.location || 'Remote',
              remotePolicy: j.remotePolicy || 'REMOTE',
              matchScore: Math.round((j.matchScore || 0.85) * 100),
              skills: skillsList.length > 0 ? skillsList.slice(0, 4) : ['Java', 'Spring Boot', 'SQL'],
            };
          });
        } catch (e) {
          console.warn('Jobs list query error:', e);
        }

        setKpis({
          resumes: resumesCount,
          jobs: discoveredJobsCount,
          applications: appsCount,
          avgMatch: avgMatch,
        });

        // Fetch copilot health score
        try {
          const healthRes = await api.get('/copilot/health-score');
          const data = healthRes.data;
          if (data && resumesCount > 0) {
            setHealth({
              overallScore: Math.round((data.overallScore || 0) * 100),
              categoryScores: {
                profileCompleteness: Math.round((data.categoryScores?.profileCompleteness || 0) * 100),
                resumeQuality: Math.round((data.categoryScores?.resumeQuality || 0) * 100),
                skillAlignment: Math.round((data.categoryScores?.skillAlignment || 0) * 100),
                interviewReadiness: Math.round((data.categoryScores?.interviewReadiness || 0) * 100),
                applicationActivity: Math.round((data.categoryScores?.applicationActivity || 0) * 100),
              },
            });
          } else {
            setHealth(null);
          }
        } catch (e) {
          setHealth(null);
        }

        // Fetch career intelligence data
        try {
          const intelRes = await api.get('/analytics/career-intelligence');
          setCareerIntel(intelRes.data);
        } catch (e) {
          console.warn('Career intelligence query error:', e);
        }

        try {
          const rolesRes = await api.get('/analytics/role-performance');
          setRolePerf(rolesRes.data || []);
        } catch (e) {
          console.warn('Role performance query error:', e);
        }

        try {
          const skillsRes = await api.get('/analytics/skill-performance');
          setSkillPerf(skillsRes.data || []);
        } catch (e) {
          console.warn('Skill performance query error:', e);
        }

        try {
          const sourcesRes = await api.get('/analytics/source-performance');
          setSourcePerf(sourcesRes.data || []);
        } catch (e) {
          console.warn('Source performance query error:', e);
        }

        try {
          const resumesRes = await api.get('/analytics/resume-performance');
          setResumePerf(resumesRes.data?.resumePerformances || resumesRes.data || []);
        } catch (e) {
          console.warn('Resume performance query error:', e);
        }

        setJobs(recommendedJobs);
      } catch (err) {
        console.error('Error fetching dashboard data:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

  if (loading) {
    return (
      <AppShell title="Career Command Center" description="Your personal career cockpit at a glance.">
        <LoadingState label="Loading profile context, career health metrics, and job recommendations..." />
      </AppShell>
    );
  }

  return (
    <AppShell title="Career Command Center" description="Your personal career cockpit at a glance.">
      <div className="space-y-8 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-12">
        
        {/* Welcome Section */}
        <section className="flex flex-col md:flex-row md:items-center justify-between gap-6 pb-6 border-b border-iron-gray/10">
          <div className="space-y-1">
            <h2 className="text-heading font-bold text-jet-black tracking-tight leading-none">
              Welcome back{profile?.firstName ? `, ${profile.firstName}` : ''}.
            </h2>
            <p className="text-body text-slate max-w-2xl">
              Your career cockpit is fully synchronized with live job intelligence and autonomous agent engines.
            </p>
          </div>
          <div className="flex gap-3">
            <Button onClick={() => navigate('/resumes')}>
              Upload Resume
            </Button>
            <Button variant="outline" onClick={() => navigate('/jobs')}>
              Explore Jobs
            </Button>
          </div>
        </section>

        {/* Career Search Profile Banner */}
        <CareerSearchProfileBanner />

        {/* Career Overview KPI Cards */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card>
            <div className="flex justify-between items-start">
              <span className="text-caption font-semibold uppercase tracking-wider text-slate">Resumes Stored</span>
              <FileText className="w-4 h-4 text-iron-gray" />
            </div>
            <p className="text-heading font-normal text-jet-black mt-2">{kpis.resumes}</p>
            <p className="text-caption text-ash-gray mt-1">Manage CV versions</p>
          </Card>

          <Card>
            <div className="flex justify-between items-start">
              <span className="text-caption font-semibold uppercase tracking-wider text-slate">Jobs Discovered</span>
              <Briefcase className="w-4 h-4 text-iron-gray" />
            </div>
            <p className="text-heading font-normal text-jet-black mt-2">{kpis.jobs}</p>
            <p className="text-caption text-ash-gray mt-1">Tailored opportunities feed</p>
          </Card>

          <Card>
            <div className="flex justify-between items-start">
              <span className="text-caption font-semibold uppercase tracking-wider text-slate">Match Avg</span>
              <Award className="w-4 h-4 text-iron-gray" />
            </div>
            <p className="text-heading font-normal text-jet-black mt-2">
              {kpis.resumes > 0 && kpis.avgMatch > 0 ? `${kpis.avgMatch}%` : '—'}
            </p>
            <p className="text-caption text-ash-gray mt-1">Average alignment with jobs</p>
          </Card>

          <Card>
            <div className="flex justify-between items-start">
              <span className="text-caption font-semibold uppercase tracking-wider text-slate">Applications</span>
              <Send className="w-4 h-4 text-iron-gray" />
            </div>
            <p className="text-heading font-normal text-jet-black mt-2">{kpis.applications}</p>
            <p className="text-caption text-ash-gray mt-1">Active candidate workflows</p>
          </Card>
        </section>

        {/* Career Health & Quick Operations */}
        <section className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2 space-y-4">
            <h3 className="text-heading-sm font-bold text-jet-black tracking-tight">Career Health Index</h3>
            <Card variant="white">
              {!health ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <p className="text-body-sm font-bold text-jet-black">Upload a default resume to calculate health index</p>
                  <p className="text-caption text-slate mt-1 max-w-sm">
                    AI analysis will compute Profile Completeness, Skill Alignment, and ATS Readiness.
                  </p>
                </div>
              ) : (
                <>
                  <div className="flex items-end justify-between pb-4 border-b border-iron-gray/10 mb-6">
                    <div>
                      <p className="text-caption text-slate uppercase tracking-wider font-semibold">Overall Index</p>
                      <p className="text-display font-normal text-jet-black leading-none mt-1">
                        {health.overallScore}<span className="text-subheading text-slate">/100</span>
                      </p>
                    </div>
                    <div className="w-1/2">
                      <ProgressBar value={health.overallScore} />
                    </div>
                  </div>

                  <div className="space-y-4">
                    <div className="flex justify-between items-center">
                      <span className="text-body-sm text-charcoal">Profile Completeness</span>
                      <div className="flex items-center gap-3 w-40">
                        <ProgressBar value={health.categoryScores.profileCompleteness} />
                        <span className="text-caption font-semibold w-8 text-right">{health.categoryScores.profileCompleteness}%</span>
                      </div>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-body-sm text-charcoal">Resume Quality</span>
                      <div className="flex items-center gap-3 w-40">
                        <ProgressBar value={health.categoryScores.resumeQuality} />
                        <span className="text-caption font-semibold w-8 text-right">{health.categoryScores.resumeQuality}%</span>
                      </div>
                    </div>

                    <div className="flex justify-between items-center">
                      <span className="text-body-sm text-charcoal">Skill Alignment</span>
                      <div className="flex items-center gap-3 w-40">
                        <ProgressBar value={health.categoryScores.skillAlignment} />
                        <span className="text-caption font-semibold w-8 text-right">{health.categoryScores.skillAlignment}%</span>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </Card>
          </div>

          <div className="space-y-4">
            <h3 className="text-heading-sm font-bold text-jet-black tracking-tight">Quick Operations</h3>
            <div className="flex flex-col gap-3">
              <button
                onClick={() => navigate('/resumes')}
                className="w-full flex justify-between items-center p-4 bg-mist-gray hover:bg-iron-gray/10 rounded-xl border border-iron-gray/15 text-left transition-all"
              >
                <div>
                  <p className="text-body-sm font-bold text-jet-black">Optimize Resume</p>
                  <p className="text-caption text-slate">Analyze ATS score & match quality</p>
                </div>
                <ArrowRight className="w-4 h-4 text-slate" />
              </button>

              <button
                onClick={() => navigate('/matching')}
                className="w-full flex justify-between items-center p-4 bg-mist-gray hover:bg-iron-gray/10 rounded-xl border border-iron-gray/15 text-left transition-all"
              >
                <div>
                  <p className="text-body-sm font-bold text-jet-black">Calculate Skill Gaps</p>
                  <p className="text-caption text-slate">Audit tech stack against market demand</p>
                </div>
                <ArrowRight className="w-4 h-4 text-slate" />
              </button>

              <button
                onClick={() => navigate('/agents')}
                className="w-full flex justify-between items-center p-4 bg-mist-gray hover:bg-iron-gray/10 rounded-xl border border-iron-gray/15 text-left transition-all"
              >
                <div>
                  <p className="text-body-sm font-bold text-jet-black">Automation Agents</p>
                  <p className="text-caption text-slate">Orchestrate job discovery & matching</p>
                </div>
                <ArrowRight className="w-4 h-4 text-slate" />
              </button>
            </div>
          </div>
        </section>

        {/* Milestone 20 — Adaptive Career Intelligence Dashboard Section */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-bold text-jet-black tracking-tight">Adaptive Career Intelligence</h3>
          
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Career Health Panel */}
            <Card variant="white" className="space-y-4">
              <h4 className="text-body-sm font-bold text-jet-black">Career History & Outcomes</h4>
              {(!careerIntel || careerIntel.totalApplications < 10) ? (
                <div className="py-6 text-center">
                  <p className="text-body-sm text-slate">Historical Signal: <Badge variant="neutral">INSUFFICIENT_DATA</Badge></p>
                  <p className="text-caption text-ash-gray mt-2">Apply and record outcomes for at least 10 jobs to build career outcomes model (currently: {careerIntel?.totalApplications || 0}/10).</p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex justify-between">
                    <span className="text-body-sm text-slate">Applications / Submissions</span>
                    <span className="text-body-sm font-semibold">{careerIntel.totalApplications}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-body-sm text-slate">Interviews Secured</span>
                    <span className="text-body-sm font-semibold">{careerIntel.totalInterviews}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-body-sm text-slate">Offers Received</span>
                    <span className="text-body-sm font-semibold text-green-600">{careerIntel.totalOffers}</span>
                  </div>
                  <div className="flex justify-between border-t border-iron-gray/10 pt-2">
                    <span className="text-body-sm font-bold">Interview Conversion Rate</span>
                    <span className="text-body-sm font-bold text-indigo-600">{Math.round(careerIntel.interviewRate * 100)}%</span>
                  </div>
                </div>
              )}
            </Card>

            {/* Performance Dimensions Panel */}
            <Card variant="white" className="space-y-4">
              <h4 className="text-body-sm font-bold text-jet-black">Top Performing Dimensions</h4>
              <div className="space-y-3">
                
                {/* Roles */}
                <div>
                  <span className="text-caption font-semibold uppercase tracking-wider text-slate">Top Roles</span>
                  {rolePerf.filter(r => r.confidenceStatus === 'SUFFICIENT').length === 0 ? (
                    <p className="text-caption text-ash-gray mt-0.5">Roles: <Badge variant="neutral">INSUFFICIENT_DATA</Badge></p>
                  ) : (
                    <ul className="text-body-sm space-y-1 mt-1">
                      {rolePerf.filter(r => r.confidenceStatus === 'SUFFICIENT')
                        .sort((a,b) => b.interviewRate - a.interviewRate).slice(0, 2).map((rp, i) => (
                          <li key={i} className="flex justify-between">
                            <span>{rp.roleName}</span>
                            <span className="font-semibold text-indigo-600">{Math.round(rp.interviewRate * 100)}% IR</span>
                          </li>
                      ))}
                    </ul>
                  )}
                </div>

                {/* Skills */}
                <div>
                  <span className="text-caption font-semibold uppercase tracking-wider text-slate">Top Success-Correlated Skills</span>
                  {skillPerf.filter(s => s.confidenceStatus === 'SUFFICIENT').length === 0 ? (
                    <p className="text-caption text-ash-gray mt-0.5">Skills: <Badge variant="neutral">INSUFFICIENT_DATA</Badge></p>
                  ) : (
                    <div className="flex flex-wrap gap-1.5 mt-1">
                      {skillPerf.filter(s => s.confidenceStatus === 'SUFFICIENT')
                        .sort((a,b) => b.interviewRate - a.interviewRate).slice(0, 3).map((sp, i) => (
                          <Badge key={i} variant="active">{sp.skillName}</Badge>
                      ))}
                    </div>
                  )}
                </div>

                {/* Sources */}
                <div>
                  <span className="text-caption font-semibold uppercase tracking-wider text-slate">Top Job Sources</span>
                  {sourcePerf.filter(s => s.confidenceStatus === 'SUFFICIENT').length === 0 ? (
                    <p className="text-caption text-ash-gray mt-0.5">Sources: <Badge variant="neutral">INSUFFICIENT_DATA</Badge></p>
                  ) : (
                    <ul className="text-body-sm space-y-1 mt-1">
                      {sourcePerf.filter(s => s.confidenceStatus === 'SUFFICIENT')
                        .sort((a,b) => b.interviewRate - a.interviewRate).slice(0, 2).map((sp, i) => (
                          <li key={i} className="flex justify-between">
                            <span>{sp.source}</span>
                            <span className="font-semibold text-indigo-600">{Math.round(sp.interviewRate * 100)}% IR</span>
                          </li>
                      ))}
                    </ul>
                  )}
                </div>

              </div>
            </Card>

            {/* Resume Performance Panel */}
            <Card variant="white" className="space-y-4">
              <h4 className="text-body-sm font-bold text-jet-black">Resume Version Performance</h4>
              {resumePerf.length === 0 ? (
                <p className="text-caption text-ash-gray">No resume version data collected.</p>
              ) : (
                <div className="space-y-3">
                  {resumePerf.map((rp, i) => (
                    <div key={i} className="flex flex-col border-b border-iron-gray/10 last:border-b-0 pb-2 last:pb-0">
                      <div className="flex justify-between">
                        <span className="text-body-sm font-semibold truncate max-w-[180px]">{rp.title}</span>
                        <span className="text-caption">
                          {rp.confidenceStatus === 'SUFFICIENT' ? (
                            <span className="font-semibold text-indigo-600">{Math.round(rp.interviewRate * 100)}% IR</span>
                          ) : (
                            <Badge variant="neutral">INSUFFICIENT</Badge>
                          )}
                        </span>
                      </div>
                      <span className="text-caption text-ash-gray">{rp.applications} applications ({rp.interviews} interviews, {rp.offers || 0} offers)</span>
                    </div>
                  ))}
                </div>
              )}
            </Card>

          </div>
        </section>

        {/* Recommended Opportunities */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-heading-sm font-bold text-jet-black tracking-tight">Top Matched Opportunities</h3>
            <Button variant="outline" onClick={() => navigate('/jobs')}>
              All Discovered Jobs
            </Button>
          </div>

          {jobs.length === 0 ? (
            <Card className="text-center py-12">
              <p className="text-body-sm text-slate">No recommended jobs discovered yet.</p>
              <Button className="mt-4 mx-auto" onClick={() => navigate('/jobs')}>
                Search Jobs
              </Button>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {jobs.map((job) => (
                <Card key={job.jobId} variant="white" className="flex flex-col justify-between min-h-[220px]">
                  <div>
                    <div className="flex justify-between items-start gap-4">
                      <div>
                        <h4 className="text-body-sm font-bold text-jet-black tracking-tight leading-tight">
                          {job.title}
                        </h4>
                        <p className="text-caption text-slate mt-0.5">
                          {job.companyName}
                        </p>
                      </div>
                      <Badge variant={job.matchScore >= 80 ? 'active' : 'neutral'}>
                        {job.matchScore}% Match
                      </Badge>
                    </div>

                    <p className="text-caption text-slate mt-2">
                      {job.location} · {job.remotePolicy}
                    </p>

                    <div className="flex flex-wrap gap-1.5 mt-4">
                      {job.skills.map((skill) => (
                        <span
                          key={skill}
                          className="px-2 py-0.5 bg-mist-gray text-[11px] font-medium text-charcoal rounded border border-iron-gray/10"
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  </div>

                  <Button
                    variant="secondary"
                    className="w-full mt-6"
                    onClick={() => navigate(`/jobs?id=${job.jobId}`)}
                  >
                    View Opportunity Details
                  </Button>
                </Card>
              ))}
            </div>
          )}
        </section>

      </div>
    </AppShell>
  );
};
