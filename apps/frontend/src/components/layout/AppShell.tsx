import React, { useState } from 'react';
import { Sidebar } from './Sidebar';
import { TopHeader } from './TopHeader';

interface AppShellProps {
  children: React.ReactNode;
  title: string;
  description?: string;
}

export const AppShell: React.FC<AppShellProps> = ({ children, title, description }) => {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="min-h-screen bg-paper-white flex app-shell relative overflow-x-hidden">
      {/* Desktop & Mobile Responsive Sidebar */}
      <Sidebar mobileOpen={mobileOpen} onCloseMobile={() => setMobileOpen(false)} />

      {/* Backdrop overlay for mobile drawer */}
      {mobileOpen && (
        <div
          onClick={() => setMobileOpen(false)}
          className="fixed inset-0 bg-slate-950/70 backdrop-blur-sm z-40 lg:hidden"
        />
      )}

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 main-area">
        {/* Top Header */}
        <TopHeader
          title={title}
          description={description}
          onToggleMobileMenu={() => setMobileOpen(!mobileOpen)}
        />

        {/* Dynamic Page Content */}
        <main className="page-content">
          {children}
        </main>
      </div>
    </div>
  );
};
