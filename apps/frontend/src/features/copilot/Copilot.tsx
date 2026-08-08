import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Badge } from '../../components/ui/Badge';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { LoadingState } from '../../components/ui/LoadingState';
import { api } from '../../services/api';
import { Sparkles, Send, RefreshCw, FileText, CheckCircle2, ClipboardList } from 'lucide-react';

interface Recommendation {
  title: string;
  reason: string;
  evidence: string[];
  action: string;
}

interface PlanStep {
  stepId: string;
  title: string;
  description: string;
  order: number;
}

interface CopilotPlan {
  planId: string;
  planType: string;
  steps: PlanStep[];
}

interface Message {
  sender: 'user' | 'copilot';
  text: string;
  plan?: CopilotPlan;
  recommendations?: Recommendation[];
}

export const Copilot: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [chatLoader, setChatLoader] = useState(false);

  // Copilot health sidebar state
  const [healthScore, setHealthScore] = useState(78);
  const [resumesCount, setResumesCount] = useState(0);
  const [appsCount, setAppsCount] = useState(0);
  const [readinessScore, setReadinessScore] = useState(72);

  const fetchSidebarData = async () => {
    try {
      const healthRes = await api.get('/copilot/health-score');
      setHealthScore(Math.round((healthRes.data?.overallScore || 0.78) * 100));

      const resumesRes = await api.get('/resumes');
      setResumesCount(resumesRes.data?.length || 0);

      const appsRes = await api.get('/applications');
      setAppsCount(appsRes.data?.length || 0);

      const readinessRes = await api.get('/interview/readiness');
      setReadinessScore(Math.round((readinessRes.data?.readinessScore || 0.72) * 100));
    } catch (e) {
      console.error('Error fetching copilot sidebar data:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSidebarData();
    // Default welcome message
    setMessages([
      {
        sender: 'copilot',
        text: 'Hello! I am your Career Copilot. I evaluate your profile completeness, ATS score, mock interviews, and application workflows to generate evidence-based growth recommendations. Try asking: "Review my skill alignment" or "How can I improve my job search?"',
      },
    ]);
  }, []);

  const sendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;

    const userMsg = input.trim();
    setInput('');
    setMessages((prev) => [...prev, { sender: 'user', text: userMsg }]);
    setChatLoader(true);

    try {
      // 1. Process intent/chat
      const chatRes = await api.post('/copilot/chat', { message: userMsg });
      
      // 2. Fetch Plan
      let plan: CopilotPlan | undefined;
      try {
        const planRes = await api.post('/copilot/plan', { message: userMsg });
        if (planRes.data && planRes.data.steps?.length > 0) {
          plan = planRes.data;
        }
      } catch (e) {
        console.warn('No active plan generated for this query');
      }

      // 3. Fetch Recommendations
      let recs: Recommendation[] = [];
      try {
        const recsRes = await api.post('/copilot/recommendations', { message: userMsg });
        recs = recsRes.data || [];
      } catch (e) {
        console.warn('No recommendations found');
      }

      // Response assembly text
      let responseText = 'I have analyzed your request against your uploaded CV and candidate profile.';
      if (chatRes.data?.intent) {
        responseText += ` Detected Intent: ${chatRes.data.intent.replace(/_/g, ' ').toLowerCase()}.`;
      }
      if (recs.length > 0) {
        responseText += ` Generated ${recs.length} actionable career recommendation(s) below.`;
      }

      setMessages((prev) => [
        ...prev,
        {
          sender: 'copilot',
          text: responseText,
          plan,
          recommendations: recs,
        },
      ]);
    } catch (e) {
      console.error(e);
      setMessages((prev) => [
        ...prev,
        {
          sender: 'copilot',
          text: 'Sorry, I encountered an issue parsing your profile details. Please verify your resume is uploaded.',
        },
      ]);
    } finally {
      setChatLoader(false);
    }
  };

  const clearSession = async () => {
    try {
      await api.post('/copilot/clear-session');
      setMessages([
        {
          sender: 'copilot',
          text: 'Conversation history cleared. Ready for your next career query.',
        },
      ]);
    } catch (e) {
      console.error(e);
    }
  };

  if (loading) {
    return (
      <AppShell title="Career Copilot" description="AI growth advisor and career health checker.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="Career Copilot" description="AI growth advisor and career health checker.">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Left/Center Conversation area (2/3 width) */}
        <div className="lg:col-span-2 flex flex-col h-[70vh] border border-iron-gray/15 rounded-lg bg-paper-white overflow-hidden">
          {/* Header */}
          <div className="px-6 py-4 border-b border-iron-gray/10 bg-mist-gray/30 flex justify-between items-center">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-jet-black" />
              <span className="text-body-sm font-bold text-jet-black">Chat Session</span>
            </div>
            <button onClick={clearSession} className="text-caption text-slate hover:text-jet-black flex items-center gap-1">
              <RefreshCw className="w-3.5 h-3.5" /> Clear Chat
            </button>
          </div>

          {/* Messages Feed */}
          <div className="flex-1 overflow-y-auto p-6 space-y-6">
            {messages.map((msg, i) => (
              <div key={i} className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[85%] rounded-lg p-4 space-y-4 ${
                  msg.sender === 'user'
                    ? 'bg-jet-black text-paper-white'
                    : 'bg-mist-gray text-jet-black border border-iron-gray/10'
                }`}>
                  <p className="text-body-sm leading-relaxed whitespace-pre-line">{msg.text}</p>

                  {/* Render Recommendations if present */}
                  {msg.recommendations && msg.recommendations.length > 0 && (
                    <div className="space-y-3 pt-3 border-t border-iron-gray/20">
                      <h5 className="text-caption font-bold uppercase tracking-wider text-slate">Action Recommendations</h5>
                      <div className="space-y-2">
                        {msg.recommendations.map((rec, rIdx) => (
                          <div key={rIdx} className="bg-paper-white p-3 rounded border border-iron-gray/15 space-y-2">
                            <div className="flex justify-between items-start gap-2">
                              <span className="text-body-sm font-bold text-jet-black">{rec.title}</span>
                              <Badge variant="active">{rec.action}</Badge>
                            </div>
                            <p className="text-caption text-slate">{rec.reason}</p>
                            {rec.evidence?.length > 0 && (
                              <div className="pt-2 border-t border-iron-gray/10 text-caption text-ash-gray">
                                <strong>Evidence:</strong> {rec.evidence.join(', ')}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Render Plan if present */}
                  {msg.plan && (
                    <div className="space-y-3 pt-3 border-t border-iron-gray/20">
                      <h5 className="text-caption font-bold uppercase tracking-wider text-slate">AI Implementation Plan</h5>
                      <div className="space-y-2">
                        {msg.plan.steps.map((step, sIdx) => (
                          <div key={sIdx} className="bg-paper-white p-3 rounded border border-iron-gray/15 flex gap-3">
                            <span className="w-5 h-5 rounded-full bg-mist-gray border border-iron-gray/10 flex items-center justify-center text-caption font-bold text-jet-black">
                              {step.order}
                            </span>
                            <div>
                              <p className="text-body-sm font-bold text-jet-black">{step.title}</p>
                              <p className="text-caption text-slate mt-0.5">{step.description}</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ))}

            {chatLoader && (
              <div className="flex justify-start">
                <div className="bg-mist-gray border border-iron-gray/10 rounded-lg p-4 flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-jet-black rounded-full animate-bounce" />
                  <span className="w-1.5 h-1.5 bg-jet-black rounded-full animate-bounce delay-75" />
                  <span className="w-1.5 h-1.5 bg-jet-black rounded-full animate-bounce delay-150" />
                </div>
              </div>
            )}
          </div>

          {/* Prompt Entry Form */}
          <form onSubmit={sendMessage} className="p-4 border-t border-iron-gray/10 bg-mist-gray/10 flex gap-3">
            <div className="flex-1">
              <Input
                placeholder="Ask Career Copilot (e.g. 'Review my resume', 'Am I ready for interviews?')..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                disabled={chatLoader}
              />
            </div>
            <Button type="submit" disabled={!input.trim() || chatLoader}>
              <Send className="w-4 h-4" /> Send
            </Button>
          </form>
        </div>

        {/* Right Career Health Dashboard Sidebar */}
        <div className="space-y-6">
          <h3 className="text-heading-sm font-normal text-jet-black">Career Dashboard</h3>
          
          {/* Health Score Widget */}
          <Card variant="white" className="space-y-4">
            <div>
              <span className="text-caption text-slate font-semibold uppercase tracking-wider">Overall Health Index</span>
              <p className="text-heading font-normal text-jet-black leading-none mt-1">
                {healthScore}<span className="text-subheading text-slate">/100</span>
              </p>
            </div>
            <ProgressBar value={healthScore} />
          </Card>

          {/* Quick Metrics Checklist */}
          <Card className="space-y-4">
            <h4 className="text-body-sm font-bold text-jet-black">Checklist Status</h4>
            
            <div className="space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-iron-gray/10">
                <span className="text-body-sm text-charcoal flex items-center gap-2">
                  <FileText className="w-4 h-4 text-slate" /> Resumes Stored
                </span>
                <span className="text-body-sm font-bold text-jet-black">{resumesCount}</span>
              </div>

              <div className="flex items-center justify-between pb-2 border-b border-iron-gray/10">
                <span className="text-body-sm text-charcoal flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-slate" /> Interview Readiness
                </span>
                <span className="text-body-sm font-bold text-jet-black">{readinessScore}%</span>
              </div>

              <div className="flex items-center justify-between pb-2 border-b border-iron-gray/10">
                <span className="text-body-sm text-charcoal flex items-center gap-2">
                  <ClipboardList className="w-4 h-4 text-slate" /> Active Workflows
                </span>
                <span className="text-body-sm font-bold text-jet-black">{appsCount}</span>
              </div>
            </div>
          </Card>
        </div>

      </div>
    </AppShell>
  );
};
