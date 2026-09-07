import { RequestExchangeDialog } from '@/components/design/domain/RequestExchangeDialog'
import { Pagination } from '@/components/design/ui/Pagination'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import { useAsync } from '../hooks/useAsync'
import { Section } from '@/components/design/layout/Section'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { MatchCard } from '@/components/design/domain/MatchCard'
import { deriveDirections } from '@/components/design/domain/match-helpers'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonMatchCard } from '@/components/design/feedback/LoadingState'
import { LinkButton, Button } from '@/components/design/ui/Button'
import type { Match } from '@/lib/api/types'

/**
 * The product's centre. GET /matches already returns MUTUAL first, then
 * PARTIAL, ordered by reputation and availability overlap — but ordering
 * alone is invisible, because a user sees a list, not a sort key.
 *
 * Five layers carry the distinction instead: separate sections, plain-language
 * headers, card density, grid size, and action weight. Only the complete
 * trade gets the solid button.
 */
export function HomePage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [requesting, setRequesting] = useState<Match | null>(null)
  const matches = useAsync(() => api.getMatches(page), [page])
  const mine = useAsync(() => api.getMySkills(), [])

  const request = (match: Match) => setRequesting(match)

  if (mine.status === 'error') return <><Head /><ErrorState error={mine.error} onRetry={mine.reload} /></>

  if (matches.status === 'error') {
    return (
      <>
        <Head />
        <ErrorState error={matches.error} onRetry={matches.reload} context="We couldn't load your matches." />
      </>
    )
  }

  if (matches.status === 'loading' || mine.status !== 'ready') {
    return (
      <>
        <Head />
        <LoadingState label="Loading matches">
          <Section title="Complete trades" emphasis note="You both already said you want what the other teaches.">
            <div className="grid-mutual">
              <SkeletonMatchCard />
              <SkeletonMatchCard />
            </div>
          </Section>
        </LoadingState>
      </>
    )
  }

  const all = matches.data.items
  const mutual = all.filter((m) => m.strength === 'MUTUAL')
  const partial = all.filter((m) => m.strength === 'PARTIAL')

  if (all.length === 0) {
    return (
      <>
        <Head total={0} />
        <EmptyState
          title="Nobody yet teaches what you want to learn"
          actions={
            <>
              <LinkButton to="/skills" variant="primary" size="sm">Add more wanted skills</LinkButton>
              <LinkButton to="/availability" variant="quiet" size="sm">Widen your availability</LinkButton>
            </>
          }
        >
          Your wanted skills are rare on the platform right now. Two things change this,
          and both are yours to pull.
        </EmptyState>
      </>
    )
  }

  return (
    <>
      <Head total={matches.data.total} />
      {requesting && <RequestExchangeDialog match={requesting} skills={requesting.user.offeredSkills.filter((s) => mine.data.wanted.some((w) => w.id === s.id))} onClose={() => setRequesting(null)} />}

      <Section
        title="Complete trades"
        count={mutual.length}
        emphasis
        note="You both already said you want what the other teaches. Nothing is left to negotiate except the time."
      >
        {mutual.length === 0 ? (
          <EmptyState title="No complete trades yet">
            A complete trade is when you both already want what the other teaches. Until one
            appears, the partial suggestions below are the way in — the other person picks what
            they&rsquo;d learn from you when they accept.
          </EmptyState>
        ) : (
          <div className="grid-mutual">
            {mutual.map((m) => (
              <MatchCard
                key={m.user.id}
                match={m}
                directions={deriveDirections(m, mine.data.offered, mine.data.wanted)}
                onRequest={request}
              />
            ))}
          </div>
        )}
      </Section>

      <Section
        title="Partial suggestions"
        count={partial.length}
        note="They teach something you want, but nothing of yours is on their list yet. Send a request anyway — they pick what they'd like to learn from you when they accept."
      >
        <div className="grid-partial">
          {partial.map((m) => (
            <MatchCard
              key={m.user.id}
              match={m}
              directions={deriveDirections(m, mine.data.offered, mine.data.wanted)}
              onRequest={request}
            />
          ))}
        </div>

        <div className="row-between" style={{ marginTop: 'var(--gap-md)' }}>
          <span className="meta">Partial suggestions on this page: {partial.length}</span>
          <Button variant="quiet" size="sm" onClick={() => navigate('/search')}>
            Search for a specific skill
          </Button>
        </div>
      </Section>
      <Pagination {...matches.data} onChange={setPage} />
    </>
  )
}

function Head({ total, mutual }: { total?: number; mutual?: number }) {
  return (
    <PageHeader
      title="Matches"
      lead={
        total === undefined
          ? 'People who offer something on your wanted list.'
          : total === 0
            ? 'Nobody currently offers anything on your wanted list.'
            : `${total} ${total === 1 ? 'person offers' : 'people offer'} something on your wanted list.` +
              (mutual ? ` ${mutual} of them also want something you teach.` : '')
      }
    />
  )
}
