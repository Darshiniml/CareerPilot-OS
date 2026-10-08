import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Briefcase, FilterX } from 'lucide-react';
import { resumesApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { ResumeSkill, ResumeStructuredKnowledge } from '../../api/types';
import { Badge, BulletList, Chips, EmptyState, ErrorState, KeyValue, Notice, SkeletonRows } from '../../components/ui';
import { formatMonths, humanize, isNum } from '../../lib/format';

function groupSkills(skills: ResumeSkill[]) {
  const map = new Map<string, string[]>();
  for (const s of skills) {
    const cat = s.category?.trim() || 'Uncategorised';
    const list = map.get(cat) ?? [];
    list.push(s.skill);
    map.set(cat, list);
  }
  return Array.from(map.entries()).sort((a, b) => b[1].length - a[1].length);
}

function SubHeading({ children }: { children: ReactNode }) {
  return <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">{children}</h3>;
}

function GroundingInfo({ k }: { k: ResumeStructuredKnowledge }) {
  const dropped = k.grounding?.dropped;
  if (!dropped) return null;
  const entries = Object.entries(dropped).filter(([, n]) => isNum(n) && n > 0);
  const total = entries.reduce((s, [, n]) => s + n, 0);
  if (total === 0) {
    return (
      <Notice tone="success" title="Every extracted item was found in your resume text">
        Nothing was discarded during grounding.
      </Notice>
    );
  }
  return (
    <Notice tone="info" icon={<FilterX className="h-4 w-4" />} title={`${total} unsupported item${total === 1 ? ' was' : 's were'} discarded`}>
      <p>
        The AI extracted these but they could not be found word for word in your resume, so they were removed:{' '}
        {entries.map(([k2, n]) => `${n} ${humanize(k2).toLowerCase()}`).join(', ')}.
      </p>
    </Notice>
  );
}

export function KnowledgePanel({ resumeId, ready }: { resumeId: string; ready: boolean }) {
  const q = useQuery({ queryKey: qk.resumeKnowledge(resumeId), queryFn: () => resumesApi.knowledge(resumeId), enabled: ready });

  if (!ready) {
    return <EmptyState title="Not processed yet" description="Parsed knowledge appears once AI processing has finished successfully." />;
  }
  if (q.isLoading) return <SkeletonRows rows={4} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const k = q.data?.structuredKnowledge;
  if (!k) {
    return (
      <EmptyState
        title="No parsed knowledge available"
        description={q.data?.processingError ?? 'The AI did not return structured data for this resume.'}
      />
    );
  }

  const skills = k.skills ?? [];
  const exp = k.experience ?? [];
  const edu = k.education ?? [];
  const projects = k.projects ?? [];
  const certs = k.certifications ?? [];
  const ei = k.intelligence?.experienceIntelligence;
  const pi = k.personalInformation;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-2 text-xs text-fg-muted">
        <Badge tone="primary">AI-extracted</Badge>
        {isNum(q.data?.versionNumber) && <span>From version {q.data?.versionNumber}</span>}
      </div>

      <GroundingInfo k={k} />

      {pi && (
        <section>
          <SubHeading>Contact details found</SubHeading>
          <KeyValue
            items={[
              { label: 'Name', value: pi.name ?? <span className="italic text-fg-subtle">Not found</span> },
              { label: 'Email', value: pi.email ?? <span className="italic text-fg-subtle">Not found</span> },
              { label: 'Phone', value: pi.phone ?? <span className="italic text-fg-subtle">Not found</span> },
              { label: 'Location', value: pi.location ?? <span className="italic text-fg-subtle">Not found</span> },
            ]}
          />
        </section>
      )}

      {k.summary && (
        <section>
          <SubHeading>Summary</SubHeading>
          <p className="whitespace-pre-line text-sm text-fg">{k.summary}</p>
        </section>
      )}

      {ei && (
        <section>
          <SubHeading>Experience overview</SubHeading>
          <KeyValue
            items={[
              {
                label: 'Total experience',
                value: isNum(ei.totalYearsExperience) ? (
                  `${ei.totalYearsExperience} years`
                ) : (
                  <span className="italic text-fg-subtle">Not available (dates missing)</span>
                ),
              },
              { label: 'Current role', value: ei.currentRole ?? <span className="italic text-fg-subtle">Not available</span> },
              ...(isNum(ei.datedRoles) ? [{ label: 'Roles with dates', value: `${ei.datedRoles}` }] : []),
              ...(isNum(ei.undatedRoles) ? [{ label: 'Roles without dates', value: `${ei.undatedRoles}` }] : []),
            ]}
          />
          {ei.employmentGaps && ei.employmentGaps.length > 0 && (
            <div className="mt-3">
              <p className="mb-1 text-xs text-fg-subtle">Employment gaps</p>
              <BulletList items={ei.employmentGaps.map((g) => `${g.from} → ${g.to} (${formatMonths(g.months)})`)} />
            </div>
          )}
        </section>
      )}

      <section>
        <SubHeading>Skills ({skills.length})</SubHeading>
        {skills.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No skills were extracted.</p>
        ) : (
          <div className="space-y-3">
            {groupSkills(skills).map(([cat, list]) => (
              <div key={cat}>
                <p className="mb-1 text-xs font-medium text-fg-muted">{cat}</p>
                <Chips items={list} tone="primary" />
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <SubHeading>Experience ({exp.length})</SubHeading>
        {exp.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No experience entries were extracted.</p>
        ) : (
          <ol className="relative space-y-4 border-l border-border pl-5">
            {exp.map((e, i) => (
              <li key={i} className="relative">
                <span className="absolute -left-[27px] top-1 flex h-3.5 w-3.5 items-center justify-center rounded-full border border-border bg-surface">
                  <Briefcase className="h-2 w-2 text-fg-subtle" aria-hidden />
                </span>
                <p className="text-sm font-medium text-fg">
                  {e.designation ?? <span className="italic text-fg-subtle">Title not found</span>}
                  <span className="font-normal text-fg-muted"> · {e.company}</span>
                </p>
                <p className="text-xs text-fg-subtle">
                  {e.startDate ?? 'Start not found'} – {e.endDate ?? 'End not found'}
                  {isNum(e.durationMonths) && ` · ${formatMonths(e.durationMonths)}`}
                </p>
                {e.responsibilities && e.responsibilities.length > 0 && (
                  <BulletList className="mt-1.5" items={e.responsibilities} />
                )}
              </li>
            ))}
          </ol>
        )}
      </section>

      <section>
        <SubHeading>Education ({edu.length})</SubHeading>
        {edu.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No education entries were extracted.</p>
        ) : (
          <ul className="space-y-2">
            {edu.map((e, i) => (
              <li key={i} className="text-sm">
                <p className="font-medium text-fg">{e.institution}</p>
                <p className="text-fg-muted">
                  {[e.degree, e.specialization].filter(Boolean).join(', ') || <span className="italic text-fg-subtle">Degree not found</span>}
                  {isNum(e.graduationYear) && ` · ${e.graduationYear}`}
                  {e.gpa && ` · GPA ${e.gpa}`}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <SubHeading>Projects ({projects.length})</SubHeading>
        {projects.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No projects were extracted.</p>
        ) : (
          <ul className="space-y-3">
            {projects.map((p, i) => (
              <li key={i} className="text-sm">
                <p className="font-medium text-fg">{p.name}</p>
                {p.description && <p className="text-fg-muted">{p.description}</p>}
                {p.technologies && p.technologies.length > 0 && (
                  <div className="mt-1">
                    <Chips items={p.technologies} />
                  </div>
                )}
                <div className="mt-1 flex flex-wrap gap-3 text-xs">
                  {p.gitHubUrl && (
                    <a className="text-primary hover:underline" href={p.gitHubUrl} target="_blank" rel="noopener noreferrer">
                      Repository
                    </a>
                  )}
                  {p.liveUrl && (
                    <a className="text-primary hover:underline" href={p.liveUrl} target="_blank" rel="noopener noreferrer">
                      Live site
                    </a>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <SubHeading>Certifications ({certs.length})</SubHeading>
        {certs.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No certifications were extracted.</p>
        ) : (
          <ul className="space-y-2">
            {certs.map((c, i) => (
              <li key={i} className="text-sm">
                <p className="font-medium text-fg">{c.certificationName}</p>
                {(c.issuingOrganization || c.issueDate) && (
                  <p className="text-fg-muted">{[c.issuingOrganization, c.issueDate].filter(Boolean).join(' · ')}</p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
