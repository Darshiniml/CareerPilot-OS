import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { api } from '../../services/api';
import { 
  Play, 
  Pause, 
  XOctagon, 
  Cpu, 
  CheckCircle2, 
  Clock, 
  AlertTriangle,
  Sliders,
  ShieldCheck,
  Globe2,
  FolderLock,
  Radio,
  Server
} from 'lucide-react';

interface AgentTask {
  id: string;
  taskType: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'RETRYING' | 'SKIPPED' | 'BLOCKED';
  agentId: string;
  priority: number;
  retryCount: number;
  errorMessage: string;
  startedAt: string;
  completedAt: string;
}

interface AgentPolicy {
  enabled: boolean;
  maxApplicationsPerDay: number;
  minimumMatchScore: number;
  requireApproval: boolean;
  allowAutomaticSubmission: boolean;
  allowReferenceResearch: boolean;
  allowExternalConnectors: boolean;
}

interface ConnectorItem {
  id: string;
  name: string;
  type: string;
  version: string;
  enabled: boolean;
  status?: string;
  message?: string;
}

interface ActiveAgentsResponse {
  activeAgents: number;
  executions: Array<{
    agentId: string;
    taskType: string;
    status: string;
  }>;
}

export const Agents: React.FC = () => {
  const [activeWorkflowId, setActiveWorkflowId] = useState<string | null>(null);
  const [workflowStatus, setWorkflowStatus] = useState<string>('IDLE');
  const [tasks, setTasks] = useState<AgentTask[]>([]);
  const [activeExecCount, setActiveExecCount] = useState<number>(0);
  const [connectors, setConnectors] = useState<ConnectorItem[]>([
    { id: 'greenhouse', name: 'Greenhouse ATS', type: 'DIRECT_ATS', version: '1.0.0', enabled: true, status: 'HEALTHY' },
    { id: 'lever', name: 'Lever ATS', type: 'DIRECT_ATS', version: '1.0.0', enabled: true, status: 'HEALTHY' },
    { id: 'ashby', name: 'Ashby ATS', type: 'DIRECT_ATS', version: '1.0.0', enabled: true, status: 'HEALTHY' },
    { id: 'company-career', name: 'Company Career Pages', type: 'DIRECT_ATS', version: '1.0.0', enabled: true, status: 'HEALTHY' },
    { id: 'remotive', name: 'Remotive Remote Jobs', type: 'AGGREGATOR', version: '1.0.0', enabled: true, status: 'HEALTHY' },
    { id: 'weworkremotely', name: 'We Work Remotely', type: 'AGGREGATOR', version: '1.0.0', enabled: true, status: 'HEALTHY' },
    { id: 'adzuna', name: 'Adzuna Jobs', type: 'AGGREGATOR', version: '1.0.0', enabled: true, status: 'NOT_CONFIGURED' },
    { id: 'jooble', name: 'Jooble Jobs', type: 'AGGREGATOR', version: '1.0.0', enabled: true, status: 'NOT_CONFIGURED' },
    { id: 'wellfound', name: 'Wellfound (AngelList)', type: 'AGGREGATOR', version: '1.0.0', enabled: true, status: 'NOT_CONFIGURED' },
    { id: 'indeed', name: 'Indeed Jobs', type: 'AGGREGATOR', version: '1.0.0', enabled: true, status: 'NOT_CONFIGURED' }
  ]);
  const [policy, setPolicy] = useState<AgentPolicy>({
    enabled: true,
    maxApplicationsPerDay: 5,
    minimumMatchScore: 70,
    requireApproval: true,
    allowAutomaticSubmission: false,
    allowReferenceResearch: true,
    allowExternalConnectors: true
  });
  const [loading, setLoading] = useState(false);
  const [pollingActive, setPollingActive] = useState(false);

  // Fetch Policy
  const fetchPolicy = async () => {
    try {
      const res = await api.get('/agents/policy');
      if (res.data) {
        setPolicy(res.data);
      }
    } catch (e) {
      console.error('Failed to fetch policy settings:', e);
    }
  };

  // Fetch Connectors and Health
  const fetchConnectors = async () => {
    try {
      const [listRes, healthRes] = await Promise.all([
        api.get('/connectors'),
        api.get('/discovery/connectors/health')
      ]);

      const healthMap = new Map<string, any>();
      if (healthRes.data && Array.isArray(healthRes.data)) {
        healthRes.data.forEach((h: any) => healthMap.set(h.connectorId, h));
      }

      if (listRes.data && Array.isArray(listRes.data)) {
        const merged = listRes.data.map((c: any) => {
          const h = healthMap.get(c.id);
          return {
            id: c.id,
            name: c.name || c.id,
            type: c.type || 'AGGREGATOR',
            version: c.version || '1.0.0',
            enabled: c.enabled,
            status: h ? h.status : (c.enabled ? 'HEALTHY' : 'DISABLED'),
            message: h ? h.message : ''
          };
        });
        setConnectors(merged);
      }
    } catch (e) {
      console.error('Failed to fetch connector configurations:', e);
    }
  };

  // Toggle Connector Enablement
  const handleToggleConnector = async (id: string, currentEnabled: boolean) => {
    try {
      await api.post('/connectors/register', { connectorId: id, enabled: !currentEnabled });
      fetchConnectors();
    } catch (e) {
      console.error('Failed to update connector:', e);
    }
  };

  // Fetch active executions count
  const fetchActiveExecutions = async () => {
    try {
      const res = await api.get<ActiveAgentsResponse>('/agents/active');
      if (res.data) {
        setActiveExecCount(res.data.activeAgents);
      }
    } catch (e) {
      console.error('Failed to fetch active agent count:', e);
    }
  };

  // Fetch running workflows
  const fetchActiveWorkflow = async () => {
    try {
      const res = await api.get('/agents/workflow/active');
      if (res.data && res.data.length > 0) {
        setActiveWorkflowId(res.data[0].id);
        setWorkflowStatus(res.data[0].status);
        setPollingActive(true);
      }
    } catch (e) {
      console.error('Failed to fetch active workflows:', e);
    }
  };

  // Fetch tasks for the current active workflow
  const fetchWorkflowTasks = async (id: string) => {
    try {
      const res = await api.get<any>(`/agents/workflow/${id}/status`);
      if (res.data) {
        const taskList = res.data.tasks || [];
        setTasks(taskList);
        setWorkflowStatus(res.data.status || 'RUNNING');
        
        // If all tasks are completed, failed or blocked, stop polling
        const running = taskList.some((t: any) => t.status === 'RUNNING' || t.status === 'RETRYING' || t.status === 'PENDING');
        if (!running || res.data.status === 'COMPLETED' || res.data.status === 'FAILED' || res.data.status === 'BLOCKED' || res.data.status === 'CANCELLED') {
          setPollingActive(false);
        }
      }
    } catch (e) {
      console.error('Failed to fetch task timeline:', e);
    }
  };

  useEffect(() => {
    fetchPolicy();
    fetchConnectors();
    fetchActiveWorkflow();
    fetchActiveExecutions();
  }, []);

  // Poll for active executions and task updates
  useEffect(() => {
    let interval: any = null;
    if (pollingActive && activeWorkflowId) {
      interval = setInterval(() => {
        fetchWorkflowTasks(activeWorkflowId);
        fetchActiveExecutions();
      }, 2000);
    } else {
      clearInterval(interval);
    }
    return () => clearInterval(interval);
  }, [pollingActive, activeWorkflowId]);

  const handleStartWorkflow = async () => {
    setLoading(true);
    try {
      const res = await api.post('/agents/workflow/start');
      if (res.data) {
        setActiveWorkflowId(res.data.id);
        setWorkflowStatus(res.data.status);
        setPollingActive(true);
        fetchWorkflowTasks(res.data.id);
      }
    } catch (e) {
      alert('Failed to start workflow search. Ensure you have an uploaded resume.');
    } finally {
      setLoading(false);
    }
  };

  const handlePauseWorkflow = async () => {
    if (!activeWorkflowId) return;
    try {
      await api.post(`/agents/workflow/${activeWorkflowId}/pause`);
      setWorkflowStatus('PAUSED');
      setPollingActive(false);
    } catch (e) {
      console.error(e);
    }
  };

  const handleResumeWorkflow = async () => {
    if (!activeWorkflowId) return;
    try {
      await api.post(`/agents/workflow/${activeWorkflowId}/resume`);
      setWorkflowStatus('RUNNING');
      setPollingActive(true);
    } catch (e) {
      console.error(e);
    }
  };

  const handleCancelWorkflow = async () => {
    if (!activeWorkflowId) return;
    try {
      await api.post(`/agents/workflow/${activeWorkflowId}/cancel`);
      setWorkflowStatus('CANCELLED');
      setPollingActive(false);
      fetchWorkflowTasks(activeWorkflowId);
    } catch (e) {
      console.error(e);
    }
  };

  const handleUpdatePolicy = async (newPolicy: AgentPolicy) => {
    try {
      const res = await api.put('/agents/policy', newPolicy);
      if (res.data) {
        setPolicy(res.data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // Map backend task types to premium user-facing step titles
  const timelineStages = [
    { type: 'RESUME_ANALYSIS', name: 'Resume Intelligence & Parsing' },
    { type: 'JOB_DISCOVERY', name: 'Opportunity Sync & Discover' },
    { type: 'COMPANY_RESEARCH', name: 'Corporate Research Insights' },
    { type: 'JOB_MATCHING', name: 'ATS & Skill Profile Matching' },
    { type: 'REFERENCE_RESEARCH', name: 'Hiring Signals & References' },
    { type: 'APPLICATION_PREPARATION', name: 'Form Preparation & Filing' },
    { type: 'VERIFICATION', name: 'Receipt & Evidence Audit' },
    { type: 'TRACKING', name: 'Status & Funnel Synchronization' },
    { type: 'NOTIFICATION', name: 'Alerts & Platform Notification' }
  ];

  const directConnectors = connectors.filter(c => c.type === 'DIRECT_ATS');
  const aggregatorConnectors = connectors.filter(c => c.type === 'AGGREGATOR');

  return (
    <AppShell title="Autonomous Automation Agents" description="Orchestrate and monitor your multi-agent career search stream.">
      <div className="space-y-8 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-12">
        
        {/* Top KPI row */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <Card variant="white" className="p-6 flex items-center justify-between">
            <div>
              <p className="text-caption text-slate uppercase tracking-wider font-semibold">Active Agents</p>
              <h3 className="text-heading-sm font-bold text-jet-black mt-1 select-none">{activeExecCount} Running</h3>
            </div>
            <div className="p-3 bg-mist-gray rounded-full">
              <Cpu className={`w-6 h-6 text-jet-black ${activeExecCount > 0 ? 'animate-pulse' : ''}`} />
            </div>
          </Card>
          
          <Card variant="white" className="p-6 flex items-center justify-between">
            <div>
              <p className="text-caption text-slate uppercase tracking-wider font-semibold">Workflow Status</p>
              <div className="mt-2">
                <Badge variant={workflowStatus === 'RUNNING' ? 'active' : workflowStatus === 'BLOCKED' ? 'danger' : 'neutral'}>
                  {workflowStatus}
                </Badge>
              </div>
            </div>
            <div className="p-3 bg-mist-gray rounded-full">
              <Clock className="w-6 h-6 text-jet-black" />
            </div>
          </Card>
          
          <Card variant="white" className="p-6 flex items-center justify-between">
            <div>
              <p className="text-caption text-slate uppercase tracking-wider font-semibold">Completed Steps</p>
              <h3 className="text-heading-sm font-bold text-jet-black mt-1 select-none">
                {tasks.filter(t => t.status === 'COMPLETED').length} / {timelineStages.length}
              </h3>
            </div>
            <div className="p-3 bg-mist-gray rounded-full">
              <CheckCircle2 className="w-6 h-6 text-jet-black" />
            </div>
          </Card>
          
          <Card variant="white" className="p-6 flex items-center justify-between">
            <div>
              <p className="text-caption text-slate uppercase tracking-wider font-semibold">Active Connectors</p>
              <h3 className="text-heading-sm font-bold text-jet-black mt-1 select-none">
                {connectors.filter(c => c.enabled && (c.status === 'HEALTHY' || c.status === 'UNKNOWN')).length} Enabled
              </h3>
            </div>
            <div className="p-3 bg-mist-gray rounded-full">
              <Server className="w-6 h-6 text-jet-black" />
            </div>
          </Card>
        </div>

        {/* Current Activity Banner */}
        {workflowStatus !== 'IDLE' && (
          <Card variant="white" className="p-6 border border-iron-gray/10 shadow-sm">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
              <div className="space-y-1">
                <span className="text-caption font-semibold uppercase tracking-wider text-slate">Current Activity</span>
                <p className="text-body-sm font-bold text-jet-black">
                  {workflowStatus === 'BLOCKED' && '⚠️ Workflow Blocked: Action Required'}
                  {workflowStatus === 'RUNNING' && (
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'RESUME_ANALYSIS' ? 'Analyzing your resume...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'JOB_DISCOVERY' ? 'Discovering matching jobs across enabled connectors...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'COMPANY_RESEARCH' ? 'Researching company profiles...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'JOB_MATCHING' ? 'Calculating job matches...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'REFERENCE_RESEARCH' ? 'Researching public hiring signals...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'APPLICATION_PREPARATION' ? 'Preparing applications...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'VERIFICATION' ? 'Verifying application...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'TRACKING' ? 'Updating application tracking...' :
                    tasks.find(t => t.status === 'RUNNING' || t.status === 'RETRYING')?.taskType === 'NOTIFICATION' ? 'Sending notifications...' :
                    'Initializing autonomous automation engines...'
                  )}
                  {workflowStatus === 'COMPLETED' && '✓ Career Automation workflow completed successfully.'}
                  {workflowStatus === 'FAILED' && '✗ Career Automation workflow execution failed.'}
                  {workflowStatus === 'CANCELLED' && 'Career Automation workflow run cancelled.'}
                  {workflowStatus === 'PAUSED' && 'Career Automation workflow run paused.'}
                </p>
                {workflowStatus === 'BLOCKED' && (
                  <p className="text-caption text-red-600 font-semibold mt-1">
                    {tasks.find(t => t.status === 'BLOCKED')?.errorMessage || 'No enabled job connectors or resume required.'}
                  </p>
                )}
              </div>
              <div className="text-caption text-slate text-right">
                <span>Last updated: {new Date().toLocaleTimeString()}</span>
              </div>
            </div>
          </Card>
        )}

        {/* Workflow Controllers and Timeline */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          
          {/* Timeline Visualizer */}
          <div className="lg:col-span-2 space-y-6">
            <Card variant="white" className="p-6">
              <div className="flex justify-between items-center border-b border-iron-gray/10 pb-4 mb-6">
                <h3 className="text-subheading font-bold text-jet-black">Workflow Execution Stream</h3>
                <div className="flex gap-2">
                  {workflowStatus === 'IDLE' || workflowStatus === 'COMPLETED' || workflowStatus === 'CANCELLED' || workflowStatus === 'FAILED' || workflowStatus === 'BLOCKED' ? (
                    <Button onClick={handleStartWorkflow} disabled={loading} variant="primary" className="flex items-center gap-2">
                      <Play className="w-3.5 h-3.5" /> Start Search
                    </Button>
                  ) : null}
                  {workflowStatus === 'RUNNING' && (
                    <Button onClick={handlePauseWorkflow} variant="secondary" className="flex items-center gap-2">
                      <Pause className="w-3.5 h-3.5" /> Pause
                    </Button>
                  )}
                  {workflowStatus === 'PAUSED' && (
                    <Button onClick={handleResumeWorkflow} variant="primary" className="flex items-center gap-2">
                      <Play className="w-3.5 h-3.5" /> Resume
                    </Button>
                  )}
                  {workflowStatus !== 'IDLE' && workflowStatus !== 'COMPLETED' && (
                    <Button onClick={handleCancelWorkflow} variant="secondary" className="flex items-center gap-2">
                      <XOctagon className="w-3.5 h-3.5" /> Cancel
                    </Button>
                  )}
                </div>
              </div>

              {/* Steps timeline list */}
              <div className="relative border-l border-iron-gray/20 ml-4 pl-8 space-y-6 py-2">
                {timelineStages.map((stage) => {
                  const dbTask = tasks.find(t => t.taskType === stage.type);
                  const status = dbTask ? dbTask.status : 'PENDING';
                  const retries = dbTask ? dbTask.retryCount : 0;
                  const error = dbTask ? dbTask.errorMessage : '';

                  let indicatorColor = 'bg-iron-gray/20 border-iron-gray/30';
                  if (status === 'RUNNING') indicatorColor = 'bg-jet-black border-jet-black animate-pulse';
                  if (status === 'COMPLETED') indicatorColor = 'bg-jet-black border-jet-black';
                  if (status === 'FAILED') indicatorColor = 'bg-red-500 border-red-600';
                  if (status === 'RETRYING') indicatorColor = 'bg-yellow-500 border-yellow-600';
                  if (status === 'SKIPPED') indicatorColor = 'bg-ash-gray border-iron-gray';
                  if (status === 'BLOCKED') indicatorColor = 'bg-red-500 border-red-600';

                  return (
                    <div key={stage.type} className="relative">
                      {/* Timeline dot */}
                      <span className={`absolute -left-12 top-1.5 flex h-4 w-4 rounded-full border-2 ${indicatorColor}`} />
                      
                      <div className="flex justify-between items-start">
                        <div>
                          <h4 className="text-body-sm font-bold text-jet-black tracking-tight">{stage.name}</h4>
                          {error && <p className="text-caption text-red-500 mt-1">{error}</p>}
                          {retries > 0 && <p className="text-caption text-yellow-600 mt-0.5">Retrying: {retries}/3 attempts</p>}
                        </div>
                        <Badge variant={status === 'COMPLETED' ? 'active' : status === 'BLOCKED' ? 'danger' : 'neutral'}>
                          {status}
                        </Badge>
                      </div>
                    </div>
                  );
                })}
              </div>
            </Card>

            {/* Job Discovery Connectors Configuration Card */}
            <Card variant="white" className="p-6">
              <div className="flex items-center gap-2 border-b border-iron-gray/10 pb-4 mb-6">
                <Radio className="w-5 h-5 text-jet-black" />
                <h3 className="text-subheading font-bold text-jet-black">Job Discovery Connectors</h3>
              </div>

              {/* Direct Company / ATS Sources */}
              <div className="space-y-4 mb-6">
                <h4 className="text-caption font-semibold text-slate uppercase tracking-wider">Direct Company / ATS Sources</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {directConnectors.map((c) => (
                    <div key={c.id} className="p-3 bg-mist-gray rounded-lg border border-iron-gray/10 flex items-center justify-between">
                      <div>
                        <span className="text-body-sm font-bold text-jet-black block">{c.name}</span>
                        <span className="text-[11px] text-slate font-medium uppercase tracking-wider">{c.status || 'HEALTHY'}</span>
                      </div>
                      <input
                        type="checkbox"
                        checked={c.enabled}
                        onChange={() => handleToggleConnector(c.id, c.enabled)}
                        className="w-4 h-4 accent-jet-black"
                      />
                    </div>
                  ))}
                </div>
              </div>

              {/* Job Aggregators */}
              <div className="space-y-4">
                <h4 className="text-caption font-semibold text-slate uppercase tracking-wider">Job Aggregators</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {aggregatorConnectors.map((c) => (
                    <div key={c.id} className="p-3 bg-mist-gray rounded-lg border border-iron-gray/10 flex items-center justify-between">
                      <div>
                        <span className="text-body-sm font-bold text-jet-black block">{c.name}</span>
                        <span className={`text-[11px] font-medium uppercase tracking-wider ${c.status === 'NOT_CONFIGURED' ? 'text-yellow-600' : 'text-slate'}`}>
                          {c.status || 'HEALTHY'}
                        </span>
                      </div>
                      <input
                        type="checkbox"
                        checked={c.enabled}
                        disabled={c.status === 'NOT_CONFIGURED'}
                        onChange={() => handleToggleConnector(c.id, c.enabled)}
                        className="w-4 h-4 accent-jet-black disabled:opacity-40"
                      />
                    </div>
                  ))}
                </div>
              </div>
            </Card>
          </div>

          {/* User Automation Policy Settings Panel */}
          <div className="space-y-6">
            <Card variant="white" className="p-6">
              <div className="flex items-center gap-2 border-b border-iron-gray/10 pb-4 mb-6">
                <Sliders className="w-5 h-5 text-jet-black" />
                <h3 className="text-subheading font-bold text-jet-black">Automation Safety Policy</h3>
              </div>

              <div className="space-y-6">
                {/* Enabled Toggle */}
                <div className="flex justify-between items-center">
                  <div>
                    <label className="text-body-sm font-bold text-jet-black block">Enable Automation</label>
                    <span className="text-caption text-slate">Allow automation runner to execute tasks</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={policy.enabled}
                    onChange={(e) => handleUpdatePolicy({ ...policy, enabled: e.target.checked })}
                    className="w-4 h-4 accent-jet-black"
                  />
                </div>

                {/* Require Manual Approval Toggle */}
                <div className="flex justify-between items-center">
                  <div>
                    <label className="text-body-sm font-bold text-jet-black block">Require Approval</label>
                    <span className="text-caption text-slate">Ask user permission before any submission</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={policy.requireApproval}
                    onChange={(e) => handleUpdatePolicy({ ...policy, requireApproval: e.target.checked })}
                    className="w-4 h-4 accent-jet-black"
                  />
                </div>

                {/* Automatic Submission (MUST BE DISABLED BY DEFAULT) */}
                <div className="p-4 bg-mist-gray rounded-lg border border-iron-gray/10">
                  <div className="flex justify-between items-center">
                    <div>
                      <label className="text-body-sm font-bold text-jet-black flex items-center gap-1.5">
                        <ShieldCheck className="w-4 h-4 text-red-500" /> Automatic Submission
                      </label>
                      <span className="text-caption text-slate">Warning: Submits applications automatically</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={policy.allowAutomaticSubmission}
                      onChange={(e) => handleUpdatePolicy({ ...policy, allowAutomaticSubmission: e.target.checked })}
                      className="w-4 h-4 accent-red-600"
                    />
                  </div>
                  <div className="mt-2 text-[11px] text-red-600 font-semibold uppercase tracking-wider select-none">
                    Default Status: DISABLED
                  </div>
                </div>

                {/* Min Match Score Slider */}
                <div>
                  <div className="flex justify-between items-center mb-2">
                    <label className="text-body-sm font-bold text-jet-black">Min Match Score</label>
                    <span className="text-body-sm font-bold text-jet-black">{policy.minimumMatchScore}%</span>
                  </div>
                  <input
                    type="range"
                    min="50"
                    max="100"
                    value={policy.minimumMatchScore}
                    onChange={(e) => handleUpdatePolicy({ ...policy, minimumMatchScore: parseInt(e.target.value) })}
                    className="w-full accent-jet-black"
                  />
                </div>

                {/* Max Applications per Day */}
                <div>
                  <label className="text-body-sm font-bold text-jet-black block mb-2">Max Applications / Day</label>
                  <input
                    type="number"
                    min="1"
                    max="50"
                    value={policy.maxApplicationsPerDay}
                    onChange={(e) => handleUpdatePolicy({ ...policy, maxApplicationsPerDay: parseInt(e.target.value) })}
                    className="w-full px-3 py-2 bg-mist-gray border border-iron-gray/10 rounded-lg text-body-sm focus:outline-none focus:ring-1 focus:ring-jet-black"
                  />
                </div>

                {/* Reference Research Toggle */}
                <div className="flex justify-between items-center">
                  <div>
                    <label className="text-body-sm font-bold text-jet-black flex items-center gap-1.5">
                      <Globe2 className="w-4 h-4" /> Reference Research
                    </label>
                    <span className="text-caption text-slate">Research public hiring signals & references</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={policy.allowReferenceResearch}
                    onChange={(e) => handleUpdatePolicy({ ...policy, allowReferenceResearch: e.target.checked })}
                    className="w-4 h-4 accent-jet-black"
                  />
                </div>

                {/* External Connectors */}
                <div className="flex justify-between items-center">
                  <div>
                    <label className="text-body-sm font-bold text-jet-black flex items-center gap-1.5">
                      <FolderLock className="w-4 h-4" /> External Connectors
                    </label>
                    <span className="text-caption text-slate">Fetch and sync external postings</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={policy.allowExternalConnectors}
                    onChange={(e) => handleUpdatePolicy({ ...policy, allowExternalConnectors: e.target.checked })}
                    className="w-4 h-4 accent-jet-black"
                  />
                </div>
              </div>
            </Card>
          </div>

        </div>

      </div>
    </AppShell>
  );
};
