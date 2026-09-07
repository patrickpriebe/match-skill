import type { ReactNode } from 'react'
import { Rule } from '../ui/Surface'

/**
 * A titled block. `emphasis` draws the ink rule instead of the hairline and
 * is reserved for the primary group on a screen — on Home and Search that is
 * the complete-trades section, which is how the hierarchy survives even
 * before a single card is read.
 */
export function Section({ title, count, note, emphasis, children, id }: {
  title: ReactNode
  count?: number
  note?: ReactNode
  emphasis?: boolean
  children: ReactNode
  id?: string
}) {
  return (
    <section className="block" id={id}>
      <div className="block-head">
        <div className="row" style={{ alignItems: 'baseline', gap: 10 }}>
          <h2 className="h2">{title}</h2>
          {count !== undefined && <span className="meta">{count}</span>}
        </div>
        {note && <p className="note">{note}</p>}
      </div>
      <Rule soft={!emphasis} />
      {children}
    </section>
  )
}
