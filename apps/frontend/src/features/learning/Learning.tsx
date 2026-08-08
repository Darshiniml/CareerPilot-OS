import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Badge } from '../../components/ui/Badge';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { LoadingState } from '../../components/ui/LoadingState';
import { EmptyState } from '../../components/ui/EmptyState';
import { api } from '../../services/api';
import { BookOpen, Award, CheckSquare, Plus, GraduationCap } from 'lucide-react';

interface Goal {
  goalId: string;
  title: string;
  targetSkill: string;
  targetLevel: string;
  targetDate: string;
  progress: number;
}

interface PathItem {
  itemId: string;
  stepName: string;
  description: string;
  status: string;
  sequenceOrder: number;
}

interface LearningPath {
  pathId: string;
  skill: string;
  targetLevel: string;
  urgency: string;
  progress: number;
  learningSequence: PathItem[];
}

export const Learning: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [paths, setPaths] = useState<LearningPath[]>([]);
  const [selectedPath, setSelectedPath] = useState<LearningPath | null>(null);

  // New goal form states
  const [title, setTitle] = useState('');
  const [skill, setSkill] = useState('');
  const [level, setLevel] = useState('INTERMEDIATE');
  const [date, setDate] = useState('');
  const [savingGoal, setSavingGoal] = useState(false);

  // New path form states
  const [pathSkill, setPathSkill] = useState('');
  const [pathLevel, setPathLevel] = useState('BEGINNER');
  const [pathUrgency, setPathUrgency] = useState('STRONG');
  const [generatingPath, setGeneratingPath] = useState(false);

  const fetchGoalsAndPaths = async () => {
    try {
      const goalsRes = await api.get('/learning/goals');
      setGoals(goalsRes.data || []);

      // Fetch learning paths (using recommendations endpoint as default loader)
      const pathsRes = await api.get('/learning/recommendations');
      const list = pathsRes.data || [];
      setPaths(list);
      if (list.length > 0 && !selectedPath) {
        setSelectedPath(list[0]);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchGoalsAndPaths();
  }, []);

  const createGoal = async () => {
    if (!title || !skill) return;
    setSavingGoal(true);
    try {
      await api.post('/learning/goals', {
        title,
        targetSkill: skill,
        targetLevel: level,
        targetDate: date || new Date(Date.now() + 90 * 24 * 3600 * 1000).toISOString().split('T')[0],
      });
      setTitle('');
      setSkill('');
      setDate('');
      fetchGoalsAndPaths();
    } catch (e) {
      console.error(e);
    } finally {
      setSavingGoal(false);
    }
  };

  const generatePath = async () => {
    if (!pathSkill) return;
    setGeneratingPath(true);
    try {
      const res = await api.post('/learning/path', {
        skill: pathSkill,
        targetLevel: pathLevel,
        urgency: pathUrgency,
      });
      setPathSkill('');
      fetchGoalsAndPaths();
      setSelectedPath(res.data);
    } catch (e) {
      console.error(e);
      alert('Failed to generate learning pathway. Check server state.');
    } finally {
      setGeneratingPath(false);
    }
  };

  const toggleStepStatus = async (itemId: string, currentStatus: string) => {
    try {
      const newStatus = currentStatus === 'COMPLETED' ? 'PENDING' : 'COMPLETED';
      await api.post('/learning/progress', {
        itemId,
        status: newStatus,
      });
      fetchGoalsAndPaths();
      // Update selected path state
      if (selectedPath) {
        const updatedSequence = selectedPath.learningSequence.map((item) =>
          item.itemId === itemId ? { ...item, status: newStatus } : item
        );
        const completedCount = updatedSequence.filter((x) => x.status === 'COMPLETED').length;
        const newProgress = Math.round((completedCount / updatedSequence.length) * 100);
        setSelectedPath({
          ...selectedPath,
          progress: newProgress,
          learningSequence: updatedSequence,
        });
      }
    } catch (e) {
      console.error(e);
    }
  };

  if (loading) {
    return (
      <AppShell title="Learning Engine" description="Map skill milestones, study pathways, and track objectives.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="Learning Engine" description="Map skill milestones, study pathways, and track objectives.">
      <div className="space-y-12">
        
        {/* Upper Layout: Goals and Generator */}
        <section className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Goals Checklist */}
          <div className="space-y-4">
            <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
              <Award className="w-5 h-5" /> Target Career Goals
            </h3>
            <Card variant="white" className="space-y-6">
              {goals.length === 0 ? (
                <p className="text-body-sm text-slate">No career goals set yet.</p>
              ) : (
                <div className="space-y-4 max-h-[300px] overflow-y-auto pr-2">
                  {goals.map((goal) => (
                    <div key={goal.goalId} className="space-y-2 border-b border-iron-gray/10 pb-3 last:border-b-0">
                      <div className="flex justify-between items-center">
                        <div>
                          <p className="text-body-sm font-bold text-jet-black">{goal.title}</p>
                          <p className="text-caption text-slate">
                            Target Skill: {goal.targetSkill} ({(goal.targetLevel || '').toLowerCase()}) · Due: {new Date(goal.targetDate).toLocaleDateString()}
                          </p>
                        </div>
                        <span className="text-caption font-bold text-jet-black">{goal.progress}%</span>
                      </div>
                      <ProgressBar value={goal.progress} />
                    </div>
                  ))}
                </div>
              )}

              {/* Goal Form */}
              <div className="pt-4 border-t border-iron-gray/15 space-y-4">
                <h4 className="text-body-sm font-bold text-jet-black">+ Add Career Goal</h4>
                <div className="grid grid-cols-2 gap-3">
                  <div className="col-span-2">
                    <Input placeholder="Goal Title (e.g. Master Kubernetes)" value={title} onChange={(e) => setTitle(e.target.value)} />
                  </div>
                  <Input placeholder="Target Skill" value={skill} onChange={(e) => setSkill(e.target.value)} />
                  <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
                  <div className="col-span-2">
                    <select
                      value={level}
                      onChange={(e) => setLevel(e.target.value)}
                      className="w-full bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2 text-body-sm text-jet-black focus:outline-none focus:border-jet-black"
                    >
                      <option value="BEGINNER">Beginner Level</option>
                      <option value="INTERMEDIATE">Intermediate Level</option>
                      <option value="ADVANCED">Advanced Level</option>
                    </select>
                  </div>
                </div>
                <Button onClick={createGoal} variant="secondary" disabled={savingGoal}>
                  <Plus className="w-4 h-4" /> Save Goal
                </Button>
              </div>
            </Card>
          </div>

          {/* Generator Column */}
          <div className="space-y-4">
            <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
              <GraduationCap className="w-5 h-5" /> Generate Skill Pathway
            </h3>
            <Card variant="white" className="space-y-4">
              <Input
                label="Target Skill"
                placeholder="e.g. Docker, AWS, System Design"
                value={pathSkill}
                onChange={(e) => setPathSkill(e.target.value)}
              />

              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col space-y-1.5">
                  <label className="block text-caption font-semibold text-slate uppercase tracking-wider">Target Level</label>
                  <select
                    value={pathLevel}
                    onChange={(e) => setPathLevel(e.target.value)}
                    className="bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2 text-body-sm focus:outline-none focus:border-jet-black"
                  >
                    <option value="BEGINNER">Beginner</option>
                    <option value="INTERMEDIATE">Intermediate</option>
                    <option value="ADVANCED">Advanced</option>
                  </select>
                </div>

                <div className="flex flex-col space-y-1.5">
                  <label className="block text-caption font-semibold text-slate uppercase tracking-wider">Urgency</label>
                  <select
                    value={pathUrgency}
                    onChange={(e) => setPathUrgency(e.target.value)}
                    className="bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2 text-body-sm focus:outline-none focus:border-jet-black"
                  >
                    <option value="STRONG">High Focus</option>
                    <option value="MEDIUM">Medium Focus</option>
                    <option value="LOW">Low Priority</option>
                  </select>
                </div>
              </div>

              <Button onClick={generatePath} className="w-full mt-4" disabled={!pathSkill || generatingPath}>
                {generatingPath ? 'Generating AI Syllabus...' : 'Create Learning Pathway'}
              </Button>
            </Card>
          </div>
        </section>

        {/* Lower Layout: Pathway Sequences */}
        <section className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Paths List (left 1/3) */}
          <div className="space-y-4">
            <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
              <BookOpen className="w-5 h-5" /> Learning Syllabus
            </h3>
            {paths.length === 0 ? (
              <Card>
                <p className="text-caption text-slate text-center">No syllabus paths generated.</p>
              </Card>
            ) : (
              <div className="space-y-3">
                {paths.map((p) => (
                  <button
                    key={p.pathId}
                    onClick={() => setSelectedPath(p)}
                    className={`w-full text-left p-4 rounded-lg border transition-all flex justify-between items-center ${
                      selectedPath?.pathId === p.pathId
                        ? 'bg-mist-gray border-jet-black'
                        : 'bg-paper-white border-iron-gray/15 hover:bg-mist-gray/40'
                    }`}
                  >
                    <div>
                      <p className="text-body-sm font-bold text-jet-black">{p.skill}</p>
                      <p className="text-caption text-slate mt-0.5">Level: {(p.targetLevel || '').toLowerCase()}</p>
                    </div>
                    <Badge variant={p.progress === 100 ? 'success' : 'active'}>
                      {p.progress}% Complete
                    </Badge>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Active Sequence Checklists (right 2/3) */}
          <div className="lg:col-span-2 space-y-4">
            <h3 className="text-heading-sm font-normal text-jet-black">Sequence Checklist</h3>
            {selectedPath ? (
              <Card variant="white" className="space-y-6">
                <div className="flex justify-between items-center pb-4 border-b border-iron-gray/10">
                  <div>
                    <h4 className="text-body font-bold text-jet-black">{selectedPath.skill}</h4>
                    <p className="text-caption text-slate mt-0.5">Syllabus Urgency: {(selectedPath.urgency || '').toLowerCase()}</p>
                  </div>
                  <div className="w-32 flex items-center gap-3">
                    <ProgressBar value={selectedPath.progress} />
                    <span className="text-caption font-bold text-jet-black">{selectedPath.progress}%</span>
                  </div>
                </div>

                <div className="space-y-4">
                  {selectedPath.learningSequence?.map((step) => (
                    <div
                      key={step.itemId}
                      className="flex items-start gap-4 p-4 bg-mist-gray rounded border border-iron-gray/10 hover:border-iron-gray/20 transition-all select-none"
                    >
                      <button
                        onClick={() => toggleStepStatus(step.itemId, step.status)}
                        className={`w-5 h-5 rounded border flex items-center justify-center transition-all ${
                          step.status === 'COMPLETED'
                            ? 'bg-jet-black border-jet-black text-paper-white'
                            : 'border-iron-gray/40 hover:border-jet-black'
                        }`}
                      >
                        {step.status === 'COMPLETED' && <CheckSquare className="w-3.5 h-3.5" />}
                      </button>
                      <div className="flex-1">
                        <p className={`text-body-sm font-bold ${step.status === 'COMPLETED' ? 'line-through text-ash-gray' : 'text-jet-black'}`}>
                          Step {step.sequenceOrder}: {step.stepName}
                        </p>
                        <p className={`text-caption mt-1 ${step.status === 'COMPLETED' ? 'line-through text-ash-gray' : 'text-slate'}`}>
                          {step.description}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </Card>
            ) : (
              <EmptyState
                title="No syllabus selected"
                description="Select or generate a skill pathway to review step checklists and complete educational milestones."
              />
            )}
          </div>
        </section>

      </div>
    </AppShell>
  );
};
