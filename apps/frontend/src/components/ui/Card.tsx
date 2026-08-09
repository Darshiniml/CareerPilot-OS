import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  variant?: 'white' | 'gray' | 'glass';
}

export const Card: React.FC<CardProps> = ({
  children,
  variant = 'glass',
  className = '',
  ...props
}) => {
  const styles = {
    glass: 'bg-slate-900/60 backdrop-blur-md border border-white/10 text-slate-100 shadow-xl shadow-black/20',
    white: 'bg-slate-900/80 backdrop-blur-md border border-white/10 text-slate-100 shadow-xl shadow-black/20',
    gray: 'bg-slate-800/40 backdrop-blur-sm border border-white/5 text-slate-200',
  };
  
  return (
    <div
      className={`rounded-2xl p-6 transition-all duration-200 ${styles[variant]} ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};
