import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { LoadingState } from '../../components/ui/LoadingState';
import { api } from '../../services/api';
import { useUserContextStore } from '../../store/userContextStore';
import { Plus, Trash, Globe, Linkedin, Github } from 'lucide-react';

interface ProfileData {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  location: string;
  linkedinUrl: string;
  githubUrl: string;
  portfolioUrl: string;
  preferredRole: string;
  preferredIndustry: string;
  preferredLocation: string;
  remotePreference: string;
  minSalary: number;
}

interface Experience {
  experienceId?: string;
  companyName: string;
  title: string;
  startDate: string;
  endDate: string;
  description: string;
}

interface Education {
  educationId?: string;
  institution: string;
  degree: string;
  fieldOfStudy: string;
  startDate: string;
  endDate: string;
}

interface Project {
  projectId?: string;
  projectName: string;
  description: string;
  technologiesUsed: string;
  projectUrl: string;
}

interface Certification {
  certificationId?: string;
  certificationName: string;
  issuingOrganization: string;
  issueDate: string;
  credentialId: string;
}

export const Profile: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [profile, setProfile] = useState<ProfileData>({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    location: '',
    linkedinUrl: '',
    githubUrl: '',
    portfolioUrl: '',
    preferredRole: '',
    preferredIndustry: '',
    preferredLocation: '',
    remotePreference: 'REMOTE',
    minSalary: 0,
  });

  const [experiences, setExperiences] = useState<Experience[]>([]);
  const [educations, setEducations] = useState<Education[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [certifications, setCertifications] = useState<Certification[]>([]);

  // Temp form states for adding items
  const [newExp, setNewExp] = useState<Experience>({ companyName: '', title: '', startDate: '', endDate: '', description: '' });
  const [newEdu, setNewEdu] = useState<Education>({ institution: '', degree: '', fieldOfStudy: '', startDate: '', endDate: '' });
  const [newProj, setNewProj] = useState<Project>({ projectName: '', description: '', technologiesUsed: '', projectUrl: '' });
  const [newCert, setNewCert] = useState<Certification>({ certificationName: '', issuingOrganization: '', issueDate: '', credentialId: '' });

  const fetchProfile = async () => {
    try {
      const res = await api.get('/profile');
      const data = res.data;
      if (data) {
        setProfile({
          firstName: data.firstName || '',
          lastName: data.lastName || '',
          email: data.email || '',
          phone: data.phone || '',
          location: data.location || '',
          linkedinUrl: data.linkedinUrl || '',
          githubUrl: data.githubUrl || '',
          portfolioUrl: data.portfolioUrl || '',
          preferredRole: data.preferredRole || '',
          preferredIndustry: data.preferredIndustry || '',
          preferredLocation: data.preferredLocation || '',
          remotePreference: data.remotePreference || 'REMOTE',
          minSalary: data.minSalary || 0,
        });

        // Load details lists
        const expRes = await api.get('/profile/experience');
        setExperiences(expRes.data?.content || expRes.data || []);

        const eduRes = await api.get('/profile/education');
        setEducations(eduRes.data?.content || eduRes.data || []);

        const projRes = await api.get('/profile/projects');
        setProjects(projRes.data?.content || projRes.data || []);

        const certRes = await api.get('/profile/certifications');
        setCertifications(certRes.data?.content || certRes.data || []);
      }
    } catch (e) {
      console.error('Error loading profile:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  const handleProfileChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setProfile((prev) => ({ ...prev, [name]: value }));
  };

  const savePersonalInfo = async () => {
    setSaving(true);
    try {
      await api.put('/profile/personal-info', {
        firstName: profile.firstName,
        lastName: profile.lastName,
        email: profile.email,
        phone: profile.phone,
        location: profile.location,
      });
      useUserContextStore.getState().fetchUserContext();
      alert('Personal information updated!');
    } catch (e) {
      console.error(e);
      alert('Failed to save personal information.');
    } finally {
      setSaving(false);
    }
  };

  const savePreferences = async () => {
    setSaving(true);
    try {
      await api.put('/profile/preferences', {
        preferredRole: profile.preferredRole,
        preferredIndustry: profile.preferredIndustry,
        preferredLocation: profile.preferredLocation,
        remotePreference: profile.remotePreference,
        minSalary: profile.minSalary,
      });
      useUserContextStore.getState().fetchUserContext();
      alert('Career preferences updated!');
    } catch (e) {
      console.error(e);
      alert('Failed to save career preferences.');
    } finally {
      setSaving(false);
    }
  };

  const saveSocialLinks = async () => {
    setSaving(true);
    try {
      await api.put('/profile/social-links', {
        linkedinUrl: profile.linkedinUrl,
        githubUrl: profile.githubUrl,
        portfolioUrl: profile.portfolioUrl,
      });
      useUserContextStore.getState().fetchUserContext();
      alert('Social links updated!');
    } catch (e) {
      console.error(e);
      alert('Failed to save social links.');
    } finally {
      setSaving(false);
    }
  };

  // Add handlers
  const addExperience = async () => {
    if (!newExp.companyName || !newExp.title) return;
    try {
      await api.post('/profile/experience', newExp);
      setNewExp({ companyName: '', title: '', startDate: '', endDate: '', description: '' });
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  const addEducation = async () => {
    if (!newEdu.institution || !newEdu.degree) return;
    try {
      await api.post('/profile/education', newEdu);
      setNewEdu({ institution: '', degree: '', fieldOfStudy: '', startDate: '', endDate: '' });
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  const addProject = async () => {
    if (!newProj.projectName || !newProj.description) return;
    try {
      await api.post('/profile/projects', newProj);
      setNewProj({ projectName: '', description: '', technologiesUsed: '', projectUrl: '' });
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  const addCertification = async () => {
    if (!newCert.certificationName || !newCert.issuingOrganization) return;
    try {
      await api.post('/profile/certifications', newCert);
      setNewCert({ certificationName: '', issuingOrganization: '', issueDate: '', credentialId: '' });
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  // Delete handlers
  const deleteExperience = async (id: string) => {
    try {
      await api.delete(`/profile/experience/${id}`);
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  const deleteEducation = async (id: string) => {
    try {
      await api.delete(`/profile/education/${id}`);
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  const deleteProject = async (id: string) => {
    try {
      await api.delete(`/profile/projects/${id}`);
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  const deleteCertification = async (id: string) => {
    try {
      await api.delete(`/profile/certifications/${id}`);
      fetchProfile();
    } catch (e) {
      console.error(e);
    }
  };

  if (loading) {
    return (
      <AppShell title="Profile" description="Manage your professional background and settings.">
        <LoadingState />
      </AppShell>
    );
  }

  return (
    <AppShell title="Profile" description="Manage your professional background and settings.">
      <div className="space-y-12">
        
        {/* Personal Details */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Personal Information</h3>
          <Card variant="white">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Input label="First Name" name="firstName" value={profile.firstName} onChange={handleProfileChange} />
              <Input label="Last Name" name="lastName" value={profile.lastName} onChange={handleProfileChange} />
              <Input label="Email" name="email" type="email" value={profile.email} onChange={handleProfileChange} />
              <Input label="Phone" name="phone" value={profile.phone} onChange={handleProfileChange} />
              <div className="md:col-span-2">
                <Input label="Location (City, Country)" name="location" value={profile.location} onChange={handleProfileChange} />
              </div>
            </div>
            <div className="mt-6 flex justify-end">
              <Button onClick={savePersonalInfo} disabled={saving}>
                Save Details
              </Button>
            </div>
          </Card>
        </section>

        {/* Preferences */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Career Preferences</h3>
          <Card variant="white">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Input label="Preferred Role" name="preferredRole" value={profile.preferredRole} onChange={handleProfileChange} placeholder="e.g. Senior Software Engineer" />
              <Input label="Preferred Industry" name="preferredIndustry" value={profile.preferredIndustry} onChange={handleProfileChange} placeholder="e.g. FinTech / SaaS" />
              <Input label="Preferred Location" name="preferredLocation" value={profile.preferredLocation} onChange={handleProfileChange} placeholder="e.g. Bangalore, IN" />
              
              <div className="flex flex-col space-y-1.5">
                <label className="block text-caption font-medium text-slate uppercase tracking-wider select-none">Remote Preference</label>
                <select
                  name="remotePreference"
                  value={profile.remotePreference}
                  onChange={handleProfileChange}
                  className="w-full bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2.5 text-body-sm text-jet-black focus:outline-none focus:border-jet-black focus:ring-1 focus:ring-jet-black transition-all"
                >
                  <option value="REMOTE">Remote Only</option>
                  <option value="HYBRID">Hybrid</option>
                  <option value="ONSITE">On-Site Only</option>
                </select>
              </div>

              <div className="md:col-span-2">
                <Input label="Minimum Annual Salary Expectation ($)" type="number" name="minSalary" value={profile.minSalary} onChange={handleProfileChange} />
              </div>
            </div>
            <div className="mt-6 flex justify-end">
              <Button onClick={savePreferences} disabled={saving}>
                Save Preferences
              </Button>
            </div>
          </Card>
        </section>

        {/* Social Links */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Social & Professional Profiles</h3>
          <Card variant="white">
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <Linkedin className="w-5 h-5 text-slate" />
                <Input name="linkedinUrl" value={profile.linkedinUrl} onChange={handleProfileChange} placeholder="https://linkedin.com/in/username" />
              </div>
              <div className="flex items-center gap-3">
                <Github className="w-5 h-5 text-slate" />
                <Input name="githubUrl" value={profile.githubUrl} onChange={handleProfileChange} placeholder="https://github.com/username" />
              </div>
              <div className="flex items-center gap-3">
                <Globe className="w-5 h-5 text-slate" />
                <Input name="portfolioUrl" value={profile.portfolioUrl} onChange={handleProfileChange} placeholder="https://myportfolio.com" />
              </div>
            </div>
            <div className="mt-6 flex justify-end">
              <Button onClick={saveSocialLinks} disabled={saving}>
                Save Links
              </Button>
            </div>
          </Card>
        </section>

        {/* Work Experience */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Work Experience</h3>
          <Card variant="white" className="space-y-6">
            {/* List */}
            {experiences.map((exp) => (
              <div key={exp.experienceId} className="flex justify-between items-start pb-4 border-b border-iron-gray/10 last:border-b-0 last:pb-0">
                <div>
                  <h4 className="text-body font-bold text-jet-black">{exp.title}</h4>
                  <p className="text-body-sm text-slate">{exp.companyName}</p>
                  <p className="text-caption text-ash-gray mt-1">{exp.startDate} — {exp.endDate || 'Present'}</p>
                  <p className="text-body-sm text-charcoal mt-2 whitespace-pre-line">{exp.description}</p>
                </div>
                <button
                  onClick={() => deleteExperience(exp.experienceId!)}
                  className="text-slate hover:text-red-600 p-1 rounded hover:bg-red-500/5 transition-all"
                >
                  <Trash className="w-4 h-4" />
                </button>
              </div>
            ))}

            {/* Form */}
            <div className="pt-4 border-t border-iron-gray/15 space-y-4">
              <h4 className="text-body-sm font-bold text-jet-black">+ Add Experience</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input placeholder="Company Name" value={newExp.companyName} onChange={(e) => setNewExp({ ...newExp, companyName: e.target.value })} />
                <Input placeholder="Job Title" value={newExp.title} onChange={(e) => setNewExp({ ...newExp, title: e.target.value })} />
                <Input placeholder="Start Date (e.g. Aug 2024)" value={newExp.startDate} onChange={(e) => setNewExp({ ...newExp, startDate: e.target.value })} />
                <Input placeholder="End Date (e.g. Present)" value={newExp.endDate} onChange={(e) => setNewExp({ ...newExp, endDate: e.target.value })} />
                <div className="md:col-span-2">
                  <Input placeholder="Role Description & Achievements" value={newExp.description} onChange={(e) => setNewExp({ ...newExp, description: e.target.value })} />
                </div>
              </div>
              <Button onClick={addExperience} variant="secondary">
                <Plus className="w-4 h-4" /> Add Experience Record
              </Button>
            </div>
          </Card>
        </section>

        {/* Education */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Education</h3>
          <Card variant="white" className="space-y-6">
            {/* List */}
            {educations.map((edu) => (
              <div key={edu.educationId} className="flex justify-between items-start pb-4 border-b border-iron-gray/10 last:border-b-0 last:pb-0">
                <div>
                  <h4 className="text-body font-bold text-jet-black">{edu.degree} in {edu.fieldOfStudy}</h4>
                  <p className="text-body-sm text-slate">{edu.institution}</p>
                  <p className="text-caption text-ash-gray mt-1">{edu.startDate} — {edu.endDate || 'Present'}</p>
                </div>
                <button
                  onClick={() => deleteEducation(edu.educationId!)}
                  className="text-slate hover:text-red-600 p-1 rounded hover:bg-red-500/5 transition-all"
                >
                  <Trash className="w-4 h-4" />
                </button>
              </div>
            ))}

            {/* Form */}
            <div className="pt-4 border-t border-iron-gray/15 space-y-4">
              <h4 className="text-body-sm font-bold text-jet-black">+ Add Education</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input placeholder="Institution Name" value={newEdu.institution} onChange={(e) => setNewEdu({ ...newEdu, institution: e.target.value })} />
                <Input placeholder="Degree (e.g. Bachelor of Science)" value={newEdu.degree} onChange={(e) => setNewEdu({ ...newEdu, degree: e.target.value })} />
                <Input placeholder="Field of Study (e.g. Computer Science)" value={newEdu.fieldOfStudy} onChange={(e) => setNewEdu({ ...newEdu, fieldOfStudy: e.target.value })} />
                <Input placeholder="Start & End Date" value={newEdu.startDate} onChange={(e) => setNewEdu({ ...newEdu, startDate: e.target.value, endDate: 'Present' })} />
              </div>
              <Button onClick={addEducation} variant="secondary">
                <Plus className="w-4 h-4" /> Add Education Record
              </Button>
            </div>
          </Card>
        </section>

        {/* Projects */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Featured Projects</h3>
          <Card variant="white" className="space-y-6">
            {/* List */}
            {projects.map((proj) => (
              <div key={proj.projectId} className="flex justify-between items-start pb-4 border-b border-iron-gray/10 last:border-b-0 last:pb-0">
                <div>
                  <h4 className="text-body font-bold text-jet-black">{proj.projectName}</h4>
                  <p className="text-body-sm text-charcoal mt-1 whitespace-pre-line">{proj.description}</p>
                  <p className="text-caption text-slate mt-2"><strong>Tech Stack:</strong> {proj.technologiesUsed}</p>
                  {proj.projectUrl && (
                    <a href={proj.projectUrl} target="_blank" rel="noreferrer" className="text-caption text-faded-teal hover:underline mt-1 block">
                      {proj.projectUrl}
                    </a>
                  )}
                </div>
                <button
                  onClick={() => deleteProject(proj.projectId!)}
                  className="text-slate hover:text-red-600 p-1 rounded hover:bg-red-500/5 transition-all"
                >
                  <Trash className="w-4 h-4" />
                </button>
              </div>
            ))}

            {/* Form */}
            <div className="pt-4 border-t border-iron-gray/15 space-y-4">
              <h4 className="text-body-sm font-bold text-jet-black">+ Add Project</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input placeholder="Project Name" value={newProj.projectName} onChange={(e) => setNewProj({ ...newProj, projectName: e.target.value })} />
                <Input placeholder="Technologies Used (e.g. React, Spring Boot)" value={newProj.technologiesUsed} onChange={(e) => setNewProj({ ...newProj, technologiesUsed: e.target.value })} />
                <div className="md:col-span-2">
                  <Input placeholder="Project URL" value={newProj.projectUrl} onChange={(e) => setNewProj({ ...newProj, projectUrl: e.target.value })} />
                </div>
                <div className="md:col-span-2">
                  <Input placeholder="Project Description" value={newProj.description} onChange={(e) => setNewProj({ ...newProj, description: e.target.value })} />
                </div>
              </div>
              <Button onClick={addProject} variant="secondary">
                <Plus className="w-4 h-4" /> Add Project Record
              </Button>
            </div>
          </Card>
        </section>

        {/* Certifications */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black">Certifications</h3>
          <Card variant="white" className="space-y-6">
            {/* List */}
            {certifications.map((cert) => (
              <div key={cert.certificationId} className="flex justify-between items-start pb-4 border-b border-iron-gray/10 last:border-b-0 last:pb-0">
                <div>
                  <h4 className="text-body font-bold text-jet-black">{cert.certificationName}</h4>
                  <p className="text-body-sm text-slate">{cert.issuingOrganization}</p>
                  <p className="text-caption text-ash-gray mt-1">Issued: {cert.issueDate} | Credential: {cert.credentialId}</p>
                </div>
                <button
                  onClick={() => deleteCertification(cert.certificationId!)}
                  className="text-slate hover:text-red-600 p-1 rounded hover:bg-red-500/5 transition-all"
                >
                  <Trash className="w-4 h-4" />
                </button>
              </div>
            ))}

            {/* Form */}
            <div className="pt-4 border-t border-iron-gray/15 space-y-4">
              <h4 className="text-body-sm font-bold text-jet-black">+ Add Certification</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input placeholder="Certification Name" value={newCert.certificationName} onChange={(e) => setNewCert({ ...newCert, certificationName: e.target.value })} />
                <Input placeholder="Issuing Organization" value={newCert.issuingOrganization} onChange={(e) => setNewCert({ ...newCert, issuingOrganization: e.target.value })} />
                <Input placeholder="Issue Date" value={newCert.issueDate} onChange={(e) => setNewCert({ ...newCert, issueDate: e.target.value })} />
                <Input placeholder="Credential ID / URL" value={newCert.credentialId} onChange={(e) => setNewCert({ ...newCert, credentialId: e.target.value })} />
              </div>
              <Button onClick={addCertification} variant="secondary">
                <Plus className="w-4 h-4" /> Add Certification
              </Button>
            </div>
          </Card>
        </section>

      </div>
    </AppShell>
  );
};
