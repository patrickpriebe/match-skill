import type { ReactNode } from 'react'

export interface TradeSide {
  direction: string
  skill?: ReactNode
  from?: ReactNode
  /** Rendered when the skill on this side is not decided yet. */
  open?: ReactNode
}

/**
 * The signature device of the product: two entries of equal weight, opposite
 * alignment, one hairline between them. It reappears on Home, Search,
 * Invitations, Scheduling, Exchange details and History, and it is what makes
 * a complete trade look complete without spending a colour on it.
 *
 * `full` draws the top rule in ink (both sides settled); `half` draws it in
 * hairline (one side still open). That single line carries the MUTUAL /
 * PARTIAL distinction even in monochrome.
 */
export function TradeLedger({ sides, variant = 'full', className, style }: {
  sides: TradeSide[]
  variant?: 'full' | 'half'
  className?: string
  style?: React.CSSProperties
}) {
  return (
    <div
      className={['trade', variant === 'full' ? 'is-full' : 'is-half', className].filter(Boolean).join(' ')}
      style={style}
    >
      {sides.map((side, i) => (
        <div className="trade-row" key={side.direction + i}>
          <span className="trade-dir">{side.direction}</span>
          {side.open
            ? <span className="trade-open">{side.open}</span>
            : (
              <span className="trade-skill">
                {side.skill}
                {side.from && <span className="trade-from"> {side.from}</span>}
              </span>
            )}
        </div>
      ))}
    </div>
  )
}
