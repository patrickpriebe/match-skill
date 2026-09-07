import { AbsoluteDate } from './ZonedTime'
import type { ReactNode } from 'react'
import { UserCard } from './UserCard'
import { TradeLedger } from './TradeLedger'
import { ExchangeStatusBadge, MatchStrengthBadge } from './ExchangeStatusBadge'
import type { ExchangeView } from '@/lib/api/types'

import { counterpart, isIncoming } from './exchange-helpers'

/**
 * One record row for an exchange: counterpart, what travels, and the actions
 * its current state allows. History, Invitations and Search all use the same
 * three-column grid — before this component existed each screen had its own
 * column set for the same layout.
 *
 * A terminal row is dimmed and carries no actions rather than disappearing.
 * A user who sent a request deserves to see what became of it.
 */
export function ExchangeCard({ exchange, meId, actions, footnote, showStrength = true, tense = 'present' }: {
  exchange: ExchangeView
  meId: string
  actions?: ReactNode
  footnote?: ReactNode
  showStrength?: boolean
  tense?: 'present' | 'past'
}) {
  const other = counterpart(exchange, meId)
  const incoming = isIncoming(exchange, meId)
  const terminal = exchange.status === 'DECLINED' || exchange.status === 'CANCELLED'

  // On an incoming exchange, skillFromReceiver is what they learn from me.
  const theyLearn = incoming ? exchange.skillFromReceiver : exchange.skillFromRequester
  const youLearn = incoming ? exchange.skillFromRequester : exchange.skillFromReceiver

  const youVerb = tense === 'past' ? 'You learned' : 'You learn'
  const theyVerb = tense === 'past' ? 'They learned' : 'They learn'

  return (
    <div className={terminal ? 'rec-row is-closed' : 'rec-row'}>
      <div>
        <UserCard
          name={other.displayName}
          size="sm"
          nameSize={14}
          meta={other.timeZone}
        />
        <div className="row wrap" style={{ gap: 8, marginTop: 10 }}>
          <ExchangeStatusBadge status={exchange.status} />
          {showStrength && !terminal && <MatchStrengthBadge strength={exchange.strength} />}
        </div>
      </div>

      <TradeLedger
        variant={youLearn && theyLearn ? 'full' : 'half'}
        style={{ borderTop: 0 }}
        sides={[
          theyLearn
            ? { direction: theyVerb, skill: theyLearn.name, from: 'from you' }
            : { direction: theyVerb, open: incoming ? 'you choose when you accept' : 'they choose at acceptance' },
          youLearn
            ? { direction: youVerb, skill: youLearn.name, from: 'from ' + other.displayName.split(' ')[0] }
            : { direction: youVerb, open: 'not settled yet' },
        ]}
      />

      <div className="rec-act">
        {actions}
        {footnote && <p className="small dim">{footnote}</p>}
        {tense === 'past' && <p className="small dim">Last updated <AbsoluteDate iso={exchange.updatedAt} /></p>}
      </div>
    </div>
  )
}
