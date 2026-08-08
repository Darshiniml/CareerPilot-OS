import React from 'react';
import { Sidebar } from './Sidebar';
import { TopHeader } from './TopHeader';

interface AppShellProps {
  children: React.ReactNode;
  title: string;
  description?: string;
}

export const AppShell: React.FC<AppShellProps> = ({ children, title, description }) => {
  return (
    <div className="min-h-screen bg-paper-white flex app-shell">
      {/* Left Sidebar */}
      <Sidebar />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 main-area">
        {/* Top Header */}
        <TopHeader title={title} description={description} />

        {/* Dynamic Page Content */}
        <main className="page-content">
          {children}
        </main>
      </div>
    </div>
  );
};
