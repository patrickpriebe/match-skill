import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { Section } from '@/components/design/layout/Section'
import { Button, LinkButton } from '@/components/design/ui/Button'
import { Card, Panel, Notice } from '@/components/design/ui/Surface'
import { IconExternal } from '@/components/design/ui/icons'
import { ExchangeStatusBadge, MatchStrengthBadge } from '@/components/design/domain/ExchangeStatusBadge'
import { TradeLedger } from '@/components/design/domain/TradeLedger'
import { ZonedTime, RelativeTime } from '@/components/design/domain/ZonedTime'
import { UserCard } from '@/components/design/domain/UserCard'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'
import { counterpart } from '@/components/design/domain/exchange-helpers'
import type { ExchangeView } from '@/lib/api/types'

/**
 * One exchange, its timeline, and only the actions its current state allows.
 * The action set is derived from status here so it can never drift from what
 * the API would accept.
 */
export function ExchangeDetailsPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const exchange = useAsync(() => api.getExchange(id), [id])

  if (exchange.status === 'error') {
    return <ErrorState error={exchange.error} onRetry={exchange.reload} context="We couldn't open that exchange." />
  }
  if (exchange.status === 'loading') {
    return <LoadingState label="Loading exchange"><SkeletonRows count={4} /></LoadingState>
  }

  const ex = exchange.data
  const meId = user?.id ?? ''
  const other = counterpart(ex, meId)
  const first = other.displayName.split(' ')[0]
  const incoming = ex.receiverId === meId
  const theyLearn = incoming ? ex.skillFromReceiver : ex.skillFromRequester
  const youLearn = incoming ? ex.skillFromRequester : ex.skillFromReceiver

  async function act(fn: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try { await fn(); exchange.reload() } catch (err) { setError(err instanceof Error ? err.message : 'The action failed.') } finally { setBusy(false) }
  }

  return (
    <>
      <PageHeader
        detail
        back={
          <Link className="link small dim only-d" to="/invitations" style={{ display: 'inline-block', marginBottom: 12 }}>
            ← Invitations
          </Link>
        }
        badges={
          <>
            <ExchangeStatusBadge status={ex.status} />
            <MatchStrengthBadge strength={ex.strength} />
          </>
        }
        title={'Exchange with ' + other.displayName}
        lead={nextStep(ex, first)}
      />

      <div className="ex-grid">
        <div>
          <Section title="What travels" emphasis>
            <TradeLedger
              style={{ borderTop: 0 }}
              variant={youLearn && theyLearn ? 'full' : 'half'}
              sides={[
                youLearn
                  ? { direction: 'You learn', skill: youLearn.name, from: 'from ' + first }
                  : { direction: 'You learn', open: 'not settled yet' },
                theyLearn
                  ? { direction: 'They learn', skill: theyLearn.name, from: 'from you' }
                  : { direction: 'They learn', open: 'decided at acceptance' },
              ]}
            />
          </Section>

          {ex.scheduledAt && (
            <Section title="When and where">
              <ZonedTime
                iso={ex.scheduledAt}
                mine={{ timeZone: user?.timeZone ?? 'UTC', label: 'You' }}
                theirs={{ timeZone: other.timeZone ?? user?.timeZone ?? 'UTC', label: first }}
              />
              {ex.meetingUrl && (
                <Card style={{ marginTop: 14 }}>
                  <div className="row-between wrap" style={{ gap: 12 }}>
                    <div>
                      <p className="eyebrow" style={{ marginBottom: 4 }}>Meeting link</p>
                      <p className="num small">{ex.meetingUrl.replace(/^https:\/\//, '')}</p>
                    </div>
                    <a className="btn btn-primary btn-sm" href={ex.meetingUrl} target="_blank" rel="noreferrer">
                      <IconExternal /> Open meeting link
                    </a>
                  </div>
                </Card>
              )}
            </Section>
          )}

          <Section
            title="How it got here"
            note="The request date and latest update are recorded below."
          >
            <Timeline exchange={ex} />
          </Section>

          <Section
            title="What you can do now"
            note="The set below is everything this exchange allows in its current state."
          >
            {error && <Notice tone="stop">{error}</Notice>}
            <Actions exchange={ex} busy={busy} act={act} navigate={navigate} />
          </Section>
        </div>

        <aside>
          <Panel title="Who you are meeting">
            <UserCard name={other.displayName} meta={other.timeZone} reputation={other.reputation} />
            <LinkButton
              to={'/profile/' + other.id}
              size="sm"
              wide
              style={{ marginTop: 16 }}
            >
              View full profile
            </LinkButton>
          </Panel>

          <Notice className="aside-note">
            The platform arranges the exchange; it does not host it. Nobody here observes the
            meeting, which is why completion is something one of you declares.
          </Notice>
        </aside>
      </div>
    </>
  )
}

function nextStep(ex: ExchangeView, first: string): string {
  switch (ex.status) {
    case 'REQUESTED': return 'Waiting for an answer. Nothing is committed until it is accepted.'
    case 'ACCEPTED': return 'Both of you agreed. The next step is settling a date and a link.'
    case 'SCHEDULED': return 'Nothing else is needed from either of you until the session.'
    case 'COMPLETED': return 'This one happened. Rating it is what feeds ' + first + '’s reputation.'
    case 'CANCELLED': return 'Called off after acceptance. This state is terminal.'
    case 'DECLINED': return 'The invitation was refused. This state is terminal.'
  }
}

function Timeline({ exchange }: { exchange: ExchangeView }) {
  return <div className="tl">
    <div className="tl-row"><span className="tl-dot" aria-hidden="true" /><span className="tl-what">Request created by {exchange.requester.displayName}</span><RelativeTime iso={exchange.createdAt} /></div>
    <div className="tl-row"><span className="tl-dot" aria-hidden="true" /><span className="tl-what">Latest update: <ExchangeStatusBadge status={exchange.status} /></span><RelativeTime iso={exchange.updatedAt} /></div>
  </div>
}

/**
 * Actions by state, matching the authorization rules in the contract: only
 * the receiver may accept or decline; either side may schedule, complete or
 * cancel. Feedback is shown disabled rather than hidden while the exchange is
 * not complete, so the path to it is visible before you get there.
 */
function Actions({ exchange, busy, act, navigate }: {
  exchange: ExchangeView
  busy: boolean
  act: (fn: () => Promise<unknown>) => Promise<void>
  navigate: (to: string) => void
}) {
  const { status, id } = exchange

  if (status === 'REQUESTED') return <LinkButton to="/invitations">Review invitation</LinkButton>

  if (status === 'DECLINED' || status === 'CANCELLED') {
    return <p className="small dim">This exchange is closed. Nothing further can happen on it.</p>
  }

  if (status === 'COMPLETED') {
    return (
      <div className="row wrap" style={{ gap: 10 }}>
        <Button variant="primary" onClick={() => navigate('/feedback/' + id)}>Leave feedback</Button>
      </div>
    )
  }

  return (
    <>
      <div className="row wrap" style={{ gap: 10 }}>
        {status === 'ACCEPTED' && (
          <LinkButton to={'/scheduled/' + id} variant="primary">Settle a time</LinkButton>
        )}
        {status === 'SCHEDULED' && (
          <Button loading={busy} onClick={() => void act(() => api.completeExchange(id))}>
            Mark as completed
          </Button>
        )}
        <Button variant="stop" disabled={busy} onClick={() => void act(() => api.cancelExchange(id))}>
          Cancel exchange
        </Button>
        <Button variant="quiet" disabled aria-disabled="true">Leave feedback</Button>
      </div>
      <p className="small dim" style={{ marginTop: 10, maxWidth: '60ch' }}>
        Feedback is disabled because the exchange is not completed yet — shown rather than
        hidden, so the path to it is visible before you get there.
      </p>
      <Notice tone="warn" className="aside-note">
        Cancelling is terminal. There is no reschedule step, so a new time means a new request
        from the start.
      </Notice>
    </>
  )
}
