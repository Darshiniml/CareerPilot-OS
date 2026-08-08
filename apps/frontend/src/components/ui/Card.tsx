import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  variant?: 'white' | 'gray';
}

export const Card: React.FC<CardProps> = ({
  children,
  variant = 'gray',
  className = '',
  ...props
}) => {
  const surfaceColor = variant === 'white' ? 'bg-paper-white' : 'bg-mist-gray';
  
  return (
    <div
      className={`rounded-lg border border-iron-gray/15 p-6 ${surfaceColor} ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};
