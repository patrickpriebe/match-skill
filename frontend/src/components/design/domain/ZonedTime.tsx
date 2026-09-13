import { useI18n } from '@/i18n/I18nContext'
import type { Locale } from '@/i18n/I18nContext'

/** Maps the app's two-language toggle to a real Intl locale for date/number
 *  formatting. Kept in one place so "add a language" only means one line. */
const INTL_LOCALE: Record<Locale, string> = { en: 'en-GB', pt: 'pt-BR' }

const fmt = (locale: string, iso: string, timeZone: string, opts: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat(locale, { ...opts, timeZone }).format(new Date(iso))

/**
 * Scheduling between strangers in different cities is the normal case here,
 * not the exception, so a proposed time is meaningless without a zone. Both
 * sides are always shown, and the record carries the absolute instant so
 * neither person has to do the arithmetic again.
 */
export function ZonedTime({ iso, mine, theirs }: {
  iso: string
  mine: { timeZone: string; label: string; offset?: string }
  theirs: { timeZone: string; label: string; offset?: string }
}) {
  const { locale } = useI18n()
  const intlLocale = INTL_LOCALE[locale]
  const render = (z: { timeZone: string; label: string; offset?: string }) => (
    <div className="zone">
      <time dateTime={iso} className="zone-when">
        {fmt(intlLocale, iso, z.timeZone, { weekday: 'short', day: 'numeric', month: 'short' })}
        {' · '}
        {fmt(intlLocale, iso, z.timeZone, { hour: '2-digit', minute: '2-digit', hour12: false })}
      </time>
      <div className="zone-who">
        {z.label} · {z.timeZone}{z.offset ? ' · ' + z.offset : ''}
      </div>
    </div>
  )

  return (
    <div className="zones">
      {render(mine)}
      {render(theirs)}
    </div>
  )
}

const UNITS: Array<[Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 31536000000], ['month', 2592000000], ['week', 604800000],
  ['day', 86400000], ['hour', 3600000], ['minute', 60000],
]

/** "2 days ago" visible, the absolute date in the tooltip and in datetime. */
export function RelativeTime({ iso, className = 'meta' }: { iso: string; className?: string }) {
  const { locale, t } = useI18n()
  const intlLocale = INTL_LOCALE[locale]
  const diff = new Date(iso).getTime() - Date.now()
  const abs = Math.abs(diff)
  const unit = UNITS.find(([, ms]) => abs >= ms)
  const rtf = new Intl.RelativeTimeFormat(intlLocale, { numeric: 'auto' })
  const label = unit ? rtf.format(Math.round(diff / unit[1]), unit[0]) : t('common.justNow')
  const absolute = new Intl.DateTimeFormat(intlLocale, { dateStyle: 'long', timeStyle: 'short' })
    .format(new Date(iso))

  return <time className={className} dateTime={iso} title={absolute}>{label}</time>
}

/** An absolute date, for ledgers where "3 weeks ago" is less useful. */
export function AbsoluteDate({ iso, className = 'meta' }: { iso: string; className?: string }) {
  const { locale } = useI18n()
  return (
    <time className={className} dateTime={iso}>
      {new Intl.DateTimeFormat(INTL_LOCALE[locale], { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(iso))}
    </time>
  )
}
