import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input: React.FC<InputProps> = ({
  label,
  error,
  className = '',
  id,
  ...props
}) => {
  return (
    <div className="space-y-1.5 w-full">
      {label && (
        <label
          htmlFor={id}
          className="block text-caption font-medium text-slate uppercase tracking-wider select-none"
        >
          {label}
        </label>
      )}
      <input
        id={id}
        className={`w-full bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2.5 text-body-sm text-jet-black placeholder-ash-gray focus:outline-none focus:border-jet-black focus:ring-1 focus:ring-jet-black transition-all ${
          error ? 'border-red-500 focus:ring-red-500' : ''
        } ${className}`}
        {...props}
      />
      {error && <p className="text-caption text-red-600 mt-1 select-none">{error}</p>}
    </div>
  );
};
