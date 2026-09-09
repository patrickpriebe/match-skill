import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { useAsync } from '@/hooks/useAsync'
import { exchangeDirections } from '@/lib/scheduling'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { Button, LinkButton } from '@/components/design/ui/Button'
import { Field, Textarea } from '@/components/design/ui/Form'
import { Card, Notice } from '@/components/design/ui/Surface'
import { IconStar } from '@/components/design/ui/icons'
import { TradeLedger } from '@/components/design/domain/TradeLedger'
import { UserCard } from '@/components/design/domain/UserCard'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { counterpart } from '@/components/design/domain/exchange-helpers'
import { useT } from '@/i18n/I18nContext'

export function FeedbackPage() {
  const t = useT()
  const { id = '' } = useParams()
  const { user } = useAuth()
  const meId = user?.id ?? ''
  const record = useAsync(async () => {
    const exchange = await api.getExchange(id)
    return { exchange, feedback: exchange.status === 'COMPLETED' ? await api.getExchangeFeedback(exchange.id) : null }
  }, [id, meId])
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (record.status === 'error') return <ErrorState error={record.error} onRetry={record.reload} />
  if (record.status === 'loading') return <LoadingState label={t('feedback.loadingFeedback')}><SkeletonRows /></LoadingState>
  const { exchange, feedback } = record.data
  const mine = feedback?.mine
  const other = counterpart(exchange, meId)
  const directions = exchangeDirections(exchange, meId)
  if (exchange.status !== 'COMPLETED') return <><PageHeader title={t('feedback.title')} /><EmptyState title={t('feedback.notCompletedTitle')} actions={<LinkButton to={'/exchanges/' + id}>{t('feedback.openExchange')}</LinkButton>}>{t('feedback.notCompletedBody')}</EmptyState></>
  if (mine) return <><PageHeader title={t('feedback.yourFeedbackFor', { name: other.displayName })} />
    <Card><h2 className="h2">{t('feedback.ratingSaved')}</h2><p>{t('feedback.outOfFive', { rating: mine.rating })}</p>{mine.comment && <p>{mine.comment}</p>}
      <Notice>{feedback?.counterpartSubmitted ? t('feedback.bothPublished') : t('feedback.savedPrivately')}</Notice>
      {feedback?.theirs && <section style={{ marginTop: 20 }}><h2 className="h2">{t('feedback.theirFeedbackForYou')}</h2><p>{t('feedback.outOfFive', { rating: feedback.theirs.rating })}</p>{feedback.theirs.comment && <p>{feedback.theirs.comment}</p>}</section>}
      <p className="small dim">{t('feedback.onePerPerson')}</p>
      <LinkButton to="/history">{t('feedback.returnToHistory')}</LinkButton>
    </Card></>
  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (busy || rating < 1) return
    setBusy(true); setError(null)
    try {
      await api.createFeedback(id, { rating, comment: comment.trim() || undefined })
      record.reload()
    } catch (err) {
      if (err instanceof ApiError && err.code === 'FEEDBACK_ALREADY_SUBMITTED') record.reload()
      else setError(err instanceof Error ? err.message : t('feedback.couldNotSave'))
    } finally { setBusy(false) }
  }
  return <div className="fb-wrap">
    <PageHeader detail title={t('feedback.howDidItGo', { name: other.displayName.split(' ')[0] })} lead={t('feedback.lead')} />
    <Card style={{ marginBottom: 24 }}><UserCard name={other.displayName} meta={other.timeZone} />
      <TradeLedger sides={[
        { direction: t('feedback.youLearned'), skill: directions.youLearn?.name ?? t('scheduling.notRecorded') },
        { direction: t('feedback.theyLearned'), skill: directions.theyLearn?.name ?? t('scheduling.notRecorded') },
      ]} />
    </Card>
    <form className="stack" onSubmit={submit}>
      <fieldset style={{ border: 0, padding: 0 }}><legend className="h3">{t('feedback.yourRating')}</legend>
        <StarInput value={rating} onChange={setRating} />
        <p className="small dim">{rating ? t('feedback.ratingOf', { rating }) : t('feedback.chooseARating')} {t('feedback.useArrowKeys')}</p>
      </fieldset>
      <Field label={t('feedback.commentOptional')}>{(fieldId) => <Textarea id={fieldId} maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />}</Field>
      <Notice>{feedback?.counterpartSubmitted ? t('feedback.theirsSubmittedNote') : t('feedback.stayPrivateNote')}</Notice>
      {error && <Notice tone="stop">{error}</Notice>}
      <div className="row wrap" style={{ gap: 8 }}><Button variant="primary" type="submit" loading={busy} disabled={!rating}>{t('feedback.submitRating')}</Button><LinkButton to="/history">{t('common.notNow')}</LinkButton></div>
      <p className="small dim">{t('feedback.onePerPersonSubmitNote')}</p>
    </form>
  </div>
}
function StarInput({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  const t = useT()
  return <div className="rate" role="radiogroup" aria-label="Rating from 1 to 5">
    {[1, 2, 3, 4, 5].map((n) => <button key={n} type="button" role="radio" aria-checked={value === n}
      tabIndex={value === n || (!value && n === 1) ? 0 : -1} aria-label={n + (n === 1 ? t('feedback.star') : t('feedback.stars'))}
      className={n <= value ? 'is-lit' : undefined} onClick={() => onChange(n)} onKeyDown={(e) => {
        const direction = ['ArrowRight', 'ArrowUp'].includes(e.key) ? 1 : ['ArrowLeft', 'ArrowDown'].includes(e.key) ? -1 : 0
        if (!direction) return
        e.preventDefault()
        const next = ((n - 1 + direction + 5) % 5) + 1
        onChange(next)
        e.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('button')[next - 1]?.focus()
      }}><IconStar size={22} /></button>)}
  </div>
}
