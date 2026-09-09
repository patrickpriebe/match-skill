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
import { useT } from '@/i18n/I18nContext'
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
  const t = useT()
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
      title={scheduledOnly ? t('invitations.scheduledTitle') : t('invitations.title')}
      lead={t('invitations.lead')}
    />
  )

  if (exchanges.status === 'error') {
    return <>{head}<ErrorState error={exchanges.error} onRetry={exchanges.reload} /></>
  }
  if (exchanges.status === 'loading') {
    return <>{head}<LoadingState label={t('invitations.loadingInvitations')}><SkeletonRows /></LoadingState></>
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
        label={t('invitations.direction')}
        active={tab}
        onChange={(id) => setTab(id as 'received' | 'sent')}
        items={[
          { id: 'received', label: t('invitations.received'), count: received.length },
          { id: 'sent', label: t('invitations.sent'), count: sent.length },
        ]}
      />

      <p className="meta" style={{ marginBottom: 16 }}>{t('invitations.pageNote')}</p>
      {list.length === 0 ? (
        tab === 'received' ? (
          <EmptyState
            title={t('invitations.noReceivedTitle')}
            actions={
              <>
                <LinkButton to="/profile/me" size="sm">{t('invitations.reviewProfile')}</LinkButton>
                <LinkButton to="/availability" variant="quiet" size="sm">{t('invitations.setAvailability')}</LinkButton>
              </>
            }
          >
            {t('invitations.noReceivedBody')}
          </EmptyState>
        ) : (
          <EmptyState
            title={t('invitations.noSentTitle')}
            actions={<LinkButton to="/matches" size="sm">{t('invitations.seeMatches')}</LinkButton>}
          >
            {t('invitations.noSentBody')}
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
  const t = useT()
  const terminal = exchange.status === 'DECLINED' || exchange.status === 'CANCELLED'
  if (terminal) return <span className="small dim">{t('invitations.noFurtherAction')}</span>

  if (exchange.status === 'REQUESTED' && incoming) {
    const needsChoice = !exchange.skillFromRequester
    return (
      <>
        <Button variant="primary" size="sm" loading={busy} onClick={onAccept}>
          {needsChoice ? t('invitations.acceptEllipsis') : t('invitations.accept')}
        </Button>
        <Button variant="quiet" size="sm" disabled={busy} onClick={onDecline}>{t('invitations.decline')}</Button>
      </>
    )
  }

  if (exchange.status === 'ACCEPTED') {
    return (
      <>
        <LinkButton to={'/scheduled/' + exchange.id} variant="primary" size="sm">{t('invitations.settleATime')}</LinkButton>
        <Button variant="quiet" size="sm" disabled={busy} onClick={onCancel}>{t('invitations.cancel')}</Button>
      </>
    )
  }

  if (exchange.status === 'SCHEDULED') {
    return <LinkButton to={'/exchanges/' + exchange.id} size="sm">{t('invitations.openExchange')}</LinkButton>
  }

  return <span className="small dim">{t('invitations.waitingOnThem')}</span>
}

/** The moment PARTIAL earns its place in the product. */
function AcceptPartialDialog({ exchange, onClose, onConfirm }: {
  exchange: ExchangeView
  onClose: () => void
  onConfirm: (skill: Skill) => void | Promise<void>
}) {
  const t = useT()
  const offered = useAsync(() => api.getProfile(exchange.requesterId).then((p) => p.offeredSkills.filter((s) => s.status === 'APPROVED')), [exchange.requesterId])
  const [picked, setPicked] = useState<Skill | null>(null)
  const first = exchange.requester.displayName.split(' ')[0]

  return (
    <Dialog
      eyebrow={exchange.strength === 'PARTIAL' ? t('invitations.acceptPartialEyebrow') : t('invitations.acceptCompleteEyebrow')}
      title={t('invitations.acceptPartialTitle', { name: first })}
      onClose={onClose}
      footer={
        <>
          <Button size="sm" onClick={onClose}>{t('common.cancel')}</Button>
          <Button
            variant="primary"
            size="sm"
            disabled={!picked}
            onClick={() => picked && void onConfirm(picked)}
          >
            {t('invitations.acceptAndSettle')}
          </Button>
        </>
      }
    >
      <TradeLedger
        style={{ marginBottom: 20 }}
        sides={[
          { direction: t('invitations.settled'), skill: exchange.skillFromReceiver.name, from: t('invitations.settledFromYou') },
          { direction: t('invitations.open'), open: t('invitations.openChooseBelow') },
        ]}
      />

      <p className="small dim" style={{ marginBottom: 12 }}>
        {t('invitations.approvedSkillsNote', { name: first })}
      </p>

      {offered.status === 'ready' && (
        <div role="radiogroup" aria-label={t('invitations.whatYouWillLearn')}>
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
        {t('invitations.historyQuote', { name: first, skill: exchange.skillFromReceiver.name })}
      </Notice>
    </Dialog>
  )
}
