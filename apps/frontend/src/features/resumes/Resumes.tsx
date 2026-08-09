import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { LoadingState } from '../../components/ui/LoadingState';
import { api } from '../../services/api';
import { useUserContextStore } from '../../store/userContextStore';
import { FileText, Download, Check, Trash, Sparkles, AlertCircle, FileUp } from 'lucide-react';

interface Resume {
  id: string;
  fileName: string;
  fileKey: string;
  version: number;
  isDefault: boolean;
  uploadedAt: string;
}

interface AtsReport {
  score: number;
  feedback: string[];
  skillsScore: number;
  experienceScore: number;
  educationScore: number;
  formattingScore: number;
}

interface ResumeDetails {
  skills: string[];
  experienceSummary: string;
  educationSummary: string;
  suggestedRoles: string[];
}

export const Resumes: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState<string | null>(null);
  const [atsReport, setAtsReport] = useState<AtsReport | null>(null);
  const [details, setDetails] = useState<ResumeDetails | null>(null);
  const [processing, setProcessing] = useState(false);
  const [uploading, setUploading] = useState(false);

  // Stateful Processing Pipeline
  const [dragActive, setDragActive] = useState(false);
  const [uploadStatus, setUploadStatus] = useState<'IDLE' | 'UPLOADING' | 'PARSING' | 'COMPLETED' | 'FAILED'>('IDLE');
  const [progressPercent, setProgressPercent] = useState<number>(0);
  const [notification, setNotification] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  const fetchResumes = async () => {
    try {
      const res = await api.get('/resumes');
      const list = res.data || [];
      setResumes(list);

      // Automatically select default resume or first resume
      if (list.length > 0) {
        const defaultResume = list.find((r: any) => r.isDefault) || list[0];
        setSelectedResumeId(defaultResume.id);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchResumes();
  }, []);

  useEffect(() => {
    if (selectedResumeId) {
      fetchResumeIntelligence(selectedResumeId);
    } else {
      setAtsReport(null);
      setDetails(null);
    }
  }, [selectedResumeId]);

  const fetchResumeIntelligence = async (id: string) => {
    setProcessing(true);
    try {
      // Fetch H2/PostgreSQL stored cache details
      const detailsRes = await api.get(`/ai/resume/${id}`);
      const cacheData = detailsRes.data;
      if (cacheData && cacheData.structuredKnowledge) {
        const knowledge = typeof cacheData.structuredKnowledge === 'string'
          ? JSON.parse(cacheData.structuredKnowledge)
          : cacheData.structuredKnowledge;
        setDetails({
          skills: knowledge.skills?.map((s: any) => s.skill || s) || [],
          experienceSummary: knowledge.experienceSummary || 'No summary available.',
          educationSummary: knowledge.educationSummary || 'No education listed.',
          suggestedRoles: knowledge.suggestedRoles || [],
        });
      }

      // Fetch ATS details
      const atsRes = await api.get(`/ai/resume/${id}/ats`);
      const atsData = atsRes.data;
      if (atsData && atsData.reportData) {
        const report = typeof atsData.reportData === 'string'
          ? JSON.parse(atsData.reportData)
          : atsData.reportData;
        setAtsReport({
          score: atsData.score || report.score || 75,
          feedback: report.feedback || report.warnings || [],
          skillsScore: report.skillsScore || 80,
          experienceScore: report.experienceScore || 70,
          educationScore: report.educationScore || 90,
          formattingScore: report.formattingScore || 85,
        });
      }
    } catch (e) {
      console.warn('Resume intelligence parsing not ready yet, triggering process');
      triggerProcess(id);
    } finally {
      setProcessing(false);
    }
  };

  const triggerProcess = async (id: string) => {
    try {
      await api.post('/ai/resume/process', { resumeId: id });
      // Retry fetch once
      setTimeout(() => fetchResumeIntelligence(id), 2000);
    } catch (e) {
      console.error('Process trigger failed', e);
    }
  };

  // Drag and Drop handlers
  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      const mockEvent = {
        target: {
          files: [file]
        }
      } as any;
      handleFileUpload(mockEvent);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    setUploadStatus('UPLOADING');
    setProgressPercent(0);
    setNotification(null);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', file.name);

    try {
      await api.post('/resumes/upload', formData, {
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setProgressPercent(percent);
          }
        }
      });
      setUploadStatus('PARSING');
      setNotification({ type: 'success', message: 'Resume uploaded successfully! AI analysis is processing...' });
      
      await fetchResumes();
      useUserContextStore.getState().fetchUserContext();
      setUploadStatus('COMPLETED');
      setTimeout(() => setUploadStatus('IDLE'), 5000);
    } catch (err: any) {
      console.error('Upload Error Context:', {
        status: err.response?.status,
        data: err.response?.data,
        message: err.message,
        url: err.config?.url,
      });

      setUploadStatus('FAILED');
      const serverMessage = err.response?.data?.message || err.response?.data?.error;
      let friendlyMessage = 'Resume processing failed. Please try again.';

      if (!err.response) {
        friendlyMessage = 'Unable to reach CareerPilot backend. Check network connectivity.';
      } else if (err.response.status === 400) {
        friendlyMessage = `Unsupported resume format or bad request: ${serverMessage || 'Please ensure file is under 10MB and in PDF/Docx format.'}`;
      } else if (err.response.status === 413) {
        friendlyMessage = 'Upload failed: Maximum file size exceeded (Max 10MB).';
      } else if (err.response.status === 415) {
        friendlyMessage = 'Upload failed: Unsupported file type.';
      } else if (err.response.status === 401) {
        friendlyMessage = 'Your session has expired. Please sign in again.';
      } else if (err.response.status === 503 || err.response.status === 500) {
        friendlyMessage = `Resume storage is temporarily unavailable: ${serverMessage || 'MinIO storage service failed.'}`;
      }

      setNotification({ type: 'error', message: friendlyMessage });
    } finally {
      setUploading(false);
    }
  };

  const deleteResume = async (id: string) => {
    if (!confirm('Are you sure you want to delete this resume?')) return;
    try {
      await api.delete(`/resumes/${id}`);
      if (selectedResumeId === id) {
        setSelectedResumeId(null);
      }
      fetchResumes();
    } catch (e) {
      console.error(e);
    }
  };

  const setDefaultResume = async (id: string) => {
    try {
      await api.put(`/resumes/${id}/default`);
      fetchResumes();
    } catch (e) {
      console.error(e);
    }
  };

  const downloadResume = async (resume: Resume) => {
    try {
      const response = await api.get(`/resumes/${resume.id}/download`, { responseType: 'blob' });
      const contentType = response.headers['content-type'] ? String(response.headers['content-type']) : 'application/pdf';
      const blob = new Blob([response.data], { type: contentType });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', resume.fileName);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (e) {
      console.error(e);
      alert('Download failed.');
    }
  };

  if (loading) {
    return (
      <AppShell title="My Resumes" description="Upload and parse your CV versions.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="My Resumes" description="Upload and parse your CV versions.">
      <div className="space-y-8">
        
        {/* Document Repository Header */}
        <div>
          <h2 className="text-heading font-normal text-jet-black">Document Repository</h2>
          <p className="text-body text-slate">Manage your resumes and inspect automated AI ATS scoring reports.</p>
        </div>

        {/* Localized Notifications Banner */}
        {notification && (
          <div className={`p-4 rounded-lg flex items-center justify-between border transition-all ${
            notification.type === 'success' 
              ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-700' 
              : 'bg-red-500/10 border-red-500/20 text-red-700'
          }`}>
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4" />
              <span className="text-body-sm font-medium">{notification.message}</span>
            </div>
            <button onClick={() => setNotification(null)} className="text-caption font-bold underline hover:opacity-80">
              Dismiss
            </button>
          </div>
        )}

        {/* Drag & Drop Upload Zone */}
        <div 
          onDragEnter={handleDrag}
          onDragOver={handleDrag}
          onDragLeave={handleDrag}
          onDrop={handleDrop}
          className={`border-2 border-dashed rounded-xl p-8 text-center transition-all flex flex-col items-center justify-center space-y-4 ${
            dragActive 
              ? 'border-jet-black bg-mist-gray/60' 
              : 'border-iron-gray/30 bg-paper-white hover:border-iron-gray/60'
          }`}
        >
          <div className="p-4 bg-mist-gray rounded-full">
            <FileUp className="w-8 h-8 text-jet-black" />
          </div>
          <div>
            <p className="text-body-sm font-bold text-jet-black">Drag & Drop Resume Here</p>
            <p className="text-caption text-slate mt-1">Supports PDF / DOCX formats up to 10MB</p>
          </div>
          
          <input
            type="file"
            id="resume-file-workspace"
            className="hidden"
            accept=".pdf,.docx"
            onChange={handleFileUpload}
            disabled={uploading}
          />
          <label htmlFor="resume-file-workspace" className="cursor-pointer">
            <Button as="span" disabled={uploading}>
              {uploading ? 'Uploading...' : 'Select Resume'}
            </Button>
          </label>
        </div>

        {/* Processing Checklist Pipeline */}
        {uploadStatus !== 'IDLE' && (
          <Card variant="white" className="p-6 space-y-4">
            <h4 className="text-body-sm font-bold text-jet-black">Document Processing Pipeline</h4>
            <div className="space-y-3">
              <div className="flex justify-between items-center text-caption font-semibold">
                <span>
                  {uploadStatus === 'UPLOADING' && `Uploading document... ${progressPercent}%`}
                  {uploadStatus === 'PARSING' && 'Running AI ATS Parser...'}
                  {uploadStatus === 'COMPLETED' && '✓ Processing complete'}
                  {uploadStatus === 'FAILED' && '✗ Upload failed'}
                </span>
              </div>
              <ProgressBar value={uploadStatus === 'UPLOADING' ? progressPercent : 100} />
              
              <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 pt-2 border-t border-iron-gray/10">
                <div className="flex items-center gap-2 text-caption">
                  <span className={`w-2.5 h-2.5 rounded-full ${uploadStatus !== 'FAILED' ? 'bg-jet-black' : 'bg-iron-gray/25'}`} />
                  Resume Uploaded
                </div>
                <div className="flex items-center gap-2 text-caption">
                  <span className={`w-2.5 h-2.5 rounded-full ${uploadStatus === 'PARSING' || uploadStatus === 'COMPLETED' ? 'bg-jet-black' : 'bg-iron-gray/25'}`} />
                  Resume Parsed
                </div>
                <div className="flex items-center gap-2 text-caption">
                  <span className={`w-2.5 h-2.5 rounded-full ${uploadStatus === 'COMPLETED' ? 'bg-jet-black' : 'bg-iron-gray/25'}`} />
                  Skills Extracted
                </div>
                <div className="flex items-center gap-2 text-caption">
                  <span className={`w-2.5 h-2.5 rounded-full ${uploadStatus === 'COMPLETED' ? 'bg-jet-black' : 'bg-iron-gray/25'}`} />
                  ATS Analysis Ready
                </div>
              </div>
            </div>
          </Card>
        )}

        {resumes.length === 0 ? (
          <div className="py-6 text-center">
            <p className="text-body-sm text-slate">No resumes uploaded yet. Drag a resume to start analysis.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 pt-4">
            {/* Left Resume List */}
            <div className="space-y-4">
              <h3 className="text-heading-sm font-normal text-jet-black">My Documents</h3>
              <div className="space-y-3">
                {resumes.map((resume) => (
                  <button
                    key={resume.id}
                    onClick={() => setSelectedResumeId(resume.id)}
                    className={`w-full text-left p-4 rounded-lg border transition-all flex justify-between items-start gap-4 ${
                      selectedResumeId === resume.id
                        ? 'bg-mist-gray border-jet-black'
                        : 'bg-paper-white border-iron-gray/15 hover:bg-mist-gray/40'
                    }`}
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <FileText className="w-4 h-4 text-slate" />
                        <span className="text-body-sm font-bold text-jet-black truncate max-w-[160px]">
                          {resume.fileName}
                        </span>
                      </div>
                      <p className="text-caption text-slate">
                        Version {resume.version} · {new Date(resume.uploadedAt).toLocaleDateString()}
                      </p>
                      {resume.isDefault && (
                        <span className="inline-block mt-2">
                          <Badge variant="active">Default</Badge>
                        </span>
                      )}
                    </div>

                    <div className="flex gap-1.5" onClick={(e) => e.stopPropagation()}>
                      {!resume.isDefault && (
                        <button
                          onClick={() => setDefaultResume(resume.id)}
                          title="Set Default"
                          className="p-1.5 rounded hover:bg-jet-black/5 text-slate hover:text-jet-black"
                        >
                          <Check className="w-4 h-4" />
                        </button>
                      )}
                      <button
                        onClick={() => downloadResume(resume)}
                        title="Download"
                        className="p-1.5 rounded hover:bg-jet-black/5 text-slate hover:text-jet-black"
                      >
                        <Download className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => deleteResume(resume.id)}
                        title="Delete"
                        className="p-1.5 rounded hover:bg-red-500/5 text-slate hover:text-red-600"
                      >
                        <Trash className="w-4 h-4" />
                      </button>
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* Right ATS & Intelligence Panel */}
            <div className="lg:col-span-2 space-y-4">
              <h3 className="text-heading-sm font-normal text-jet-black">ATS & Resume Intelligence</h3>
              {processing ? (
                <Card>
                  <div className="flex flex-col items-center justify-center py-12 text-center">
                    <Sparkles className="w-8 h-8 text-jet-black animate-spin mb-3" />
                    <p className="text-body-sm text-slate">Processing document content with AI...</p>
                  </div>
                </Card>
              ) : atsReport ? (
                <Card variant="white" className="space-y-8">
                  {/* Score Header */}
                  <div className="flex items-center justify-between pb-6 border-b border-iron-gray/10">
                    <div>
                      <p className="text-caption text-slate uppercase tracking-wider font-semibold">ATS Compatibility</p>
                      <p className="text-display font-normal text-jet-black leading-none mt-1">
                        {atsReport.score}<span className="text-subheading text-slate">/100</span>
                      </p>
                    </div>
                    <div className="w-48">
                      <ProgressBar value={atsReport.score} />
                    </div>
                  </div>

                  {/* Criteria Breakdowns */}
                  <div className="grid grid-cols-2 gap-4 pb-6 border-b border-iron-gray/10">
                    <div>
                      <span className="text-caption text-slate uppercase tracking-wider">Skills Score</span>
                      <div className="mt-1">
                        <ProgressBar value={atsReport.skillsScore} />
                      </div>
                    </div>
                    <div>
                      <span className="text-caption text-slate uppercase tracking-wider">Formatting Score</span>
                      <div className="mt-1">
                        <ProgressBar value={atsReport.formattingScore} />
                      </div>
                    </div>
                    <div>
                      <span className="text-caption text-slate uppercase tracking-wider">Experience Relevance</span>
                      <div className="mt-1">
                        <ProgressBar value={atsReport.experienceScore} />
                      </div>
                    </div>
                    <div>
                      <span className="text-caption text-slate uppercase tracking-wider">Education Alignment</span>
                      <div className="mt-1">
                        <ProgressBar value={atsReport.educationScore} />
                      </div>
                    </div>
                  </div>

                  {/* Warnings & Suggestions */}
                  {atsReport.feedback?.length > 0 && (
                    <div className="space-y-3">
                      <h4 className="text-body-sm font-bold text-jet-black flex items-center gap-2">
                        <AlertCircle className="w-4 h-4 text-amber-600" />
                        Optimization Warnings
                      </h4>
                      <ul className="list-disc pl-5 text-body-sm text-charcoal space-y-1.5">
                        {atsReport.feedback.map((f, i) => (
                          <li key={i}>{f}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {/* Parsed Details */}
                  {details && (
                    <div className="space-y-6 pt-6 border-t border-iron-gray/15">
                      <div className="space-y-2">
                        <h4 className="text-body-sm font-bold text-jet-black">Extracted Skill Keywords</h4>
                        <div className="flex flex-wrap gap-1.5">
                          {details.skills.map((skill) => (
                            <span key={skill} className="px-2.5 py-0.5 bg-mist-gray text-caption text-slate border border-iron-gray/10 rounded-full">
                              {skill}
                            </span>
                          ))}
                        </div>
                      </div>

                      <div className="space-y-2">
                        <h4 className="text-body-sm font-bold text-jet-black">Experience Highlight</h4>
                        <p className="text-body-sm text-charcoal leading-relaxed whitespace-pre-line">
                          {details.experienceSummary}
                        </p>
                      </div>

                      <div className="space-y-2">
                        <h4 className="text-body-sm font-bold text-jet-black">Education Highlight</h4>
                        <p className="text-body-sm text-charcoal leading-relaxed">
                          {details.educationSummary}
                        </p>
                      </div>

                      {details.suggestedRoles?.length > 0 && (
                        <div className="space-y-2">
                          <h4 className="text-body-sm font-bold text-jet-black">AI Suggested Career Roles</h4>
                          <div className="flex flex-wrap gap-1.5">
                            {details.suggestedRoles.map((role) => (
                              <Badge key={role} variant="active">{role}</Badge>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </Card>
              ) : (
                <Card className="text-center py-12">
                  <p className="text-body-sm text-slate">No intelligence reports parsed yet.</p>
                  <Button className="mt-4 mx-auto" onClick={() => selectedResumeId && triggerProcess(selectedResumeId)}>
                    Trigger AI Audit
                  </Button>
                </Card>
              )}
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
};
