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

      </div>
    </AppShell>
  );
};
