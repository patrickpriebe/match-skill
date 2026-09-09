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
import { useT } from '@/i18n/I18nContext'
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
  const t = useT()
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
        <ErrorState error={matches.error} onRetry={matches.reload} context={t('home.couldNotLoad')} />
      </>
    )
  }

  if (matches.status === 'loading' || mine.status !== 'ready') {
    return (
      <>
        <Head />
        <LoadingState label={t('home.loadingMatches')}>
          <Section title={t('home.completeTrades')} emphasis note={t('home.completeTradesNote')}>
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
          title={t('home.noMatchesTitle')}
          actions={
            <>
              <LinkButton to="/skills" variant="primary" size="sm">{t('home.addMoreWanted')}</LinkButton>
              <LinkButton to="/availability" variant="quiet" size="sm">{t('home.widenAvailability')}</LinkButton>
            </>
          }
        >
          {t('home.noMatchesBody')}
        </EmptyState>
      </>
    )
  }

  return (
    <>
      <Head total={matches.data.total} />
      {requesting && <RequestExchangeDialog match={requesting} skills={requesting.user.offeredSkills.filter((s) => mine.data.wanted.some((w) => w.id === s.id))} onClose={() => setRequesting(null)} />}

      <Section
        title={t('home.completeTrades')}
        count={mutual.length}
        emphasis
        note={t('home.completeTradesNote')}
      >
        {mutual.length === 0 ? (
          <EmptyState title={t('home.completeTradesEmptyTitle')}>
            {t('home.completeTradesEmptyBody')}
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
        title={t('home.partialSuggestions')}
        count={partial.length}
        note={t('home.partialSuggestionsNote')}
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
          <span className="meta">{t('home.partialSuggestionsOnPage', { count: partial.length })}</span>
          <Button variant="quiet" size="sm" onClick={() => navigate('/search')}>
            {t('home.searchSpecificSkill')}
          </Button>
        </div>
      </Section>
      <Pagination {...matches.data} onChange={setPage} />
    </>
  )
}

function Head({ total, mutual }: { total?: number; mutual?: number }) {
  const t = useT()
  const lead = total === undefined
    ? t('home.leadUnknown')
    : total === 0
      ? t('home.leadZero')
      : (total === 1 ? t('home.leadOne') : t('home.leadOther', { total })) +
        (mutual ? t('home.leadMutualSuffix', { mutual }) : '')
  return (
    <PageHeader
      title={t('home.title')}
      lead={lead}
    />
  )
}
