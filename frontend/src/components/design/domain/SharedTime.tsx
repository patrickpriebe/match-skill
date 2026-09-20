import { Link } from 'react-router-dom'
import { useT } from '@/i18n/I18nContext'
import { DAYS, type SharedWindow } from '@/lib/api/types'

const MINUTES_PER_DAY = 24 * 60

function toMinutes(time: string): number {
  const [hours, minutes] = time.split(':')
  return Number(hours) * 60 + Number(minutes)
}

/** '00:00' as an end time closes the day rather than opening the next one. */
function endMinutes(time: string): number {
  const value = toMinutes(time)
  return value === 0 ? MINUTES_PER_DAY : value
}

function hhmm(time: string): string {
  return time.slice(0, 5)
}

/**
 * Renders the number that decides the ranking, which used to exist only inside
 * the server: how much of the week these two people are free at the same time,
 * in the viewer's own clock.
 *
 * The strip is seven day columns over a midnight-to-midnight track, with the
 * shared stretches drawn in place. A list of times would say the same thing in
 * words; the strip says it at a glance, and the shape of someone's week --
 * weekday evenings, or Saturday morning -- is the part a person recognises
 * before reading anything.
 */
export function SharedTime({ windows, minutes, compact }: {
  windows: SharedWindow[]
  minutes: number
  compact?: boolean
}) {
  const t = useT()

  if (windows.length === 0) {
    return (
      <p className="shared-none small">
        {t('sharedTime.none')}{' '}
        <Link to="/availability">{t('sharedTime.noneAction')}</Link>
      </p>
    )
  }

  const hours = Math.round((minutes / 60) * 10) / 10
  const byDay = new Map(DAYS.map((day) => [day, windows.filter((w) => w.dayOfWeek === day)]))
  const longest = windows.reduce((best, w) => (w.minutes > best.minutes ? w : best), windows[0])

  return (
    <div className={compact ? 'shared shared-compact' : 'shared'}>
      <p className="shared-head">
        <strong>{t('sharedTime.total', { hours })}</strong>
        <span className="dim"> · {t('sharedTime.yourClock')}</span>
      </p>

      <div className="shared-week" role="img" aria-label={t('sharedTime.total', { hours })}>
        {DAYS.map((day) => (
          <div className="shared-day" key={day}>
            <span className="shared-daylabel">{t(`daysShort.${day}`)}</span>
            <div className="shared-track">
              {(byDay.get(day) ?? []).map((w) => (
                <span
                  key={`${w.startTime}-${w.endTime}`}
                  className="shared-block"
                  style={{
                    top: `${(toMinutes(w.startTime) / MINUTES_PER_DAY) * 100}%`,
                    height: `${((endMinutes(w.endTime) - toMinutes(w.startTime)) / MINUTES_PER_DAY) * 100}%`,
                  }}
                />
              ))}
            </div>
          </div>
        ))}
      </div>

      <p className="shared-best small dim">
        {t('sharedTime.longest', {
          day: t(`days.${longest.dayOfWeek}`),
          start: hhmm(longest.startTime),
          end: hhmm(longest.endTime),
        })}
      </p>
    </div>
  )
}
