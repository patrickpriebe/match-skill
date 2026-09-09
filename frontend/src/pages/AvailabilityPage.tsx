import { windowsOverlap } from '@/lib/availability'
import { useAuth } from '@/context/AuthContext'
import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { useAsync } from '../hooks/useAsync'
import { PageHeader } from '@/components/design/layout/PageHeader'
import { Rule, Panel, Notice } from '@/components/design/ui/Surface'
import { Button } from '@/components/design/ui/Button'
import { Field, Select } from '@/components/design/ui/Form'
import { AvailabilitySelector, AvailabilityPreview } from '@/components/design/domain/AvailabilitySelector'
import { summarise, totalHours, windowIsValid } from '@/components/design/domain/availability-helpers'
import type { DraftWindow } from '@/components/design/domain/availability-helpers'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'
import { useT } from '@/i18n/I18nContext'
import type { ApiError, DayOfWeek } from '@/lib/api/types'

const ZONES = [
  'America/Sao_Paulo', 'America/New_York', 'Europe/Lisbon', 'Europe/Berlin',
  'Asia/Dubai', 'Asia/Tokyo',
]

/**
 * Recurring weekly windows, not specific dates. Availability narrows matching
 * as well as scheduling, so an empty week weakens the feed too — the empty
 * state says that rather than shrugging.
 *
 * PUT replaces the whole set, so the draft is local and the save is atomic.
 * Partial saves are not expressible against this API, so there is no autosave
 * to half-finish.
 */
export function AvailabilityPage() {
  const t = useT()
  const dayLabel = (day: DayOfWeek) => t(`daysShort.${day}`)
  const { refreshUser } = useAuth()
  const saved = useAsync(() => api.getMyAvailability(), [])
  const [windows, setWindows] = useState<DraftWindow[] | null>(null)
  const [zone, setZone] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [savedMessage, setSavedMessage] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (saved.status === 'ready' && windows === null) {
      setWindows(saved.data.windows.map(({ dayOfWeek, startTime, endTime }) => ({ dayOfWeek, startTime, endTime })))
      setZone(saved.data.timezone)
    }
  }, [saved.status, saved.data, windows])

  const head = (
    <PageHeader
      title={t('availability.title')}
      lead={t('availability.lead')}
    />
  )

  if (saved.status === 'error') {
    return <>{head}<ErrorState error={saved.error} onRetry={saved.reload} /></>
  }
  if (saved.status === 'loading' || windows === null || zone === null) {
    return <>{head}<LoadingState label={t('availability.loadingAvailability')}><SkeletonRows count={4} /></LoadingState></>
  }

  const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone
  const overlap = windowsOverlap(windows)
  const valid = windows.every(windowIsValid) && !overlap
  const hours = totalHours(windows)

  async function save() {
    if (!valid || busy) return
    setBusy(true)
    setError(null)
    try {
      await api.replaceMyAvailability({ timezone: zone!, windows: windows! })
      await refreshUser()
      setSavedMessage(true)
      saved.reload()
    } catch (err) {
      setError((err as ApiError).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {head}

      <div className="av-grid">
        <div>
          <section className="block" style={{ marginBottom: 'var(--gap-lg)' }}>
            <Field
              label={t('availability.yourTimeZone')}
              className="tz-field"
              hint={t('availability.timeZoneHint')}
            >
              {(id) => (
                <Select id={id} disabled={busy} value={zone} onChange={(e) => { setZone(e.target.value); setSavedMessage(false) }}>
                  {[...new Set([zone, 'UTC', ...(typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ZONES)])].map((z) => <option key={z} value={z}>{z}</option>)}
                </Select>
              )}
            </Field>
          </section>

          <section>
            <Rule />
            {windows.length === 0 ? (
              <EmptyState
                title={t('availability.emptyTitle')}
                actions={
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setWindows([{ dayOfWeek: 'TUESDAY', startTime: '18:00', endTime: '20:00' }])}
                  >
                    {t('availability.addFirstWindow')}
                  </Button>
                }
              >
                {t('availability.emptyBody')}
              </EmptyState>
            ) : (
              <fieldset disabled={busy} style={{ padding: 0, margin: 0, border: 0 }}><legend className="sr-only">Weekly windows</legend><AvailabilitySelector windows={windows} onChange={(next) => { setWindows(next); setSavedMessage(false) }} /></fieldset>
            )}
          </section>

          {overlap && <Notice tone="stop">{t('availability.overlapError')}</Notice>}
          {savedMessage && <Notice>{t('availability.saved')}</Notice>}
          {error && <Notice tone="stop" className="aside-note">{error}</Notice>}

          <div className="save-bar row-between">
            <span className="meta">{t('availability.windowsPerWeek', { count: windows.length, hours })}</span>
            <div className="row" style={{ gap: 10 }}>
              <Button variant="quiet" size="sm" disabled={busy} onClick={() => { setWindows(null); setSavedMessage(false) }}>{t('availability.discardChanges')}</Button>
              <Button variant="primary" size="sm" loading={busy} disabled={!valid} onClick={() => void save()}>
                {t('availability.save')}
              </Button>
            </div>
          </div>
          <p className="small dim" style={{ marginTop: 10, maxWidth: '60ch' }}>
            {t('availability.saveNote')}
          </p>
        </div>

        <aside>
          <Panel title={t('availability.weekAtAGlance')} aside={<span className="meta">{t('availability.yourZone')}</span>}>
            <div className="only-d">
              <AvailabilityPreview windows={windows} />
            </div>
            <p className="small" style={{ marginTop: 14 }}>
              <b>{t('profile.inWords')}</b> {windows.length ? summarise(windows, dayLabel) : t('availability.nothingSet')}.
            </p>
          </Panel>

          <Notice tone={browserZone === zone ? 'neutral' : 'warn'} className="aside-note">
            {browserZone === zone
              ? <>{t('availability.browserMatchesPrefix')}<b>{browserZone}</b>{t('availability.browserMatchesSuffix')}</>
              : <>{t('availability.browserMismatchPrefix')}<b>{browserZone}</b>{t('availability.browserMismatchMiddle')}<b>{zone}</b>{t('availability.browserMismatchSuffix')}</>}
          </Notice>
        </aside>
      </div>
    </>
  )
}
