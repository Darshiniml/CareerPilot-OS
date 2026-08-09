import { create } from 'zustand';
import { api } from '../services/api';

export interface ProfileInfo {
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

export interface ResumeItem {
  id: string;
  title: string;
  storageKey: string;
  isDefault: boolean;
  fileSize: number;
  uploadedAt: string;
}

export interface ResumeIntelligence {
  skills: string[];
  experienceLevel: string;
  targetRoles: string[];
  summary?: string;
}

export interface DerivedCriteria {
  targetRoles: string[];
  coreSkills: string[];
  locations: string[];
  remotePreference: string;
}

interface UserContextState {
  user: any | null;
  profile: ProfileInfo | null;
  defaultResume: ResumeItem | null;
  resumes: ResumeItem[];
  resumeIntelligence: ResumeIntelligence | null;
  derivedCriteria: DerivedCriteria;
  loading: boolean;
  error: string | null;

  fetchUserContext: () => Promise<void>;
  setUser: (user: any) => void;
  clearUserContext: () => void;
}

export const useUserContextStore = create<UserContextState>((set) => ({
  user: null,
  profile: null,
  defaultResume: null,
  resumes: [],
  resumeIntelligence: null,
  derivedCriteria: {
    targetRoles: ['Software Engineer', 'Backend Developer'],
    coreSkills: ['Java', 'Spring Boot', 'SQL', 'TypeScript'],
    locations: ['Bangalore, IN', 'Remote'],
    remotePreference: 'REMOTE',
  },
  loading: false,
  error: null,

  setUser: (user) => set({ user }),

  clearUserContext: () =>
    set({
      user: null,
      profile: null,
      defaultResume: null,
      resumes: [],
      resumeIntelligence: null,
      derivedCriteria: {
        targetRoles: [],
        coreSkills: [],
        locations: [],
        remotePreference: 'REMOTE',
      },
    }),

  fetchUserContext: async () => {
    set({ loading: true, error: null });
    try {
      // 1. Load User Profile
      let profData: ProfileInfo | null = null;
      try {
        const profRes = await api.get('/profile');
        if (profRes.data) {
          profData = profRes.data;
        }
      } catch (e) {
        console.warn('Profile fetch warning:', e);
      }

      // 2. Load Resumes
      let resumeList: ResumeItem[] = [];
      let defaultRes: ResumeItem | null = null;
      try {
        const resumesRes = await api.get('/resumes');
        if (resumesRes.data && Array.isArray(resumesRes.data)) {
          resumeList = resumesRes.data;
          defaultRes = resumeList.find((r) => r.isDefault) || resumeList[0] || null;
        }
      } catch (e) {
        console.warn('Resumes fetch warning:', e);
      }

      // 3. Load Resume Intelligence for Default Resume
      let intelData: ResumeIntelligence | null = null;
      if (defaultRes) {
        try {
          const intelRes = await api.get(`/ai/resume/parse-summary?resumeId=${defaultRes.id}`);
          if (intelRes.data) {
            intelData = {
              skills: intelRes.data.skills || [],
              experienceLevel: intelRes.data.experienceLevel || 'Mid Level',
              targetRoles: intelRes.data.recommendedRoles || [],
              summary: intelRes.data.summary,
            };
          }
        } catch (e) {
          console.warn('Resume intelligence fetch warning:', e);
        }
      }

      // 4. Derive Career Search Criteria
      const rolesSet = new Set<string>();
      if (profData?.preferredRole) rolesSet.add(profData.preferredRole);
      if (intelData?.targetRoles) intelData.targetRoles.forEach((r) => rolesSet.add(r));
      if (rolesSet.size === 0) {
        rolesSet.add('Software Engineer');
        rolesSet.add('Backend Developer');
      }

      const skillsSet = new Set<string>();
      if (intelData?.skills) intelData.skills.forEach((s) => skillsSet.add(s));
      if (skillsSet.size === 0) {
        skillsSet.add('Java');
        skillsSet.add('Spring Boot');
        skillsSet.add('TypeScript');
        skillsSet.add('PostgreSQL');
      }

      const locationsSet = new Set<string>();
      if (profData?.preferredLocation) locationsSet.add(profData.preferredLocation);
      if (profData?.location) locationsSet.add(profData.location);
      if (locationsSet.size === 0) {
        locationsSet.add('Bangalore, IN');
        locationsSet.add('Remote');
      }

      set({
        profile: profData,
        resumes: resumeList,
        defaultResume: defaultRes,
        resumeIntelligence: intelData,
        derivedCriteria: {
          targetRoles: Array.from(rolesSet),
          coreSkills: Array.from(skillsSet),
          locations: Array.from(locationsSet),
          remotePreference: profData?.remotePreference || 'REMOTE',
        },
        loading: false,
      });
    } catch (err: any) {
      set({ error: err.message || 'Failed to load user context', loading: false });
    }
  },
}));
