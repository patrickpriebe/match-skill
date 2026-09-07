import type { ReactNode } from 'react'

/**
 * `detail` steps the title down one level for a single-record screen. It is a
 * modifier rather than an inline font-size, so the type scale stays in one
 * place instead of forking per page.
 */
export function PageHeader({ title, lead, badges, back, detail, actions }: {
  title: ReactNode
  lead?: ReactNode
  badges?: ReactNode
  back?: ReactNode
  detail?: boolean
  actions?: ReactNode
}) {
  return (
    <header className="page-head">
      {back}
      {badges && <div className="row wrap" style={{ gap: 10, marginBottom: 10 }}>{badges}</div>}
      <h1 className={detail ? 'h1 is-detail' : 'h1'}>{title}</h1>
      {lead && <p className="lead">{lead}</p>}
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}
