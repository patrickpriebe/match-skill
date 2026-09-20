import { useEffect, useState } from 'react'
import { useT } from '@/i18n/I18nContext'

/**
 * Skeletons are sized to the real content so the page does not jump when data
 * lands. Each variant matches the component it stands in for.
 */

export function SkeletonLine({ width = '100%', height = 11, style }: {
  width?: string | number
  height?: number
  style?: React.CSSProperties
}) {
  return <div className="skel skel-line" style={{ width, height, ...style }} />
}

export function SkeletonMatchCard() {
  return (
    <div className="skel-card" style={{ minHeight: 196 }} aria-hidden="true">
      <div className="row" style={{ gap: 12 }}>
        <div className="skel" style={{ width: 40, height: 40, borderRadius: 999 }} />
        <div style={{ flex: 1 }}>
          <SkeletonLine width="44%" />
          <SkeletonLine width="28%" style={{ marginTop: 8 }} />
        </div>
      </div>
      <SkeletonLine width="100%" height={1} style={{ marginTop: 22 }} />
      <SkeletonLine width="66%" style={{ marginTop: 16 }} />
      <SkeletonLine width="58%" style={{ marginTop: 12 }} />
      <div className="skel" style={{ width: 112, height: 36, borderRadius: 8, marginTop: 24 }} />
    </div>
  )
}

export function SkeletonRows({ count = 3 }: { count?: number }) {
  return (
    <div aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} style={{ padding: '20px 0', borderBottom: '1px solid var(--border)' }}>
          <SkeletonLine width="32%" />
          <SkeletonLine width="56%" style={{ marginTop: 10 }} />
        </div>
      ))}
    </div>
  )
}

/**
 * Announced to assistive tech; visually it is the skeleton above.
 *
 * After a few seconds it also says why the wait is this long. The API runs on
 * an instance that sleeps, and a measured cold start is 71 seconds — long
 * enough that the first person to open a shared link concluded the site was
 * broken and closed it. A skeleton that never explains itself is what made
 * that a reasonable conclusion.
 */
export function LoadingState({ label, children }: { label: string; children?: React.ReactNode }) {
  const t = useT()
  const [slow, setSlow] = useState(false)

  useEffect(() => {
    // Long enough that a normal load never shows it, short enough to arrive
    // well before someone decides the page is dead.
    const timer = setTimeout(() => setSlow(true), 6000)
    return () => clearTimeout(timer)
  }, [])

  return (
    <div role="status" aria-live="polite" aria-label={label}>
      {children ?? <p>{label}...</p>}
      {slow && <p className="small dim wake-note">{t('common.wakingUp')}</p>}
    </div>
  )
}
