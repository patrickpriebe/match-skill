import { Avatar } from '../ui/Avatar'
import { ReputationDisplay } from './ReputationDisplay'
import { useT } from '@/i18n/I18nContext'
import type { Reputation } from '@/lib/api/types'

/**
 * The header of the screen where trust is decided. Reputation always travels
 * with its count, and the time zone is shown because this is the one endpoint
 * that actually returns it.
 */
export function ProfileHeader({ name, bio, timeZone, utcOffset, reputation }: {
  name: string
  bio?: string | null
  timeZone?: string
  utcOffset?: string
  reputation: Reputation
}) {
  const t = useT()
  return (
    <header className="page-head">
      <div className="row" style={{ gap: 16, alignItems: 'flex-start' }}>
        <Avatar name={name} size="lg" />
        <div style={{ minWidth: 0 }}>
          <h1 className="h1 is-detail">{name}</h1>
          <div className="row wrap" style={{ gap: 14, marginTop: 6 }}>
            <ReputationDisplay
              reputation={reputation}
              countLabel={
                reputation.count > 0
                  ? t(reputation.count === 1 ? 'reputation.ratingsFromExchangesOne' : 'reputation.ratingsFromExchangesOther', { count: reputation.count })
                  : undefined
              }
            />
            {timeZone && (
              <span className="who-meta">
                {timeZone}{utcOffset ? ' · ' + utcOffset : ''}
              </span>
            )}
          </div>
        </div>
      </div>
      {bio && <p className="lead" style={{ marginTop: 16 }}>{bio}</p>}
    </header>
  )
}
