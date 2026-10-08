import type { ReactNode } from 'react';
import type { ExtractedSkill, ExtractedValue, InsightEvidence, JobAnalysisView } from '../../api/types';
import { AiLabel, Badge } from '../../components/ui/Badge';
import { BulletList, Chips, KeyValue } from '../../components/ui/Data';
import { Notice, Unavailable } from '../../components/ui/Feedback';
import { formatDateTime, humanize, isNum } from '../../lib/format';

function ev<T>(v: ExtractedValue<T> | undefined): T | null {
  return v && v.value !== null && v.value !== undefined ? v.value : null;
}

function text(v: ExtractedValue<string> | undefined) {
  const x = ev(v);
  return x ? x : <Unavailable />;
}

function pct(v: number | null | undefined) {
  return isNum(v) ? `${Math.round(v * 100)}%` : <Unavailable />;
}

function SkillList({ skills, empty }: { skills: ExtractedSkill[] | undefined; empty: string }) {
  if (!skills || skills.length === 0) return <p className="text-sm italic text-fg-subtle">{empty}</p>;
  return (
    <ul className="flex flex-wrap gap-1.5">
      {skills.map((s, i) => (
        <li key={`${s.name}-${i}`}>
          <Badge
            tone={s.importance === 'REQUIRED' ? 'primary' : 'neutral'}
            title={[s.category, isNum(s.confidence) ? `confidence ${Math.round(s.confidence * 100)}%` : null].filter(Boolean).join(' · ')}
          >
            {s.name}
            {s.category ? ` · ${s.category}` : ''}
          </Badge>
        </li>
      ))}
    </ul>
  );
}

function SubHeading({ children }: { children: ReactNode }) {
  return <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">{children}</h4>;
}

function InsightBlock({ title, items, evidence }: { title: string; items: string[] | undefined; evidence: InsightEvidence[] | undefined }) {
  const list = items ?? [];
  const evid = evidence ?? [];
  if (list.length === 0 && evid.length === 0) return null;
  return (
    <div>
      <SubHeading>{title}</SubHeading>
      {evid.length > 0 ? (
        <ul className="space-y-2">
          {evid.map((e, i) => (
            <li key={i} className="rounded-lg border border-border bg-surface-2 p-2.5 text-sm">
              <p className="text-fg">{e.insight}</p>
              <p className="mt-1 text-xs text-fg-muted">
                <span className="font-medium">Evidence:</span> “{e.evidence}”
              </p>
            </li>
          ))}
        </ul>
      ) : (
        <BulletList items={list} />
      )}
    </div>
  );
}

/** Renders a parsed job analysis (from analyze / process-text / process-url / GET job). */
export function AnalysisView({ analysis }: { analysis: JobAnalysisView }) {
  const k = analysis.knowledge;
  const md = analysis.metadata ?? {};
  const qm = analysis.qualityMetrics ?? {};
  const ins = analysis.insights ?? {};
  const salary = k?.salary;
  const categories = Object.entries(md.technologyCategories ?? {}).filter(([, v]) => v && v.length > 0);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        <AiLabel>AI analysis</AiLabel>
        <Badge tone={analysis.status === 'FAILED' ? 'danger' : 'neutral'}>Status: {humanize(analysis.status)}</Badge>
        {analysis.updatedAt && <span className="text-xs text-fg-subtle">Updated {formatDateTime(analysis.updatedAt)}</span>}
      </div>

      {!k ? (
        <Notice tone="neutral" title="No parsed details">
          The analysis did not return structured job details.
        </Notice>
      ) : (
        <>
          <KeyValue
            items={[
              { label: 'Title', value: text(k.jobTitle) },
              { label: 'Company', value: text(k.companyName) },
              { label: 'Seniority', value: ev(k.declaredSeniority) ?? ev(k.inferredSeniority) ?? <Unavailable /> },
              { label: 'Employment type', value: text(k.employmentType) },
              { label: 'Work mode', value: text(k.workMode) },
              { label: 'Locations', value: ev(k.locations)?.length ? ev(k.locations)!.join(', ') : <Unavailable /> },
              { label: 'Experience required', value: text(k.experienceRequired) },
              {
                label: 'Salary',
                value: salary?.salarySpecified
                  ? `${salary.minimum.toLocaleString()} – ${salary.maximum.toLocaleString()}${salary.currency ? ` ${salary.currency}` : ''}${
                      salary.payPeriod ? ` / ${humanize(salary.payPeriod).toLowerCase()}` : ''
                    }`
                  : <Unavailable>Not specified in the posting</Unavailable>,
              },
            ]}
          />
          <div>
            <SubHeading>Required skills</SubHeading>
            <SkillList skills={k.requiredSkills} empty="No required skills extracted" />
          </div>
          <div>
            <SubHeading>Preferred skills</SubHeading>
            <SkillList skills={k.preferredSkills} empty="No preferred skills extracted" />
          </div>
          <div>
            <SubHeading>Responsibilities</SubHeading>
            <BulletList items={ev(k.responsibilities)} empty="None extracted" />
          </div>
          <div>
            <SubHeading>Qualifications</SubHeading>
            <BulletList items={ev(k.qualifications)} empty="None extracted" />
          </div>
          {(ev(k.educationRequirements)?.length ?? 0) > 0 && (
            <div>
              <SubHeading>Education</SubHeading>
              <BulletList items={ev(k.educationRequirements)} />
            </div>
          )}
          {(ev(k.benefits)?.length ?? 0) > 0 && (
            <div>
              <SubHeading>Benefits</SubHeading>
              <BulletList items={ev(k.benefits)} />
            </div>
          )}
        </>
      )}

      {categories.length > 0 && (
        <div>
          <SubHeading>Technologies by category</SubHeading>
          <div className="space-y-2">
            {categories.map(([cat, list]) => (
              <div key={cat} className="grid gap-1 sm:grid-cols-[160px_minmax(0,1fr)]">
                <span className="text-xs text-fg-muted">{cat}</span>
                <Chips items={list} />
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="space-y-4">
        <InsightBlock title="Engineering insights" items={ins.engineering} evidence={ins.evidence?.engineering} />
        <InsightBlock title="Business insights" items={ins.business} evidence={ins.evidence?.business} />
        <InsightBlock title="Hiring insights" items={ins.hiring} evidence={ins.evidence?.hiring} />
      </div>

      {Object.keys(qm).length > 0 && (
        <div>
          <SubHeading>Posting quality</SubHeading>
          <KeyValue
            items={[
              { label: 'Requirement completeness', value: pct(qm.requirementCompleteness) },
              { label: 'Detail quality', value: pct(qm.jobDetailQuality) },
              { label: 'Skill density', value: pct(qm.skillDensity) },
              { label: 'Technology diversity', value: pct(qm.technologyDiversity) },
              { label: 'Remote friendliness', value: pct(qm.remoteFriendliness) },
              { label: 'Seniority complexity', value: pct(qm.seniorityComplexity) },
            ]}
          />
        </div>
      )}
    </div>
  );
}
