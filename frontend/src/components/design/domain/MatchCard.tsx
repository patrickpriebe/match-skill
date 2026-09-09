import { Link } from 'react-router-dom'
import { UserCard } from './UserCard'
import { TradeLedger } from './TradeLedger'
import { Button } from '../ui/Button'
import { useT } from '@/i18n/I18nContext'
import type { Match } from '@/lib/api/types'

import type { MatchDirections } from './match-helpers'

/**
 * A complete trade and a partial suggestion are the same data in two very
 * different presentations: card density, grid size and action weight all
 * change. Only the mutual card gets the solid button.
 *
 * The card shows no time zone, because MatchUser does not carry one (Q12).
 * The absence is honest — it is not filled with a guess.
 */
export function MatchCard({ match, directions, note, onRequest, requesting }: {
  match: Match
  directions: MatchDirections
  note?: string
  onRequest?: (match: Match) => void
  requesting?: boolean
}) {
  const t = useT()
  const mutual = match.strength === 'MUTUAL'
  const { user } = match
  const first = user.displayName.split(' ')[0]

  return (
    <article className={mutual ? 'mcard mcard-mutual' : 'mcard mcard-partial'}>
      <UserCard
        name={user.displayName}
        size={mutual ? 'md' : 'sm'}
        nameSize={mutual ? undefined : 14}
        reputation={user.reputation}
        meta={user.timeZone}
      />

      <TradeLedger
        variant={mutual ? 'full' : 'half'}
        sides={[
          {
            direction: t('matchCard.youLearn'),
            skill: directions.youLearn?.name,
            from: mutual ? t('common.from', { name: first }) : undefined,
          },
          directions.theyLearn
            ? { direction: t('matchCard.theyLearn'), skill: directions.theyLearn.name, from: t('common.fromYou') }
            : { direction: t('matchCard.theyLearn'), open: t('matchCard.openUntilAccept') },
        ]}
      />

      {mutual && note && <p className="small dim">{note}</p>}

      <div className="mcard-foot row" style={{ gap: 6 }}>
        <Button
          variant={mutual ? 'primary' : 'secondary'}
          size="sm"
          loading={requesting}
          disabled={!onRequest}
          onClick={() => onRequest?.(match)}
        >
          {t('matchCard.sendRequest')}
        </Button>
        {(
          <Link className="btn btn-quiet btn-sm" to={'/profile/' + user.id}>{t('matchCard.viewProfile')}</Link>
        )}
      </div>
    </article>
  )
}
