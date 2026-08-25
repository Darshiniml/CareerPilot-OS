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

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const fetchUserContext = useUserContextStore((state) => state.fetchUserContext);
  const profile = useUserContextStore((state) => state.profile);

  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState<any>(null);
  const [health, setHealth] = useState<HealthScore | null>(null);

  const fetchDashboardData = async () => {
    setLoading(true);
    try {
      fetchUserContext();

      // Fetch dashboard summary (Milestone 21)
      try {
        const summaryRes = await api.get('/analytics/dashboard-summary');
        setSummary(summaryRes.data);
      } catch (e) {
        console.warn('Dashboard summary endpoint error:', e);
      }

      // Fetch copilot health score
      try {
        const healthRes = await api.get('/copilot/health-score');
        const data = healthRes.data;
        if (data) {
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
        }
      } catch (e) {
        setHealth(null);
      }
    } catch (err) {
      console.error('Error fetching dashboard data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  if (loading) {
    return (
      <AppShell title="Career Command Center" description="Your personal career cockpit at a glance.">
        <LoadingState label="Loading profile context, career health metrics, and job recommendations..." />
      </AppShell>
    );
  }

  const formatRate = (val: any) => {
    if (val === 'INSUFFICIENT_DATA' || val === null || val === undefined) {
      return 'INSUFFICIENT_DATA';
    }
    return `${Math.round(Number(val))}%`;
  };

  const formatIntel = (val: any) => {
    if (val === 'INSUFFICIENT_DATA' || val === null || val === undefined || val === '') {
      return <span className="text-slate font-bold">INSUFFICIENT_DATA</span>;
    }
    return <span className="font-bold text-jet-black">{val}</span>;
  };

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
            <Button variant="outline" onClick={() => navigate('/opportunities')}>
              Explore Opportunities
            </Button>
          </div>
        </section>

        {/* Career Search Profile Banner */}
        <CareerSearchProfileBanner />

        {/* Core KPIs */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Applications</span>
            <p className="text-heading font-normal text-jet-black mt-2">{summary?.totalApplications || 0}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Submitted & Verified</span>
            <p className="text-heading font-normal text-emerald-800 mt-2">{summary?.submittedCount || 0}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Interviews</span>
            <p className="text-heading font-normal text-indigo-800 mt-2">{summary?.interviewsCount || 0}</p>
          </Card>
          <Card>
            <span className="text-caption font-semibold uppercase tracking-wider text-slate">Offers</span>
            <p className="text-heading font-normal text-rose-800 mt-2">{summary?.offersCount || 0}</p>
          </Card>
        </section>

        {/* Rates row */}
        <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Card variant="white" className="border border-iron-gray/15">
            <span className="text-caption font-bold text-slate uppercase">Interview Success Rate</span>
            <p className="text-subheading font-bold text-jet-black mt-2">{formatRate(summary?.interviewRate)}</p>
            <p className="text-caption text-ash-gray mt-1">Conversion of applications to interviews (min 10 apps required)</p>
          </Card>
          <Card variant="white" className="border border-iron-gray/15">
            <span className="text-caption font-bold text-slate uppercase">Offer Success Rate</span>
            <p className="text-subheading font-bold text-jet-black mt-2">{formatRate(summary?.offerRate)}</p>
            <p className="text-caption text-ash-gray mt-1">Conversion of applications to offers (min 10 apps required)</p>
          </Card>
        </section>

        {/* Today's Actions & Career Health */}
        <section className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Career Health */}
          <div className="lg:col-span-2 space-y-4">
            <h3 className="text-heading-sm font-bold text-jet-black tracking-tight">Career Health Index</h3>
            <Card variant="white" className="border border-iron-gray/15">
              {!health ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <p className="text-body-sm font-bold text-jet-black">Upload a default resume to calculate health index</p>
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

          {/* Today's Actions */}
          <div className="space-y-4">
            <h3 className="text-heading-sm font-bold text-jet-black tracking-tight">Today's Actions</h3>
            <Card variant="white" className="border border-iron-gray/15 space-y-4">
              <div className="flex justify-between items-center">
                <span className="text-body-sm text-charcoal">High-priority Opportunities</span>
                <Badge variant="danger">{summary?.todayActions?.highPriorityOpportunities || 0}</Badge>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-body-sm text-charcoal">Applications Awaiting Approval</span>
                <Badge variant="warning">{summary?.todayActions?.waitingApprovalCount || 0}</Badge>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-body-sm text-charcoal">Manual Application Actions</span>
                <Badge variant="warning">{summary?.todayActions?.manualActionCount || 0}</Badge>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-body-sm text-charcoal">Stale Applications</span>
                <Badge variant="neutral">{summary?.todayActions?.staleApplicationsCount || 0}</Badge>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-body-sm text-charcoal">Status Updates Required</span>
                <Badge variant="neutral">{summary?.todayActions?.statusUpdatesRequiredCount || 0}</Badge>
              </div>
            </Card>
          </div>
        </section>

        {/* Career Intelligence */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-bold text-jet-black tracking-tight">Career Intelligence Insights</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card variant="white" className="border border-iron-gray/15">
              <span className="text-caption font-bold text-slate block uppercase">Best Role</span>
              <p className="text-body-sm mt-2">{formatIntel(summary?.intelligence?.bestRole)}</p>
            </Card>
            <Card variant="white" className="border border-iron-gray/15">
              <span className="text-caption font-bold text-slate block uppercase">Best Source</span>
              <p className="text-body-sm mt-2">{formatIntel(summary?.intelligence?.bestSource)}</p>
            </Card>
            <Card variant="white" className="border border-iron-gray/15">
              <span className="text-caption font-bold text-slate block uppercase">Best Work Mode</span>
              <p className="text-body-sm mt-2">{formatIntel(summary?.intelligence?.bestWorkMode)}</p>
            </Card>
            <Card variant="white" className="border border-iron-gray/15">
              <span className="text-caption font-bold text-slate block uppercase">Best Resume Version</span>
              <p className="text-body-sm mt-2">{formatIntel(summary?.intelligence?.bestResume)}</p>
            </Card>
            <Card variant="white" className="border border-iron-gray/15">
              <span className="text-caption font-bold text-slate block uppercase">Strongest Skill Correlation</span>
              <p className="text-body-sm mt-2">{formatIntel(summary?.intelligence?.strongestSkill)}</p>
            </Card>
            <Card variant="white" className="border border-iron-gray/15">
              <span className="text-caption font-bold text-slate block uppercase">Primary Skill Gap</span>
              <p className="text-body-sm mt-2">{formatIntel(summary?.intelligence?.skillGap)}</p>
            </Card>
          </div>
        </section>

      </div>
    </AppShell>
  );
};
