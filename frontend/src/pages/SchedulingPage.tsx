import { Navigate } from 'react-router-dom'
import { localToInstant, exchangeDirections } from '@/lib/scheduling'
import { meetingUrlError } from '@/lib/meetingUrl'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { Section } from '@/components/design/layout/Section'
import { Button } from '@/components/design/ui/Button'
import { Field, Input } from '@/components/design/ui/Form'
import { Notice } from '@/components/design/ui/Surface'
import { ExchangeStatusBadge } from '@/components/design/domain/ExchangeStatusBadge'
import { TradeLedger } from '@/components/design/domain/TradeLedger'
import { ZonedTime } from '@/components/design/domain/ZonedTime'
import { MeetingLinkField } from '@/components/design/domain/MeetingLinkField'
import { AvailabilityPreview } from '@/components/design/domain/AvailabilitySelector'
import { summarise } from '@/components/design/domain/availability-helpers'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'
import { counterpart } from '@/components/design/domain/exchange-helpers'
import type { ApiError } from '@/lib/api/types'

/**
 * Turns ACCEPTED into SCHEDULED. There is no endpoint that returns mutual
 * free slots and scheduledAt is a single absolute instant, so the weekly
 * windows are guidance beside the picker — they warn, they do not block.
 */
export function SchedulingPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()

  const exchange = useAsync(() => api.getExchange(id), [id])
  const myAvailability = useAsync(() => api.getMyAvailability(), [])

  // Their windows come from the public profile — the only endpoint that has
  // them. Resolved before any early return, so the hook order never changes.
  const otherId = exchange.data
    ? (exchange.data.requesterId === user?.id ? exchange.data.receiverId : exchange.data.requesterId)
    : ''
  const theirProfile = useAsync(
    () => (otherId ? api.getProfile(otherId) : Promise.resolve(null)),
    [otherId],
  )
  const theirAvailability = theirProfile.data?.availability ?? []

  const [date, setDate] = useState('')
  const [time, setTime] = useState('')
  const [link, setLink] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [linkError, setLinkError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (myAvailability.status === 'error') return <ErrorState error={myAvailability.error} onRetry={myAvailability.reload} />
  if (theirProfile.status === 'error') return <ErrorState error={theirProfile.error} onRetry={theirProfile.reload} />
  if (exchange.status === 'error') {
    return <ErrorState error={exchange.error} onRetry={exchange.reload} />
  }
  if (exchange.status === 'loading' || myAvailability.status !== 'ready') {
    return <LoadingState label="Loading"><SkeletonRows count={4} /></LoadingState>
  }

  const ex = exchange.data
  const other = counterpart(ex, user?.id ?? '')
  const first = other.displayName.split(' ')[0]
  if (ex.status !== 'ACCEPTED') return <Navigate to={'/exchanges/' + ex.id} replace />
  const zone = user?.timeZone ?? 'UTC'
  const converted = localToInstant(date, time, zone)
  const iso = converted.iso
  const future = Boolean(iso && Date.parse(iso) > Date.now())
  const ready = future && !meetingUrlError(link)
  const directions = exchangeDirections(ex, user?.id ?? '')

  async function confirm() {
    if (!iso || !ready || busy) return
    setBusy(true)
    setError(null)
    setLinkError(null)
    try {
      await api.scheduleExchange(id, { scheduledAt: iso, meetingUrl: link.trim() })
      navigate('/exchanges/' + id)
    } catch (err) {
      const apiErr = err as ApiError
      // A rejected link surfaces on the field itself, with the server's own
      // message. The client never approves what the server refused.
      if (apiErr.code?.includes('MEETING') || apiErr.code?.includes('URL')) setLinkError(apiErr.message)
      else setError(apiErr.message)
    } finally {
      setBusy(false)
    }
  }

  const hasAvailability = theirAvailability.length > 0 || myAvailability.data.windows.length > 0

  return (
    <>
      <PageHeader
        badges={<ExchangeStatusBadge status={ex.status} />}
        title={'Settle a time with ' + first}
        lead="Every time below is shown in both zones. The platform arranges the exchange — the meeting itself happens on your own tool."
      />

      <div className="sch-grid">
        <div>
          <Section title="What you agreed" emphasis>
            <TradeLedger
              style={{ borderTop: 0 }}
              sides={[
                { direction: 'You learn', skill: directions.youLearn?.name ?? 'Not recorded', from: 'from ' + first },
                { direction: 'They learn', skill: directions.theyLearn?.name ?? 'Not recorded', from: 'from you' },
              ]}
            />
          </Section>

          <Section
            title="When you are both usually free"
            note="Each weekly window is shown in its owner's time zone. These windows guide your choice; they do not reserve a meeting."
          >
            {hasAvailability ? (
              <>
                <div className="only-d">
                  <AvailabilityPreview windows={myAvailability.data.windows} />
                </div>
                <p className="small" style={{ marginTop: 14 }}>
                  <b>You ({zone}):</b> {summarise(myAvailability.data.windows) || 'No windows recorded'}.
                </p>
                <p className="small"><b>{first} ({theirProfile.data?.timezone ?? other.timeZone}):</b> {summarise(theirAvailability) || 'No windows recorded'}.</p>
              </>
            ) : (
              <EmptyState title="No weekly windows recorded">
                One of you has no recorded windows, or they never meet. You can still pick a
                time — it will just take a round trip to confirm.
              </EmptyState>
            )}
          </Section>

          <Section title="Date and time">
            <div className="when-row">
              <Field label="Date">
                {(fieldId) => (
                  <Input id={fieldId} type="date" value={date} onChange={(e) => setDate(e.target.value)} />
                )}
              </Field>
              <Field label={<>Start time <span className="dim">- {zone}</span></>}>
                {(fieldId) => (
                  <Input id={fieldId} type="time" value={time} onChange={(e) => setTime(e.target.value)} />
                )}
              </Field>
            </div>

            {converted.error && <Notice tone="stop">{converted.error}</Notice>}
            {iso && !future && <Notice tone="stop">Choose a future date and time.</Notice>}
            {iso && (
              <>
                <div style={{ marginTop: 16 }}>
                  <ZonedTime
                    iso={iso}
                    mine={{ timeZone: user?.timeZone ?? 'UTC', label: 'You' }}
                    theirs={{ timeZone: theirProfile.data?.timezone ?? other.timeZone ?? 'UTC', label: first }}
                  />
                </div>
                <Notice className="aside-note">
                  The confirmation and the exchange record both carry the absolute instant, so
                  neither of you has to do the arithmetic again.
                </Notice>
              </>
            )}
          </Section>

          <Section
            title="Meeting link"
            note="An exchange is a channel to a stranger, so the link is checked on the server against a list of meeting-tool hosts and refused otherwise."
          >
            <MeetingLinkField value={link} onChange={(value) => { setLink(value); setLinkError(null) }} serverError={linkError} />
          </Section>

          {error && <Notice tone="stop">{error}</Notice>}

          <div className="row wrap" style={{ gap: 10, marginTop: 'var(--gap-lg)' }}>
            <Button variant="primary" loading={busy} disabled={!ready} onClick={() => void confirm()}>
              Confirm the meeting
            </Button>
            <Button variant="quiet" onClick={() => navigate('/invitations')}>Back to invitations</Button>
          </div>
          <p className="small dim" style={{ marginTop: 10, maxWidth: '56ch' }}>
            There is no reschedule step. Changing a confirmed time means cancelling the exchange
            and starting again, so check the zones once more before you confirm.
          </p>
        </div>
      </div>
    </>
  )
}
