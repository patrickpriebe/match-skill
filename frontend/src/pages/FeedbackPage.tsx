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

export function FeedbackPage() {
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
  if (record.status === 'loading') return <LoadingState label="Loading feedback"><SkeletonRows /></LoadingState>
  const { exchange, feedback } = record.data
  const mine = feedback?.mine
  const other = counterpart(exchange, meId)
  const directions = exchangeDirections(exchange, meId)
  if (exchange.status !== 'COMPLETED') return <><PageHeader title="Feedback" /><EmptyState title="This exchange is not completed yet" actions={<LinkButton to={'/exchanges/' + id}>Open exchange</LinkButton>}>Only completed exchanges can be rated.</EmptyState></>
  if (mine) return <><PageHeader title={'Your feedback for ' + other.displayName} />
    <Card><h2 className="h2">Your rating is saved</h2><p>{mine.rating} of 5</p>{mine.comment && <p>{mine.comment}</p>}
      <Notice>{feedback?.counterpartSubmitted ? 'Both ratings are published and contribute to your public reputations.' : 'Your rating is saved privately. It will publish when the other participant submits their rating. There is no release deadline.'}</Notice>
      {feedback?.theirs && <section style={{ marginTop: 20 }}><h2 className="h2">Their feedback for you</h2><p>{feedback.theirs.rating} of 5</p>{feedback.theirs.comment && <p>{feedback.theirs.comment}</p>}</section>}
      <p className="small dim">One rating per person per exchange. Your saved rating cannot be edited.</p>
      <LinkButton to="/history">Return to history</LinkButton>
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
      else setError(err instanceof Error ? err.message : 'Feedback could not be saved.')
    } finally { setBusy(false) }
  }
  return <div className="fb-wrap">
    <PageHeader detail title={'How did it go with ' + other.displayName.split(' ')[0] + '?'} lead="Describe your exchange to help others decide who to learn with." />
    <Card style={{ marginBottom: 24 }}><UserCard name={other.displayName} meta={other.timeZone} />
      <TradeLedger sides={[
        { direction: 'You learned', skill: directions.youLearn?.name ?? 'Not recorded' },
        { direction: 'They learned', skill: directions.theyLearn?.name ?? 'Not recorded' },
      ]} />
    </Card>
    <form className="stack" onSubmit={submit}>
      <fieldset style={{ border: 0, padding: 0 }}><legend className="h3">Your rating</legend>
        <StarInput value={rating} onChange={setRating} />
        <p className="small dim">{rating ? rating + ' of 5' : 'Choose a rating'} · use arrow keys to move.</p>
      </fieldset>
      <Field label="Comment (optional)">{(fieldId) => <Textarea id={fieldId} maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />}</Field>
      <Notice>{feedback?.counterpartSubmitted ? 'They have submitted their rating. Its contents remain hidden until you submit yours.' : 'Your rating stays private until both participants submit. Both ratings then publish together and contribute to reputation.'}</Notice>
      {error && <Notice tone="stop">{error}</Notice>}
      <div className="row wrap" style={{ gap: 8 }}><Button variant="primary" type="submit" loading={busy} disabled={!rating}>Submit rating</Button><LinkButton to="/history">Not now</LinkButton></div>
      <p className="small dim">One rating per person per completed exchange. Once saved, it cannot be edited.</p>
    </form>
  </div>
}
function StarInput({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  return <div className="rate" role="radiogroup" aria-label="Rating from 1 to 5">
    {[1, 2, 3, 4, 5].map((n) => <button key={n} type="button" role="radio" aria-checked={value === n}
      tabIndex={value === n || (!value && n === 1) ? 0 : -1} aria-label={n + (n === 1 ? ' star' : ' stars')}
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
