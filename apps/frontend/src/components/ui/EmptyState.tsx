import React from 'react';
import { Inbox } from 'lucide-react';

interface EmptyStateProps {
  title: string;
  description: string;
  action?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  action,
}) => {
  return (
    <div className="flex flex-col items-center justify-center text-center p-12 bg-slate-900/60 backdrop-blur-md rounded-2xl border border-white/10 select-none">
      <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 flex items-center justify-center border border-indigo-500/20 text-indigo-400 mb-4 shadow-lg shadow-indigo-500/10">
        <Inbox className="w-6 h-6" />
      </div>
      <h4 className="text-base font-bold text-slate-100 tracking-tight font-heading">{title}</h4>
      <p className="text-sm text-slate-400 mt-1 max-w-sm">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
};
