import { RequestExchangeDialog } from '@/components/design/domain/RequestExchangeDialog'
import { Pagination } from '@/components/design/ui/Pagination'
import { useCallback, useState } from 'react'
import { api } from '@/lib/api'
import { useAsync } from '../hooks/useAsync'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { Section } from '@/components/design/layout/Section'
import { SkillAutocomplete } from '@/components/design/domain/SkillAutocomplete'
import { MatchCard } from '@/components/design/domain/MatchCard'
import { deriveDirections } from '@/components/design/domain/match-helpers'
import { MatchStrengthBadge } from '@/components/design/domain/ExchangeStatusBadge'
import { UserCard } from '@/components/design/domain/UserCard'
import { TradeLedger } from '@/components/design/domain/TradeLedger'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'
import { Button } from '@/components/design/ui/Button'
import type { Match, Skill } from '@/lib/api/types'

/**
 * The deliberate path, as opposed to the automatic suggestions on Home. It
 * uses the same controlled vocabulary as registration — free text would find
 * nothing here and would teach the wrong habit.
 *
 * Results keep the two-tier grouping, because the ranking rule applies to
 * search as well as the feed.
 */
export function SearchPage() {
  const [page, setPage] = useState(0)
  const [requesting, setRequesting] = useState<Match | null>(null)
  const [skill, setSkill] = useState<Skill[]>([])
  const active = skill[0] ?? null
  const mine = useAsync(() => api.getMySkills(), [])
  const results = useAsync(
    () => (active ? api.search(active.id, page) : Promise.resolve({ items: [], page: 0, size: 0, total: 0 })),
    [active?.id, page],
  )

  const search = useCallback((q: string) => api.searchSkills(q), [])

  const query = (
    <SkillAutocomplete
      label="Skill"
      multiple={false}
      value={skill}
      onChange={(value) => { setSkill(value); setPage(0) }}
      onSearch={search}
      hint="Every skill comes from the shared vocabulary — free text finds nothing here."
    />
  )

  if (mine.status === 'error') return <><Head /><ErrorState error={mine.error} onRetry={mine.reload} /></>

  if (!active) {
    return (
      <>
        <Head />
        <section className="block">{query}</section>
        <EmptyState title="Search by skill">
          Start from something already on your wanted list, or type to see what the
          vocabulary offers.
          {mine.status === 'ready' && mine.data.wanted.length > 0 && (
            <span className="chips" style={{ marginTop: 16 }}>
              {mine.data.wanted.map((s) => (
                <button key={s.id} type="button" className="chip" onClick={() => setSkill([s])}>
                  {s.name}
                </button>
              ))}
            </span>
          )}
        </EmptyState>
      </>
    )
  }

  if (results.status === 'error') {
    return (
      <>
        <Head />
        <section className="block">{query}</section>
        <ErrorState error={results.error} onRetry={results.reload} />
      </>
    )
  }

  if (results.status === 'loading' || mine.status !== 'ready') {
    return (
      <>
        <Head />
        <section className="block">{query}</section>
        <LoadingState label="Searching"><SkeletonRows count={3} /></LoadingState>
      </>
    )
  }

  const mutual = results.data.items.filter((m) => m.strength === 'MUTUAL')
  const partial = results.data.items.filter((m) => m.strength === 'PARTIAL')

  return (
    <>
      <Head />
      <section className="block">{query}</section>
      {requesting && <RequestExchangeDialog match={requesting} skills={[active]} onClose={() => setRequesting(null)} />}

      {results.data.items.length === 0 ? (
        <EmptyState
          title={'Nobody currently offers ' + active.name}
          actions={<Button size="sm" onClick={() => setSkill([])}>Search a different skill</Button>}
        >
          {active.status === 'PENDING_REVIEW'
            ? 'That term is still waiting for review, so it does not participate in matching yet. Approved skills are the only ones the engine can see.'
            : 'No one on the platform teaches this yet.'}
        </EmptyState>
      ) : (
        <>
          <Section
            title="Complete trades"
            count={mutual.length}
            emphasis
            note={'Teaches ' + active.name + ', and wants something you teach.'}
          >
            <div className="grid-mutual">
              {mutual.map((m) => (
                <MatchCard
                  key={m.user.id}
                  match={m}
                  directions={{ ...deriveDirections(m, mine.data.offered, mine.data.wanted), youLearn: active }}
                  onRequest={setRequesting}
                />
              ))}
            </div>
          </Section>

          <Section
            title="Partial suggestions"
            count={partial.length}
            note={'They teach ' + active.name + '. What they would learn from you is decided at acceptance.'}
          >
            {partial.map((m) => <PartialRow key={m.user.id} match={m} skill={active} onRequest={() => setRequesting(m)} />)}
            <p className="meta" style={{ marginTop: 'var(--gap-md)' }}>
              Ordered by reputation, then by how much your weekly availability overlaps.
            </p>
          </Section>
          <Pagination {...results.data} onChange={setPage} />
        </>
      )}
    </>
  )
}

function PartialRow({ match, skill, onRequest }: { match: Match; skill: Skill; onRequest: () => void }) {
  const others = match.user.offeredSkills.filter((s) => s.id !== skill.id)
  return (
    <div className="rec-row">
      <UserCard
        name={match.user.displayName}
        size="sm"
        nameSize={14}
        reputation={match.user.reputation}
      />
      <TradeLedger
        variant="half"
        style={{ borderTop: 0 }}
        sides={[
          {
            direction: 'Teaches',
            skill: skill.name,
            from: others.length ? '· also ' + others.map((s) => s.name).join(', ') : undefined,
          },
          { direction: 'Wants', open: 'nothing of yours yet' },
        ]}
      />
      <div className="rec-act">
        <div className="row" style={{ gap: 6 }}>
          <MatchStrengthBadge strength="PARTIAL" />
          <Button size="sm" onClick={onRequest}>Send request</Button>
        </div>
      </div>
    </div>
  )
}

function Head() {
  return (
    <PageHeader
      title="Search"
      lead="Look up one skill and see who teaches it. Results keep the same two-tier grouping as your matches."
    />
  )
}
