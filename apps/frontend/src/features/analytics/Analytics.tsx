import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { LoadingState } from '../../components/ui/LoadingState';
import { api } from '../../services/api';
import { BarChart3, TrendingUp } from 'lucide-react';

interface MetricTrend {
  metricType: string;
  period: string;
  current: number;
  previous: number;
  change: number;
}

interface SkillDemand {
  skillName: string;
  demandPercentage: number;
  isGap: boolean;
}

export const Analytics: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [trends, setTrends] = useState<MetricTrend[]>([]);
  const [skills, setSkills] = useState<SkillDemand[]>([]);
  const [overview, setOverview] = useState({
    growthScore: 70,
    matchScore: 85,
    readinessScore: 72,
    skillCoverage: 60,
  });

  // Milestone 20 states
  const [careerIntel, setCareerIntel] = useState<any>(null);
  const [rolePerf, setRolePerf] = useState<any[]>([]);
  const [skillPerf, setSkillPerf] = useState<any[]>([]);
  const [companyPerf, setCompanyPerf] = useState<any[]>([]);
  const [locationPerf, setLocationPerf] = useState<any[]>([]);
  const [remotePerf, setRemotePerf] = useState<any[]>([]);
  const [sourcePerf, setSourcePerf] = useState<any[]>([]);
  const [resumePerf, setResumePerf] = useState<any[]>([]);
  const [adaptiveInsights, setAdaptiveInsights] = useState<any[]>([]);

  const fetchAnalytics = async () => {
    try {
      // 1. Fetch overview metrics
      const overviewRes = await api.get('/analytics/overview');
      const ov = overviewRes.data;
      setOverview({
        growthScore: Math.round(ov.careerGrowthScore || 70),
        matchScore: Math.round(ov.averageMatchScore || 85),
        readinessScore: Math.round((ov.averageInterviewReadiness || 0.72) * 100),
        skillCoverage: Math.round((ov.skillCoverage || 0.6) * 100),
      });

      // 2. Fetch trends
      try {
        const trendsRes = await api.get('/analytics/trends');
        const data = trendsRes.data || {};
        const list: MetricTrend[] = [];

        if (data.growthScoreTrend) {
          list.push({
            metricType: 'growth_score',
            period: data.growthScoreTrend.period || 'MONTHLY',
            current: Math.round(data.growthScoreTrend.current || 0),
            previous: Math.round(data.growthScoreTrend.previous || 0),
            change: Math.round(data.growthScoreTrend.change || 0)
          });
        }
        if (data.matchScoreTrend) {
          list.push({
            metricType: 'match_score',
            period: data.matchScoreTrend.period || 'MONTHLY',
            current: Math.round(data.matchScoreTrend.current || 0),
            previous: Math.round(data.matchScoreTrend.previous || 0),
            change: Math.round(data.matchScoreTrend.change || 0)
          });
        }
        if (data.readinessTrend) {
          list.push({
            metricType: 'readiness_score',
            period: data.readinessTrend.period || 'MONTHLY',
            current: Math.round(data.readinessTrend.current || 0),
            previous: Math.round(data.readinessTrend.previous || 0),
            change: Math.round(data.readinessTrend.change || 0)
          });
        }

        setTrends(list);
      } catch (e) {
        // Fallback demo trends
        setTrends([
          { metricType: 'growth_score', period: 'WEEKLY', current: 70, previous: 60, change: 10 },
          { metricType: 'match_score', period: 'WEEKLY', current: 85, previous: 80, change: 5 },
          { metricType: 'readiness_score', period: 'WEEKLY', current: 72, previous: 70, change: 2 },
        ]);
      }

      // 3. Fetch skills analysis
      try {
        const skillsRes = await api.get('/analytics/skills');
        const data = skillsRes.data || {};
        const demandMap: Record<string, number> = data.demandPercentages || {};
        const missingList: string[] = data.missingSkills || [];
        
        const mappedSkills = Object.entries(demandMap).map(([sName, pct]) => ({
          skillName: sName,
          demandPercentage: Math.round(pct),
          isGap: missingList.includes((sName || '').toLowerCase()),
        }));
        setSkills(mappedSkills.slice(0, 8));
      } catch (e) {
        // Fallback demo skills
        setSkills([
          { skillName: 'Java', demandPercentage: 100, isGap: false },
          { skillName: 'AWS', demandPercentage: 80, isGap: true },
          { skillName: 'React', demandPercentage: 70, isGap: false },
          { skillName: 'Spring Boot', demandPercentage: 90, isGap: false },
          { skillName: 'Kubernetes', demandPercentage: 60, isGap: true },
        ]);
      }

      // Fetch Milestone 20 Adaptive Analytics
      try {
        const intelRes = await api.get('/analytics/career-intelligence');
        setCareerIntel(intelRes.data);
      } catch (e) {
        console.warn(e);
      }
      try {
        const rolesRes = await api.get('/analytics/role-performance');
        setRolePerf(rolesRes.data || []);
      } catch (e) {
        console.warn(e);
      }
      try {
        const skillsRes = await api.get('/analytics/skill-performance');
        setSkillPerf(skillsRes.data || []);
      } catch (e) {
        console.warn(e);
      }
      try {
        const companyRes = await api.get('/analytics/company-performance');
        setCompanyPerf(companyRes.data || []);
      } catch (e) {
        console.warn(e);
      }
      try {
        const locRes = await api.get('/analytics/location-performance');
        setLocationPerf(locRes.data?.locationPerformance || []);
        setRemotePerf(locRes.data?.remoteTypePerformance || []);
      } catch (e) {
        console.warn(e);
      }
      try {
        const sourceRes = await api.get('/analytics/source-performance');
        setSourcePerf(sourceRes.data || []);
      } catch (e) {
        console.warn(e);
      }
      try {
        const resumeRes = await api.get('/analytics/resume-performance');
        setResumePerf(resumeRes.data?.resumePerformances || resumeRes.data || []);
      } catch (e) {
        console.warn(e);
      }
      try {
        const insightsRes = await api.get('/analytics/adaptive-insights');
        setAdaptiveInsights(insightsRes.data || []);
      } catch (e) {
        console.warn(e);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAnalytics();
  }, []);

  if (loading) {
    return (
      <AppShell title="Career Analytics" description="Measure skill gaps, match trends, and target career growth.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="Career Analytics" description="Measure skill gaps, match trends, and target career growth.">
      <div className="space-y-12">
        
        {/* Upper metrics breakdown (4 grid cards) */}
        <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <Card variant="white">
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Career Growth Score</span>
            <p className="text-heading font-normal text-jet-black mt-2">{overview.growthScore}%</p>
            <div className="mt-4">
              <ProgressBar value={overview.growthScore} />
            </div>
          </Card>

          <Card variant="white">
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Average Match Index</span>
            <p className="text-heading font-normal text-jet-black mt-2">{overview.matchScore}%</p>
            <div className="mt-4">
              <ProgressBar value={overview.matchScore} />
            </div>
          </Card>

          <Card variant="white">
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Interview Readiness</span>
            <p className="text-heading font-normal text-jet-black mt-2">{overview.readinessScore}%</p>
            <div className="mt-4">
              <ProgressBar value={overview.readinessScore} />
            </div>
          </Card>

          <Card variant="white">
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Required Skill Coverage</span>
            <p className="text-heading font-normal text-jet-black mt-2">{overview.skillCoverage}%</p>
            <div className="mt-4">
              <ProgressBar value={overview.skillCoverage} />
            </div>
          </Card>
        </section>

        {/* Lower Details: Trends and Skill Demand Gaps */}
        <section className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          
          {/* Historical Trends List */}
          <div className="lg:col-span-1 space-y-4">
            <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
              <TrendingUp className="w-5 h-5 text-slate" /> Career Metrics Trends
            </h3>
            <div className="space-y-3">
              {trends.map((t, idx) => (
                <Card key={idx} variant="white" className="space-y-3">
                  <div className="flex justify-between items-center">
                    <span className="text-body-sm font-bold text-jet-black capitalize">
                      {(t.metricType || '').replace('_', ' ')}
                    </span>
                    <Badge variant={t.change >= 0 ? 'success' : 'danger'}>
                      {t.change >= 0 ? `+${t.change}%` : `${t.change}%`}
                    </Badge>
                  </div>
                  <p className="text-caption text-slate">
                    Current: {t.current}% · Previous: {t.previous}% ({(t.period || '').toLowerCase()})
                  </p>
                </Card>
              ))}
            </div>
          </div>

          {/* Skill Gaps & Market Demands */}
          <div className="lg:col-span-2 space-y-4">
            <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
              <BarChart3 className="w-5 h-5 text-slate" /> Skill Demand & Gaps Analyzer
            </h3>
            <Card variant="white" className="space-y-6">
              <div className="space-y-4">
                {skills.map((skill, idx) => (
                  <div key={idx} className="flex justify-between items-center py-2 border-b border-iron-gray/10 last:border-b-0">
                    <div className="space-y-1">
                      <span className="text-body-sm font-bold text-jet-black">{skill.skillName}</span>
                      {skill.isGap && (
                        <span className="ml-3 inline-block">
                          <Badge variant="warning">Missing Stack Gap</Badge>
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-4 w-48">
                      <ProgressBar value={skill.demandPercentage} />
                      <span className="text-caption font-semibold w-8 text-right">
                        {skill.demandPercentage}%
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          </div>

        </section>

        {/* Milestone 20 Sections — Adaptive Career Analytics & Performance */}
        <section className="space-y-8">
          <h2 className="text-heading font-bold text-jet-black tracking-tight">Adaptive Performance Analyzer</h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            
            {/* Career Outcomes Funnel */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Career Execution Funnel</h3>
              {!careerIntel ? (
                <p className="text-caption text-ash-gray">No funnel data available.</p>
              ) : (
                <div className="grid grid-cols-2 gap-4">
                  <div className="p-3 bg-mist-gray rounded-xl">
                    <p className="text-caption text-slate uppercase">Total Applications</p>
                    <p className="text-subheading font-bold">{careerIntel.totalApplications}</p>
                  </div>
                  <div className="p-3 bg-mist-gray rounded-xl">
                    <p className="text-caption text-slate uppercase">Verified Submissions</p>
                    <p className="text-subheading font-bold">{careerIntel.totalVerified}</p>
                  </div>
                  <div className="p-3 bg-mist-gray rounded-xl">
                    <p className="text-caption text-slate uppercase">Interviews Secured</p>
                    <p className="text-subheading font-bold text-indigo-600">{careerIntel.totalInterviews}</p>
                  </div>
                  <div className="p-3 bg-mist-gray rounded-xl">
                    <p className="text-caption text-slate uppercase">Offers Received</p>
                    <p className="text-subheading font-bold text-green-600">{careerIntel.totalOffers}</p>
                  </div>
                  <div className="col-span-2 p-3 bg-indigo-50 border border-indigo-100 rounded-xl flex justify-between items-center">
                    <div>
                      <p className="text-caption text-indigo-700 font-semibold uppercase">Interview & Offer Rate</p>
                      <p className="text-body-sm text-indigo-600">Avg Time: {careerIntel.averageTimeToInterview > 0 ? `${careerIntel.averageTimeToInterview.toFixed(1)}d` : '—'} to Interview</p>
                    </div>
                    <div className="text-right">
                      <p className="text-body font-bold text-indigo-700">{Math.round(careerIntel.interviewRate * 100)}% IR</p>
                      <p className="text-caption text-indigo-500">{Math.round(careerIntel.offerRate * 100)}% OR</p>
                    </div>
                  </div>
                </div>
              )}
            </Card>

            {/* Adaptive Insights Panel */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Adaptive Career Insights</h3>
              <div className="space-y-3">
                {adaptiveInsights.length === 0 ? (
                  <p className="text-caption text-ash-gray">No adaptive insights generated yet.</p>
                ) : (
                  adaptiveInsights.map((insight, idx) => (
                    <div key={idx} className="p-3 bg-mist-gray rounded-xl border border-iron-gray/10 space-y-1">
                      <div className="flex justify-between items-center">
                        <span className="text-body-sm font-bold text-jet-black">{insight.title}</span>
                        <Badge variant={insight.confidenceStatus === 'SUFFICIENT' ? 'success' : 'neutral'}>
                          {insight.confidenceStatus}
                        </Badge>
                      </div>
                      <p className="text-body-sm text-charcoal">{insight.explanation}</p>
                      <p className="text-caption text-slate italic">Evidence: {insight.evidence}</p>
                    </div>
                  ))
                )}
              </div>
            </Card>

            {/* Role Performance */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Role Category Performance</h3>
              <div className="space-y-3 max-h-72 overflow-y-auto">
                {rolePerf.length === 0 ? (
                  <p className="text-caption text-ash-gray">No role performance data recorded.</p>
                ) : (
                  rolePerf.map((rp, idx) => (
                    <div key={idx} className="flex flex-col border-b border-iron-gray/10 last:border-b-0 pb-2 last:pb-0">
                      <div className="flex justify-between items-start">
                        <span className="text-body-sm font-bold text-jet-black">{rp.roleName}</span>
                        <span>
                          {rp.confidenceStatus === 'SUFFICIENT' ? (
                            <span className="text-body-sm font-semibold text-indigo-600">{Math.round(rp.interviewRate * 100)}% IR</span>
                          ) : (
                            <Badge variant="neutral">INSUFFICIENT_DATA</Badge>
                          )}
                        </span>
                      </div>
                      <span className="text-caption text-slate">{rp.applications} apps ({rp.interviews} interviews, {rp.offers} offers, {rp.rejections} rejections)</span>
                    </div>
                  ))
                )}
              </div>
            </Card>

            {/* Skill Success Outcome Correlation */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Skill Outcome Associations</h3>
              <div className="space-y-3 max-h-72 overflow-y-auto">
                {skillPerf.length === 0 ? (
                  <p className="text-caption text-ash-gray">No skill outcome association data recorded.</p>
                ) : (
                  skillPerf.map((sp, idx) => (
                    <div key={idx} className="flex justify-between items-center py-1.5 border-b border-iron-gray/10 last:border-b-0">
                      <span className="text-body-sm font-bold text-jet-black">{sp.skillName}</span>
                      <div className="flex items-center gap-3">
                        <span className="text-caption text-slate">{sp.applications} apps</span>
                        {sp.confidenceStatus === 'SUFFICIENT' ? (
                          <span className="text-body-sm font-semibold text-indigo-600">{Math.round(sp.interviewRate * 100)}% IR</span>
                        ) : (
                          <Badge variant="neutral">INSUFFICIENT</Badge>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </Card>

            {/* Company Performance */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Company Performance Breakdown</h3>
              <div className="space-y-3 max-h-72 overflow-y-auto">
                {companyPerf.length === 0 ? (
                  <p className="text-caption text-ash-gray">No company performance data recorded.</p>
                ) : (
                  companyPerf.map((cp, idx) => (
                    <div key={idx} className="flex flex-col border-b border-iron-gray/10 last:border-b-0 pb-2 last:pb-0">
                      <div className="flex justify-between">
                        <span className="text-body-sm font-semibold">{cp.companyName}</span>
                        <span>
                          {cp.confidenceStatus === 'SUFFICIENT' ? (
                            <span className="text-body-sm font-semibold text-indigo-600">{Math.round(cp.interviewRate * 100)}% IR</span>
                          ) : (
                            <Badge variant="neutral">INSUFFICIENT</Badge>
                          )}
                        </span>
                      </div>
                      <span className="text-caption text-slate">{cp.applications} apps ({cp.interviews} interviews, {cp.offers} offers)</span>
                    </div>
                  ))
                )}
              </div>
            </Card>

            {/* Source Performance */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Discovery Source Performance</h3>
              <div className="space-y-3 max-h-72 overflow-y-auto">
                {sourcePerf.length === 0 ? (
                  <p className="text-caption text-ash-gray">No source performance data recorded.</p>
                ) : (
                  sourcePerf.map((sp, idx) => (
                    <div key={idx} className="flex flex-col border-b border-iron-gray/10 last:border-b-0 pb-2 last:pb-0">
                      <div className="flex justify-between">
                        <span className="text-body-sm font-semibold capitalize">{sp.source}</span>
                        <span>
                          {sp.confidenceStatus === 'SUFFICIENT' ? (
                            <span className="text-body-sm font-semibold text-indigo-600">{Math.round(sp.interviewRate * 100)}% IR</span>
                          ) : (
                            <Badge variant="neutral">INSUFFICIENT</Badge>
                          )}
                        </span>
                      </div>
                      <span className="text-caption text-slate">{sp.applications} apps ({sp.interviews} interviews, {sp.offers} offers)</span>
                    </div>
                  ))
                )}
              </div>
            </Card>

            {/* Location & Remote Type Performance */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Location & Remote Performance</h3>
              <div className="space-y-4">
                <div>
                  <span className="text-caption font-semibold uppercase text-slate">Location Analytics</span>
                  <div className="space-y-2 mt-2">
                    {locationPerf.length === 0 ? (
                      <p className="text-caption text-ash-gray">No location data.</p>
                    ) : (
                      locationPerf.map((lp, idx) => (
                        <div key={idx} className="flex justify-between text-body-sm">
                          <span>{lp.location}</span>
                          <span className="font-semibold">{lp.confidenceStatus === 'SUFFICIENT' ? `${Math.round(lp.interviewRate * 100)}% IR` : 'INSUFFICIENT'}</span>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                <div>
                  <span className="text-caption font-semibold uppercase text-slate">Remote Style Analytics</span>
                  <div className="space-y-2 mt-2">
                    {remotePerf.length === 0 ? (
                      <p className="text-caption text-ash-gray">No remote style data.</p>
                    ) : (
                      remotePerf.map((rp, idx) => (
                        <div key={idx} className="flex justify-between text-body-sm">
                          <span>{rp.workMode}</span>
                          <span className="font-semibold">{rp.confidenceStatus === 'SUFFICIENT' ? `${Math.round(rp.interviewRate * 100)}% IR` : 'INSUFFICIENT'}</span>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </Card>

            {/* Resume Performance */}
            <Card variant="white" className="space-y-4">
              <h3 className="text-body font-bold text-jet-black">Resume Version Comparison</h3>
              <div className="space-y-3">
                {resumePerf.length === 0 ? (
                  <p className="text-caption text-ash-gray">No resume comparison data recorded.</p>
                ) : (
                  resumePerf.map((rp, idx) => (
                    <div key={idx} className="flex flex-col border-b border-iron-gray/10 last:border-b-0 pb-2 last:pb-0">
                      <div className="flex justify-between">
                        <span className="text-body-sm font-semibold truncate max-w-[200px]">{rp.title}</span>
                        <span>
                          {rp.confidenceStatus === 'SUFFICIENT' ? (
                            <span className="text-body-sm font-semibold text-indigo-600">{Math.round(rp.interviewRate * 100)}% IR</span>
                          ) : (
                            <Badge variant="neutral">INSUFFICIENT</Badge>
                          )}
                        </span>
                      </div>
                      <span className="text-caption text-slate">{rp.applications} applications ({rp.interviews} interviews, {rp.offers} offers)</span>
                    </div>
                  ))
                )}
              </div>
            </Card>

          </div>
        </section>

      </div>
    </AppShell>
  );
};
