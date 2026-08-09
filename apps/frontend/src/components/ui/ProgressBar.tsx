import React from 'react';

interface ProgressBarProps {
  value: number; // 0 to 100
  showLabel?: boolean;
}

export const ProgressBar: React.FC<ProgressBarProps> = ({
  value,
  showLabel = false,
}) => {
  const percentage = Math.max(0, Math.min(100, value));

  return (
    <div className="w-full select-none">
      {showLabel && (
        <div className="flex justify-between text-xs font-semibold text-slate-300 mb-1.5">
          <span>Progress</span>
          <span>{percentage}%</span>
        </div>
      )}
      <div className="w-full bg-slate-800/80 h-2.5 rounded-full overflow-hidden border border-white/5">
        <div
          className="bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 h-full transition-all duration-500 ease-out shadow-sm shadow-indigo-500/50"
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
};
