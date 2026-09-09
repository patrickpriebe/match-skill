import { timeInMinutes as minutes } from '@/lib/availability'
import { totalHours, windowIsValid, type DraftWindow } from './availability-helpers'
import { Button } from '../ui/Button'
import { Input } from '../ui/Form'
import { useT } from '@/i18n/I18nContext'
import { DAYS } from '@/lib/api/types'
import type { DayOfWeek } from '@/lib/api/types'

/**
 * Explicit range rows grouped by day, not a cell grid. AvailabilityWindow
 * stores free start and end times, so a 30-minute grid would invent a
 * granularity the backend never asked for and then have to quantise anything
 * the user already had. Range rows map one-to-one onto the payload, express
 * 19:00–20:30 without special cases, and reduce to a readable list on a phone
 * where a seven-column grid cannot fit.
 *
 * PUT /me/availability replaces the whole set, so this component holds a
 * draft and saves atomically. Partial saves are not expressible against this
 * API, so there is no autosave to half-finish.
 */
export function AvailabilitySelector({ windows, onChange }: {
  windows: DraftWindow[]
  onChange: (next: DraftWindow[]) => void
}) {
  const t = useT()
  const dayLabel = (day: DayOfWeek) => t(`days.${day}`)
  const update = (index: number, patch: Partial<DraftWindow>) =>
    onChange(windows.map((w, i) => (i === index ? { ...w, ...patch } : w)))

  const remove = (index: number) => onChange(windows.filter((_, i) => i !== index))

  const add = (day: DayOfWeek) =>
    onChange([...windows, { dayOfWeek: day, startTime: '18:00', endTime: '20:00' }])

  return (
    <div>
      {DAYS.map((day) => {
        const rows = windows
          .map((w, index) => ({ w, index }))
          .filter(({ w }) => w.dayOfWeek === day)
        const hours = totalHours(rows.map(({ w }) => w))
        const invalid = rows.some(({ w }) => !windowIsValid(w))

        return (
          <div className="day" key={day}>
            <div className="day-head">
              <span className="day-name">{dayLabel(day)}</span>
              <span className="meta">
                {rows.length === 0 ? t('availability.noWindows') : t('availability.hoursShort', { hours })}
                {invalid && t('availability.oneInvalid')}
              </span>
            </div>

            {rows.length === 0 ? (
              <div className="row" style={{ gap: 10 }}>
                <span className="day-empty">{t('availability.nothingSetDay')}</span>
                <Button variant="quiet" size="sm" onClick={() => add(day)}>{t('availability.addAWindow')}</Button>
              </div>
            ) : (
              <>
                {rows.map(({ w, index }) => {
                  const bad = !windowIsValid(w)
                  return (
                    <div className="range" key={index}>
                      <Input
                        type="time" mono value={w.startTime} invalid={bad}
                        aria-label={dayLabel(day) + ' start'}
                        onChange={(e) => update(index, { startTime: e.target.value })}
                      />
                      <span className="sep">{t('availability.to')}</span>
                      <Input
                        type="time" mono value={w.endTime} invalid={bad}
                        aria-label={dayLabel(day) + ' end'}
                        onChange={(e) => update(index, { endTime: e.target.value })}
                      />
                      <button
                        type="button" className="rm"
                        aria-label={t('availability.removeWindow', { day: dayLabel(day), start: w.startTime, end: w.endTime })}
                        onClick={() => remove(index)}
                      >
                        ×
                      </button>
                    </div>
                  )
                })}
                {invalid && (
                  <p className="err-text" style={{ marginTop: 8 }}>
                    {t('availability.windowInvalidError')}
                  </p>
                )}
                <Button variant="quiet" size="sm" style={{ marginTop: 8 }} onClick={() => add(day)}>
                  {t('availability.addAWindow')}
                </Button>
              </>
            )}
          </div>
        )
      })}
    </div>
  )
}

/** Read-only week preview. A list of ranges is precise but hard to feel; the
 *  grid is what makes "weekday evenings only" obvious. It never takes input,
 *  so there is exactly one editing surface. */
export function AvailabilityPreview({ windows, otherWindows }: {
  windows: DraftWindow[]
  otherWindows?: DraftWindow[]
}) {
  const t = useT()
  const bands = [0, 3, 6, 9, 12, 15, 18, 21]
  const covers = (list: DraftWindow[], day: DayOfWeek, band: number) =>
    list.some((w) => w.dayOfWeek === day && minutes(w.startTime) < (band + 3) * 60 && minutes(w.endTime) > band * 60)

  return (
    <div className="wk" style={{ gridTemplateColumns: '34px repeat(7, 1fr)' }}>
      <span />
      {DAYS.map((d) => <span className="wk-h" key={d}>{t(`daysShort.${d}`)[0]}</span>)}
      {bands.flatMap((band) => [
        <span className="wk-t" key={'t' + band}>{String(band).padStart(2, '0')}</span>,
        ...DAYS.map((day) => {
          const mine = covers(windows, day, band)
          const theirs = otherWindows ? covers(otherWindows, day, band) : false
          const cls = mine && theirs ? 'wk-c both' : mine ? 'wk-c on' : theirs ? 'wk-c them' : 'wk-c'
          return <span className={cls} key={day + band} />
        }),
      ])}
    </div>
  )
}
