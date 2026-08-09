import React from 'react';

interface LoadingStateProps {
  label?: string;
}

export const LoadingState: React.FC<LoadingStateProps> = ({ label }) => {
  return (
    <div className="w-full space-y-4 py-8 animate-pulse select-none">
      {label && (
        <p className="text-caption font-semibold text-indigo-400 tracking-wide uppercase">
          {label}
        </p>
      )}
      <div className="h-6 bg-slate-800/80 rounded-lg w-1/4" />
      <div className="space-y-2">
        <div className="h-4 bg-slate-800/60 rounded-lg w-full" />
        <div className="h-4 bg-slate-800/60 rounded-lg w-5/6" />
        <div className="h-4 bg-slate-800/60 rounded-lg w-4/6" />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
        <div className="h-28 bg-slate-800/80 rounded-2xl border border-white/5" />
        <div className="h-28 bg-slate-800/80 rounded-2xl border border-white/5" />
      </div>
    </div>
  );
};
