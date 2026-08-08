import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'pill';
  children: React.ReactNode;
  as?: any;
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  children,
  className = '',
  as: Component = 'button',
  ...props
}) => {
  const baseStyles = 'px-4 py-2.5 rounded-lg text-body-sm font-medium transition-all select-none focus:outline-none flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed';
  
  const variants = {
    primary: 'bg-jet-black text-paper-white hover:bg-charcoal border border-transparent',
    secondary: 'bg-mist-gray text-jet-black hover:bg-iron-gray/10 border border-transparent',
    outline: 'bg-transparent text-jet-black hover:bg-mist-gray border border-iron-gray/30',
    pill: 'bg-jet-black text-paper-white hover:bg-charcoal rounded-full px-6 py-2',
  };

  return (
    <Component
      className={`${baseStyles} ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </Component>
  );
};
