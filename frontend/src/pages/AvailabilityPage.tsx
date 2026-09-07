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
import type { ApiError } from '@/lib/api/types'

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
      title="Availability"
      lead="Recurring weekly windows, not specific dates. This narrows matching as well as scheduling: two complementary skill lists that never overlap in time are not a usable match."
    />
  )

  if (saved.status === 'error') {
    return <>{head}<ErrorState error={saved.error} onRetry={saved.reload} /></>
  }
  if (saved.status === 'loading' || windows === null || zone === null) {
    return <>{head}<LoadingState label="Loading availability"><SkeletonRows count={4} /></LoadingState></>
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
              label="Your time zone"
              className="tz-field"
              hint="Every window below is stored in this zone. Change it and the times keep their numbers — they do not shift."
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
                title="Your week is empty"
                actions={
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setWindows([{ dayOfWeek: 'TUESDAY', startTime: '18:00', endTime: '20:00' }])}
                  >
                    Add your first window
                  </Button>
                }
              >
                Availability narrows matching as well as scheduling. With nothing here you still
                appear in results, but every conversation starts by asking when you are free.
              </EmptyState>
            ) : (
              <fieldset disabled={busy} style={{ padding: 0, margin: 0, border: 0 }}><legend className="sr-only">Weekly windows</legend><AvailabilitySelector windows={windows} onChange={(next) => { setWindows(next); setSavedMessage(false) }} /></fieldset>
            )}
          </section>

          {overlap && <Notice tone="stop">Windows on the same day must not overlap.</Notice>}
          {savedMessage && <Notice>Availability saved.</Notice>}
          {error && <Notice tone="stop" className="aside-note">{error}</Notice>}

          <div className="save-bar row-between">
            <span className="meta">{windows.length} windows · {hours}h per week</span>
            <div className="row" style={{ gap: 10 }}>
              <Button variant="quiet" size="sm" disabled={busy} onClick={() => { setWindows(null); setSavedMessage(false) }}>Discard changes</Button>
              <Button variant="primary" size="sm" loading={busy} disabled={!valid} onClick={() => void save()}>
                Save availability
              </Button>
            </div>
          </div>
          <p className="small dim" style={{ marginTop: 10, maxWidth: '60ch' }}>
            Saving replaces the whole week at once, so nothing is written until you press it —
            there is no partial autosave to half-finish.
          </p>
        </div>

        <aside>
          <Panel title="Week at a glance" aside={<span className="meta">your zone</span>}>
            <div className="only-d">
              <AvailabilityPreview windows={windows} />
            </div>
            <p className="small" style={{ marginTop: 14 }}>
              <b>In words:</b> {windows.length ? summarise(windows) : 'nothing set'}.
            </p>
          </Panel>

          <Notice tone={browserZone === zone ? 'neutral' : 'warn'} className="aside-note">
            {browserZone === zone
              ? <>Your browser reports <b>{browserZone}</b>, which matches what is saved.</>
              : <>Your browser reports <b>{browserZone}</b>, but <b>{zone}</b> is saved. Change it above if you moved.</>}
          </Notice>
        </aside>
      </div>
    </>
  )
}
