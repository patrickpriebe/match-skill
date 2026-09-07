import type { ReactNode } from 'react'

/**
 * Every empty state on every screen routes through here. Text and one action —
 * no illustration. An empty state names the cause and offers the lever that
 * changes it, or it is not worth rendering.
 */
export function EmptyState({ title, children, actions }: {
  title: ReactNode
  children: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="empty">
      <h3 className="h3">{title}</h3>
      <p>{children}</p>
      {actions && <div className="row wrap" style={{ gap: 8 }}>{actions}</div>}
    </div>
  )
}
