import { useState, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Award, Briefcase, FolderGit2, GraduationCap, Link2, SlidersHorizontal, UserRound } from 'lucide-react';
import { profileApi } from '../../api/endpoints';
import { qk, useProfile } from '../../api/queries';
import type {
  Certification,
  Education,
  EmploymentType,
  Experience,
  Preferences,
  Profile,
  Project,
  SalaryPeriod,
  SocialLinks,
  WorkStyle,
} from '../../api/types';
import {
  Button,
  Card,
  CardHeader,
  Checkbox,
  ErrorState,
  Input,
  PageHeader,
  Select,
  Skeleton,
} from '../../components/ui';
import { toast } from '../../store/toast';
import { formatDate, humanize } from '../../lib/format';
import { TagInput } from './TagInput';
import { SectionList } from './SectionList';
import { blankToNull, isValidEmail, isValidUrl } from './validation';

/* ---------------------------------------------------------- personal info */
function PersonalInfoCard({ profile }: { profile: Profile }) {
  const qc = useQueryClient();
  const [firstName, setFirstName] = useState(profile.firstName ?? '');
  const [lastName, setLastName] = useState(profile.lastName ?? '');
  const [email, setEmail] = useState(profile.email ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const save = useMutation({
    mutationFn: () => profileApi.updatePersonal({ firstName: firstName.trim(), lastName: lastName.trim(), email: email.trim() }),
    onSuccess: () => {
      toast.success('Personal information saved');
      qc.invalidateQueries({ queryKey: qk.profile });
    },
    onError: toast.apiError,
  });

  const submit = () => {
    const e: Record<string, string> = {};
    if (!firstName.trim()) e.firstName = 'First name is required';
    if (!lastName.trim()) e.lastName = 'Last name is required';
    if (!email.trim()) e.email = 'Email is required';
    else if (!isValidEmail(email.trim())) e.email = 'Enter a valid email address';
    setErrors(e);
    if (!Object.keys(e).length) save.mutate();
  };

  const dirty =
    firstName !== (profile.firstName ?? '') || lastName !== (profile.lastName ?? '') || email !== (profile.email ?? '');

  return (
    <Card>
      <CardHeader title="Personal information" icon={<UserRound className="h-4 w-4" />} />
      <form
        className="grid gap-4 px-5 py-4 sm:grid-cols-2"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <Input label="First name" required value={firstName} error={errors.firstName} onChange={(e) => setFirstName(e.target.value)} autoComplete="given-name" />
        <Input label="Last name" required value={lastName} error={errors.lastName} onChange={(e) => setLastName(e.target.value)} autoComplete="family-name" />
        <Input
          containerClassName="sm:col-span-2"
          label="Email"
          type="email"
          required
          value={email}
          error={errors.email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
        />
        <div className="flex justify-end sm:col-span-2">
          <Button type="submit" loading={save.isPending} disabled={!dirty}>
            Save personal info
          </Button>
        </div>
      </form>
    </Card>
  );
}

/* ------------------------------------------------------------ preferences */
const WORK_STYLES: WorkStyle[] = ['REMOTE', 'HYBRID', 'ONSITE', 'FLEXIBLE'];
const EMPLOYMENT_TYPES: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN', 'FREELANCE', 'TEMPORARY'];
const PERIODS: SalaryPeriod[] = ['YEARLY', 'MONTHLY', 'HOURLY'];

function PreferencesCard({ prefs }: { prefs: Preferences | null }) {
  const qc = useQueryClient();
  const [roles, setRoles] = useState<string[]>(prefs?.preferredRoles ?? []);
  const [locations, setLocations] = useState<string[]>(prefs?.preferredLocations ?? []);
  const [companies, setCompanies] = useState<string[]>(prefs?.preferredCompanies ?? []);
  const [workStyle, setWorkStyle] = useState<string>(prefs?.workStyle ?? '');
  const [employmentType, setEmploymentType] = useState<string>(prefs?.employmentType ?? '');
  const [salaryMin, setSalaryMin] = useState(prefs?.salaryMin != null ? String(prefs.salaryMin) : '');
  const [salaryMax, setSalaryMax] = useState(prefs?.salaryMax != null ? String(prefs.salaryMax) : '');
  const [currency, setCurrency] = useState(prefs?.currencyCode ?? '');
  const [period, setPeriod] = useState<string>(prefs?.salaryPeriod ?? '');
  const [alerts, setAlerts] = useState(prefs?.jobAlertSettings ?? false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const save = useMutation({
    mutationFn: (body: Preferences) => profileApi.updatePreferences(body),
    onSuccess: () => {
      toast.success('Preferences saved');
      qc.invalidateQueries({ queryKey: qk.profile });
    },
    onError: toast.apiError,
  });

  const submit = () => {
    const e: Record<string, string> = {};
    const min = salaryMin.trim() ? Number(salaryMin) : null;
    const max = salaryMax.trim() ? Number(salaryMax) : null;
    if (min !== null && (!Number.isFinite(min) || min < 0)) e.salaryMin = 'Enter a positive number';
    if (max !== null && (!Number.isFinite(max) || max < 0)) e.salaryMax = 'Enter a positive number';
    if (min !== null && max !== null && max < min) e.salaryMax = 'Maximum must be at least the minimum';
    if (currency.trim() && !/^[A-Za-z]{3}$/.test(currency.trim())) e.currency = 'Use a 3-letter ISO code, e.g. USD';
    setErrors(e);
    if (Object.keys(e).length) return;
    save.mutate({
      preferredRoles: roles,
      preferredLocations: locations,
      preferredCompanies: companies,
      workStyle: (workStyle || null) as WorkStyle | null,
      employmentType: (employmentType || null) as EmploymentType | null,
      salaryMin: min !== null ? Math.round(min) : null,
      salaryMax: max !== null ? Math.round(max) : null,
      currencyCode: currency.trim() ? currency.trim().toUpperCase() : null,
      salaryPeriod: (period || null) as SalaryPeriod | null,
      jobAlertSettings: alerts,
    });
  };

  return (
    <Card>
      <CardHeader
        title="Job preferences"
        description="Used for matching and prioritising opportunities."
        icon={<SlidersHorizontal className="h-4 w-4" />}
      />
      <form
        className="grid gap-4 px-5 py-4 sm:grid-cols-2"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <div className="sm:col-span-2">
          <TagInput label="Preferred roles" values={roles} onChange={setRoles} placeholder="e.g. Backend Engineer" />
        </div>
        <TagInput label="Preferred locations" values={locations} onChange={setLocations} placeholder="e.g. Berlin" />
        <TagInput label="Preferred companies" values={companies} onChange={setCompanies} placeholder="Company name" />
        <Select
          label="Work style"
          value={workStyle}
          onChange={(e) => setWorkStyle(e.target.value)}
          placeholder="No preference"
          options={WORK_STYLES.map((w) => ({ value: w, label: humanize(w) }))}
        />
        <Select
          label="Employment type"
          value={employmentType}
          onChange={(e) => setEmploymentType(e.target.value)}
          placeholder="No preference"
          options={EMPLOYMENT_TYPES.map((w) => ({ value: w, label: humanize(w) }))}
        />
        <Input label="Minimum salary" inputMode="numeric" value={salaryMin} error={errors.salaryMin} onChange={(e) => setSalaryMin(e.target.value)} />
        <Input label="Maximum salary" inputMode="numeric" value={salaryMax} error={errors.salaryMax} onChange={(e) => setSalaryMax(e.target.value)} />
        <Input label="Currency" placeholder="USD" maxLength={3} value={currency} error={errors.currency} onChange={(e) => setCurrency(e.target.value)} />
        <Select
          label="Salary period"
          value={period}
          onChange={(e) => setPeriod(e.target.value)}
          placeholder="Not specified"
          options={PERIODS.map((w) => ({ value: w, label: humanize(w) }))}
        />
        <div className="sm:col-span-2">
          <Checkbox label="Job alerts" description="Receive alerts about new matching jobs." checked={alerts} onChange={setAlerts} />
        </div>
        <div className="flex justify-end sm:col-span-2">
          <Button type="submit" loading={save.isPending}>
            Save preferences
          </Button>
        </div>
      </form>
    </Card>
  );
}

/* ----------------------------------------------------------- social links */
const SOCIAL_FIELDS: Array<{ key: 'linkedin' | 'github' | 'portfolio' | 'twitter'; label: string; placeholder: string }> = [
  { key: 'linkedin', label: 'LinkedIn', placeholder: 'https://www.linkedin.com/in/…' },
  { key: 'github', label: 'GitHub', placeholder: 'https://github.com/…' },
  { key: 'portfolio', label: 'Portfolio', placeholder: 'https://…' },
  { key: 'twitter', label: 'X / Twitter', placeholder: 'https://x.com/…' },
];

function SocialLinksCard({ links }: { links: SocialLinks | null }) {
  const qc = useQueryClient();
  const [values, setValues] = useState<Record<string, string>>({
    linkedin: links?.linkedin ?? '',
    github: links?.github ?? '',
    portfolio: links?.portfolio ?? '',
    twitter: links?.twitter ?? '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  const save = useMutation({
    mutationFn: (b: SocialLinks) => profileApi.updateSocial(b),
    onSuccess: () => {
      toast.success('Social links saved');
      qc.invalidateQueries({ queryKey: qk.profile });
    },
    onError: toast.apiError,
  });

  const submit = () => {
    const e: Record<string, string> = {};
    for (const f of SOCIAL_FIELDS) {
      const v = values[f.key].trim();
      if (v && !isValidUrl(v)) e[f.key] = 'Enter a full URL starting with https://';
    }
    setErrors(e);
    if (Object.keys(e).length) return;
    save.mutate({
      id: links?.id ?? null,
      linkedin: blankToNull(values.linkedin),
      github: blankToNull(values.github),
      portfolio: blankToNull(values.portfolio),
      twitter: blankToNull(values.twitter),
    });
  };

  return (
    <Card>
      <CardHeader title="Social links" icon={<Link2 className="h-4 w-4" />} />
      <form
        className="grid gap-4 px-5 py-4 sm:grid-cols-2"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        {SOCIAL_FIELDS.map((f) => (
          <Input
            key={f.key}
            label={f.label}
            type="url"
            placeholder={f.placeholder}
            value={values[f.key]}
            error={errors[f.key]}
            onChange={(e) => setValues((s) => ({ ...s, [f.key]: e.target.value }))}
          />
        ))}
        <div className="flex justify-end sm:col-span-2">
          <Button type="submit" loading={save.isPending}>
            Save links
          </Button>
        </div>
      </form>
    </Card>
  );
}

/* ------------------------------------------------------------------ page */
const dateRange = (start: string | null, end: string | null, current?: boolean) => {
  if (!start && !end && !current) return null;
  const s = start ? formatDate(start) : 'Start date not set';
  const e = current ? 'Present' : end ? formatDate(end) : 'End date not set';
  return `${s} – ${e}`;
};

const Muted = ({ children }: { children: ReactNode }) => <p className="text-xs text-fg-subtle">{children}</p>;

export function ProfilePage() {
  const q = useProfile();

  if (q.isLoading) {
    return (
      <>
        <PageHeader title="Profile" description="Your personal details, job preferences and career history." />
        <div className="space-y-4">
          <Skeleton className="h-48 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      </>
    );
  }
  if (q.isError || !q.data) {
    return (
      <>
        <PageHeader title="Profile" />
        <ErrorState error={q.error} onRetry={() => q.refetch()} />
      </>
    );
  }

  const p = q.data;
  return (
    <>
      <PageHeader title="Profile" description="Your personal details, job preferences and career history. Everything here is used to ground AI output." />
      <div className="grid gap-6 xl:grid-cols-2" key={p.userId}>
        <div className="space-y-6">
          <PersonalInfoCard profile={p} />
          <SocialLinksCard links={p.socialLinks} />
        </div>
        <PreferencesCard prefs={p.preferences} />
      </div>

      {p.skills && p.skills.length > 0 && (
        <Card className="mt-6">
          <CardHeader title="Skills on your profile" />
          <div className="flex flex-wrap gap-1.5 px-5 py-4">
            {p.skills.map((s) => (
              <span key={s} className="rounded-md border border-border bg-surface-2 px-2 py-0.5 text-xs text-fg-muted">
                {s}
              </span>
            ))}
          </div>
        </Card>
      )}

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <SectionList<Experience>
          section="experience"
          title="Experience"
          description="Roles you have held."
          icon={<Briefcase className="h-4 w-4" />}
          items={p.experience ?? []}
          emptyText="Add your work history so matching can assess your experience."
          dateRange={['startDate', 'endDate']}
          itemLabel={(x) => `${x.title} at ${x.companyName}`}
          fields={[
            { key: 'title', label: 'Job title', type: 'text', required: true },
            { key: 'companyName', label: 'Company', type: 'text', required: true },
            { key: 'location', label: 'Location', type: 'text' },
            { key: 'currentJob', label: 'I currently work here', type: 'checkbox' },
            { key: 'startDate', label: 'Start date', type: 'date', required: true },
            { key: 'endDate', label: 'End date', type: 'date', hiddenWhen: 'currentJob' },
            { key: 'description', label: 'Description', type: 'textarea' },
          ]}
          renderItem={(x) => (
            <>
              <p className="text-sm font-medium text-fg">{x.title}</p>
              <p className="text-sm text-fg-muted">
                {x.companyName}
                {x.location ? ` · ${x.location}` : ''}
              </p>
              {dateRange(x.startDate, x.endDate, x.currentJob) && <Muted>{dateRange(x.startDate, x.endDate, x.currentJob)}</Muted>}
              {x.description && <p className="mt-1 whitespace-pre-line text-sm text-fg-muted">{x.description}</p>}
            </>
          )}
        />
        <SectionList<Education>
          section="education"
          title="Education"
          description="Degrees and courses."
          icon={<GraduationCap className="h-4 w-4" />}
          items={p.education ?? []}
          emptyText="Add your education history."
          dateRange={['startDate', 'endDate']}
          itemLabel={(x) => x.institution}
          fields={[
            { key: 'institution', label: 'Institution', type: 'text', required: true, full: true },
            { key: 'degree', label: 'Degree', type: 'text' },
            { key: 'fieldOfStudy', label: 'Field of study', type: 'text' },
            { key: 'startDate', label: 'Start date', type: 'date' },
            { key: 'endDate', label: 'End date', type: 'date' },
            { key: 'description', label: 'Description', type: 'textarea' },
          ]}
          renderItem={(x) => (
            <>
              <p className="text-sm font-medium text-fg">{x.institution}</p>
              {(x.degree || x.fieldOfStudy) && (
                <p className="text-sm text-fg-muted">{[x.degree, x.fieldOfStudy].filter(Boolean).join(', ')}</p>
              )}
              {dateRange(x.startDate, x.endDate) && <Muted>{dateRange(x.startDate, x.endDate)}</Muted>}
              {x.description && <p className="mt-1 whitespace-pre-line text-sm text-fg-muted">{x.description}</p>}
            </>
          )}
        />
        <SectionList<Project>
          section="projects"
          title="Projects"
          description="Things you have built."
          icon={<FolderGit2 className="h-4 w-4" />}
          items={p.projects ?? []}
          emptyText="Projects help demonstrate skills that are not in your job history."
          itemLabel={(x) => x.name}
          fields={[
            { key: 'name', label: 'Project name', type: 'text', required: true },
            { key: 'role', label: 'Your role', type: 'text' },
            { key: 'url', label: 'URL', type: 'url', full: true, placeholder: 'https://…' },
            { key: 'description', label: 'Description', type: 'textarea' },
          ]}
          renderItem={(x) => (
            <>
              <p className="text-sm font-medium text-fg">{x.name}</p>
              {x.role && <p className="text-sm text-fg-muted">{x.role}</p>}
              {x.url && (
                <a href={x.url} target="_blank" rel="noopener noreferrer" className="break-all text-xs text-primary hover:underline">
                  {x.url}
                </a>
              )}
              {x.description && <p className="mt-1 whitespace-pre-line text-sm text-fg-muted">{x.description}</p>}
            </>
          )}
        />
        <SectionList<Certification>
          section="certifications"
          title="Certifications"
          description="Certificates and credentials."
          icon={<Award className="h-4 w-4" />}
          items={p.certifications ?? []}
          emptyText="Add certifications you hold."
          dateRange={['issueDate', 'expirationDate']}
          itemLabel={(x) => x.name}
          fields={[
            { key: 'name', label: 'Name', type: 'text', required: true },
            { key: 'issuingOrganization', label: 'Issuing organisation', type: 'text' },
            { key: 'issueDate', label: 'Issue date', type: 'date' },
            { key: 'expirationDate', label: 'Expiration date', type: 'date' },
            { key: 'credentialId', label: 'Credential ID', type: 'text' },
            { key: 'credentialUrl', label: 'Credential URL', type: 'url', placeholder: 'https://…' },
          ]}
          renderItem={(x) => (
            <>
              <p className="text-sm font-medium text-fg">{x.name}</p>
              {x.issuingOrganization && <p className="text-sm text-fg-muted">{x.issuingOrganization}</p>}
              {(x.issueDate || x.expirationDate) && (
                <Muted>
                  {x.issueDate ? `Issued ${formatDate(x.issueDate)}` : 'Issue date not set'}
                  {x.expirationDate ? ` · Expires ${formatDate(x.expirationDate)}` : ''}
                </Muted>
              )}
              {x.credentialUrl && (
                <a href={x.credentialUrl} target="_blank" rel="noopener noreferrer" className="break-all text-xs text-primary hover:underline">
                  View credential
                </a>
              )}
            </>
          )}
        />
      </div>
    </>
  );
}
