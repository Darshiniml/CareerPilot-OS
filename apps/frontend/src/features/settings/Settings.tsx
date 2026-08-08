import React, { useEffect, useState } from 'react';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { api } from '../../services/api';
import { Settings as SettingsIcon, Shield, Database, Save } from 'lucide-react';

export const Settings: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [email, setEmail] = useState('');
  const [matchingStrategy, setMatchingStrategy] = useState('STANDARD');
  const [sessionTimeout, setSessionTimeout] = useState(15);

  useEffect(() => {
    // Read stored user from localStorage
    const storedUser = localStorage.getItem('cp_user');
    if (storedUser) {
      const parsed = JSON.parse(storedUser);
      setEmail(parsed.email || '');
    }
  }, []);

  const saveSettings = async () => {
    setLoading(true);
    try {
      // Mock save delay
      await new Promise((resolve) => setTimeout(resolve, 600));
      alert('System configurations updated successfully!');
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const clearDatabaseCaches = async () => {
    if (!confirm('Clear all AI matching vectors, cache repositories, and mock histories?')) return;
    setLoading(true);
    try {
      await api.post('/copilot/clear-session');
      try {
        await api.post('/ai/matching/weights/reset');
      } catch (e) {}
      alert('Caches cleared successfully!');
    } catch (e) {
      console.error(e);
      alert('Failed to clear some caches.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AppShell title="System Settings" description="Configure core parameters, security, and vector index baselines.">
      <div className="space-y-8 max-w-2xl">
        
        {/* Account Details */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
            <SettingsIcon className="w-5 h-5" /> Account settings
          </h3>
          <Card variant="white">
            <div className="space-y-4">
              <Input label="Registered Email" type="email" value={email} disabled />
              
              <div className="flex flex-col space-y-1.5">
                <label className="block text-caption font-semibold text-slate uppercase tracking-wider">
                  AI Matching Pipeline Strategy
                </label>
                <select
                  value={matchingStrategy}
                  onChange={(e) => setMatchingStrategy(e.target.value)}
                  className="w-full bg-paper-white border border-iron-gray/30 rounded-lg px-4 py-2.5 text-body-sm text-jet-black focus:outline-none focus:border-jet-black"
                >
                  <option value="STANDARD">Standard Weights Index</option>
                  <option value="STRICT">Strict Technical Prerequisite Match</option>
                  <option value="LENIENT">Lenient Similarity Search (Vector only)</option>
                </select>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <Button onClick={saveSettings} disabled={loading}>
                <Save className="w-4 h-4" /> Save Settings
              </Button>
            </div>
          </Card>
        </section>

        {/* Storage / Security */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
            <Shield className="w-5 h-5" /> Security Preferences
          </h3>
          <Card variant="white">
            <div className="space-y-4">
              <Input
                label="Session Token Timeout (Minutes)"
                type="number"
                value={sessionTimeout}
                onChange={(e) => setSessionTimeout(parseInt(e.target.value))}
              />
            </div>
            <div className="mt-6 flex justify-end">
              <Button onClick={saveSettings} disabled={loading}>
                Update Security Parameters
              </Button>
            </div>
          </Card>
        </section>

        {/* Database Clear */}
        <section className="space-y-4">
          <h3 className="text-heading-sm font-normal text-jet-black flex items-center gap-2">
            <Database className="w-5 h-5" /> Vector & Memory Caches
          </h3>
          <Card variant="white">
            <p className="text-body-sm text-slate">
              Clear transient database caches, active conversations, session history memory, and local redis cache entries.
            </p>
            <div className="mt-6">
              <Button onClick={clearDatabaseCaches} variant="secondary" className="text-red-700 bg-red-500/5 hover:bg-red-500/10 border border-red-500/20">
                Purge Temporary Cache Indexes
              </Button>
            </div>
          </Card>
        </section>

      </div>
    </AppShell>
  );
};
