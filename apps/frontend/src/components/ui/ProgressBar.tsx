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
        <div className="flex justify-between text-caption font-semibold text-jet-black mb-1.5">
          <span>Progress</span>
          <span>{percentage}%</span>
        </div>
      )}
      <div className="w-full bg-mist-gray h-2.5 rounded-full overflow-hidden border border-iron-gray/10">
        <div
          className="bg-jet-black h-full transition-all duration-500 ease-out"
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
};
