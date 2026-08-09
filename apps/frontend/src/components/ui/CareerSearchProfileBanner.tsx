import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useUserContextStore } from '../../store/userContextStore';
import { Sparkles, Edit3, MapPin, Briefcase, Code } from 'lucide-react';

export const CareerSearchProfileBanner: React.FC = () => {
  const navigate = useNavigate();
  const derivedCriteria = useUserContextStore((state) => state.derivedCriteria);

  return (
    <div className="bg-gradient-to-r from-indigo-950/80 via-slate-900 to-slate-950 p-6 rounded-2xl border border-indigo-500/20 shadow-xl backdrop-blur-xl mb-8">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">
              <Sparkles className="w-4 h-4" />
            </span>
            <span className="text-caption font-bold uppercase tracking-wider text-indigo-300">
              Derived Career Search Profile
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-6 text-body-sm text-slate-200">
            {/* Roles */}
            <div className="flex items-start gap-2">
              <Briefcase className="w-4 h-4 text-indigo-400 mt-0.5" />
              <div>
                <span className="text-caption text-slate-400 block font-medium">Target Roles</span>
                <span className="font-semibold text-white">
                  {derivedCriteria.targetRoles.slice(0, 3).join(' • ') || 'Software Engineer'}
                </span>
              </div>
            </div>

            {/* Skills */}
            <div className="flex items-start gap-2">
              <Code className="w-4 h-4 text-purple-400 mt-0.5" />
              <div>
                <span className="text-caption text-slate-400 block font-medium">Core Skills</span>
                <span className="font-semibold text-white">
                  {derivedCriteria.coreSkills.slice(0, 4).join(', ') || 'Java, Spring Boot'}
                </span>
              </div>
            </div>

            {/* Locations */}
            <div className="flex items-start gap-2">
              <MapPin className="w-4 h-4 text-pink-400 mt-0.5" />
              <div>
                <span className="text-caption text-slate-400 block font-medium">Locations & Mode</span>
                <span className="font-semibold text-white">
                  {derivedCriteria.locations.join(', ')} ({derivedCriteria.remotePreference})
                </span>
              </div>
            </div>
          </div>
        </div>

        <div>
          <button
            onClick={() => navigate('/profile')}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-white text-body-sm font-semibold border border-white/15 transition-all select-none whitespace-nowrap"
          >
            <Edit3 className="w-4 h-4" /> Edit Profile
          </button>
        </div>
      </div>
    </div>
  );
};
