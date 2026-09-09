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
import { useT } from '@/i18n/I18nContext'
import type { ExchangeView } from '@/lib/api/types'

/**
 * One exchange, its timeline, and only the actions its current state allows.
 * The action set is derived from status here so it can never drift from what
 * the API would accept.
 */
export function ExchangeDetailsPage() {
  const t = useT()
  const { id = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const exchange = useAsync(() => api.getExchange(id), [id])

  if (exchange.status === 'error') {
    return <ErrorState error={exchange.error} onRetry={exchange.reload} context={t('exchange.couldNotOpen')} />
  }
  if (exchange.status === 'loading') {
    return <LoadingState label={t('exchange.loadingExchange')}><SkeletonRows count={4} /></LoadingState>
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
    try { await fn(); exchange.reload() } catch (err) { setError(err instanceof Error ? err.message : t('exchange.actionFailed')) } finally { setBusy(false) }
  }

  return (
    <>
      <PageHeader
        detail
        back={
          <Link className="link small dim only-d" to="/invitations" style={{ display: 'inline-block', marginBottom: 12 }}>
            {t('exchange.backToInvitations')}
          </Link>
        }
        badges={
          <>
            <ExchangeStatusBadge status={ex.status} />
            <MatchStrengthBadge strength={ex.strength} />
          </>
        }
        title={t('exchange.withName', { name: other.displayName })}
        lead={nextStep(ex, first, t)}
      />

      <div className="ex-grid">
        <div>
          <Section title={t('exchange.whatTravels')} emphasis>
            <TradeLedger
              style={{ borderTop: 0 }}
              variant={youLearn && theyLearn ? 'full' : 'half'}
              sides={[
                youLearn
                  ? { direction: t('exchange.youLearn'), skill: youLearn.name, from: t('common.from', { name: first }) }
                  : { direction: t('exchange.youLearn'), open: t('exchange.notSettledYet') },
                theyLearn
                  ? { direction: t('exchange.theyLearn'), skill: theyLearn.name, from: t('common.fromYou') }
                  : { direction: t('exchange.theyLearn'), open: t('exchange.decidedAtAcceptance') },
              ]}
            />
          </Section>

          {ex.scheduledAt && (
            <Section title={t('exchange.whenAndWhere')}>
              <ZonedTime
                iso={ex.scheduledAt}
                mine={{ timeZone: user?.timeZone ?? 'UTC', label: t('common.you') }}
                theirs={{ timeZone: other.timeZone ?? user?.timeZone ?? 'UTC', label: first }}
              />
              {ex.meetingUrl && (
                <Card style={{ marginTop: 14 }}>
                  <div className="row-between wrap" style={{ gap: 12 }}>
                    <div>
                      <p className="eyebrow" style={{ marginBottom: 4 }}>{t('exchange.meetingLink')}</p>
                      <p className="num small">{ex.meetingUrl.replace(/^https:\/\//, '')}</p>
                    </div>
                    <a className="btn btn-primary btn-sm" href={ex.meetingUrl} target="_blank" rel="noreferrer">
                      <IconExternal /> {t('exchange.openMeetingLink')}
                    </a>
                  </div>
                </Card>
              )}
            </Section>
          )}

          <Section
            title={t('exchange.howItGotHere')}
            note={t('exchange.howItGotHereNote')}
          >
            <Timeline exchange={ex} />
          </Section>

          <Section
            title={t('exchange.whatYouCanDoNow')}
            note={t('exchange.whatYouCanDoNowNote')}
          >
            {error && <Notice tone="stop">{error}</Notice>}
            <Actions exchange={ex} busy={busy} act={act} navigate={navigate} />
          </Section>
        </div>

        <aside>
          <Panel title={t('exchange.whoYouAreMeeting')}>
            <UserCard name={other.displayName} meta={other.timeZone} reputation={other.reputation} />
            <LinkButton
              to={'/profile/' + other.id}
              size="sm"
              wide
              style={{ marginTop: 16 }}
            >
              {t('exchange.viewFullProfile')}
            </LinkButton>
          </Panel>

          <Notice className="aside-note">
            {t('exchange.asideNote')}
          </Notice>
        </aside>
      </div>
    </>
  )
}

function nextStep(ex: ExchangeView, first: string, t: (key: string, vars?: Record<string, string | number>) => string): string {
  switch (ex.status) {
    case 'REQUESTED': return t('exchange.stepRequested')
    case 'ACCEPTED': return t('exchange.stepAccepted')
    case 'SCHEDULED': return t('exchange.stepScheduled')
    case 'COMPLETED': return t('exchange.stepCompleted', { name: first })
    case 'CANCELLED': return t('exchange.stepCancelled')
    case 'DECLINED': return t('exchange.stepDeclined')
  }
}

function Timeline({ exchange }: { exchange: ExchangeView }) {
  const t = useT()
  return <div className="tl">
    <div className="tl-row"><span className="tl-dot" aria-hidden="true" /><span className="tl-what">{t('exchange.requestCreatedBy', { name: exchange.requester.displayName })}</span><RelativeTime iso={exchange.createdAt} /></div>
    <div className="tl-row"><span className="tl-dot" aria-hidden="true" /><span className="tl-what">{t('exchange.latestUpdate')} <ExchangeStatusBadge status={exchange.status} /></span><RelativeTime iso={exchange.updatedAt} /></div>
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
  const t = useT()
  const { status, id } = exchange

  if (status === 'REQUESTED') return <LinkButton to="/invitations">{t('exchange.reviewInvitation')}</LinkButton>

  if (status === 'DECLINED' || status === 'CANCELLED') {
    return <p className="small dim">{t('exchange.closedNote')}</p>
  }

  if (status === 'COMPLETED') {
    return (
      <div className="row wrap" style={{ gap: 10 }}>
        <Button variant="primary" onClick={() => navigate('/feedback/' + id)}>{t('exchange.leaveFeedback')}</Button>
      </div>
    )
  }

  return (
    <>
      <div className="row wrap" style={{ gap: 10 }}>
        {status === 'ACCEPTED' && (
          <LinkButton to={'/scheduled/' + id} variant="primary">{t('exchange.settleATime')}</LinkButton>
        )}
        {status === 'SCHEDULED' && (
          <Button loading={busy} onClick={() => void act(() => api.completeExchange(id))}>
            {t('exchange.markCompleted')}
          </Button>
        )}
        <Button variant="stop" disabled={busy} onClick={() => void act(() => api.cancelExchange(id))}>
          {t('exchange.cancelExchange')}
        </Button>
        <Button variant="quiet" disabled aria-disabled="true">{t('exchange.leaveFeedback')}</Button>
      </div>
      <p className="small dim" style={{ marginTop: 10, maxWidth: '60ch' }}>
        {t('exchange.feedbackDisabledNote')}
      </p>
      <Notice tone="warn" className="aside-note">
        {t('exchange.cancelWarning')}
      </Notice>
    </>
  )
}
