import { Pagination } from '@/components/design/ui/Pagination'
import { useState } from 'react'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { Tabs } from '@/components/design/ui/Tabs'
import { Button, LinkButton } from '@/components/design/ui/Button'
import { Dialog, PickOption } from '@/components/design/ui/Dialog'
import { Notice } from '@/components/design/ui/Surface'
import { TradeLedger } from '@/components/design/domain/TradeLedger'
import { ExchangeCard } from '@/components/design/domain/ExchangeCard'
import { isIncoming } from '@/components/design/domain/exchange-helpers'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'
import type { ApiError, ExchangeView, Skill } from '@/lib/api/types'

/**
 * Where an exchange is born and where it turns. Only the receiver may accept
 * or decline, so the outgoing tab carries no approve action — it would be a
 * button that cannot work.
 *
 * Nothing is removed optimistically. The consequence of accepting is a
 * commitment to a real person's time, and rolling that back visually is worse
 * than a half-second wait.
 */
export function InvitationsPage({ scheduledOnly = false }: { scheduledOnly?: boolean }) {
  const [page, setPage] = useState(0)
  const { user } = useAuth()
  const [tab, setTab] = useState<'received' | 'sent'>('received')
  const [accepting, setAccepting] = useState<ExchangeView | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [rowError, setRowError] = useState<Record<string, string>>({})

  const exchanges = useAsync(() => api.getExchanges(scheduledOnly ? 'SCHEDULED' : undefined, page), [page, scheduledOnly])
  const meId = user?.id ?? ''

  const head = (
    <PageHeader
      title={scheduledOnly ? 'Scheduled exchanges' : 'Invitations'}
      lead="A request is a commitment to a stranger's time. Nothing is automatic — you answer each one, and only you can accept or decline what was sent to you."
    />
  )

  if (exchanges.status === 'error') {
    return <>{head}<ErrorState error={exchanges.error} onRetry={exchanges.reload} /></>
  }
  if (exchanges.status === 'loading') {
    return <>{head}<LoadingState label="Loading invitations"><SkeletonRows /></LoadingState></>
  }

  const open = exchanges.data.items.filter((e) => e.status !== 'COMPLETED')
  const received = open.filter((e) => isIncoming(e, meId))
  const sent = open.filter((e) => !isIncoming(e, meId))
  const list = tab === 'received' ? received : sent

  async function run(exchange: ExchangeView, action: () => Promise<unknown>) {
    setBusy(exchange.id)
    setRowError((prev) => ({ ...prev, [exchange.id]: '' }))
    try {
      await action()
      exchanges.reload()
    } catch (err) {
      setRowError((prev) => ({ ...prev, [exchange.id]: (err as ApiError).message }))
    } finally {
      setBusy(null)
    }
  }

  function onAccept(exchange: ExchangeView) {
    // On a PARTIAL only skillFromReceiver is known. The receiver chooses
    // skillFromRequester here, from the requester's offered list — that
    // choice is the whole reason PARTIAL requests exist.
    if (!exchange.skillFromRequester) {
      setAccepting(exchange)
      return
    }
    void run(exchange, () => api.acceptExchange(exchange.id, { skillFromRequester: exchange.skillFromRequester!.id }))
  }

  return (
    <>
      {head}

      <Tabs
        label="Invitation direction"
        active={tab}
        onChange={(id) => setTab(id as 'received' | 'sent')}
        items={[
          { id: 'received', label: 'Received', count: received.length },
          { id: 'sent', label: 'Sent', count: sent.length },
        ]}
      />

      <p className="meta" style={{ marginBottom: 16 }}>Received and sent counts refer to this page. Use Next to see older exchanges.</p>
      {list.length === 0 ? (
        tab === 'received' ? (
          <EmptyState
            title="No received invitations on this page"
            actions={
              <>
                <LinkButton to="/profile/me" size="sm">Review your profile</LinkButton>
                <LinkButton to="/availability" variant="quiet" size="sm">Set availability</LinkButton>
              </>
            }
          >
            Requests arrive faster when your profile says what you actually cover and your week
            has windows in it.
          </EmptyState>
        ) : (
          <EmptyState
            title="No sent invitations on this page"
            actions={<LinkButton to="/matches" size="sm">See your matches</LinkButton>}
          >
            A complete trade is the cheapest place to start — both sides already declared.
          </EmptyState>
        )
      ) : (
        list.map((exchange) => (
          <ExchangeCard
            key={exchange.id}
            exchange={exchange}
            meId={meId}
            footnote={rowError[exchange.id] || undefined}
            actions={<RowActions
              exchange={exchange}
              incoming={tab === 'received'}
              busy={busy === exchange.id}
              onAccept={() => onAccept(exchange)}
              onDecline={() => void run(exchange, () => api.declineExchange(exchange.id))}
              onCancel={() => void run(exchange, () => api.cancelExchange(exchange.id))}
            />}
          />
        ))
      )}

      <Pagination {...exchanges.data} onChange={setPage} />
      {accepting && (
        <AcceptPartialDialog
          exchange={accepting}
          onClose={() => setAccepting(null)}
          onConfirm={async (skill) => {
            const target = accepting
            setAccepting(null)
            await run(target, () => api.acceptExchange(target.id, { skillFromRequester: skill.id }))
          }}
        />
      )}
    </>
  )
}

