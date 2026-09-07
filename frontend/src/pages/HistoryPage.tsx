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

export function HistoryPage() {
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const meId = user?.id ?? ''
  const records = useAsync(async () => {
    const result = await api.getExchanges('COMPLETED', page)
    const items = await Promise.all(result.items.map(async (exchange) => ({ exchange, feedback: await api.getExchangeFeedback(exchange.id) })))
    return { ...result, records: items }
  }, [page, meId])
  const head = <PageHeader title="History" lead="Your record of completed exchanges. Each row shows what you learned and what you taught." />
  if (records.status === 'error') return <>{head}<ErrorState error={records.error} onRetry={records.reload} /></>
  if (records.status === 'loading') return <>{head}<LoadingState label="Loading history"><SkeletonRows /></LoadingState></>
  if (!records.data.total) return <>{head}<EmptyState title="Nothing completed yet" actions={<LinkButton to="/invitations">See invitations</LinkButton>}>An exchange appears here after a participant marks the meeting as completed.</EmptyState></>
  return <>{head}
    <section className="tally" aria-label="History totals">
      <div><div className="tally-n">{records.data.total}</div><div className="tally-l">exchanges completed</div></div>
      <div><div className="tally-n">{records.data.total * 2}</div><div className="tally-l">skills traded in both directions</div></div>
    </section>
    {records.data.records.map(({ exchange, feedback }) => <ExchangeCard key={exchange.id} exchange={exchange} meId={meId} tense="past" showStrength={false}
      actions={<LinkButton to={'/feedback/' + exchange.id} variant={feedback.mine ? 'secondary' : 'primary'} size="sm">{feedback.mine ? 'View your feedback' : 'Leave feedback'}</LinkButton>}
      footnote={feedback.mine ? 'Your rating: ' + feedback.mine.rating + ' of 5. ' + (feedback.counterpartSubmitted ? 'Published.' : 'Saved privately; awaiting their rating.') : 'You have not rated this exchange.'} />)}
    <Pagination {...records.data} onChange={setPage} />
  </>
}
