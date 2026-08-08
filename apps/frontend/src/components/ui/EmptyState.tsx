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
    <div className="flex flex-col items-center justify-center text-center p-12 bg-mist-gray rounded-lg border border-iron-gray/10 select-none">
      <div className="w-12 h-12 rounded-full bg-paper-white flex items-center justify-center border border-iron-gray/10 text-slate mb-4">
        <Inbox className="w-6 h-6" />
      </div>
      <h4 className="text-body font-bold text-jet-black tracking-tight">{title}</h4>
      <p className="text-body-sm text-slate mt-1 max-w-sm">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
};
