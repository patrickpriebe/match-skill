import type { ReactNode } from 'react'
import { IconInfo, IconWarning, IconXCircle } from './icons'

const cx = (...p: Array<string | false | undefined>) => p.filter(Boolean).join(' ')

export function Card({ quiet, accent, className, children, ...rest }: {
  quiet?: boolean
  accent?: boolean
  className?: string
  children: ReactNode
} & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      {...rest}
      className={cx(quiet ? 'card-quiet' : 'card', className)}
      style={accent ? { borderColor: 'var(--accent-line)', borderWidth: '1.5px', ...rest.style } : rest.style}
    >
      {children}
    </div>
  )
}

export function Panel({ title, aside, children, className }: {
  title?: ReactNode
  aside?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cx('panel', className)}>
      {title && (
        <div className={cx('panel-head', Boolean(aside) && 'row-between')} style={aside ? { alignItems: 'baseline' } : undefined}>
          <h2 className="h3">{title}</h2>
          {aside}
        </div>
      )}
      <div className="panel-body">{children}</div>
    </div>
  )
}

export type NoticeTone = 'neutral' | 'warn' | 'stop'

const NOTICE_ICON = {
  neutral: IconInfo,
  warn: IconWarning,
  stop: IconXCircle,
}

/** Inline explanation. Never a toast — a message that vanishes is a message
 *  nobody read. */
export function Notice({ tone = 'neutral', children, className }: {
  tone?: NoticeTone
  children: ReactNode
  className?: string
}) {
  const Icon = NOTICE_ICON[tone]
  return (
    <div role={tone === 'stop' ? 'alert' : 'status'} className={cx('notice', tone === 'warn' && 'notice-warn', tone === 'stop' && 'notice-stop', className)}>
      <Icon size={15} />
      <span>{children}</span>
    </div>
  )
}

export function Rule({ soft, className }: { soft?: boolean; className?: string }) {
  return <hr className={cx('block-rule', soft && 'soft', className)} />
}
