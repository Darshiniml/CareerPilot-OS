import React from 'react';

export const LoadingState: React.FC = () => {
  return (
    <div className="w-full space-y-4 py-8 animate-pulse select-none">
      <div className="h-6 bg-mist-gray rounded-md w-1/4" />
      <div className="space-y-2">
        <div className="h-4 bg-mist-gray rounded-md w-full" />
        <div className="h-4 bg-mist-gray rounded-md w-5/6" />
        <div className="h-4 bg-mist-gray rounded-md w-4/6" />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
        <div className="h-24 bg-mist-gray rounded-lg" />
        <div className="h-24 bg-mist-gray rounded-lg" />
      </div>
    </div>
  );
};
