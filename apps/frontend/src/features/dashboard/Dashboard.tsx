import React from 'react';
import { useAuthStore } from '../../store/authStore';
import { Navbar } from '../../components/layout/Navbar';

export const Dashboard: React.FC = () => {
  const user = useAuthStore((state) => state.user);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-50 flex flex-col">
      <Navbar />

      <main className="flex-1 p-8 max-w-7xl w-full mx-auto space-y-8">
        {/* Welcome Section */}
        <section className="bg-gradient-to-r from-indigo-950/40 via-zinc-900/50 to-zinc-900/30 border border-zinc-800/80 rounded-2xl p-8 backdrop-blur-md">
          <h1 className="text-3xl font-bold bg-gradient-to-r from-white via-zinc-200 to-zinc-400 bg-clip-text text-transparent">
            Welcome back, {user?.firstName || 'User'}!
          </h1>
          <p className="text-zinc-400 mt-2 max-w-2xl text-sm">
            Your career platform is primed and ready. Upload your resumes, parse job postings, and launch AI agents to automate your applications.
          </p>
        </section>

        {/* Stats Grid */}
        <section className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-zinc-900/40 border border-zinc-800 rounded-xl p-6 backdrop-blur-sm">
            <h3 className="text-zinc-500 text-xs font-semibold uppercase tracking-wider">Resumes Stored</h3>
            <p className="text-4xl font-extrabold text-white mt-2">0</p>
          </div>
          <div className="bg-zinc-900/40 border border-zinc-800 rounded-xl p-6 backdrop-blur-sm">
            <h3 className="text-zinc-500 text-xs font-semibold uppercase tracking-wider">Jobs Discovered</h3>
            <p className="text-4xl font-extrabold text-white mt-2">0</p>
          </div>
          <div className="bg-zinc-900/40 border border-zinc-800 rounded-xl p-6 backdrop-blur-sm">
            <h3 className="text-zinc-500 text-xs font-semibold uppercase tracking-wider">Active Workflows</h3>
            <p className="text-4xl font-extrabold text-white mt-2">0</p>
          </div>
        </section>

        {/* Modules Section */}
        <section className="space-y-4">
          <h2 className="text-xl font-bold text-zinc-100">Quick Operations</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {/* Resume Optimizer */}
            <div className="bg-zinc-900/30 hover:bg-zinc-900/50 border border-zinc-800/80 hover:border-indigo-500/30 rounded-xl p-6 transition-all duration-300 group">
              <span className="text-3xl">📄</span>
              <h3 className="text-lg font-bold text-zinc-100 mt-4 group-hover:text-indigo-400 transition-colors">Resumes</h3>
              <p className="text-zinc-400 text-xs mt-2">Manage multiple versions and optimize descriptions using AI.</p>
            </div>

            {/* Jobs Matcher */}
            <div className="bg-zinc-900/30 hover:bg-zinc-900/50 border border-zinc-800/80 hover:border-indigo-500/30 rounded-xl p-6 transition-all duration-300 group">
              <span className="text-3xl">🎯</span>
              <h3 className="text-lg font-bold text-zinc-100 mt-4 group-hover:text-indigo-400 transition-colors">AI Job Matching</h3>
              <p className="text-zinc-400 text-xs mt-2">Find matches, calculate scoring indexes, and get explanations.</p>
            </div>

            {/* Workflows */}
            <div className="bg-zinc-900/30 hover:bg-zinc-900/50 border border-zinc-800/80 hover:border-indigo-500/30 rounded-xl p-6 transition-all duration-300 group">
              <span className="text-3xl">⚡</span>
              <h3 className="text-lg font-bold text-zinc-100 mt-4 group-hover:text-indigo-400 transition-colors">Agent Workflows</h3>
              <p className="text-zinc-400 text-xs mt-2">Run asynchronous multi-agent automation pipelines.</p>
            </div>

            {/* Connectors */}
            <div className="bg-zinc-900/30 hover:bg-zinc-900/50 border border-zinc-800/80 hover:border-indigo-500/30 rounded-xl p-6 transition-all duration-300 group">
              <span className="text-3xl">🔌</span>
              <h3 className="text-lg font-bold text-zinc-100 mt-4 group-hover:text-indigo-400 transition-colors">Connectors SDK</h3>
              <p className="text-zinc-400 text-xs mt-2">Monitor board crawling APIs and check Greenhouse connection.</p>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
};
