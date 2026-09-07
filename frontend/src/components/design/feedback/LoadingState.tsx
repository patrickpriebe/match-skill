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

/** Announced to assistive tech; visually it is the skeleton above. */
export function LoadingState({ label, children }: { label: string; children?: React.ReactNode }) {
  return (
    <div role="status" aria-live="polite" aria-label={label}>
      {children ?? <p>{label}...</p>}
    </div>
  )
}
