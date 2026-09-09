import { useState } from 'react'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { useAsync } from '@/hooks/useAsync'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { Pagination } from '@/components/design/ui/Pagination'
import { LinkButton } from '@/components/design/ui/Button'
import { ExchangeCard } from '@/components/design/domain/ExchangeCard'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'
import { useT } from '@/i18n/I18nContext'

export function HistoryPage() {
  const t = useT()
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const meId = user?.id ?? ''
  const records = useAsync(async () => {
    const result = await api.getExchanges('COMPLETED', page)
    const items = await Promise.all(result.items.map(async (exchange) => ({ exchange, feedback: await api.getExchangeFeedback(exchange.id) })))
    return { ...result, records: items }
  }, [page, meId])
  const head = <PageHeader title={t('history.title')} lead={t('history.lead')} />
  if (records.status === 'error') return <>{head}<ErrorState error={records.error} onRetry={records.reload} /></>
  if (records.status === 'loading') return <>{head}<LoadingState label={t('history.loadingHistory')}><SkeletonRows /></LoadingState></>
  if (!records.data.total) return <>{head}<EmptyState title={t('history.nothingCompletedTitle')} actions={<LinkButton to="/invitations">{t('history.seeInvitations')}</LinkButton>}>{t('history.nothingCompletedBody')}</EmptyState></>
  return <>{head}
    <section className="tally" aria-label="History totals">
      <div><div className="tally-n">{records.data.total}</div><div className="tally-l">{t('history.exchangesCompleted')}</div></div>
      <div><div className="tally-n">{records.data.total * 2}</div><div className="tally-l">{t('history.skillsTraded')}</div></div>
    </section>
    {records.data.records.map(({ exchange, feedback }) => <ExchangeCard key={exchange.id} exchange={exchange} meId={meId} tense="past" showStrength={false}
      actions={<LinkButton to={'/feedback/' + exchange.id} variant={feedback.mine ? 'secondary' : 'primary'} size="sm">{feedback.mine ? t('history.viewYourFeedback') : t('history.leaveFeedback')}</LinkButton>}
      footnote={feedback.mine ? t('history.ratingLine', { rating: feedback.mine.rating, status: feedback.counterpartSubmitted ? t('history.published') : t('history.awaitingRating') }) : t('history.notRated')} />)}
    <Pagination {...records.data} onChange={setPage} />
  </>
}