function RowActions({ exchange, incoming, busy, onAccept, onDecline, onCancel }: {
  exchange: ExchangeView
  incoming: boolean
  busy: boolean
  onAccept: () => void
  onDecline: () => void
  onCancel: () => void
}) {
  const terminal = exchange.status === 'DECLINED' || exchange.status === 'CANCELLED'
  if (terminal) return <span className="small dim">No further action.</span>

  if (exchange.status === 'REQUESTED' && incoming) {
    const needsChoice = !exchange.skillFromRequester
    return (
      <>
        <Button variant="primary" size="sm" loading={busy} onClick={onAccept}>
          {needsChoice ? 'Accept…' : 'Accept'}
        </Button>
        <Button variant="quiet" size="sm" disabled={busy} onClick={onDecline}>Decline</Button>
      </>
    )
  }

  if (exchange.status === 'ACCEPTED') {
    return (
      <>
        <LinkButton to={'/scheduled/' + exchange.id} variant="primary" size="sm">Settle a time</LinkButton>
        <Button variant="quiet" size="sm" disabled={busy} onClick={onCancel}>Cancel</Button>
      </>
    )
  }

  if (exchange.status === 'SCHEDULED') {
    return <LinkButton to={'/exchanges/' + exchange.id} size="sm">Open exchange</LinkButton>
  }

  return <span className="small dim">Waiting on them.</span>
}

/** The moment PARTIAL earns its place in the product. */
function AcceptPartialDialog({ exchange, onClose, onConfirm }: {
  exchange: ExchangeView
  onClose: () => void
  onConfirm: (skill: Skill) => void | Promise<void>
}) {
  const offered = useAsync(() => api.getProfile(exchange.requesterId).then((p) => p.offeredSkills.filter((s) => s.status === 'APPROVED')), [exchange.requesterId])
  const [picked, setPicked] = useState<Skill | null>(null)
  const first = exchange.requester.displayName.split(' ')[0]

  return (
    <Dialog
      eyebrow={exchange.strength === 'PARTIAL' ? 'Partial request - accepting' : 'Complete trade - accepting'}
      title={'Pick what you’ll learn from ' + first}
      onClose={onClose}
      footer={
        <>
          <Button size="sm" onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            size="sm"
            disabled={!picked}
            onClick={() => picked && void onConfirm(picked)}
          >
            Accept and settle the trade
          </Button>
        </>
      }
    >
      <TradeLedger
        style={{ marginBottom: 20 }}
        sides={[
          { direction: 'Settled', skill: exchange.skillFromReceiver.name, from: '— they learn this from you' },
          { direction: 'Open', open: 'choose below to complete the trade' },
        ]}
      />

      <p className="small dim" style={{ marginBottom: 12 }}>
        These are the approved skills {first} offers. Choose what you would like to learn in return.
      </p>

      {offered.status === 'ready' && (
        <div role="radiogroup" aria-label="What you will learn">
          {offered.data.map((skill) => (
            <PickOption
              key={skill.id}
              selected={picked?.id === skill.id}
              onSelect={() => setPicked(skill)}
              title={skill.name}
            />
          ))}
        </div>
      )}
      {offered.status === 'loading' && <SkeletonRows count={3} />}
      {offered.status === 'error' && <ErrorState error={offered.error} onRetry={offered.reload} />}

      <Notice className="dialog-note">
        Your choice is recorded on the exchange, so the history later reads
        &ldquo;{first} taught X, you taught {exchange.skillFromReceiver.name}&rdquo;.
      </Notice>
    </Dialog>
  )
}
