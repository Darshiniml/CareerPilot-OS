import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { LoadingState } from '../../components/ui/LoadingState';
import { EmptyState } from '../../components/ui/EmptyState';
import { api } from '../../services/api';
import { AlertTriangle, CheckCircle, HelpCircle, Save, RotateCcw, Sliders } from 'lucide-react';

interface Weights {
  skillMatch: number;
  experienceMatch: number;
  projectMatch: number;
  technologyMatch: number;
  locationMatch: number;
  salaryMatch: number;
  cultureMatch: number;
  educationMatch: number;
  certificationMatch: number;
  responsibilityMatch: number;
  industryMatch: number;
  remotePreferenceMatch: number;
  employmentTypeMatch: number;
  careerGrowthMatch: number;
  learningOpportunityMatch: number;
}

interface MatchExplanation {
  overallScore: number;
  individualScores: Record<string, number>;
  strengths: string[];
  weaknesses: string[];
  criticalGaps: Array<{ name: string; type: string; severity?: string }>;
  recommendations: Array<{ title: string; action: string; reason?: string }>;
}

export const Matching: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [weights, setWeights] = useState<Weights | null>(null);
  const [selectedJobId, setSelectedJobId] = useState<string>('');
  const [jobs, setJobs] = useState<Array<{ jobId: string; title: string; companyName: string }>>([]);
  const [matchResult, setMatchResult] = useState<MatchExplanation | null>(null);

  const fetchJobsAndWeights = async () => {
    try {
      const weightsRes = await api.get('/ai/matching/weights');
      setWeights(weightsRes.data);

      const jobsRes = await api.post('/ai/job/search', { limit: 10 });
      const list = jobsRes.data?.content || jobsRes.data || [];
      setJobs(
        list.map((j: any) => ({
          jobId: j.jobId || j.id,
          title: j.title || 'Engineer',
          companyName: j.companyName || j.company || 'Enterprise',
        }))
      );
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchJobsAndWeights();
  }, []);

  const triggerMatchEvaluation = async () => {
    if (!selectedJobId) return;
    setLoading(true);
    try {
      const res = await api.post('/ai/matching/match', { jobId: selectedJobId });
      const data = res.data;
      
      // Fetch Gaps
      const gapsRes = await api.post('/ai/matching/gap-analysis', { jobId: selectedJobId });
      
      // Fetch Recommendations
      const recsRes = await api.post('/ai/matching/recommendations', { jobId: selectedJobId });

      setMatchResult({
        overallScore: Math.round((data.overallScore || 0.82) * 100),
        individualScores: Object.entries(data.individualScores || {}).reduce((acc, [k, v]) => {
          acc[k] = Math.round((v as number));
          return acc;
        }, {} as Record<string, number>),
        strengths: data.strengths || [],
        weaknesses: data.weaknesses || [],
        criticalGaps: gapsRes.data || [],
        recommendations: recsRes.data || [],
      });
    } catch (e) {
      console.error(e);
      alert('Match failed. Verify you have a default resume.');
    } finally {
      setLoading(false);
    }
  };

  const isDefaultConfig = (w: Weights) => {
    const defaults: Record<string, number> = {
      skillMatch: 0.30,
      experienceMatch: 0.20,
      projectMatch: 0.10,
      technologyMatch: 0.15,
      locationMatch: 0.05,
      salaryMatch: 0.05,
      cultureMatch: 0.05,
      educationMatch: 0.05,
      certificationMatch: 0.05,
      responsibilityMatch: 0.05,
      industryMatch: 0.05,
      remotePreferenceMatch: 0.05,
      employmentTypeMatch: 0.05,
      careerGrowthMatch: 0.05,
      learningOpportunityMatch: 0.05,
    };
    
    return Object.entries(defaults).every(([k, v]) => {
      const val = w[k as keyof Weights];
      return val !== undefined && Math.abs(val - v) < 0.001;
    });
  };

  const handleWeightChange = (key: keyof Weights, val: number) => {
    if (!weights) return;
    setWeights({ ...weights, [key]: val });
  };

  const saveWeights = async () => {
    if (!weights) return;
    
    // Validate weights sum is approximately 1.0 (100%)
    const total = Object.values(weights).reduce((sum, w) => sum + (w || 0), 0);
    if (Math.abs(total - 1.0) > 0.1) {
      alert(`Validation error: Weights must sum to approximately 100%. Current sum: ${Math.round(total * 100)}%. Please adjust sliders before saving.`);
      return;
    }

    setSaving(true);
    try {
      await api.post('/ai/matching/weights', weights);
      alert('Match weights updated successfully!');
      // Refresh configurations from backend
      const weightsRes = await api.get('/ai/matching/weights');
      setWeights(weightsRes.data);
      if (selectedJobId) triggerMatchEvaluation();
    } catch (e: any) {
      console.error(e);
      const serverMessage = e.response?.data?.message || e.message;
      alert(`Failed to save weights: ${serverMessage}`);
    } finally {
      setSaving(false);
    }
  };

  const resetWeights = async () => {
    setSaving(true);
    try {
      await api.post('/ai/matching/weights/reset');
      const weightsRes = await api.get('/ai/matching/weights');
      setWeights(weightsRes.data);
      alert('Reset to default weights!');
      if (selectedJobId) triggerMatchEvaluation();
    } catch (e) {
      console.error(e);
    } finally {
      setSaving(false);
    }
  };

  if (loading && !weights) {
    return (
      <AppShell title="AI Match Analytics" description="Understand scoring breakdowns and adjust algorithm parameters.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="AI Match Analytics" description="Understand scoring breakdowns and adjust algorithm parameters.">
      <div className="space-y-12">
        {/* Evaluator Trigger Header */}
        <section className="bg-mist-gray p-6 rounded-lg border border-iron-gray/10 flex flex-col md:flex-row gap-4 items-end">
          <div className="flex-1 w-full">
            <label className="block text-caption font-semibold uppercase tracking-wider text-slate mb-1.5 select-none">
              Evaluate Opportunity Match
            </label>
            <select
              value={selectedJobId}
              onChange={(e) => setSelectedJobId(e.target.value)}
              className="w-full bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2.5 text-body-sm text-jet-black focus:outline-none focus:border-jet-black"
            >
              <option value="">Select Opportunity to Evaluate...</option>
              {jobs.map((job) => (
                <option key={job.jobId} value={job.jobId}>
                  {job.title} — {job.companyName}
                </option>
              ))}
            </select>
          </div>
          <Button onClick={triggerMatchEvaluation} disabled={!selectedJobId || loading}>
            Run Match Analyzer
          </Button>
        </section>

        {loading ? (
          <LoadingState />
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            
            {/* Left factor sliders & configurations */}
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
                  <Sliders className="w-4 h-4" /> Match Weights
                </h3>
                <button onClick={resetWeights} className="text-caption text-slate hover:text-jet-black flex items-center gap-1">
                  <RotateCcw className="w-3.5 h-3.5" /> Reset
                </button>
              </div>

              {weights && (
                <Card variant="white" className="space-y-5">
                  {/* Configuration Source status */}
                  <div className="bg-mist-gray p-3.5 rounded border border-iron-gray/10 text-left select-none space-y-2 text-caption">
                    <div>
                      <span className="text-slate font-semibold uppercase tracking-wider block">Configuration Source</span>
                      <span className="text-jet-black font-bold">{isDefaultConfig(weights) ? 'System Default' : 'Custom User Weights'}</span>
                    </div>
                    <div>
                      <span className="text-slate font-semibold uppercase tracking-wider block">Active Strategy</span>
                      <span className="text-jet-black font-bold">Standard Weights</span>
                    </div>
                  </div>

                  <div className="max-h-[380px] overflow-y-auto pr-2 space-y-4">
                    {Object.entries(weights).map(([k, v]) => (
                      <div key={k} className="space-y-1.5">
                        <div className="flex justify-between text-caption font-semibold text-slate">
                          <span className="capitalize">{k.replace('Match', '')} Weight</span>
                          <span>{Math.round((v as number) * 100)}%</span>
                        </div>
                        <input
                          type="range"
                          min="0"
                          max="100"
                          value={Math.round((v as number) * 100)}
                          onChange={(e) => handleWeightChange(k as keyof Weights, parseInt(e.target.value) / 100)}
                          className="w-full h-1 bg-mist-gray rounded-lg appearance-none cursor-pointer accent-jet-black"
                        />
                      </div>
                    ))}
                  </div>

                  <Button onClick={saveWeights} className="w-full mt-4" disabled={saving}>
                    <Save className="w-4 h-4" /> Save Algorithm Weights
                  </Button>
                </Card>
              )}
            </div>

            {/* Right audit outcomes */}
            <div className="lg:col-span-2 space-y-6">
              <h3 className="text-heading-sm font-normal text-jet-black">Match Audit Verdict</h3>
              
              {matchResult ? (
                <div className="space-y-6">
                  {/* Overall Match Index */}
                  <Card variant="white" className="flex items-center justify-between">
                    <div>
                      <p className="text-caption text-slate uppercase tracking-wider font-semibold">Overall Index Score</p>
                      <p className="text-display font-normal text-jet-black leading-none mt-1">
                        {matchResult.overallScore}<span className="text-subheading text-slate">%</span>
                      </p>
                    </div>
                    <div className="w-1/2">
                      <ProgressBar value={matchResult.overallScore} />
                    </div>
                  </Card>

                  {/* Strengths & Weaknesses Split */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* Strengths */}
                    <Card className="space-y-3">
                      <h4 className="text-body-sm font-bold text-emerald-800 flex items-center gap-2">
                        <CheckCircle className="w-4 h-4" /> Profile Strengths
                      </h4>
                      {matchResult.strengths?.length === 0 ? (
                        <p className="text-caption text-slate">No notable match strengths highlighted.</p>
                      ) : (
                        <ul className="list-disc pl-5 text-body-sm text-charcoal space-y-1">
                          {matchResult.strengths.map((str, i) => (
                            <li key={i}>{str}</li>
                          ))}
                        </ul>
                      )}
                    </Card>

                    {/* Weaknesses */}
                    <Card className="space-y-3">
                      <h4 className="text-body-sm font-bold text-red-800 flex items-center gap-2">
                        <AlertTriangle className="w-4 h-4" /> Profile Weaknesses
                      </h4>
                      {matchResult.weaknesses?.length === 0 ? (
                        <p className="text-caption text-slate">No major weaknesses identified.</p>
                      ) : (
                        <ul className="list-disc pl-5 text-body-sm text-charcoal space-y-1">
                          {matchResult.weaknesses.map((wk, i) => (
                            <li key={i}>{wk}</li>
                          ))}
                        </ul>
                      )}
                    </Card>
                  </div>

                  {/* Factor Scoring Breakdown */}
                  <Card variant="white" className="space-y-4">
                    <h4 className="text-body-sm font-bold text-jet-black">Explainable Factor Scores</h4>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {Object.entries(matchResult.individualScores).map(([factor, score]) => (
                        <div key={factor} className="flex justify-between items-center py-2 border-b border-iron-gray/10">
                          <span className="text-body-sm text-charcoal capitalize">{factor.replace('Match', '')}</span>
                          <span className="text-caption font-bold text-jet-black">{score}%</span>
                        </div>
                      ))}
                    </div>
                  </Card>

                  {/* Skill & Profile Gaps */}
                  {matchResult.criticalGaps?.length > 0 && (
                    <Card className="space-y-3">
                      <h4 className="text-body-sm font-bold text-jet-black flex items-center gap-2">
                        <AlertTriangle className="w-4 h-4 text-amber-600" /> Detected Profile Gaps
                      </h4>
                      <div className="flex flex-wrap gap-2">
                        {matchResult.criticalGaps.map((gap, i) => (
                          <Badge key={i} variant="warning">
                            {gap.name} ({gap.type})
                          </Badge>
                        ))}
                      </div>
                    </Card>
                  )}

                  {/* Recommendations */}
                  {matchResult.recommendations?.length > 0 && (
                    <Card variant="white" className="space-y-3">
                      <h4 className="text-body-sm font-bold text-jet-black flex items-center gap-2">
                        <HelpCircle className="w-4 h-4 text-faded-teal" /> AI Action Recommendations
                      </h4>
                      <div className="space-y-2">
                        {matchResult.recommendations.map((rec, i) => (
                          <div key={i} className="p-3 bg-mist-gray rounded border border-iron-gray/10">
                            <p className="text-body-sm font-bold text-jet-black">{rec.title}</p>
                            <p className="text-caption text-slate mt-1">{rec.reason || 'To bridge detected gaps.'}</p>
                          </div>
                        ))}
                      </div>
                    </Card>
                  )}

                </div>
              ) : (
                <EmptyState
                  title="No opportunity audited"
                  description="Select a job position at the top to evaluate matches and inspect explainable scoring parameters."
                />
              )}
            </div>

          </div>
        )}
      </div>
    </AppShell>
  );
};
