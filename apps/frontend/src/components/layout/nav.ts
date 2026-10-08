import {
  LayoutDashboard,
  User,
  FileText,
  Briefcase,
  Target,
  KanbanSquare,
  Inbox,
  Send,
  Mic,
  PenLine,
  GraduationCap,
  BarChart3,
  Bot,
  Workflow,
  Settings,
  type LucideIcon,
} from 'lucide-react';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
}
export interface NavSection {
  title: string;
  items: NavItem[];
}

export const NAV: NavSection[] = [
  {
    title: 'Overview',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
      { to: '/copilot', label: 'Copilot', icon: Bot },
    ],
  },
  {
    title: 'You',
    items: [
      { to: '/profile', label: 'Profile', icon: User },
      { to: '/resumes', label: 'Resumes', icon: FileText },
    ],
  },
  {
    title: 'Job search',
    items: [
      { to: '/jobs', label: 'Jobs', icon: Briefcase },
      { to: '/opportunities', label: 'Opportunities', icon: Target },
      { to: '/applications', label: 'Applications', icon: KanbanSquare },
    ],
  },
  {
    title: 'Communication',
    items: [
      { to: '/inbox', label: 'Inbox', icon: Inbox },
      { to: '/follow-ups', label: 'Follow-ups', icon: Send },
      { to: '/cover-letters', label: 'Cover letters', icon: PenLine },
    ],
  },
  {
    title: 'Growth',
    items: [
      { to: '/interviews', label: 'Interview coach', icon: Mic },
      { to: '/learning', label: 'Learning', icon: GraduationCap },
      { to: '/analytics', label: 'Analytics', icon: BarChart3 },
    ],
  },
  {
    title: 'System',
    items: [
      { to: '/agents', label: 'Agents & automation', icon: Workflow },
      { to: '/settings', label: 'Settings', icon: Settings },
    ],
  },
];

export function titleForPath(pathname: string): string {
  for (const s of NAV) for (const i of s.items) if (pathname === i.to || pathname.startsWith(`${i.to}/`)) return i.label;
  return 'CareerPilot';
}
